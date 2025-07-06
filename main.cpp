#include <SDL2/SDL.h>
#include "comm.h"
#include "dsp/dsp.h"
#include "nc2000.h"
#include <SDL_keycode.h>
#include <cstring>
#include <iostream>
#include <map>
#include "sound.h"
#include "udp_server.h"
#include "key.h"
#include "wayback_key.h"
#include "settings.h"
#include "display.h"

using namespace std;

extern SDL_Renderer* renderer;

bool fast_forward=false;

bool InitEverything() {
  if (SDL_Init(SDL_INIT_EVERYTHING) == -1) {
    std::cout << " Failed to initialize SDL : " << SDL_GetError() << std::endl;
    return false;
  }
  init_audio();

  SDL_Window* window =
    SDL_CreateWindow("WQX", 0, 40, LINE_SIZE * SCREEN_WIDTH *total_size, LINE_SIZE * SCREEN_HEIGHT *total_size, 0);
  if (!window) {
    std::cout << "Failed to create window : " << SDL_GetError() << std::endl;
    return false;
  }
  renderer = SDL_CreateRenderer(window, -1, 0);
  if (!renderer) {
    std::cout << "Failed to create renderer : " << SDL_GetError() << std::endl;
    return false;
  }
  SDL_RenderSetLogicalSize(renderer, SCREEN_WIDTH * LINE_SIZE *total_size, SCREEN_HEIGHT * LINE_SIZE *total_size);

  LoadNC1020();
  
  return true;
}

void main_loop() {
  bool loop = true;
  bool power_save= false;

  uint64_t start_tick = SDL_GetTicks64();
  uint64_t expected_tick = 0;

  uint64_t last_key_pressed_tick = 0;

  while (loop) {
    if(power_save) {
      SDL_Delay(200);
    }
    if(! power_save){
      RunTimeSlice(SLICE_INTERVAL, false);
    }

    SDL_Event event;
    map<signed int, bool> mp;
    bool key_pressed= false;
    while (SDL_PollEvent(&event)) {
      if ( event.type == SDL_QUIT ) {
        loop = false;
      } else if (event.type == SDL_KEYDOWN || event.type == SDL_KEYUP) {
        key_pressed = true;
        bool key_down = (event.type == SDL_KEYDOWN);
        //try to consolidate multiple key shoot into one
        //not sure if necessary. But it's helpful for debug
        mp[event.key.keysym.sym]= key_down;
        for(auto it=mp.begin();it!=mp.end();it++){
          if(use_legacy_key_io) handle_key(it->first, it->second);
          else handle_key_wayback(it->first,it->second);
        }
      }
    }

    if(expected_tick/LCD_REFRESH_INTERVAL != (expected_tick+SLICE_INTERVAL)/LCD_REFRESH_INTERVAL){
      if(!power_save){
        Render();
      }
    }

    uint64_t current_time = SDL_GetTicks64();
    if (key_pressed) {
      last_key_pressed_tick = current_time;
    }

    if(current_time - last_key_pressed_tick >1200*1000){
      if(power_save == false){
        power_save = true;
        printf("enter power save\n");
      }
    }else{
      if(power_save == true) {
        power_save = false;
        printf("get out of power save\n");
      }
    }

    expected_tick+=SLICE_INTERVAL;
    uint64_t actual_tick= current_time - start_tick;

  if(fast_forward) {
      expected_tick =actual_tick;
  }

  //if actual is behind expected_tick too much, we only remember 300ms
  if(actual_tick >expected_tick + 300) {
    expected_tick = actual_tick-300;
  }

  // similiar strategy as above
  if(expected_tick > actual_tick + 300) {
    actual_tick = expected_tick-300;
  }

  if(actual_tick < expected_tick) {
    {SDL_Delay(expected_tick-actual_tick);}
    long long exceed=current_time -start_tick  -expected_tick;
    if(exceed>10){
      printf("oops sleep too much %lld\n",exceed);
    }
  }

  /*while((actual_tick=SDL_GetTicks64() - start_tick) <expected_tick) {
    //{SDL_Delay(expected_tick-actual_tick);}
  }*/
    //SDL_Delay(FRAME_INTERVAL < tick ? 0 : FRAME_INTERVAL - tick);
  }
}

int main(int argc, char* args[]) {
  process_args(argc, args);
  init_parameters();
  if(listen_port>0) init_udp_server(listen_port);
  init_keyitems();
  if (!InitEverything())
    return -1;
  
  //SDL_SetThreadPriority(SDL_THREAD_PRIORITY_HIGH);
  //SDL_SetThreadPriority(SDL_THREAD_PRIORITY_TIME_CRITICAL);
  main_loop();
  if(false){
    //SaveNC1020();
  }

  return 0;
}
