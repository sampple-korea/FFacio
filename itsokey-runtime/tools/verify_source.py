#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

for manifest in [
    root / "app/src/main/AndroidManifest.xml",
    root / "runtime-client/src/main/AndroidManifest.xml",
]:
    try:
        ET.parse(manifest)
    except Exception as exc:
        errors.append(f"XML parse failed: {manifest}: {exc}")

aidl_app = root / "app/src/main/aidl/io/ffacio/itsokeyruntime/IITSOKeyRuntime.aidl"
aidl_client = root / "runtime-client/src/main/aidl/io/ffacio/itsokeyruntime/IITSOKeyRuntime.aidl"
require(aidl_app.read_bytes() == aidl_client.read_bytes(), "AIDL files differ")

manifest_text = (root / "app/src/main/AndroidManifest.xml").read_text()
require('android:protectionLevel="signature"' in manifest_text, "Binder permission is not signature protected")
require('android:usesCleartextTraffic="false"' in manifest_text, "Cleartext traffic is not disabled")

java = "\n".join(p.read_text(errors="ignore") for p in root.rglob("*.java"))
require('https://v2.api.itsokey.kr' in java, "ITSOKEY base URL missing")
for forbidden in ['http://v2.api.itsokey.kr', 'api.itsokey.com', 'SMARTTHINGS_ACCESS_TOKEN']:
    require(forbidden not in java, f"Forbidden legacy string found: {forbidden}")
for endpoint in ['/api/oauth/me.do', '/api/widget/oauth/generated.do', '/api/widget/devices.do', '/api/widget/oauth/refresh.do', '/api/widget/device/', '/control.do']:
    require(endpoint in java, f"Required endpoint missing: {endpoint}")
for unsupported in ['/api/device/me.do', '/api/device/control.do', '/api/oauth/refresh.do']:
    require(unsupported not in java, f"Unsupported legacy endpoint remains: {unsupported}")
require('KeyProperties.KEY_ALGORITHM_AES' in java and 'AES/GCM/NoPadding' in java, "Keystore AES-GCM session storage missing")
require('ALLOWED_PACKAGES' in java and 'Binder.getCallingUid()' in java, "Caller validation missing")
require('browser_fallback_url' in java and 'sessionStorage.getItem' in java, "Web login compatibility handling missing")
require('generateWidgetSession(memberSession)' in java, "Web session is not exchanged for widget credentials")
require('loadMemberInformation(session)' in java, "Official post-login member lookup is missing")

secret_patterns = [
    r'ghp_[A-Za-z0-9]{20,}',
    r'sk-[A-Za-z0-9_-]{20,}',
    r'AIza[0-9A-Za-z_-]{20,}',
    r'Bearer\s+[A-Za-z0-9._-]{40,}',
]
for pattern in secret_patterns:
    if re.search(pattern, java):
        errors.append(f"Possible embedded secret matched: {pattern}")

if errors:
    print("FAIL")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("PASS runtime source checks=24")
