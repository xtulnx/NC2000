#include "nc2000.h"
#include "comm.h"
#include "disassembler.h"
#include "state.h"
#include "cpu.h"
#include "mem.h"
#include "io.h"
#include "rom.h"
#include "nor.h"
#include "nand.h"
#include <SDL2/SDL.h>
#include <SDL_timer.h>
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <deque>
#include "sound.h"
#include "compare/c6502.h"
extern WqxRom nc1020_rom;

nc1020_states_t nc1020_states;
BusWrapper *dummy_bus = nullptr;

//static uint32_t& version = nc1020_states.version;

static bool& slept = nc1020_states.slept;
static bool& should_wake_up = nc1020_states.should_wake_up;

static uint8_t* keypad_matrix = nc1020_states.keypad_matrix;
//static uint32_t& lcd_addr = nc1020_states.lcd_addr;

static bool& wake_up_pending = nc1020_states.pending_wake_up;
static uint8_t& wake_up_key = nc1020_states.wake_up_flags;

/*void ResetStates(){
	//version = VERSION;
	memset(&nc1020_states,0,sizeof(nc1020_states_t));
	init_mem();
	reset_cpu_states();
	//cpu->reset();
}*/

/*
void Reset() {
	init_nor();
	ResetStates();
}*/

#if 0
void LoadStates(){
	FILE* file = fopen(nc1020_rom.statesPath.c_str(), "rb");
	if (file == NULL) {
		return;
	}
	fread(&nc1020_states, 1, sizeof(nc1020_states), file);
	fclose(file);
	/*
	if (version != VERSION) {
		return;
	}*/
	super_switch();
}

void SaveStates(){
	FILE* file = fopen(nc1020_rom.statesPath.c_str(), "wb");
	fwrite(&nc1020_states, 1, sizeof(nc1020_states), file);
	fflush(file);
	fclose(file);
}
#endif

void save_state(string file_name){
	if(file_name.empty()) file_name=nc1020_rom.statesPath;
	else file_name+=".state";
	FILE* file = fopen(file_name.c_str(), "wb");
	if (file == NULL) {
		printf("states file %s open failed, skip saving!\n", nc1020_rom.statesPath.c_str());
		return;
	}
	fwrite(&nc1020_states.SAVE_STATE_BEGIN, 1, &nc1020_states.SAVE_STATE_END-&nc1020_states.SAVE_STATE_BEGIN, file);
	fflush(file);
	fclose(file);
	printf("state saved to file %s!!\n",file_name.c_str());
}

void delete_state(string file_name){
	if(file_name.empty()) file_name=nc1020_rom.statesPath;
	else file_name+=".state";
	if(remove(file_name.c_str())==0){
		printf("state file %s deleted!\n",file_name.c_str());
	}else{
		printf("state file %s not exist!\n",file_name.c_str());
	}
}

void load_state(){
	FILE* file = fopen(nc1020_rom.statesPath.c_str(), "rb");
	if (file == NULL) {
		printf("states file %s open failed, skip loading!\n", nc1020_rom.statesPath.c_str());
		return;
	}
	int ret=fread(&nc1020_states.SAVE_STATE_BEGIN, 1, &nc1020_states.SAVE_STATE_END-&nc1020_states.SAVE_STATE_BEGIN, file);
	fclose(file);
	printf("loaded states from %s, ret=%d\n", nc1020_rom.statesPath.c_str(),ret);
	//super_switch();
}

void LoadNC1020(){
	memset(&nc1020_states,0,sizeof(nc1020_states_t));
	dummy_bus= new BusWrapper();

	init_io();
	
	void CreateHotlinkMapping();
	CreateHotlinkMapping();

	/*
	if(cpu_loop_version==CPU_RUN1) {
		init_cpu();
	}
	if(cpu_loop_version==CPU_RUN2) {
		if(io_version==IO_V1) {
			init_cpu();
		}else if(io_version==IO_V2) {
			init_cpu_new();
		} else {
			assert(false);
		}
	}
	if(cpu_loop_version==CPU_RUN3) { 
		init_cpu_new();
	}*/

	//rom_switcher();
	init_nor();
	if(pc1000mode||nc1020mode) {
		init_rom();
	}
	if(nc2000mode||nc3000mode) {
		read_nand0_file();
		read_nand_file();
	}

	init_mem();
	if(enable_load_state){
		load_state();
		prepare_soft_reset();
		void sync_time_2000();
		if(nc2000mode){
			if(enable_sync) sync_time_2000();
		}
	}

	//reset_cpu_states();
	init_cpu_new();

	if(nc2000mode||nc3000mode){
		//nc3000c-lee has it but seems like no need?
		//ram_io[0x18]=0x20;
	}
	//LoadStates();
}
/*
void SaveNC1020(){
	SaveNor();
	//SaveStates();
}*/

bool is_grey_mode(){
    extern unsigned short lcdbuffaddr;
    extern unsigned short lcdbuffaddrmask;
	unsigned short lcd_addr = lcdbuffaddr&lcdbuffaddrmask;
	//printf("%x  ",lcd_addr);
	//fflush(stdout);
	if(nc2000mode||nc3000mode||nc1020mode)
		return lcd_addr==0x1380;
	return false;
}
bool CopyLcdBuffer(uint8_t* buffer){
    extern unsigned short lcdbuffaddr;
    extern unsigned short lcdbuffaddrmask;
    unsigned short lcd_addr = lcdbuffaddr&lcdbuffaddrmask;
	if (lcd_addr == 0) return false;

	if(nc1020mode){
		if(!is_grey_mode()){
			memcpy(buffer, ram_buff + lcd_addr, 1600 );
		}else{
			memcpy(buffer, ram_buff + lcd_addr, 1600 *2);
		}
		return true;;
	}
	else if(nc2000mode||nc3000mode){
		//TODO: cannot use lcd_addr, it has some offset
		if(!is_grey_mode()){
			memcpy(buffer, ram_buff + 0x19c0, 1600 );
		}else{
			memcpy(buffer, ram_buff + 0x19c0 -1600, 1600 *2);
		}
		return true;
	}else{
		memcpy(buffer, ram_buff + lcd_addr, 1600);
		return true;
	}
	assert(false);
}


void RunTimeSlice(uint32_t time_slice, bool speed_up) {
	uint32_t new_cycles = time_slice * CYCLES_MS;

	new_cycles= new_cycles * speed_multiplier;

	uint64_t target_cycles=nc1020_states.cycles +new_cycles;

	//auto old=sound_stream.size();
	//printf("<%u,%u, %lld>",cycles,end_cycles,SDL_GetTicks64());
	while (nc1020_states.cycles < target_cycles) {
		if(cpu_loop_version == CPU_RUN1){
			cpu_run();
		}else if (cpu_loop_version == CPU_RUN2){
			cpu_run2();
		}else if (cpu_loop_version == CPU_RUN3){
			cpu_run3();
		}else{
			assert(false);
		}
	}

	post_cpu_run_sound_handling();

	//nc1020_states.previous_cycles+=end_cycles;
	//nc1020_states.cycles -= end_cycles;
	//nc1020_states.timer0_cycles -= end_cycles;
	//nc1020_states.timer1_cycles -= end_cycles;


}


void save_flash(string file){
	write_nand0_file(file);
	write_nand_file(file);
	SaveNor(file);
	printf("flash saved to file!!\n");
}
