#!/usr/bin/env python3
"""Run existing behavior tests on protected inputs, not only maintained plaintext sources."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
public=root/'build/sdk/public/protection'
subprocess.run(['python3',str(root/'tests/check-protected-build.py'),str(public)],check=True,cwd=root)
with tempfile.TemporaryDirectory() as directory:
    subprocess.run(['javac','-encoding','UTF-8','-d',directory,
        str(public/'java/dev/local/newsoul/HostBindings.java'),
        str(public/'java/dev/local/newsoul/ShieldValues.java'),
        str(root/'tests/HostBindingsTest.java')],check=True)
    subprocess.run(['java','-cp',directory,'dev.local.newsoul.HostBindingsTest'],check=True)
script=(root/'tests/run-ai-tests.sh').read_text()
script=script.replace('cd "$(dirname "$0")/.."','cd '+str(root))
script=script.replace('app/src/main/java/dev/local/newsoul/',str(public/'java/dev/local/newsoul')+'/')
script=script.replace('tests/AiModelConfigTest.java\n','tests/AiModelConfigTest.java '+str(public/'java/dev/local/newsoul/ShieldValues.java')+'\n')
script=script.replace('app/src/main/resources',str(public/'resources'))
script=script.replace('dev.local.newsoul.AiReplyPromptTest\n',
    'dev.local.newsoul.AiReplyPromptTest '+str(root/'app/src/main/resources/newsoul/cac_runtime_v2.md')+'\n')
subprocess.run(['bash','-c',script],check=True,cwd=root)
