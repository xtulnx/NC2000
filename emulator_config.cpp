#include "emulator_config.h"

#include "comm.h"
#include "nor.h"

#include <fstream>

namespace {

bool path_exists(const std::string& path) {
    if (path.empty()) return false;
    std::ifstream file(path.c_str(), std::ios::binary);
    return file.good();
}

bool require_path(
    const std::string& path,
    const char* label,
    bool check_files,
    std::string* error) {
    if (path.empty()) {
        if (error) *error = std::string("缺少 ") + label + " 文件";
        return false;
    }
    if (check_files && !path_exists(path)) {
        if (error) *error = std::string(label) + " 文件不存在：" + path;
        return false;
    }
    return true;
}

bool uses_mask_rom(EmulatorModel model) {
    return model == EmulatorModel::NC1020 ||
        model == EmulatorModel::NC1020_TW ||
        model == EmulatorModel::PC1000;
}

bool uses_nand(EmulatorModel model) {
    return model == EmulatorModel::NC2000 || model == EmulatorModel::NC3000;
}

}  // namespace

bool parse_emulator_model(
    const std::string& value,
    EmulatorModel* model,
    std::string* error) {
    if (model == nullptr) {
        if (error) *error = "机型输出参数为空";
        return false;
    }
    if (value == "NC1020") *model = EmulatorModel::NC1020;
    else if (value == "NC1020_TW") *model = EmulatorModel::NC1020_TW;
    else if (value == "NC2000") *model = EmulatorModel::NC2000;
    else if (value == "NC3000") *model = EmulatorModel::NC3000;
    else if (value == "PC1000") *model = EmulatorModel::PC1000;
    else {
        if (error) *error = "不支持的机型：" + value;
        return false;
    }
    return true;
}

bool validate_emulator_config(
    const EmulatorLaunchConfig& config,
    std::string* error,
    bool check_files) {
    if (!require_path(config.nor_path, "NOR", check_files, error)) return false;
    if (uses_mask_rom(config.model) &&
        !require_path(config.rom_path, "ROM", check_files, error)) {
        return false;
    }
    if (uses_nand(config.model)) {
        if (!require_path(config.nand_path, "NAND", check_files, error)) return false;
        if (!require_path(config.nand0_path, "NAND0", check_files, error)) return false;
    }
    if (config.load_state &&
        !require_path(config.state_path, "运行现场 STATE（RAM/CPU）", check_files, error)) {
        return false;
    }
    if (config.auto_save_state && config.state_path.empty()) {
        if (error) *error = "自动保存 STATE 需要运行现场保存路径";
        return false;
    }
    if (config.overclock_factor < 0.1 || config.overclock_factor > 20.0) {
        if (error) *error = "主频倍率必须在 0.1 到 20.0 之间";
        return false;
    }
    if (config.fast_forward_limit < 0) {
        if (error) *error = "快进上限不能为负数";
        return false;
    }
    return true;
}

bool apply_emulator_config(
    const EmulatorLaunchConfig& config,
    std::string* error,
    bool check_files) {
    if (!validate_emulator_config(config, error, check_files)) return false;

    nc1020mode = false;
    nc2000mode = false;
    nc3000mode = false;
    pc1000mode = false;
    nc1020tw_mode = false;

    switch (config.model) {
    case EmulatorModel::NC1020:
        nc1020mode = true;
        break;
    case EmulatorModel::NC1020_TW:
        nc1020mode = true;
        nc1020tw_mode = true;
        break;
    case EmulatorModel::NC2000:
        nc2000mode = true;
        break;
    case EmulatorModel::NC3000:
        nc3000mode = true;
        break;
    case EmulatorModel::PC1000:
        pc1000mode = true;
        break;
    }

    nc2k_rom = {};
    nc2k_rom.romPath = config.rom_path;
    nc2k_rom.norFlashPath = config.nor_path;
    nc2k_rom.nandFlashPath = config.nand_path;
    nc2k_rom.nand0Path = config.nand0_path;
    nc2k_rom.statesPath = config.state_path;

    enable_load_state = config.load_state;
    reset_after_load_state = false;
    save_flash_on_exit = config.auto_save_flash;
    save_state_on_exit = config.auto_save_state;
    enable_auto_time_sync = config.auto_time_sync;
    sync_on_resume = config.sync_on_resume;
    enable_keepon = config.keep_power_on;
    oc_factor = config.overclock_factor;
    timer01_speed_fix = 1.0 / oc_factor;
    fast_forward = false;
    fast_forward_limit = config.fast_forward_limit;
    speed_multiplier = 1.0;

    if (nc1020mode) {
        nor_info_block[8] = 0xfc;
        nor_info_block[9] = 0x03;
    }

    return true;
}
