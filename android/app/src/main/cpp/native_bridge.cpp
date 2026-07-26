#include <jni.h>
#include <android/log.h>

#include "comm.h"
#include "cmd.h"
#include "cpu.h"
#include "emulator_config.h"
#include "emulator_session.h"
#include "key_matrix.h"
#include "mem.h"
#include "nand.h"
#include "nc2000.h"
#include "nor.h"
#include "sound.h"
#include "state.h"

#include <array>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#if defined(NC2000_HAS_SDL2)
#include <SDL.h>
SDL_Window* window = nullptr;
#endif

bool console_on = false;

void print_help() {}

extern nc2k_states_t nc2k_states;
void cold_reset();

namespace {

constexpr size_t kLcdWidth = 160;
constexpr size_t kLcdHeight = 80;
constexpr size_t kLcdPixelCount = kLcdWidth * kLcdHeight;
constexpr size_t kLcdMonoBufferSize = kLcdPixelCount / 8;
constexpr size_t kLcdGreyBufferSize = kLcdPixelCount / 4;
constexpr uint64_t kLcdMaximumPendingMs = 48;
constexpr uint64_t kLcdInputSettleMs = 40;
constexpr uint8_t kStorageFlash = 1 << 0;
constexpr uint8_t kStorageState = 1 << 1;
constexpr uint64_t kImportWaitTimeoutMs = 15000;
constexpr uint64_t kImportStallTimeoutMs = 30000;

struct KeyEvent {
    uint8_t key_id;
    bool pressed;
    uint64_t ready_at_ms;
};

uint64_t monotonic_time_ms() {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch())
            .count());
}

class NativeSessionController {
public:
    std::string start_import(std::string source_path, std::vector<uint8_t> device_name) {
        if (!active_.load()) return "模拟器未运行";
        if (!desired_running_.load()) return "模拟器正在暂停，恢复运行后再导入";
        if (source_path.empty()) return "导入源文件不能为空";
        std::ifstream source(source_path, std::ios::binary | std::ios::ate);
        if (!source.good()) return "无法打开导入源文件";
        const std::streampos source_size = source.tellg();
        if (source_size < 0) return "无法读取导入源文件大小";
        if (device_name.empty() || device_name.size() > 16) return "设备文件名必须为 1 到 16 个 GBK 字节";
        for (uint8_t byte : device_name) if (byte == 0) return "设备文件名不能包含 NUL";
        std::lock_guard<std::mutex> lock(import_mutex_);
        if (import_state_ == ImportState::Pending || import_state_ == ImportState::Running)
            return "已有文件传输任务";
        import_source_path_ = std::move(source_path);
        import_device_name_ = std::move(device_name);
        import_state_ = ImportState::Pending;
        import_error_.clear();
        import_started_ms_ = monotonic_time_ms();
        import_transferred_ = 0;
        import_total_ = static_cast<size_t>(source_size);
        return {};
    }

    std::string import_status() const {
        std::lock_guard<std::mutex> lock(import_mutex_);
        const char* state = import_state_ == ImportState::Pending ? "pending" :
            import_state_ == ImportState::Running ? "running" :
            import_state_ == ImportState::Succeeded ? "succeeded" : "failed";
        return std::string(state) + "|" + std::to_string(import_transferred_) + "|" +
            std::to_string(import_total_) + "|" + import_error_;
    }
    ~NativeSessionController() {
        stop();
    }

    std::string configure(EmulatorLaunchConfig options) {
        if (active_.load()) return "模拟器正在运行，请先返回启动器";

        std::string error;
        if (!validate_emulator_config(options, &error, true)) return error;

        std::lock_guard<std::mutex> lock(lifecycle_mutex_);
        if (active_.load()) return "模拟器正在运行，请先返回启动器";
        launch_options_ = std::move(options);
        configured_ = true;
        return {};
    }

