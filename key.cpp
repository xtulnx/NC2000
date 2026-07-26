#include <SDL2/SDL.h>
#include "comm.h"
#include "key_matrix.h"

static int enable_debug_key_shoot=false;

uint8_t map_key(int32_t sym){
    switch(sym){
          case SDLK_RIGHT: return 0x1F;
          case SDLK_LEFT: return 0x3F;
          case SDLK_DOWN: return 0x1B;
          case SDLK_UP: return 0x1A;

          case SDLK_RETURN: return 0x1D;
          case SDLK_SPACE: return 0x3E;
          case SDLK_PERIOD: return 0x3D;
          case SDLK_ESCAPE: return 0x3B;
          case SDLK_MINUS: return 0x0E;
          case SDLK_EQUALS: return 0x3E;

          case SDLK_LEFTBRACKET: return 0x38;
          case SDLK_RIGHTBRACKET: return 0x39;
          case SDLK_BACKSLASH: return 0x3A;

          case SDLK_COMMA: return 0x37;
          case SDLK_SLASH: return 0x1E;
          case SDLK_BACKSPACE: return 0x3F;

          case SDLK_0: return 0x3c;
          case SDLK_1: return 0x34;
          case SDLK_2: return 0x35;
          case SDLK_3: return 0x36;
          case SDLK_4: return 0x2c;
          case SDLK_5: return 0x2d;
          case SDLK_6: return 0x2e;
          case SDLK_7: return 0x24;
          case SDLK_8: return 0x25;
          case SDLK_9: return 0x26;

          case SDLK_a: return 0x28;
          case SDLK_b: return 0x34;
          case SDLK_c: return 0x32;
          case SDLK_d: return 0x2a;
          case SDLK_e: return 0x22;
          case SDLK_f: return 0x2b;
          case SDLK_g: return 0x2c;
          case SDLK_h: return 0x2d;
          case SDLK_i: return 0x27;
          case SDLK_j: return 0x2e;
          case SDLK_k: return 0x2f;
          case SDLK_l: return 0x19;
          case SDLK_m: return 0x36;
          case SDLK_n: return 0x35;
          case SDLK_o: return 0x18;
          case SDLK_p: return 0x1c;
          case SDLK_q: return 0x20;
          case SDLK_r: return 0x23;
          case SDLK_s: return 0x29;
          case SDLK_t: return 0x24;
          case SDLK_u: return 0x26;
          case SDLK_v: return 0x33;
          case SDLK_w: return 0x21;
          case SDLK_x: return 0x31;
          case SDLK_y: return 0x25;
          case SDLK_z: return 0x30;

          case SDLK_F1: return 0x10;
          case SDLK_F2: return 0x11;
          case SDLK_F3: return 0x12;
          case SDLK_F4: return 0x13;
          case SDLK_F5: return 0x0B;
          case SDLK_F6: return 0x0C;
          case SDLK_F7: return 0x0D;
          case SDLK_F8: return 0x0A;
          case SDLK_F9: return 0x09;
          case SDLK_F10: return 0x08;
          case SDLK_F11: return 0x0E;
          case SDLK_F12: return 0x0F;

          case SDLK_SEMICOLON: return 0x15;
          case SDLK_QUOTE: return 0x14;
          
          default:return 0xff;
    }

    /*note: 0x01  infra red
            0x00  on/off  (according to peek/pkoe)
            0x0f  on/off  (according to hackwaly/nc1020/NC1020_KeypadView.java) (wang-yue/NC1020/blob/master/main.cpp)
            0x02  on/off   (according to hackwaly/jswqx/src/keyinput.js)
    */
}

void handle_key(signed int sym, bool key_down){
        if(enable_debug_key_shoot){
          printf("event <%d,%d; %llu>\n", sym,key_down,(u64_t)SDL_GetTicks64()%1000);
        }
        uint8_t value=map_key(sym);
        if(value!=0xff){
          SetKey(value, key_down);
        }
        switch ( sym) {
          case SDLK_BACKQUOTE:
            if(key_down==1){
              //enable_dyn_debug^= 0x1;
            }
            break;

          case SDLK_TAB:
            if(key_down==1){
                fast_forward^= 0x1;
                printf("fast_forward %s\n", fast_forward ? "on" : "off");
            }
            break;

          default :  // unsupported
            break;
        }
}
