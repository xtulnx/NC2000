#pragma once

#include "comm.h"
#include "key_matrix.h"

/*
===================
emulator api
===================
*/
//void Initialize();
//void Reset();
void RunTimeSlice(uint32_t);
bool CopyLcdBuffer(uint8_t*);
void LoadNC2k();
//void SaveNC1020();
void save_flash(string file);

void save_state(string file_name);
void delete_state(string file_name);
void load_state();

bool is_grey_mode();
