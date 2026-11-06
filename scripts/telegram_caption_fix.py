"""Repair bot mention spacing in an existing, receipt-backed release caption."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.request
from telegram_release import message_plan, installation_entities


def main():
    tag = os.environ['RELEASE_TAG']
    assert re.fullmatch(r'v\d+\.\d+\.\d+', tag)
    release = json.loads(subprocess.check_output(['gh', 'release', 'view', tag, '--json', 'body,isDraft,isPrerelease']))
    assert not release['isDraft'] and not release['isPrerelease']
    notes = Path(f'release-notes/{tag[1:]}.md').read_text().strip()
    original = release['body'].strip()
    assert original == notes or original.replace('私聊@JuanZoranNewSoulReleaseBot配合适配', '私聊 @JuanZoranNewSoulReleaseBot 配合适配') == notes
    caption, extra = message_plan(tag, notes)
    assert not extra, 'Only single-caption releases are supported'
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run(['gh', 'release', 'download', tag, '--pattern', 'telegram-public-delivery.json', '--dir', directory], check=True)
        receipt = json.loads((Path(directory) / 'telegram-public-delivery.json').read_text())
    assert receipt['tag'] == tag and receipt['status'] == 'complete'
    assert receipt['expected_messages'] == len(receipt['message_ids']) == 1
    message_id = receipt['message_ids'][0]
    payload = {'chat_id': os.environ['TELEGRAM_CHAT_ID'], 'message_id': message_id, 'caption': caption, 'caption_entities': installation_entities(caption)}
    token = os.environ['TELEGRAM_BOT_TOKEN']
    request = urllib.request.Request('https://api.telegram.org/bot' + token + '/editMessageCaption', data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            result = json.load(response)
    except Exception:
        raise SystemExit('Caption edit failed or outcome uncertain; no automatic retry') from None
    assert result.get('ok') is True
    message = result['result']
    assert message['message_id'] == message_id and message['caption'] == caption
    data = caption.encode('utf-16-le')
    mentions = [data[e['offset'] * 2:(e['offset'] + e['length']) * 2].decode('utf-16-le') for e in message.get('caption_entities', []) if e['type'] == 'mention']
    assert mentions.count('@JuanZoranNewSoulReleaseBot') == 2
    print(f'Message {message_id} updated; both bot names verified as mention entities')


if __name__ == '__main__':
    main()
