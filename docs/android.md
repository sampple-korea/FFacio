# FFacio Android Runtime 구현

## 책임 분리

FFacio는 화면, 카메라 수명주기, 사용자 등록 절차, 인증 정책, Head Admin, 암호화 저장소와 문 릴레이를 소유합니다. 얼굴 엔진과 네이티브 모델은 FFacio Runtime이 소유합니다. 앱이 Runtime 반환 템플릿을 해석하거나 자체 벡터로 변환하지 않습니다.

`android/runtime-client`는 제공된 FFacio-Runtime 저장소의 `client` 계약을 포함한 소스 모듈입니다. 원본 IPC 계약을 유지하면서 입력 크기 검증과 임시 파일 실패 정리를 보강했습니다. AIDL, Parcelable, `FFacioRuntimeClient`, 기존 호환 API인 `FaceSDK`가 들어 있습니다. Runtime의 IPC 계약이 바뀌면 이 모듈도 같은 버전으로 갱신해야 합니다.

## 시작과 연결

`MainActivity`는 Runtime 패키지 설치 여부를 먼저 확인하고 `FaceSDK.initialize()`를 호출합니다. 연결 상태 listener로 다음 상태를 구분합니다.

- 패키지 미설치
- 서비스 연결 중
- Binder 연결·SDK 초기화 완료
- 라이선스/App ID/만료/비활성/초기화 오류
- 서비스 연결 해제 또는 Binder 사망
- 서명 권한 거부

`FaceSDK`는 프로세스당 한 Binder 세션을 유지하고 연결이 사라지면 재연결합니다. Activity 종료 시 listener 제거, 연결 해제, 분석 executor 종료 순서로 정리합니다.

## 카메라와 Runtime 분석

CameraX 분석은 `YUV_420_888`을 사용합니다. 각 plane의 row stride와 pixel stride를 반영해 NV21을 만들고 Demo의 EXIF orientation 매핑과 동일한 코드를 Runtime `yuv2Bitmap()`에 넘깁니다. 전면 카메라 미러링은 Runtime 변환 단계에서 한 번만 적용합니다.

한 분석 프레임의 호출 순서는 다음과 같습니다.

1. `FaceSDK.yuv2Bitmap`
2. `FaceSDK.faceDetection` — 전체 속성 옵션 사용
3. 단일 얼굴·최소 크기·품질·자세·68점 랜드마크 검사
4. 선택적 라이브니스, 양쪽 눈, 가림, 입 벌림 검사
5. `FaceSDK.templateExtraction`
6. 등록 또는 인증 단계에서 `FaceSDK.similarityCalculation`


Runtime Binder 계약상 Bitmap과 NV21은 앱 전용 cache의 임시 파일·파일 디스크립터를 거칩니다. 현재 요청의 파일은 성공·실패 경로 모두 가능한 범위에서 0으로 덮어쓴 뒤 즉시 삭제하고, 이전 프로세스가 남긴 임시 파일은 시작 경로를 막지 않도록 빠르게 제거합니다. NV21 배열과 삭제된 템플릿 배열도 가능한 시점에 0으로 덮어씁니다. 이는 영구 얼굴 사진 저장과는 다르지만, 플래시 저장장치 전체를 대상으로 한 포렌식 삭제 보장은 아닙니다. Runtime 초기화 예외 때는 binding을 해제해 다음 재연결을 허용합니다.

분석 간격은 180ms이며 이전 분석이나 등록·인증 비교가 끝나기 전에 새 작업을 쌓지 않습니다. 템플릿 비교는 UI 스레드가 아닌 I/O 작업에서 단일 실행하며, 8초 제한과 상태 세대 번호·판정 토큰으로 오래된 결과를 폐기합니다. 동기식 Binder 호출이 10초 이상 반환하지 않으면 정지 감시가 해당 판정을 무효화하고 Runtime 연결을 다시 초기화합니다. CameraX 프레임은 항상 `finally`에서 닫습니다. Runtime 호출이 끊기면 성공으로 간주하지 않고 사용자에게 설치·서명·연결 상태를 안내합니다.

## Runtime 옵션과 앱 판정

요청 옵션은 `check_liveness`, `check_liveness_level`, `check_eye_closeness`, `check_face_occlusion`, `check_mouth_opened`, `estimate_age_gender`입니다. 0.6.0부터 라이브니스 검사 레벨과 얼굴 가림 검사는 관리자 설정입니다. 가림 검사를 끄면 결과만 숨기는 것이 아니라 Runtime 검출 요청에서 `check_face_occlusion=false`로 제외하고 인증·등록 통과 조건에도 사용하지 않습니다(런타임 데모와 같은 의미). 라이브니스 레벨 정수는 엔진 계약에 그대로 전달하며 앱은 0 또는 1만 허용합니다. FFacio는 Runtime 원점수를 다음 앱 정책에 사용합니다.

