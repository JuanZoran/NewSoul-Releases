#!/usr/bin/env python3
"""Verify pinned JVM test dependencies; never build or sign an APK."""
import hashlib
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
artifacts = (
    ('io/github/libxposed/api/102.0.0/api-102.0.0.aar', root / 'build/sdk/api.aar',
     '423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0'),
    ('org/json/json/20240303/json-20240303.jar', root / 'build/test-libs/json-20240303.jar',
     '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'),
)
for remote, path, expected in artifacts:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        pending = path.with_suffix(path.suffix + '.test-download')
        subprocess.run(['curl', '--connect-timeout', '15', '--max-time', '120', '-fL',
                        'https://repo.maven.apache.org/maven2/' + remote, '-o', str(pending)], check=True)
        if hashlib.sha256(pending.read_bytes()).hexdigest() != expected:
            raise SystemExit('Test dependency checksum mismatch: ' + path.name)
        pending.replace(path)
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise SystemExit('Test dependency checksum mismatch: ' + path.name)
with zipfile.ZipFile(root / 'build/sdk/api.aar') as archive:
    (root / 'build/sdk/api.jar').write_bytes(archive.read('classes.jar'))
subprocess.run(['python3', str(root / 'scripts/prepare-dexkit-deps.py')], check=True)
subprocess.run(['python3', str(root / 'scripts/prepare-home-deps.py')], check=True)
print('Pinned libxposed API/service and JSON test runtime verified')
