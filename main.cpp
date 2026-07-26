#include <SDL2/SDL.h>
#include "comm.h"
#include "dsp/dsp.h"
#include "nc2000.h"
#include <SDL_events.h>
#include <SDL_keycode.h>
#include <cstring>
#include <iostream>
#include <map>
#include "sound.h"
#include "udp_server.h"
#include "key.h"
#include "key_new.h"
#include "settings.h"
#include "display.h"
#include "console.h"
#include "emulator_session.h"

using namespace std;

SDL_Window* window;

bool InitAudioVideo() {
  extern SDL_Renderer* renderer;
  lcd_effect_buffer = new unsigned char[SCREEN_HEIGHT*total_size* SCREEN_WIDTH*total_size * 4];
  memset(lcd_effect_buffer, 0, SCREEN_HEIGHT*total_size* SCREEN_WIDTH*total_size * 4);

  if (SDL_Init(SDL_INIT_EVERYTHING) == -1) {
    std::cout << " Failed to initialize SDL : " << SDL_GetError() << std::endl;
    return false;
  }
  init_audio();

  window =
    SDL_CreateWindow(get_str_of_mode().c_str(), 0, 40, lcd_scale * (SCREEN_WIDTH +LEFT_GAP +RIGHT_GAP-1) *total_size +(LEFT_GAP_EXTRA+RIGHT_GAP_EXTRA)*lcd_scale, lcd_scale * SCREEN_HEIGHT *total_size, 0);
  if (!window) {
    std::cout << "Failed to create window : " << SDL_GetError() << std::endl;
    return false;
  }
  renderer = SDL_CreateRenderer(window, -1, 0);
  if (!renderer) {
    std::cout << "Failed to create renderer : " << SDL_GetError() << std::endl;
    return false;
  }

  init_lcd_stripe();//need to call after renderer is created
  
  return true;
}

bool desktop_platform_step(EmulatorSession& session, u64_t expected_tick, bool should_render) {
    SDL_Event event;
    map<signed int, bool> mp;
    bool key_pressed= false;
    
    while (SDL_PollEvent(&event)) {
      if ( event.type == SDL_QUIT ) {
        return false;
      } else if (event.type == SDL_KEYDOWN || event.type == SDL_KEYUP) {
        key_pressed = true;
        bool key_down = (event.type == SDL_KEYDOWN);
        //try to consolidate multiple key shoot into one
        //not sure if necessary. But it's helpful for debug
        mp[event.key.keysym.sym]= key_down;
        for(auto it=mp.begin();it!=mp.end();it++){
          if(it->first==SDLK_LSHIFT || it->first==SDLK_RSHIFT){
            shift_down=it->second;
            continue;
          }
          if(it->first==SDLK_LCTRL || it->first==SDLK_RCTRL){
            ctrl_down=it->second;
            continue;
          }
          bool console_on_saved=console_on;
          handle_console(it->first, it->second);// handles 1. console toggle 2. console itself
          if(console_on_saved){
            continue;
          }
          if(use_legacy_key_io) handle_key(it->first, it->second);
          else handle_key_wayback(it->first,it->second);
        }
      } else if (event.type == SDL_TEXTINPUT) {
        input_text(event.text.text);
      }
    }

    if (key_pressed) session.notify_input();
    if (should_render) {
      Render(expected_tick);
    }
    return true;
}

int main(int argc, char* args[]) {
  process_args(argc, args);
#if defined(__MINGW32__)
  SDL_SetHint(SDL_HINT_RENDER_DRIVER, "opengl");
#endif
  int res1=SDL_SetThreadPriority(SDL_THREAD_PRIORITY_TIME_CRITICAL);
  if(debug_level>=1) printf("SDL_SetThreadPriority returned %d\n", res1);

  EmulatorSession session;
  init_keyitems();
  if (!session.initialize()) return -1;

  if(listen_port>0) init_udp_server(listen_port);
  if (!InitAudioVideo())
    return -1;

  session.resume();
  while (session.run_iteration(
      [&session](u64_t expected_tick, bool should_render) {
        return desktop_platform_step(session, expected_tick, should_render);
      })) {
  }
  session.shutdown();

  shutdown_audio();

  return 0;
}
