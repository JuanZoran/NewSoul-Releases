#!/usr/bin/env python3
"""Test staged runtime decoding and require selected plaintext to be absent in final DEX."""
import argparse
import base64
import json
import hashlib
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def dex_strings(data):
    size, offset=struct.unpack_from('<II',data,56)
    result=set()
    for i in range(size):
        p=struct.unpack_from('<I',data,offset+4*i)[0]
        while data[p]&128: p+=1
        p+=1
        end=data.index(0,p)
        result.add(data[p:end].decode('utf-8',errors='replace'))
    return result

def verify(stage, apk=None, mapping=None):
    report=json.loads((stage/'report.json').read_text())
    internal=True
    for rel,digest in report['source_hashes'].items():
        assert hashlib.sha256((stage/'java'/rel).read_bytes()).hexdigest()==digest,rel
    with tempfile.TemporaryDirectory() as tmp:
        temp=Path(tmp)
        expected=temp/'expected'
        expected.write_text('\n'.join(base64.b64encode(s.encode()).decode() for s in report['protected_values'])+'\n')
        probe=temp/'ProtectionProbe.java'
        probe.write_text('''package dev.local.newsoul;
import java.nio.file.*;
import java.util.*;
public final class ProtectionProbe {
 public static void main(String[] args) throws Exception {
  List<String> expected=Files.readAllLines(Path.of(args[0]));
  for(int i=0;i<expected.size();i++) {
   String s=new String(Base64.getDecoder().decode(expected.get(i)),java.nio.charset.StandardCharsets.UTF_8);
   if(!s.equals(ShieldValues.get(i)) || ShieldValues.get(i)!=s.intern()) throw new AssertionError("String mismatch");
  }
  for(byte[] invalid:new byte[][]{new byte[0],new byte[31],new byte[32]}) {
   try { ShieldValues.open(invalid);throw new AssertionError("Invalid header accepted"); }
   catch(java.security.GeneralSecurityException good){}
  }
  if(args.length>1) {
   byte[] encrypted=Files.readAllBytes(Path.of(args[1]));
   byte[] original=Files.readAllBytes(Path.of(args[2]));
   if(!Arrays.equals(original,ShieldValues.open(encrypted))) throw new AssertionError("Prompt mismatch");
   for(int position:new int[]{4,16,encrypted.length-1}) {
    byte[] bad=encrypted.clone();bad[position]^=1;
    try { ShieldValues.open(bad);throw new AssertionError("Tampered resource accepted"); }
    catch(java.security.GeneralSecurityException good){}
   }
   Class<?> cls=Class.forName("dev.local.newsoul.AiReplyPrompt");
   java.lang.reflect.Method method=cls.getDeclaredMethod("systemPrompt");method.setAccessible(true);
   String loaded=(String)method.invoke(null);
   if(!loaded.equals(new String(original,java.nio.charset.StandardCharsets.UTF_8))) throw new AssertionError("Loader mismatch");
  }
 }
}
''')
        sources=[stage/'java/dev/local/newsoul/ShieldValues.java',probe]
        if internal: sources.append(stage/'java/dev/local/newsoul/AiReplyPrompt.java')
        subprocess.run(['javac','-encoding','UTF-8','-d',str(temp),*map(str,sources)],check=True)
        args=[str(expected)]
        if internal: args += [str(stage/'resources/newsoul/rules.bin'),str(ROOT/'app/src/main/resources/newsoul/cac_runtime_v2.md')]
        subprocess.run(['java','-cp',str(temp)+':'+str(stage/'resources'),'dev.local.newsoul.ProtectionProbe',*args],check=True)
    if apk:
        with zipfile.ZipFile(apk) as archive:
            names=archive.namelist()
            strings=set()
            for name in names:
                if re.fullmatch(r'classes\d*\.dex',name):strings.update(dex_strings(archive.read(name)))
            exposed=set(report['protected_values']) & strings
            assert not exposed, f'Unprotected final DEX strings: {sorted(exposed)}'
            assert 'newsoul/cac_runtime_v2.md' not in names
            assert ('newsoul/rules.bin' in names)==internal
            if internal:
                assert archive.read('newsoul/rules.bin')==(stage/'resources/newsoul/rules.bin').read_bytes()
            assert not any(n.endswith(('mapping.txt','report.json','.java')) for n in names)
            for marker in ['HostBindings.java','AiReplyRules.java']:
                assert marker not in strings, f'Source name retained: {marker}'
        if mapping:
            # Private evidence stays outside APK/ZIP and survives the next build's mapping overwrite.
            digest=hashlib.sha256(apk.read_bytes()).hexdigest()
            destination=ROOT/'build/protection-evidence'/report['edition']/digest
            destination.mkdir(parents=True,exist_ok=True)
            for source,name in [(mapping,'mapping.txt'),(stage/'report.json','report.json'),
                                (stage/'java/dev/local/newsoul/ShieldValues.java','ShieldValues.java')]:
                data=source.read_bytes()
                target=destination/name
                if target.exists() and target.read_bytes()!=data:
                    raise AssertionError('Conflicting evidence for APK digest: '+name)
                target.write_bytes(data)
            (destination/'artifact.json').write_text(json.dumps({'apk_sha256':digest,
                'apk_name':apk.name,'mapping_sha256':hashlib.sha256(mapping.read_bytes()).hexdigest()},indent=2)+'\n')
    print(f'{report["edition"]}: {report["strings"]} strings round-trip; malformed input rejected; prompt={internal}; final APK checked={bool(apk)}')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('stage',type=Path);parser.add_argument('apk',type=Path,nargs='?')
    parser.add_argument('--mapping',type=Path)
    args=parser.parse_args();verify(args.stage,args.apk,args.mapping)
