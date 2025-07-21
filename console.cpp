#include "comm.h"

#include "nand.h"
#include "nor.h"
#include <cstdio>
#include <time.h>

#include <mutex>
#include "mem.h"
#include "ram.h"
#include "state.h"
#include "cpu.h"
extern "C" {
#include "ansi/w65c02.h"
}
#include "compare/pc1000bus.h"
#include "nc2000.h"
extern nc1020_states_t nc1020_states;
extern CPUInterface *cpu;
extern string nand_magic;

deque<string> udp_msgs;
std::mutex g_mutex;

bool is_nc2600_rom(){
	/*if(nand_magic=="ggv nc2010"){
		return true;
	}*/
	if(nand_magic[8]=='1') return true;
	return false;
}

void push_message(string msg){
	if(msg.empty()) return;
	g_mutex.lock();
	udp_msgs.push_back(msg);
	g_mutex.unlock();
}

string get_message(){
    string res;
    g_mutex.lock();
    if(!udp_msgs.empty()){
        res= udp_msgs.front();
        udp_msgs.pop_front();
        udp_msgs.clear();
    }
	g_mutex.unlock();
    return res;
}

char *peek_message(){
	char *p=NULL;
	g_mutex.lock();
    if(!udp_msgs.empty()){
        p= (char *)udp_msgs.front().c_str();
    }
	g_mutex.unlock();
    return p;
}


deque<char> queue;
int32_t dummy_io_cnt=-1;
bool dummy_io_for_read(uint16_t addr, uint8_t &value){
	if(addr!=0x3fff) return false;
	if(dummy_io_cnt== -1) return false;
	if(queue.empty()) {
		value=0;
        dummy_io_cnt=-1;
		//printf("<dummy read %02x>\n",value);
		return true;
	}
	if(dummy_io_cnt++%2==0) {
		value=1;
	}else{
		value=queue.front();
		queue.pop_front();
	}
	//printf("<dummy read %02x>\n",value);
	return true;
}

deque<char> queue_for_write;
string file_name_for_write;
int32_t dummy_io_write_cnt=-1;
bool dummy_io_for_write(uint16_t addr, uint8_t value){
	if(addr!=0x3fff) return false;

	if(dummy_io_write_cnt== -1) {
		//printf("write value=%02x pc=%04x!!\n",value,mPC);
		//printf("but dummy io is closed\n");
		return false;
	}
	if(dummy_io_write_cnt++%2==0) {
		if(value==1) {
			//printf("continue!!\n");
			//do nothing
		}
		else if(value==0) {
			printf("dummy_io_for_write closed, size=%d\n",(int)queue_for_write.size());
			if(dummy_io_write_cnt!=-1){
				auto fp=fopen(file_name_for_write.c_str(),"wb");
				for(auto c: queue_for_write){
					fwrite(&c,1,1,fp);
				}
				fclose(fp);
				queue_for_write.clear();
			}
			dummy_io_write_cnt=-1;

		}else{
			printf("got invalid value for dummy_io");
			assert(false);
		}
	}else{
		//printf("got char %02x\n",value);
		queue_for_write.push_back(value);
		if(queue_for_write.size()%10000==0) {
			printf("got %d bytes\n",(int)queue_for_write.size());
		}
	}
	//printf("<dummy read %02x>\n",value);
	return true;
}

void copy_to_addr(uint16_t addr, uint8_t * buf,uint16_t size){
	for(uint32_t i=0;i<size;i++){
		Peek16(addr+i)=buf[i];
	}
}

std::string HexToBytes(const std::string& hex) {
  std::string bytes;

  for (unsigned int i = 0; i < hex.length(); i += 2) {
    std::string byteString = hex.substr(i, 2);
    char b = (char) strtol(byteString.c_str(), NULL, 16);
    bytes.push_back(b);
  }

  return bytes;
}

