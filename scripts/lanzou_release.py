#!/usr/bin/env python3
"""Package a verified public APK; upload only with --upload. No third-party services."""
import argparse
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import socket
import ssl
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

from telegram_release import validate, require, command

ORIGIN = 'https://pc.woozooo.com'
SHARE_PATH = '/b007utki2d'
# Bound using both the real public share page and authenticated root listing.
EXPECTED_FOLDER_ID = '14007423'
PUBLIC_ORIGIN = 'https://wwbnj.lanzoux.com'
GUARD = 'lanzou-public-delivery-started.json'
RECEIPT = 'lanzou-public-delivery.json'
USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0'


def parse_public_page(page, expected_folder):
    def capture(text, pattern):
        match = re.search(pattern, text)
        require(match is not None, 'Public release page format changed')
        return match.group(1)
    endpoint = capture(page, r'''url\s*:\s*['"](/filemoreajax\.php\?file=[0-9]{1,12})['"]''')
    data = capture(page, r'data\s*:\s*\{([^}]{1,4096})\}')
    fid = capture(data, r'''['"]fid['"]\s*:\s*([0-9]{1,12})\s*,''')
    require(fid == expected_folder and endpoint == '/filemoreajax.php?file=' + fid,
            'Public share page does not identify the configured backend folder')
    fields = dict(lx='2', fid=fid, pwd='3gin', pg='1')
    for key in ('uid', 'puid', 'rep', 't', 'k', 'up', 'ls'):
        value = capture(data, r'''['"]''' + key + r'''['"]\s*:\s*([^,}]+)''').strip()
        if re.fullmatch(r'''['"][A-Za-z0-9_=-]{1,256}['"]''', value):
            value = value[1:-1]
        elif not re.fullmatch(r'[0-9]{1,12}', value):
            require(re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]{0,63}', value), 'Public page token is not static data')
            value = capture(page, r'\bvar\s+' + re.escape(value) + r'''\s*=\s*['"]([A-Za-z0-9_=-]{1,256})['"]\s*;''')
        fields[key] = value
    return endpoint, urllib.parse.urlencode(fields).encode()


