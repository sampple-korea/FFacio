# FFacio Android 0.6.0 — FFacio Runtime 기반

FFacio는 카메라 기반 출입 인증과 사용자·Head Admin·릴레이 관리 흐름을 담당하는 Android 앱입니다. 0.5.0부터 얼굴 엔진을 앱에 직접 포함하지 않으며, 별도 설치되는 **FFacio Runtime**(`com.kbyai.faceattribute`)을 Binder/AIDL로 호출합니다.

## 바뀐 구조

```text
FFacio UI / 사용자·관리자·릴레이 로직
        ↓
runtime-client (FaceSDK 호환 API + AIDL Binder client)
        ↓
FFacio Runtime APK / FFacioRuntimeService
        ↓
Runtime facesdk / JNI / 모델
```

FFacio APK에는 OpenCV, ONNX Runtime, ArcFace, YuNet, SFace, MiniFASNet 또는 모델 파일이 들어가지 않습니다. 얼굴 검출, 속성 분석, YUV 변환, 템플릿 추출, 템플릿 비교는 Runtime에서 처리합니다.


## 0.5.1 안정화 내용

- Runtime 템플릿 비교를 UI 스레드에서 분리하고 8초 제한·단일 실행·오래된 결과 폐기를 적용했습니다. 동기식 Binder 호출이 계속 응답하지 않으면 10초 정지 감시가 판정 토큰을 무효화하고 Runtime 연결을 다시 초기화합니다.
- 비교 중에는 새 카메라 분석을 초기에 건너뛰어 Binder 호출 중첩과 카메라 watchdog 오판을 막습니다.
- YUV plane의 실제 ByteBuffer 시작 위치, row/pixel stride, crop 정렬을 검증합니다.
- Runtime 연결 상태 변화와 사용자 저장소 로딩을 분리해 재연결 때 메모리 사용자 목록이 덮어써지지 않습니다. 백그라운드 비교에는 사용자 템플릿의 독립 복사본을 사용해 삭제·초기화와의 경합을 막고 작업 종료 시 복사본을 지웁니다.
- 저장 스키마 v3에서 손상되거나 크기가 섞인 샘플을 조용히 버리지 않고 비호환 처리합니다.
- Runtime IPC 임시 파일을 현재 요청 종료 시 가능한 범위에서 덮어쓴 뒤 삭제하고, 이전 프로세스의 잔여 파일은 앱 시작을 막지 않도록 빠르게 제거합니다. 템플릿 크기·NV21 길이·orientation도 검증합니다.
- 만료된 관리자 세션이 자동 잠금 직전 관리 작업을 통과하지 못하도록 실행 시각을 다시 검사하고, 릴레이는 파싱 가능한 HTTPS URL과 토큰이 모두 있을 때만 활성화합니다. 앱 전체의 평문 HTTP도 차단했습니다.

## 실제 사용 중인 Runtime 기능

- CameraX `YUV_420_888` 프레임을 NV21로 정리한 뒤 Runtime `yuv2Bitmap()` 호출
- `faceDetection()`으로 얼굴 수와 좌표, 68점 랜드마크, yaw/pitch/roll, 품질, 밝기 요청
- 선택 가능한 Runtime 라이브니스와 눈 감김·가림·입 벌림 검사
- 나이 추정 원값과 성별 코드 요청(등록 화면 진단에만 표시)
- `templateExtraction()`으로 Runtime 전용 바이트 템플릿 생성
- `similarityCalculation()`으로 등록 중복 검사, 대표 템플릿 선택, 1:N 인증과 1·2위 모호성 검사
- Binder 연결 상태 구독, Runtime 사망 시 자동 재연결, 초기화 코드별 오류 안내
- 관리자 진단 카드: Runtime 패키지 버전, Binder 상태, 초기화 결과, 끊김 사유, 자동 재연결 횟수, 수동 재연결
- 프레임별 Runtime 호출 계측: YUV 변환·검출+속성·템플릿 추출 시간 (검출과 속성은 하나의 AIDL 호출이므로 속성만의 시간은 표시하지 않음)
- 라이브니스 검사 레벨(엔진 계약에 그대로 전달)과 얼굴 가림 검사 토글 — 끄면 Runtime 검출 요청 옵션에서도 제외

## 유지되는 FFacio 기능

- 5단계 정면/좌/우 등록 흐름과 안정화 시간
- 다중 등록 샘플, 대표 템플릿 선정, 오염된 등록 세트 차단
- 최고 후보 점수·2위 점수 차이·복수 샘플 지지를 함께 쓰는 인증
- Head Admin 얼굴 승인과 Android 화면잠금 복구 경로
- 암호화된 사용자·릴레이 설정 저장, 승인·판정 로그
- HTTPS 릴레이 단일 실행과 상태 점검
- 카메라 정지 감시, 몰입형 출입 단말 화면, 개인정보 노출 최소화

## 중요한 호환 조건

FFacio Runtime 서비스 권한은 `signature` 보호 수준입니다. **FFacio APK와 Runtime APK는 반드시 같은 인증서로 서명해야 합니다.** Runtime을 먼저 설치한 뒤 FFacio를 설치합니다.

기존 자체 엔진의 Float 임베딩은 Runtime 템플릿과 변환·비교하지 않습니다. 기존 사용자 이름은 복구·삭제를 위해 읽을 수 있지만 인증에는 사용하지 않으며, Runtime 전환 뒤 다시 등록해야 합니다.

## 빌드

요구 환경은 JDK 17, Android SDK 36, 지원 기기 ABI `arm64-v8a` 또는 `armeabi-v7a`입니다.

```powershell
$env:FFACIO_KEYSTORE_PATH = "C:\secure\ffacio-runtime-pair.p12"
$env:FFACIO_KEYSTORE_PASSWORD = "..."
$env:FFACIO_KEY_ALIAS = "ffacio-runtime-pair"
$env:FFACIO_KEY_PASSWORD = "..."

# 같은 환경 변수로 FFacio-Runtime의 runtime APK를 먼저 빌드
# 그다음 FFacio 빌드와 Runtime 인증서 일치 검증
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1 `
  -RuntimeApk C:\path\to\runtime-release.apk
```

직접 Gradle을 실행할 때는 다음과 같습니다.

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug 빌드도 두 프로젝트를 같은 컴퓨터의 같은 Android debug keystore로 빌드해야 서비스에 연결됩니다.

## 실제 기기 확인

Runtime 네이티브 라이브러리는 현재 ARM ABI를 대상으로 하므로 실제 ARM Android 기기에서 통합 확인합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_device.ps1 `
  -RuntimeApk C:\path\to\runtime-release.apk `
  -AppApk .\release\FFacio-Android-release.apk
```

세부 구현과 마이그레이션 근거는 [docs/android.md](docs/android.md), [docs/runtime-migration.md](docs/runtime-migration.md)를 참고하세요.

## 소스 정적 점검

```bash
python3 scripts/verify_source_static.py
```

이 검사는 XML, Kotlin/Java/Gradle 괄호 구조, Runtime 필수 호출, 이전 엔진 흔적, 모델·서명키 포함 여부와 주요 IPC 방어 코드를 확인합니다.
