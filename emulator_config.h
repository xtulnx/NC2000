#pragma once

#include <string>

enum class EmulatorModel {
    NC1020,
    NC1020_TW,
    NC2000,
    NC3000,
    PC1000,
};

struct EmulatorLaunchConfig {
    EmulatorModel model = EmulatorModel::NC1020;
    std::string rom_path;
    std::string nor_path;
    std::string nand_path;
    std::string nand0_path;
    std::string state_path;
    bool load_state = false;
    bool auto_save_flash = false;
    bool auto_save_state = false;
    bool auto_time_sync = true;
    bool sync_on_resume = true;
    bool keep_power_on = true;
    double overclock_factor = 1.0;
    int fast_forward_limit = 5;
};

bool parse_emulator_model(
    const std::string& value,
    EmulatorModel* model,
    std::string* error);

bool validate_emulator_config(
    const EmulatorLaunchConfig& config,
    std::string* error,
    bool check_files = true);

bool apply_emulator_config(
    const EmulatorLaunchConfig& config,
    std::string* error,
    bool check_files = true);
