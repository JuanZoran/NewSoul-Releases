#!/usr/bin/env python3
"""Export unified sources to an empty directory without network access."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
TREES = ('app/src', 'app/donation-source', 'gradle', 'tests', 'scripts/protection')
FILES = ('README.md', 'LICENSE', 'build-sdk.sh', 'build.gradle.kts', 'settings.gradle.kts',
         'gradlew', 'app/build.gradle.kts', 'app/proguard-rules.pro')
SCRIPTS = ('prepare-test-deps.py', 'prepare-dexkit-deps.py', 'prepare-home-deps.py',
           'prepare-protected-build.py', 'generate-donation-data.py', 'internal_release.py',
           'telegram_release.py', 'telegram_check.py', 'telegram_feedback.py',
           'telegram_caption_fix.py', 'lanzou_release.py', 'DebugDecrypt.java', 'debug-decrypt.sh',
           'export-open-source.py')
SECRET = re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----|'
                    rb'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|'
                    rb'AKIA[A-Z0-9]{16}|\d{8,12}:[A-Za-z0-9_-]{35}')


def export(destination):
    destination = destination.resolve()
    if destination == ROOT or ROOT in destination.parents and ROOT / 'build' not in destination.parents:
        raise ValueError('Destination inside project must be below build/')
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Destination must be empty')
    selected = [ROOT / name for name in FILES]
    selected += [ROOT / 'scripts' / name for name in SCRIPTS]
    for name in TREES:
        selected += [p for p in (ROOT / name).rglob('*') if p.is_file()
                     and '__pycache__' not in p.parts and p.suffix not in ('.pyc', '.md')]
    selected.append(ROOT / 'app/src/main/resources/newsoul/cac_runtime_v2.md')
    manifest = {}
    for source in sorted(set(selected)):
        if source.is_symlink():
            raise ValueError('Symlink not allowed: ' + str(source.relative_to(ROOT)))
        relative = source.relative_to(ROOT)
        data = source.read_bytes()
        if SECRET.search(data):
            raise ValueError('Credential pattern in ' + str(relative))
        if source.suffix in ('.jks', '.p12', '.keystore', '.apk', '.dex', '.log'):
            raise ValueError('Private/generated input in ' + str(relative))
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        manifest[str(relative)] = hashlib.sha256(data).hexdigest()
    (destination / '.gitignore').write_text('build/\n**/build/\n.gradle/\nlocal.properties\n*.jks\n*.p12\n*.keystore\n*.pass\n*.log\n__pycache__/\n*.py[cod]\n')
    (destination / '.github/workflows').mkdir(parents=True)
    (destination / '.github/workflows/regression.yml').write_text('''name: Unified source checks
on: [push, pull_request, workflow_dispatch]
permissions:
  contents: read
jobs:
  regression:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
        with:
          packages: 'platform-tools platforms;android-35'
      - run: bash tests/run-regression-tests.sh
''')
    print(json.dumps({'files': len(manifest), 'destination': str(destination)}, ensure_ascii=False))
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    export(args.destination)
