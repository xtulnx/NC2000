#pragma once

extern unsigned int speed_scaledown;

int io_v2_read(int address);
void io_v2_write(int address, int value);

bool setTimerA();
void setIrqTimeBase();
bool nmiEnable();
bool timeBaseEnable();

void io_warm_reset();
void io_cold_reset();
