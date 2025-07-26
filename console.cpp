#include "comm.h"
#include "font.h"
#include "SDL.h"
#include "cmd.h"

string console_input="";


bool console_on=false;

extern uint8_t lcd_buf[SCREEN_WIDTH * SCREEN_HEIGHT / 8*2];


const int FONT_START = 0;
const int FONT_WIDTH = 8; //need to <=8
const int FONT_HEIGHT = 16;

void draw_console(){
	string to_draw = ">"+console_input;
	memset(lcd_buf,0, sizeof(lcd_buf));
	for(int i=0;i<to_draw.length();i++){
		int row_size=SCREEN_WIDTH/8;
		int line= i/row_size;
		if(line>= SCREEN_HEIGHT/(FONT_HEIGHT-1)) break; //no more space to draw
		for(int row=1;row<FONT_HEIGHT-1;row++){
			char c=to_draw[i]-FONT_START;
			lcd_buf[row_size*(row+line* (FONT_HEIGHT-1)) +i]= font8x16[c*FONT_HEIGHT+row];
		}
	}

}

void handle_console(signed int sym, bool key_down){
	if(!key_down) return;
	if(!shift_down && sym==SDLK_BACKQUOTE){
		console_on^= 0x1;
		if(console_on) SDL_StartTextInput();
		else SDL_StopTextInput();
		//printf("console %s\n", console_on ? "on" : "off");
	}
	if(!console_on) return;
	if(sym== SDLK_BACKSPACE){
		if(!console_input.empty()) {
			console_input.pop_back();
		}
	}
	if(sym==SDLK_RETURN){
		if(!console_input.empty()){
			handle_cmd(console_input);
			console_input.clear();
		}
		console_on=false;
		SDL_StopTextInput();
	}
}
