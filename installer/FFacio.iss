#define AppName "FFacio"
#define AppVersion "0.3.5"
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
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
Type: files; Name: "{app}\FFacio.exe"
Type: filesandordirs; Name: "{app}\_internal"

[Icons]
Name: "{group}\FFacio"; Filename: "{app}\FFacio.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\FFacio"; Filename: "{app}\FFacio.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\FFacio.exe"; Description: "Run FFacio"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; User biometric templates and logs are intentionally preserved in %LOCALAPPDATA%\FFacio.

[Code]
function InitializeUninstall(): Boolean;
begin
  Result :=
    MsgBox(
      'FFacio preserves registered face templates, settings, and logs in %LOCALAPPDATA%\FFacio by default so reinstalling does not erase local biometric data.' + #13#10#13#10 +
      'To remove all local data, use Settings > Reset local data before uninstalling, or run FFacio.exe --wipe-local-data --yes.' + #13#10#13#10 +
      'Continue uninstalling FFacio?',
      mbInformation,
      MB_YESNO
    ) = IDYES;
end;
