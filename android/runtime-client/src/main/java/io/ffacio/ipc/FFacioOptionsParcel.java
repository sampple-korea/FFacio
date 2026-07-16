package io.ffacio.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public final class FFacioOptionsParcel implements Parcelable {
    public boolean checkLiveness;
    public int livenessLevel;
    public boolean checkEyeCloseness;
    public boolean checkFaceOcclusion;
    public boolean checkMouthOpened;
    public boolean estimateAgeGender;

    public FFacioOptionsParcel() { }

    private FFacioOptionsParcel(Parcel in) {
        checkLiveness = in.readByte() != 0;
        livenessLevel = in.readInt();
        checkEyeCloseness = in.readByte() != 0;
        checkFaceOcclusion = in.readByte() != 0;
        checkMouthOpened = in.readByte() != 0;
        estimateAgeGender = in.readByte() != 0;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (checkLiveness ? 1 : 0));
        dest.writeInt(livenessLevel);
        dest.writeByte((byte) (checkEyeCloseness ? 1 : 0));
        dest.writeByte((byte) (checkFaceOcclusion ? 1 : 0));
        dest.writeByte((byte) (checkMouthOpened ? 1 : 0));
        dest.writeByte((byte) (estimateAgeGender ? 1 : 0));
    }

    public static final Creator<FFacioOptionsParcel> CREATOR = new Creator<FFacioOptionsParcel>() {
        @Override public FFacioOptionsParcel createFromParcel(Parcel in) {
            return new FFacioOptionsParcel(in);
        }

        @Override public FFacioOptionsParcel[] newArray(int size) {
            return new FFacioOptionsParcel[size];
        }
    };
}
