#!/usr/bin/env python3
"""Check shipped APK boundaries, identity and signing, rather than build flags alone."""
from pathlib import Path
import os
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
bt = Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk')) / 'build-tools/35.0.0'
release_cert = '0169d3c49680f45aa33186b24b77496f622a0477e4a3b4a283a5160fa072391e'
version = re.search(r'versionName = "([^"]+)"', (root / 'app/build.gradle.kts').read_text()).group(1)
version_code = re.search(r'versionCode = (\d+)', (root / 'app/build.gradle.kts').read_text()).group(1)
for edition, package, label in [
    ('public', 'io.github.juanzoran.newsoul', 'New Soul'),
]:
    filename = f'new-soul-{version}.apk' if edition == 'public' else 'new-soul-internal.apk'
    apk = root / f'build/sdk/{edition}/{filename}'
    mapping = (root / f'build/sdk/{edition}/mapping.txt').read_text()
    renamed = dict(re.findall(r'^([\w.$]+) -> ([\w.$]+):$', mapping, re.M))
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate APK entries'
        dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n))
        preferences = ('dev.local.soulhookdemo.public.settings.v1' if edition == 'public'
                       else 'dev.local.soulhookdemo.settings.v1')
        assert preferences.encode() in dex, 'Legacy host configuration filename changed'
        assert b'dev.local.soulhookdemo.recalled.v1' in dex, 'Recall storage filename changed'
        assert b'newsoul_ai_materials_v1' in dex, 'Internal material storage filename changed'
        for abi in ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'):
            entry = archive.getinfo(f'lib/{abi}/libdexkit.so')
            assert entry.compress_type == zipfile.ZIP_STORED, (edition, abi)
        assert b'Lorg/luckypray/dexkit/DexKitBridge;' in dex
        assert 'newsoul/third-party/NOTICE.txt' in names
        for source in ['SoulHook', 'SettingsUi']:
            original = f'dev.local.newsoul.{source}'
            assert original in renamed, (edition, source)
            assert f'L{renamed[original].replace(".", "/")};'.encode() in dex, (edition, source)
        for component in ('HomeActivity', 'HomeStatusProvider', 'HomeApplication', 'HomeUpgradeReceiver'):
            original = f'dev.local.newsoul.{component}'
            assert renamed[original] == original, ('Manifest component renamed', edition, component)
            assert f'L{original.replace(".", "/")};'.encode() in dex
        assert renamed['dev.local.newsoul.SoulHook'] == 'dev.local.newsoul.SoulHook'
        assert renamed['dev.local.newsoul.SettingsUi'] != 'dev.local.newsoul.SettingsUi'
        for source in ['AiReplyEntry', 'AiReplyUi', 'AiReplyConfig']:
            assert f'dev.local.newsoul.{source}' in renamed, source
        assert 'newsoul/cac_runtime_v2.md' not in names, 'Plaintext internal rules shipped'
        for name in ['newsoul/rules.bin', 'newsoul/ai_entry_v2.png']:
            assert (name in names) == True, (edition, name)
        assert archive.read('META-INF/xposed/java_init.list').strip() == b'dev.local.newsoul.SoulHook'
    subprocess.run([str(bt / 'zipalign'), '-c', '-P', '16', '4', str(apk)], check=True, stdout=subprocess.DEVNULL)
    badging = subprocess.check_output([str(bt / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
    assert f"package: name='{package}'" in badging
    assert f"versionCode='{version_code}' versionName='{version}'" in badging
    assert f"application-label:'{label}'" in badging
    assert "uses-permission: name='android.permission.INTERNET'" in badging
    manifest = subprocess.check_output([str(bt / 'aapt2'), 'dump', 'xmltree', str(apk),
                                       '--file', 'AndroidManifest.xml'], text=True)
    assert f'{package}.runtime' in manifest
    assert f'{package}.XposedService' in manifest
    assert 'io.github.libxposed.service.XposedProvider' in manifest
    assert 'de.robv.android.xposed.category.MODULE_SETTINGS' in manifest
    assert 'dev.local.newsoul.HomeActivity' in manifest
    alias = manifest.split('E: activity-alias', 1)[1].split('E: provider', 1)[0]
    assert 'dev.local.newsoul.Launcher' in alias
    assert re.search(r':enabled\([^)]*\)=false', alias), 'Alias must default disabled to suppress synthetic details icon'
    assert 'android.intent.action.MY_PACKAGE_REPLACED' in manifest
    assert 'dev.local.newsoul.HomeApplication' in manifest
    assert 'targetActivity' in alias and 'dev.local.newsoul.HomeActivity' in alias
    assert 'android.intent.category.LAUNCHER' in alias
    assert 'dev.local.newsoul.HomeStatusProvider' in manifest
    assert 'cn.soulapp.android' in manifest
    assert 'application-debuggable' not in badging
    certs = subprocess.check_output([str(bt / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    assert f'Signer #1 certificate SHA-256 digest: {release_cert}' in certs
    print(f'{edition}: identity, R8 mapping, unified AI and release signature passed')
