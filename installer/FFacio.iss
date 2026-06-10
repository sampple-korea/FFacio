#define AppName "FFacio"
#define AppVersion "0.2.0"
#define Publisher "FFacio"
#define SourceDir "..\dist\FFacio"

[Setup]
AppId={{A6D19519-97B7-4833-877D-AC3D17D8D039}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Publisher}
DefaultDirName={autopf}\FFacio
DefaultGroupName=FFacio
DisableProgramGroupPage=yes
OutputDir=..\release
OutputBaseFilename=FFacio-Setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
UninstallDisplayName=FFacio
UninstallDisplayIcon={app}\FFacio.exe
SetupLogging=yes

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"

[Tasks]
Name: "desktopicon"; Description: "바탕 화면 바로 가기 만들기"; GroupDescription: "추가 바로 가기:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
Type: files; Name: "{app}\FFacio.exe"
Type: filesandordirs; Name: "{app}\_internal"

[Icons]
Name: "{group}\FFacio"; Filename: "{app}\FFacio.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\FFacio"; Filename: "{app}\FFacio.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\FFacio.exe"; Description: "FFacio 실행"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; User biometric templates and logs are intentionally preserved in %LOCALAPPDATA%\FFacio.

[Code]
function InitializeUninstall(): Boolean;
begin
  Result :=
    MsgBox(
      'FFacio는 재설치를 위해 %LOCALAPPDATA%\FFacio의 등록 얼굴 템플릿, 설정, 로그를 기본 보존합니다.' + #13#10#13#10 +
      '완전히 삭제하려면 제거 전 앱 설정의 "로컬 데이터 초기화" 또는 FFacio.exe --wipe-local-data --yes를 실행하세요.' + #13#10#13#10 +
      '제거를 계속할까요?',
      mbInformation,
      MB_YESNO
    ) = IDYES;
end;
