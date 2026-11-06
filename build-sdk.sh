#!/usr/bin/env bash
# Small reproducible SDK-only build; no Gradle download required.
set -euo pipefail
cd "$(dirname "$0")"
python3 tests/check-ui-appearance.py
python3 scripts/generate-donation-data.py --check
SDK_DIR="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK_DIR/build-tools/35.0.0"
PLATFORM="$SDK_DIR/platforms/android-35/android.jar"
SIGNING_KEYSTORE="${NEWSOUL_SIGNING_KEYSTORE:-$HOME/.local/share/newsoul/signing/new-soul-release.p12}"
SIGNING_PASSWORD_FILE="${NEWSOUL_SIGNING_PASSWORD_FILE:-$HOME/.config/newsoul/signing.pass}"
SIGNING_ALIAS="${NEWSOUL_SIGNING_ALIAS:-newsoul-release}"
VARIANT="${1:-public}"
case "$VARIANT" in
    public) PACKAGE_ID="io.github.juanzoran.newsoul"; MODULE_LABEL="New Soul" ;;
    *) echo "Usage: $0 [public]" >&2; exit 2 ;;
esac
if [[ ! -r "$SIGNING_KEYSTORE" || ! -r "$SIGNING_PASSWORD_FILE" ]]; then
    echo "Release signing material missing; set NEWSOUL_SIGNING_KEYSTORE and NEWSOUL_SIGNING_PASSWORD_FILE" >&2
    exit 2
fi
CACHE="$PWD/build/sdk"
OUT="$CACHE/$VARIANT"
VERSION_NAME="$(python3 - "$VARIANT" <<'PY_VERSION'
import re, sys
from pathlib import Path
config = Path('app/build.gradle.kts').read_text()
pattern = r'versionName = "([^"]+)"'
print(re.search(pattern, config).group(1))
PY_VERSION
)"
[[ "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid versionName" >&2; exit 2; }
APK="$OUT/new-soul-$VARIANT.apk"
if [[ "$VARIANT" == "public" ]]; then
    APK="$OUT/new-soul-$VERSION_NAME.apk"
fi
# Never reuse another edition's classes or removed source files.
rm -rf "$OUT/classes" "$OUT/dex"
mkdir -p "$CACHE"

mkdir -p "$OUT/classes" "$OUT/dex"
if [[ ! -f "$CACHE/api.aar" ]]; then
    curl -fL https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar -o "$CACHE/api.aar"
fi
echo '423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0  build/sdk/api.aar' | sha256sum -c -
unzip -p "$CACHE/api.aar" classes.jar > "$CACHE/api.jar"
python3 scripts/prepare-dexkit-deps.py
python3 scripts/prepare-home-deps.py
DEPS="$PWD/build/deps"
DEPS_CP="$DEPS/dexkit.jar:$DEPS/flatbuffers-java-23.5.26.jar:$DEPS/kotlin-stdlib-1.9.24.jar:$DEPS/annotations-13.0.jar:$DEPS/xposed-service.jar:$DEPS/xposed-interface.jar"
python3 scripts/prepare-protected-build.py "$VARIANT" "$OUT/protection"
javac -encoding UTF-8 -source 17 -target 17 -cp "$PLATFORM:$CACHE/api.jar:$DEPS_CP" \
    -d "$OUT/classes" "$OUT/protection/java/dev/local/newsoul/"*.java
jar cf "$OUT/module.jar" -C "$OUT/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 26 \
    --lib "$PLATFORM" --classpath "$CACHE/api.jar" \
    --pg-conf app/proguard-rules.pro --pg-conf "$DEPS/dexkit-proguard.pro" --pg-map-output "$OUT/mapping.txt" \
    --output "$OUT/dex" "$OUT/module.jar" "$DEPS/dexkit.jar" \
    "$DEPS/flatbuffers-java-23.5.26.jar" "$DEPS/kotlin-stdlib-1.9.24.jar" "$DEPS/annotations-13.0.jar" "$DEPS/xposed-service.jar" "$DEPS/xposed-interface.jar"
python3 - "$OUT" "$VARIANT" "$PACKAGE_ID" "$MODULE_LABEL" "$VERSION_NAME" <<'PY'
import sys
from pathlib import Path
out = Path(sys.argv[1])
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
import re
config = Path('app/build.gradle.kts').read_text()
code = re.search(r'versionCode = (\d+)', config).group(1)
version = sys.argv[5]
manifest = manifest.replace('${moduleLabel}', sys.argv[4]).replace('${applicationId}', sys.argv[3])
manifest = manifest.replace('<manifest xmlns:', f'<manifest package="{sys.argv[3]}" android:versionCode="{code}" android:versionName="{version}" xmlns:')
manifest = manifest.replace('    <application', '    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />\n    <application android:debuggable="false"')
(out / 'AndroidManifest.xml').write_text(manifest)
PY
"$BT/aapt2" compile --dir app/src/main/res -o "$OUT/resources.zip"
"$BT/aapt2" link -I "$PLATFORM" --manifest "$OUT/AndroidManifest.xml" \
    -o "$OUT/unsigned.apk" "$OUT/resources.zip"
python3 - "$OUT" "$VARIANT" <<'PY'
import sys, zipfile
from pathlib import Path
out = Path(sys.argv[1])
roots = [out / 'protection/resources']
with zipfile.ZipFile(out / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
    for dex in sorted((out / 'dex').glob('classes*.dex')):
        apk.write(dex, dex.name)
    for library in sorted(Path('build/deps/native').glob('*/*.so')):
        apk.write(library, 'lib/' + str(library.relative_to('build/deps/native')), compress_type=zipfile.ZIP_STORED)
    for root in roots:
        for path in sorted(root.rglob('*')):
            if path.is_file():
                apk.write(path, str(path.relative_to(root)))
PY
"$BT/zipalign" -P 16 -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-type PKCS12 \
    --ks-key-alias "$SIGNING_ALIAS" --ks-pass "file:$SIGNING_PASSWORD_FILE" \
    --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$APK"
python3 tests/check-protected-build.py "$OUT/protection" "$APK" --mapping "$OUT/mapping.txt"
echo "APK: $APK"
if [[ "$VARIANT" == "public" ]]; then
    python3 - "$OUT" "$VERSION_NAME" <<'PY'
import re, sys, zipfile
from pathlib import Path
out = Path(sys.argv[1])
version = sys.argv[2]
apk = out / f'new-soul-{version}.apk'
bundle = out / f'new-soul-{version}.zip'
temp = bundle.with_suffix('.zip.tmp')
try:
    with zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(apk, apk.name)
    with zipfile.ZipFile(temp) as archive:
        assert archive.namelist() == [apk.name] and archive.testzip() is None
        assert archive.read(apk.name) == apk.read_bytes()
    temp.replace(bundle)
finally:
    temp.unlink(missing_ok=True)
print(f'ZIP: {bundle.resolve()}')
PY
fi
