# Third-party native dependencies

The Android build uses the official SDL2 source tree at:

```text
android/third_party/SDL
```

It is pinned as a Git submodule to SDL2 `release-2.32.10`. Clone with
`--recurse-submodules`, or run `git submodule update --init --recursive` after
cloning. Firmware files never belong here and must be selected by the user
through Android's system file picker.
