#!/usr/bin/env python3
"""Validate public releases; dry-run by default. Uses only the Python standard library."""
import argparse
from contextlib import nullcontext
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid
import zipfile

CERT = '0169d3c49680f45aa33186b24b77496f622a0477e4a3b4a283a5160fa072391e'
PRIVATE_REPO = 'JuanZoran/NewSoul'
RECEIPT = 'telegram-public-delivery.json'
GUARD = 'telegram-public-delivery-started.json'
# Same user-facing invitation as the plugin's Join TG Group entry.
GROUP_LINK = 'https://t.me/+3Hl2lJ1X-oZlZTZl'
GROUP_FOOTER = '\n\nNewSoul 群组：' + GROUP_LINK


def require(condition, message):
    if not condition:
        raise ValueError(message)


def command(*args):
    return subprocess.check_output(args, text=True)


def validate(apk, release, build_tools, edition='public'):
    require(edition in ('public', 'internal'), 'Unknown release edition')
    require(not release['isDraft'] and not release['isPrerelease'], 'Only final published releases may be sent')
    tag = release['tagName']
    require(re.fullmatch(r'v\d+\.\d+\.\d+', tag), 'Tag must be vMAJOR.MINOR.PATCH')
    notes = (release.get('body') or '').strip()
    require(notes and '<!-- TEMPLATE -->' not in notes, 'Write public release notes before publishing')
    return notes, validate_apk(apk, tag, build_tools, edition)


def validate_apk(apk, tag, build_tools, edition='public', *, allow_sdk_name=False):
    require(edition in ('public', 'internal'), 'Unknown release edition')
    require(re.fullmatch(r'v\d+\.\d+\.\d+', tag), 'Tag must be vMAJOR.MINOR.PATCH')
    prefix = 'new-soul-internal' if edition == 'internal' else 'new-soul'
    require(apk.name == f'{prefix}-{tag[1:]}.apk'
            or (allow_sdk_name and edition == 'internal' and apk.name == 'new-soul-internal.apk'),
            'APK filename must match release version')
    require(0 < apk.stat().st_size <= 50_000_000, 'APK must be nonempty and at most 50 MB')
    badging = command(str(build_tools / 'aapt2'), 'dump', 'badging', str(apk))
    identity = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    require(identity is not None, 'Cannot read APK identity')
    package, code, version = identity.groups()
    expected_package = 'io.github.juanzoran.newsoul' + ('.internal' if edition == 'internal' else '')
    require(package == expected_package, f'Refusing non-{edition} package')
    require(version == tag[1:], 'APK version differs from release tag')
    require('application-debuggable' not in badging, 'Refusing debuggable APK')
    certs = command(str(build_tools / 'apksigner'), 'verify', '--print-certs', str(apk))
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', certs, re.M)
    require([d.lower() for d in digests] == [CERT], 'Unexpected release signing certificate')
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate APK entries')
        require('newsoul/cac_runtime_v2.md' not in names, 'Plaintext internal rules found')
        require(all(n in names for n in ('newsoul/rules.bin', 'newsoul/ai_entry_v2.png')), 'AI resources missing')
    return {'tag': tag, 'edition': edition, 'version_code': int(code), 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}


def utf16len(value):
    return len(value.encode('utf-16-le')) // 2


def chunks(value, limit=4000):
    # Conservative UTF-16 budget, including emoji. Preserve all text.
    part, size = [], 0
    for char in value:
        width = utf16len(char)
        if size + width > limit:
            yield ''.join(part)
            part, size = [], 0
        part.append(char)
        size += width
    if part:
        yield ''.join(part)


def message_plan(tag, notes, edition='public', audience=None):
    require(edition in ('public', 'internal'), 'Unknown release edition')
    audience = audience or edition
    require(audience in ('public', 'internal'), 'Unknown release audience')
    title = f'New Soul {tag}｜' + ('内部版' if edition == 'internal' else '统一版')
    footer = '' if audience == 'internal' else GROUP_FOOTER
    full = title + '\n\n' + notes + footer
    if utf16len(full) <= 1024:
        return full, []
    caption = title + '\n\n完整更新说明见下方回复。' + footer
    # Each separately forwarded part retains a usable group entrance.
    extra = [part + footer for part in chunks(notes, 4000 - utf16len(footer))]
    return caption, extra