    std::string start() {
        std::lock_guard<std::mutex> lock(lifecycle_mutex_);
        if (active_.load()) return "模拟器核心已在运行";
        if (!configured_) return "尚未应用启动配置";

        if (worker_.joinable()) worker_.join();
        session_.reset();

        std::string error;
        if (!apply_emulator_config(launch_options_, &error, true)) return error;

        session_ = std::make_unique<EmulatorSession>();
        if (!session_->initialize()) {
            session_.reset();
            return "模拟器核心初始化失败";
        }

        ResetKeys();
        clear_runtime_state();
#if defined(NC2000_HAS_SDL2)
        if (audio_available_.load()) {
            SDL_SetMainReady();
            SDL_SetHint(SDL_HINT_AUDIODRIVER, "AAudio");
            audio_subsystem_started_ = SDL_InitSubSystem(SDL_INIT_AUDIO) == 0;
            if (audio_subsystem_started_) {
                audio_started_ = init_audio();
                __android_log_print(
                    ANDROID_LOG_INFO,
                    "NC2000",
                    "SDL audio device %s",
                    audio_started_ ? "started" : "failed to open");
            } else {
                __android_log_print(
                    ANDROID_LOG_WARN,
                    "NC2000",
                    "SDL audio subsystem failed: %s",
                    SDL_GetError());
            }
        }
#endif
        desired_running_.store(true);
        active_.store(true);
        worker_ = std::thread([this] { run(); });
        return {};
    }

    void pause() {
        desired_running_.store(false);
        release_pressed_keys();
    }

    void resume() {
        if (active_.load()) {
            enable_keepon = launch_options_.keep_power_on;
            desired_running_.store(true);
        }
    }

    void continue_in_background() {
        if (active_.load()) {
            enable_keepon = true;
            desired_running_.store(true);
        }
    }

    void set_audio_available(bool available) {
        audio_available_.store(available);
    }

    bool active() const {
        return active_.load();
    }

    void set_fast_forward(bool enabled) {
        std::lock_guard<std::mutex> lock(import_mutex_);
        if (import_fast_forward_forced_) {
            // Remember changes made from the UI while import owns the speed;
            // they become effective immediately after the transfer finishes.
            import_restore_fast_forward_ = enabled;
            return;
        }
        desired_fast_forward_.store(enabled);
        if (!enabled) fast_forward_multiplier_.store(0.0);
    }

    double fast_forward_multiplier() const {
        return fast_forward_multiplier_.load();
    }

    void request_reset() {
        reset_requested_.store(true);
    }

    void request_save(bool include_flash, bool include_state) {
        uint8_t requested = 0;
        if (include_flash) requested |= kStorageFlash;
        if (include_state) requested |= kStorageState;
        if (requested != 0) save_requested_.fetch_or(requested);
    }

    bool request_load(bool include_flash, bool include_state) {
        if (!active_.load() || (!include_flash && !include_state)) return false;
        if (include_state) {
            if (launch_options_.state_path.empty()) return false;
            std::ifstream state_file(launch_options_.state_path, std::ios::binary);
            if (!state_file.good()) return false;
        }
        uint8_t requested = 0;
        if (include_flash) requested |= kStorageFlash;
        if (include_state) requested |= kStorageState;
        load_requested_.fetch_or(requested);
        return true;
    }

    void stop() {
        std::lock_guard<std::mutex> lock(lifecycle_mutex_);
        desired_running_.store(false);
        if (session_) session_->request_stop();
        if (worker_.joinable()) worker_.join();
#if defined(NC2000_HAS_SDL2)
        if (audio_started_) shutdown_audio();
        audio_started_ = false;
        if (audio_subsystem_started_) SDL_QuitSubSystem(SDL_INIT_AUDIO);
        audio_subsystem_started_ = false;
#endif
        ResetKeys();
        session_.reset();
        active_.store(false);
        clear_runtime_state();
    }

