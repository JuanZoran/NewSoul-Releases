#!/usr/bin/env python3
"""Prepare a private internal release; send requires the reviewed plan's approval digest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request

import telegram_release as delivery

REPO = delivery.PRIVATE_REPO
PLAN = 'internal-release.json'


def digest(value):
    return hashlib.sha256(value).hexdigest()


def unstarted(directory, assets=()):
    markers = delivery.delivery_names('internal')
    delivery.require(not any((directory / name).exists() for name in markers)
                     and not any(asset['name'] in markers for asset in assets),
                     'Delivery guard or receipt already exists; inspect the existing delivery before recovery')


def approved_payload(plan):
    # Freeze the actual messages, recipient/topic and APK, including split announcements.
    keys = ('tag', 'version_code', 'sha256', 'source_commit', 'chat_id', 'chat_title',
            'thread_id', 'caption', 'extra_messages')
    return {key: plan[key] for key in keys}


def approval_digest(plan):
    return digest(json.dumps(approved_payload(plan), ensure_ascii=False, sort_keys=True,
                             separators=(',', ':')).encode())


def prepare(apk, notes, version, source, chat_id, chat_title, thread_id, directory, build_tools):
    delivery.require(re.fullmatch(r'[0-9a-f]{40}', source), 'Source must be a full commit SHA')
    delivery.require(re.fullmatch(r'-[0-9]+', chat_id), 'Expected a numeric Telegram group ID')
    delivery.require(not thread_id or (thread_id.isdigit() and int(thread_id) > 0), 'Invalid topic ID')
    text = notes.read_text()
    delivery.require(text.strip() and '<!-- TEMPLATE -->' not in text, 'Write the complete announcement first')
    info = delivery.validate_apk(apk, 'v' + version, build_tools, 'internal', allow_sdk_name=True)
    caption, extra = delivery.message_plan(info['tag'], text.strip(), 'internal')
    directory.mkdir(parents=True, exist_ok=True)
    unstarted(directory)
    plan = dict(info, source_commit=source, chat_id=chat_id, chat_title=chat_title,
                thread_id=thread_id, caption=caption, extra_messages=extra,
                archive_tag=f'internal-v{version}-{info["version_code"]}', notes_sha256=digest(notes.read_bytes()))
    plan['approval_sha256'] = approval_digest(plan)
    path = directory / PLAN
    if path.exists():
        old = json.loads(path.read_text())
        delivery.require(all(old[key] == plan[key] for key in ('tag', 'sha256', 'version_code', 'source_commit')),
                         'Output directory belongs to a different APK/source; use a new directory')
        if old.get('archive_id'):
            plan['archive_id'] = old['archive_id']
    target = directory / f'new-soul-internal-{version}.apk'
    if target.exists():
        delivery.require(digest(target.read_bytes()) == info['sha256'], 'Prepared APK was changed')
    elif apk.resolve() != target.resolve():
        shutil.copy2(apk, target)
    note_target = target.with_suffix('.md')
    if notes.resolve() != note_target.resolve():
        shutil.copy2(notes, note_target)
    delivery.write_json(path, plan)
    return plan


def verify_plan(directory, build_tools):
    plan = json.loads((directory / PLAN).read_text())
    apk = directory / f'new-soul-internal-{plan["tag"][1:]}.apk'
    notes = apk.with_suffix('.md')
    info = delivery.validate_apk(apk, plan['tag'], build_tools, 'internal')
    delivery.require(all(info[key] == plan[key] for key in info), 'Prepared APK identity or digest changed')
    delivery.require(plan['archive_tag'] == f'internal-{plan["tag"]}-{plan["version_code"]}'
                     and re.fullmatch(r'[0-9a-f]{40}', plan['source_commit'])
                     and re.fullmatch(r'-[0-9]+', plan['chat_id']) and plan['chat_title']
                     and (not plan['thread_id'] or (plan['thread_id'].isdigit() and int(plan['thread_id']) > 0)),
                     'Invalid private archive or recipient identity')
    delivery.require(digest(notes.read_bytes()) == plan['notes_sha256'], 'Prepared announcement changed; prepare and review again')
    caption, extra = delivery.message_plan(plan['tag'], notes.read_text().strip(), 'internal')
    delivery.require(caption == plan['caption'] and extra == plan['extra_messages']
                     and approval_digest(plan) == plan['approval_sha256'], 'Prepared message plan changed')
    return plan, apk, notes


class PrivateDraftArchive:
    """GitHub release IDs stay usable even when draft tag lookup returns 404."""
    def __init__(self, plan, directory):
        self.plan, self.directory = plan, directory

    def api(self, path, method='GET', body=None):
        args = ['gh', 'api', '--method', method, path]
        if body is not None:
            args += ['--input', '-']
        raw = subprocess.check_output(args, input=json.dumps(body) if body is not None else None, text=True)
        return json.loads(raw) if raw.strip() else None

    def private_repository(self):
        repository = self.api(f'repos/{REPO}')
        delivery.require(repository['private'] is True and repository['full_name'] == REPO,
                         'Internal delivery requires the private source repository')

    def read(self):
        self.private_repository()
        current = self.api(f'repos/{REPO}/releases/{int(self.plan["archive_id"])}')
        delivery.require(current['draft'] is True and current['prerelease'] is False
                         and current['tag_name'] == self.plan['archive_tag']
                         and current['target_commitish'] == self.plan['source_commit'],
                         'Private draft identity/source changed')
        return current

    def ensure(self):
        self.private_repository()
        unstarted(self.directory)
        if not self.plan.get('archive_id'):
            matches, page = [], 1
            while True:
                rows = self.api(f'repos/{REPO}/releases?per_page=100&page={page}')
                matches.extend(row for row in rows if row['tag_name'] == self.plan['archive_tag'])
                if len(rows) < 100:
                    break
                page += 1
            delivery.require(len(matches) <= 1, 'Multiple matching release archives')
            if matches:
                current = matches[0]
            else:
                current = self.api(f'repos/{REPO}/releases', 'POST', self.metadata())
            self.plan['archive_id'] = current['id']
            delivery.write_json(self.directory / PLAN, self.plan)
        current = self.read()
        unstarted(self.directory, current['assets'])
        # Include the tag and source on every PATCH: a body-only draft update may
        # otherwise receive an untagged-* tag from GitHub.
        self.api(f'repos/{REPO}/releases/{self.plan["archive_id"]}', 'PATCH', self.metadata())
        apk = self.directory / f'new-soul-internal-{self.plan["tag"][1:]}.apk'
        for path in (apk, apk.with_suffix('.md')):
            self.upload(path, replace=True)
        self.inspect()

    def metadata(self):
        notes = self.directory / f'new-soul-internal-{self.plan["tag"][1:]}.md'
        return dict(tag_name=self.plan['archive_tag'], target_commitish=self.plan['source_commit'],
                    draft=True, prerelease=False, name=f'New Soul {self.plan["tag"]}｜内部群 · {self.plan["version_code"]}',
                    body=notes.read_text())

    def inspect(self):
        current = self.read()
        apk = self.directory / f'new-soul-internal-{self.plan["tag"][1:]}.apk'
        delivery.require(digest(apk.read_bytes()) == self.plan['sha256']
                         and digest(apk.with_suffix('.md').read_bytes()) == self.plan['notes_sha256'],
                         'Prepared data changed during archiving; prepare and review again')
        delivery.require(current['body'].strip() == apk.with_suffix('.md').read_text().strip(), 'Archived announcement changed')
        for path in (apk, apk.with_suffix('.md')):
            assets = [a for a in current['assets'] if a['name'] == path.name]
            delivery.require(len(assets) == 1 and assets[0]['state'] == 'uploaded'
                             and assets[0]['size'] == path.stat().st_size
                             and assets[0].get('digest') == 'sha256:' + digest(path.read_bytes()),
                             'Archived asset size/digest differs: ' + path.name)
        return current

    def upload(self, path, replace=False):
        current = self.read()
        found = [a for a in current['assets'] if a['name'] == path.name]
        guard = delivery.delivery_names('internal')[0]
        delivery.require(not (replace and path.name == guard), 'Immutable delivery guard cannot be replaced')
        if found:
            delivery.require(replace, 'Archive asset already exists: ' + path.name)
            if len(found) == 1 and found[0].get('digest') == 'sha256:' + digest(path.read_bytes()):
                return found[0]
            delivery.require(path.suffix != '.apk', 'Refusing to replace a different archived APK')
            for asset in found:
                self.api(f'repos/{REPO}/releases/assets/{asset["id"]}', 'DELETE')
        token = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN') or delivery.command('gh', 'auth', 'token').strip()
        request = urllib.request.Request(
            f'https://uploads.github.com/repos/{REPO}/releases/{int(self.plan["archive_id"])}/assets?name='
            + urllib.parse.quote(path.name), data=path.read_bytes(),
            headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/octet-stream',
                     'Accept': 'application/vnd.github+json'})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                asset = json.load(response)
        except Exception:
            raise RuntimeError('Private archive upload failed; inspect archive state before retrying') from None
        delivery.require(asset['state'] == 'uploaded' and asset['size'] == path.stat().st_size
                         and asset.get('digest') == 'sha256:' + digest(path.read_bytes()), 'Archive upload digest/size mismatch')
        return asset


def publish(directory, approved, build_tools, bot_config=None):
    plan, apk, notes = verify_plan(directory, build_tools)
    delivery.require(approved == plan['approval_sha256'], 'Approval digest differs; review the entire current plan first')
    unstarted(directory)
    for key, expected in [('GH_REPO', REPO), ('TELEGRAM_INTERNAL_CHAT_ID', plan['chat_id']),
                          ('TELEGRAM_INTERNAL_MESSAGE_THREAD_ID', plan['thread_id'])]:
        delivery.require(key not in os.environ or os.environ[key] == expected, 'Environment differs from approved plan: ' + key)
        os.environ[key] = expected
    delivery.require(plan['chat_id'] != os.environ.get('TELEGRAM_CHAT_ID'), 'Internal target equals public target')
    if not os.environ.get('TELEGRAM_BOT_TOKEN') and bot_config:
        os.environ['TELEGRAM_BOT_TOKEN'] = json.loads(bot_config.read_text())['token']
    from telegram_check import check
    check('internal', True)
    archive = PrivateDraftArchive(plan, directory)
    archive.ensure()
    # Persist the exact assertion supplied by the operator before any send.
    delivery.write_json(directory / 'approval.json', dict(approval_sha256=approved, **approved_payload(plan)))
    archive.upload(directory / 'approval.json', replace=True)
    info = dict(plan, pin_release=True, release_tag=plan['archive_tag'])
    return delivery.send(apk, None, info, plan['caption'], plan['extra_messages'], archive=archive,
                         output_dir=directory, verify_pinned=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare_parser = commands.add_parser('prepare', help='Validate/copy APK and show all planned messages; no Telegram requests')
    for name in ('apk', 'notes', 'out'):
        prepare_parser.add_argument('--' + name, type=Path, required=True)
    prepare_parser.add_argument('--version', required=True)
    prepare_parser.add_argument('--source-commit', default=None)
    prepare_parser.add_argument('--chat-id', required=True)
    prepare_parser.add_argument('--chat-title', required=True)
    prepare_parser.add_argument('--thread-id', default='')
    prepare_parser.add_argument('--archive', action='store_true', help='Create/update the private draft after local validation')
    send_parser = commands.add_parser('send', help='Archive, send once, pin and read back the approved plan')
    send_parser.add_argument('--out', type=Path, required=True)
    send_parser.add_argument('--approve', required=True, help='Approval SHA-256 printed by prepare, after human confirmation')
    send_parser.add_argument('--bot-config', type=Path, help='Optional private JSON token file; prefer environment credentials')
    for command in (prepare_parser, send_parser):
        command.add_argument('--build-tools', type=Path, default=Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk')) / 'build-tools/35.0.0')
    args = parser.parse_args()
    if args.command == 'prepare':
        source = args.source_commit or delivery.command('git', 'rev-parse', 'HEAD').strip()
        plan = prepare(args.apk, args.notes, args.version, source, args.chat_id, args.chat_title,
                       args.thread_id, args.out, args.build_tools)
        print(json.dumps(dict(approval_sha256=plan['approval_sha256'], messages=[plan['caption'], *plan['extra_messages']]),
                         ensure_ascii=False, indent=2))
        if args.archive:
            PrivateDraftArchive(plan, args.out).ensure()
            print('Private draft ready. No Telegram message sent.')
        else:
            print('Prepared locally. No GitHub mutation or Telegram request made.')
    else:
        publish(args.out, args.approve, args.build_tools, args.bot_config)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print(f'Internal release stopped: {error}', file=sys.stderr)
        sys.exit(1)