def telegram(method, fields, apk=None):
    boundary = uuid.uuid4().hex
    body = bytearray()
    for key, value in fields.items():
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    if apk:
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="document"; filename="{apk.name}"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n'.encode())
        body.extend(apk.read_bytes())
        body.extend(b'\r\n')
    body.extend(f'--{boundary}--\r\n'.encode())
    request = urllib.request.Request(
        f'https://api.telegram.org/bot{os.environ["TELEGRAM_BOT_TOKEN"]}/{method}',
        data=bytes(body), headers={'Content-Type': f'multipart/form-data; boundary={boundary}'})
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            result = json.load(response)
        require(result.get('ok') is True, 'Telegram rejected request; inspect delivery before retrying')
        return result['result']['message_id'] if method in ('sendDocument', 'sendMessage') else result['result']
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, KeyError):
        # Never print exception URLs: they contain the bot token. Never retry an ambiguous send.
        raise RuntimeError('Telegram send failed or result is uncertain. Check the chat and release receipt; automatic resend is blocked.') from None


def installation_entities(text):
    # Telegram offsets use UTF-16 code units; keep the original text unchanged.
    start = text.find('安装说明\n')
    if start < 0:
        return []
    end = text.find('\n\n', start)
    if end < 0:
        end = len(text)
    return [{'type': 'bold', 'offset': utf16len(text[:start]),
             'length': utf16len(text[start:end])}]


