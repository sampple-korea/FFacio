package io.ffacio.itsokeyruntime;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ItsokeyRuntimeService extends Service {
    public static final int API_VERSION = 1;
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
            "io.ffacio.itsokeyruntime",
            "com.ffacio.mobile"
    ));
    private SecureSessionStore sessionStore;
    private ItsokeyApiClient api;

    private final IITSOKeyRuntime.Stub binder = new IITSOKeyRuntime.Stub() {
        @Override public int getApiVersion() { enforceCaller(); return API_VERSION; }
        @Override public boolean isAuthenticated() {
            enforceCaller();
            ItsokeySession session = sessionStore.load();
            return session != null && session.usable();
        }
        @Override public String getSessionInfoJson() { enforceCaller(); return api.sessionInfo(); }
        @Override public String verifySessionJson() { enforceCaller(); return api.verifySession(); }
        @Override public String refreshSessionJson() { enforceCaller(); return api.refreshSession(); }
        @Override public String listDevicesJson() { enforceCaller(); return api.listDevices(); }
        @Override public String getDeviceJson(String deviceIdx) { enforceCaller(); return api.getDevice(deviceIdx); }
        @Override public String openDeviceJson(String deviceIdx) { enforceCaller(); return api.controlDevice(deviceIdx, "OPEN"); }
        @Override public String closeDeviceJson(String deviceIdx) { enforceCaller(); return api.controlDevice(deviceIdx, "CLOSE"); }
        @Override public void clearSession() { enforceCaller(); sessionStore.clear(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        sessionStore = new SecureSessionStore(this);
        api = new ItsokeyApiClient(sessionStore);
    }

    @Override public IBinder onBind(Intent intent) {
        return binder;
    }

    private void enforceCaller() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return;
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (packages != null) {
            for (String packageName : packages) {
                if (ALLOWED_PACKAGES.contains(packageName)) return;
            }
        }
        throw new SecurityException("허용되지 않은 ITSOKEY Runtime 호출자입니다");
    }
}
