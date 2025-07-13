#pragma once

#include "ansi/c6502.h"
#include "comm.h"

extern "C" {
#include "ansi/w65c02.h"
}

extern double speed_multiplier;

void reset_cpu_states();
void cpu_run();
void cpu_run2();
void cpu_run3();

void init_emux_cpu_with_dummy_bus();
void init_emux_cpu_and_bus();

class CPUInterface{
public:
	int &A;
    int &X;
    int &Y;
    //int &P;
    int &SP;
    int &PC;

    CPUInterface():A(mA), X(mX), Y(mY), SP(mSP), PC(mPC) {
        //no need, already initialized from outside
        //CpuInitialize();
    };
    CPUInterface(C6502 *cpu) :A(cpu->A), X(cpu->X), Y(cpu->Y), SP(cpu->SP), PC(cpu->PC) {
        cpu_impl_emux = cpu;
    };

    void reset();
    int exec2(int max_cycles);
    void NMI();
	void IRQ();
    int P();

    //only for emux cpu
    C6502 *cpu_impl_emux = NULL;

    //only for w65c02
    int cycle=0;
};
