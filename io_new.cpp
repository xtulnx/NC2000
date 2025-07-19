#include "comm.h"
#include "cpu.h"
#include "NekoDriverIO.h"
#include "nand.h"
#include "state.h"
#include "ram.h"
#include "dsp/dsp.h"
#include "mem.h"
#include "io.h"
#include <cassert>
#include "CC800IOName.h"

extern nc1020_states_t nc1020_states;
extern Dsp dsp;

static int dspData;
static bool dspTrans=0;
static bool dspSleep;

static int tmaValue;
static int tmaReload;

const int DSP_WAKEUP_FLAG = 0x80;
const int DSP_RESET_FLAG = 0x40;
const int DSP_SLEEP_FLAG = 0x80;

const int IO_TIMERA_VAL_L = 0x10;
const int IO_TIMERA_VAL_H = 0x11;
const int IO_TIMERAB_CTRL = 0x14;

const int INT_TIME_BASE = 8;

const int O_INT_ENABLE = 0x40;

unsigned int speed_scaledown=1;

static uint8_t * rtc_reg=nc1020_states.rtc_reg;
static uint8_t& interr_flag = nc1020_states.interr_flag;

static unsigned char* ioReg=nc1020_states.ram_io;

bool dsp_0xd0=0;
// dsp functions adapted from pc1000emux
int dspRetData() {
	int ret=dspData;
	if (ret==0x5a)
		dspData=0xff;
	else
		dspData=0;
	return ret;
}


int dspStat() {
    int value = 0;
    if(nc2000mode||nc3000mode){
        //value=ram_io[0x30];
        //value &=~DSP_SLEEP_FLAG;
        //value &=~0x30;
    }
    if (dspSleep)
        value |= DSP_SLEEP_FLAG;
    /*********** 
	if (!dspSleep && sound->busy())
		value |= 0x30;*/
    bool sound_busy(void);
    if(!dspSleep && sound_busy()){
        value |= 0x30;
    }
    if(pc1000mode||dspTrans||dsp_0xd0){
	    value |= 0x40;
    }
    return value;
}

void dspCmd(int cmd) {
    switch (cmd) {
        case 0x8000: //SLEEP
            dspSleep = true;
            break;
		case 0xd001:
			dspData = 0x5a;
			break;
    }
    if(cmd==0x7004){
        //enable_dyn_debug_next_n=100;
        dspTrans=true;
    }
    if(cmd==0xffff){
        dspTrans=false;
    }
}

//timerA from pc1000emux
void setTimerA() {
    int temp = ioReg[IO_TIMERAB_CTRL] >> 4;
    if (temp != 0) {
        tmaValue += (256 >> temp);
        if (tmaValue >= 0x10000) {
            tmaValue = tmaReload;
            if ((ioReg[O_INT_ENABLE] & 1) != 0)
                ioReg[io01_int_status] |= 1;
        }
    }
}

void setIrqTimeBase() {
    ioReg[io01_int_status] |= INT_TIME_BASE;
}

bool nmiEnable() {
    return (ioReg[O_INT_ENABLE] & 0x10) == 0;
}

bool timeBaseEnable() {
    //if(nc1020mode||nc2000mode||nc3000mode){
        if((ioReg[O_INT_ENABLE] & 8)) return false;
        /*
        // todo fix this
        if (this->field_0x96d4ac != '\0') {
            return true;
        }*/
        return (ioReg[io04_general_ctrl] & 0xf)!=0;
    //}
    //assert(false);
}


