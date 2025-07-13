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

int CPUInterface::emux_exec_helper(int max_cycles) {
	auto &clk=cpu_impl_emux->clk;
	auto &lineclk=cpu_impl_emux->lineclk;
	auto &total_cycles=cpu_impl_emux->total_cycles;
	auto &nmiPending=cpu_impl_emux->nmiPending;
	auto &irqPending=cpu_impl_emux->irqPending;

	int initial_clk = clk;

	if(nmiPending){
		nmiPending = false;
		cpu_impl_emux->doNMI();
	}
	if (irqPending && (P() & 4) == 0) {
		irqPending = false;
		cpu_impl_emux->doIRQ();
	}
	do{
		void debug_pc();
		debug_pc();
		cpu_impl_emux->doCode(cpu_impl_emux->getCode());//allow one cycle anyway
	}while(clk<max_cycles);
	
	int res=clk - initial_clk;
	lineclk += clk;
	total_cycles += clk;
	//clk=0;
	clk-=max_cycles; //if cycle remains positive, next call will compensate it
	return res;
}

int CPUInterface::execute(int max_cycles){
	if(cpu_impl_emux) {
		if(max_cycles!=0){
			//assert(cpu_impl_emux->clk < max_cycles);
		}
		return emux_exec_helper(max_cycles*12)/12;
	}

	int initial_cycle = cycle;

	if(max_cycles!=0){
		assert(cycle < max_cycles);
	}

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
