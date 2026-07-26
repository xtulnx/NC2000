# DSP 上游来源与更新

本目录是直接纳入 NC2000 的源码快照，而不是 Git 子模块。这样 Android 和桌面
构建不依赖 CI 期间拉取 DSP 仓库，且对应的实现会随本仓库版本一起固定。

## 当前来源

- 上游仓库：<https://github.com/wangyu-/wqxdsp>
- 固定提交：`4a50b8fd54da024b1755dd60fd66d6422bc87eee`
- 直接上游文件保留了原有的 `README.md` 与作者说明。

## 更新步骤

1. 克隆上游到本仓库外的临时目录，并检出要引入的提交。

   ```shell
   git clone https://github.com/wangyu-/wqxdsp.git /tmp/wqxdsp-update
   git -C /tmp/wqxdsp-update checkout <commit>
   ```

2. 先检查改动，重点确认 `dsp.cpp`、`dsp.h` 以及测试文件的差异：

   ```shell
   diff -ru --exclude .git /tmp/wqxdsp-update dsp
   ```

3. 将确认后的上游内容覆盖到本目录；不要删除本文件。推荐从上游 Git 对象导出，
   因为它不会带入 `.git` 元数据：

   ```shell
   git -C /tmp/wqxdsp-update archive <commit> | tar -x -C dsp
   ```

4. 把上方“固定提交”更新为实际引入的提交，并运行 Android 构建验证：

   ```shell
   cd android
   ./gradlew :app:assembleDebug
   ```

之后检查并提交本目录的源码、`UPSTREAM.md` 与调用方改动。更新完成可删除临时
目录。
