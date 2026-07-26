#include "key_matrix.h"

#include "NekoDriverIO.h"
#include "comm.h"
#include "state.h"

#include <cstring>

extern nc2k_states_t nc2k_states;

void SetKey(uint8_t key_id, bool down_or_up) {
    uint8_t* keypad_matrix = nc2k_states.keypad_matrix;
    bool& slept = nc2k_states.slept;
    uint8_t& wake_up_key = nc2k_states.wake_up_flags;
    bool& should_wake_up = nc2k_states.should_wake_up;
    bool& wake_up_pending = nc2k_states.pending_wake_up;

    const uint8_t row = key_id % 8;
    const uint8_t col = key_id / 8;
    uint8_t bits = 1 << col;
    if (key_id == 0x0f) bits = 0xfe;

    uint8_t* ram_io = nc2k_states.ram_io;
    if (down_or_up) {
        keypad_matrix[row] |= bits;
        if (key_id == 0x25) ram_io[0x8] = 0x55;
        else if (key_id == 0x35) ram_io[0x8] = 0x45;
    } else {
        ram_io[0x8] = 0;
        keypad_matrix[row] &= ~bits;
    }

    if (down_or_up && slept) {
        if (key_id >= 0x08 && key_id <= 0x0f && key_id != 0x0e) {
            switch (key_id) {
            case 0x08: wake_up_key = 0x00; break;
            case 0x09: wake_up_key = 0x0a; break;
            case 0x0a: wake_up_key = 0x08; break;
            case 0x0b: wake_up_key = 0x06; break;
            case 0x0c: wake_up_key = 0x04; break;
            case 0x0d: wake_up_key = 0x02; break;
            case 0x0e: wake_up_key = 0x0c; break;
            case 0x0f: wake_up_key = 0x00; break;
            default: break;
            }
            should_wake_up = true;
            wake_up_pending = true;
            slept = false;
        }
    } else if (down_or_up && key_id == 0x0f) {
        slept = true;
    }

    // The current IO implementation scans NekoDriverIO's 8x8 matrix. Keep
    // the legacy matrix above in sync so callers can use one platform-neutral
    // key API regardless of the selected IO implementation.
    if (key_id == 0x0f && nc1020mode) {
        if (down_or_up) {
            ram_io[0x0b] &= ~1;
            void warm_reset_if_clkoff();
            warm_reset_if_clkoff();
        } else {
            ram_io[0x0b] |= 1;
        }
        return;
    }

    uint8_t matrix_row = row;
    uint8_t matrix_col = col;
    if (key_id == 0x0f && (nc2000mode || nc3000mode)) {
        matrix_row = 0;
        matrix_col = 0;
    }
    keypadmatrix[matrix_row][matrix_col] = down_or_up;

    if ((nc2000mode || nc1020mode) && matrix_col < 2 && down_or_up) {
        void warm_reset_if_clkoff();
        warm_reset_if_clkoff();
    }
}

void ResetKeys() {
    std::memset(nc2k_states.keypad_matrix, 0, sizeof(nc2k_states.keypad_matrix));
    std::memset(keypadmatrix, 0, sizeof(keypadmatrix));
}
