#!/usr/bin/env python3
"""Resolve the official Prism instance metadata for an isolated full-pack run."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import urllib.request
import zipfile

ROOT = Path(os.environ.get('PR_FULLPACK_ROOT', str(Path.home() / '.local/share/gtnh-fullpack-verification')))
OFFICIAL = ROOT / 'official-2.9.0/GT New Horizons 2.9.0'
CACHE = Path(os.environ.get('PR_FULLPACK_CACHE', str(Path.home() / '.cache/gtnh-fullpack-verification')))
LIBS = CACHE / 'libraries'
LIBS.mkdir(parents=True, exist_ok=True)


def allowed(lib):
    rules = lib.get('rules')
    if not rules:
        return True
    result = False
    for rule in rules:
        system = rule.get('os', {})
        if system.get('name', 'linux') != 'linux':
            continue
        if 'arch' in system and not re.search(system['arch'], 'x86_64'):
            continue
        result = rule['action'] == 'allow'
    return result


patches = sorted((json.loads(p.read_text()) for p in (OFFICIAL / 'patches').glob('*.json')),
                 key=lambda d: d.get('order', 0))
cached = {}
for parent in [Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches',
               Path(os.environ.get('PR_FULLPACK_SERVER_PACK', '/nonexistent-server-pack-cache'))]:
    for base, dirs, files in os.walk(parent):
        for name in files:
            if name.endswith('.jar'):
                cached.setdefault(name, []).append(Path(base) / name)


def digest(path, algorithm='sha1'):
    h = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def resolve(lib):
    artifact = lib.get('downloads', {}).get('artifact')
    native = False
    if artifact is None and 'natives' in lib:
        artifact = lib['downloads']['classifiers'][lib['natives']['linux']]
        native = True
    if artifact is None:
        assert lib.get('MMC-hint') == 'local', lib
        local = list((OFFICIAL / 'libraries').rglob('*.jar'))
        matches = [p for p in local if 'forgePatches' in p.name]
        assert len(matches) == 1, (lib, matches)
        p = matches[0]
        return {'name': lib['name'], 'path': str(p), 'sha1': digest(p), 'source': 'official ZIP'}
    url, expected = artifact['url'], artifact['sha1']
    filename = url.rsplit('/', 1)[1]
    dest = LIBS / (expected + '-' + filename)
    if not dest.exists() or digest(dest) != expected:
        for p in cached.get(filename, []):
            if p.stat().st_size == artifact.get('size', p.stat().st_size) and digest(p) == expected:
                shutil.copy2(p, dest)
                break
        else:
            alternatives = [url]
            if 'repo1.maven.org/maven2/' in url:
                alternatives.append(url.replace('repo1.maven.org/maven2/', 'repo.maven.apache.org/maven2/'))
            error = None
            for candidate in alternatives:
                try:
                    with urllib.request.urlopen(candidate, timeout=45) as response, dest.with_suffix('.part').open('wb') as stream:
                        shutil.copyfileobj(response, stream)
                    assert digest(dest.with_suffix('.part')) == expected, 'Checksum mismatch: ' + candidate
                    dest.with_suffix('.part').replace(dest)
                    break
                except Exception as exc:
                    error = exc
            else:
                raise RuntimeError(f'{filename}: {error}')
    if native:
        target = CACHE / 'natives'
        target.mkdir(exist_ok=True)
        with zipfile.ZipFile(dest) as jar:
            for name in jar.namelist():
                if not name.startswith('META-INF/') and not name.endswith('/'):
                    jar.extract(name, target)
    return {'name': lib['name'], 'path': str(dest), 'sha1': expected, 'source': url}


selected = {}
for patch in patches:
    for lib in patch.get('libraries', []):
        if allowed(lib):
            parts = lib['name'].split(':')
            selected[tuple(parts[:2] + parts[3:])] = lib
libraries = list(selected.values())
mc = next(p for p in patches if p['uid'] == 'net.minecraft')
libraries.append(mc['mainJar'])
resolved = {}
with ThreadPoolExecutor(max_workers=8) as executor:
    futures = {executor.submit(resolve, lib): lib for lib in libraries}
    for future in as_completed(futures):
        result = future.result()
        resolved[result['name']] = result
        print('ready', result['name'], flush=True)
ordered = [resolved[lib['name']] for lib in libraries]
# The patched FML/RFB classes must precede the ordinary Forge universal JAR.
ordered.sort(key=lambda r: 0 if 'lwjgl3ify' in r['name'] and 'forgePatches' in r['name'] else 1)
assets = Path(os.environ.get('PR_FULLPACK_ASSETS', str(Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/retro_futura_gradle/assets')))
index = assets / 'indexes/1.7.10.json'
assert index.exists(), index
assert digest(index) == mc['assetIndex']['sha1']
args = []
tweakers = []
for patch in patches:
    args.extend(patch.get('+jvmArgs', []))
    tweakers.extend(patch.get('+tweakers', []))
manifest = {
    'official_zip_url': 'https://downloads.gtnewhorizons.com/Multi_mc_downloads/GT_New_Horizons_2.9.0_Java_17-26.zip',
    'official_zip_sha256': digest(CACHE / 'GT_New_Horizons_2.9.0_Java_17-26.zip', 'sha256'),
    'official_instance': str(OFFICIAL), 'libraries': ordered,
    'assets': str(assets), 'natives': str(CACHE / 'natives'), 'jvm_args': args, 'tweakers': tweakers,
    'main_class': patches[-1]['mainClass'],
}
(ROOT / 'launch-metadata.json').write_text(json.dumps(manifest, indent=2) + '\n')
for label in ['baseline', 'candidate']:
    dest = ROOT / label
    if not dest.exists():
        shutil.copytree(OFFICIAL / '.minecraft', dest)
print('READY', len(ordered), 'libraries; two complete isolated game directories', flush=True)