    void set_key(uint8_t key_id, bool pressed) {
        if (!active_.load() || key_id >= key_states_.size()) return;
        std::lock_guard<std::mutex> lock(input_mutex_);
        if (key_states_[key_id] == pressed) return;
        key_states_[key_id] = pressed;
        const uint64_t now = monotonic_time_ms();
        if (pressed) minimum_release_time_ms_[key_id] = now + 30;
        key_events_.push_back({
            key_id,
            pressed,
            pressed ? now : std::max(now, minimum_release_time_ms_[key_id]),
        });
    }

    uint64_t copy_lcd_frame(uint8_t* destination, size_t destination_size, uint64_t last_sequence) {
        if (destination == nullptr || destination_size < kLcdPixelCount) return 0;
        std::lock_guard<std::mutex> lock(frame_mutex_);
        if (frame_sequence_ == 0 || frame_sequence_ == last_sequence) return frame_sequence_;
        std::memcpy(destination, lcd_pixels_.data(), kLcdPixelCount);
        return frame_sequence_;
    }

private:
    void run() {
        bool running = false;
        uint64_t speed_sample_started_ms = monotonic_time_ms();
        double sampled_emulated_ms = 0.0;
        while (session_) {
            apply_pending_keys();
            apply_runtime_requests();
            const bool should_run = desired_running_.load();
            if (should_run != running) {
                if (should_run) session_->resume();
                else session_->pause();
                running = should_run;
            }

            if (!session_->run_iteration(
                    [this](uint64_t expected_tick, bool should_render) {
                        if (should_render) latch_lcd_frame(expected_tick);
                        return true;
                    })) {
                break;
            }

            const bool fast_forwarding = should_run && desired_fast_forward_.load();
            const uint64_t now = monotonic_time_ms();
            if (!fast_forwarding) {
                fast_forward_multiplier_.store(0.0);
                speed_sample_started_ms = now;
                sampled_emulated_ms = 0.0;
                continue;
            }

            // A finite fast-forward limit advances that many emulated
            // milliseconds per wall-clock slice. Unlimited mode advances one
            // millisecond per iteration, so this also reports its actual rate.
            const double slice_multiplier = launch_options_.fast_forward_limit == 0
                ? 1.0
                : static_cast<double>(launch_options_.fast_forward_limit);
            sampled_emulated_ms += static_cast<double>(SLICE_INTERVAL) * slice_multiplier;
            const uint64_t elapsed_ms = now - speed_sample_started_ms;
            if (elapsed_ms >= 200) {
                fast_forward_multiplier_.store(sampled_emulated_ms / elapsed_ms);
                speed_sample_started_ms = now;
                sampled_emulated_ms = 0.0;
            }
        }

        if (session_) session_->shutdown();
        active_.store(false);
        fast_forward_multiplier_.store(0.0);
    }

    void apply_pending_keys() {
        std::vector<KeyEvent> events;
        {
            std::lock_guard<std::mutex> lock(input_mutex_);
            const uint64_t now = monotonic_time_ms();
            std::array<bool, 64> handled_keys{};
            auto event = key_events_.begin();
            while (event != key_events_.end()) {
                if (event->ready_at_ms <= now && !handled_keys[event->key_id]) {
                    handled_keys[event->key_id] = true;
                    events.push_back(*event);
                    event = key_events_.erase(event);
                } else {
                    ++event;
                }
            }
        }
        if (events.empty()) return;
        for (const KeyEvent& event : events) SetKey(event.key_id, event.pressed);
        lcd_settle_until_ms_ = monotonic_time_ms() + kLcdInputSettleMs;
        if (session_) session_->notify_input();
    }

