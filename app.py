import os
import sys
import time

from ffacio.engine import InsightFaceEngine, OpenCVSFaceEngine, create_engine
from ffacio.models import verify_models
from ffacio.store import purge_local_data


def smoke_test() -> int:
    verify_models()
    if "--strict-insightface" in sys.argv:
        InsightFaceEngine()
    elif "--strict-opencv" in sys.argv:
        OpenCVSFaceEngine()
    else:
        create_engine(prefer_insightface=True)

    if "--ui-smoke" in sys.argv:
        if "--offscreen" in sys.argv:
            os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
        from PySide6.QtWidgets import QApplication

        from ffacio.ui import FFacioWindow

        runtime_smoke = "--runtime-smoke" in sys.argv
        if runtime_smoke:
            os.environ.setdefault("FFACIO_SKIP_CAMERA", "1")
            os.environ.setdefault("FFACIO_FORCE_OPENCV", "1")
        app = QApplication.instance() or QApplication(sys.argv)
        window = FFacioWindow(auto_start=runtime_smoke)
        if runtime_smoke:
            window.store.settings.camera_index = int(os.environ.get("FFACIO_SMOKE_CAMERA_INDEX", "9999"))
            deadline = time.monotonic() + 20
            ok = False
            while time.monotonic() < deadline:
                app.processEvents()
                if window.runtime_ready and window.engine and window.camera_error == "Camera smoke skip":
                    ok = True
                    break
                time.sleep(0.05)
            window.close()
            app.processEvents()
            if not ok:
                return 1
        else:
            window.close()
            app.processEvents()
    return 0


if __name__ == "__main__":
    if "--wipe-local-data" in sys.argv:
        if "--yes" not in sys.argv:
            raise SystemExit("Refusing to wipe local data without --yes.")
        purge_local_data()
        raise SystemExit(0)
    if "--smoke-test" in sys.argv:
        os._exit(smoke_test())
    from ffacio.ui import main

    main()
