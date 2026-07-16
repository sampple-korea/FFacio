# FFacio → FFacio Runtime 최종 재감사 보고서

## 최종 구조

FFacio의 화면, 사용자·Head Admin 관리, 등록 절차, 출입 승인 정책, 암호화 저장소, 릴레이와 카메라 수명주기는 유지했다. 앱 내부 얼굴 엔진은 제거하고 초기화, YUV 변환, 얼굴 검출·속성, 라이브니스, 템플릿 추출과 템플릿 비교를 FFacio Runtime Binder 경로로 통일했다.

이전 OpenCV/ONNX/ArcFace/SFace/MiniFASNet 템플릿은 Runtime 바이트 템플릿과 같은 표현 공간이라고 볼 근거가 없으므로 변환하지 않는다. 이전 등록자는 이름만 복구·삭제 대상으로 유지하고 인증에서는 제외해 재등록하도록 했다.

## 재감사에서 추가로 고친 문제

- Runtime 유사도 비교가 Compose/UI 스레드에서 반복 실행되던 경로를 I/O 작업으로 이동했다.
- 한 번에 하나의 등록·인증 판정만 실행하고, 8초 제한과 세대 번호·판정 토큰으로 화면·모드·Runtime 상태가 바뀐 뒤 도착한 오래된 결과를 버린다. 동기식 Binder가 제한 시간 이후에도 반환하지 않는 경우를 위해 단조 증가 시계 기준 10초 정지 감시가 판정을 무효화하고 Runtime 연결을 다시 초기화한다.
- 판정 중 새 카메라 프레임을 Runtime에 보내지 않으며, camera watchdog도 정상적인 비교 대기를 프레임 정지로 오판하지 않는다.
- Runtime 재연결 상태 변화 때마다 암호화 사용자 목록을 다시 읽어 메모리 상태를 덮어쓰던 효과를 시작 시 1회 로딩과 연결 상태 표시로 분리했다. 백그라운드 등록·인증 비교는 사용자 템플릿의 독립 복사본을 사용하고 종료 시 지워, 관리자 삭제·초기화와 비교가 겹쳐 원본 배열이 바뀌는 경합을 막았다.
- CameraX plane의 ByteBuffer 시작 위치, row stride, pixel stride와 짝수 crop 원점을 반영해 NV21을 만든다.
- 등록에서는 pitch/roll, 68점 랜드마크, 품질·밝기·눈·가림·입 상태를 엄격히 확인하고 나이·성별 요청은 등록 진단에만 사용한다.
- 인증 중 한 후보가 정해진 뒤에는 그 후보만 다시 비교해 좌우 챌린지 동안 불필요한 전체 1:N Binder 호출을 줄였다.
- Runtime 비교 성공·실패 횟수를 분리해 모든 비교가 실패한 상태를 일반적인 미인식과 구분한다.
- 저장 스키마를 v3으로 올리고, 손상되거나 크기가 섞인 보조 샘플을 조용히 버리지 않고 전체 레코드를 비호환 처리한다. 스키마 v2 Runtime 레코드는 읽을 수 있다.
- 등록 취소·삭제·초기화 시 템플릿 배열을 0으로 덮어쓰며, NV21 배열과 Runtime IPC 임시 파일도 성공·실패 경로에서 정리한다.
- Runtime client에 템플릿 크기 일치, NV21 정확한 길이, EXIF orientation 코드와 임시 파일 검증을 추가했다. 현재 요청의 얼굴·YUV 임시 파일은 가능한 범위에서 0으로 덮어쓴 뒤 삭제하고, 이전 프로세스의 잔여 파일은 시작 지연을 피하기 위해 빠르게 제거한다. 초기화 중 예외가 나면 죽은 binding을 해제해 다음 연결 시도를 막지 않는다.
- 관리자 세션 만료 시각을 관리 작업 실행 직전에 다시 확인해 자동 잠금과의 짧은 경합에서도 만료 세션이 권한으로 쓰이지 않게 했다. 릴레이 설정은 파싱 가능한 HTTPS URL·host·token을 모두 요구하며 Manifest에서도 평문 HTTP를 차단했다.
- APK 정적 검사는 첫 `classes.dex`만 보지 않고 모든 `classes*.dex`를 검사하도록 수정했다.

## Runtime 호출 경로

1. `com.kbyai.faceattribute` 설치 여부 확인
2. signature 권한 `io.ffacio.sdk.permission.BIND_RUNTIME`으로 `FFacioRuntimeService` 연결
3. 연결 listener로 Binder·초기화·라이선스 오류와 자동 재연결 상태 반영
4. CameraX `YUV_420_888` → stride/crop을 반영한 NV21
5. Runtime `yuv2Bitmap()`과 Demo 동일 orientation 매핑
6. Runtime `faceDetection()`으로 좌표, 품질, 밝기, 자세, 68점 특징점, 눈·가림·입·선택적 라이브니스·등록용 나이/성별 요청
7. Runtime `templateExtraction()`으로 바이트 템플릿 생성
8. Runtime `similarityCalculation()`만 사용해 등록 중복·샘플 일관성·대표 템플릿·1:N 인증 계산

## 등록·인증 정책