    void apply_runtime_requests() {
        apply_import_request();
        fast_forward = desired_fast_forward_.load();
        if (reset_requested_.exchange(false)) {
            cold_reset();
            ResetKeys();
        }
        const uint8_t save_requested = save_requested_.exchange(0);
        if ((save_requested & kStorageFlash) != 0) save_flash("");
        if ((save_requested & kStorageState) != 0) save_state("");
        const uint8_t load_requested = load_requested_.exchange(0);
        if (load_requested != 0) {
            if ((load_requested & kStorageFlash) != 0) {
                init_nor();
                if (nc2000mode || nc3000mode) {
                    read_nand0_file();
                    read_nand_file();
                    clear_nand_status();
                }
            }
            if ((load_requested & kStorageState) != 0) load_state();
            super_switch();
            ResetKeys();
            {
                std::lock_guard<std::mutex> lock(input_mutex_);
                key_events_.clear();
                key_states_.fill(false);
                minimum_release_time_ms_.fill(0);
            }
            if (session_) session_->notify_input();
        }
    }

    void apply_import_request() {
        std::lock_guard<std::mutex> lock(import_mutex_);
        if (import_state_ != ImportState::Pending && import_state_ != ImportState::Running) return;
        const uint64_t now = monotonic_time_ms();
        if (import_state_ == ImportState::Pending) {
            // Match cpu_run3's legacy put gate: the injection is only safe after
            // interrupts are enabled by the device operating system.
            if ((cpu->P() & 4) != 0) {
                if (now - import_started_ms_ > kImportWaitTimeoutMs) fail_import("设备未进入系统（等待中断允许超时）");
                return;
            }
            std::string error;
            if (!begin_put_from_file(import_source_path_, import_device_name_, &error)) {
                fail_import(error.empty() ? "无法启动文件传输" : error);
                return;
            }
            // The queue now owns the data. Kotlin retains responsibility for
            // the app-private staging file and removes it after terminal status.
            import_source_path_.clear();
            import_state_ = ImportState::Running;
            import_last_progress_ms_ = now;
            import_total_ = put_transfer_total();
            import_transferred_ = 0;
            import_restore_fast_forward_ = desired_fast_forward_.load();
            import_fast_forward_forced_ = true;
            desired_fast_forward_.store(true);
            // Zero is the existing core's unlimited mode: it removes wall-clock
            // pacing rather than multiplying a single time slice.
            fast_forward_limit = 0;
            return;
        }
        const size_t transferred = put_transfer_transferred();
        if (transferred > import_transferred_) import_last_progress_ms_ = now;
        import_transferred_ = transferred;
        import_total_ = put_transfer_total();
        if (!put_transfer_active()) {
            import_transferred_ = import_total_;
            import_state_ = ImportState::Succeeded;
            import_source_path_.clear();
            restore_import_speed();
        } else if (now - import_last_progress_ms_ > kImportStallTimeoutMs) {
            fail_import("文件传输超时（30 秒无进度）");
        }
    }

    void fail_import(const std::string& error) {
        if (import_state_ == ImportState::Running) cancel_put_transfer();
        restore_import_speed();
        import_state_ = ImportState::Failed;
        import_error_ = error;
        import_source_path_.clear();
    }

    void restore_import_speed() {
        if (!import_fast_forward_forced_) return;
        desired_fast_forward_.store(import_restore_fast_forward_);
        fast_forward_limit = launch_options_.fast_forward_limit;
        if (!import_restore_fast_forward_) fast_forward_multiplier_.store(0.0);
        import_fast_forward_forced_ = false;
    }

