#!/usr/bin/env python3
"""Compare original/candidate in disposable full GTNH servers, never the live instance."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,tempfile,time,zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--pack',type=Path,required=True,help='Unmodified GTNH 2.9.0 Java 17+ server pack')
parser.add_argument('--candidate',type=Path,required=True,help='Built, reobfuscated ProjRed JAR')
parser.add_argument('--java-home',type=Path,required=True,help='Java 25 JDK')
args=parser.parse_args()
root=Path(__file__).resolve().parent
pack=args.pack.resolve();candidate=args.candidate.resolve()
assert 'eula=true' in (pack/'eula.txt').read_text().replace(' ',''),'Accept the EULA in the supplied pack first'
original=pack/'mods/ProjRed-4.12.50-GTNH.jar'
assert original.is_file() and candidate.is_file()
run=Path(tempfile.mkdtemp(prefix='projectred-routing-native-'))
print('Disposable working directory:',run,flush=True)
for name in ['config','serverutilities','libraries','mods']:
    if (pack/name).exists():shutil.copytree(pack/name,run/name)
for name in ['lwjgl3ify-forgePatches.jar','minecraft_server.1.7.10.jar','forge-1.7.10-10.13.4.1614-1.7.10-universal.jar','java9args.txt']:
    shutil.copy2(pack/name,run/name)
for p in (run/'mods').glob('*seed*roller*.jar'):p.unlink()
(run/'eula.txt').write_text('eula=true\n')
for name in ['ops.json','whitelist.json','usercache.json','banned-ips.json','banned-players.json']:(run/name).write_text('[]\n')
(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nlevel-name=RoutingTestWorld\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\nmax-tick-time=-1\nonline-mode=false\nwhite-list=false\nview-distance=2\ngenerate-structures=false\nspawn-protection=0\n')
classes=run/'fixture-classes';classes.mkdir()
java=args.java_home/('bin/java.exe' if os.name=='nt' else 'bin/java')
javac=args.java_home/('bin/javac.exe' if os.name=='nt' else 'bin/javac')
subprocess.run([str(javac),'--release','8','-cp',str(run/'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar'),'-d',str(classes),str(root/'CompatHarness.java'),str(root/'NativeRouting.java')],check=True)
with zipfile.ZipFile(run/'mods/routing-test-harness.jar','w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
with zipfile.ZipFile(run/'native-routing.jar','w',zipfile.ZIP_DEFLATED) as z:
    p=classes/'runtimechecks/NativeRouting.class';z.write(p,'runtimechecks/NativeRouting.class')
(run/'checks').mkdir()
def prop(value):
    text=str(value).replace('\\','\\\\').replace(':','\\:')
    return ''.join(c if ord(c)<128 else '\\u%04x'%ord(c) for c in text)
results={}
for label,jar in [('original',original),('patched',candidate)]:
    shutil.copy2(jar,run/'mods/ProjRed-4.12.50-GTNH.jar')
    for name in ['ready','stop']:(run/'checks'/name).unlink(missing_ok=True)
    output=run/(label+'.json')
    (run/'checks/request.properties').write_text('jar='+prop(run/'native-routing.jar')+'\nclass=runtimechecks.NativeRouting\noutput='+prop(output)+'\n')
    print('Starting',label,'on loopback with an ephemeral port',flush=True)
    with (run/(label+'.log')).open('w') as log:
        process=subprocess.Popen([str(java),'-Xms2G','-Xmx8G','-Duser.language=en','-Djava.awt.headless=true','@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui'],cwd=run,stdout=log,stderr=subprocess.STDOUT)
        try:
            deadline=time.monotonic()+360
            while not output.exists():
                if process.poll() is not None:raise RuntimeError(f'{label} exited; inspect {run/label}.log')
                if time.monotonic()>deadline:raise TimeoutError(label+' timed out')
                time.sleep(.5)
            result=json.loads(output.read_text());assert result.get('completed'),result
            assert result['adjacent_interface_hop_to_target']==5,result
            assert result['interface_branch_hop']==3,result
            assert result['two_interfaces_deliver_both_directions']==[True,True],result
            assert result['original_peer_version_accepted'] and all(result['original_peer_version_accepted'].values()),result
            assert not result['full_inventory_accepted'],result
            assert result['partial_remaining']==3,result
            if label=='original':
                assert result['bounce_destination']==result['target_ip'] and result['bounce_output']==5,result
                assert result['partial_remaining_reservation']==5,result
            else:
                assert result['bounce_destination']==result['fallback_ip'] and result['bounce_output']==4,result
                assert result['old_target_reserved_after_bounce']==0,result
                assert result['partial_remaining_reservation']==3,result
            results[label]=result
        finally:
            (run/'checks/stop').touch()
            try:process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait()
(run/'verification.json').write_text(json.dumps({'passed':True,'results':results},indent=2)+'\n')
print('PASS; results and server logs:',run,flush=True)
