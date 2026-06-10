# FFacio

로컬 Windows 데스크톱에서 얼굴 등록과 얼굴 인증을 테스트하는 개인 프로젝트용 앱입니다. 유료 클라우드 API 없이 웹캠, OpenCV YuNet/SFace, 선택적 InsightFace 백엔드를 사용하도록 설계했습니다.

## 결론

- 정확도 최우선 개인/비상업 연구: InsightFace + ONNX Runtime이 가장 강합니다. 다만 제공 pretrained 모델은 비상업 연구 목적 문구가 있어 실제 제품 운영에는 확인이 필요합니다.
- 무료 로컬 Windows MVP: OpenCV YuNet + SFace가 가장 안정적입니다. OpenCV Zoo는 YuNet 얼굴 검출과 SFace 얼굴 인식 예제를 제공하고, OpenCV Zoo 자체는 Apache-2.0이며 각 모델별 라이선스를 확인해야 합니다.
- 실제 문 제어: 기본은 안전한 `MockDoorController` 로그 모드입니다. 설정에서 로컬 HTTP 릴레이 URL을 지정하고 별도의 실제 열림 활성화 체크를 켠 경우에만 얼굴+라이브니스 인증 성공 후 `HttpDoorController`가 open URL을 호출합니다. 선택 Bearer 토큰은 로컬 저장 시 DPAPI로 보호되며, URL 누락, 타임아웃, 비정상 응답은 모두 fail-closed로 기록됩니다.

참고한 공식 자료:

- InsightFace: https://github.com/deepinsight/insightface
- InsightFace Python package: https://github.com/deepinsight/insightface/tree/master/python-package
- ONNX Runtime install: https://onnxruntime.ai/docs/install/
- OpenCV Zoo: https://github.com/opencv/opencv_zoo
- OpenCV YuNet: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- OpenCV SFace: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface

## 실행

PowerShell:

```powershell
.\setup_best.ps1
.\run.bat
```

## 설치 파일 만들기

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_release.ps1
```

결과물은 `release\FFacio-Setup.exe` 하나입니다. 이 설치 파일은 앱 런타임과 OpenCV/InsightFace 모델을 포함하므로 대상 PC에서 Python, pip, Git, 모델 다운로드가 필요하지 않습니다.
설치본에는 `docs/door-relay.md`, ESP32 HTTP 릴레이 예제, 로컬 mock relay 참고 유틸리티도 포함됩니다. FFacio 앱 실행에는 Python이 필요 없지만, 참고용 mock relay `.py` 파일을 직접 실행하려면 개발 PC 또는 릴레이 PC에 Python이 필요합니다.

비관리자 릴리스 무결성 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_release_static.ps1
```

실제 설치/제거까지 포함한 검증은 관리자 PowerShell에서 실행합니다:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_installer.ps1
```

Windows 배치:

```bat
run.bat
```

개발 실행에서는 `scripts/prepare_models.ps1`로 `resources/models/`를 준비합니다. 설치본은 모델을 내장하므로 첫 실행 다운로드를 하지 않습니다.

## Android APK

Android 버전은 Kotlin + Jetpack Compose + CameraX + OpenCV Android AAR로 구성되어 있습니다. Windows판과 같은 `resources/models/` 번들을 APK assets에 포함하며, 현재 실제 추론은 모바일 호환성이 좋은 OpenCV YuNet/SFace 경로를 사용합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup_android_deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_static.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_emulator.ps1
```

결과물은 `release\FFacio-Android-release.apk`와 `release\FFacio-Android-debug.apk`입니다. release APK는 로컬 sideload 테스트용 키로 서명되며, Play/production 배포에는 사용자 소유 keystore를 지정해야 합니다.

## 주요 기능

- 실시간 얼굴 인증
- 얼굴 등록 샘플 다중 수집
- 얼굴 품질 게이트: 단일 얼굴, 검출 신뢰도, 크기, 중앙 정렬, 밝기, 흐림
- 사진/정지 화면 방어용 active liveness 챌린지: 랜덤 방향 응시와 짧은 포즈 유지 요구
- 중복 등록 방지
- Unknown/Ambiguous 거부
- 최근 프레임 다수결 기반 승인
- 인증/등록/삭제/문 열림 요청 로그
- 테스트용 Mock 문 제어와 로컬 HTTP 릴레이 문 제어
- `docs/door-relay.md`의 HTTP 릴레이 프로토콜, ESP32 예제 스케치, 개발용 mock relay
- Apple 느낌의 밝고 단순한 PySide6 UI

## 저장 위치

- 번들 모델: 설치 폴더의 `resources/models/`
- 사용자 템플릿/설정/로그: `%LOCALAPPDATA%\FFacio\ffacio_store.json`

원본 얼굴 이미지는 기본 저장하지 않습니다.
제거 프로그램은 재설치 편의를 위해 `%LOCALAPPDATA%\FFacio`를 기본 보존합니다. 앱의 설정 화면에서 `로컬 데이터 초기화`를 실행하면 등록 얼굴 템플릿, 설정, 로그를 삭제할 수 있습니다.

## 한계와 안전 메모

일반 RGB 웹캠만 쓰는 얼굴인식은 사진, 휴대폰 화면, 영상 재생 공격에 취약합니다. 실제 문 개방 전에는 최소한 PIN/카드/관리자 승인 또는 라이브니스 모델을 추가하고, 실패 시 fail-closed 정책을 유지해야 합니다.

현재 앱은 정지 사진 공격을 줄이기 위해 중앙/좌/우 랜덤 응시 챌린지와 짧은 포즈 유지 확인을 먼저 통과해야 인증하도록 했습니다. 휴대폰 영상 재생 같은 고급 공격까지 막으려면 MiniFASNet 같은 passive anti-spoofing 모델, IR/depth 카메라, 또는 PIN/카드 2차 인증을 붙이는 것이 다음 단계입니다.

HTTP 릴레이를 실제 문에 연결할 때는 공유 Wi-Fi에 평문 Bearer 토큰을 노출하지 않는 구성이 필요합니다. 가능하면 localhost 브리지, 유선/격리 네트워크, HTTPS 지원 컨트롤러, 또는 nonce/HMAC 검증이 있는 릴레이를 사용하세요.

현재 installer는 코드서명 인증서가 없어 unsigned 상태입니다. Windows SmartScreen 경고가 표시될 수 있습니다.
