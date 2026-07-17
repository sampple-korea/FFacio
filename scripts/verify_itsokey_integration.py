#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android = root / "android"
errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

manifest = android / "app/src/main/AndroidManifest.xml"
try:
    ET.parse(manifest)
except Exception as exc:
    errors.append(f"Manifest XML parse failed: {exc}")

settings = (android / "settings.gradle").read_text()
gradle = (android / "app/build.gradle").read_text()
main = (android / "app/src/main/java/com/ffacio/mobile/MainActivity.kt").read_text()
settings_activity = (android / "app/src/main/java/com/ffacio/mobile/ItsokeySettingsActivity.java").read_text()
client = (android / "itsokey-runtime-client/src/main/java/io/ffacio/itsokeyruntime/client/ItsokeyRuntimeClient.java").read_text()

require('include ":app", ":runtime-client", ":itsokey-runtime-client"' in settings, "ITSOKEY module not included")
require('implementation project(":itsokey-runtime-client")' in gradle, "ITSOKEY client dependency missing")
require('io.ffacio.itsokeyruntime.permission.BIND_RUNTIME' in manifest.read_text(), "Runtime signature permission missing")
require('ItsokeySettingsActivity' in manifest.read_text(), "ITSOKEY settings activity missing")
require('unlockItsokeyDoor' in main and 'openDevice' in main, "Face success OPEN integration missing")
require('ITSOKEY_RUNTIME_SENTINEL' in main, "Runtime-only token sentinel missing")
require('HttpURLConnection' not in main, "Direct door HTTP call remains in FFacio MainActivity")
require('api.smartthings.com' not in main, "Legacy SmartThings endpoint remains")
require('accessToken' not in settings_activity, "FFacio settings activity handles a raw access token")
require('RUNTIME_PACKAGE = "io.ffacio.itsokeyruntime"' in client, "Runtime client package mismatch")

aidl_ffacio = android / "itsokey-runtime-client/src/main/aidl/io/ffacio/itsokeyruntime/IITSOKeyRuntime.aidl"
require(aidl_ffacio.exists(), "FFacio AIDL missing")

for pattern in [r'ghp_[A-Za-z0-9]{20,}', r'sk-[A-Za-z0-9_-]{20,}', r'Bearer\s+[A-Za-z0-9._-]{40,}']:
    corpus = main + settings_activity + client
    require(re.search(pattern, corpus) is None, f"Possible embedded secret: {pattern}")

if errors:
    print("FAIL")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("PASS ffacio ITSOKEY integration checks=13")
