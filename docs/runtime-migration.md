> **0.9.0 안내:** 현재 문 제어는 SmartThings가 아니라 별도 ITSOKEY Runtime Binder로 이전되었습니다. 이 문서의 SmartThings 내용은 이전 버전 역사 기록입니다. 현재 구현은 `../ITSOKEY_INTEGRATION.md`를 참고하세요.

# 자체 엔진에서 FFacio Runtime으로의 마이그레이션

## 제거한 경로

- OpenCV 초기화와 YuNet 얼굴 검출
- ONNX Runtime 세션과 ArcFace 모델 로딩
- SFace 정렬·fallback 임베딩
- MiniFASNet 앱 내 라이브니스
- 모델 asset 복사·manifest 생성·다운로드 스크립트
- 512차원 `FloatArray` 정규화, centroid, 자체 cosine 계산
- 모델 준비 로그를 기다리는 emulator 검증

## 새 경로

- `:runtime-client` Android library 모듈
- Runtime 설치 확인과 process-wide Binder 세션
- Demo와 같은 YUV orientation 변환
- Runtime의 전체 얼굴 속성 요청
- Runtime 바이트 템플릿을 원형 그대로 저장
- 모든 동일인·중복·대표 샘플·1:N 판정에 Runtime `compare()` 사용
- Runtime 연결·초기화·서명 오류를 UI 상태로 전달
- Runtime APK와 FFacio APK의 signer 인증서 일치 검증

## 의도적으로 유지한 FFacio 로직

사용자 이름, 등록 화면, Head Admin 권한, Android 화면잠금 복구, 암호화 저장, 로그, SmartThings 도어락, 카메라 watchdog과 공개 화면 개인정보 정책은 제품 로직으로 유지했습니다. 과거 다섯 자세 수집과 능동 고개 챌린지는 폐기했습니다. 다만 이 로직이 받는 얼굴 데이터의 형식과 판정 입력은 모두 Runtime 결과로 바뀌었습니다.

## 저장소 전환 정책

이전 임베딩을 새 템플릿으로 수학적으로 변환하는 코드는 만들지 않았습니다. 서로 다른 엔진의 표현 공간을 변환했다고 가장하면 오인증 위험이 생기기 때문입니다. 이 버전 첫 실행에서 이전 사용자 얼굴 데이터는 자동으로 전부 삭제되며, 모든 사용자를 새 Runtime 단일 템플릿 방식으로 다시 등록합니다. 기존 릴레이 설정은 폐기되며 SmartThings 설정을 새로 입력해야 하고, 얼굴 인식 설정은 Runtime Demo 기본값(라이브니스 켬·레벨 0·가림 검사 끔)으로 초기화됩니다.

## Runtime client 동기화

`android/runtime-client`는 FFacio-Runtime의 `client` 모듈 계약과 함께 움직입니다. 현재 사본은 AIDL·Parcelable·FaceSDK 호환 API를 유지하면서 템플릿 크기, NV21 길이/orientation, 임시 파일 실패 정리를 추가로 검증합니다. 다음 파일군을 임의로 일부만 갱신하면 안 됩니다.

- `src/main/aidl/io/ffacio/ipc/*`
- `src/main/java/io/ffacio/ipc/*`
- `src/main/java/io/ffacio/sdk/FFacioRuntimeClient.java`
- `src/main/java/com/kbyai/facesdk/*`
- client Manifest의 permission/query

AIDL 메서드, Runtime package/service 이름, Parcelable 필드 순서 또는 템플릿 형식이 바뀌면 양쪽 APK 버전을 함께 올리고 재등록 필요 여부를 명시해야 합니다.
