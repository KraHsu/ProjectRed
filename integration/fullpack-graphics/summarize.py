#!/usr/bin/env python3
"""Verify the completed, identical-fixture baseline and candidate full-pack runs."""
from pathlib import Path
import argparse
import hashlib
import json

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('results', type=Path)
parser.add_argument('--instances', type=Path, required=True)
args = parser.parse_args()
root = args.results


def read(path):
    return json.loads(path.read_text())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


variants = {}
for label in ['baseline', 'candidate']:
    folder = root / label
    suite = read(folder / 'suite.json')
    scene = read(folder / 'scene/client-results.json')
    inventory = read(folder / 'inventory/results.json')
    launch = read(folder / 'launch.json')
    assert suite['status'] == scene['status'] == 'passed'
    assert inventory['status'] == 'completed'
    assert suite['loaded_mods']['angelica'] == '2.2.43'
    assert suite['loaded_mod_count'] == 324
    assert scene['world_basic_and_junction_samples'] == 144
    assert scene['redstone_overlay_samples'] == 16
    assert scene['native_item_flow_client_ticks'] > 0
    assert scene['save_reopen_redstone_and_transport'] and scene['resource_reload_completed']
    assert scene['empty_render_state_checks'] > 0
    assert suite['drawer_transport']['native_total'] == suite['drawer_transport']['cold_reload_total'] == 8224
    assert suite['drawer_transport']['native_target'] > 33
    assert all(v == 0 for k, v in inventory.items() if k.endswith('_gl_error'))
    mods = args.instances / label / 'mods'
    installed = list(mods.rglob('*.jar'))
    stock = launch['stock_mod_jars']
    assert {p.name for p in installed} - set(stock) == {'projectred-fullpack-verification.jar', 'EJML-core-0.26.jar'}
    runtime_dependencies = {}
    for jar in installed:
        if jar.name == 'projectred-fullpack-verification.jar':
            assert sha(jar) == launch['verification_helper_sha256']
        elif jar.name == 'ProjRed-4.12.50-GTNH.jar':
            assert sha(jar) == launch['installed_projectred_sha256']
        elif jar.name == 'EJML-core-0.26.jar':
            runtime_dependencies[jar.name] = sha(jar)
        else:
            assert sha(jar) == stock[jar.name], jar
    variants[label] = {'status': 'passed', 'loaded_mod_count': suite['loaded_mod_count'],
                       'projectred_sha256': launch['installed_projectred_sha256'],
                       'helper_sha256': launch['verification_helper_sha256'],
                       'scene': scene, 'drawer_transport': suite['drawer_transport'],
                       'gl_renderer': suite['gl_renderer'], 'gl_version': suite['gl_version'],
                       'java': suite['java'], 'stock_mod_jars_verified': len(stock),
                       'angelica_version': suite['loaded_mods']['angelica'],
                       'auto_downloaded_runtime_dependencies': runtime_dependencies}
assert variants['baseline']['helper_sha256'] == variants['candidate']['helper_sha256']
assert variants['baseline']['auto_downloaded_runtime_dependencies'] == variants['candidate']['auto_downloaded_runtime_dependencies']
assert variants['candidate']['projectred_sha256'] == 'e86e94e869cb0e1e7c17d46eb19f7e704b0ae3906565927b02cb4d6684078867'
images = read(root / 'inventory-verification.json')
assert images['passed'] and len(images['candidate']['comparisons']) == 44
assert all(c['changed_pixels'] == 0 for c in images['candidate']['comparisons'])
summary = {'passed': True, 'source_commit': '9a619a135931b0d800e0bf01d27bd2abb3584353',
           'pack': 'Official GTNH 2.9.0 MultiMC/Prism Java 17-26',
           'official_zip_sha256': read(root / 'baseline/launch.json')['official_zip_sha256'],
           'variants': variants, 'image_comparisons': 44,
           'candidate_sampler_state_checks': images['candidate']['state_checks'],
           'candidate_gallery_state_checks': images['candidate']['gallery_state_checks'],
           'wool_scope': 'Wool-dyed pressure tubes excluded from full-pack pass criteria; existing development tests cover the model. Both full-pack crafting lists contain zero recipes for pipe metadata 7 and 8; this alone does not prove the code is disabled or exclude every possible machine recipe.',
           'limits': ['Linux/Radeon/Mesa with Angelica; Windows GPU drivers not exercised',
                      'Targeted graphical and integrated-server functional checks, not an exhaustive all-mod or performance benchmark',
                      'Stock-pack logs contain unrelated recipe, texture, Discord and shutdown warnings; raw logs are retained',
                      'No production server files, worlds or processes were changed']}
(root / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print('PASS: complete GTNH 2.9.0 baseline and candidate; 324 loaded mods, stock JAR hashes verified, 44 exact image comparisons')
