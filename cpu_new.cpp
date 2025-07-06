#include "ansi/c6502.h"
#include "comm.h"
#include "mem.h"
#include "console.h"
#include "ram.h"
#include "state.h"
#include "disassembler.h"
#include "pc1000bus.h"
#include "io_new.h"

#define qDebug(...)


extern unsigned short gThreadFlags;
extern nc1020_states_t nc1020_states;
static uint64_t& cycles = nc1020_states.cycles;
static uint64_t& last_cycles = nc1020_states.last_cycles;
static uint8_t * rtc_reg=nc1020_states.rtc_reg;
static uint8_t& interr_flag = nc1020_states.interr_flag;
struct BusPC1000 *bus_pc1000=0;
extern IBus6502 *dummy_bus;
extern C6502* cpu;

void init_emux_cpu_and_bus(){
	//assert(use_emux_cpu);
	// assert(use_emux_bus);
	if(pc1000mode) {
		bus_pc1000=new BusPC1000();
		cpu=new C6502(bus_pc1000);
		bus_pc1000->cpu=cpu;
		dummy_bus=0;
	}else{
		cpu=new C6502(dummy_bus);
	}
	

	// now in resetStates()
	//cpu->reset();
}

bool trigger_every_x_ms(int x){
	uint32_t target_cycles=  CYCLES_SECOND*x/1000;
	return (cycles/target_cycles > last_cycles/target_cycles);
}

bool trigger_x_times_per_s(int x){
	uint32_t target_cycles=CYCLES_SECOND/x;
	return (cycles/target_cycles > last_cycles/target_cycles);
}

void setTime1000() {
	const int ADDR_POWER_UP_FLAG = 0x435;
	const int ADDR_WATCH_DOG = 0x468;
	const int ADDR_IDLESEC = 0x471;
    const int ADDR_HOUR = 0x469;
    const int ADDR_YEAR = 0x46c;

    bus_pc1000->write(ADDR_IDLESEC, 0); //设置idlesec为0，禁止自动关机
    int year = bus_pc1000->read(ADDR_YEAR) + 1881;
    if (year == 2000) {
		/*SYSTEMTIME sys;
		GetLocalTime(&sys);
        bus->write(ADDR_YEAR, sys.wYear - 1881);
        bus->write(ADDR_YEAR + 1, sys.wMonth - 1);
        bus->write(ADDR_YEAR + 2, sys.wDay - 1);
        bus->write(ADDR_HOUR, sys.wHour);
        bus->write(ADDR_HOUR + 1, sys.wMinute);
        bus->write(ADDR_HOUR + 2, sys.wSecond * 2);*/
    }
}

#define	TR_s		0x0
#define	TR_m		0x01
#define	TR_h		0x02
#define	TR_d		0x03
#define	TR_ms		0x04
#define	AR_s		0x05
#define	AR_m		0x06
#define	AR_h		0x07
#define	RTC_CTRL	0x0a
#define	INT_CLEAR	0x0b
// nc3000lee1.1 version, only for compare
unsigned char chk_ar0()
{
	unsigned char alm=0;
	if(!(rtc_reg[RTC_CTRL]&0x02)||!(interr_flag&0x02))
		return alm;
	if((rtc_reg[AR_h]&0x80))
		{
		alm=1;
		if((rtc_reg[AR_h]&0x1f)!=(rtc_reg[TR_h]&0x1f))
			return	0;
		}
	if((rtc_reg[AR_m]&0x80))
		{
		alm=1;
		if((rtc_reg[AR_m]&0x3f)!=(rtc_reg[TR_m]&0x3f))
			return	0;
		}
	if((rtc_reg[AR_s]&0x80))
		{
		alm=1;
		if((rtc_reg[AR_s]&0x3f)!=(rtc_reg[TR_s]&0x3f))
			return	0;
		}
	return	alm;
}

