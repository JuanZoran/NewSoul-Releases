#!/usr/bin/env python3
"""Release boundary and partial-delivery regression tests; no network access."""
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

spec = importlib.util.spec_from_file_location('telegram_release', Path(__file__).resolve().parents[1] / 'scripts/telegram_release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
check_spec = importlib.util.spec_from_file_location('telegram_check', Path(__file__).resolve().parents[1] / 'scripts/telegram_check.py')
checker = importlib.util.module_from_spec(check_spec)
check_spec.loader.exec_module(checker)


class PermissionTests(unittest.TestCase):
    def responses(self, member, chat_type='supergroup'):
        return [io.StringIO(json.dumps(dict(ok=True, result=result))) for result in (
            dict(id=42, username='release_bot'), dict(id=-100456, title='Test group', type=chat_type), member)]

    def test_pin_permission_preflight(self):
        env = dict(TELEGRAM_BOT_TOKEN='fake', TELEGRAM_INTERNAL_CHAT_ID='-100456', TELEGRAM_CHAT_ID='-100123')
        with patch.dict(os.environ, env), patch.object(checker.urllib.request, 'urlopen', side_effect=self.responses(dict(status='administrator', can_pin_messages=True))), patch('sys.stdout', new=io.StringIO()):
            checker.check('internal', True)
        for member in (dict(status='member'), dict(status='administrator', can_pin_messages=False), dict(status='kicked')):
            with patch.dict(os.environ, env), patch.object(checker.urllib.request, 'urlopen', side_effect=self.responses(member)), self.assertRaises(ValueError):
                checker.check('internal', True)

    def test_pin_permission_in_channel_and_basic_group(self):
        env = dict(TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123')
        for chat_type, member in [('group', dict(status='administrator')), ('channel', dict(status='administrator', can_post_messages=True, can_edit_messages=True))]:
            with patch.dict(os.environ, env), patch.object(checker.urllib.request, 'urlopen', side_effect=self.responses(member, chat_type)), patch('sys.stdout', new=io.StringIO()):
                checker.check('public', True)
        with patch.dict(os.environ, env), patch.object(checker.urllib.request, 'urlopen', side_effect=self.responses(dict(status='administrator', can_post_messages=True, can_edit_messages=False), 'channel')), self.assertRaises(ValueError):
            checker.check('public', True)

    def test_internal_target_missing_or_same_as_public_never_uses_network(self):
        for value in ('', '-100123'):
            with patch.dict(os.environ, dict(TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123', TELEGRAM_INTERNAL_CHAT_ID=value)), patch.object(checker.urllib.request, 'urlopen') as network, self.assertRaises(ValueError):
                checker.check('internal', True)
            network.assert_not_called()


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name) / 'new-soul-1.30.7.apk'
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('classes.dex', b'public dex fixture')
            archive.writestr('newsoul/rules.bin', b'encrypted fixture')
            archive.writestr('newsoul/ai_entry_v2.png', b'image fixture')
        self.metadata = dict(tagName='v1.30.7', body='修复公开版问题', isDraft=False, isPrerelease=False)
        self.badging = "package: name='io.github.juanzoran.newsoul' versionCode='63' versionName='1.30.7'"
        self.certs = f'Signer #1 certificate SHA-256 digest: {release.CERT}\n'

    def validate(self, badging=None, certs=None):
        with patch.object(release, 'command', side_effect=[badging or self.badging, certs or self.certs]):
            return release.validate(self.apk, self.metadata, Path('/fake'))

    def test_public_valid(self):
        notes, info = self.validate()
        self.assertEqual(notes, self.metadata['body'])
        self.assertEqual(info['version_code'], 63)

    def test_reject_legacy_internal_and_similar_package_ids(self):
        for package in ('dev.local.newsoul.public',
                        'dev.local.newsoul',
                        'io.github.juanzoran.newsoul.internal',
                        'io.github.juanzoran.newsoul.public',
                        'io.github.juanzoran.newsoul.fake'):
            with self.subTest(package=package), self.assertRaisesRegex(ValueError, 'non-public'):
                self.validate(self.badging.replace('io.github.juanzoran.newsoul', package))

    def test_reject_filename_from_another_version(self):
        renamed = self.apk.with_name('new-soul-1.30.6.apk')
        self.apk.rename(renamed)
        self.apk = renamed
        with self.assertRaisesRegex(ValueError, 'filename must match'):
            self.validate()

    def test_reject_internal_wrong_version_debug_and_signature(self):
        cases = [
            (self.badging.replace('io.github.juanzoran.newsoul', 'io.github.juanzoran.newsoul.internal'), self.certs),
            (self.badging.replace('1.30.7', '1.30.6'), self.certs),
            (self.badging + '\napplication-debuggable', self.certs),
            (self.badging, self.certs.replace(release.CERT, '0' * 64)),
        ]
        for badging, certs in cases:
            with self.subTest(badging=badging, certs=certs), self.assertRaises(ValueError):
                self.validate(badging, certs)

    def test_reject_draft_prerelease_empty_notes_template(self):
        for update in [dict(isDraft=True), dict(isPrerelease=True), dict(body=' '), dict(body='<!-- TEMPLATE -->')]:
            with self.subTest(update=update), patch.dict(self.metadata, update), self.assertRaises(ValueError):
                self.validate()

    def test_reject_internal_resource(self):
        with zipfile.ZipFile(self.apk, 'a') as archive:
            archive.writestr('newsoul/cac_runtime_v2.md', 'internal')
        with self.assertRaises(ValueError):
            self.validate()

    def test_public_ai_is_allowed(self):
        with zipfile.ZipFile(self.apk, 'a') as archive:
            archive.writestr('classes2.dex', b'api.deepseek.com ai_reply_credential_v1')
        self.validate()

    def test_missing_ai_resources_is_rejected(self):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('classes.dex', b'fixture')
        with self.assertRaisesRegex(ValueError, 'AI resources missing'):
            self.validate()

    def test_unicode_message_limits_preserve_full_notes(self):
        notes = ('修复😀\n' * 3000)
        caption, extra = release.message_plan('v1.30.7', notes)
        self.assertLessEqual(release.utf16len(caption), 1024)
        self.assertEqual(''.join(part.removesuffix(release.GROUP_FOOTER) for part in extra), notes)
        self.assertTrue(caption.endswith(release.GROUP_FOOTER))
        self.assertTrue(all(part.endswith(release.GROUP_FOOTER) for part in extra))
        self.assertTrue(all(release.utf16len(part) <= 4000 for part in extra))
        short, extra = release.message_plan('v1.30.7', '<tag> & _literal_')
        self.assertIn('<tag> & _literal_', short)
        self.assertEqual(extra, [])
        self.assertTrue(short.endswith(release.GROUP_FOOTER))

    def test_group_link_counts_toward_caption_limit(self):
        prefix = 'New Soul v1.30.7｜统一版\n\n'
        budget = 1024 - release.utf16len(prefix + release.GROUP_FOOTER)
        notes = '字' * budget
        caption, extra = release.message_plan('v1.30.7', notes)
        self.assertEqual(release.utf16len(caption), 1024)
        self.assertEqual(extra, [])
        caption, extra = release.message_plan('v1.30.7', notes + '字')
        self.assertLessEqual(release.utf16len(caption), 1024)
        self.assertEqual(''.join(part.removesuffix(release.GROUP_FOOTER) for part in extra), notes + '字')

    def test_group_link_matches_plugin_entry(self):
        source = Path(__file__).resolve().parents[1] / 'app/src/main/java/dev/local/newsoul/SettingsOtherPage.java'
        self.assertIn('"' + release.GROUP_LINK + '"', source.read_text())

    def test_installation_emphasis_uses_utf16_and_preserves_text(self):
        caption = '更新😀\n\n安装说明\n旧包名无法覆盖升级\n保留 Soul 数据\n\n反馈说明\n反馈 <tag> & 内容'
        entities = release.installation_entities(caption)
        self.assertEqual(len(entities), 1)
        entity = entities[0]
        encoded = caption.encode('utf-16-le')
        selected = encoded[entity['offset'] * 2:(entity['offset'] + entity['length']) * 2].decode('utf-16-le')
        self.assertEqual(entity['type'], 'bold')
        self.assertEqual(selected, '安装说明\n旧包名无法覆盖升级\n保留 Soul 数据')
        self.assertEqual(release.installation_entities('普通公告'), [])

    def test_send_checkpoints_and_rerun_guard(self):
        receipts, sent, uploads = [], [], []
        def checkpoint(args, **kwargs):
            uploads.append(args)
            receipts.append(json.loads(Path(args[4]).read_text()))
        def telegram(*args):
            self.assertTrue(receipts) # guard must be persisted before any send
            sent.append(args)
            return len(sent)
        info = dict(tag='v1.30.7', sha256='fixture', version_code=63)
        env = dict(GH_REPO='owner/repo', TELEGRAM_BOT_TOKEN='fake-token', TELEGRAM_CHAT_ID='-100123')
        current = dict(assets=[], isDraft=False, isPrerelease=False)
        with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release.subprocess, 'run', side_effect=checkpoint), patch.object(release, 'telegram', side_effect=telegram):
            release.send(self.apk, self.metadata, info, 'caption', ['long notes'])
        self.assertEqual(receipts[0]['status'], 'started')
        self.assertIn('caption_entities', sent[0][1])
        self.assertIn('entities', sent[1][1])
        self.assertEqual(receipts[-1]['status'], 'complete')
        self.assertEqual(receipts[-1]['message_ids'], [1, 2])
        self.assertEqual(Path(uploads[0][4]).name, release.GUARD)
        self.assertNotIn('--clobber', uploads[0])
        self.assertTrue(all(Path(args[4]).name == release.RECEIPT for args in uploads[1:]))
        self.assertEqual(json.loads(sent[1][1]['reply_parameters']), {'message_id': 1})
        for marker in (release.GUARD, release.RECEIPT):
            current['assets'] = [dict(name=marker)]
            with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release, 'telegram') as network, self.assertRaises(ValueError):
                release.send(self.apk, self.metadata, info, 'caption', [])
            network.assert_not_called()

    def test_partial_failure_leaves_guard_and_no_retry(self):
        receipts = []
        def checkpoint(args, **kwargs):
            receipts.append(json.loads(Path(args[4]).read_text()))
        env = dict(GH_REPO='owner/repo', TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123')
        current = dict(assets=[], isDraft=False, isPrerelease=False)
        with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release.subprocess, 'run', side_effect=checkpoint), patch.object(release, 'telegram', side_effect=[1, RuntimeError('network')]) as network, self.assertRaises(RuntimeError):
            release.send(self.apk, self.metadata, dict(tag='v1.30.7'), 'caption', ['notes'])
        self.assertEqual(network.call_count, 2)
        self.assertEqual(receipts[-1]['status'], 'partial')
        self.assertEqual(receipts[-1]['message_ids'], [1])

    def test_network_error_does_not_expose_token(self):
        with patch.dict(os.environ, TELEGRAM_BOT_TOKEN='SECRET'), patch.object(release.urllib.request, 'urlopen', side_effect=urllib.error.URLError('https://api.telegram.org/botSECRET/sendDocument')):
            with self.assertRaises(RuntimeError) as error:
                release.telegram('sendDocument', {'chat_id': '-100123'}, self.apk)
        self.assertNotIn('SECRET', str(error.exception))

    def test_upload_preserves_versioned_filename(self):
        with patch.dict(os.environ, TELEGRAM_BOT_TOKEN='fake'), patch.object(release.urllib.request, 'urlopen', return_value=io.StringIO('{"ok":true,"result":{"message_id":42}}')) as network:
            self.assertEqual(release.telegram('sendDocument', {'chat_id': '-100123'}, self.apk), 42)
        request = network.call_args.args[0]
        self.assertIn(b'filename="new-soul-1.30.7.apk"', request.data)

    def test_internal_identity_requires_internal_package_and_resources(self):
        internal = self.apk.with_name('new-soul-internal-1.30.7.apk')
        self.apk.rename(internal)
        badging = self.badging.replace('io.github.juanzoran.newsoul', 'io.github.juanzoran.newsoul.internal')
        with patch.object(release, 'command', side_effect=[badging, self.certs]):
            _, info = release.validate(internal, self.metadata, Path('/fake'), 'internal')
        self.assertEqual(info['edition'], 'internal')
        with patch.object(release, 'command', return_value=self.badging), self.assertRaisesRegex(ValueError, 'non-internal'):
            release.validate(internal, self.metadata, Path('/fake'), 'internal')
        with zipfile.ZipFile(internal, 'w') as archive:
            archive.writestr('classes.dex', b'fixture')
        with patch.object(release, 'command', side_effect=[badging, self.certs]), self.assertRaisesRegex(ValueError, 'resources missing'):
            release.validate(internal, self.metadata, Path('/fake'), 'internal')

    def test_internal_notes_do_not_include_public_group_footer(self):
        caption, extra = release.message_plan('v1.30.7', '内部更新说明', 'internal')
        self.assertIn('内部版', caption)
        self.assertNotIn(release.GROUP_LINK, caption)
        caption, extra = release.message_plan('v1.30.7', '字' * 5000, 'internal')
        self.assertEqual(''.join(extra), '字' * 5000)
        self.assertTrue(all(release.GROUP_LINK not in text for text in [caption, *extra]))

    def test_internal_send_has_separate_target_guard_and_pinning(self):
        receipts, sent = [], []
        def checkpoint(args, **kwargs):
            receipts.append((Path(args[4]).name, json.loads(Path(args[4]).read_text())))
        def api(method, fields, apk=None):
            sent.append((method, fields))
            return True if method == 'pinChatMessage' else 42
        env = dict(GH_REPO='JuanZoran/NewSoul', TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123', TELEGRAM_INTERNAL_CHAT_ID='-100456')
        current = dict(assets=[dict(name=release.GUARD)], isDraft=False, isPrerelease=False)
        info = dict(tag='v1.30.7', edition='internal', pin_release=True)
        with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release.subprocess, 'run', side_effect=checkpoint), patch.object(release, 'telegram', side_effect=api):
            release.send(self.apk, self.metadata, info, 'caption', [])
        self.assertEqual(receipts[0][0], 'telegram-internal-delivery-started.json')
        self.assertEqual(receipts[-1][0], 'telegram-internal-delivery.json')
        self.assertEqual(receipts[-1][1]['pinned_message_id'], 42)
        self.assertEqual(receipts[-1][1]['pin_status'], 'complete')
        self.assertEqual([method for method, _ in sent], ['sendDocument', 'pinChatMessage'])
        self.assertTrue(all(fields['chat_id'] == '-100456' for _, fields in sent))
        self.assertEqual(sent[-1][1]['disable_notification'], 'true')
        current['assets'].append(dict(name='telegram-internal-delivery-started.json'))
        with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release, 'telegram') as api, self.assertRaises(ValueError):
            release.send(self.apk, self.metadata, info, 'caption', [])
        api.assert_not_called()

    def test_unified_apk_to_internal_audience_uses_separate_receipts(self):
        _, info = self.validate()
        info.update(audience='internal', pin_release=True)
        caption, extra = release.message_plan(info['tag'], '统一版更新', 'public', 'internal')
        self.assertIn('统一版', caption)
        self.assertNotIn(release.GROUP_LINK, caption)
        receipts, sent = [], []
        def checkpoint(args, **kwargs):
            receipts.append((Path(args[4]).name, json.loads(Path(args[4]).read_text())))
        def api(method, fields, apk=None):
            sent.append((method, fields))
            return True if method == 'pinChatMessage' else 42
        env = dict(GH_REPO=release.PRIVATE_REPO, TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123', TELEGRAM_INTERNAL_CHAT_ID='-100456')
        current = dict(assets=[dict(name=release.GUARD)], isDraft=False, isPrerelease=False)
        with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release.subprocess, 'run', side_effect=checkpoint), patch.object(release, 'telegram', side_effect=api):
            release.send(self.apk, self.metadata, info, caption, extra)
        self.assertEqual(receipts[0][0], 'telegram-internal-delivery-started.json')
        self.assertEqual(receipts[-1][1]['edition'], 'public')
        self.assertEqual(receipts[-1][1]['audience'], 'internal')
        self.assertTrue(all(fields['chat_id'] == '-100456' for _, fields in sent))
        self.assertEqual(receipts[-1][1]['pin_status'], 'complete')

    def test_internal_missing_or_public_target_is_rejected(self):
        env = dict(GH_REPO='JuanZoran/NewSoul', TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123')
        for internal_id in ('', '-100123'):
            with patch.dict(os.environ, dict(env, TELEGRAM_INTERNAL_CHAT_ID=internal_id)), patch.object(release, 'telegram') as api, self.assertRaises(ValueError):
                release.send(self.apk, self.metadata, dict(tag='v1.30.7', edition='internal'), 'caption', [])
            api.assert_not_called()

    def test_internal_delivery_refuses_non_private_repository(self):
        env = dict(GH_REPO='JuanZoran/NewSoul-Releases', TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123', TELEGRAM_INTERNAL_CHAT_ID='-100456')
        with patch.dict(os.environ, env), patch.object(release, 'telegram') as api, self.assertRaisesRegex(ValueError, 'private source'):
            release.send(self.apk, self.metadata, dict(tag='v1.30.7', edition='internal'), 'caption', [])
        api.assert_not_called()

    def test_pin_failure_preserves_delivery_guard_and_partial_receipt(self):
        receipts = []
        env = dict(GH_REPO='owner/repo', TELEGRAM_BOT_TOKEN='fake', TELEGRAM_CHAT_ID='-100123')
        current = dict(assets=[], isDraft=False, isPrerelease=False)
        def checkpoint(args, **kwargs):
            receipts.append(json.loads(Path(args[4]).read_text()))
        with patch.dict(os.environ, env), patch.object(release, 'command', return_value=json.dumps(current)), patch.object(release.subprocess, 'run', side_effect=checkpoint), patch.object(release, 'telegram', side_effect=[42, RuntimeError('pin uncertain')]) as api, self.assertRaises(RuntimeError):
            release.send(self.apk, self.metadata, dict(tag='v1.30.7', pin_release=True), 'caption', [])
        self.assertEqual(api.call_count, 2)
        self.assertEqual(receipts[-1]['message_ids'], [42])
        self.assertEqual(receipts[-1]['pin_status'], 'pending')
        self.assertEqual(receipts[-1]['status'], 'partial')


if __name__ == '__main__':
    unittest.main()
