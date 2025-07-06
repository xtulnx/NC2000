#pragma once

#include "comm.h"


extern double speed_multiplier;

void reset_cpu_states();
void cpu_run();
void cpu_run2();
void cpu_run3();

void init_emux_cpu_with_dummy_bus();
void init_emux_cpu_and_bus();

