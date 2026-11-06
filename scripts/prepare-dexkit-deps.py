#!/usr/bin/env python3
"""Pinned Maven dependencies for the SDK-only build; Gradle resolves the same POM."""
import hashlib, subprocess, zipfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
cache = root / 'build/deps'
cache.mkdir(parents=True, exist_ok=True)
artifacts = {
 'org/luckypray/dexkit/2.0.7/dexkit-2.0.7.aar': '801b226adb78918aabad297caf258f42faaa2d7a8a59b09b962eab2ee468dda3',
 'com/google/flatbuffers/flatbuffers-java/23.5.26/flatbuffers-java-23.5.26.jar': '8d10cac2ea9878896077ba437d76fdb1b9a07f55a863c560bb8a024b04103f8b',
 'org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar': '858b902696da9cf585ab9d98ffc1c2712269828354dfe9107e3711b084a36468',
 'org/jetbrains/annotations/13.0/annotations-13.0.jar': 'ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478',
}
for remote, digest in artifacts.items():
 path = cache / remote.rsplit('/', 1)[1]
 if not path.exists():
  temp = path.with_suffix(path.suffix + '.tmp')
  subprocess.run(['curl', '--connect-timeout', '15', '--max-time', '120', '-fL', 'https://repo.maven.apache.org/maven2/' + remote, '-o', str(temp)], check=True)
  if hashlib.sha256(temp.read_bytes()).hexdigest() != digest: raise SystemExit('Dependency checksum mismatch: ' + path.name)
  temp.replace(path)
 if hashlib.sha256(path.read_bytes()).hexdigest() != digest: raise SystemExit('Dependency checksum mismatch: ' + path.name)
with zipfile.ZipFile(cache / 'dexkit-2.0.7.aar') as z:
 (cache / 'dexkit.jar').write_bytes(z.read('classes.jar'))
 (cache / 'dexkit-proguard.pro').write_bytes(z.read('proguard.txt'))
# The cxx POM dependency is prefab/static-only; libdexkit.so already contains it.
for artifact in ('dexkit-2.0.7.aar',):
 with zipfile.ZipFile(cache / artifact) as z:
  for name in z.namelist():
   if name.startswith('jni/') and name.endswith('.so'):
    dest = cache / 'native' / name.removeprefix('jni/')
    dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(z.read(name))
print('DexKit 2.0.7 dependencies verified')
