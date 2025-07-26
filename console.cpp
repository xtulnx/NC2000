#include "comm.h"
#include "font.h"

string console_input=">zonglei123456789asdfghjklqwertyuiop";


bool console_on=false;

extern uint8_t lcd_buf[SCREEN_WIDTH * SCREEN_HEIGHT / 8*2];


const int FONT_START = 0;
const int FONT_WIDTH = 8; //need to <=8
const int FONT_HEIGHT = 16;

void draw_console(){
	memset(lcd_buf,0, sizeof(lcd_buf));
	for(int i=0;i<console_input.length();i++){
		int row_size=SCREEN_WIDTH/8;
		int line= i/row_size;
		for(int row=0;row<FONT_HEIGHT;row++){
			char c=console_input[i]-FONT_START;
			lcd_buf[row_size*(row+line* FONT_HEIGHT) +i]= font8x16[c*FONT_HEIGHT+row];
		}
	}

}
