# NC2000 Android

本目录包含 NC2000 的原生 Android 应用。当前实现提供：

- 基于 Kotlin 与 Jetpack Compose 的应用结构。
- 可视化启动器，以及可版本化、可编辑的启动配置。
- 启动配置的新增、复制、删除、排序、内置图标与自定义图片图标。
- 通过 Storage Access Framework 选择 ROM、NOR、NAND、NAND0 和状态文件。
- 在原生代码读取前，将所选固件复制到应用私有目录。
- 使用 DataStore 持久化结构化配置，不依赖原始命令行文本。
- 通过 JNI 控制、在独立进程中运行的模拟器会话，支持安全存档和前台服务后台运行。
- 160×80 LCD 渲染器，保留原机的段码与状态指示图案。
- 紧凑键盘和全键盘复用同一套全键盘视觉库，并同时兼容当前与旧版按键矩阵。
- 竖屏时 LCD 保持固定，仅键盘区域可滚动；横屏时采用 LCD 与键盘分栏布局。
- 自定义控制方案可从同一按键视觉库中选择按键，同时独立配置位置、尺寸、透明度和触控响应区域。
- 运行时屏幕配色、按住/锁定加速、重置、完整进度保存与退出控制。
- 运行时导入文件会先暂存至应用缓存。自动 BIN 模式会保留原始 `AE EE EA` 文件、移除 48 字节的 `Application` 属性头，或解密兼容 GGVPacker 的容器；也可以选择逐字节直接导入。
- 运行中的会话可返回启动器而不停机；最多可同时运行四个启动配置，并能在启动器中切换。
- 每个启动配置可选择后台行为，也可在当前会话中调整：自动暂停会保留内存中的位置且不持有唤醒锁；持续后台运行会持有部分 CPU 唤醒锁，并消耗更多电量。
- 通过 `android/third_party/SDL` 子模块集成 SDL2 2.32.10 的原生与 Java 组件。

应用不附带固件。选择文件后，应用会获得访问权限并将其复制到私有存储。这样可让便携的模拟器核心无需处理 Android 文档 URI，并在不意外改写用户原始文件的前提下管理可写的 NOR、NAND 与状态数据。

## 当前边界

JNI 桥接层会校验类型化的启动配置，在专用工作线程启动共享 C++ 核心，并提供生命周期、LCD 帧和按键 API。现有的 CPU、内存、ROM/NOR/NAND、DSP、声音生成、命令、按键矩阵和会话源码均会编译进两个 Android ABI。SDL2 的 AAudio 设备已能在 arm64 模拟器上成功打开；蜂鸣器和 DSP 声音是否可听仍需人工确认。

内置全键盘是紧凑布局与可编辑游戏布局共用的标准视觉/按键目录。未来可用皮肤包替换该目录，而无需改变按键 ID、自定义位置、缩放或响应区域。

存档概念刻意分为三层：

- ROM 为只读固件，存档操作不会写入它。
- 可写存储指 NOR，以及适用机型上的 NAND/NAND0；其中包含持久化文件和设置。
- 运行状态（`STATE`）包含 RAM、CPU 与外设状态，相当于让设备保持通电。可写存储与 `STATE` 可以分别保存和加载。退出时自动保存分别设有开关；快捷保存/加载的目标可以是 `STATE`、可写存储或两者。

## BIN 导入格式参考

自动 BIN 导入解密器及其 256 × 256 替换表遵循
[banxian/GGVPacker](https://github.com/banxian/GGVPacker) 所记录和实现的文件格式。格式识别与临时文件准备位于 Kotlin 层；C++ 核心只接收应用私有目录中的本地路径和原始 GBK 设备文件名字节。

## 构建

项目当前目标为 Android API 36，使用 NDK `30.0.15729638`、CMake `4.1.2`、Java 17 和 Gradle 8.9 Wrapper。

```shell
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

调试 APK 输出至 `app/build/outputs/apk/debug/app-debug.apk`。其中包含 `arm64-v8a` 与 `x86_64` 的 JNI/SDL2 库。当前项目已通过 CMake 4.1.2、Android Lint、arm64 模拟器以及 NC1020 3.6 桌面端回归基线验证。

## 已签名发布 APK

推送如 `v0.1.0` 的标签会触发 GitHub Actions 工作流 **Android release APKs**，并向对应的 GitHub Release 创建和附加以下三个 APK：

- `*-arm64-v8a-release.apk`：适用于当前实体 Android 设备的较小安装包。
- `*-x86_64-release.apk`：适用于 x86_64 模拟器的较小安装包。
- `*-universal-release.apk`：同时包含两个原生 ABI 的安装包。

发布版本名取自去掉前缀 `v` 的标签；GitHub Actions 的运行编号用作 Android 版本号。

请勿提交密钥库或其密码。在仓库的 **Settings → Environments** 中创建名为 `android-release` 的受保护环境，并添加以下环境密钥：

| 密钥 | 值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 完整 `.jks`/`.keystore` 文件的 Base64 编码，且不换行。 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码。 |
| `ANDROID_KEY_ALIAS` | 密钥别名。 |
| `ANDROID_KEY_PASSWORD` | 密钥密码。 |

例如，在 macOS 上可执行：`base64 -i release.keystore | tr -d '\n'`。工作流仅在临时 GitHub 托管运行器上以仅所有者权限写入密钥，不会记录到日志，并会在构建后删除。受保护环境的审批也能阻止拉取请求和未经批准的标签构建取得签名密钥。

如需在本地构建已签名的发布包，请导出 `SIGNING_STORE_FILE`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS` 和 `SIGNING_KEY_PASSWORD`，然后执行 `./gradlew :app:assembleRelease`。密钥库文件扩展名与 Android 本地配置均已被 Git 忽略。
