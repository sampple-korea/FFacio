# -*- mode: python ; coding: utf-8 -*-

from PyInstaller.utils.hooks import collect_dynamic_libs


hiddenimports = [
    "insightface.app.face_analysis",
    "insightface.model_zoo.arcface_onnx",
    "insightface.model_zoo.scrfd",
    "insightface.model_zoo.model_zoo",
    "onnxruntime.capi.onnxruntime_pybind11_state",
]

binaries = []
binaries += collect_dynamic_libs("onnxruntime")
binaries += collect_dynamic_libs("cv2")

a = Analysis(
    ["app.py"],
    pathex=[],
    binaries=binaries,
    datas=[
        ("resources", "resources"),
        ("docs", "docs"),
        ("hardware", "hardware"),
        ("scripts/mock_door_relay.py", "tools"),
    ],
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["PyQt5", "PyQt6", "PySide2", "tkinter", "matplotlib", "insightface.gui"],
    noarchive=False,
)
pyz = PYZ(a.pure, a.zipped_data)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="FFacio",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="FFacio",
)
