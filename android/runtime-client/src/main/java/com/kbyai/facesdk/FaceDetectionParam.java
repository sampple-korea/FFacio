package com.kbyai.facesdk;

/** 기존 앱 API와 런타임 IPC 옵션을 연결하는 호환 데이터 객체입니다. */
public final class FaceDetectionParam {
    public boolean check_liveness;
    public int check_liveness_level;
    public boolean check_eye_closeness;
    public boolean check_face_occlusion;
    public boolean check_mouth_opened;
    public boolean estimate_age_gender;
}
