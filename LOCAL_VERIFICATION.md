# Local verification

Completed in the provided execution environment:

- FFacio source audit: 37 Kotlin/Java/Gradle files checked
- ITSOKEY integration static verification: 13 checks passed
- FFacio parser harness: 6 checks passed
- Door guard tests: 10 checks passed
- Java integration source compilation against Android 16 API stubs
- XML parse validation
- No signing key, password, GitHub token, or ITSOKEY account token is included

Not completed here:

- Full Gradle dependency resolution and APK build, because this execution environment cannot resolve `services.gradle.org`
- Real Kakao account login
- Real HG-1300 door unlock test

The modified FFacio source expects the separately installed ITSOKEY Runtime app, signed with the same certificate.
