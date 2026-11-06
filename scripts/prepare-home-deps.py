"""Pinned official libxposed service for SDK builds and offline JVM tests."""
import hashlib, subprocess, zipfile
from pathlib import Path
cache = Path(__file__).resolve().parents[1] / 'build/deps'
cache.mkdir(parents=True, exist_ok=True)
for artifact, digest in {
    'service': 'a665e4af4638924047f5d5cb610553e15bc2c0ea2ce64a27afb6dee257ff014c',
    'interface': '4e0b76d6050be1d3dc0d35525180ad7d9b60a47fd8d8fe1d18c2bdc840be9ddb',
}.items():
    path = cache / f'xposed-{artifact}-102.0.0.aar'
    if not path.exists():
        temp = path.with_suffix('.tmp')
        subprocess.run(['curl', '-fL', '--connect-timeout', '15', '--max-time', '120',
            f'https://repo.maven.apache.org/maven2/io/github/libxposed/{artifact}/102.0.0/{artifact}-102.0.0.aar', '-o', str(temp)], check=True)
        if hashlib.sha256(temp.read_bytes()).hexdigest() != digest: raise SystemExit('Invalid libxposed dependency')
        temp.replace(path)
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest: raise SystemExit('Invalid libxposed dependency')
    with zipfile.ZipFile(path) as z:
        (cache / f'xposed-{artifact}.jar').write_bytes(z.read('classes.jar'))
print('libxposed service 102.0.0 dependencies verified')
