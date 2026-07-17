# FFacio 0.8.2 검증 기록 — 2026-07-17

## 확인한 구현

- Runtime Demo와 동일한 Fotoapparat NV21·회전·미러링 입력 경로
- 가장 큰 얼굴 한 명 선택과 CenterCrop 좌표 기반 실시간 사각 추적
- 카메라가 켜진 동안 중복 안내 문구 제거; 상태 안내는 하단 패널 한 곳에서 표시
- 눈·입 속성 오검출은 차단하지 않고, 심한 자세(yaw 35°, pitch/roll 30° 초과)만 차단
- 1200ms 최적 원본 프레임 재검출·재판정·단일 템플릿 등록
- 기존 사용자 전원 중복 비교, 1회 재시도, 불완전 비교와 유사도 0.80 이상 등록 차단
- 사용자 이름 정규화·제어문자/길이 검사·대소문자 무시 중복 차단
- 인증 안정화 1프레임을 유지하되 라이브니스·품질·면적·심한 자세·전체 비교·유사도·후보 격차를 fail-closed로 적용
- Runtime 유사도·라이브니스·품질·가림 값은 0.0~1.0 범위만 허용
- SmartThings 기기 상태/명령 경로와 `main`/`lock`/`unlock` 명령 본문
- 라이브니스 비활성화 시 SmartThings 문 열림과 Head Admin 얼굴 승인 자동 비활성화
- access token Keystore AES-GCM 암호화, preference key AAD 결합, 고정 API host, redirect 금지, 응답 크기 제한, 단일 실행·쿨다운
- SmartThings 저장 실패 시 Device ID·token 암호문·활성화 플래그 보안 롤백

## 실행한 검증

- `scripts/verify_source_static.py`: XML, 괄호·문자열 구조, 필수 Runtime·보안·SmartThings 표식, 카메라 내부 중복 문구, 금지된 자체 엔진·과거 등록 로직·비밀키 검사 통과
- `scripts/verify_runtime_demo_alignment.py`: AIDL/Parcelable/FaceBox/옵션 계약 7개 바이트 일치, Fotoapparat AAR SHA-256 일치, Demo 기본값 18개 확인
- Kotlin PSI 전체 소스 구문 감사: Kotlin 파일 10개 오류 없음
- Kotlin/JVM 실행 검사: 실제 `RuntimeDemoPolicy.kt`와 Runtime `FaceBox`/`FaceDetectionParam`을 컴파일해 얼굴 정책 18개 통과
- Kotlin/JVM 실행 검사: 실제 소스 선언을 자동 추출해 중복 등록·인증·SmartThings 입력·보안 gate·CenterCrop 추적 63개 통과
- Kotlin/JVM 실행 검사: 실제 SmartThings JSON 판정 함수와 테스트용 `org.json` 계약으로 상태/명령 응답 11개 통과
- JUnit 회귀 테스트 소스 102개 확인
- `git diff --check` 통과

## 환경상 제외

현재 실행 환경에는 Android SDK의 `android.jar`/Build Tools와 Gradle 8.13 배포본이 없어 전체 `testDebugUnitTest`, `lintDebug`, `assembleDebug`를 완료하지 못했습니다. 따라서 APK 전체 빌드 성공은 주장하지 않습니다. 실제 카메라, Runtime Binder, SmartThings 기기 제어는 동일 인증서로 서명한 ARM Android 기기와 권한 있는 SmartThings token으로 최종 현장 확인이 필요합니다.
