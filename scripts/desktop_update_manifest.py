#!/usr/bin/env python3
"""Publish update metadata only after all desktop archives have been built and checked."""
import argparse
import hashlib
import io
import json
import re
from pathlib import Path
import zipfile

MAX_JAR = 128 * 1024 * 1024

def build_manifest(directory: Path, version: str) -> dict:
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError('Invalid release version')
    result = {'schema_version': 1, 'version': version, 'minimum_java': 23, 'platforms': {}}
    for platform in ('windows', 'linux', 'macos'):
        name = f'petri-swing-ui-{version}-{platform}.zip'
        archive = directory / name
        with zipfile.ZipFile(archive) as zip_file:
            jars = [e for e in zip_file.infolist() if not e.is_dir() and e.filename.endswith('.jar')]
            if len(jars) != 1 or jars[0].file_size > MAX_JAR:
                raise ValueError('Expected one bounded application JAR')
            entry = jars[0]
            if '\\' in entry.filename or entry.filename.startswith('/') or '..' in entry.filename.split('/') or Path(entry.filename).name != f'petri-swing-ui-{platform}.jar':
                raise ValueError('Invalid JAR path')
            data = zip_file.read(entry)
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            properties = dict(line.split('=', 1) for line in jar.read('ua/stetsenkoinna/updates/build.properties').decode().splitlines() if '=' in line and not line.startswith('#'))
            if properties.get('version') != version or properties.get('arch') not in ('amd64', 'x86_64', 'aarch64', 'arm64'):
                raise ValueError('Build metadata differs from release')
            if 'ua/stetsenkoinna/PetriObjPaintApp.class' not in jar.namelist():
                raise ValueError('Application entry point missing')
        result['platforms'][platform] = {
            'url': f'https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v{version}/{name}',
            'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
            'jar_sha256': hashlib.sha256(data).hexdigest(),
            'size': archive.stat().st_size,
            'arch': properties['arch'],
        }
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--directory', type=Path, default=Path('.'))
    parser.add_argument('--output', type=Path, default=Path('desktop-update.json'))
    args = parser.parse_args()
    args.output.write_text(json.dumps(build_manifest(args.directory, args.version), indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
