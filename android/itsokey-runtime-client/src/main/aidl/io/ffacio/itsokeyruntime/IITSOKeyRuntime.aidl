package io.ffacio.itsokeyruntime;

interface IITSOKeyRuntime {
    int getApiVersion();
    boolean isAuthenticated();
    String getSessionInfoJson();
    String verifySessionJson();
    String refreshSessionJson();
    String listDevicesJson();
    String getDeviceJson(String deviceIdx);
    String openDeviceJson(String deviceIdx);
    String closeDeviceJson(String deviceIdx);
    void clearSession();
}
