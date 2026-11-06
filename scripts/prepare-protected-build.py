#!/usr/bin/env python3
"""Stage sources/resources for both build paths; never edit maintained Java sources."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
TOKEN = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)
PROMPT = 'newsoul/cac_runtime_v2.md'
SEALED = 'newsoul/rules.bin'

def stage(edition, output):
    output = output.resolve()
    # Refuse destructive output locations outside build staging.
    if ROOT / 'build' not in output.parents:
        raise ValueError('Output must be below repository build/')
    output.mkdir(parents=True, exist_ok=True)
    values, ids = [], {}
    occurrences = 0
    def replace(match):
        nonlocal occurrences
        token = match.group()
        if not re.fullmatch(r'"cn\.soulapp\.[A-Za-z0-9_.$]+"', token):
            return token
        value = token[1:-1]
        if value not in ids:
            ids[value] = len(values)
            values.append(value)
        occurrences += 1
        return f'ShieldValues.get({ids[value]})'
    sources = {}
    resources = {}
    for scope in ['main']:
        for path in sorted((ROOT / f'app/src/{scope}/java').rglob('*.java')):
            rel = path.relative_to(ROOT / f'app/src/{scope}/java')
            if rel in sources: raise ValueError(f'Duplicate source {rel}')
            sources[rel] = TOKEN.sub(replace, path.read_text())
        for path in sorted((ROOT / f'app/src/{scope}/resources').rglob('*')):
            if path.is_file(): resources[str(path.relative_to(ROOT / f'app/src/{scope}/resources'))] = path.read_bytes()
    if not values: raise ValueError('No protected strings; refusing unprotected build')
    records = [s.encode() for s in values]
    prompt = resources.pop(PROMPT)
    if not prompt.strip() or len(prompt) + 32 > 128 * 1024:
        raise ValueError('Prompt exceeds runtime loader bounds or is empty')
    records.append(prompt)
    path = Path('dev/local/newsoul/AiReplyPrompt.java')
    source = sources[path]
    old = 'new String(out.toByteArray(), StandardCharsets.UTF_8)'
    if source.count(old) != 1 or source.count(PROMPT) != 1:
        raise ValueError('Prompt loader contract changed')
    sources[path] = source.replace(PROMPT, SEALED).replace(old, 'new String(ShieldValues.open(out.toByteArray()), StandardCharsets.UTF_8)')
    with tempfile.TemporaryDirectory(dir=output) as temp:
        temp = Path(temp)
        (temp/'input').write_text('\n'.join(base64.b64encode(b).decode() for b in records)+'\n')
        subprocess.run(['java', str(ROOT/'scripts/protection/Seal.java'), str(temp/'input'), str(temp/'sealed'), str(temp/'key')], check=True)
        encrypted = (temp/'sealed').read_text().splitlines()
        key = (temp/'key').read_bytes()
    a = os.urandom(len(key)); b = bytes(x ^ y for x,y in zip(a,key))
    signed = lambda data: ','.join(str(x if x < 128 else x-256) for x in data)
    template = (ROOT/'scripts/protection/ShieldValues.java.in').read_text()
    sources[Path('dev/local/newsoul/ShieldValues.java')] = template.replace('@A@',signed(a)).replace('@B@',signed(b)).replace('@DATA@', ',\n'.join(json.dumps(s) for s in encrypted[:len(values)]))
    resources[SEALED] = base64.b64decode(encrypted[-1])
    for folder in ['java', 'resources']:
        if (output/folder).exists(): shutil.rmtree(output/folder)
    for rel, source in sources.items():
        dest=output/'java'/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(source)
    for rel, data in resources.items():
        dest=output/'resources'/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    report={'edition':edition,'strings':len(values),'occurrences':occurrences,
            'protected_values':values,'source_hashes':{str(p):hashlib.sha256(s.encode()).hexdigest() for p,s in sources.items()}}
    (output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(f'{edition}: staged {len(values)} protected strings / {occurrences} uses; encrypted prompt=True')

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('edition',choices=['public']);parser.add_argument('output',type=Path)
    args=parser.parse_args();stage(args.edition,args.output)
