# NC1020 Android UI 实现交接与任务计划

## 目标

在新的会话中，把已确认的第四版视觉稿实现到 `android` 子项目。优先完成运行页和全键盘视觉，不修改模拟器核心或现有按键功能。

## 开始前必读

1. `android/design/NC1020_UI_DESIGN.md`
2. `android/design/nc1020-ui-concept-v4.png`（用户已确认的视觉基线）
3. `android/design/references/pc1000a-keyboard-front.jpeg`
4. `android/design/references/pc1000a-device-open.jpeg`
5. `android/design/assets/README.md`

真机照片只用于借鉴键盘区域，不代表目标型号，也不能把 `PC1000A` 标识带入应用。

## 当前代码入口

| 领域 | 文件 |
| --- | --- |
| 运行页、LCD、迷你/全键盘、运行菜单 | `android/app/src/main/java/io/github/wangyu/nc2000/emulator/EmulatorScreen.kt` |
| 应用主题 | `android/app/src/main/java/io/github/wangyu/nc2000/ui/theme/Theme.kt` |
| 启动器 | `android/app/src/main/java/io/github/wangyu/nc2000/launcher/LauncherScreen.kt` |
| 自定义控制场景模型 | `android/app/src/main/java/io/github/wangyu/nc2000/controls/ControlScene.kt` |
| 控制场景编辑器 | `android/app/src/main/java/io/github/wangyu/nc2000/controls/ControlSceneEditorDialog.kt` |
| Native 接口 | `android/app/src/main/java/io/github/wangyu/nc2000/nativebridge/NativeBridge.kt` |

## 严格边界

- 不修改 C/C++ 模拟核心。
- 不修改 `NativeBridge.setKey()` 的语义或任何 native key ID。
- 不改变 `fullKeyboardRows` 中已有按键的 key ID、功能和按下/释放行为。
- 不根据 `SHIFT`、`CAPS`、`继续`等辅助文字推断或新增按键动作。
- 精简/完整是纯视觉形态，不是输入模式，也不改变点击区域。
- 不把键帽文字烘焙到图片。
- 不直接从概念图裁切生产键帽。
- 当前工作树已有用户改动；开始前运行 `git status --short`，不得覆盖或回滚无关改动。

## 已确认的设计决策

### 整体

- 第四版 `nc1020-ui-concept-v4.png` 是实现基线。
- 整体风格轻薄、扁平、暖白；真机质感集中在 LCD 与键盘区域。
- 不绘制厚重手机外框，不使用大面积深蓝卡片或圆形 D-pad。

### 全键盘右下角

必须保持现有 10 列矩阵，最后两行的右侧四列视觉排列为：

```text
[ M / 3 ] [  税  ] [ ↑ / − ] [  M−  ]
[空格/= ] [  ←  ] [ ↓ / + ] [ →/M+ ]
```

这只是 UI 主/副标呈现。实际按键仍分别使用当前项目中 `M / 3`、`Pg↑`、`▲`、`Pg↓`、`空格`、`←`、`▼`、`→`对应的既有 key ID。

### 模拟器快捷区

- 原喇叭孔位置显示 2×2：`加速 / 存档 / 读档 / 菜单`。
- 快捷区使用浅色现代 UI，与真机深色键帽明确区分。
- 当前代码已有加速、请求保存和菜单能力。
- 当前 `NativeBridge` 没有运行中“读档”接口；本轮不得为了视觉稿新增 native 读档功能。实现时将 `读档`显示为禁用/未提供状态，或在提交前向用户确认是否暂不展示。

### 按键视觉形态

- `Compact`：键帽主体＋现有主体文字。
- `Full`：键帽主体＋上方和/或下方辅助文字。
- 辅助文字只是印刷式 UI，例如 `中英数`键上方的 `SHIFT`。
- 建议以现有 key ID 建立只读的视觉说明表，而不是修改按键动作模型。

## 推荐实现结构

建议新建纯 UI 文件，避免继续扩大 `EmulatorScreen.kt`：

```text
android/app/src/main/java/io/github/wangyu/nc2000/ui/theme/
  Color.kt                 # 设计 Token
  Theme.kt

android/app/src/main/java/io/github/wangyu/nc2000/emulator/ui/
  EmulatorKeyVisual.kt     # 键帽及精简/完整形态
  FullKeyboardLayout.kt    # 视觉说明表与 10 列布局
  EmulatorShortcutPad.kt   # 2×2 快捷区
  EmulatorChrome.kt        # 银灰面板与运行页外观
  RuntimeControlSheet.kt   # 运行控制 Bottom Sheet
```

命名可以按项目习惯调整，但 UI、视觉说明和动作映射应保持分离。