    bool read_lcd_frame(std::array<uint8_t, kLcdPixelCount>* pixels) {
        if (pixels == nullptr) return false;
        std::array<uint8_t, kLcdGreyBufferSize> source{};
        bool lcd_on = true;
        if (nc2000mode || nc1020mode) {
            if (nc2000mode) lcd_on = nc2k_states.lcden && nc2k_states.lcdon;
            if (nc1020mode) lcd_on = nc2k_states.lcdon;
            if (nc2k_states.ram_io[0x05] >> 5 == 7) lcd_on = false;
        }

        const bool grey_mode = is_grey_mode();
        if (lcd_on && !CopyLcdBuffer(source.data())) return false;

        pixels->fill(0);
        if (lcd_on && grey_mode) {
            for (size_t index = 0; index < kLcdGreyBufferSize; ++index) {
                for (size_t pixel = 0; pixel < 4; ++pixel) {
                    (*pixels)[index * 4 + pixel] =
                        (source[index] >> (6 - pixel * 2)) & 0x03;
                }
            }
        } else if (lcd_on) {
            for (size_t index = 0; index < kLcdMonoBufferSize; ++index) {
                for (size_t pixel = 0; pixel < 8; ++pixel) {
                    (*pixels)[index * 8 + pixel] =
                        (source[index] & (1 << (7 - pixel))) != 0 ? 3 : 0;
                }
            }
        }
        return true;
    }

    void publish_lcd_frame(const std::array<uint8_t, kLcdPixelCount>& pixels) {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        if (frame_sequence_ != 0 && lcd_pixels_ == pixels) return;
        lcd_pixels_ = pixels;
        ++frame_sequence_;
    }

    void latch_lcd_frame(uint64_t expected_tick) {
        const uint64_t refresh_interval = std::max<uint32_t>(1, LCD_INNER_REFRESH_INTERVAL);
        if (has_lcd_latch_ && expected_tick < last_lcd_latch_tick_) {
            has_lcd_latch_ = false;
            has_pending_lcd_frame_ = false;
        }
        if (has_lcd_latch_ &&
            expected_tick / refresh_interval == last_lcd_latch_tick_ / refresh_interval) {
            return;
        }
        last_lcd_latch_tick_ = expected_tick;
        has_lcd_latch_ = true;

        std::array<uint8_t, kLcdPixelCount> candidate{};
        if (!read_lcd_frame(&candidate)) return;
        const bool input_settling = monotonic_time_ms() < lcd_settle_until_ms_;
        if (!has_pending_lcd_frame_) {
            pending_lcd_pixels_ = candidate;
            pending_lcd_since_tick_ = expected_tick;
            pending_lcd_frame_published_ = false;
            has_pending_lcd_frame_ = true;
            return;
        }
        if (candidate == pending_lcd_pixels_) {
            if (!input_settling && !pending_lcd_frame_published_) {
                publish_lcd_frame(candidate);
                pending_lcd_frame_published_ = true;
            }
            return;
        }

        if (pending_lcd_frame_published_) pending_lcd_since_tick_ = expected_tick;
        pending_lcd_pixels_ = candidate;
        pending_lcd_frame_published_ = false;
        if (input_settling) return;
        if (expected_tick - pending_lcd_since_tick_ >= kLcdMaximumPendingMs) {
            publish_lcd_frame(candidate);
            pending_lcd_frame_published_ = true;
        }
    }

    void release_pressed_keys() {
        std::lock_guard<std::mutex> lock(input_mutex_);
        for (size_t key_id = 0; key_id < key_states_.size(); ++key_id) {
            if (!key_states_[key_id]) continue;
            key_states_[key_id] = false;
            key_events_.push_back({
                static_cast<uint8_t>(key_id),
                false,
                monotonic_time_ms(),
            });
        }
    }

    void clear_runtime_state() {
        {
            std::lock_guard<std::mutex> lock(input_mutex_);
            key_events_.clear();
            key_states_.fill(false);
            minimum_release_time_ms_.fill(0);
            desired_fast_forward_.store(false);
            fast_forward_multiplier_.store(0.0);
            reset_requested_.store(false);
            save_requested_.store(0);
            load_requested_.store(0);
        }
        {
            std::lock_guard<std::mutex> lock(frame_mutex_);
            lcd_pixels_.fill(0);
            frame_sequence_ = 0;
        }
        has_lcd_latch_ = false;
        last_lcd_latch_tick_ = 0;
        pending_lcd_pixels_.fill(0);
        pending_lcd_since_tick_ = 0;
        lcd_settle_until_ms_ = 0;
        has_pending_lcd_frame_ = false;
        pending_lcd_frame_published_ = false;
        std::lock_guard<std::mutex> import_lock(import_mutex_);
        if (import_state_ == ImportState::Pending || import_state_ == ImportState::Running)
            fail_import("模拟器已停止");
        desired_fast_forward_.store(false);
        fast_forward = false;
        fast_forward_limit = launch_options_.fast_forward_limit;
    }

