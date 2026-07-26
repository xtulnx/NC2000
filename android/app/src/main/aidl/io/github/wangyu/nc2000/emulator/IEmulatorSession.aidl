package io.github.wangyu.nc2000.emulator;

interface IEmulatorSession {
    String configure(
        String profileId,
        String model,
        String romPath,
        String norPath,
        String nandPath,
        String nand0Path,
        String statePath,
        boolean loadState,
        boolean autoSaveFlash,
        boolean autoSaveState,
        boolean autoTimeSync,
        boolean syncOnResume,
        boolean keepPowerOn,
        double overclockFactor,
        int fastForwardLimit
    );
    String start();
    void pause();
    void resume();
    void continueInBackground();
    void stop();
    long copyLcdFrame(inout byte[] destination, long lastSequence);
    void setKey(int keyId, boolean pressed);
    void setFastForward(boolean enabled);
    double fastForwardMultiplier();
    void requestReset();
    void requestSave(boolean includeFlash, boolean includeState);
    boolean requestLoad(boolean includeFlash, boolean includeState);
    // sourcePath is an app-private temporary file; deviceNameGbk is at most 16 bytes.
    String startImport(String sourcePath, in byte[] deviceNameGbk);
    String importStatus();
    String profileId();
    boolean isActive();
    String buildInfo();
}
