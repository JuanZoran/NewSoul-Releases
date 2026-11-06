import json
import io
from email.message import Message
import os
import socket
import ssl
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.error
import urllib.request
import urllib.response
from unittest.mock import Mock, patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import lanzou_release as subject


class LanzouTests(unittest.TestCase):
    def client(self):
        client = subject.Lanzou('ylogin=123456; phpdisk_info=test', '14007423')
        client.vei = 'fixture-token'
        return client

    def test_account_uid_is_required_and_only_sent_to_backend(self):
        for cookie in ('phpdisk_info=test', 'ylogin=secret', 'ylogin=12&x=3',
                       'ylogin=1; ylogin=2', 'not_ylogin=123', 'ylogin=', 'ylogin=１２３'):
            with self.subTest(cookie=cookie), self.assertRaises(ValueError) as context:
                subject.Lanzou(cookie, '14007423')
            self.assertNotIn(cookie, str(context.exception))
        client = subject.Lanzou('phpdisk_info=test; ylogin = 123456 ', '14007423')
        client.vei = 'fixture-token'
        client.request = Mock(return_value={'zt': 1})
        client.api(task=18, file_id='14007423')
        client.request.assert_called_once_with('/doupload.php?uid=123456&vei=fixture-token', b'task=18&file_id=14007423&vei=fixture-token',
                                               'application/x-www-form-urlencoded')

    def test_cookie_refresh_from_session_get_is_used_by_post(self):
        captured = []

        class Transport(urllib.request.BaseHandler):
            handler_order = 100

            def https_open(self, request):
                captured.append((request.get_method(), request.get_header('Cookie')))
                headers = Message()
                if request.get_method() == 'GET':
                    headers.add_header('Set-Cookie', 'phpdisk_info=refreshed; Path=/; Secure; HttpOnly')
                    headers.add_header('Set-Cookie', 'session=new-session; Domain=.woozooo.com; Path=/; Secure')
                    headers.add_header('Set-Cookie', 'foreign=reject; Domain=evil.com; Path=/')
                    body = b"var u='doupload.php?uid=123456';data:{'vei':'token'}"
                else:
                    body = b'{"zt":1}'
                response = urllib.response.addinfourl(io.BytesIO(body), headers, request.full_url, 200)
                response.msg = 'OK'
                return response

        client = subject.Lanzou('ylogin=123456; phpdisk_info=old', '14007423')
        client.opener = urllib.request.build_opener(Transport(), subject.NoRedirect(),
                                                    urllib.request.HTTPCookieProcessor(client.cookie_jar))
        self.assertEqual(client.api(task=18), {'zt': 1})
        self.assertEqual([item[0] for item in captured], ['GET', 'POST'])
        self.assertIn('phpdisk_info=old', captured[0][1])
        self.assertIn('phpdisk_info=refreshed', captured[1][1])
        self.assertIn('session=new-session', captured[1][1])
        self.assertNotIn('phpdisk_info=old', captured[1][1])
        self.assertNotIn('foreign=', captured[1][1])
        for origin in ('https://up.woozooo.com', 'https://child.pc.woozooo.com', 'http://pc.woozooo.com', 'https://evil.com'):
            request = urllib.request.Request(origin + '/doupload.php')
            client.cookie_jar.add_cookie_header(request)
            self.assertIsNone(request.get_header('Cookie'))

    @patch.object(subject, 'verify_public_folder')
    def test_folder_identity_and_password(self, verify):
        client = self.client()
        client.api = Mock(return_value={'zt': 1, 'text': [{'fol_id': '14007423', 'name': 'Renamed'}]})
        client.files = Mock(return_value=[])
        client.check_folder()
        verify.assert_not_called()
        client.api.assert_called_once_with(task=47, folder_id=-1)
        client.files.assert_called_once()

    def test_wrong_folder_or_account_root_fails_before_listing(self):
        client = self.client()
        client.folder = '9'
        client.api = Mock()
        client.files = Mock()
        with self.assertRaises(ValueError):
            client.check_folder()
        client.api.assert_not_called()
        client.files.assert_not_called()
        client.folder = subject.EXPECTED_FOLDER_ID
        for result in ({'zt': 9, 'text': []}, {'zt': 1, 'text': [{'fol_id': '9'}]},
                       {'zt': 1, 'text': []}, {'zt': 1, 'text': 'invalid'}):
            client.api.return_value = result
            with self.subTest(result=result), self.assertRaises(ValueError):
                client.check_folder()
        client.files.assert_not_called()

    @patch.object(subject, 'Lanzou')
    @patch.object(subject, 'verify_public_folder')
    def test_public_cli_needs_no_account_and_rejects_combined_modes(self, verify, account):
        with patch.object(sys, 'argv', ['lanzou_release.py', '--check-public']):
            subject.main()
        verify.assert_called_once_with(subject.EXPECTED_FOLDER_ID)
        account.assert_not_called()
        for flag in ('--upload', '--check-config'):
            verify.reset_mock()
            with patch.object(sys, 'argv', ['lanzou_release.py', '--check-public', flag]):
                with self.assertRaises(ValueError):
                    subject.main()
            verify.assert_not_called()

    def test_public_fixture_parser_and_fail_closed_identity(self):
        # Static structure preserved from the real public release page; public
        # transient tokens replaced with inert fixture values.
        page = """var ibjr73 = '1790352122';var _hi38w = 'fixturekey';
        url : '/filemoreajax.php?file=14007423',
        data : {'lx':2,'fid':14007423,'uid':'123','puid':'fixturepublic',
        'pg':pgs,'rep':'0','t':ibjr73,'k':_hi38w,'up':1,'ls':1,'pwd':pwd}"""
        endpoint, raw = subject.parse_public_page(page, '14007423')
        fields = urllib.parse.parse_qs(raw.decode())
        self.assertEqual(endpoint, '/filemoreajax.php?file=14007423')
        self.assertEqual(fields['k'], ['fixturekey'])
        self.assertEqual(fields['pwd'], ['3gin'])
        self.assertEqual(fields['pg'], ['1'])
        for bad in (page.replace('file=14007423', 'file=9'), page.replace("'k':_hi38w", "'k':runCode()")):
            with self.assertRaises(ValueError):
                subject.parse_public_page(bad, '14007423')
        with self.assertRaises(ValueError):
            subject.parse_public_page(page, '9')
        with patch.object(subject, 'public_request', side_effect=[page, '{"zt":1,"text":[]}']):
            subject.verify_public_folder('14007423')
        with patch.object(subject, 'public_request', side_effect=[page, '{"zt":3,"text":[]}']):
            with self.assertRaises(ValueError):
                subject.verify_public_folder('14007423')

    def test_public_request_is_separate_and_cookie_free(self):
        client = self.client()
        response = Mock()
        response.status = 200
        response.read.return_value = b'{}'
        opener = Mock()
        opener.open.return_value.__enter__ = Mock(return_value=response)
        opener.open.return_value.__exit__ = Mock(return_value=False)
        with patch.object(subject.urllib.request, 'build_opener', return_value=opener) as build:
            subject.public_request(subject.SHARE_PATH)
        self.assertEqual(len(build.call_args.args), 1)
        self.assertIsInstance(build.call_args.args[0], subject.NoRedirect)
        request = opener.open.call_args.args[0]
        self.assertEqual(request.full_url, subject.PUBLIC_ORIGIN + subject.SHARE_PATH)
        self.assertIsNone(request.get_header('Cookie'))
        self.assertEqual(opener.open.call_args.kwargs['timeout'], 20)
        response.read.assert_called_once_with(512 * 1024 + 1)
        self.assertIsNot(client.opener, opener)
        with self.assertRaises(ValueError):
            subject.public_request('//evil.com/')

    def test_session_page_static_tokens_only(self):
        prefix = '''<script>var target='doupload.php?uid=123456';'''
        for snippet in ["data : {'vei':'token123','task':18}",
                        "var v='token123'; data : {'vei':v,'task':18}",
                        'let v="token123"; data: {"vei":v}',
                        "/* data:{'vei':'ignore'} */ data:{'vei':'token123'}"]:
            self.assertEqual(subject.parse_session_page(prefix + snippet, '123456'), ('123456', 'token123'))
        for snippet in ["data:{'vei':runCode()}", "var v=runCode();data:{'vei':v}",
                        "data:{'vei':'a'};data:{'vei':'b'}", "data:{'vei':''}",
                        "// data:{'vei':'comment'}", "data:{'vei':'token\nsecret'}"]:
            with self.subTest(snippet=snippet), self.assertRaises(ValueError):
                subject.parse_session_page(prefix + snippet, '123456')
        with self.assertRaises(ValueError):
            subject.parse_session_page(prefix + "data:{'vei':'token'}", '9876')

    def test_opaque_session_token_is_query_encoded(self):
        token = 'a+/==&task=6'
        page = "var url='doupload.php?uid=123456';data:{'vei':'" + token + "'}"
        self.assertEqual(subject.parse_session_page(page, '123456'), ('123456', token))
        client = subject.Lanzou('ylogin=123456', '14007423')
        client.request = Mock(side_effect=[page, {'zt': 1}])
        client.api(task=18)
        self.assertEqual(client.request.call_args.args[0], '/doupload.php?uid=123456&vei=a%2B%2F%3D%3D%26task%3D6')

    def test_session_get_precedes_api_once(self):
        client = subject.Lanzou('ylogin=123456; phpdisk_info=test', '14007423')
        client.request = Mock(side_effect=["var url='doupload.php?uid=123456';var v='token';data:{'vei':v}", {'zt': 1}, {'zt': 1}])
        client.api(task=18)
        client.api(task=5)
        self.assertEqual(client.request.call_count, 3)
        self.assertEqual(client.request.call_args_list[0].args, ('/mydisk.php?item=files&action=index', None, 'text/html'))
        self.assertEqual(client.request.call_args_list[0].kwargs, {'text_response': True})
        self.assertEqual(client.request.call_args_list[1].args[0], '/doupload.php?uid=123456&vei=token')

    def test_invalid_session_never_sends_api(self):
        client = subject.Lanzou('ylogin=123456', '14007423')
        client.request = Mock(return_value='<html>Login or verification needed</html>')
        with self.assertRaises(ValueError):
            client.api(task=18)
        client.request.assert_called_once()

    def test_pagination_and_login_failure(self):
        client = self.client()
        row = {'id': '7', 'name_all': 'old.zip'}
        client.api = Mock(side_effect=[{'zt': 1, 'text': [row]}, {'zt': 1, 'info': 0, 'text': []}])
        self.assertEqual(client.files(), [row])
        self.assertEqual(client.api.call_count, 2)
        client.api = Mock(return_value={'zt': 1, 'text': [row]})
        with self.assertRaises(ValueError):
            client.files()
        client.api = Mock(return_value={'zt': 0, 'info': 0})
        with self.assertRaises(ValueError):
            client.files()

    def test_redirect_never_forwarded(self):
        with self.assertRaises(ValueError):
            subject.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://evil.com')

    def test_zip_exact_single_apk_and_reproducible(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            apk = base / 'new-soul-1.31.0.apk'
            apk.write_bytes(b'example verified apk')
            a = subject.package(apk, base / 'a')
            os.utime(apk, (123456789, 123456789))
            b = subject.package(apk, base / 'b')
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with zipfile.ZipFile(a) as archive:
                self.assertEqual(archive.namelist(), [apk.name])
                self.assertEqual(archive.read(apk.name), apk.read_bytes())

    @patch.dict(os.environ, {'GH_REPO': 'owner/repo'})
    @patch.object(subject.subprocess, 'run')
    @patch.object(subject, 'command')
    def test_duplicate_and_guard_stop_before_upload(self, command, run):
        client = self.client()
        client.check_folder = Mock()
        client.files = Mock(return_value=[{'id': '5', 'name_all': 'new-soul-1.31.0.zip'}])
        client.upload = Mock()
        command.return_value = json.dumps({'isDraft': False, 'isPrerelease': False, 'assets': []})
        with self.assertRaises(ValueError):
            subject.deliver(client, Path('new-soul-1.31.0.zip'), {'tag': 'v1.31.0'})
        run.assert_not_called()
        client.upload.assert_not_called()
        command.return_value = json.dumps({'isDraft': False, 'isPrerelease': False, 'assets': [{'name': subject.GUARD}]})
        client.check_folder.reset_mock()
        with self.assertRaises(ValueError):
            subject.deliver(client, Path('new-soul-1.31.0.zip'), {'tag': 'v1.31.0'})
        client.check_folder.assert_not_called()

    @patch.dict(os.environ, {'GH_REPO': 'owner/repo'})
    @patch.object(subject.subprocess, 'run')
    @patch.object(subject, 'command')
    def test_uncertain_upload_keeps_guard_and_does_not_retry(self, command, run):
        command.return_value = json.dumps({'isDraft': False, 'isPrerelease': False, 'assets': []})
        client = self.client()
        client.check_folder = Mock()
        client.files = Mock(return_value=[])
        client.upload = Mock(side_effect=ValueError('timeout'))
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / 'new-soul-1.31.0.zip'
            bundle.write_bytes(b'zip')
            with self.assertRaises(ValueError):
                subject.deliver(client, bundle, {'tag': 'v1.31.0'})
        self.assertEqual(run.call_count, 1)
        self.assertEqual(Path(run.call_args.args[0][-1]).name, subject.GUARD)
        client.upload.assert_called_once()

    def test_remote_error_is_redacted(self):
        client = self.client()
        client.opener = Mock()
        client.opener.open.side_effect = Exception('credential leaked remotely')
        with self.assertRaises(ValueError) as context:
            client.api(task=18)
        self.assertNotIn('credential', str(context.exception))

    def test_transport_diagnostics_are_safe_and_do_not_retry(self):
        secret = 'secret-cookie-or-url'
        cases = [
            (urllib.error.HTTPError('https://' + secret, 403, secret, {'Cookie': secret}, None), 'HTTP failure (status 403;'),
            (urllib.error.HTTPError('https://' + secret, 302, secret, {'Location': secret}, None), 'redirect blocked (status 302;'),
            (subject.RequestFailure('Lanzou redirect blocked. No automatic retry.'), 'redirect blocked'),
            (urllib.error.URLError(ssl.SSLError(secret)), 'TLS handshake/certificate failure'),
            (ssl.SSLError(secret), 'TLS handshake/certificate failure'),
            (urllib.error.URLError(socket.gaierror(secret)), 'DNS resolution failure'),
            (urllib.error.URLError(TimeoutError(secret)), 'connection/read timeout'),
            (TimeoutError(secret), 'connection/read timeout'),
            (urllib.error.URLError(secret), 'connection/network failure'),
            (ConnectionResetError(secret), 'connection/network failure'),
            (UnicodeEncodeError('ascii', secret, 0, 1, secret), 'invalid local data or header encoding'),
            (Exception(secret), 'unexpected transport error'),
        ]
        for error, category in cases:
            with self.subTest(category=category):
                client = self.client()
                client.opener = Mock()
                client.opener.open.side_effect = error
                with self.assertRaises(ValueError) as context:
                    client.api(task=18)
                message = str(context.exception)
                self.assertIn(category, message)
                self.assertNotIn(secret, message)
                self.assertIn('No automatic retry', message)
                client.opener.open.assert_called_once()

    def test_response_diagnostics_do_not_print_response(self):
        for raw, category in [
            (b'<html>secret</html>', 'non-JSON'),
            (b'\xffsecret', 'invalid-encoding'),
            (b'["secret"]', 'unexpected top-level type'),
            (b'x' * 2_000_001, 'exceeds 2 MB'),
        ]:
            with self.subTest(category=category):
                client = self.client()
                client.opener = Mock()
                response = Mock()
                response.read.return_value = raw
                client.opener.open.return_value.__enter__ = Mock(return_value=response)
                client.opener.open.return_value.__exit__ = Mock(return_value=False)
                with self.assertRaises(ValueError) as context:
                    client.api(task=18)
                self.assertIn(category, str(context.exception))
                self.assertNotIn('secret', str(context.exception))
                client.opener.open.assert_called_once()
                response.read.assert_called_once_with(2_000_001)

    @patch.dict(os.environ, {'GH_REPO': 'owner/repo'})
    @patch.object(subject.subprocess, 'run')
    @patch.object(subject, 'command')
    def test_receipt_requires_confirmed_directory_entry(self, command, run):
        command.return_value = json.dumps({'isDraft': False, 'isPrerelease': False, 'assets': []})
        client = self.client()
        client.check_folder = Mock()
        client.files = Mock(side_effect=[[], [{'id': '99', 'name_all': 'new-soul-1.31.0.zip'}]])
        client.upload = Mock(return_value='99')
        captured = []
        run.side_effect = lambda args, **kwargs: captured.append((Path(args[-1]).name, json.loads(Path(args[-1]).read_text())))
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / 'new-soul-1.31.0.zip'
            bundle.write_bytes(b'zip')
            subject.deliver(client, bundle, {'tag': 'v1.31.0'})
        self.assertEqual([x[0] for x in captured], [subject.GUARD, subject.RECEIPT])
        self.assertEqual(captured[1][1]['status'], 'complete')
        self.assertEqual(captured[1][1]['file_id'], '99')
        self.assertNotIn('Cookie', json.dumps(captured))

    def test_upload_response_identity(self):
        client = self.client()
        client.request = Mock(return_value={'zt': 1, 'text': [{'id': '99', 'name_all': 'other.zip'}]})
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / 'new-soul-1.31.0.zip'
            bundle.write_bytes(b'zip')
            with self.assertRaises(ValueError):
                client.upload(bundle)


if __name__ == '__main__':
    unittest.main()
