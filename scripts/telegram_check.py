#!/usr/bin/env python3
"""Read-only deployment check. Never sends Telegram messages or prints credentials."""
import argparse
import json
import os
import sys
import urllib.parse
import urllib.request


def check(edition='public', require_pin=False):
    token = os.environ.get('TELEGRAM_BOT_TOKEN', '').strip()
    key = 'TELEGRAM_INTERNAL_CHAT_ID' if edition == 'internal' else 'TELEGRAM_CHAT_ID'
    chat_id = os.environ.get(key, '').strip()
    if not token or not chat_id:
        raise ValueError(f'Configure TELEGRAM_BOT_TOKEN and {key} in Actions Secrets')
    if edition == 'internal' and chat_id == os.environ.get('TELEGRAM_CHAT_ID', '').strip():
        raise ValueError('Internal target equals public target')

    def api(method, **fields):
        request = urllib.request.Request(
            f'https://api.telegram.org/bot{token}/{method}',
            data=urllib.parse.urlencode(fields).encode())
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)
            if not result.get('ok'):
                raise ValueError('API rejected request')
            return result['result']
        except Exception:
            raise RuntimeError(f'{method} failed; verify bot credentials and group membership') from None

    bot = api('getMe')
    chat = api('getChat', chat_id=chat_id)
    member = api('getChatMember', chat_id=chat_id, user_id=bot['id'])
    status = member['status']
    if status in ('left', 'kicked'):
        raise ValueError('Bot is not a group member')
    if chat['type'] == 'channel':
        if status != 'administrator' or not member.get('can_post_messages'):
            raise ValueError('Bot needs channel publishing permission')
    elif chat['type'] in ('group', 'supergroup'):
        permissions = member if status == 'restricted' else chat.get('permissions', {})
        if status not in ('creator', 'administrator'):
            if permissions.get('can_send_messages') is False or permissions.get('can_send_documents') is False:
                raise ValueError('Bot cannot send messages or documents to this group')
    else:
        raise ValueError('Expected a release group or channel')
    if require_pin:
        if status not in ('creator', 'administrator'):
            raise ValueError('Bot needs administrator permission to pin release announcements')
        if chat['type'] == 'supergroup' and status != 'creator' and not member.get('can_pin_messages'):
            raise ValueError('Bot needs can_pin_messages permission')
        if chat['type'] == 'channel' and not member.get('can_edit_messages'):
            raise ValueError('Bot needs can_edit_messages permission to pin in a channel')
    print(f'Bot: @{bot["username"]}; target: {chat["title"]}; membership: {status}')
    print('Read-only Telegram configuration check passed. No messages were sent.')


if __name__ == '__main__':
    try:
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument('--edition', choices=('public', 'internal'), default='public')
        parser.add_argument('--require-pin', action='store_true')
        args = parser.parse_args()
        check(args.edition, args.require_pin)
    except (ValueError, RuntimeError) as error:
        print(f'Configuration check failed: {error}', file=sys.stderr)
        sys.exit(1)