bool chk_ar(){
  uint8_t bVar1;
  uint8_t uVar2;
  
  if ((rtc_reg[10] >> 1 & 1) == 0) {
    return 0;
  }
  if (((byte)interr_flag >> 1 & 1) == 0) {
    return 0;
  }
  if ((signed char)rtc_reg[7] < (signed char)'\0') {
    if (((rtc_reg[2] ^ rtc_reg[7]) & 0x1f) != 0) {
      return 0;
    }
    uVar2 = 1;
    bVar1 = rtc_reg[6];
  }
  else {
    uVar2 = 0;
    bVar1 = rtc_reg[6];
  }
  if ((signed char)bVar1 < (signed char)'\0') {
    if (((rtc_reg[1] ^ bVar1) & 0x3f) != 0) {
      return 0;
    }
    uVar2 = 1;
    bVar1 = rtc_reg[5];
  }
  else {
    bVar1 = rtc_reg[5];
  }
  if (-1 < (signed char)bVar1) {
    return uVar2;
  }
  if (((rtc_reg[0] ^ bVar1) & 0x3f) == 0) {
    return 1;
  }
  return 0;
}
void setTimeRTC(){
	rtc_reg[0]++;
	if (rtc_reg[0] == '<') {
      rtc_reg[0] = '\0';
      rtc_reg[1] = rtc_reg[1] + '\x01';
      if (rtc_reg[1] == '<') {
        rtc_reg[1] = '\0';
        rtc_reg[2] = rtc_reg[2] + '\x01';
        if (rtc_reg[2] == '\x18') {
          rtc_reg[2] = '\0';
          rtc_reg[3] = rtc_reg[3] + '\x01';
        }
      }
    }
}
uint8_t trigger256_cnt=0;// uint8 here, will wrap back to 0
int cpu_emux_target_cycles=128*12;

