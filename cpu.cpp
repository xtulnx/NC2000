#include "ansi/c6502.h"
extern "C" {
#include "ansi/w65c02.h"
}
#include "comm.h"
#include "cpu.h"

void CPUInterface::reset(){
	if(cpu_impl_emux) return cpu_impl_emux->reset();

	CpuInitialize();
	
}
int CPUInterface::exec2(int max_cycles){
	if(cpu_impl_emux) return cpu_impl_emux->exec2(max_cycles*12)/12;

	int initial_cycle = cycle;

	assert(cycle < max_cycles);

	//int cycle =0;
    if(g_nmi){
        g_nmi = false;
        cycle+=CpuExecuteNMI();
    }
    if (g_irq && !mI){
		g_irq = false;
        cycle+=CpuExecuteIRQ();
    }

    do{
        //void debug_pc();
        //debug_pc();
        //doCode(getCode());//allow one cycle anyway
		cycle += CpuExecuteOP();
    }while(cycle<max_cycles);

	int res=cycle - initial_cycle;

	cycle -= max_cycles; // if cycle remains positive, next call will compensate it
    
    return res;
}

void CPUInterface::NMI() {
	if(cpu_impl_emux) return cpu_impl_emux->NMI();

	g_nmi = true;
}

void CPUInterface::IRQ() {
	if(cpu_impl_emux) return cpu_impl_emux->IRQ();

	if(!mI) {
		cycle+=CpuExecuteIRQ();
	}
	g_irq = true;
}

int CPUInterface::P() {
	if(cpu_impl_emux) return cpu_impl_emux->P;

	return PS();
}