void handle_cmd(string str){
	printf("handling cmd %s\n",str.c_str());
	auto cmds=split_s(str," ");
	for(int i=0;i<cmds.size();i++){
		printf("<%s>",cmds[i].c_str());
	}
	printf("\n");
	fflush(stdout);
	if(cmds.size()==0) return;
	if(cmds[0]=="warm_reset"){
		prepare_soft_reset();
		cpu->reset();
	}
	if(cmds[0]=="cold_reset"){
		memset(ram_io,0,0x40);
		cpu->reset();
	}
	if(cmds[0]=="save_flash"||cmds[0]=="save_all"||cmds[0]=="save_state"){
		string file="";
		if(cmds.size()>1){
			file=cmds[1];	
		}
		if(cmds[0]=="save_flash"||cmds[0]=="save_all"){
			save_flash(file);
		}
		if(cmds[0]=="save_state"||cmds[0]=="save_all"){
			save_state(file);
		}
		return;
	}
	if(cmds[0]=="delete_state"||cmds[0]=="del_state"){
		string file="";
		if(cmds.size()>1){
			file=cmds[1];	
		}
		delete_state(file);
		return;
	}
	if(cmds[0]=="dump"){
		uint32_t start=stoi(cmds[1],0,16);
		uint32_t size=stoi(cmds[2],0,10);
		for(uint32_t i=start;i<start+size;i++){
			printf("%02x ",Peek16(i));
		}
		printf("\n");
		return;
	}

	if(cmds[0]=="ec"){
		uint32_t start=stoi(cmds[1],0,16);
		for(uint32_t i=2;i<cmds.size();i++){
			Peek16(start++)=stoi(cmds[i],0,16);;
		}
		printf("ec done\n");
		return;
	}

	if(cmds[0]=="file_manager"||cmds[0]=="f"){
		cpu->PC=0x3000;
		/*if(nc1020mode){
			uint8_t buf[]={0x00,0x2d,0x93,0x18,0x90,0xfa};
			copy_to_addr(0x3000, buf, sizeof buf);
		}*/
		
		if(nc2000mode){
			if(is_nc2600_rom()){
				uint8_t buf[]={0x00,0x27,0x05,0x18,0x90,0xfa};
				copy_to_addr(0x3000, buf, sizeof buf);
			}else{
				uint8_t buf[]={0x00,0x28,0x05,0x18,0x90,0xfa};
				copy_to_addr(0x3000, buf, sizeof buf);
			}
		}
		if(nc3000mode){
				uint8_t buf[]={0x00,0x28,0x05,0x18,0x90,0xfa};
				copy_to_addr(0x3000, buf, sizeof buf);
		}
		return;
	}

	if(cmds[0]=="create_dir" || cmds[0]=="create_dir_hex"){
			printf("<pc=%x>\n",cpu->PC);
			cpu->PC=0x3000;
			string dir_name=cmds[1];
			if(cmds[0]=="create_dir_hex"){
				dir_name=HexToBytes(dir_name);
			}
			if(nc1020mode){
				copy_to_addr(0x121c, (uint8_t*)(dir_name+"                    ").c_str(), 16);
				Peek16(0x1219)=0x02;
				Peek16(0x121A)=0xF0; 
				Peek16(0x121B)=0xFF;
				Peek16(0x122F)=0xFF;
				Peek16(0x1230)=0xFF; 
				Peek16(0x1231)=0xFF;  
				uint8_t buf[]={0x00,0x01,0x93,0x00,0x04,0x83,0x18,0x90,0xfa};
				copy_to_addr(0x3000, buf, sizeof buf);
			}
			if(nc2000mode){
				prepare_soft_reset();
				if(is_nc2600_rom()){
					copy_to_addr(0x08d6, (uint8_t*)dir_name.c_str(), dir_name.size()+1);
					//Peek16(0x0912)=0x02; //not really useful?
					uint8_t buf[]={0x00,0x0b,0x05,0x00,0x01,0xc0};
					copy_to_addr(0x3000, buf, sizeof buf);
				}else{
					copy_to_addr(0x08be, (uint8_t*)dir_name.c_str(), dir_name.size()+1);
					uint8_t buf[]={0x00,0x0b,0x05,0x00,0x01,0xc0};
					copy_to_addr(0x3000, buf, sizeof buf);
				}
			}

			if(nc3000mode){
					copy_to_addr(0x088d, (uint8_t*)dir_name.c_str(), dir_name.size()+1);
					uint8_t buf[]={0x00,0x0b,0x05,0x00,0x28,0x05,0x18,0x90,0xfa};
					copy_to_addr(0x3000, buf, sizeof buf);	
			}
			return;
	}


	if(cmds[0]=="wqxhex"){
			vector<char> wqxhex;
			read_file("wqxhex.bin", wqxhex);
			memcpy(nc1020_states.ext_ram+0x4000, &wqxhex[0], wqxhex.size());
			ram_io[0x00]=0x80;
			ram_io[0x0a]=0x80;
			super_switch();
			cpu->PC=0x4018;
			return;
	}
	if(cmds[0]=="speed"){
			if(cmds.size()==1) speed_multiplier=1;
			else{
				sscanf(cmds[1].c_str(),"%lf",&speed_multiplier);
			}
			printf("change speed to %f\n",speed_multiplier);
			return;
	}
	if(cmds[0]=="log"){
			enable_dyn_debug=true;
			return;
	}
	if(cmds[0]=="nolog"){
			enable_dyn_debug=false;
			return;
	}
	if(cmds[0]=="get"){
			string src=cmds[1];
			string target=cmds[1];
			if(cmds.size()>2) target=cmds[2];
			file_name_for_write=target;
			dummy_io_write_cnt = 0;
			if(nc2000mode){
				if(is_nc2600_rom()){
					copy_to_addr(0x08d6, (uint8_t*)src.c_str(), src.size()+1);
					uint8_t buf[]={0xA9,0x80,0x8D,0x12,0x09,0xA9,0xEF,0x8D,0x13,0x09,0x8D,0x14,0x09,0x00,0x14,0x05,
					0xA9,0x00,0x8D,0xF6,0x03,0xA9,0x00,0x85,0xDD,0xA9,0x32,0x85,0xDE,0xA9,0x01,0x8D,
					0x0F,0x09,0xA9,0x00,0x8D,0x10,0x09,0x8D,0x11,0x09,0x00,0x15,0x05,0xAD,0x0F,0x09,
					0xF0,0x0E,0xA9,0x01,0x8D,0xFF,0x3F,0xAD,0x00,0x32,0x8D,0xFF,0x3F,0x4C,0x10,0x30,
					0xA9,0x00,0x8D,0xFF,0x3F,0x00,0x16,0x05,0x00,0x27,0x05,0x4C,0x48,0x30};
					copy_to_addr(0x3000,buf,sizeof(buf));
				}else{
					copy_to_addr(0x08be, (uint8_t*)src.c_str(), src.size()+1);
					uint8_t buf[]={0xA9,0x80,0x8D,0xFA,0x08,0xA9,0xEF,0x8D,0xFB,0x08,0x8D,0xFC,0x08,0x00,0x15,0x05,
					0xA9,0x00,0x8D,0xF6,0x03,0xA9,0x00,0x85,0xDD,0xA9,0x32,0x85,0xDE,0xA9,0x01,0x8D,
					0xF7,0x08,0xA9,0x00,0x8D,0xF8,0x08,0x8D,0xF9,0x09,0x00,0x16,0x05,0xAD,0xF7,0x08,
					0xF0,0x0E,0xA9,0x01,0x8D,0xFF,0x3F,0xAD,0x00,0x32,0x8D,0xFF,0x3F,0x4C,0x10,0x30,
					0xA9,0x00,0x8D,0xFF,0x3F,0x00,0x16,0x05,0x00,0x28,0x05,0x4C,0x48,0x30,};
					copy_to_addr(0x3000,buf,sizeof(buf));
				}
			}
			cpu->PC=0x3000;
			//enable_dyn_debug=true;
			return;
	}
	if(cmds[0]=="put"){
			vector<char> file;
			if(read_file_noexit(cmds[1], file)!=0){
				return ;
			}
			string target=split_s(cmds[1],"/").back();
			if(cmds.size()>2) target=cmds[2];
			queue.clear();
			for(int i=0;i<file.size();i++){
				queue.push_back(file[i]);
			}
			dummy_io_cnt=0;
			if(nc1020mode){
				copy_to_addr(0x121c, (uint8_t*)(target+"                    ").c_str(), 16);
				Peek16(0x1219)=0x61;
				Peek16(0x121A)=0xF8; 
				Peek16(0x121B)=0xFF;
				Peek16(0x122F)=0xFF;
				Peek16(0x1230)=0xFF; 
				Peek16(0x1231)=0xFF;  
				uint8_t buf[]={0x00,0x01,0x93,0xA9,0x00,0x8D,0xF6,0x03,0xAD,0xFF,0x3F,0xC9,0x00,0xF0,0x20,0xAD,
0xFF,0x3F,0x8D,0x00,0x32,0xA9,0x00,0x8D,0x0D,0x12,0xA9,0x32,0x8D,0x0E,0x12,0xA9,
0x01,0x8D,0x0F,0x12,0xA9,0x00,0x8D,0x10,0x12,0x00,0x05,0x93,0x4C,0x03,0x30,0x00,
0x07,0x93,0x00,0x04,0x83,0x4C,0x32,0x30,};
				copy_to_addr(0x3000, buf, sizeof buf);
			}

			if(nc2000mode){
				if(is_nc2600_rom()){
					copy_to_addr(0x08d6, (uint8_t*)target.c_str(), target.size()+1);

					/*
					for(;;){
						auto value=Load(0x3fff);
						printf("<%02x>",value);
						if(value==0) break;
						auto value2=Load(0x3fff);
						printf("<%02x>",value2);
					}
					printf("\n");*/
					prepare_soft_reset();
					uint8_t buf[]={0x00,0x1C,0x05,0xA9,0x70,0x8D,0x12,0x09,0xA9,0xEF,0x8D,0x13,0x09,0x8D,0x14,0x09,
					0x00,0x14,0x05,0xA9,0x00,0x8D,0xF6,0x03,0xAD,0xFF,0x3F,0xC9,0x00,0xF0,0x21,0xAD,
					0xFF,0x3F,0x8D,0x00,0x32,0xA9,0x00,0x85,0xDD,0xA9,0x32,0x85,0xDE,0xA9,0x01,0x8D,
					0x0F,0x09,0xA9,0x00,0x8D,0x10,0x09,0x8D,0x11,0x09,0x00,0x17,0x05,0xB8,0x50,0xD3,
					0x00,0x16,0x05,0x00,0x01,0xC0,};
					copy_to_addr(0x3000,buf,sizeof(buf));
				}else{
					copy_to_addr(0x08be, (uint8_t*)target.c_str(), target.size()+1);
					prepare_soft_reset();
					uint8_t buf[]={0x00,0x1D,0x05,0xA9,0x70,0x8D,0xFA,0x08,0xA9,0xEF,0x8D,0xFB,0x08,0x8D,0xFC,0x08,
					0x00,0x15,0x05,0xA9,0x00,0x8D,0xF6,0x03,0xAD,0xFF,0x3F,0xC9,0x00,0xF0,0x21,0xAD,
					0xFF,0x3F,0x8D,0x00,0x32,0xA9,0x00,0x85,0xDD,0xA9,0x32,0x85,0xDE,0xA9,0x01,0x8D,
					0xF7,0x08,0xA9,0x00,0x8D,0xF8,0x08,0x8D,0xF9,0x08,0x00,0x18,0x05,0xB8,0x50,0xD3,
					0x00,0x17,0x05,0x00,0x01,0xC0,};
					copy_to_addr(0x3000,buf,sizeof(buf));
				}
				
				
			}
			if(nc3000mode){
					copy_to_addr(0x088d, (uint8_t*)target.c_str(), target.size()+1);
					uint8_t buf[]={0xA9,0x70,0x8D,0xC9,0x08,0xA9,0xEF,0x8D,0xCA,0x08,0x8D,0xCB,0x08,0x00,0x15,0x05,
0xA9,0x00,0x8D,0xF6,0x03,0xAD,0xFF,0x3F,0xC9,0x00,0xF0,0x21,0xAD,0xFF,0x3F,0x8D,
0x00,0x32,0xA9,0x00,0x85,0xE0,0xA9,0x32,0x85,0xE1,0xA9,0x01,0x8D,0xC6,0x08,0xA9,
0x00,0x8D,0xC7,0x08,0x8D,0xC8,0x08,0x00,0x18,0x05,0x4C,0x10,0x30,0x00,0x17,0x05,
0x00,0x28,0x05,0x4C,0x40,0x30,};
					copy_to_addr(0x3000,buf,sizeof(buf));
			}
			cpu->PC=0x3000;

			return;
	}

	if(cmds[0]=="sync_time") {
		if(nc2000mode){
			extern void sync_time_2000();
			sync_time_2000();
		}
		return ;
	}
	printf("unknow command <%s>\n",cmds[0].c_str());
	fflush(stdout);
}


