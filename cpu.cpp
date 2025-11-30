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
	static unsigned char mp[256];
	bool initialized = false;
	if(!initialized){
		memset(mp,0,sizeof(mp));
		initialized = true;

		//03 fixes nctools (all version) "S h"
		//13 and 1f fixes nctools 4.0's "制作应用程序" and "系统信息->文件列表"

		// below value from https://www.masswerk.at/6502/6502_instruction_set.html

		//ALR(asr)
		mp[0x4b]=2;

		//ANC
		mp[0x0b]=2;

		//ANC(ANC2)
		mp[0x2b]=2;

		//ANE(XAA)
		mp[0x8b]=2;

		//ARR
		mp[0x6b]=2;

		//DCP(DCM)
		mp[0xc7]=2;
		mp[0xd7]=2;
		mp[0xcf]=3;
		mp[0xdf]=3;
		mp[0xdb]=3;
		mp[0xc3]=2;
		mp[0xd3]=2;

		//ISC(ISB,INS)
		mp[0xe7]=2;
		mp[0xf7]=2;
		mp[0xef]=3;
		mp[0xff]=3;
		mp[0xfb]=3;
		mp[0xe3]=2;
		mp[0xf3]=2;

		//LAS (LAR)
		mp[0xbb]=3;

		//LAX
		mp[0xa7]=2;
		mp[0xb7]=2;
		mp[0xaf]=3;
		mp[0xbf]=3;
		mp[0xa3]=2;
		mp[0xb3]=2;

		//LXA(LAX immediate)
		mp[0xab]=2;

		//RLA
		mp[0x27]=2;
		mp[0x37]=2;
		mp[0x2f]=3;
		mp[0x3f]=3;
		mp[0x3b]=3;
		mp[0x23]=2;
		mp[0x33]=2;

		//RRA
		mp[0x67]=2;
		mp[0x77]=2;
		mp[0x6f]=3;
		mp[0x7f]=3;
		mp[0x7b]=3;
		mp[0x63]=2;
		mp[0x73]=2;

		//SAX(AXS,AAX)
		mp[0x87]=2;
		mp[0x97]=2;
		mp[0x8f]=3;
		mp[0x83]=2;

		//SBX(AXS,SAX)
		mp[0xcb]=2;

		//SHA(AHX,AXA)
		mp[0x9f]=3;
		mp[0x93]=2;

		//SHX(A11,SXA,XAS)
		mp[0x9e]=3;

		//SHY(A11,SYA,SAY)
		mp[0x9c]=3;

		//SLO(ASO)
		mp[0x07]=2;
		mp[0x17]=2;
		mp[0x0f]=3;
		mp[0x1f]=3;
		mp[0x1b]=3;
		mp[0x03]=2;
		mp[0x13]=2;

		//SRE(LSE)
		mp[0x47]=2;
		mp[0x57]=2;
		mp[0x4f]=3;
		mp[0x5f]=3;
		mp[0x5b]=3;
		mp[0x43]=2;
		mp[0x53]=2;

		//TAS(XAS,SHS)
		mp[0x9b]=3;

		//USBC(SBC)
		mp[0xeb]=2;

		//NOPS(including DOP, TOP)
		mp[0x1a]=1;
		mp[0x3a]=1;
		mp[0x5a]=1;
		mp[0x7a]=1;
		mp[0xda]=1;
		mp[0xfa]=1;	
		mp[0x80]=2;
		mp[0x82]=2;
		mp[0x89]=2;
		mp[0xc2]=2;
		mp[0xe2]=2;
		mp[0x04]=2;
		mp[0x44]=2;
		mp[0x64]=2;
		mp[0x14]=2;
		mp[0x34]=2;
		mp[0x54]=2;
		mp[0x74]=2;
		mp[0xd4]=2;
		mp[0xf4]=2;
		mp[0x0c]=3;
		mp[0x1c]=3;
		mp[0x3c]=3;
		mp[0x5c]=3;
		mp[0x7c]=3;
		mp[0xdc]=3;
		mp[0xfc]=3;

		//JAM (KIL,HLT)
		mp[0x02]=1;
		mp[0x12]=1;
		mp[0x22]=1;
		mp[0x32]=1;
		mp[0x42]=1;
		mp[0x52]=1;
		mp[0x62]=1;
		mp[0x72]=1;
		mp[0x92]=1;
		mp[0xb2]=1;
		mp[0xd2]=1;
		mp[0xf2]=1;
	}
	if(mp[op]==0){
		if(debug_level>=1) printf("invalid op %02x, but not know how to skip extra bytes\n",op);
		return 0;
	}
	if(debug_level>=1) printf("skipped extra %d bytes for invalid op %02x\n",mp[op]-1,op);
	return mp[op]-1;
}
