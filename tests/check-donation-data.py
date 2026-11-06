#!/usr/bin/env python3
"""Execute generated Java, compare exact originals, and check payloads in final DEX."""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/dev/local/newsoul/DonationImageData.java'
subprocess.run(['python3', str(root / 'scripts/generate-donation-data.py'), '--check'], check=True)
with tempfile.TemporaryDirectory() as directory:
    work = Path(directory)
    probe = work / 'DonationDataProbe.java'
    probe.write_text('''package dev.local.newsoul;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;
public class DonationDataProbe {
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            byte[] bytes = DonationImageData.read(i);
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null)
                throw new AssertionError("Not an image");
            Files.write(Path.of(args[0], i + ".bin"), bytes);
        }
        for (int i : new int[]{-1, 2}) {
            try { DonationImageData.read(i); throw new AssertionError("Invalid index accepted"); }
            catch (IllegalArgumentException expected) {}
        }
    }
}
''')
    subprocess.run(['javac', '-d', directory, str(source), str(probe)], check=True)
    subprocess.run(['java', '-cp', directory, 'dev.local.newsoul.DonationDataProbe', directory], check=True)
    for index, name in enumerate(['donation_wechat.png', 'donation_alipay.jpg']):
        assert (work / f'{index}.bin').read_bytes() == (root / 'app/donation-source' / name).read_bytes()
    print('Both images decode and match originals byte-for-byte; invalid indices rejected')

chunks = re.findall(r'data.append\("([A-Za-z0-9+/=]+)"\)', source.read_text())
for path in sys.argv[1:]:
    with zipfile.ZipFile(path) as apk:
        dex = b''.join(apk.read(n) for n in apk.namelist() if re.fullmatch(r'classes\d*\.dex', n))
        assert dex and all(chunk.encode() in dex for chunk in chunks), 'Missing image data in ' + path
        assert not any('donation_' in n for n in apk.namelist()), 'Duplicate resource images in ' + path
    print('Both image payloads retained in final DEX: ' + path)
