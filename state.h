#include "comm.h"

struct cpu_states_t {
	uint16_t reg_pc;
	uint8_t reg_a;
	uint8_t reg_ps;
	uint8_t reg_x;
	uint8_t reg_y;
	uint8_t reg_sp;
};

struct nc2k_states_t{
	//uint32_t version;
	////////////cpu_states_t cpu;

	uint8_t SAVE_STATE_BEGIN;
	uint8_t ram_io[0x40*2];
	uint8_t ram_b[0x2000];
	uint8_t ram_b2[0x2000];
	uint8_t ram[0x8000*2];
	uint8_t ext_ram[0x8000];
	
	uint8_t ext_reg[256];

	uint8_t SAVE_STATE_END; //TODO: in theory some IO's internal state need to be saved too

	uint8_t fp_step;
	uint8_t fp_type;

	uint64_t cycles;
	uint64_t last_cycles;

	//uint8_t interr_flag;

/*
===================
below are all legacy fields, only used in old cpu_loop or io
===================
*/

	uint8_t clock_buff[80];
	uint8_t clock_flags;

	uint8_t jg_wav_data[0x20];
	uint8_t jg_wav_flags;
	uint8_t jg_wav_idx;
	bool jg_wav_playing;
    
	bool slept;
	bool should_wake_up;
	bool pending_wake_up;
	uint8_t wake_up_flags;

	bool timer0_toggle;

	uint64_t unknown_timer_cycles;
	uint64_t timer0_cycles;
	uint64_t timer1_cycles;
	uint64_t timebase_cycles;
	uint64_t nmi_cycles;
	uint8_t keypad_matrix[8];
};