def delivery_names(edition):
    require(edition in ('public', 'internal'), 'Unknown release edition')
    return ('telegram-internal-delivery-started.json', 'telegram-internal-delivery.json') if edition == 'internal' else (GUARD, RECEIPT)


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    try:
        temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def send(apk, release, info, caption, extra, *, archive=None, output_dir=None, verify_pinned=False):
    require(os.environ.get('GH_REPO'), 'GH_REPO is required')
    edition = info.get('edition', 'public')
    require(edition in ('public', 'internal'), 'Unknown release edition')
    audience = info.get('audience', edition)
    require(audience in ('public', 'internal'), 'Unknown release audience')
    internal = audience == 'internal'
    if internal:
        require(os.environ['GH_REPO'] == PRIVATE_REPO, 'Internal delivery requires the private source repository')
    chat_key = 'TELEGRAM_INTERNAL_CHAT_ID' if internal else 'TELEGRAM_CHAT_ID'
    for key in ('TELEGRAM_BOT_TOKEN', chat_key):
        require(os.environ.get(key, '').strip(), f'Missing {key}')
    if internal:
        require(os.environ[chat_key].strip() != os.environ.get('TELEGRAM_CHAT_ID', '').strip(), 'Internal target equals public target')
    thread = os.environ.get('TELEGRAM_INTERNAL_MESSAGE_THREAD_ID' if internal else 'TELEGRAM_MESSAGE_THREAD_ID', '').strip()
    require(not thread or (thread.isdigit() and int(thread) > 0), 'Invalid Telegram topic ID')
    # Fetch fresh assets; a workflow rerun must not blindly repeat a previous send.
    if archive is None:
        current = json.loads(command('gh', 'release', 'view', info['tag'], '--json', 'assets,isDraft,isPrerelease'))
        require(not current['isDraft'] and not current['isPrerelease'], 'Release is no longer a final published release')
    else:
        require(internal and output_dir is not None and verify_pinned and info.get('pin_release'),
                'Private draft delivery requires persistent internal receipts and verified pinning')
        current = archive.inspect()
    guard_name, receipt_name = delivery_names(audience)
    require(not any(a['name'] in (guard_name, receipt_name) for a in current['assets']),
            'Delivery guard or receipt already exists. Check the chat before any manual resend.')
    fields = {'chat_id': os.environ[chat_key]}
    if thread:
        fields['message_thread_id'] = thread
    info = dict(info, chat_id=fields['chat_id'], status='started', message_ids=[], expected_messages=1 + len(extra))
    workspace = nullcontext(output_dir) if output_dir is not None else tempfile.TemporaryDirectory()
    with workspace as directory:
        directory = Path(directory)
        directory.mkdir(parents=True, exist_ok=True)
        receipt = Path(directory) / receipt_name
        guard = Path(directory) / guard_name
        require(not guard.exists() and not receipt.exists(), 'Local delivery guard or receipt already exists')

        def upload(path, replace=False):
            if archive is not None:
                archive.upload(path, replace=replace)
            else:
                args = ['gh', 'release', 'upload', info['tag'], str(path)]
                subprocess.run(args + (['--clobber'] if replace else []), check=True)

        def save():
            write_json(receipt, info)
            upload(receipt, replace=True)

        if verify_pinned:
            before = telegram('getChat', {'chat_id': fields['chat_id']})
            require(str(before['id']) == fields['chat_id'], 'Telegram chat identity differs from target')
            if info.get('chat_title'):
                require(before['title'] == info['chat_title'], 'Telegram chat title differs from approved target')
            info['previous_pinned_message_id'] = before.get('pinned_message', {}).get('message_id')

        # Never overwrite this guard. Updating a GitHub asset uses delete + upload;
        # a separate immutable guard prevents that replacement window from allowing a resend.
        with guard.open('x') as stream:
            stream.write(json.dumps(info, ensure_ascii=False, indent=2) + '\n')
        upload(guard)
        try:
            message_id = telegram('sendDocument', dict(fields, caption=caption, caption_entities=json.dumps(installation_entities(caption))), apk)
            info['message_ids'].append(message_id)
            info['status'] = 'partial'
            save()
            for text in extra:
                reply = json.dumps({'message_id': message_id})
                info['message_ids'].append(telegram('sendMessage', dict(fields, text=text, entities=json.dumps(installation_entities(text)), reply_parameters=reply)))
                save()
            if info.get('pin_release'):
                # Pin the APK announcement after all notes arrive; never resend on pin failure.
                info['pin_status'] = 'pending'
                save()
                require(telegram('pinChatMessage', dict(chat_id=fields['chat_id'], message_id=message_id, disable_notification='true')) is True,
                        'Telegram did not confirm pinning')
                info['pinned_message_id'] = message_id
                info['pin_status'] = 'complete'
                save()
                if verify_pinned:
                    after = telegram('getChat', {'chat_id': fields['chat_id']})
                    pinned = after.get('pinned_message', {})
                    document = pinned.get('document', {})
                    require(str(after['id']) == fields['chat_id'] and pinned.get('message_id') == message_id
                            and pinned.get('caption') == caption and document.get('file_name') == apk.name
                            and document.get('file_size') == apk.stat().st_size,
                            'Pinned announcement or APK does not match; inspect existing messages before recovery')
                    info.update(caption_matches_approved=True, file_name=apk.name, file_size=apk.stat().st_size)
            info['status'] = 'complete'
            save()
        except Exception:
            print('Delivery stopped. The persistent receipt blocks automatic repetition.', file=sys.stderr)
            raise
    print(f'Sent {len(info["message_ids"])} Telegram message(s). Receipt saved.')
    return info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--release-json', type=Path, required=True,
                        help='gh release view output: tagName,body,isDraft,isPrerelease')
    parser.add_argument('--build-tools', type=Path, default=Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk')) / 'build-tools/35.0.0')
    parser.add_argument('--send', action='store_true', help='Actually send; default is offline validation only')
    parser.add_argument('--edition', choices=('public', 'internal'), default='public')
    parser.add_argument('--audience', choices=('public', 'internal'), help='Delivery target; independent of APK identity')
    parser.add_argument('--pin', action='store_true', help='Pin the APK announcement after sending')
    args = parser.parse_args()
    release = json.loads(args.release_json.read_text())
    notes, info = validate(args.apk, release, args.build_tools, args.edition)
    info['pin_release'] = args.pin
    info['audience'] = args.audience or args.edition
    caption, extra = message_plan(info['tag'], notes, args.edition, info['audience'])
    print(json.dumps(dict(info, caption=caption, caption_entities=installation_entities(caption), extra_messages=extra), ensure_ascii=False, indent=2))
    if args.send:
        from telegram_check import check
        check(info['audience'], args.pin)
        send(args.apk, release, info, caption, extra)
    else:
        print('Dry run passed; no GitHub mutation or Telegram request made.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f'Release stopped: {error}', file=sys.stderr)
        sys.exit(1)
