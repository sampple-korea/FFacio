package com.kbyai.facesdk;

/** 기존 앱이 사용하는 얼굴 검출·속성 결과 형식을 유지합니다. */
public final class FaceBox {
    public int x1, y1, x2, y2;
    public float yaw, roll, pitch, face_quality, face_luminance, liveness;
    public float left_eye_closed, right_eye_closed, face_occlusion, mouth_opened;
    public int age, gender;
    public float[] landmarks_68;

    public FaceBox() { }
}
