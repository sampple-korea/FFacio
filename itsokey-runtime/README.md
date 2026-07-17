# ITSOKEY Runtime 1.0.0

FFacio 같은 별도 앱에서 ITSOKEY 로그인·세션·기기 조회·문 제어를 호출할 수 있도록 만든 개인용 Android Binder 런타임입니다. 원본 ITSOKEY APK를 재패키징하지 않고, ITSOKEY v2.2.6(72) 웹 번들에서 확인한 통신 규격만 최소 재구현했습니다.

## 프로젝트 구성

- `:app` — 독립 실행 관리 앱 + 카카오 웹 로그인 + 암호화 세션 저장 + Binder 서비스
- `:runtime-client` — FFacio 등 호출 앱이 붙이는 AIDL 클라이언트 라이브러리
- `docs/ITSOKEY_PROTOCOL.md` — 확인한 요청 규격과 증거 범위
- `tools/verify_source.py` — 소스 구조·보안 설정·AIDL 일치 검사

## 들어간 기능

- ITSOKEY 공식 로그인 페이지 `https://v2.api.itsokey.kr/signIn`을 통한 카카오 로그인
- 로그인 완료 후 ITSOKEY access/refresh token 추출
- Android Keystore AES-GCM 암호화 저장
- 세션 검증 및 access token 자동 갱신
- 등록 기기 목록·단일 기기 조회
- `OPEN` / `CLOSE` 명령
- 독립 관리 화면에서 로그인, 기기 조회, 명시적 시험 열기
- FFacio에서 사용할 수 있는 서명 보호 AIDL Binder API

## 설치·서명 조건

런타임은 문 열기 권한을 보호하기 위해 `signature` 권한을 사용합니다. **ITSOKEY Runtime APK와 FFacio APK는 반드시 같은 인증서로 서명해야 합니다.**

- 같은 개발 PC에서 두 프로젝트의 debug APK를 빌드하면 보통 동일한 debug keystore를 사용합니다.
- release 빌드는 두 프로젝트에 동일한 keystore·alias를 설정해야 합니다. 두 프로젝트 모두 `ffacioStoreFile`, `ffacioStorePassword`, `ffacioKeyAlias`, `ffacioKeyPassword` Gradle 속성 또는 같은 이름 계열의 `FFACIO_*` 환경변수를 읽도록 맞춰 두었습니다.
- 런타임을 먼저 설치하고 FFacio를 설치합니다.

## 빌드

요구 환경: JDK 17, Android SDK 36, Gradle Wrapper가 의존성을 받을 수 있는 네트워크.

```bash
./gradlew clean test assembleDebug
```

생성물:

```text
app/build/outputs/apk/debug/app-debug.apk
runtime-client/build/outputs/aar/runtime-client-debug.aar
```

## 사용 흐름

1. ITSOKEY Runtime 앱 설치
2. FFacio 앱 설치
3. FFacio 관리자 설정 → `ITSOKEY 로그인·도어락 선택`
4. 공식 ITSOKEY 로그인 화면에서 카카오 로그인
5. 등록된 도어락 선택
6. 연결 확인 후 얼굴 인증 성공 시 선택 기기에 `OPEN` 전송

FFacio에는 ITSOKEY 토큰이 저장되지 않습니다. FFacio는 선택한 기기 ID와 활성화 상태만 저장하고, 실제 토큰은 Runtime 앱의 Keystore 암호화 저장소에만 둡니다.

## 확인 범위와 한계

- 새 Java/AIDL 코드는 Android 16 API 스텁을 기준으로 전체 정적 컴파일했습니다.
- 프로토콜 입력 검증 하네스 10개 검사를 통과했습니다.
- 이 실행 환경에는 Android SDK/Gradle 배포본과 실제 카카오 계정·HG-1300 도어락이 없어 APK 완전 빌드, 카카오 로그인, 실문 OPEN은 실행하지 못했습니다.
- ITSOKEY 공개 개발자 API가 아니라 v2.2.6(72) 클라이언트 규격을 재현한 것이므로 서버 규격 변경 시 수정이 필요할 수 있습니다.
- 본인 계정과 본인이 제어 권한을 가진 도어락에만 사용하세요.
