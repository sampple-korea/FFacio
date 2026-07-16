# FFacio Android 0.6.2 — Runtime Demo 정렬 버전

FFacio는 카메라 기반 출입 인증과 사용자·Head Admin·HTTPS 릴레이 관리 흐름을 담당하는 Android 앱입니다. 얼굴 검출, 속성 분석, YUV 변환, 템플릿 추출과 템플릿 비교는 별도 설치되는 **FFacio Runtime**(`com.kbyai.faceattribute`)을 Binder/AIDL로 호출합니다.

## 구조

```text
FFacio UI / 사용자·관리자·릴레이 로직
        ↓
runtime-client (FaceSDK 호환 API + AIDL Binder client)
        ↓
FFacio Runtime APK / FFacioRuntimeService
        ↓
Runtime facesdk / JNI / 모델
```

FFacio APK에는 OpenCV, ONNX Runtime, ArcFace, YuNet, SFace, MiniFASNet 또는 얼굴 모델 파일이 들어가지 않습니다.

## 0.6.2 변경 사항

- 고개 돌리기 챌린지와 관련 상태·판정·안내를 완전히 제거했습니다.
- 정면·좌·우 5장 등록, 자세 유지, 반복 자세 판정, 샘플 간 일관성 검사와 대표 샘플 선정 절차를 제거했습니다.
- 등록은 Runtime Demo 기준을 통과한 얼굴을 1200ms 안정적으로 관찰한 뒤 품질이 가장 좋은 Runtime 템플릿 하나만 저장합니다.
- 화면에 여러 얼굴이 있어도 사각형 면적이 가장 큰 얼굴 하나만 등록·인증합니다.
- 인증은 Runtime 라이브니스(켜진 경우), 품질 0.50, 유사도 0.80, 1위·2위 차이 0.03을 사용하며 안정화 프레임은 1입니다.
- 이 버전 첫 실행 시 이전 등록 사용자를 전부 삭제하고 얼굴 인식 설정을 Runtime Demo 기본값(라이브니스 켬·레벨 0·가림 검사 끔)으로 초기화합니다. 이후 스키마 5의 대표 Runtime 템플릿 하나만 저장·비교합니다.
- Runtime Demo처럼 인증·등록 모두 눈·입 속성을 요청하되, 눈·입 기준은 등록에서만 통과 조건으로 사용합니다. 나이·성별 추정은 요청하지 않습니다.
- 라이브니스를 끄면 과거의 동작 챌린지로 대체하지 않고 품질·유사도 기준만 사용합니다.
- Runtime Demo와 달리 매 프레임 임시 파일을 덮어쓰기·동기화하던 추가 I/O를 제거해, 앱 전용 캐시 파일을 즉시 삭제합니다.
- 저장 작업은 템플릿의 독립 복사본을 사용하며, 손상된 개별 사용자 레코드는 전체 저장소를 막지 않고 제외합니다.

## Runtime Demo 기준 기본값

| 항목 | 값 |
|---|---:|
| 라이브니스 | 켜짐, 레벨 0, 임계값 0.70 |
| 식별 임계값 | 0.80 |
| 1위·2위 불확실 구간 | 0.03 |
| 품질 임계값 | 0.50 |
| 밝기 | 0~255 |
| yaw / pitch / roll | 각각 최대 10도 |
| 양쪽 눈 감김 | 각각 0.80 이하 |
| 얼굴 가림 | 기본 꺼짐, 임계값 0.50 |
| 입 벌림 | 0.50 이하 |
| 등록 얼굴 크기 | 80~1200px |
| 등록 안정화 | 1200ms |
| 프레임 분석 간격 | 180ms |
| 인증 결과 표시 | 3500ms |
| 인증 안정화 | **1프레임** |

## 유지되는 기능

- Head Admin 얼굴 승인과 Android 화면잠금 복구 경로
- 암호화된 사용자·릴레이 설정 저장, 승인·판정 로그
- HTTPS 릴레이 단일 실행과 상태 점검
- Runtime 비교의 I/O 스레드 실행, 제한 시간, 오래된 결과 폐기
- Runtime 연결 상태 구독·자동 재연결과 카메라 정지 감시
- 관리자 진단 카드와 프레임별 Runtime 호출 계측
- Runtime 라이브니스, 라이브니스 레벨, 얼굴 가림 검사 설정

## 중요한 호환 조건

FFacio Runtime 서비스 권한은 `signature` 보호 수준입니다. **FFacio APK와 Runtime APK는 반드시 같은 인증서로 서명해야 합니다.** Runtime을 먼저 설치한 뒤 FFacio를 설치합니다.

기존 등록 데이터는 새 단일 템플릿 정책의 첫 실행 초기화에서 전부 삭제됩니다. 릴레이 설정은 유지되며, 사용자는 모두 다시 등록해야 합니다.

## 빌드

요구 환경은 JDK 17, Android SDK 36, 지원 기기 ABI `arm64-v8a` 또는 `armeabi-v7a`입니다.

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release 빌드는 Runtime 프로젝트와 FFacio 프로젝트에 같은 `FFACIO_KEYSTORE_*` 환경 변수를 사용해야 합니다. 실제 ARM 기기 통합 확인은 `scripts/verify_android_device.ps1`을 사용합니다.

## 소스 정적 점검

```bash
python3 scripts/verify_source_static.py
```

이 검사는 XML과 소스 구조, Runtime 필수 호출, 폐기한 등록·인증 로직의 잔존 여부, 이전 자체 엔진 흔적, 모델·서명키 포함 여부와 주요 IPC 방어 코드를 확인합니다.

세부 내용은 [docs/android.md](docs/android.md)와 [FFACIO_RUNTIME_REFACTOR_REPORT.md](FFACIO_RUNTIME_REFACTOR_REPORT.md)를 참고하세요.
