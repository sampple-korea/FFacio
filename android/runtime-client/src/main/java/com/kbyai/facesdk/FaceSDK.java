package com.kbyai.facesdk;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import io.ffacio.ipc.FFacioFaceParcel;
import io.ffacio.ipc.FFacioOptionsParcel;
import io.ffacio.sdk.FFacioRuntimeClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/** 기존 정적 API와 process-wide 단일 Runtime Binder session을 함께 제공합니다. */
public final class FaceSDK {
    public static final int SDK_SUCCESS = 0;
    public static final int SDK_LICENSE_KEY_ERROR = -1;
    public static final int SDK_LICENSE_APPID_ERROR = -2;
    public static final int SDK_LICENSE_EXPIRED = -3;
    public static final int SDK_NO_ACTIVATED = -4;
    public static final int SDK_INIT_ERROR = -5;

    public enum DisconnectReason { NONE, MANUAL, SERVICE_DISCONNECTED, BINDER_DIED, ERROR }

    public static final class ConnectionSnapshot {
        public final boolean connected;
        public final boolean connecting;
        public final boolean ready;
        public final int initializationResult;
        public final DisconnectReason disconnectReason;
        public final Throwable error;
        public final int reconnectAttempt;

        private ConnectionSnapshot(boolean connected, boolean connecting, boolean ready,
                                   int initializationResult, DisconnectReason disconnectReason,
                                   Throwable error, int reconnectAttempt) {
            this.connected = connected;
            this.connecting = connecting;
            this.ready = ready;
            this.initializationResult = initializationResult;
            this.disconnectReason = disconnectReason;
            this.error = error;
            this.reconnectAttempt = reconnectAttempt;
        }
    }

    public interface InitializationListener { void onInitialized(int result); }
    public interface ConnectionStateListener { void onConnectionStateChanged(ConnectionSnapshot snapshot); }

    private static final Object LOCK = new Object();
    private static final List<InitializationListener> pendingListeners = new ArrayList<>();
    private static final CopyOnWriteArraySet<ConnectionStateListener> stateListeners = new CopyOnWriteArraySet<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Object RECONNECT_TOKEN = new Object();
    private static volatile FFacioRuntimeClient client;
    private static volatile Context applicationContext;
    private static volatile boolean ready;
    private static volatile boolean connecting;
    private static volatile boolean manualDisconnect;
    private static volatile int initializationResult = Integer.MIN_VALUE;
    private static volatile DisconnectReason disconnectReason = DisconnectReason.NONE;
    private static volatile Throwable lastError;
    private static volatile int reconnectAttempt;

    private FaceSDK() { }