推荐的 UI-only 类型：

```kotlin
enum class KeyVisualForm { Compact, Full }

data class KeyVisualLegend(
    val text: String,
    val position: LegendPosition,
    val colorRole: LegendColorRole,
)

data class KeyVisualSpec(
    val keyId: Int,
    val primaryLabel: String,
    val legends: List<KeyVisualLegend> = emptyList(),
)
```

这些类型不得包含新的 input action，也不得替代现有 `EmulatorKeySpec` 的 key ID 映射。

## 分阶段任务计划

### 阶段 0：保护现场与建立基线

- [x] 运行 `git status --short`，记录已有修改。
- [x] 阅读上述设计文件和相关 Kotlin 文件。
- [x] 运行当前单元测试与 `./gradlew :app:assembleDebug`，记录基线结果。
- [x] 若构建失败，先判断是否为既有问题，不顺手修改无关代码。

完成标准：明确哪些文件属于本任务、哪些是用户已有改动。

### 阶段 1：主题 Token 与基础组件

- [x] 在主题层定义暖白、银灰、石墨、青绿、玫红和 LCD 色彩 Token。
- [x] 实现低矮键帽基础组件：圆角、描边、轻微高光、按下位移。
- [x] 实现 `Compact` 与 `Full` 两种纯视觉形态。
- [x] 主体文字与上下辅助文字分别渲染。
- [x] 先用 Compose 完成，不生成位图纹理。

完成标准：Preview 中同一个 key ID 可以切换两种外观，但触控行为和范围完全一致。

### 阶段 2：全键盘第四版布局

- [x] 保留当前 `fullKeyboardRows` 的 key ID 和行列顺序。
- [x] 建立单独的视觉说明表，添加数字、运算符和 `SHIFT/CAPS`等辅助文字。
- [x] 实现银灰面板、紧凑 10 列键盘和分组颜色。
- [x] 精确实现右下角两行四列，不使用长空格键或独立 D-pad。
- [x] 在 360dp 手机宽度和较宽设备上检查文字与间距。

完成标准：视觉上匹配 v4，现有按键事件测试无需修改仍能通过。

#### 阶段 0–2 实施记录（2026-07-23）

- 阶段 0 开始时工作树已有 `android/README.md`、`native_bridge.cpp`、`EmulatorScreen.kt`、物理键盘实现与测试等用户修改；本轮保留了其中的物理键盘和 LCD 刷新逻辑，未修改 C/C++、NativeBridge 或 native key ID。
- 基线命令 `./gradlew :app:testDebugUnitTest :app:assembleDebug` 成功；最终同一命令再次成功。构建仅输出既有 Android SDK XML/C++ 工具链版本警告。
- 新增纯 Compose 主题 Token、`Compact`/`Full` 键帽组件、只读视觉说明表和 10 列银灰键盘面板；新增测试锁定原始行列 ID 与右下角两行四列的动作映射。
- 已在 Android 模拟器以 360dp、411dp 和 600dp 宽度检查文字与间距；最终截图为 `nc1020-stage2-360dp.png`、`nc1020-stage2-411dp.png` 和 `nc1020-stage2-600dp.png`。测试后已恢复模拟器原始 560dpi。

### 阶段 3：运行页框架和快捷区

- [x] 收紧 TopAppBar、LCD 边框和键盘间距。
- [x] 添加浅色 2×2 快捷区。
- [x] 将 `加速`接到现有快速播放状态，将 `存档`接到现有 `requestSave()`，将 `菜单`接到现有运行菜单。
- [x] `读档`不得新增 native 行为；采用禁用状态或暂不展示并记录决定。
- [x] 保证打开菜单、失焦、旋转时仍沿用现有 release-all 保护。

完成标准：快捷区与真机键帽可明显区分，不引入新的模拟器功能定义。

### 阶段 4：运行控制 Bottom Sheet

- [x] 把现有 `RuntimeMenuDialog`改为轻量 Modal Bottom Sheet。
- [x] 保留现有 LCD palette、frame style、快速播放、保存、复位、后台运行和结束行为。
- [x] 不实现项目当前不存在的读档能力。
- [x] 结束操作保持独立、危险色和必要确认。

完成标准：所有现有菜单能力可达，打开/关闭不会留下按键 down 状态。

#### 阶段 3–4 实施记录（2026-07-23）