    std::mutex lifecycle_mutex_;
    std::unique_ptr<EmulatorSession> session_;
    std::thread worker_;
    EmulatorLaunchConfig launch_options_;
    bool configured_ = false;
    bool audio_started_ = false;
    bool audio_subsystem_started_ = false;
    std::atomic<bool> active_{false};
    std::atomic<bool> desired_running_{false};
    std::atomic<bool> audio_available_{false};
    std::atomic<bool> desired_fast_forward_{false};
    std::atomic<double> fast_forward_multiplier_{0.0};
    std::atomic<bool> reset_requested_{false};
    std::atomic<uint8_t> save_requested_{0};
    std::atomic<uint8_t> load_requested_{0};

    enum class ImportState { Pending, Running, Succeeded, Failed };
    mutable std::mutex import_mutex_;
    ImportState import_state_ = ImportState::Failed;
    std::string import_source_path_;
    std::vector<uint8_t> import_device_name_;
    std::string import_error_ = "尚未提交导入";
    uint64_t import_started_ms_ = 0;
    uint64_t import_last_progress_ms_ = 0;
    size_t import_transferred_ = 0;
    size_t import_total_ = 0;
    bool import_fast_forward_forced_ = false;
    bool import_restore_fast_forward_ = false;

    std::mutex input_mutex_;
    std::vector<KeyEvent> key_events_;
    std::array<bool, 64> key_states_{};
    std::array<uint64_t, 64> minimum_release_time_ms_{};

    std::mutex frame_mutex_;
    std::array<uint8_t, kLcdPixelCount> lcd_pixels_{};
    uint64_t frame_sequence_ = 0;
    uint64_t last_lcd_latch_tick_ = 0;
    bool has_lcd_latch_ = false;
    std::array<uint8_t, kLcdPixelCount> pending_lcd_pixels_{};
    uint64_t pending_lcd_since_tick_ = 0;
    uint64_t lcd_settle_until_ms_ = 0;
    bool has_pending_lcd_frame_ = false;
    bool pending_lcd_frame_published_ = false;
};

NativeSessionController controller;

