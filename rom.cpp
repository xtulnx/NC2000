#include "comm.h"

extern WqxRom nc2k_rom;

uint8_t rom_buff[24*1024*1024];

uint8_t* rom_volume0[0x100];
uint8_t* rom_volume1[0x100];
uint8_t* rom_volume2[0x100];

void LoadRom(const string romPath){
	assert(sizeof(rom_buff) >= ROM_SIZE);

	int rom_size=-1;

	if(nc1020mode){
		rom_size=ROM_SIZE;
	}
	if(pc1000mode|| nc1020tw_mode) {
		//for pc1000, becausing of remapping, the file size is not equal to sizeof(rom_buff)
		rom_size=0x8000*128*3;
	}

	uint8_t* temp_buff = (uint8_t*)malloc(rom_size);
	FILE* file = fopen(romPath.c_str(), "rb");
	if(file==0) {
        printf("file %s not exist!\n",romPath.c_str());
        exit(-1);
    }
	fread(temp_buff, 1, rom_size, file);
	ProcessBinaryLinear(rom_buff, temp_buff, rom_size);
	free(temp_buff);
	fclose(file);
}

void SaveRomHack(){
	FILE* file = fopen("tw1020/hack.rom", "wb");
	for(int j=0;j<80;j++){
		fwrite(rom_volume0[128+j], 0x8000, 1, file);
	}
		for(int j=0;j<80;j++){
		fwrite(rom_volume1[128+j], 0x8000, 1, file);
	}
		for(int j=0;j<80;j++){
		fwrite(rom_volume2[128+j], 0x8000, 1, file);
	}
	fclose(file);
}

void init_rom(){
    memset(&rom_buff,0xff,ROM_SIZE);
	LoadRom(nc2k_rom.romPath);
	if(nc1020mode){
		if(!nc1020tw_mode){
			for (uint32_t i=0; i<num_rom_pages/3; i++) {
				rom_volume0[i] = rom_buff + (0x8000 * i);
				rom_volume1[i] = rom_buff + (0x8000 * (0x100 + i));
				rom_volume2[i] = rom_buff + (0x8000 * (0x200 + i));
			}
		}
		else{
			for (int i = 0; i < 128; i++) {
				rom_volume0[i  ]=rom_volume0[i + 128 ] = rom_buff + (0x8000 * i);
				rom_volume1[i  ]=rom_volume1[i + 128 ] = rom_buff + (0x8000 * (128 + i));
				rom_volume2[i  ]=rom_volume2[i + 128 ] = rom_buff + (0x8000 * (256 + i));
			}
		}
	}


	if(pc1000mode){
		for (int i = 0; i < 128; i++) {
        	// 0~128 
        	rom_volume0[i] = (unsigned char*)rom_buff + i * 0x8000;
        	rom_volume1[i] = rom_volume0[i];

        	rom_volume0[i + 128] = (unsigned char*)rom_buff + (i + 128) * 0x8000;
        	rom_volume1[i + 128] = rom_volume0[i + 128] + 128 * 0x8000;
    	}
	}

}