- 运行页改为 48dp 轻量顶部栏、1dp LCD 边框、紧凑内容间距和底部模式切换；全键模式把浅色 2×2 快捷区嵌入功能键左侧，精简模式也保留同一快捷入口。
- `加速`支持点按锁定及长按临时加速，`存档`继续调用现有 `requestSave()`，`菜单`打开运行控制面板；由于项目没有运行中读档接口，快捷区和面板中的`读档`均以“未提供”禁用态显示，没有新增 native 行为。
- 原运行设置弹窗已替换为 `ModalBottomSheet`，LCD 显示效果、边框、快速播放、保存、复位、后台运行和结束能力均保留；结束操作使用危险色并增加二次确认。
- 新增虚拟按键 release-all 守卫；打开面板、失焦、切换键盘形态、离开页面及旋转销毁时会释放仍按下的虚拟键，物理键盘的既有保护继续保留。
- 按真机照片修正计算辅助文字归属：`A log / 10ˣ`、`S ln / eˣ`、`D xʸ / ʸ√x`、`F √ / x²`、`Z ( / )`、`X π / x!`、`C EXP / 0/±`；`Pg↑`和`Pg↓`动作分别显示为上/下翻页符号，`税`和`M−`降为副标，key ID 和动作未改。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 成功。已在 Android 模拟器检查 360dp、411dp 和 600dp 全键布局、快捷区锁定反馈、Bottom Sheet 滚动与结束确认；截图为 `nc1020-stage34-360dp-full.png`、`nc1020-stage34-411dp-full.png`、`nc1020-stage34-600dp-full.png`、`nc1020-stage34-411dp-sheet.png` 和 `nc1020-stage34-411dp-sheet-actions.png`，检查后恢复 560dpi。

### 阶段 5：启动器轻量化

- [ ] 按 v4 重排标题、LCD 预览、主操作和设备列表。
- [ ] 把构建信息和低频管理操作移出首页主层级。
- [ ] 保留已有新增、复制、删除、排序、固件选择和启动逻辑。

完成标准：仅改变信息架构和外观，不破坏配置管理能力。

### 阶段 6：自定义布局的视觉形态

- [ ] 在不改变 `VirtualControlAction` 的前提下，为自定义控件增加纯外观配置。
- [ ] 场景可选择默认精简/完整，单键可选择跟随场景或覆盖。
- [ ] 编辑器只编辑外观、位置、尺寸、透明度和辅助文字。
- [ ] 如需持久化，升级 JSON schema 并提供旧数据默认值；旧布局加载后行为必须不变。

完成标准：同一个自定义按键切换视觉形态后，action、key IDs 和触控范围保持一致。

### 阶段 7：素材判断、测试和视觉 QA

- [ ] 评估纯 Compose 是否已足够接近 v4。
- [ ] 仅在必要时制作 `assets/README.md`列出的透明纹理。
- [ ] 增加/更新纯 UI 与 JSON 兼容测试，不改变既有按键映射断言。
- [ ] 运行单元测试、assembleDebug 和可行的 lint。
- [ ] 对竖屏、横屏、360dp、600dp、字体放大和深色系统设置进行截图检查。

完成标准：构建和测试通过，关键屏幕与 v4 一致，无文字烘焙素材和映射回归。

## 建议分会话执行

为控制上下文与额度，可拆为三个新会话：

1. 会话 A：阶段 0–2，只做主题、按键组件和全键盘。
2. 会话 B：阶段 3–4，只做运行页、快捷区和 Bottom Sheet。
3. 会话 C：阶段 5–7，完成启动器、自定义视觉形态、测试和 QA。

每个会话结束时更新本文的复选框，并在 `android/design/`下保存最新截图或实现说明，避免下一会话重新分析。

## 新会话可直接使用的指令

```text
请继续执行 NC1020 Android UI 改造。工作范围仅限 android 子项目。

开始前完整阅读：
- android/design/IMPLEMENTATION_HANDOFF.md
- android/design/NC1020_UI_DESIGN.md
- android/design/assets/README.md

视觉基线是 android/design/nc1020-ui-concept-v4.png。先运行 git status，保护工作树已有修改。严格保持现有按键 key ID、映射、功能和按下/释放语义不变；精简/完整只是按键 UI 形态，SHIFT 等外部文字只是视觉说明。

本会话执行 IMPLEMENTATION_HANDOFF.md 的阶段 <填写阶段号>。完成实现、必要测试和视觉检查后，更新任务复选框并报告修改文件、测试结果和剩余阶段。不要扩展到 native 模拟器功能。
```

## 最终验收重点

- 第四版轻薄视觉得到实现。
- 真机式右下角两行四列准确。
- 完整按键形态支持键帽外辅助文字，精简形态不显示。
- 所有文字由 Compose 渲染，键帽素材透明且无文字，或完全不需要位图。
- 全键模式和自定义场景的既有按键行为无任何变化。
- 用户已有未提交修改没有被覆盖。
