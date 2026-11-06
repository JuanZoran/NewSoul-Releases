#!/usr/bin/env python3
"""Check supported host DEX contracts; optionally resolve production bindings in device ART without installation."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parent
sys.path.insert(0, str(ROOT / 'shared/host-analysis'))
from dex_members import dex_data, read_members

RESEARCH = ROOT / 'shared/host-analysis/research'
HOSTS = {
    '4.88.0': RESEARCH / 'soul-4.88-audit',
    '6.37.0': RESEARCH / 'soul-6.37.0.apk',
    '6.38.0': RESEARCH / 'soul-6.38-candidate.apk',
    '6.39.0': RESEARCH / 'soul-6.39-audit/soul.apk',
}


def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='Optional Android device for isolated ART resolution')
    args = parser.parse_args()
    supported = re.findall(r'"([\d.]+)"', (PROJECT / 'app/src/main/java/dev/local/newsoul/HomeCheck.java')
                           .read_text().split('VERSIONS =', 1)[1].split(';', 1)[0])
    assert set(supported) == set(HOSTS), 'Update popup matrix for the supported host versions'
    sdk = Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk'))
    adb = ['adb', '-s', args.serial] if args.serial else None
    with tempfile.TemporaryDirectory(prefix='newsoul-popup-matrix-') as tmp:
        out = Path(tmp)
        remote = None
        try:
            if adb:
                sources = [PROJECT / 'app/src/main/java/dev/local/newsoul' / (name + '.java')
                           for name in ('HostBindings', 'HostDecodedResponse', 'TeenagePopupResponse')]
                sources.append(PROJECT / 'tests/TeenagePopupHostProbe.java')
                android = sdk / 'platforms/android-35/android.jar'
                run(['javac', '-encoding', 'UTF-8', '-source', '17', '-target', '17', '-cp', str(android),
                     '-d', str(out / 'classes')] + list(map(str, sources)))
                run(['jar', 'cf', str(out / 'classes.jar'), '-C', str(out / 'classes'), '.'])
                (out / 'probe').mkdir()
                run(['java', '-cp', str(sdk / 'build-tools/35.0.0/lib/d8.jar'), 'com.android.tools.r8.D8',
                     '--min-api', '26', '--lib', str(android), '--output', str(out / 'probe'), str(out / 'classes.jar')])
                with zipfile.ZipFile(out / 'probe.jar', 'w') as jar:
                    jar.write(out / 'probe/classes.dex', 'classes.dex')
                remote = run(adb + ['shell', 'mktemp', '-d', '/data/local/tmp/newsoul-popup.XXXXXXXX'],
                             capture_output=True, text=True).stdout.strip()
                assert re.fullmatch(r'/data/local/tmp/newsoul-popup\.[A-Za-z0-9]+', remote), remote
                run(adb + ['push', str(out / 'probe.jar'), remote + '/probe.jar'], capture_output=True)
            for version, source in HOSTS.items():
                classes = read_members(source)
                def require(owner, fields=(), methods=()):
                    actual = classes['L' + owner.replace('.', '/') + ';']
                    for member in fields:
                        assert member in actual[0], (version, owner, member)
                    for member in methods:
                        assert member in actual[1], (version, owner, member)
                legacy = version == '4.88.0'
                raw, request, url = ('t', 'r', 'm') if legacy else ('v', 't', 'n')
                require('okhttp3.Call', methods=(('request', f'()Lokhttp3/{request};'), ('execute', f'()Lokhttp3/{raw};')))
                require('okhttp3.' + raw, methods=(('t', f'()Lokhttp3/{request};'),))
                require('okhttp3.' + request, methods=(('k', f'()Lokhttp3/{url};'),))
                require('retrofit2.OkHttpCall', methods=(('parseResponse', f'(Lokhttp3/{raw};)Lretrofit2/Response;'),))
                require('retrofit2.Response', methods=(('body', '()Ljava/lang/Object;'),))
                require('cn.soulapp.android.net.HttpResult', methods=(('getData', '()Ljava/lang/Object;'),))
                require('cn.soulapp.android.client.component.middle.platform.model.api.account.FuncSetting',
                        fields=tuple((name, 'Z') for name in ('isTeenageMode', 'needTeenageExam', 'isPopTeenWindow')))
                require('retrofit2.OkHttpCall$1')
                if source.is_file():
                    badging = run([str(sdk / 'build-tools/36.1.0/aapt'), 'dump', 'badging', str(source)],
                                  capture_output=True, text=True).stdout
                    assert f"name='cn.soulapp.android'" in badging and f"versionName='{version}'" in badging
                    with source.open('rb') as apk:
                        fingerprint = hashlib.file_digest(apk, 'sha256').hexdigest()
                    print(version, 'APK SHA-256', fingerprint, flush=True)
                else:
                    # The original 4.88 APK is archived as extracted DEX on this workstation.
                    digest = hashlib.sha256()
                    for dex in dex_data(source): digest.update(hashlib.sha256(dex).digest())
                    print(version, 'archived DEX set SHA-256', digest.hexdigest(), flush=True)
                print(version, 'PASS static popup contracts', flush=True)
                if adb:
                    with zipfile.ZipFile(out / 'host.jar', 'w') as jar:
                        for i, dex in enumerate(dex_data(source)):
                            jar.writestr('classes' + (str(i + 1) if i else '') + '.dex', dex)
                    run(adb + ['push', str(out / 'host.jar'), remote + '/host.jar'], capture_output=True)
                    run(adb + ['shell', f'CLASSPATH={remote}/probe.jar', 'app_process', '/system/bin',
                               'dev.local.newsoul.TeenagePopupHostProbe', remote + '/host.jar'])
        finally:
            if remote and re.fullmatch(r'/data/local/tmp/newsoul-popup\.[A-Za-z0-9]+', remote):
                run(adb + ['shell', 'rm', '-rf', remote])


if __name__ == '__main__':
    main()