    public static void initialize(Context context, InitializationListener listener) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        boolean start;
        synchronized (LOCK) {
            applicationContext = context.getApplicationContext();
            if (ready) {
                listener.onInitialized(SDK_SUCCESS);
                return;
            }
            if (client != null && client.isConnected() && initializationResult != Integer.MIN_VALUE) {
                // Binder는 살아 있지만 SDK 초기화가 실패한 상태. 무한 connecting 대신 실제 코드를 즉시 반환합니다.
                listener.onInitialized(initializationResult);
                return;
            }
            pendingListeners.add(listener);
            manualDisconnect = false;
            start = !connecting;
            if (start) connecting = true;
            ensureClientLocked();
        }
        if (start) {
            publishState();
            client.connect();
        }
    }

    /** 어느 화면에서 호출하든 process-wide 단일 Binder session을 시작하거나 공유합니다. */
    public static boolean connect(Context context) {
        initialize(context, result -> { });
        FFacioRuntimeClient current = client;
        return current != null && (current.isBound() || current.isConnecting() || current.isConnected());
    }

    public static boolean reconnect(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        synchronized (LOCK) {
            applicationContext = context.getApplicationContext();
            manualDisconnect = false;
            connecting = true;
            ready = false;
            initializationResult = Integer.MIN_VALUE;
            disconnectReason = DisconnectReason.NONE;
            lastError = null;
            ensureClientLocked();
        }
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN);
        publishState();
        return client.reconnect();
    }

    public static void disconnect() {
        List<InitializationListener> pending;
        synchronized (LOCK) {
            manualDisconnect = true;
            ready = false;
            connecting = false;
            disconnectReason = DisconnectReason.MANUAL;
            pending = new ArrayList<>(pendingListeners);
            pendingListeners.clear();
        }
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN);
        FFacioRuntimeClient current = client;
        if (current != null) current.disconnect();
        for (InitializationListener listener : pending) {
            try { listener.onInitialized(SDK_INIT_ERROR); } catch (RuntimeException ignored) { }
        }
        publishState();
    }

    public static boolean isReady() { return ready; }
    public static boolean isConnecting() { return connecting; }
    public static int getInitializationResult() { return initializationResult; }

    public static ConnectionSnapshot getConnectionSnapshot() {
        FFacioRuntimeClient current = client;
        return new ConnectionSnapshot(
                current != null && current.isConnected(), connecting, ready,
                initializationResult, disconnectReason, lastError, reconnectAttempt
        );
    }

    public static void addConnectionStateListener(ConnectionStateListener listener, boolean emitCurrent) {
        if (listener == null) return;
        stateListeners.add(listener);
        if (emitCurrent) listener.onConnectionStateChanged(getConnectionSnapshot());
    }

    public static void removeConnectionStateListener(ConnectionStateListener listener) {
        stateListeners.remove(listener);
    }

    public static List<FaceBox> faceDetection(Bitmap bitmap, FaceDetectionParam param) {
        try {
            FFacioOptionsParcel options = null;
            if (param != null) {
                options = new FFacioOptionsParcel();
                options.checkLiveness = param.check_liveness;
                options.livenessLevel = param.check_liveness_level;
                options.checkEyeCloseness = param.check_eye_closeness;
                options.checkFaceOcclusion = param.check_face_occlusion;
                options.checkMouthOpened = param.check_mouth_opened;
                options.estimateAgeGender = param.estimate_age_gender;
            }
            List<FFacioFaceParcel> detected = requireClient().detect(bitmap, options);
            if (detected == null || detected.isEmpty()) return Collections.emptyList();
            List<FaceBox> result = new ArrayList<>(detected.size());
            for (FFacioFaceParcel face : detected) result.add(fromParcel(face));
            return result;
        } catch (Exception error) {
            throw runtimeError("face detection", error);
        }
    }

    public static byte[] templateExtraction(Bitmap bitmap, FaceBox face) {
        try { return requireClient().extractTemplate(bitmap, toParcel(face)); }
        catch (Exception error) { throw runtimeError("template extraction", error); }
    }

    public static float similarityCalculation(byte[] first, byte[] second) {
        try { return requireClient().compare(first, second); }
        catch (Exception error) { throw runtimeError("similarity calculation", error); }
    }

    public static Bitmap yuv2Bitmap(byte[] yuv, int width, int height, int rotation) {
        try { return requireClient().yuvToBitmap(yuv, width, height, rotation); }
        catch (Exception error) { throw runtimeError("YUV conversion", error); }
    }

    private static void ensureClientLocked() {
        if (client != null) return;
        client = new FFacioRuntimeClient(applicationContext, new FFacioRuntimeClient.ConnectionListener() {
            @Override public void onConnected(int result) {
                reconnectAttempt = 0;
                disconnectReason = DisconnectReason.NONE;
                lastError = null;
                finishInitialization(result);
            }

            @Override public void onDisconnected() {
                handleConnectionLoss(DisconnectReason.SERVICE_DISCONNECTED, null);
            }

            @Override public void onBinderDied() {
                handleConnectionLoss(DisconnectReason.BINDER_DIED, null);
            }

            @Override public void onError(Throwable error) {
                finishInitialization(SDK_INIT_ERROR);
                handleConnectionLoss(DisconnectReason.ERROR, error);
            }
        });
    }

    private static void handleConnectionLoss(DisconnectReason reason, Throwable error) {
        synchronized (LOCK) {
            ready = false;
            connecting = false;
            disconnectReason = reason;
            lastError = error;
        }
        publishState();
        scheduleReconnect();
    }

    private static void scheduleReconnect() {
        Context context = applicationContext;
        if (manualDisconnect || context == null) return;
        int attempt;
        synchronized (LOCK) {
            if (reconnectAttempt >= 3) return;
            attempt = ++reconnectAttempt;
        }
        publishState();
        long delay = Math.min(3000L, 750L * attempt);
        mainHandler.postAtTime(() -> {
            if (!manualDisconnect && !ready) reconnect(context);
        }, RECONNECT_TOKEN, android.os.SystemClock.uptimeMillis() + delay);
    }

    private static FFacioRuntimeClient requireClient() {
        FFacioRuntimeClient result = client;
        if (!ready || result == null || !result.isConnected()) {
            throw new IllegalStateException("FFacio Runtime is not connected");
        }
        return result;
    }

    private static void finishInitialization(int result) {
        List<InitializationListener> listeners;
        synchronized (LOCK) {
            initializationResult = result;
            ready = result == SDK_SUCCESS;
            connecting = false;
            listeners = new ArrayList<>(pendingListeners);
            pendingListeners.clear();
        }
        for (InitializationListener listener : listeners) {
            try { listener.onInitialized(result); } catch (RuntimeException ignored) { }
        }
        publishState();
    }

    private static void publishState() {
        ConnectionSnapshot snapshot = getConnectionSnapshot();
        for (ConnectionStateListener listener : stateListeners) {
            try { listener.onConnectionStateChanged(snapshot); } catch (RuntimeException ignored) { }
        }
    }

    private static FaceBox fromParcel(FFacioFaceParcel source) {
        FaceBox face = new FaceBox();
        face.x1 = source.x1; face.y1 = source.y1; face.x2 = source.x2; face.y2 = source.y2;
        face.yaw = source.yaw; face.roll = source.roll; face.pitch = source.pitch;
        face.face_quality = source.quality; face.face_luminance = source.luminance;
        face.liveness = source.liveness; face.left_eye_closed = source.leftEyeClosed;
        face.right_eye_closed = source.rightEyeClosed; face.face_occlusion = source.occlusion;
        face.mouth_opened = source.mouthOpened; face.age = source.age; face.gender = source.gender;
        face.landmarks_68 = source.landmarks68 == null ? null : source.landmarks68.clone();
        return face;
    }

    private static FFacioFaceParcel toParcel(FaceBox source) {
        if (source == null) throw new IllegalArgumentException("face must not be null");
        FFacioFaceParcel face = new FFacioFaceParcel();
        face.x1 = source.x1; face.y1 = source.y1; face.x2 = source.x2; face.y2 = source.y2;
        face.yaw = source.yaw; face.roll = source.roll; face.pitch = source.pitch;
        face.quality = source.face_quality; face.luminance = source.face_luminance;
        face.liveness = source.liveness; face.leftEyeClosed = source.left_eye_closed;
        face.rightEyeClosed = source.right_eye_closed; face.occlusion = source.face_occlusion;
        face.mouthOpened = source.mouth_opened; face.age = source.age; face.gender = source.gender;
        face.landmarks68 = source.landmarks_68 == null ? null : source.landmarks_68.clone();
        return face;
    }

    private static IllegalStateException runtimeError(String operation, Exception error) {
        return new IllegalStateException("FFacio Runtime " + operation + " failed", error);
    }
}
