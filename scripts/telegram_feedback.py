#!/usr/bin/env python3
"""Private feedback relay. SQLite stores routing/receipts, never message bodies or files."""
import argparse
import fcntl
import json
import logging
import os
from pathlib import Path
import sqlite3
import time
import urllib.error
import urllib.request

LOG = logging.getLogger('newsoul-feedback')
WELCOME = '请发送问题描述、日志与截图，反馈会转交维护者；回复将在这里送达。电脑离线时处理会延迟，请勿发送密码等敏感信息。'
OWNER_HELP = '直接回复收到的反馈即可转达给对应用户。支持文字、图片和文件。/status 查看服务状态。'
MEDIA = ('text', 'photo', 'document', 'video', 'audio', 'voice', 'animation', 'sticker')


class ApiError(Exception):
    def __init__(self, code=0, retry_after=0):
        self.code, self.retry_after = code, retry_after
        super().__init__('Telegram request failed (code %s)' % code)


class Api:
    def __init__(self, token):
        self.token = token

    def __call__(self, method, **fields):
        req = urllib.request.Request('https://api.telegram.org/bot' + self.token + '/' + method,
            data=json.dumps(fields).encode(), headers={'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=45) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            try:
                result = json.load(error)
                raise ApiError(result.get('error_code', error.code), result.get('parameters', {}).get('retry_after', 0)) from None
            except (ValueError, KeyError):
                raise ApiError(error.code) from None
        except (OSError, ValueError):
            # Never propagate exception URLs or credentials into logs.
            raise ApiError() from None
        if not result.get('ok'):
            raise ApiError(result.get('error_code', 0), result.get('parameters', {}).get('retry_after', 0))
        return result['result']


class Relay:
    def __init__(self, path, owner, api):
        self.db = sqlite3.connect(path)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS updates (id INTEGER PRIMARY KEY, state TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS routes (message INTEGER PRIMARY KEY, peer INTEGER NOT NULL, original INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS blocked (peer INTEGER PRIMARY KEY);
            CREATE TABLE IF NOT EXISTS notices (peer INTEGER PRIMARY KEY, stamp REAL NOT NULL);
        ''')
        self.db.commit()
        self.owner, self.api = owner, api

    def meta(self, key, default=''):
        row = self.db.execute('SELECT value FROM meta WHERE key=?', (key,)).fetchone()
        return row[0] if row else default

    def set_meta(self, key, value):
        self.db.execute('INSERT OR REPLACE INTO meta VALUES (?,?)', (key, str(value)))
        self.db.commit()

    def bind(self, bot_id):
        identity = '%s:%s' % (bot_id, self.owner)
        if self.meta('identity') not in ('', identity):
            raise ValueError('State belongs to another bot or owner')
        self.set_meta('identity', identity)

    def text(self, peer, text, reply=None):
        fields = dict(chat_id=peer, text=text)
        if reply:
            fields['reply_parameters'] = dict(message_id=reply, allow_sending_without_reply=True)
        return self.api('sendMessage', **fields)['message_id']

    def route(self, reply):
        if not reply:
            return None
        return self.db.execute('SELECT peer, original FROM routes WHERE message=?', (reply.get('message_id'),)).fetchone()

    def notify_once(self, peer, text, interval=60):
        now = time.time()
        row = self.db.execute('SELECT stamp FROM notices WHERE peer=?', (peer,)).fetchone()
        if row and now - row[0] < interval:
            return
        self.db.execute('INSERT OR REPLACE INTO notices VALUES (?,?)', (peer, now))
        self.db.commit()
        self.text(peer, text)

    def dispatch(self, msg):
        chat, sender = msg.get('chat', {}), msg.get('from', {})
        if chat.get('type') != 'private' or sender.get('is_bot') or chat.get('id') != sender.get('id'):
            return
        peer, mid = chat['id'], msg['message_id']
        text = msg.get('text', '')
        command = text.split()[0].split('@')[0] if text.startswith('/') else ''
        if peer == self.owner:
            target = self.route(msg.get('reply_to_message'))
            if command in ('/start', '/help'):
                self.text(peer, OWNER_HELP)
            elif command == '/status':
                count = self.db.execute("SELECT count(*) FROM updates WHERE state IN ('uncertain','failed') OR (state='processing' AND id < ?)", (int(self.meta('offset', '0')),)).fetchone()[0]
                self.text(peer, '反馈服务在线。待核对记录：%s。' % count)
            elif command in ('/block', '/unblock'):
                if not target:
                    self.text(peer, '请回复该用户的反馈，再使用此命令。')
                else:
                    if command == '/block':
                        self.db.execute('INSERT OR IGNORE INTO blocked VALUES (?)', (target[0],))
                    else:
                        self.db.execute('DELETE FROM blocked WHERE peer=?', (target[0],))
                    self.db.commit()
                    self.text(peer, '已屏蔽该用户。' if command == '/block' else '已解除屏蔽。')
            elif target and any(k in msg for k in MEDIA):
                self.api('copyMessage', chat_id=target[0], from_chat_id=peer, message_id=mid,
                         reply_parameters=dict(message_id=target[1], allow_sending_without_reply=True))
                self.text(peer, '已转达。', mid)
            else:
                self.text(peer, '请直接回复机器人转来的反馈，避免发错对象。', mid)
            return
        if self.db.execute('SELECT 1 FROM blocked WHERE peer=?', (peer,)).fetchone():
            return
        if command in ('/start', '/help'):
            self.notify_once(peer, WELCOME)
            return
        if command == '/privacy':
            self.notify_once(peer, '反馈内容仅通过 Telegram 转交维护者。本机只保存回复对应关系及处理状态，不下载附件；请勿发送密码等敏感信息。')
            return
        if not any(k in msg for k in MEDIA):
            self.notify_once(peer, '请发送文字、图片或日志文件。')
            return
        # No parsing of user text for routing or commands sent to the owner.
        copied = self.api('copyMessage', chat_id=self.owner, from_chat_id=peer, message_id=mid)['message_id']
        self.db.execute('INSERT INTO routes VALUES (?,?,?)', (copied, peer, mid))
        self.db.commit()
        # Fixed numeric reference prevents user-supplied names/markup spoofing identities.
        label = self.text(self.owner, '反馈用户 #%s；回复此条或上方内容即可转达。' % peer, copied)
        self.db.execute('INSERT INTO routes VALUES (?,?,?)', (label, peer, mid))
        self.db.commit()
        self.notify_once(peer, '反馈已转交，回复将在这里送达。', 300)

    def handle(self, update):
        uid = update['update_id']
        if self.db.execute('SELECT 1 FROM updates WHERE id=?', (uid,)).fetchone():
            self.set_meta('offset', max(int(self.meta('offset', '0')), uid + 1))
            return
        # Record intent before sending. A process death/unknown response never re-sends.
        self.db.execute('INSERT INTO updates VALUES (?,?)', (uid, 'processing'))
        self.db.commit()
        state = 'done'
        try:
            if 'message' in update:
                self.dispatch(update['message'])
        except ApiError as error:
            state = 'uncertain' if not error.code or error.code >= 500 else 'failed'
            LOG.warning('Update %s: %s (API code %s)', uid, state, error.code)
            try:
                self.text(self.owner, '反馈处理未完全完成（记录 %s，%s）。请核对聊天后再处理；服务不会自动重复发送。' % (uid, state))
            except ApiError:
                pass
            if error.retry_after:
                time.sleep(min(error.retry_after, 300))
        self.db.execute('UPDATE updates SET state=? WHERE id=?', (state, uid))
        self.db.commit()
        self.set_meta('offset', uid + 1)


def run(config_path, state_dir, check=False):
    config = json.loads(Path(config_path).read_text())
    owner = int(config['owner_id'])
    if owner <= 0:
        raise ValueError('Owner must be a private user ID')
    api = Api(config['token'])
    bot = api('getMe')
    if bot['username'] != config['bot_username']:
        raise ValueError('Unexpected bot identity')
    if api('getWebhookInfo').get('url'):
        raise ValueError('Existing webhook detected; refusing to take over')
    directory = Path(state_dir)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    lock = open(directory / 'relay.lock', 'w')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    relay = Relay(directory / 'relay.sqlite3', owner, api)
    relay.bind(bot['id'])
    if check:
        print('Verified @%s; private relay configuration ready.' % bot['username'])
        return
    LOG.info('Private feedback relay started for @%s', bot['username'])
    backoff = 2
    while True:
        try:
            updates = api('getUpdates', offset=int(relay.meta('offset', '0')), timeout=25,
                          allowed_updates=['message'], limit=20)
            for update in updates:
                relay.handle(update)
            backoff = 2
        except ApiError as error:
            if error.code in (401, 409):
                raise ValueError('Bot credential invalid or another update receiver is running') from None
            LOG.warning('Polling unavailable (API code %s); reconnecting', error.code)
            time.sleep(min(max(backoff, error.retry_after), 300))
            backoff = min(backoff * 2, 60)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', required=True)
    parser.add_argument('--state-dir', required=True)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    try:
        run(args.config, args.state_dir, args.check)
    except (ApiError, ValueError, OSError, KeyError, sqlite3.Error):
        LOG.error('Service stopped; inspect configuration/permissions or receiver conflicts. No credentials logged.')
        raise SystemExit(1) from None