bool time_adjusted;
void sync_time_2000(){
		time_t current_time = time(NULL);
		struct tm *local_time = localtime(&current_time);
		Store(0x3fa, local_time->tm_year - 103 +0x7a);
		Store(0x3fb, local_time->tm_mon);
		Store(0x3fc, local_time->tm_mday-1);
		//Store(0x3fd, local_time->tm_wday);
		rtc_reg[2]=local_time->tm_hour;
		rtc_reg[1]=local_time->tm_min;
		rtc_reg[0]=local_time->tm_sec;
}
bool soft_reset=0;
void try_soft_reset(){
	if(speed_slowdown>512){
		soft_reset=1;
		printf("soft reset!!\n");
	}
}
void debug_pc(){
	if(enable_debug_pc||enable_dyn_debug||enable_dyn_debug_next_n){
		uint8_t & Peek16Debug(uint16_t addr);
		unsigned char buf[10];
		buf[0]=Peek16Debug(cpu->PC);
		buf[1]=Peek16Debug(cpu->PC+1);
		buf[2]=Peek16Debug(cpu->PC+2);
		buf[3]=0;
		printf("tick=%lld ",tick /*, reg_pc*/);
		printf("%02x %02x %02x %02x; ",Peek16Debug(cpu->PC), Peek16Debug(cpu->PC+1),Peek16Debug(cpu->PC+2),Peek16Debug(cpu->PC+3));
		printf("bs=%02x roa_bbs=%02x ramb=%02x zp=%02x reg=%02x,%02x,%02x,%02x,%03o  pc=%s",ram_io[0x00], ram_io[0x0a], ram_io[0x0d], ram_io[0x0f],cpu->A,cpu->X,cpu->Y,cpu->SP,cpu->P,disassemble_next(buf,cpu->PC).c_str());
		printf("\n");
		if(enable_dyn_debug_next_n>0) enable_dyn_debug_next_n--;
		//getchar();
	}
}
void cpu_run3(){
	if(soft_reset){
		soft_reset=0;
		ram_io[2]=1;
		ram_io[3]|=1;
		cpu->reset();
		//nc1020_states.last_cycles=0;
		//nc1020_states.cycles=0;
		speed_slowdown=1;
	}
	//assert(cycles==cpu->getTotalCycles()/12);
	char *peeked_msg=peek_message();
	if(peeked_msg){
		bool need_wait=false;
		string cmd=split_s(string(peeked_msg), " ")[0];
		//printf("cmd is %s\n",cmd.c_str());
		if(cmd=="file_manager"||cmd=="put"||cmd=="get"||cmd=="create_dir"||cmd=="create_dir_hex"){
			need_wait=true;
			//printf("need wait!!\n");
		}
		if(!need_wait||(cpu->P&4)==0)
		{
			if(cmd=="file_manager"||cmd=="put"||cmd=="get"){
				speed_slowdown=1;
				printf("set cks to highest\n");
			}
			string msg=get_message();
			if(!msg.empty()){
				handle_cmd(msg);
			}
		}
	}
	if(nc2000mode){
		if(!time_adjusted && Peek16(0x3fa)==0x7a&&rtc_reg[0]==1){
			time_adjusted=1;
			sync_time_2000();
		}
	}
	tick++;

	bool trigger256=trigger_x_times_per_s(256);

	if(trigger256){
		if(nc1020mode||nc2000mode||nc3000mode){
			if(trigger256_cnt==0){
				if(!nc1020mode) Store(1025, 0);//prevent sleep
				//at the begin of every second
				setTimeRTC();
			}
			//bump the 1/256 second
			rtc_reg[4]=trigger256_cnt;
		}
	}

	//todo study datasheet of how speed affect timers
	uint32_t target_cycles=cpu_emux_target_cycles;
	uint32_t CpuTicks;
	if(false){
		//TODO FIX ME
		target_cycles/=speed_slowdown;
		CpuTicks=cpu->exec2(target_cycles)/12;
		CpuTicks*=speed_slowdown;
	}
	else{
		CpuTicks=cpu->exec2(target_cycles)/12;
	}
	last_cycles=cycles;
	cycles+=CpuTicks;

	//magic number to fit timer0 and timer1's code
	if(trigger_x_times_per_s(576*50)){
		//printf("trigger1!\n");
		if(nc1020mode||nc2000mode||nc3000mode){
			setTimer();
		}
		if(pc1000mode){
			bus_pc1000->setTimer();
			if (bus_pc1000->setTimer0()) {
				//timer0用于录放音，蜂鸣器音乐
				bus_pc1000->setIrqTimer0();
				//printf("irq1!\n");
				cpu->IRQ();
			}
			if (bus_pc1000->setTimer1()) {
				//timer1用于秒表的百分之一秒，每秒200次
				bus_pc1000->setIrqTimer1();
				cpu->IRQ();
				//printf("irq2!\n");
			}
		}
	}

	if(nc1020mode||nc2000mode||nc3000mode){
		bool KeepTimer01( unsigned int cpuTick);
		if(KeepTimer01(CpuTicks)){
			//adapted from wayback
			if ( ram_io[0x04] & 0x0F ) {
				//why only set flag for timer0? why not set 0x20
				//_ADD_TM1I_BIT() _ADD_TM1I_BIT() is inside KeepTimer01, why set it another time?
				//this is from wayback
				ram_io[0x01] |= 0x10;
				cpu->IRQ();
			}
		}
	}

	if(trigger_x_times_per_s(250)){
		if(nc1020mode||nc2000mode||nc3000mode){
			if (timeBaseEnable()) {
				//timebase中断为4ms一次，主要用于键盘扫描
				setIrqTimeBase();
				cpu->IRQ();
			}
		}
		if(pc1000mode){
			if (bus_pc1000->timeBaseEnable()) {
				//timebase中断为4ms一次，主要用于键盘扫描
				bus_pc1000->setIrqTimeBase();
				cpu->IRQ();
			}
		}
	}

	if(trigger256){
		if(nc1020mode||nc2000mode||nc3000mode){
			if(trigger256_cnt%128==0){
				if(trigger256_cnt==0&& chk_ar()){
					printf("chk_ar() return true!\n");
					ram_io[0x3d] = 0x10;
					interr_flag&=0xfd;	
				}else{
					if(rtc_reg[10]&1 &&interr_flag&1){
						ram_io[0x3d] =0;
						cpu->IRQ();
					}
				}
			}
			if(trigger256_cnt%128==64){ //avoid nmi triggerd at same time as rtc irq
				if (nmiEnable()){
					printf("nmi!\n");
					cpu->NMI();
				}
			}
		}
		if(nc1020mode){ //not elegant, just trick the program to pass
			if(trigger256_cnt%20==10){
					ram_io[0x0c]|=0x01;
			}
			else{
					ram_io[0x0c]&=0xfe;
			}
		}
	}

	if(pc1000mode&&trigger_x_times_per_s(2)){
		setTime1000();
		if (bus_pc1000->nmiEnable()){
			cpu->NMI();
		}
	}

	if(trigger256){
		trigger256_cnt++;
	}
}