int io_v2_read(int address) {
    if(nc1020mode||nc2000mode||nc3000mode||pc1000mode){
        if(address==0x04) return Read04StopTimer0(address);
        if(address==0x05) return Read05StartTimer0(address);
        if(address==0x06) return Read06StopTimer1(address);
        if(address==0x07) return Read07StartTimer1(address);

        if(address==0x08){
            if(cpu->PC>=0x44c2 &&cpu->PC<=0x44c4) {
                //printf("<<pc=%04x>>\n",cpu->PC);
                extern int enable_key_debug_once;
                //enable_key_debug_once=1;
                //return 0x01;
            }
            return ReadPort0(address);
        }
        if(address==0x09){
            return ReadPort1(address);
        }
        if(address==0x18){
            return Read18Port4(address);//not important? seems like hotlink only
        }
    }
    if(nc1020mode||nc2000mode||nc3000mode){
        if(address==0x3b){
            if((ioReg[0x3d]&3)==0){
                return rtc_reg[0x3b]&0xfe;
            }else{
                return ioReg[0x3b];
            }
           //return Read3B(address);
        }
        if(address==0x3f){
            return rtc_reg[ioReg[0x3e]];
            //return Read3F(address);
        }
    }
    if(nc1020mode||pc1000mode) {
        switch(address){
            case 0x20:
                return dspStat();
            case 0x21:
                return dspRetData();
        }
    }
    if(nc3000mode){
        if(address==0x39) {
            return read_nand();
        }
        if(address==0x1e){
            return ReadPort6EXP(address);
        }
    } 
    if(nc2000mode){
        if(address==0x29) {
            return read_nand();
        }
        switch(address){
            case 0x30:
                return dspStat();
            case 0x31:
                return dspRetData();
        }
    }
    switch (address) {
        case io01_int_status://0x01
        {
            int t;
            t = ioReg[io01_int_status];
            ioReg[io01_int_status] &= 0xc0;
            return t;
        }
        default:
            return ioReg[address];
    }
}

