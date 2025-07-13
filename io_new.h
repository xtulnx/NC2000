#pragma once

extern unsigned int speed_slowdown;

int io_v2_read(int address);
void io_v2_write(int address, int value);

void setTimerA();
void setIrqTimeBase();
bool nmiEnable();
bool timeBaseEnable();
