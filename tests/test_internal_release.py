"""Private-draft preparation and real shared delivery lifecycle, with in-memory APIs."""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import urllib.parse
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
import internal_release as internal
import telegram_check
import telegram_release as delivery


class Backend:
    def __init__(self):
        self.private = True
        self.current = None
        self.calls = []
        self.next_id = 10
        self.fail_receipt = False

    def api(self, path, method='GET', body=None):
        self.calls.append((method, path, copy.deepcopy(body)))
        if path == f'repos/{internal.REPO}':
            return dict(private=self.private, full_name=internal.REPO)
        if '/releases?' in path:
            return [copy.deepcopy(self.current)] if self.current else []
        if method == 'POST':
            self.current = dict(body, id=7, assets=[], html_url='https://github.com/private/draft')
        elif method == 'PATCH':
            self.current.update(body)
            if 'tag_name' not in body:
                self.current['tag_name'] = 'untagged-fixture'
        elif method == 'DELETE':
            asset_id = int(path.rsplit('/', 1)[1])
            self.current['assets'] = [a for a in self.current['assets'] if a['id'] != asset_id]
            return None
        return copy.deepcopy(self.current)

    def upload(self, request, **kwargs):
        name = urllib.parse.parse_qs(urllib.parse.urlparse(request.full_url).query)['name'][0]
        if self.fail_receipt and name == 'telegram-internal-delivery.json':
            raise urllib.error.URLError('checkpoint upload failed')
        if any(a['name'] == name for a in self.current['assets']):
            raise urllib.error.HTTPError(request.full_url, 422, 'duplicate', {}, None)
        self.next_id += 1
        asset = dict(id=self.next_id, name=name, size=len(request.data), state='uploaded',
                     digest='sha256:' + hashlib.sha256(request.data).hexdigest())
        self.current['assets'].append(asset)
        return io.StringIO(json.dumps(asset))


class InternalReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / 'new-soul-internal.apk'
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('classes.dex', b'fixture')
            archive.writestr('newsoul/rules.bin', b'encrypted')
            archive.writestr('newsoul/ai_entry_v2.png', b'image')
        self.notes = self.root / 'announcement.md'
        self.notes.write_text('本次更新\n• 修复：聊天读取😀\n\n适配环境\nSoul 6.38.0\n')
        self.directory = self.root / 'delivery'
        self.build_tools = Path('/fake-tools')
        self.source = 'a' * 40
        self.backend = Backend()
        self.messages = []
        self.pinned = None
        self.fail_pin = False
        self.bad_readback = False
        self.bad_title = False
        self.fail_send = False
        self.badging = "package: name='io.github.juanzoran.newsoul.internal' versionCode='330' versionName='1.48.3'"
        self.certs = f'Signer #1 certificate SHA-256 digest: {delivery.CERT}\n'
        def tools(*args):
            if 'badging' in args:
                return self.badging
            if '--print-certs' in args:
                return self.certs
            raise AssertionError('Unexpected external command: ' + repr(args))
        self.addCleanup(patch.stopall)
        patch.object(delivery, 'command', side_effect=tools).start()
        patch.dict(os.environ, {'GH_REPO': internal.REPO, 'GH_TOKEN': 'SECRET', 'TELEGRAM_BOT_TOKEN': 'fake',
                               'TELEGRAM_INTERNAL_CHAT_ID': '-100456', 'TELEGRAM_CHAT_ID': '-100123',
                               'TELEGRAM_INTERNAL_MESSAGE_THREAD_ID': ''}).start()
        patch.object(internal.PrivateDraftArchive, 'api', side_effect=self.backend.api).start()
        patch.object(internal.urllib.request, 'urlopen', side_effect=self.backend.upload).start()
        self.permission = patch.object(telegram_check, 'check').start()
        self.telegram = patch.object(delivery, 'telegram', side_effect=self.api).start()
        patch('sys.stdout', new=io.StringIO()).start()

    def prepare(self, **changes):
        options = dict(apk=self.apk, notes=self.notes, version='1.48.3', source=self.source,
                       chat_id='-100456', chat_title='Internal test group', thread_id='',
                       directory=self.directory, build_tools=self.build_tools)
        options.update(changes)
        return internal.prepare(**options)

    def publish(self, plan=None):
        plan = plan or self.prepare()
        return internal.publish(self.directory, plan['approval_sha256'], self.build_tools)

    def api(self, method, fields, apk=None):
        if method == 'getChat':
            result = dict(id=-100456, title='Wrong group' if self.bad_title else 'Internal test group')
            if self.pinned:
                result['pinned_message'] = dict(message_id=81, caption='wrong' if self.bad_readback else self.messages[0][1]['caption'],
                                               document=dict(file_name=self.messages[0][2].name,
                                                             file_size=self.messages[0][2].stat().st_size))
            else:
                result['pinned_message'] = dict(message_id=62)
            return result
        if method == 'pinChatMessage':
            self.assertEqual(fields['disable_notification'], 'true')
            if self.fail_pin:
                raise RuntimeError('pin result uncertain')
            self.pinned = 81
            return True
        self.assertIn('telegram-internal-delivery-started.json', [a['name'] for a in self.backend.current['assets']])
        self.assertTrue((self.directory / 'telegram-internal-delivery-started.json').exists())
        self.messages.append((method, fields, apk))
        if self.fail_send:
            raise RuntimeError('send result uncertain')
        return 80 + len(self.messages)

    def test_prepare_is_local_and_preserves_full_announcement(self):
        plan = self.prepare()
        self.assertEqual(self.backend.calls, [])
        self.telegram.assert_not_called()
        self.assertIn(self.notes.read_text().strip(), plan['caption'])
        self.assertTrue((self.directory / 'new-soul-internal-1.48.3.apk').is_file())
        self.assertEqual(internal.verify_plan(self.directory, self.build_tools)[0], plan)

    def test_edited_notes_invalidate_prior_approval(self):
        old = self.prepare()
        self.notes.write_text('用户修改后的完整公告')
        new = self.prepare()
        self.assertNotEqual(old['approval_sha256'], new['approval_sha256'])
        with self.assertRaisesRegex(ValueError, 'Approval digest differs'):
            internal.publish(self.directory, old['approval_sha256'], self.build_tools)
        self.assertEqual(self.backend.calls, [])
        self.telegram.assert_not_called()

    def test_plan_binds_recipient_topic_apk_and_all_split_messages(self):
        self.notes.write_text('长公告😀' * 2000)
        plan = self.prepare()
        self.assertTrue(plan['extra_messages'])
        self.assertEqual(''.join(plan['extra_messages']), self.notes.read_text())
        for key, value in [('chat_id', '-100789'), ('thread_id', '42'), ('sha256', 'b' * 64),
                           ('source_commit', 'b' * 40), ('extra_messages', ['changed'])]:
            changed = dict(plan, **{key: value})
            self.assertNotEqual(internal.approval_digest(changed), plan['approval_sha256'])

    def test_modified_apk_or_notes_are_rejected(self):
        self.prepare()
        path = self.directory / 'new-soul-internal-1.48.3.md'
        path.write_text('changed')
        with self.assertRaisesRegex(ValueError, 'announcement changed'):
            internal.verify_plan(self.directory, self.build_tools)
        self.telegram.assert_not_called()

    def test_wrong_apk_package_is_rejected_before_copy_or_archive(self):
        self.badging = self.badging.replace('.internal', '')
        with self.assertRaisesRegex(ValueError, 'non-internal'):
            self.prepare()
        self.assertFalse(self.directory.exists())
        self.assertEqual(self.backend.calls, [])

    def test_create_archive_keeps_explicit_tag_and_uploads_verified_assets(self):
        plan = self.prepare()
        archive = internal.PrivateDraftArchive(plan, self.directory)
        archive.ensure()
        current = archive.inspect()
        self.assertTrue(current['draft'])
        self.assertEqual(plan['archive_id'], 7)
        self.assertEqual({a['name'] for a in current['assets']}, {'new-soul-internal-1.48.3.apk', 'new-soul-internal-1.48.3.md'})
        updates = [body for method, _, body in self.backend.calls if method == 'PATCH']
        self.assertEqual(updates[0]['tag_name'], plan['archive_tag'])
        self.assertEqual(updates[0]['target_commitish'], self.source)
        self.telegram.assert_not_called()

    def test_complete_delivery_pins_reads_back_and_persists_receipt(self):
        result = self.publish()
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(result['pin_status'], 'complete')
        self.assertEqual(result['message_ids'], [81])
        self.assertEqual(result['previous_pinned_message_id'], 62)
        self.assertTrue(result['caption_matches_approved'])
        self.assertEqual(json.loads((self.directory / 'telegram-internal-delivery.json').read_text()), result)
        self.assertEqual(len(self.messages), 1)

    def test_successful_or_uncertain_local_delivery_cannot_be_repeated(self):
        plan = self.prepare()
        self.publish(plan)
        self.telegram.reset_mock()
        with self.assertRaisesRegex(ValueError, 'guard or receipt'):
            self.publish(plan)
        self.telegram.assert_not_called()

    def test_split_announcements_use_the_shared_checkpoint_sequence(self):
        self.notes.write_text('长公告😀' * 2000)
        result = self.publish()
        self.assertEqual(''.join(fields['text'] for method, fields, _ in self.messages if method == 'sendMessage'),
                         self.notes.read_text())
        self.assertEqual(result['expected_messages'], len(self.messages))
        self.assertEqual(len(result['message_ids']), len(self.messages))
        self.assertTrue(result['caption_matches_approved'])

    def test_ambiguous_send_is_not_retried_and_retains_both_guards(self):
        self.fail_send = True
        plan = self.prepare()
        with self.assertRaisesRegex(RuntimeError, 'send result uncertain'):
            self.publish(plan)
        self.assertEqual(len(self.messages), 1)
        self.assertTrue((self.directory / 'telegram-internal-delivery-started.json').exists())
        self.assertIn('telegram-internal-delivery-started.json', [a['name'] for a in self.backend.current['assets']])
        with self.assertRaisesRegex(ValueError, 'guard or receipt'):
            self.publish(plan)
        self.assertEqual(len(self.messages), 1)

    def test_remote_checkpoint_failure_retains_local_message_id(self):
        self.backend.fail_receipt = True
        with self.assertRaisesRegex(RuntimeError, 'Private archive upload failed'):
            self.publish()
        receipt = json.loads((self.directory / 'telegram-internal-delivery.json').read_text())
        self.assertEqual(receipt['message_ids'], [81])
        self.assertEqual(receipt['status'], 'partial')
        self.assertEqual(len(self.messages), 1)

    def test_remote_marker_blocks_a_second_output_directory(self):
        self.publish()
        self.directory = self.root / 'another-output'
        plan = self.prepare()
        self.telegram.reset_mock()
        with self.assertRaisesRegex(ValueError, 'guard or receipt'):
            self.publish(plan)
        self.telegram.assert_not_called()

    def test_private_repository_and_source_guards(self):
        plan = self.prepare()
        archive = internal.PrivateDraftArchive(plan, self.directory)
        self.backend.private = False
        with self.assertRaisesRegex(ValueError, 'private source'):
            archive.ensure()
        self.backend.private = True
        archive.ensure()
        for key, value in [('draft', False), ('target_commitish', 'b' * 40), ('tag_name', 'untagged-fixture')]:
            original = self.backend.current[key]
            self.backend.current[key] = value
            with self.assertRaisesRegex(ValueError, 'identity/source'):
                archive.inspect()
            self.backend.current[key] = original
        self.telegram.assert_not_called()

    def test_archive_body_and_apk_digest_changes_are_rejected(self):
        plan = self.prepare()
        archive = internal.PrivateDraftArchive(plan, self.directory)
        archive.ensure()
        old = self.backend.current['body']
        self.backend.current['body'] = 'changed'
        with self.assertRaisesRegex(ValueError, 'announcement changed'):
            archive.inspect()
        self.backend.current['body'] = old
        self.backend.current['assets'][0]['digest'] = 'sha256:' + '0' * 64
        with self.assertRaisesRegex(ValueError, 'size/digest differs'):
            archive.inspect()
        with self.assertRaisesRegex(ValueError, 'different archived APK'):
            archive.ensure()

    def test_local_data_changed_during_archiving_is_rejected(self):
        plan = self.prepare()
        archive = internal.PrivateDraftArchive(plan, self.directory)
        archive.ensure()
        notes = self.directory / 'new-soul-internal-1.48.3.md'
        notes.write_text('Changed while archiving')
        with self.assertRaisesRegex(ValueError, 'changed during archiving'):
            archive.inspect()
        self.telegram.assert_not_called()

    def test_wrong_target_environment_is_rejected(self):
        plan = self.prepare()
        for key, value in [('GH_REPO', 'public/repo'), ('TELEGRAM_INTERNAL_CHAT_ID', '-100789'),
                           ('TELEGRAM_INTERNAL_MESSAGE_THREAD_ID', '42'), ('TELEGRAM_CHAT_ID', '-100456')]:
            with patch.dict(os.environ, {key: value}), self.assertRaises(ValueError):
                self.publish(plan)
        self.telegram.assert_not_called()

    def test_wrong_group_title_prevents_send(self):
        self.bad_title = True
        with self.assertRaisesRegex(ValueError, 'title differs'):
            self.publish()
        self.assertFalse(self.messages)
        self.assertFalse((self.directory / 'telegram-internal-delivery-started.json').exists())

    def test_pin_failure_keeps_message_and_checkpoint(self):
        self.fail_pin = True
        with self.assertRaisesRegex(RuntimeError, 'pin result uncertain'):
            self.publish()
        receipt = json.loads((self.directory / 'telegram-internal-delivery.json').read_text())
        self.assertEqual(receipt['message_ids'], [81])
        self.assertEqual(receipt['status'], 'partial')
        self.assertEqual(receipt['pin_status'], 'pending')
        self.assertEqual(len(self.messages), 1)

    def test_readback_mismatch_does_not_claim_complete_or_resend(self):
        self.bad_readback = True
        with self.assertRaisesRegex(ValueError, 'does not match'):
            self.publish()
        receipt = json.loads((self.directory / 'telegram-internal-delivery.json').read_text())
        self.assertEqual(receipt['status'], 'partial')
        self.assertEqual(receipt['pin_status'], 'complete')
        self.assertEqual(len(self.messages), 1)

    def test_upload_error_does_not_expose_credentials(self):
        plan = self.prepare()
        archive = internal.PrivateDraftArchive(plan, self.directory)
        archive.ensure()
        path = self.directory / 'approval.json'
        path.write_text('{}')
        with patch.object(internal.urllib.request, 'urlopen', side_effect=RuntimeError('SECRET')):
            with self.assertRaises(RuntimeError) as error:
                archive.upload(path)
        self.assertNotIn('SECRET', str(error.exception))

    def test_immutable_guard_cannot_be_replaced(self):
        self.publish()
        plan = json.loads((self.directory / internal.PLAN).read_text())
        with self.assertRaisesRegex(ValueError, 'Immutable'):
            internal.PrivateDraftArchive(plan, self.directory).upload(
                self.directory / 'telegram-internal-delivery-started.json', replace=True)


if __name__ == '__main__':
    unittest.main()
