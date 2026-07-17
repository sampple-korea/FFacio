# 검증 기록

검증일: 2026-07-17 KST

## 통과

- AndroidManifest XML 파싱
- 앱/클라이언트/FFacio의 AIDL 바이트 일치
- Runtime Java 전체 정적 컴파일(Android 16 API 스텁, Java 17)
- FFacio ITSOKEY 설정 Activity 정적 컴파일
- 프로토콜 입력 검증 하네스: `PASS checks=10`
- 기준 서버 단일화: `https://v2.api.itsokey.kr`
- HTTPS 강제 및 cleartext 비활성화
- Binder `signature` 권한과 호출 패키지 제한
- FFacio 소스에서 직접 ITSOKEY 토큰 보관·직접 HTTP 문 열기 제거

## 실행하지 못한 항목

- Gradle/AGP 전체 빌드와 APK 생성
- Android 실제 기기의 WebView 카카오 로그인
- 실제 ITSOKEY 계정의 기기 목록 응답
- 실제 HG-1300 OPEN/CLOSE

현재 샌드박스에 Android SDK와 완성된 Gradle 배포본이 없고 외부 배포 서버 다운로드도 차단되어 위 항목은 실행할 수 없었습니다. 따라서 이 ZIP은 **빌드 가능한 소스 프로젝트**이며 실제 기기에서의 완전 작동을 확정한 APK 산출물이 아닙니다.
