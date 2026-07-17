package io.ffacio.itsokeyruntime.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.ffacio.itsokeyruntime.IITSOKeyRuntime;

public final class ItsokeyRuntimeClient implements AutoCloseable {
    public static final String RUNTIME_PACKAGE = "io.ffacio.itsokeyruntime";
    public static final String SERVICE_CLASS = "io.ffacio.itsokeyruntime.ItsokeyRuntimeService";
    public static final String LOGIN_CLASS = "io.ffacio.itsokeyruntime.LoginActivity";
    public static final String ACTION_BIND = "io.ffacio.itsokeyruntime.action.BIND";
    public static final String ACTION_LOGIN = "io.ffacio.itsokeyruntime.action.LOGIN";
    public static final String BIND_PERMISSION = "io.ffacio.itsokeyruntime.permission.BIND_RUNTIME";

    private final Context context;
    private final AtomicReference<IITSOKeyRuntime> service = new AtomicReference<>();
    private volatile CountDownLatch connectionLatch = new CountDownLatch(1);
    private volatile boolean bound;
    private volatile Listener listener;

    public interface Listener {
        void onConnectionChanged(boolean connected);
    }

    public ItsokeyRuntimeClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRuntimeInstalled() {
        try {
            context.getPackageManager().getPackageInfo(RUNTIME_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    public synchronized boolean bind(Listener listener) {
        this.listener = listener;
        if (bound && service.get() != null) return true;
        if (!isRuntimeInstalled()) return false;
        connectionLatch = new CountDownLatch(1);
        Intent intent = new Intent(ACTION_BIND);
        intent.setComponent(new ComponentName(RUNTIME_PACKAGE, SERVICE_CLASS));
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return bound;
    }

    public synchronized void unbind() {
        if (bound) {
            try { context.unbindService(connection); } catch (IllegalArgumentException ignored) {}
        }
        bound = false;
        service.set(null);
        connectionLatch.countDown();
        Listener current = listener;
        if (current != null) current.onConnectionChanged(false);
    }

    public boolean isConnected() {
        return service.get() != null;
    }

    public Intent createLoginIntent() {
        Intent intent = new Intent(ACTION_LOGIN);
        intent.setComponent(new ComponentName(RUNTIME_PACKAGE, LOGIN_CLASS));
        return intent;
    }

    public int getApiVersion(long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).getApiVersion();
    }

    public boolean isAuthenticated(long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).isAuthenticated();
    }

    public String sessionInfo(long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).getSessionInfoJson();
    }

    public String verifySession(long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).verifySessionJson();
    }

    public String refreshSession(long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).refreshSessionJson();
    }

    public String listDevices(long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).listDevicesJson();
    }

    public String getDevice(String deviceIdx, long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).getDeviceJson(deviceIdx);
    }

    public String openDevice(String deviceIdx, long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).openDeviceJson(deviceIdx);
    }

    public String closeDevice(String deviceIdx, long timeoutMillis) throws Exception {
        return requireService(timeoutMillis).closeDeviceJson(deviceIdx);
    }

    public void clearSession(long timeoutMillis) throws Exception {
        requireService(timeoutMillis).clearSession();
    }

    private IITSOKeyRuntime requireService(long timeoutMillis) throws Exception {
        IITSOKeyRuntime current = service.get();
        if (current != null) return current;
        if (!bound && !bind(listener)) throw new IllegalStateException("ITSOKEY Runtime 앱이 설치되지 않았거나 서비스에 연결할 수 없습니다");
        if (!connectionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("ITSOKEY Runtime 연결 시간이 초과되었습니다");
        }
        current = service.get();
        if (current == null) throw new IllegalStateException("ITSOKEY Runtime 서비스가 연결되지 않았습니다");
        return current;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service.set(IITSOKeyRuntime.Stub.asInterface(binder));
            connectionLatch.countDown();
            Listener current = listener;
            if (current != null) current.onConnectionChanged(true);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            service.set(null);
            connectionLatch = new CountDownLatch(1);
            Listener current = listener;
            if (current != null) current.onConnectionChanged(false);
        }

        @Override public void onBindingDied(ComponentName name) {
            onServiceDisconnected(name);
            synchronized (ItsokeyRuntimeClient.this) { bound = false; }
        }

        @Override public void onNullBinding(ComponentName name) {
            onServiceDisconnected(name);
            synchronized (ItsokeyRuntimeClient.this) { bound = false; }
        }
    };

    @Override public void close() {
        unbind();
    }
}
