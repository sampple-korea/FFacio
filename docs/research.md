# FFacio Research Notes

조사일: 2026-06-10

## 목표

유료 클라우드 API 없이 Windows 로컬에서 얼굴 등록, 얼굴 인증, 최종 문 열림 연동까지 확장 가능한 방법을 찾고, 개인 프로젝트에서 최고 완성도에 가까운 1차 앱을 만든다.

## 선택 스택

### 1순위: InsightFace + ONNX Runtime

정확도 우선이면 ArcFace 계열 InsightFace가 가장 적합하다. `buffalo_l` 모델은 얼굴 검출, 랜드마크, 인식 모델을 함께 제공하고 ONNX Runtime으로 로컬 CPU 추론이 가능하다. 현재 앱은 InsightFace가 설치되어 있으면 자동으로 1순위 엔진으로 사용한다.

주의점:

- 코드 라이선스와 pretrained 모델 라이선스가 다르다.
- 제공 pretrained 모델은 비상업 연구 목적 문구가 있으므로 실제 운영/상용화 전 확인이 필요하다.
- GPU 가속은 CUDA/DirectML/WinML 프로파일로 별도 구성하는 편이 좋다.

### 2순위 fallback: OpenCV YuNet + SFace

설치 안정성과 라이선스 깔끔함이 중요하면 OpenCV Zoo의 YuNet 얼굴 검출과 SFace 얼굴 인식 조합이 가장 현실적이다. 앱은 InsightFace 초기화가 실패할 경우 자동으로 OpenCV 엔진을 사용한다.

장점:

- OpenCV만으로 실시간 웹캠 처리 가능
- 모델 파일을 로컬 `models/`에 저장하면 완전 오프라인 가능
- Windows 설치 난이도가 낮음

## 비교 요약

| 후보 | 판단 |
|---|---|
| InsightFace | 정확도 최우선. 개인 연구/테스트에 가장 강함. pretrained 모델 라이선스 확인 필요 |
| ONNX Runtime | 로컬 추론 런타임. CPU 기본, CUDA/DirectML/WinML 확장 가능 |
| OpenCV YuNet + SFace | 무료 로컬 Windows MVP에 가장 안정적. fallback으로 적합 |
| face_recognition/dlib | 단순하지만 Windows 설치와 최신성에서 후순위 |
| DeepFace | 실험용 래퍼로 편하지만 앱 코어로는 무겁고 모델별 라이선스 확인 필요 |
| MediaPipe | 신원 인식 모델은 아니지만 얼굴 랜드마크/라이브니스 보조에 유용 |

## 사진/화면 위조 방지

일반 RGB 웹캠만으로 Face ID 수준의 위조 방지를 보장할 수는 없다. NIST FATE PAD도 얼굴 presentation attack detection을 별도 평가 영역으로 다룬다. 그래서 MVP에서는 다음을 넣었다.

- 얼굴 품질 게이트: 단일 얼굴, 크기, 중앙 정렬, 밝기, 흐림, 검출 신뢰도
- passive anti-spoofing: MiniFASNet-V2 ONNX 실제 얼굴 점수를 등록/인증 임베딩 전 fail-closed 게이트로 사용
- active liveness: 인증 전에 중앙/좌/우 랜덤 응시 챌린지와 짧은 포즈 유지 확인
- 안정화: 단일 프레임 승인 금지, 최근 프레임 다수결
- unknown/ambiguous 거부
- 문 열림 쿨다운

다음 단계로 강화할 것:

- MiniFASNet 임계값/카메라별 캘리브레이션과 실기기 photo/replay 테스트
- MediaPipe Face Landmarker 기반 blink/head pose/표정 challenge
- PIN/카드 2차 인증
- IR/depth 카메라
- 실제 하드웨어 연동 전 사진, 휴대폰 화면, 영상 재생 공격 테스트셋 구축

## 문 열림 아키텍처

현재 앱은 실제 릴레이를 열지 않고 `MockDoorController`에 이벤트를 남긴다. 실제 장치는 아래 중 하나를 별도 컨트롤러로 구현한다.

- USB relay
- Serial/RS-485 relay
- MQTT relay
- HTTP door controller

실제 문 제어 규칙:

- 인증 실패, unknown, ambiguous, liveness fail은 모두 fail-closed
- 하드웨어 timeout/retry/heartbeat 필요
- UI와 장치 제어 서비스를 나누면 더 안전함

## 참고 링크

- InsightFace: https://github.com/deepinsight/insightface
- ONNX Runtime: https://onnxruntime.ai/docs/install/
- OpenCV Zoo: https://github.com/opencv/opencv_zoo
- OpenCV YuNet: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- OpenCV SFace: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
- NIST FATE PAD: https://pages.nist.gov/frvt/html/frvt_pad.html
- MediaPipe Face Landmarker: https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker
- Silent-Face-Anti-Spoofing: https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
