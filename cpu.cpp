#include "compare/c6502.h"
extern "C" {
#include "ansi/w65c02.h"
}
#include "comm.h"
#include "cpu.h"
#include "ram.h"

CPUInterface *cpu;

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

	if(nmiPending){
		nmiPending = false;
		cpu_impl_emux->doNMI();
	}
	do{	
		if (irqPending && (P() & 4) == 0) {
			irqPending = false;
			cpu_impl_emux->doIRQ();
		}
		void debug_pc();
		debug_pc();
		cpu_impl_emux->doCode(cpu_impl_emux->getCode());//allow one cycle anyway
	}while(clk<=max_cycles);
	
	int res=clk;// - initial_clk;
	lineclk += clk;
	total_cycles += clk;
	clk=0; //set to 0, instead of clk-= max_cycles. this the way how the new cpu loop works, this isn't abug.

	return res;
}

int CPUInterface::execute(int max_cycles){
	if(cpu_impl_emux) {
		return emux_exec_helper(max_cycles*12)/12;
	}

	//note: cycle can be non-zero, since IRQ can be called from outside 

    if(g_nmi){
        g_nmi = false;
        cycle+=CpuExecuteNMI();
		mI = true;     //todo: is this needed?
    }

    do{
		if (g_irq && !mI){
			g_irq = false;
			if(enable_dyn_debug_next_n) printf("execute irq!!!!!!!\n");
			cycle+=CpuExecuteIRQ();
			//cycle+=1;
		}
		void debug_pc();
		debug_pc();
		if(g_wai) {cycle=max_cycles;if(cycle<=0) cycle=1;break;}
		cycle += CpuExecuteOP();
    }while(cycle<=max_cycles);

	int res=cycle;
	cycle=0;
    
    return res;
}

void CPUInterface::NMI() {
	if(cpu_impl_emux) return cpu_impl_emux->NMI();

	g_nmi = true;
}

void CPUInterface::irq_now() {
	if(cpu_impl_emux) return cpu_impl_emux->IRQ();

	if(!mI) {
		cycle+=CpuExecuteIRQ();
		//cycle+=1;
		g_irq =false; // is this needed?
	}else{
		g_irq = true;
	}

}
void CPUInterface::set_irq_pending() {
	if(enable_dyn_debug_next_n) printf("set_irq_pending!!!\n");
	if(cpu_impl_emux) {
		cpu_impl_emux->irqPending = true;
		return;
	}
	g_irq = true;
}

int CPUInterface::P() {
	if(cpu_impl_emux) return cpu_impl_emux->P;

	return PS();
}

void prepare_soft_reset(){
/*
    lda io_timer0_val
    ora io_timer1_val
    beq cold_start
*/
	// tmp code
	// as long as one is non-zero, it will pass the check
	ram_io[2]=1;
	//ram_io[3]=1;
}


int invalid_op_extra_skip(int op){
	if(op==0x3){
		//nctools has this illegal opcode, if not handled, "S h" will not work.
		return 1;
	}
	return 0;
}
