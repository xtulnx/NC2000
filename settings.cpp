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

    if(nc2000mode){
        nc1020_rom.nandFlashPath = rom_path + ".nand";
        nc1020_rom.norFlashPath = rom_path + ".nor";
    }

}