등록은 정면 → 왼쪽 → 오른쪽 → 왼쪽 → 오른쪽 5개 샘플을 모은다. 각 자세를 잠시 유지해야 하며 반복 샘플, 기존 사용자와 유사한 얼굴, 자세가 부족하거나 서로 일관되지 않은 세트는 저장하지 않는다. 가장 일관된 Runtime 템플릿 하나를 대표로, 나머지를 보조 샘플로 저장한다.

인증은 최초에 호환 사용자 전체를 비교하고 최고 점수, 2위와의 차이, 기준 이상 보조 샘플 수를 함께 확인한다. 후보가 정해진 뒤에는 같은 후보의 템플릿만 재검증하면서 Runtime 라이브니스 설정과 좌우 얼굴 돌리기·3프레임 안정화를 통과해야 승인한다.

임계값은 Runtime Demo의 기본값을 출발점으로 한 앱 정책이다. 실제 문 앞 거리, 조명, 카메라와 사용자 집단에서 오인식·거부율을 측정한 뒤 조정해야 하며 엔진 자체의 보증값으로 간주하면 안 된다.

## 저장 형식

암호화 JSON의 Runtime 사용자 레코드는 다음 메타데이터를 사용한다.

- `schema_version: 3` (`2`도 읽기 호환)
- `engine_id: ffacio.runtime.template.v1`
- `template_size`
- `template_b64`
- `samples_b64`
- `head_admin`

Runtime 레코드의 대표·보조 템플릿 중 하나라도 비어 있거나 크기가 다르면 인증 후보에서 제외한다. 호환되지 않는 Head Admin 권한도 제거해 Android 화면잠금 복구 경로로만 접근하게 한다.

## 빌드·배포 조건

FFacio와 FFacio Runtime은 signature 권한 때문에 반드시 같은 인증서로 서명해야 한다. `scripts/build_android.ps1 -RuntimeApk <runtime.apk>`는 단위 테스트, Lint, debug/release 빌드 후 두 APK signer SHA-256을 비교한다. `scripts/verify_android_device.ps1`은 ARM 기기에 Runtime → FFacio 순으로 설치하고 연결·크래시 로그를 검사한다.

소스 단계에서는 다음 명령을 사용할 수 있다.

```bash
python3 scripts/verify_source_static.py
```

## 이번 환경에서 실제 확인한 항목

- XML 전체 파싱 성공
- Kotlin/Java/Gradle 17개 파일의 문자열·주석·괄호 구조 검사 성공
- MainActivity Kotlin parser에서 구문·label 오류 없음 확인(의존 Android classpath는 없음)
- 앱 본문·Gradle에서 자체 ONNX/OpenCV 엔진과 모델 asset 경로가 제거됐는지 검사
- Runtime 설치·초기화·YUV 변환·전체 검출 옵션·템플릿 추출·Runtime 비교 호출 존재 확인
- YUV row/pixel stride, 비영점 buffer position, crop을 사용한 NV21 계산 모의 검증 통과
- 인증 점수·2위·샘플 지지·Runtime 비교 실패 계수·크기 불일치 fail-closed 정책을 독립 Kotlin smoke로 실행해 통과
- Runtime client AIDL/Parcelable/FaceSDK 계약 유지와 추가 입력 검증 확인
- 모델 파일, keystore/private key, 흔한 토큰 패턴이 결과물에 포함되지 않았는지 검사

## 이 환경에서 확인하지 못한 항목

현재 실행 환경에는 완전한 Android SDK와 Gradle/Maven 의존성 캐시가 없고 외부 Gradle 배포본을 내려받을 수 없었다. 따라서 여기서는 `testDebugUnitTest`, `lintDebug`, `assembleDebug`, 실제 APK 설치, 카메라 방향, Runtime 네이티브 라이선스·Binder와 실얼굴 인식까지 실행하지 못했다. 결과물의 PowerShell 빌드·정적 APK·실기기 검증 스크립트가 그 단계를 fail-closed 방식으로 수행한다.

## 0.6.0-runtime 후속 검증·확장 부록 (2026-07-16)

이후 완전한 Android SDK(플랫폼 36) 환경에서 다음을 실제로 수행하고 통과를 확인했다.

- `:app:assembleDebug` 빌드 성공 — 디버그 APK 산출, 레거시 ONNX/OpenCV/모델 파일 미포함을 APK 엔트리 검사로 확인
- `:app:testDebugUnitTest` 84개 테스트 전체 통과 — 이 과정에서 0.5.x 임계값 변경(0.58→0.80)을 반영하지 못한 `ApprovalLogTest`의 기존 실패 1건을 수정
- `scripts/verify_source_static.py` 통과 — 스크립트가 빌드 산출물(`.gradle`, `build/`)을 소스로 오인하던 문제 수정

또한 Runtime Demo가 사용하는 기능을 추가로 채택해 0.6.0-runtime으로 확장했다: 관리자 Runtime 진단 카드(패키지 버전, Binder 단계, 초기화 코드, 끊김 사유, 자동 재연결 횟수, 수동 재연결), 프레임별 Runtime 호출 계측(YUV 변환·검출+속성·템플릿 추출), 라이브니스 검사 레벨 설정, Demo 의미론의 얼굴 가림 검사 토글(끄면 Runtime 요청 옵션에서 제외). 실기기 설치·카메라·실얼굴 인식 검증은 여전히 ARM 기기에서 `verify_android_device.ps1`로 수행해야 한다.
