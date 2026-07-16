package io.ffacio.ipc;

import android.os.ParcelFileDescriptor;
import io.ffacio.ipc.FFacioFaceParcel;
import io.ffacio.ipc.FFacioOptionsParcel;

interface IFFacioRuntime {
    int initialize();
    List<FFacioFaceParcel> detect(in ParcelFileDescriptor image, in FFacioOptionsParcel options);
    byte[] extractTemplate(in ParcelFileDescriptor image, in FFacioFaceParcel face);
    float compare(in byte[] firstTemplate, in byte[] secondTemplate);
    ParcelFileDescriptor yuvToPng(in ParcelFileDescriptor yuv, int width, int height, int rotation);
}
