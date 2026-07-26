cmake_minimum_required(VERSION 3.22.1)
project(nc2000_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(NC2000_CORE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")

set(NC2000_CORE_SOURCES
    "${NC2000_CORE_ROOT}/emulator_config.cpp"
    "${NC2000_CORE_ROOT}/emulator_session.cpp"
    "${NC2000_CORE_ROOT}/nc2000.cpp"
    "${NC2000_CORE_ROOT}/disassembler.cpp"
    "${NC2000_CORE_ROOT}/cpu_loop.cpp"
    "${NC2000_CORE_ROOT}/comm.cpp"
    "${NC2000_CORE_ROOT}/mem.cpp"
    "${NC2000_CORE_ROOT}/io.cpp"
    "${NC2000_CORE_ROOT}/rom.cpp"
    "${NC2000_CORE_ROOT}/nor.cpp"
    "${NC2000_CORE_ROOT}/nand.cpp"
    "${NC2000_CORE_ROOT}/ram.cpp"
    "${NC2000_CORE_ROOT}/dsp/dsp.cpp"
    "${NC2000_CORE_ROOT}/sound.cpp"
    "${NC2000_CORE_ROOT}/cmd.cpp"
    "${NC2000_CORE_ROOT}/ansi/w65c02cpu.cpp"
    "${NC2000_CORE_ROOT}/ansi/w65c02op.cpp"
    "${NC2000_CORE_ROOT}/NekoDriverIO.cpp"
    "${NC2000_CORE_ROOT}/key_matrix.cpp"
    "${NC2000_CORE_ROOT}/compare/c6502.cpp"
    "${NC2000_CORE_ROOT}/compare/pc1000bus.cpp"
    "${NC2000_CORE_ROOT}/cpu_loop_new.cpp"
    "${NC2000_CORE_ROOT}/io_new.cpp"
    "${NC2000_CORE_ROOT}/cpu.cpp"
    "${NC2000_CORE_ROOT}/disassembler_new.cpp"
    "${NC2000_CORE_ROOT}/iv_uart.cpp"
)

add_library(nc2000_jni SHARED native_bridge.cpp ${NC2000_CORE_SOURCES})

target_include_directories(nc2000_jni PRIVATE "${NC2000_CORE_ROOT}")

target_compile_definitions(nc2000_jni PRIVATE HANDYPSP=1)

find_library(log_library log REQUIRED)
target_link_libraries(nc2000_jni PRIVATE ${log_library})

# SDL2 will live under android/third_party/SDL after it is added explicitly.
# Keeping this conditional lets the launcher/JNI scaffold build independently
# while the desktop core is being separated from its SDL window lifecycle.
set(NC2000_SDL2_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/SDL")
if(EXISTS "${NC2000_SDL2_SOURCE_DIR}/CMakeLists.txt")
    set(SDL_SHARED ON CACHE BOOL "" FORCE)
    set(SDL_STATIC OFF CACHE BOOL "" FORCE)
    set(SDL_TEST OFF CACHE BOOL "" FORCE)
    set(SDL_TESTS OFF CACHE BOOL "" FORCE)
    add_subdirectory(
        "${NC2000_SDL2_SOURCE_DIR}"
        "${CMAKE_CURRENT_BINARY_DIR}/sdl2"
        EXCLUDE_FROM_ALL
    )
    target_link_libraries(nc2000_jni PRIVATE SDL2::SDL2)
    target_compile_definitions(nc2000_jni PRIVATE NC2000_HAS_SDL2=1)
endif()
