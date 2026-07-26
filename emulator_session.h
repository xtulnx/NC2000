#pragma once

#include <atomic>
#include <cstdint>
#include <functional>

enum class EmulatorSessionState {
    Stopped,
    Paused,
    Running,
    PowerSave,
};

class EmulatorSession {
public:
    using PlatformStep = std::function<bool(uint64_t expected_tick, bool should_render)>;

    EmulatorSession();

    bool initialize();
    void resume();
    void pause();
    void notify_input();
    void request_stop();

    // Runs one emulator/platform iteration. The callback is invoked after the
    // CPU slice, matching the desktop loop's existing event/render ordering.
    bool run_iteration(const PlatformStep& platform_step);

    void shutdown();
    EmulatorSessionState state() const;

private:
    void reset_timing();
    void sync_emulator_clock();
    void detect_wall_clock_jump();

    std::atomic<EmulatorSessionState> state_;
    std::atomic<bool> stop_requested_;
    uint64_t start_tick_ms_;
    uint64_t expected_tick_ms_;
    uint64_t last_input_tick_ms_;
    uint64_t last_wall_time_ms_;
    bool has_started_;
};
