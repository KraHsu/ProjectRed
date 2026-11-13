#!/usr/bin/env python3
"""Launch only the two disposable full-pack verification instances."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import uuid

ROOT = Path(os.environ.get('PR_FULLPACK_ROOT', str(Path.home() / '.local/share/gtnh-fullpack-verification')))
REPO = Path(os.environ.get('PR_FULLPACK_REPO', str(Path.cwd())))
OUTPUT = Path(os.environ.get('PR_FULLPACK_OUTPUT', str(ROOT / 'results')))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('variant', choices=['baseline', 'candidate'])
args = parser.parse_args()
game = ROOT / args.variant
assert game.parent == ROOT and (game / 'mods/angelica-2.2.43.jar').exists()
out = OUTPUT / args.variant
for part in ['scene', 'inventory']:
    (out / part).mkdir(parents=True, exist_ok=True)
meta = json.loads((ROOT / 'launch-metadata.json').read_text())
helper = REPO / 'build/libs/projectred-fullpack-verification.jar'
assert helper.exists()
for old in (game / 'mods').glob('projectred-fullpack-verification*.jar'):
    old.unlink()
shutil.copy2(helper, game / 'mods' / helper.name)
original = Path(meta['official_instance']) / '.minecraft/mods/ProjRed-4.12.50-GTNH.jar'
mod = original if args.variant == 'baseline' else REPO / 'build/libs/ProjRed-4.12.50-GTNH.jar'
shutil.copy2(mod, game / 'mods/ProjRed-4.12.50-GTNH.jar')
world = game / 'saves/PerformanceContributionTest'
if world.exists():
    shutil.rmtree(world)
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
command = [java, '-Xms1G', '-Xmx12G', '-XX:+UseG1GC', '-XX:ActiveProcessorCount=8',
           '-Djava.library.path=' + meta['natives'], '-Dprojectred.selfTest=true',
           '-Dprojectred.testVariant=' + ('followups' if args.variant == 'candidate' else 'cache'),
           '-Dprojectred.testOutput=' + str(out / 'scene'),
           '-Dprojectred.itemTestOutput=' + str(out / 'inventory'),
           '-Dfullpack.output=' + str(out), '-Dfullpack.variant=' + args.variant,
           *meta['jvm_args'], '-cp', os.pathsep.join(row['path'] for row in meta['libraries']),
           meta['main_class'], '--username', 'PRFullpackTest', '--version', '1.7.10',
           '--gameDir', str(game), '--assetsDir', meta['assets'], '--assetIndex', '1.7.10',
           '--uuid', uuid.uuid3(uuid.NAMESPACE_DNS, 'OfflinePlayer:PRFullpackTest').hex,
           '--accessToken', '0', '--userProperties', '{}', '--userType', 'legacy',
           '--width', '1280', '--height', '720']
for tweak in meta['tweakers']:
    command += ['--tweakClass', tweak]
environment = dict(os.environ)
environment.setdefault('DISPLAY', ':0')
if not environment.get('XAUTHORITY'):
    auth = list(Path('/run/user').joinpath(str(os.getuid())).glob('.mutter-Xwaylandauth.*'))
    assert len(auth) == 1, 'Set XAUTHORITY for the active graphical session'
    environment['XAUTHORITY'] = str(auth[0])
manifest = {'variant': args.variant, 'java': java, 'game': str(game),
            'official_zip_sha256': meta['official_zip_sha256'], 'command': command,
            'installed_projectred_sha256': hashlib.sha256(mod.read_bytes()).hexdigest(),
            'verification_helper_sha256': hashlib.sha256(helper.read_bytes()).hexdigest(),
            'stock_mod_jars': {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in original.parent.rglob('*.jar')},
            'config_file_count': len(list((game / 'config').rglob('*')))}
(out / 'launch.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Launching complete GTNH 2.9.0', args.variant, flush=True)
with (out / 'launch.log').open('w') as log:
    process = subprocess.Popen(command, cwd=game, env=environment, stdout=log, stderr=subprocess.STDOUT)
    (out / 'process.pid').write_text(str(process.pid) + '\n')
    code = process.wait()
print('Game exit', code, flush=True)
if code:
    raise SystemExit(code)
status = json.loads((out / 'suite.json').read_text())
assert status['status'] == 'passed', status.get('status')
print('FULLPACK SUITE PASSED', args.variant, status['loaded_mod_count'], 'mods', flush=True)