std::string to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring message(JNIEnv* env, const std::string& value) {
    if (value.empty()) return nullptr;
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeBuildInfo(
    JNIEnv* env,
    jobject) {
#if defined(NC2000_HAS_SDL2)
    SDL_version version;
    SDL_GetVersion(&version);
    return env->NewStringUTF(
        ("JNI · Core · SDL " + std::to_string(version.major) + "." +
         std::to_string(version.minor) + "." + std::to_string(version.patch))
            .c_str());
#else
    return env->NewStringUTF("JNI · Core · SDL 未连接");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeConfigure(
    JNIEnv* env,
    jobject,
    jstring model,
    jstring rom_path,
    jstring nor_path,
    jstring nand_path,
    jstring nand0_path,
    jstring state_path,
    jboolean load_state,
    jboolean auto_save_flash,
    jboolean auto_save_state,
    jboolean auto_time_sync,
    jboolean sync_on_resume,
    jboolean keep_power_on,
    jdouble overclock_factor,
    jint fast_forward_limit) {
    EmulatorLaunchConfig options;
    std::string error;
    if (!parse_emulator_model(to_string(env, model), &options.model, &error)) {
        return message(env, error);
    }
    options.rom_path = to_string(env, rom_path);
    options.nor_path = to_string(env, nor_path);
    options.nand_path = to_string(env, nand_path);
    options.nand0_path = to_string(env, nand0_path);
    options.state_path = to_string(env, state_path);
    options.load_state = load_state == JNI_TRUE;
    options.auto_save_flash = auto_save_flash == JNI_TRUE;
    options.auto_save_state = auto_save_state == JNI_TRUE;
    options.auto_time_sync = auto_time_sync == JNI_TRUE;
    options.sync_on_resume = sync_on_resume == JNI_TRUE;
    options.keep_power_on = keep_power_on == JNI_TRUE;
    options.overclock_factor = overclock_factor;
    options.fast_forward_limit = fast_forward_limit;
    return message(env, controller.configure(std::move(options)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeStart(
    JNIEnv* env,
    jobject) {
    return message(env, controller.start());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativePause(
    JNIEnv*,
    jobject) {
    controller.pause();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeResume(
    JNIEnv*,
    jobject) {
    controller.resume();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeContinueInBackground(
    JNIEnv*,
    jobject) {
    controller.continue_in_background();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeStop(
    JNIEnv*,
    jobject) {
    controller.stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeIsActive(
    JNIEnv*,
    jobject) {
    return controller.active() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeSetAudioAvailable(
    JNIEnv*,
    jobject,
    jboolean available) {
    controller.set_audio_available(available == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeSetFastForward(
    JNIEnv*,
    jobject,
    jboolean enabled) {
    controller.set_fast_forward(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeFastForwardMultiplier(
    JNIEnv*,
    jobject) {
    return controller.fast_forward_multiplier();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeRequestReset(
    JNIEnv*,
    jobject) {
    controller.request_reset();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeRequestSave(
    JNIEnv*,
    jobject,
    jboolean include_flash,
    jboolean include_state) {
    controller.request_save(include_flash == JNI_TRUE, include_state == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeRequestLoad(
    JNIEnv*,
    jobject,
    jboolean include_flash,
    jboolean include_state) {
    return controller.request_load(include_flash == JNI_TRUE, include_state == JNI_TRUE)
        ? JNI_TRUE
        : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeStartImport(
    JNIEnv* env, jobject, jstring source_path, jbyteArray device_name) {
    if (device_name == nullptr) return message(env, "设备文件名不能为空");
    const jsize size = env->GetArrayLength(device_name);
    std::vector<uint8_t> name(static_cast<size_t>(size));
    if (size > 0) env->GetByteArrayRegion(device_name, 0, size, reinterpret_cast<jbyte*>(name.data()));
    return message(env, controller.start_import(to_string(env, source_path), std::move(name)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeImportStatus(
    JNIEnv* env, jobject) {
    return env->NewStringUTF(controller.import_status().c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeCopyLcdFrame(
    JNIEnv* env,
    jobject,
    jbyteArray destination,
    jlong last_sequence) {
    if (destination == nullptr) return 0;
    const jsize size = env->GetArrayLength(destination);
    if (size < static_cast<jsize>(kLcdPixelCount)) return 0;

    std::array<uint8_t, kLcdPixelCount> pixels{};
    const uint64_t sequence = controller.copy_lcd_frame(
        pixels.data(), pixels.size(), static_cast<uint64_t>(last_sequence));
    if (sequence != 0 && sequence != static_cast<uint64_t>(last_sequence)) {
        env->SetByteArrayRegion(
            destination,
            0,
            static_cast<jsize>(pixels.size()),
            reinterpret_cast<const jbyte*>(pixels.data()));
    }
    return static_cast<jlong>(sequence);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_wangyu_nc2000_nativebridge_NativeBridge_nativeSetKey(
    JNIEnv*,
    jobject,
    jint key_id,
    jboolean pressed) {
    if (key_id < 0 || key_id > 0x3f) return;
    controller.set_key(static_cast<uint8_t>(key_id), pressed == JNI_TRUE);
}
