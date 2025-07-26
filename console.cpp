#include "comm.h"
#include "font.h"
#include "SDL.h"
#include "cmd.h"
#include <SDL_keycode.h>

string promot=">";
string console_input="";


bool console_on=false;
int cursor=0;

extern uint8_t lcd_buf[SCREEN_WIDTH * SCREEN_HEIGHT / 8*2];


const int FONT_START = 0;
const int FONT_WIDTH = 8; //need to <=8
const int FONT_HEIGHT = 16;

const int DRAW_HEIGHT_START = 1;
const int DRAW_HEIGHT = FONT_HEIGHT-2;

void draw_console(){
	string to_draw = ">"+console_input+" ";
	memset(lcd_buf,0, sizeof(lcd_buf));
	for(int i=0;i<to_draw.length();i++){
		int row_size=SCREEN_WIDTH/8;
		int line= i/row_size;
		if(line>= SCREEN_HEIGHT/(DRAW_HEIGHT)) break; //no more space to draw
		for(int row=DRAW_HEIGHT_START;row-DRAW_HEIGHT_START<DRAW_HEIGHT;row++){
			char c=to_draw[i]-FONT_START;
			lcd_buf[row_size*(row+line* (DRAW_HEIGHT)) +i]= font8x16[c*FONT_HEIGHT+row];
			if(row-DRAW_HEIGHT_START == DRAW_HEIGHT -1){
				if(i==cursor+promot.length()){
					lcd_buf[row_size*(row+line* (DRAW_HEIGHT)) +i]^=0xff; //set the last bit to 1
				}
			}
		}
	}

}

void input_text(string a){
	if(console_on && !(a[0]=='`')){
		console_input.insert(cursor,a);
		cursor+= a.length();
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
			if(cursor>0){
				console_input.erase(cursor-1);
				cursor--;
			}
		}
	}
	if(sym==SDLK_LEFT){
		cursor--;
		if(shift_down) cursor-=9;
		if(cursor<0) cursor=0;
	}
	if(sym==SDLK_RIGHT){
		cursor++;
		if(shift_down) cursor+=9;
		if(cursor>console_input.length()) cursor=console_input.length();

	}
	if(sym==SDLK_ESCAPE){
		console_input.clear();
		cursor=0;
		console_on=false;
		SDL_StopTextInput();	
	}
	if(sym==SDLK_RETURN){
		if(!console_input.empty()){
			handle_cmd(console_input);
			console_input.clear();
			cursor=0;
		}
		console_on=false;
		SDL_StopTextInput();
	}
}
