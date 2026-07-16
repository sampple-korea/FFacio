package io.ffacio.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;

import io.ffacio.ipc.FFacioFaceParcel;
import io.ffacio.ipc.FFacioOptionsParcel;
import io.ffacio.ipc.IFFacioRuntime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 다른 applicationId의 앱에서 FFacio 런타임 APK를 호출하는 경량 클라이언트입니다. */
public final class FFacioRuntimeClient {
    public static final String RUNTIME_PACKAGE = "com.kbyai.faceattribute";
    private static final String RUNTIME_SERVICE = "io.ffacio.runtime.FFacioRuntimeService";

    public interface ConnectionListener {
        void onConnected(int initializationResult);
        void onDisconnected();
        void onError(Throwable error);

        /** Binder 프로세스가 사라진 경우의 선택적 상세 알림입니다. */
        default void onBinderDied() { onDisconnected(); }
    }

    private final Context context;
    private final ConnectionListener listener;
    private volatile IFFacioRuntime remote;
    private final Object connectionLock = new Object();
    private volatile boolean bound;
    private volatile boolean connecting;
    private volatile long connectedAtElapsedRealtime;
    private final AtomicBoolean deathReported = new AtomicBoolean(false);

    private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override public void binderDied() {
            synchronized (connectionLock) {
                remote = null;
                connecting = false;
            }
            if (deathReported.compareAndSet(false, true)) listener.onBinderDied();
        }
    };

    public FFacioRuntimeClient(Context context, ConnectionListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        deleteStaleTemporaryFiles();
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (connectionLock) {
                connecting = false;
                bound = true;
                remote = IFFacioRuntime.Stub.asInterface(service);
                connectedAtElapsedRealtime = SystemClock.elapsedRealtime();
            }
            deathReported.set(false);
            try {
                service.linkToDeath(deathRecipient, 0);
                listener.onConnected(remote.initialize());
            } catch (Throwable error) {
                releaseDeadBinding();
                listener.onError(error);
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            clearRemote(false);
            if (deathReported.compareAndSet(false, true)) listener.onDisconnected();
        }

        @Override public void onBindingDied(ComponentName name) {
            releaseDeadBinding();
            if (deathReported.compareAndSet(false, true)) listener.onBinderDied();
        }

        @Override public void onNullBinding(ComponentName name) {
            releaseDeadBinding();
            listener.onError(new IllegalStateException("FFacio Runtime returned a null binder"));
        }
    };

    public boolean connect() {
        synchronized (connectionLock) {
            if (remote != null || connecting || bound) return true;
            connecting = true;
            deathReported.set(false);
        }
        Intent intent = new Intent().setComponent(new ComponentName(RUNTIME_PACKAGE, RUNTIME_SERVICE));
        boolean didBind;
        try {
            didBind = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable error) {
            synchronized (connectionLock) { connecting = false; }
            listener.onError(error);
            return false;
        }
        synchronized (connectionLock) {
            bound = didBind;
            if (!didBind) connecting = false;
        }
        if (!didBind) listener.onError(new IllegalStateException("FFacio Runtime service is unavailable"));
        return didBind;
    }

    public boolean isConnected() { return remote != null; }

    public boolean isConnecting() { return connecting; }

    public boolean isBound() { return bound; }

    public long getConnectedAtElapsedRealtime() { return connectedAtElapsedRealtime; }

    public void disconnect() {
        boolean shouldUnbind;
        synchronized (connectionLock) {
            shouldUnbind = bound;
            bound = false;
            connecting = false;
            unlinkDeathRecipient();
            remote = null;
        }
        if (shouldUnbind) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // 비동기 연결 해제와 명시적 해제가 경합한 경우 이미 해제된 상태입니다.
            }
        }
    }

    public boolean reconnect() {
        disconnect();
        return connect();
    }

    public List<FFacioFaceParcel> detect(Bitmap bitmap, FFacioOptionsParcel options)
            throws IOException, RemoteException {
        requireUsableBitmap(bitmap);
        IFFacioRuntime service = requireRemote();
        File image = writeBitmap(bitmap);
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return service.detect(fd, options);
        } finally {
            deleteTemporaryFile(image);
        }
    }

    public byte[] extractTemplate(Bitmap bitmap, FFacioFaceParcel face)
            throws IOException, RemoteException {
        requireUsableBitmap(bitmap);
        if (face == null) throw new IllegalArgumentException("face must not be null");
        IFFacioRuntime service = requireRemote();
        File image = writeBitmap(bitmap);
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return service.extractTemplate(fd, face);
        } finally {
            deleteTemporaryFile(image);
        }
    }

    public float compare(byte[] firstTemplate, byte[] secondTemplate) throws RemoteException {
        if (firstTemplate == null || firstTemplate.length == 0 || secondTemplate == null || secondTemplate.length == 0) {
            throw new IllegalArgumentException("templates must not be empty");
        }
        if (firstTemplate.length != secondTemplate.length) {
            throw new IllegalArgumentException("template sizes must match");
        }
        return requireRemote().compare(firstTemplate, secondTemplate);
    }

    public Bitmap yuvToBitmap(byte[] yuv, int width, int height, int rotation)
            throws IOException, RemoteException {
        if (yuv == null || width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException("invalid YUV input");
        }
        long expectedSize = (long) width * (long) height * 3L / 2L;
        if (expectedSize > Integer.MAX_VALUE || yuv.length != (int) expectedSize) {
            throw new IllegalArgumentException("YUV input size does not match width and height");
        }
        if (rotation < 1 || rotation > 8) {
            throw new IllegalArgumentException("invalid native orientation code");
        }
        IFFacioRuntime service = requireRemote();
        File input = File.createTempFile("ffacio-yuv-", ".bin", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(input)) {
                output.write(yuv);
            }
            try (ParcelFileDescriptor inFd = ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)) {
                ParcelFileDescriptor returned = service.yuvToPng(inFd, width, height, rotation);
                if (returned == null) throw new IOException("Runtime returned no converted image");
                try (ParcelFileDescriptor outFd = returned) {
                    Bitmap result = BitmapFactory.decodeFileDescriptor(outFd.getFileDescriptor());
                    if (result == null) throw new IOException("Could not decode converted image");
                    return result;
                }
            }
        } finally {
            deleteTemporaryFile(input);
        }
    }

    private File writeBitmap(Bitmap bitmap) throws IOException {
        requireUsableBitmap(bitmap);
        File file = File.createTempFile("ffacio-image-", ".png", context.getCacheDir());
        boolean complete = false;
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("Could not encode bitmap");
                }
            }
            complete = true;
            return file;
        } finally {
            if (!complete) deleteTemporaryFile(file);
        }
    }

    private void deleteStaleTemporaryFiles() {
        File[] stale = context.getCacheDir().listFiles((directory, name) ->
                name.startsWith("ffacio-image-") || name.startsWith("ffacio-yuv-")
        );
        if (stale == null) return;
        // Startup cleanup must stay cheap: these are leftovers from a previous process,
        // so unlink them without synchronously overwriting potentially large files.
        for (File file : stale) {
            if (!file.delete() && file.exists()) file.deleteOnExit();
        }
    }

    private static void deleteTemporaryFile(File file) {
        if (file == null || !file.exists()) return;
        try (RandomAccessFile random = new RandomAccessFile(file, "rw")) {
            byte[] zeros = new byte[16 * 1024];
            long remaining = random.length();
            random.seek(0L);
            while (remaining > 0L) {
                int count = (int) Math.min(zeros.length, remaining);
                random.write(zeros, 0, count);
                remaining -= count;
            }
            random.getFD().sync();
        } catch (Throwable ignored) {
            // Cache cleanup is best effort; the private file is still removed below.
        }
        if (!file.delete() && file.exists()) file.deleteOnExit();
    }

    private static void requireUsableBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            throw new IllegalArgumentException("bitmap must be usable");
        }
    }

    private IFFacioRuntime requireRemote() {
        IFFacioRuntime result = remote;
        if (result == null) throw new IllegalStateException("FFacio Runtime is not connected");
        return result;
    }

    private void clearRemote(boolean clearBound) {
        synchronized (connectionLock) {
            unlinkDeathRecipient();
            remote = null;
            connecting = false;
            if (clearBound) bound = false;
        }
    }

    private void unlinkDeathRecipient() {
        IFFacioRuntime current = remote;
        if (current == null) return;
        try {
            current.asBinder().unlinkToDeath(deathRecipient, 0);
        } catch (Throwable ignored) {
            // 이미 죽은 Binder는 unlink할 수 없습니다.
        }
    }

    private void releaseDeadBinding() {
        boolean shouldUnbind;
        synchronized (connectionLock) {
            shouldUnbind = bound;
            clearRemote(true);
        }
        if (shouldUnbind) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // 시스템이 이미 죽은 binding을 정리한 경우입니다.
            }
        }
    }
}
