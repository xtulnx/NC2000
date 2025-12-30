#include "comm.h"
#include <cassert>
#include <cstdint>
#include <set>
#include "iv_uart.h"
#include "state.h"
#include <libserialport.h>
using namespace std;

extern nc2k_states_t nc2k_states;
static uint8_t * ram_io=nc2k_states.ram_io;
static uint8_t * ext_reg=nc2k_states.ext_reg;

static uint8_t bk=0;

uint8_t &RCR0=ext_reg[0x0a];
uint8_t &RCR1=ext_reg[0x0b];

set<uint8_t> iv_set;
void iv_uart_reset(){
    iv_set.clear();
    RCR0=0;
    RCR1=0;
}
void put_iv(uint8_t value){
    assert(value!=IV_NONE);
    iv_set.insert(value);
}
void del_iv(uint8_t value){
    assert(value!=IV_NONE);
    iv_set.erase(value);
}
uint8_t peek_iv(){
    if(iv_set.empty()) return IV_NONE;
    return *iv_set.begin();
}
uint8_t get_iv(){
    if(iv_set.empty()) return IV_NONE;
    uint8_t value=*iv_set.begin();
    iv_set.erase(iv_set.begin());
    return value;
}

uint8_t read_rcr0(){
    return RCR0;
}
void write_rcr0(uint8_t value){
    RCR0=value;
}

uint8_t read_rcr1(){
    return RCR1;
}
void write_rcr1(uint8_t value){
    if(value & RCR1_ALARM) del_iv(IV_ALARM);
    if(value & RCR1_2HZ) del_iv(IV_2HZ);
    if(value & RCR1_SAMPLE) del_iv(IV_SAMPLE);
    //the 3 low bits are self clear
    RCR1= value&0xf8;
}

uint8_t read_3a(){
    return ram_io[0x3a];
}
void write_3a(uint8_t value){
    ram_io[0x3a]=value;
}

uint8_t read_3b(){
    if((ram_io[0x3d]&3)==0){
        return ext_reg[0x3b]&0xfe;
    }else{
        return ram_io[0x3b];
    }
}
void write_3b(uint8_t value){
    ram_io[0x3b]=value;
}

uint8_t read_3c(){
    return ram_io[0x3c];
}
void write_3c(uint8_t value){
    ram_io[0x3c]=value;
}

uint8_t read_3d(){
    if(bk==0) return get_iv()<<3;
    return ram_io[0x3d];
}
void write_3d(uint8_t value){
    ram_io[0x3d]= ram_io[0x3d] &0xf8 |value &7;
    bk=value &7;
}
