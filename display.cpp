#include <SDL2/SDL.h>
#include "comm.h"
#include "nc2000.h"
#include "lcdstripe/lcdpainter.h"

SDL_Renderer* renderer;

const bool simulate_lcd_delay=true;

uint8_t lcd_buf[SCREEN_WIDTH * SCREEN_HEIGHT / 8*2];
unsigned char p[80*total_size][160*total_size][4] ;
MyLCDView*  lcdview;

void init_lcd_stripe(){
   lcdview = new MyLCDView("lcdstripe_slice_w1313.json");
   lcdview->loadStripeTexture("lcdstripe_w1313.bmp", renderer);
}

inline void handle_pixel(int u,int v,const unsigned char * color_arr[], int idx){
    if(!simulate_lcd_delay){
        memcpy(p[u][v], color_arr[idx], 4);
    }else{
      for(int i=1;i<4;i++){
        if(color_arr[idx][i]>p[u][v][i]){
          unsigned char delta=color_arr[idx][i]-p[u][v][i];
          //delta=delta*1/8;
          delta>>=3;
          if(delta==0) delta++;
          p[u][v][i]+=delta;
            /*int tmp=p[u][v][i]+30;
            if(tmp >color_arr[idx][i]) tmp=color_arr[idx][i];
            p[u][v][i]=tmp;*/
        }
        else if( color_arr[idx][i]<p[u][v][i] ){
          unsigned char delta=p[u][v][i]- color_arr[idx][i];
          //delta=delta*1/4;
          delta>>=2;
          if(delta==0) delta++;
          p[u][v][i]-=delta;
            /*int tmp=p[u][v][i]-100;
            if(tmp <color_arr[idx][i]) tmp=color_arr[idx][i];
            p[u][v][i]=tmp;*/
        }else{
        }
      }
    }
}
void Render() {
  if (!CopyLcdBuffer(lcd_buf)) {
    std::cout << "Failed to copy buffer renderer." << std::endl;
  }
  SDL_RenderSetLogicalSize(renderer, lcdview->getLCDWidth(), lcdview->getLCDHeight());
  lcdview->paint(renderer, true);

  SDL_RenderSetLogicalSize(renderer, (SCREEN_WIDTH +LEFT_GAP +RIGHT_GAP-1) * LINE_SIZE *total_size, SCREEN_HEIGHT * LINE_SIZE *total_size);
  //SDL_RenderClear(renderer);
  SDL_Texture *texture = SDL_CreateTexture(renderer, SDL_PIXELFORMAT_RGBA8888,
    SDL_TEXTUREACCESS_STREAMING, SCREEN_WIDTH*total_size, SCREEN_HEIGHT*total_size);

  unsigned char* bytes = nullptr;
  int pitch = 0;
  static const SDL_Rect source = { 0, 0, SCREEN_WIDTH*total_size, SCREEN_HEIGHT*total_size };
  SDL_LockTexture(texture, &source, reinterpret_cast<void**>(&bytes), &pitch);
  
  static const unsigned char colors[4]={245,180,105,0};
  static const unsigned char shadows[4]={255,
        (unsigned char)(colors[1]+(255-colors[1])/8),
        (unsigned char)(colors[2]+(255-colors[2])/4),
        (unsigned char)(colors[3]+(255-colors[3])/2)
        };

  static const unsigned char white_color[4] = { 0, colors[0], colors[0], colors[0] };
  static const unsigned char near_white_color[4] = { 0, colors[1], colors[1], colors[1] };
  static const unsigned char near_black_color[4] = { 0, colors[2], colors[2], colors[2] };
  static const unsigned char black_color[4] = { 0, colors[3], colors[3], colors[3] };

  static const unsigned char white_color_shadow[4] = { 0, shadows[0], shadows[0], shadows[0] };
  static const unsigned char near_white_color_shadow[4] = { 0, shadows[1], shadows[1], shadows[1]  };
  static const unsigned char near_black_color_shadow[4] = { 0, shadows[2], shadows[2], shadows[2]  };
  static const unsigned char black_color_shadow[4] = { 0, shadows[3], shadows[3], shadows[3] };

  static const unsigned char * index[4]={white_color,near_white_color,near_black_color,black_color};
  static const unsigned char * index_shadow[4]={white_color_shadow, near_white_color_shadow, near_black_color_shadow, black_color_shadow};
  static const size_t color_size = sizeof(black_color);
  //unsigned char lcd[80*(pixel_size+gap_zize)][160*(pixel_size+gap_zize)][color_size] ;
  //unsigned char lcd[80*(pixel_size+gap_zize)][160*(pixel_size+gap_zize)][color_size] ;

  //p=(unsigned char (*)[160*total_size][color_size] ) bytes;
  if(!is_grey_mode()){
    for (int i = 0; i < SCREEN_WIDTH * SCREEN_HEIGHT/8; ++i) {
      for (int j = 0; j < 8; ++j) {
        bool pixel = (lcd_buf[i] & (1 << (7 - j))) != 0;
        int pos=i*8+j;
        int value= pixel? 3:0;
        int r=pos/160;
        int c=pos%160;
        if(c==0){
          lcdview->setPixel(0, r, value );
        }
        for(int u=r*total_size;u<r*total_size+total_size;u++){
          for(int v=c*total_size;v<c*total_size+total_size;v++){
              if(u-r*total_size<pixel_size && v-c*total_size<pixel_size){
                handle_pixel(u,v,index,value);
              }/*else if (u==r*total_size &&  v-c*total_size>=pixel_size || v==c*total_size && u-r*total_size>=pixel_size){
                memcpy(p[u][v],index[0],color_size);  
              }*/
              else{
                handle_pixel(u,v,index_shadow,value);
                /*unsigned char tmp[4];
                memcpy(tmp,index[value],color_size);
                tmp[1]+=(255-tmp[1])/2;tmp[2]+=(255-tmp[2])/2;tmp[3]+=(255-tmp[3])/2;
                memcpy(p[u][v],tmp,color_size);*/
              }
          }
        }
        //memcpy(bytes, pixel ? black_color : white_color, color_size);
        //bytes += color_size;
      }
    }
  }else{
    for (int i = 0; i < SCREEN_WIDTH * SCREEN_HEIGHT/8 *2; ++i) {
      for (int j = 0; j < 4; ++j) {
        uint8_t value=(lcd_buf[i]>>(6-j*2)) &0x03;
        int pos=(i*8+j*2)/2;
        int r=pos/160;
        int c=pos%160;
        if(c==0){
          lcdview->setPixel(0, r, value );
        }
        //memcpy(p[r][c],index[value],color_size);
        for(int u=r*total_size;u<r*total_size+total_size;u++){
          for(int v=c*total_size;v<c*total_size+total_size;v++){
              if(u-r*total_size<pixel_size && v-c*total_size<pixel_size){
                handle_pixel(u,v,index,value);    
              }/*else if (u==r*total_size &&  v-c*total_size>=pixel_size || v==c*total_size && u-r*total_size>=pixel_size){
                memcpy(p[u][v],index[0],color_size);  
              }*/
              else{
                handle_pixel(u,v,index_shadow,value);
                /*unsigned char tmp[4];
                memcpy(tmp,index[value],color_size);
                tmp[1]+=(255-tmp[1])/2;tmp[2]+=(255-tmp[2])/2;tmp[3]+=(255-tmp[3])/2;
                memcpy(p[u][v],tmp,color_size);*/
              }
          }
        }
      }
    }
  }
  memcpy(bytes,p,sizeof(p));
  /*
  for(int i=0;i<80;i++){
    for(int j=0;j<160;j++){
      memcpy(bytes, index[lcd[i][j]],color_size);
      bytes+=color_size;
    }
  }*/
  SDL_UnlockTexture(texture);

  static const SDL_Rect source2 = { 1*total_size, 0, (SCREEN_WIDTH-1)*total_size, SCREEN_HEIGHT*total_size };
  static const SDL_Rect destination =
    { LEFT_GAP* LINE_SIZE *total_size, 0, (SCREEN_WIDTH -1)* LINE_SIZE *total_size, SCREEN_HEIGHT * LINE_SIZE *total_size };
  SDL_RenderCopy(renderer, texture, &source2, &destination);
  SDL_RenderPresent(renderer);
  SDL_DestroyTexture(texture);
}
