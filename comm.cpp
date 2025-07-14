#include "comm.h"

/*
===================
global switch
===================
*/
bool nc1020mode = false;
bool nc2000mode = false;
bool nc3000mode = false;
bool pc1000mode = false;

CpuVersion cpu_version = CPU_HANDYPSP;
CpuLoopVersion cpu_loop_version = CPU_RUN3;
IoVersion io_version = IO_V2;

NorFormat nor_read_format = NorFormat::PHYSICAL_ORDER;
NorFormat nor_write_format = NorFormat::PHYSICAL_ORDER;
/*
===================
debug related
===================
*/

string inject_code;
uint64_t tick=0;  //tick is mostly for debug

bool enable_dyn_debug=false;
int enable_dyn_debug_next_n=100;

bool enable_debug_nand=false;

bool enable_debug_switch=false;
bool enable_debug_pc=false;
bool enable_oops=false;
bool enable_inject=false;

bool wanna_inject=false;
bool injected=false;

bool enable_debug_beeper=false;

/*
===================
emulation parameter
===================
*/
int power_save_interval=1200;

/*
===================
cycles related
===================
*/

double oc_factor=1.0;

//uint32_t static_multipler;
uint32_t CYCLES_SECOND;
uint32_t UNKNOWN_TIMER0_FREQ;
uint32_t TIMER0_FREQ;
uint32_t TIMER1_FREQ;
uint32_t TIMEBASE_FREQ;
uint32_t CYCLES_UNKNOWN_TIMER;
uint32_t CYCLES_TIMER0;
uint32_t CYCLES_TIMER1;
uint32_t CYCLES_TIMEBASE;
uint32_t CYCLES_TIMER1_SPEED_UP;
uint32_t CYCLES_NMI;
uint32_t CYCLES_MS;
/*
===================
rom related
===================
*/
uint32_t num_nor_pages;
uint32_t num_nand_pages;
uint32_t num_rom_pages;
uint32_t ROM_SIZE;
uint32_t NOR_SIZE;

/*
===================
display related
===================
*/
int pixel_size=3;
int gap_size=1;
int lcd_scale=1;
int total_size;


WqxRom nc1020_rom;

void init_parameters(){
    /*
    ===================
    cycles related
    ===================
    */
    //static_multipler=1; //tmp fix for speed and crash

    // cpu cycles per second (cpu freq).
    CYCLES_SECOND = 3686400*(pc1000mode) + 5120000*(nc1020mode||nc2000mode)+10240000*nc3000mode;
    CYCLES_SECOND *= oc_factor;
    
    UNKNOWN_TIMER0_FREQ = 2;
    TIMER0_FREQ = 2; //not used now
    TIMER1_FREQ = 200;//not used now
    TIMEBASE_FREQ = 250;
    CYCLES_UNKNOWN_TIMER = CYCLES_SECOND / UNKNOWN_TIMER0_FREQ;
    // cpu cycles per timer0 period (1/2 s).
    CYCLES_TIMER0 = CYCLES_SECOND / TIMER0_FREQ;
    // cpu cycles per timer1 period (1/256 s).
    CYCLES_TIMER1 = CYCLES_SECOND / TIMER1_FREQ;
    CYCLES_TIMEBASE = CYCLES_SECOND / TIMEBASE_FREQ;
    // speed up
    CYCLES_TIMER1_SPEED_UP = CYCLES_SECOND / TIMER1_FREQ / 20;
    // cpu cycles per ms (1/1000 s).
    CYCLES_NMI = CYCLES_SECOND / 2;
    CYCLES_MS = CYCLES_SECOND / 1000;

    /*
    ===================
    rom related
    ===================
    */
    num_nor_pages =0x10+uint32_t(nc1020mode&&nc1020_use_1024k_nor)*0x10+uint32_t(nc3000mode)*0x10;

    //this is the nand pages of 528byte each
    num_nand_pages = 0+ uint32_t(nc2000mode)*65536  + uint32_t(nc3000mode)*65536*2;

    //const uint32_t num_nor_pages =0x20;
    num_rom_pages =0x300;
    ROM_SIZE = 0x8000 * num_rom_pages;
    NOR_SIZE = 0x8000 * num_nor_pages;

    /*
    ===================
    display related
    ===================
    */
    total_size=pixel_size+gap_size;
}

/*void rom_switcher(){
    if(nc1020mode){
        nc1020_rom.romPath = "./1020/obj_lu.bin";
        nc1020_rom.norFlashPath = "./1020/nc1020.fls";
        //nc1020_rom.norFlashPath = "./1020/nc1020.fls2";
        //nc1020_rom.norFlashPath = "./1020/flash.ini";
        //nc1020_rom.norFlashPath = "./1020/nc1020ch.fls.half";
    }
    if(nc2000mode){
        nc1020_rom.nandFlashPath = "./2000rom/phy_ggvsimformat_2000.nand";
        nc1020_rom.norFlashPath = "./2000rom/phy_ggvsimformat_2000.nor";  

        if (nc2000_use_2600_rom){
                nc1020_rom.nandFlashPath = "./2600rom/phy_ggvsimformat_2600.nand";
                nc1020_rom.norFlashPath = "./2600rom/phy_ggvsimformat_2600.nor";  
                if(nc2600_rom_use_ggvsim){
                    nc1020_rom.nandFlashPath = "./2600rom/2600nand.bin";
                    nc1020_rom.norFlashPath = "./2600rom/2600nor.bin";  
                }

        }
    }

    if(nc3000mode){
        nc1020_rom.nandFlashPath = "./3000/nc3000.nand";
        nc1020_rom.norFlashPath = "./3000/nc3000.nor"; 
    }

    if(pc1000mode){
        nc1020_rom.romPath = "./cc800/brom.bin";
        //nc1020_rom.norFlashPath = "./cc800/pc1000emux.fls";
        nc1020_rom.norFlashPath = "./cc800/pc1000.fls";
    }
}*/

void ProcessBinaryRev(uint8_t* dest, uint8_t* src, uint32_t size){
	uint32_t offset = 0;
    while (offset < size) {
        memcpy(dest + offset + 0x4000, src + offset, 0x4000);
        memcpy(dest + offset, src + offset + 0x4000, 0x4000);
        offset += 0x8000;
    }
}

void ProcessBinaryLinear(uint8_t* dest, uint8_t* src, uint32_t size){
	uint32_t offset = 0;
    while (offset < size) {
        memcpy(dest + offset , src + offset, 0x4000);
        memcpy(dest + offset + 0x4000, src + offset + 0x4000, 0x4000);
        offset += 0x8000;
    }
}


void read_file(string name,vector<char> &v){
    FILE *f = fopen(name.c_str(), "rb");
    if(f==0) {
        printf("open file %s fail!\n",name.c_str());
        exit(-1);
    }
    fseek(f, 0, SEEK_END);
    int fsize = ftell(f);
    fseek(f, 0, SEEK_SET);  /* same as rewind(f); */
    v.resize(fsize);
    fread(&v[0], fsize, 1, f);
    fclose(f);
}

int read_file_noexit(string name,vector<char> &v){
    FILE *f = fopen(name.c_str(), "rb");
    if(f==0) {
        printf("open file %s fail!\n",name.c_str());
        return -1;
    }
    fseek(f, 0, SEEK_END);
    int fsize = ftell(f);
    fseek(f, 0, SEEK_SET);  /* same as rewind(f); */
    v.resize(fsize);
    fread(&v[0], fsize, 1, f);
    fclose(f);
    return 0;
}

