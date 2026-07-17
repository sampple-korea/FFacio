# ITSOKEY Runtime protocol notes

이 모듈은 사용자가 제공한 ITSOKEY v2.2.6(72) 디컴파일 자료에서 확인한 **일반 앱 세션 API**만 재구현합니다. 공개 API가 아니므로 서버 업데이트 시 경로·응답 형식이 바뀔 수 있습니다.

## 로그인

Runtime은 공식 웹 로그인 화면을 WebView에서 엽니다.

```text
https://v2.api.itsokey.kr/signIn
```

웹 화면의 카카오 OAuth 흐름이 성공하면 같은 origin의 `localStorage`에 다음 값이 저장됩니다.

```text
accessToken
accessTokenExpired
refreshToken
refreshTokenExpired
member
```

Runtime은 값을 읽은 뒤 Android Keystore AES-GCM으로 암호화 저장하고, FFacio에는 토큰을 전달하지 않습니다.

## 기기 목록

```http
POST /api/widget/oauth/generated.do
Authorization: Bearer {memberAccessToken}

발급된 위젯 토큰으로:

GET /api/widget/devices.do
Authorization: Bearer {accessToken}
```

## 단일 기기

```http
GET /api/device/{deviceIdx}.do
Authorization: Bearer {accessToken}
```

## 문 제어

```http
POST /api/widget/device/{deviceIdx}/control.do
Authorization: Bearer {accessToken}
Content-Type: application/json

{"deviceIdx":"...","type":"OPEN"}
```

`CLOSE`도 원본 앱 서비스에 존재하지만 FFacio 자동 인증 흐름에서는 `OPEN`만 사용합니다.

## 토큰 갱신

```http
POST /api/widget/oauth/refresh.do
Authorization: Bearer {refreshToken}
```

응답의 새 access/refresh token과 만료 시간을 한 번에 저장합니다.

## 근거

- `assets/public/assets/device.service.c7801c25.js`
  - `generatedToken()` → `/api/widget/oauth/generated.do`
  - `fetchDevices()` → `/api/widget/devices.do`
  - `fetchDeviceInformation()` → `/api/device/{id}.do`
  - `deviceControl()` → `/api/widget/device/{deviceIdx}/control.do`
- `assets/public/assets/index.219ba976.js`
  - `refreshToken()` → `/api/widget/oauth/refresh.do`
  - access token을 `Authorization: Bearer`로 전송