void io_v2_write(int address, int value) {
        if(nc3000mode){
        if(address==0x05){
            uint8_t cks=value>>5;
            if (cks!=ram_io[0x05]>>5){
                //the defintion is not same as spdc1024
                switch(cks){
                    case 0: speed_scaledown=32;break;
                    case 1: speed_scaledown=4;break;
                    case 2: speed_scaledown=2;break;
                    case 3: speed_scaledown=1;break;
                    case 4: speed_scaledown=512;break;
                    case 5: speed_scaledown=256;break;
                    case 6: speed_scaledown=64;break;
                    case 7: printf("oops clk off\n");speed_scaledown=999999;break;
                    default:assert(false);
                }
                //printf("<cks=%d slowdown=%d>\n",cks,speed_slowdown);
            }
             //purposely not return
        }
        if(address==0x39) {
            return nand_write(value);
        } 
    }
    if(nc2000mode||nc1020mode||pc1000mode) {
        if(address==0x05){
            uint8_t cks=value>>5;
            if (cks!=ram_io[0x05]>>5){
                switch(cks){
                    case 0: speed_scaledown=8;break;
                    case 1: speed_scaledown=4;break;
                    case 2: speed_scaledown=2;break;
                    case 3: speed_scaledown=1;break;
                    case 4: speed_scaledown=64;break;
                    case 5: speed_scaledown=32;break;
                    case 6: speed_scaledown=16;break;
                    case 7: printf("oops clk off\n");speed_scaledown=99999;break;
                    default:assert(false);
                }
                //printf("<cks=%d slowdown=%d>\n",cks,speed_slowdown);
            }
            //purposely not return
        }
        if(address==0x29) {
            return nand_write(value);
        }

    }

    if(nc2000mode||nc3000mode){
        if(false){
            if(address==0x32) {
                printf("<w %02x>",value);
            }
            if(address==0x33){
                printf("[w %02x]\n",value);
            }
        }
        switch(address){
            case 0x30:
                //printf("dsp reset\n");
                if (value == DSP_RESET_FLAG || value == DSP_WAKEUP_FLAG) {
                    dspSleep = false;
                    dsp.reset();
                }
                return;
            case 0x33:
                ioReg[0x33] = value;
                dspCmd(ioReg[0x33] * 256 + ioReg[0x32]);
                if(dspTrans){
                    dspData = ioReg[0x32];
                }else{
                    if(value==0xd0){
                        printf("[DSP] got dsp cmd %02x %02x\n",value,ioReg[0x32]);
                        dsp_0xd0=1;
                    }else if(value >=0x60){
                        dsp_0xd0=0;
                    }
                    dsp.write(value,ioReg[0x32]);
                }
                return;
        }
    }
    if(nc1020mode||pc1000mode){
        if(false){
            if(address==0x22) {
                printf("<w %02x>",value);
            }
            if(address==0x23){
                printf("[w %02x]\n",value);
            }
        }
        switch (address) {
            case 0x20://0x20
                if (value == DSP_RESET_FLAG || value == DSP_WAKEUP_FLAG) {
                    dspSleep = false;
                    dsp.reset();
                }
                return;
            case 0x23://0x23
                ioReg[0x23] = value;
                dspCmd(ioReg[0x23] * 256 + ioReg[0x22]);
                if(dspTrans){
                    dspData = ioReg[0x22];
                }else{
                    dsp.write(value,ioReg[0x22]);
                }
                return;
        }
    }
    if(nc1020mode||nc2000mode||nc3000mode||pc1000mode){
        if(address==0x04){
            Write04GeneralCtrl(address,value);
            //Write09Port1(0x09, ram_io[0x09]);//reapply after PTYPE changed??
            return;
        }
        if(address==0x05){
            return Write05ClockCtrl(address, value);
        }
        if(address==0x06){
            return Write06LCDStartAddr(address, value);
        }
        if(address==0x07){
            return Write07PortConfig(address,value);//not important? seems like only hotlink inside
        }
        if(address==0x08){
            return Write08Port0(address, value);
        }
        if(address==0x09){
            return Write09Port1(address,value);
        }
        if(address==0x0b){
            return Write0BPort3LCDStartAddr(address,value);
        }
        if(address==0x0c){
            return Write0CTimer01Control(address,value);
        }
        if(address==0x0d){
            ioReg[0x0d] = value;
            super_switch();
            return;
        }
        if(address==0x0f){
            return Write0F(address,value);
        }
        if(address==0x15){
            return Write15Dir1(address, value);
        }
        if(address==0x18){
            return Write18Port4(address, value);
        }
        if(address==0x19){
            return Write19CkvSelect(address, value);//not important? seems like only hotlink inside
        }
        /*
        if(address==0x20){
            return Write20JG(address, value);
        }
        if(address==0x23){
            return Write23(address,value);
        }*/
    }
    if(nc1020mode||nc2000mode||nc3000mode){
        if(address==0x3d){
            ioReg[0x3d]= ioReg[0x3d] &0xf8 |value &7;
            return;
        }
        if(address==0x3f){
            int index=ioReg[0x3e];
            ioReg[0x3f]=value;
            if(index<7){
                if((signed char)rtc_reg[0x0b]<0) return;
            }else{
                if(index==10){
                    rtc_reg[10]=value;
                    interr_flag= interr_flag|value&7;
                    return;
                }
                if(index==0x0b){
                    if((value&1)==0){
                        return ;
                    }
                    ioReg[0x3d]=0xf8;
                    return;
                }
            }
            rtc_reg[index]=value;
            return;
            //return Write3F(address,value);
        }
    }
    switch (address) {
        case io00_bank_switch://0x00
            ioReg[io00_bank_switch] = value;
            super_switch();
            /////////////bankSwitch();
            return;
        case io01_int_enable://0x01
            ioReg[O_INT_ENABLE] = value;
            return;
        case io0A_bios_bsw://0x0a
            ioReg[io0A_bios_bsw] = value;
            super_switch();
            /////////////biosBankSwitch();
            /////////////bankSwitch();
            return;
        case IO_TIMERA_VAL_L://0x10
            tmaReload = (tmaReload & 0xff00) | value;
            return;
        case IO_TIMERA_VAL_H://0x11
            tmaReload = (tmaReload & 0xff) | (value << 8);
            tmaValue = tmaReload;
            return;
        default:
            ioReg[address] = value;
    }
}
