#include <getopt.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <string>
#include "comm.h"
using namespace std;
extern WqxRom nc1020_rom;
void print_help(){
    printf("help page:\n");
    printf(" nc2000/2600 simulator\n");
    printf(" TODO");
}
static string rom_path;
int listen_port=9000;
void process_args(int argc, char *argv[])
{
	int i, j, k;
	int opt;
    static struct option long_options[] =
      {
		{"port", required_argument, 0, 1},
		{"cpu", required_argument, 0, 1},
		{"loop", required_argument, 0, 1},
		{"io", required_argument, 0, 1},
		{"oc", required_argument, 0, 1},
		{"nor-read", required_argument, 0, 1},
		{"nor-write", required_argument, 0, 1},
		{"nc1020", no_argument,    0, 1},
        {"pc1000", no_argument,    0, 1},
        {"nc2000", no_argument,    0, 1},
        {"nc3000", no_argument,    0, 1},
        {"rom", required_argument, 0, 1},
		{NULL, 0, 0, 0}
      };
    int option_index = 0;
	if (argc == 1)
	{
        printf("no argument provided\n");
	}
	for (i = 0; i < argc; i++)
	{
		if(strcmp(argv[i],"-h")==0||strcmp(argv[i],"--help")==0)
		{
			print_help();
			exit(0);
		}
	}

	int no_l = 1, no_r = 1;
	while ((opt = getopt_long(argc, argv, "l:r:tuh:",long_options,&option_index)) != -1)
	{
		switch (opt)
		{
		case 'l':
			no_l = 0;
			//local_addr.from_str(optarg);
			break;
		case 'r':
			no_r = 0;
			//remote_addr.from_str(optarg);
			break;
		case 't':
			//enable_tcp=1;
			break;
		case 'u':
			//enable_udp=1;
			break;
		case 'h':
			break;
		case 1:
			if(strcmp(long_options[option_index].name,"rom")==0)
			{
                rom_path = optarg;
			}
			else if(strcmp(long_options[option_index].name,"port")==0)
			{
                listen_port = atoi(optarg);
			}
			else if(strcmp(long_options[option_index].name,"nc1020")==0)
			{
				nc1020mode = true;
			}
			else if(strcmp(long_options[option_index].name,"pc1000")==0)
			{
				pc1000mode = true;
			}
			else if(strcmp(long_options[option_index].name,"nc2000")==0)
			{
				nc2000mode = true;
			}
			else if(strcmp(long_options[option_index].name,"nc3000")==0)
			{
				nc3000mode = true;
			}
			else if(strcmp(long_options[option_index].name,"cpu")==0)
			{
				cpu_version = (CpuVersion)stoi(optarg);
			}
			else if(strcmp(long_options[option_index].name,"loop")==0)
			{
				cpu_loop_version = (CpuLoopVersion)stoi(optarg);
			}
			else if(strcmp(long_options[option_index].name,"nor-read")==0)
			{
				nor_read_format = (NorFormat)stoi(optarg);
			}
			else if(strcmp(long_options[option_index].name,"nor-write")==0)
			{
				nor_write_format = (NorFormat)stoi(optarg);
			}
			else if(strcmp(long_options[option_index].name,"io")==0)
			{
				io_version = (IoVersion)stoi(optarg);
			}
			else if(strcmp(long_options[option_index].name,"oc")==0)
			{
				oc_factor = stod(optarg);
			}
			else
			{
				printf("unknown option\n");
				exit(-1);
			}
			break;
		default:
			printf("unknown option <%x>", opt);
			exit(-1);
		}
	}

	int mode_cnt=0;
	mode_cnt+= nc1020mode;
	mode_cnt+= pc1000mode;
	mode_cnt+= nc2000mode;
	mode_cnt+= nc3000mode;
	if(mode_cnt==0){
		printf("no mode specified, default to nc2000\n");
		nc2000mode = true;
	}
	if(mode_cnt>1){
		printf("only one of --nc1020, --pc1000, --nc2000, --nc3000 can be specified\n");
		exit(-1);
	}

	if(nc2000mode){
		if(rom_path.empty()){
			rom_path = "roms/nc2000";
		}
  	    nc1020_rom.nandFlashPath = rom_path + ".nand";
		nc1020_rom.nand0Path = rom_path + ".nand0";
        nc1020_rom.norFlashPath = rom_path + ".nor";
    }
	if(nc1020mode){
		if(rom_path.empty()){
			rom_path = "roms/nc1020";
		}
		nc1020_rom.romPath = rom_path + ".rom";
		nc1020_rom.norFlashPath = rom_path + ".nor";
	}
	if(pc1000mode){
		if(rom_path.empty()){
			rom_path = "roms/pc1000";
		}
		nc1020_rom.romPath = rom_path + ".rom";
		nc1020_rom.norFlashPath = rom_path + ".nor";
	}

	if(nc3000mode){
		if(rom_path.empty()){
			rom_path = "roms/nc3000";
		}
		nc1020_rom.nand0Path = rom_path + ".nand0";
		nc1020_rom.nandFlashPath = rom_path + ".nand";
		nc1020_rom.norFlashPath = rom_path + ".nor";
	}

}