def public_request(endpoint, data=None):
    require(endpoint == SHARE_PATH or re.fullmatch(r'/filemoreajax\.php\?file=[0-9]{1,12}', endpoint),
            'Invalid fixed public request path')
    # Deliberately separate from the account opener: no CookieJar or Cookie header.
    request = urllib.request.Request(PUBLIC_ORIGIN + endpoint, data=data, headers={
        'User-Agent': 'Mozilla/5.0 NewSoul-UpdateCheck', 'Referer': PUBLIC_ORIGIN + SHARE_PATH,
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'})
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=20) as response:
            require(response.status == 200, 'Public share request returned unexpected status')
            raw = response.read(512 * 1024 + 1)
        require(len(raw) <= 512 * 1024, 'Public share response exceeds 512 KiB limit')
        return raw.decode('utf-8')
    except Exception:
        raise ValueError('Public share request failed; no account Cookie sent and no automatic retry') from None


def verify_public_folder(folder):
    endpoint, fields = parse_public_page(public_request(SHARE_PATH), folder)
    try:
        result = json.loads(public_request(endpoint, fields))
    except json.JSONDecodeError:
        raise ValueError('Public share response is not JSON') from None
    require(isinstance(result, dict) and str(result.get('zt')) == '1' and isinstance(result.get('text'), list),
            'Public release folder cannot be read with its configured password')


def parse_session_page(page, cookie_uid):
    # Strip comments while preserving quoted strings; never evaluate JavaScript.
    page = re.sub(r'''("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|<!--.*?-->|/\*.*?\*/|//[^\r\n]*''',
                  lambda match: match.group(1) or '', page, flags=re.S)
    uids = set(re.findall(r'''\buid=([0-9]{1,32})(?=['"&;\s])''', page))
    require(uids == {cookie_uid}, 'Session page account UID missing or different from Cookie; no API request sent')
    values = set()
    for data in re.findall(r'\bdata\s*:\s*\{([^{}]*)\}', page):
        for match in re.finditer(r'''(?:['"]vei['"]|\bvei)\s*:\s*(?:'([^']*)'|"([^"]*)"|([A-Za-z_$][\w$]*|[0-9]+))\s*(?=,|$)''', data):
            single, double, expression = match.groups()
            if single is not None or double is not None:
                values.add(single if single is not None else double)
            elif expression.isascii() and expression.isdigit():
                values.add(expression)
            else:
                assignments = re.findall(r'''\b(?:var|let|const)\s+''' + re.escape(expression) + r'''\s*=\s*(?:'([^']*)'|"([^"]*)"|([0-9]+))\s*;''', page)
                require(len(assignments) == 1, 'Session page token is not a unique static assignment; no JavaScript executed')
                values.add(next((value for value in assignments[0] if value), ''))
    require(len(values) == 1, 'Session page token missing or ambiguous; login/challenge/API change possible')
    vei = next(iter(values))
    # This is opaque server data, encoded by urlencode before transmission.
    # Base64 punctuation and query delimiters must not be treated as code.
    require(re.fullmatch(r'[\x20-\x7e]{1,1024}', vei), 'Session page token has unsupported format')
    return cookie_uid, vei


class RequestFailure(ValueError):
    """Only locally selected diagnostic text; never include remote exception text."""


def network_category(error):
    if isinstance(error, ssl.SSLError):
        return 'TLS handshake/certificate failure'
    if isinstance(error, socket.gaierror):
        return 'DNS resolution failure'
    if isinstance(error, TimeoutError):
        return 'connection/read timeout'
    return 'connection/network failure'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the account Cookie, including to another Lanzou domain.
        raise RequestFailure('Lanzou redirect blocked. No automatic retry.')


class OriginCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    @staticmethod
    def allowed(request):
        url = urllib.parse.urlsplit(request.full_url)
        return url.scheme == 'https' and url.netloc == urllib.parse.urlsplit(ORIGIN).netloc

    def set_ok(self, cookie, request):
        return self.allowed(request) and super().set_ok(cookie, request)

    def return_ok(self, cookie, request):
        return self.allowed(request) and super().return_ok(cookie, request)


class Lanzou:
    def __init__(self, cookie, folder):
        require(cookie and '\r' not in cookie and '\n' not in cookie, 'Missing or invalid LANZOU_COOKIE')
        require(re.fullmatch(r'[1-9]\d*', folder), 'LANZOU_FOLDER_ID must be a positive backend folder ID')
        # The backend requires the account UID in the query as well as its Cookie.
        # Reject missing/ambiguous values instead of guessing or logging the header.
        uids = [value.strip() for part in cookie.split(';')
                for key, separator, value in [part.strip().partition('=')]
                if separator and key.strip() == 'ylogin']
        require(len(uids) == 1 and re.fullmatch(r'[0-9]{1,32}', uids[0]),
                'LANZOU_COOKIE must contain exactly one numeric ylogin value')
        self.uid = uids[0]
        self.vei = None
        self.folder = folder
        self.cookie_jar = http.cookiejar.CookieJar(policy=OriginCookiePolicy())
        names = set()
        for part in cookie.split(';'):
            if not part.strip():
                continue
            name, separator, value = part.strip().partition('=')
            name, value = name.strip(), value.strip()
            require(separator and re.fullmatch(r'[!#$%&\x27*+.^_`|~0-9A-Za-z-]+', name)
                    and name not in names and re.fullmatch(r'[\x20-\x7e]*', value),
                    'LANZOU_COOKIE has invalid or duplicate cookie fields')
            names.add(name)
            self.cookie_jar.set_cookie(http.cookiejar.Cookie(
                version=0, name=name, value=value, port=None, port_specified=False,
                domain=urllib.parse.urlsplit(ORIGIN).hostname, domain_specified=False,
                domain_initial_dot=False, path='/', path_specified=True, secure=True,
                expires=None, discard=True, comment=None, comment_url=None, rest={}, rfc2109=False))
        self.opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(self.cookie_jar))

    def request(self, endpoint, data, content_type, *, text_response=False):
        require(endpoint.startswith('/') and not endpoint.startswith('//'), 'Invalid fixed-origin request path')
        req = urllib.request.Request(ORIGIN + endpoint, data=data, headers={
            'Content-Type': content_type,
            'User-Agent': USER_AGENT,
            'Referer': ORIGIN + '/mydisk.php?item=files&action=index&u=' + self.uid,
            'Origin': ORIGIN,
        })
        if not text_response:
            req.add_header('X-Requested-With', 'XMLHttpRequest')
            req.add_header('Accept', 'application/json, text/javascript, */*; q=0.01')
        try:
            with self.opener.open(req, timeout=180) as response:
                raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                raise RequestFailure('Lanzou response exceeds 2 MB limit. No automatic retry.')
            if text_response:
                try:
                    return raw.decode('utf-8')
                except UnicodeDecodeError:
                    raise RequestFailure('Lanzou session page has invalid UTF-8 encoding. No automatic retry.') from None
            try:
                result = json.loads(raw)
            except (json.JSONDecodeError, UnicodeDecodeError):
                raise RequestFailure('Lanzou returned a non-JSON or invalid-encoding response; login page, verification challenge or API change possible. No automatic retry.') from None
            if not isinstance(result, dict):
                raise RequestFailure('Lanzou JSON response has an unexpected top-level type. No automatic retry.')
            return result
        except RequestFailure:
            raise
        except urllib.error.HTTPError as error:
            # Only numeric status is safe. Headers, reason, URL and body are omitted.
            code = error.code if type(error.code) is int and 100 <= error.code <= 599 else 'unknown'
            category = 'redirect blocked' if type(code) is int and 300 <= code <= 399 else 'HTTP failure'
            challenge = False
            try:
                error_body = error.read(2_000_001)
                challenge = isinstance(error_body, bytes) and b'acw_sc__v2' in error_body
            except Exception:
                pass
            raise RequestFailure(f'Lanzou {category} (status {code}; known_challenge={str(challenge).lower()}). No automatic retry.') from None
        except urllib.error.URLError as error:
            raise RequestFailure('Lanzou ' + network_category(error.reason) + '. No automatic retry.') from None
        except (OSError, TimeoutError) as error:
            raise RequestFailure('Lanzou ' + network_category(error) + '. No automatic retry.') from None
        except (UnicodeError, ValueError):
            raise RequestFailure('Lanzou request has invalid local data or header encoding. No automatic retry.') from None
        except Exception:
            # A remote error body/header can contain account data. Never print it.
            raise RequestFailure('Lanzou request failed with an unexpected transport error. No automatic retry.') from None

    def api(self, **fields):
        if self.vei is None:
            page = self.request('/mydisk.php?item=files&action=index', None, 'text/html', text_response=True)
            self.uid, self.vei = parse_session_page(page, self.uid)
        fields = dict(fields, vei=self.vei)
        return self.request('/doupload.php?' + urllib.parse.urlencode(dict(uid=self.uid, vei=self.vei)), urllib.parse.urlencode(fields).encode(),
                            'application/x-www-form-urlencoded')

    def check_folder(self):
        require(self.folder == EXPECTED_FOLDER_ID, 'Configured folder differs from the verified release destination')
        result = self.api(task=47, folder_id=-1)
        require(result.get('zt') == 1 and isinstance(result.get('text'), list),
                'Cannot verify release folder in the authenticated account root')
        require(any(isinstance(row, dict) and str(row.get('fol_id')) == EXPECTED_FOLDER_ID
                    for row in result['text']), 'Verified release folder is absent from the authenticated account root')
        self.files()

    def files(self):
        files = []
        seen = set()
        for page in range(1, 101):
            result = self.api(task=5, folder_id=self.folder, pg=page)
            require(result.get('zt') == 1, 'Cannot list release folder; refusing upload')
            rows = result.get('text')
            if result.get('info') == 0 and rows in (None, [], ''):
                return files
            require(isinstance(rows, list), 'Malformed file list; refusing upload')
            if not rows:
                return files
            for row in rows:
                require(isinstance(row, dict) and row.get('id') and isinstance(row.get('name_all'), str), 'Malformed file entry')
                key = str(row['id'])
                require(key not in seen, 'Repeated file page; refusing upload')
                seen.add(key)
                files.append(row)
        raise ValueError('Too many file pages; refusing upload')

    def upload(self, bundle):
        boundary = 'NewSoul' + uuid.uuid4().hex
        parts = []
        for name, value in dict(task='1', vie='2', ve='2', id='WU_FILE_0',
                                folder_id_bb_n=self.folder, name=bundle.name).items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
        parts.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="upload_file"; filename="{bundle.name}"\r\nContent-Type: application/zip\r\n\r\n'.encode(),
                      bundle.read_bytes(), f'\r\n--{boundary}--\r\n'.encode()])
        result = self.request('/html5up.php', b''.join(parts), 'multipart/form-data; boundary=' + boundary)
        require(result.get('zt') == 1, 'Upload was not confirmed; inspect remote folder before retrying')
        rows = result.get('text')
        require(isinstance(rows, list) and len(rows) == 1 and isinstance(rows[0], dict), 'Upload response incomplete; inspect remote folder')
        row = rows[0]
        require(row.get('name_all') == bundle.name and str(row.get('id', '')).isdigit(), 'Upload identity mismatch; inspect remote folder')
        return str(row['id'])


