"""Rebuild dependencies from verified download artifacts, without generated jars."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DependencyBootstrapTest(unittest.TestCase):
    def test_framework_compiles_without_apk_build_cache(self):
        with tempfile.TemporaryDirectory(prefix="newsoul-bootstrap-") as directory:
            clean = Path(directory)
            (clean / "scripts").mkdir()
            for name in ("prepare-test-deps.py", "prepare-dexkit-deps.py", "prepare-home-deps.py"):
                shutil.copy2(ROOT / "scripts" / name, clean / "scripts" / name)
            # Only immutable, checksum-verified downloads are seeded. Every derived jar
            # must be produced by the same entry point used by CI.
            downloads = ["build/sdk/api.aar", "build/test-libs/json-20240303.jar"]
            downloads += [str(p.relative_to(ROOT)) for p in (ROOT / "build/deps").glob("*.aar")]
            downloads += ["build/deps/" + name for name in (
                "flatbuffers-java-23.5.26.jar", "kotlin-stdlib-1.9.24.jar", "annotations-13.0.jar")]
            for relative in downloads:
                target = clean / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, target)
            subprocess.run(["python3", "scripts/prepare-test-deps.py"], cwd=clean, check=True)
            sdk = Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk"))
            classpath = os.pathsep.join((str(sdk / "platforms/android-35/android.jar"),
                str(clean / "build/sdk/api.jar"), str(clean / "build/deps/*")))
            subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath,
                "-d", str(clean / "classes"),
                str(ROOT / "app/src/main/java/dev/local/newsoul/HomeFramework.java")], check=True)


if __name__ == "__main__":
    unittest.main()
