#include "emulator_session.h"

#include "comm.h"
#include "nc2000.h"

#include <chrono>
#include <cstdio>
#include <thread>

namespace {

uint64_t steady_time_ms() {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch())
            .count());
}

uint64_t wall_time_ms() {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch())
            .count());
}

}  // namespace

EmulatorSession::EmulatorSession()
    : state_(EmulatorSessionState::Stopped),
      stop_requested_(false),
      start_tick_ms_(0),
      expected_tick_ms_(0),
      last_input_tick_ms_(0),
      last_wall_time_ms_(0),
      has_started_(false) {}

bool EmulatorSession::initialize() {
    if (state_.load() != EmulatorSessionState::Stopped) return false;

    init_parameters();
    LoadNC2k();

    stop_requested_.store(false);
    has_started_ = false;
    reset_timing();
    state_.store(EmulatorSessionState::Paused);
    return true;
}

void EmulatorSession::resume() {
    if (state_.load() == EmulatorSessionState::Stopped) return;
    reset_timing();
    if (has_started_ && enable_auto_time_sync && sync_on_resume) sync_emulator_clock();
    has_started_ = true;
    state_.store(EmulatorSessionState::Running);
}

void EmulatorSession::pause() {
    const EmulatorSessionState current = state_.load();
    if (current == EmulatorSessionState::Running ||
        current == EmulatorSessionState::PowerSave) {
        state_.store(EmulatorSessionState::Paused);
    }
}

void EmulatorSession::notify_input() {
    last_input_tick_ms_ = steady_time_ms();
    if (state_.load() == EmulatorSessionState::PowerSave) {
        std::printf("leave power save\n");
        reset_timing();
        if (enable_auto_time_sync && sync_on_resume) sync_emulator_clock();
        state_.store(EmulatorSessionState::Running);
    }
}

void EmulatorSession::request_stop() {
    stop_requested_.store(true);
}

bool EmulatorSession::run_iteration(const PlatformStep& platform_step) {
    if (stop_requested_.load() || state_.load() == EmulatorSessionState::Stopped) {
        return false;
    }

    if (state_.load() == EmulatorSessionState::Paused) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        return !stop_requested_.load();
    }

    detect_wall_clock_jump();

    if (state_.load() == EmulatorSessionState::PowerSave) {
        if (platform_step && !platform_step(expected_tick_ms_, false)) {
            request_stop();
            return false;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        return !stop_requested_.load();
    }

    RunTimeSlice(SLICE_INTERVAL);

    if (platform_step && !platform_step(expected_tick_ms_, true)) {
        request_stop();
        return false;
    }

    const uint64_t current_tick_ms = steady_time_ms();
    if (!enable_keepon && current_tick_ms - last_input_tick_ms_ >
        static_cast<uint64_t>(power_save_interval) * 1000ULL) {
        std::printf("enter power save\n");
        state_.store(EmulatorSessionState::PowerSave);
        return !stop_requested_.load();
    }

    expected_tick_ms_ += SLICE_INTERVAL;
    uint64_t actual_tick_ms = current_tick_ms - start_tick_ms_;

    if (fast_forward && fast_forward_limit == 0) {
        expected_tick_ms_ = actual_tick_ms;
    }

    if (actual_tick_ms > expected_tick_ms_ + 300) {
        expected_tick_ms_ = actual_tick_ms - 300;
    }
    if (expected_tick_ms_ > actual_tick_ms + 300) {
        actual_tick_ms = expected_tick_ms_ - 300;
    }

    if (actual_tick_ms < expected_tick_ms_) {
        std::this_thread::sleep_for(
            std::chrono::milliseconds(expected_tick_ms_ - actual_tick_ms));
    }

    return !stop_requested_.load();
}

void EmulatorSession::shutdown() {
    if (state_.load() == EmulatorSessionState::Stopped) return;

    if (save_flash_on_exit) save_flash("");
    if (save_state_on_exit) save_state("");

    stop_requested_.store(true);
    state_.store(EmulatorSessionState::Stopped);
}

EmulatorSessionState EmulatorSession::state() const {
    return state_.load();
}

void EmulatorSession::reset_timing() {
    start_tick_ms_ = steady_time_ms();
    expected_tick_ms_ = 0;
    last_input_tick_ms_ = start_tick_ms_;
    last_wall_time_ms_ = wall_time_ms();
}

void EmulatorSession::sync_emulator_clock() {
    if (nc2000mode) {
        void sync_time_2000();
        sync_time_2000();
    }
    if (nc1020mode) {
        void sync_time_1020();
        sync_time_1020();
    }
}

void EmulatorSession::detect_wall_clock_jump() {
    if (!enable_auto_time_sync || !sync_on_resume) return;

    const uint64_t current_wall_time_ms = wall_time_ms();
    if (last_wall_time_ms_ != 0 &&
        current_wall_time_ms > last_wall_time_ms_ + 10000ULL) {
        if (debug_level >= 1) {
            std::printf(
                "detected time jump last=%llu current=%llu delta=%llu\n",
                static_cast<unsigned long long>(last_wall_time_ms_),
                static_cast<unsigned long long>(current_wall_time_ms),
                static_cast<unsigned long long>(current_wall_time_ms - last_wall_time_ms_));
        }
        sync_emulator_clock();
    }
    last_wall_time_ms_ = current_wall_time_ms;
}