def package(apk, destination):
    require(re.fullmatch(r'new-soul-\d+\.\d+\.\d+\.apk', apk.name), 'Unexpected APK filename')
    destination.mkdir(parents=True, exist_ok=True)
    bundle = destination / (apk.stem + '.zip')
    require(not bundle.exists(), 'ZIP output exists; select an empty output directory')
    with zipfile.ZipFile(bundle, 'x', zipfile.ZIP_DEFLATED) as archive:
        # Stable metadata makes reruns byte-for-byte reproducible.
        entry = zipfile.ZipInfo(apk.name, (1980, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o644 << 16
        archive.writestr(entry, apk.read_bytes())
    with zipfile.ZipFile(bundle) as archive:
        require(archive.namelist() == [apk.name] and archive.testzip() is None
                and archive.read(apk.name) == apk.read_bytes(), 'ZIP verification failed')
    require(bundle.stat().st_size <= 100_000_000, 'ZIP exceeds conservative upload limit')
    return bundle


def deliver(client, bundle, info):
    require(os.environ.get('GH_REPO'), 'GH_REPO is required')
    current = json.loads(command('gh', 'release', 'view', info['tag'], '--json', 'assets,isDraft,isPrerelease'))
    require(not current['isDraft'] and not current['isPrerelease'], 'Release is not final')
    require(not any(a['name'] in (GUARD, RECEIPT) for a in current['assets']), 'Delivery marker exists; inspect previous upload before retrying')
    client.check_folder()
    require(not any(row['name_all'] == bundle.name for row in client.files()), 'Same filename already exists; refusing overwrite or duplicate upload')
    info = dict(info, zip_name=bundle.name, zip_sha256=hashlib.sha256(bundle.read_bytes()).hexdigest(),
                folder_id=client.folder, status='started')
    with tempfile.TemporaryDirectory() as directory:
        guard = Path(directory) / GUARD
        guard.write_text(json.dumps(info, indent=2) + '\n')
        subprocess.run(['gh', 'release', 'upload', info['tag'], str(guard)], check=True)
        # Do not retry uploads. A timeout may occur after the server stores the ZIP.
        file_id = client.upload(bundle)
        require(any(str(row['id']) == file_id and row['name_all'] == bundle.name for row in client.files()),
                'Uploaded file not confirmed in folder; inspect manually')
        info.update(status='complete', file_id=file_id)
        receipt = Path(directory) / RECEIPT
        receipt.write_text(json.dumps(info, indent=2) + '\n')
        subprocess.run(['gh', 'release', 'upload', info['tag'], str(receipt)], check=True)
    print('Upload acknowledged and listed in the target folder; receipt saved. Remote bytes were not re-downloaded.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--release-json', type=Path)
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--build-tools', type=Path, default=Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk')) / 'build-tools/35.0.0')
    parser.add_argument('--upload', action='store_true')
    parser.add_argument('--check-config', action='store_true', help='Read-only account and target folder verification; no release needed')
    parser.add_argument('--check-public', action='store_true', help='Read-only fixed public link, folder ID and password check; no account needed')
    args = parser.parse_args()
    if args.check_public:
        require(not args.upload and not args.check_config, '--check-public cannot be combined with --upload or --check-config')
        verify_public_folder(EXPECTED_FOLDER_ID)
        print('Fixed public release link, folder ID and password verified. No account credentials used.')
        return
    if args.check_config:
        require(not args.upload, '--check-config cannot be combined with --upload')
        client = Lanzou(os.environ.get('LANZOU_COOKIE', '').strip(), os.environ.get('LANZOU_FOLDER_ID', '').strip())
        print('Checking the fixed release destination in the authenticated account root.', flush=True)
        client.check_folder()
        print('Account can list the verified release folder. No upload performed.')
        return
    require(args.apk and args.release_json and args.output_dir, '--apk, --release-json and --output-dir are required')
    release = json.loads(args.release_json.read_text())
    _, info = validate(args.apk, release, args.build_tools)
    bundle = package(args.apk, args.output_dir)
    print('Verified public release ZIP: ' + bundle.name)
    if args.upload:
        deliver(Lanzou(os.environ.get('LANZOU_COOKIE', '').strip(), os.environ.get('LANZOU_FOLDER_ID', '').strip()), bundle, info)
    else:
        print('Dry run: no account or network access.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Only local validation messages are safe; suppress subprocess/remote details.
        print(str(error) if isinstance(error, ValueError) else 'Release preparation/delivery failed; inspect workflow step status.', file=sys.stderr)
        sys.exit(1)