| 항목 | 기본 정책 |
|---|---:|
| 라이브니스 | 0.70 이상 |
| 얼굴 품질 | 0.50 이상 |
| 눈 감김 | 각 0.80 이하 |
| 가림 | 0.50 이하 |
| 입 벌림 | 0.50 이하 |
| pitch/roll | 절댓값 20도 이하 |
| 랜드마크 | 68쌍, 배열 길이 136 |
| 얼굴 폭 | 변환 이미지 폭의 16% 이상 |

이 값은 Runtime Demo 기본값과 기존 FFacio 사용 흐름을 결합한 앱 정책이며 엔진 자체의 공식 보증값으로 간주하지 않습니다. 실제 설치 환경에서 오인식·거부 로그를 보고 조정해야 합니다.

나이와 성별은 신원 판정에 사용하지 않습니다. 등록 화면에서 Runtime 호출이 실제로 반환되는지 확인할 수 있도록 `추정 나이`와 원시 `성별 코드`만 표시합니다.

## 등록

등록은 정면→왼쪽→오른쪽→왼쪽→오른쪽 다섯 샘플을 수집합니다. 각 샘플은 Runtime 템플릿이며 앱이 평균 벡터를 만들지 않습니다.

- 같은 각도에서 거의 동일한 템플릿은 재수집
- 기존 사용자 모든 샘플과 Runtime 비교해 중복 등록 차단
- 다섯 샘플의 상호 유사도를 검사해 섞이거나 불안정한 세트 차단
- 각 후보가 다른 샘플과 갖는 평균 Runtime 유사도가 가장 높은 샘플을 대표 템플릿으로 선택
- 나머지 샘플은 보조 템플릿으로 보존

저장 스키마 v3는 `engine_id=ffacio.runtime.template.v1`, 템플릿 크기, Base64 대표 템플릿, Base64 보조 템플릿을 암호화 저장소 안에 기록합니다.

## 인증

입력 템플릿을 각 사용자의 대표·보조 템플릿과 Runtime에서 개별 비교합니다. 앱은 사용자별 최고 점수와 기준 이상 샘플 수를 계산합니다. 승인은 다음 조건을 모두 만족해야 합니다.

- 최고 사용자 점수 0.80 이상
- 2위 사용자와 차이 0.03 이상
- 저장 샘플 중 최소 2개가 0.75 이상(보유 샘플이 하나면 하나)
- 선택한 Runtime 라이브니스와 좌우 얼굴 돌리기 챌린지 통과
- 같은 후보가 3프레임 연속 안정적으로 유지

비교 호출 실패, 손상·크기 불일치 템플릿, 호환되지 않는 엔진 ID는 모두 fail-closed입니다.

## Runtime 진단

관리자 화면 고급 설정의 진단 카드는 다음을 표시합니다.

- Runtime 패키지(`com.kbyai.faceattribute`) 설치 여부와 버전명·버전 코드
- Binder 연결 단계(연결 안 됨 / 연결 중 / Binder 연결됨·초기화 확인 중 / 준비됨)
- 초기화 결과 코드 — `0`만 성공으로 처리
- 마지막 끊김 사유(수동 해제, 서비스 연결 끊김, Runtime 프로세스 종료, 연결 오류)
- 자동 재연결 시도 횟수와 수동 재연결 버튼
- 최근 분석 프레임의 YUV 변환·검출+속성·템플릿 추출 시간

Runtime AIDL은 검출과 요청 속성을 하나의 `detect` 호출로 반환하므로 속성 분석만의 시간은 따로 표시하지 않습니다. 진단 텍스트에는 얼굴 이미지, 템플릿, 사용자 이름이 포함되지 않습니다.

## 이전 데이터

기존 OpenCV/ONNX/ArcFace Float 임베딩은 Runtime 바이트 템플릿으로 변환할 근거가 없습니다. 로더는 사용자 이름과 Head Admin 표시를 보존할 수 있지만 해당 사용자를 `isCompatible=false`로 만들고 인증 후보에서 제외합니다. 호환되지 않는 Head Admin만 남아 있으면 Android 화면잠금으로 관리자 복구 후 삭제·재등록합니다.

## 서명과 설치

Runtime Manifest는 `io.ffacio.sdk.permission.BIND_RUNTIME`을 signature 권한으로 선언합니다. `runtime-client` Manifest가 권한 요청과 Runtime package query를 FFacio Manifest에 병합합니다.

Release 빌드는 Runtime 프로젝트와 FFacio 프로젝트에 같은 `FFACIO_KEYSTORE_*` 환경 변수를 사용해야 합니다. `scripts/build_android.ps1 -RuntimeApk ...`는 두 APK의 signer SHA-256을 비교해 다르면 실패합니다.

## 검증 범위

단위 테스트는 Runtime 비교를 가짜 comparator로 주입해 인증 문턱, 모호성, 샘플 지지, 등록 자세, 중복·오염 세트, Runtime 템플릿 호환 정책과 Runtime 비교 실패 계수를 검사합니다. 실제 Binder, 라이선스, 카메라 방향, 라이브니스와 네이티브 결과는 `verify_android_device.ps1`로 ARM Android 기기에서 확인해야 합니다.
