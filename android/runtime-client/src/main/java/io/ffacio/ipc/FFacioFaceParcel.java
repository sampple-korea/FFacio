package io.ffacio.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public final class FFacioFaceParcel implements Parcelable {
    public int x1, y1, x2, y2;
    public float yaw, roll, pitch, quality, luminance, liveness;
    public float leftEyeClosed, rightEyeClosed, occlusion, mouthOpened;
    public int age, gender;
    public float[] landmarks68;

    public FFacioFaceParcel() { }

    private FFacioFaceParcel(Parcel in) {
        x1 = in.readInt(); y1 = in.readInt(); x2 = in.readInt(); y2 = in.readInt();
        yaw = in.readFloat(); roll = in.readFloat(); pitch = in.readFloat();
        quality = in.readFloat(); luminance = in.readFloat(); liveness = in.readFloat();
        leftEyeClosed = in.readFloat(); rightEyeClosed = in.readFloat();
        occlusion = in.readFloat(); mouthOpened = in.readFloat();
        age = in.readInt(); gender = in.readInt(); landmarks68 = in.createFloatArray();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(x1); dest.writeInt(y1); dest.writeInt(x2); dest.writeInt(y2);
        dest.writeFloat(yaw); dest.writeFloat(roll); dest.writeFloat(pitch);
        dest.writeFloat(quality); dest.writeFloat(luminance); dest.writeFloat(liveness);
        dest.writeFloat(leftEyeClosed); dest.writeFloat(rightEyeClosed);
        dest.writeFloat(occlusion); dest.writeFloat(mouthOpened);
        dest.writeInt(age); dest.writeInt(gender); dest.writeFloatArray(landmarks68);
    }

    public static final Creator<FFacioFaceParcel> CREATOR = new Creator<FFacioFaceParcel>() {
        @Override public FFacioFaceParcel createFromParcel(Parcel in) {
            return new FFacioFaceParcel(in);
        }

        @Override public FFacioFaceParcel[] newArray(int size) {
            return new FFacioFaceParcel[size];
        }
    };
}
