#include "ansi/c6502.h"
#include "comm.h"
#include "cpu.h"

void CPUInterface::reset(){
	cpu_impl_emux->reset();
}
int CPUInterface::exec2(int max_cycles){
	return cpu_impl_emux->exec2(max_cycles);
}
void CPUInterface::NMI() {
	return cpu_impl_emux->NMI();
}

void CPUInterface::IRQ() {
	return cpu_impl_emux->IRQ();
}
