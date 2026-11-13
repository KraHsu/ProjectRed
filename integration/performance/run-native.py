#!/usr/bin/env python3
"""Test isolated contribution builds in disposable full GTNH packs."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,tempfile,time,zipfile

p=argparse.ArgumentParser()
p.add_argument('--pack',type=Path,required=True)
p.add_argument('--candidate',type=Path,required=True)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--variant',choices=['cache','followups'],required=True)
a=p.parse_args();source=a.pack.resolve();candidate=a.candidate.resolve();tests=Path(__file__).resolve().parent
assert 'eula=true' in (source/'eula.txt').read_text().replace(' ','')
run=Path(tempfile.mkdtemp(prefix='projectred-contribution-native-'))
print('Disposable test pack:',run,flush=True)
for name in ['config','serverutilities','libraries','mods']:
    if (source/name).exists():shutil.copytree(source/name,run/name)
for name in ['lwjgl3ify-forgePatches.jar','minecraft_server.1.7.10.jar','forge-1.7.10-10.13.4.1614-1.7.10-universal.jar','java9args.txt']:
    shutil.copy2(source/name,run/name)
for jar in (run/'mods').glob('*.jar'):
    if any(s in jar.name.lower() for s in ['projred','projectred','routing-test-harness','runtimecompatchecks']) or ('seed' in jar.name.lower() and 'roller' in jar.name.lower()):jar.unlink()
shutil.copy2(candidate,run/'mods/ProjRed-4.12.50-GTNH.jar')
(run/'eula.txt').write_text('eula=true\n')
(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nlevel-name=RoutingTestWorld\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\nmax-tick-time=-1\nonline-mode=false\nwhite-list=false\nview-distance=2\ngenerate-structures=false\nspawn-protection=0\n')
for name in ['ops.json','whitelist.json','usercache.json','banned-ips.json','banned-players.json']:(run/name).write_text('[]\n')
classes=run/'classes';classes.mkdir();checks=run/'checks';checks.mkdir()
subprocess.run([str(a.java_home/'bin/javac'),'--release','8','-cp',str(run/'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar'),'-d',str(classes),str(tests/'CompatHarness.java'),*map(str,tests.glob('Native*.java'))],check=True)
with zipfile.ZipFile(run/'mods/routing-test-harness.jar','w',zipfile.ZIP_DEFLATED) as jar:
    jar.write(classes/'runtimechecks/CompatHarness.class','runtimechecks/CompatHarness.class')
with zipfile.ZipFile(run/'native-checks.jar','w',zipfile.ZIP_DEFLATED) as jar:
    for f in classes.rglob('Native*.class'):jar.write(f,f.relative_to(classes).as_posix())
results={}
def wait_for(path,process):
    deadline=time.monotonic()+420
    while not path.exists():
        if process.poll() is not None:raise RuntimeError('Test JVM exited; inspect '+str(run))
        if time.monotonic()>deadline:raise TimeoutError(str(path))
        time.sleep(.25)
def request(name,process):
    output=run/(name+'.json');request=checks/'request.properties'
    pending=checks/'request.tmp'
    pending.write_text('jar='+str(run/'native-checks.jar')+'\nclass=runtimechecks.'+name+'\noutput='+str(output)+'\n')
    pending.rename(request);wait_for(output,process);result=json.loads(output.read_text())
    assert result.get('passed'),result
    results[name]=result;print(name,'PASS',flush=True)
for cold,names in [(False,['NativeContribution']),(True,['NativeContributionCold'])]:
    (checks/'ready').unlink(missing_ok=True);(checks/'stop').unlink(missing_ok=True)
    with (run/('cold.log' if cold else 'warm.log')).open('w') as log:
        process=subprocess.Popen([str(a.java_home/'bin/java'),'-Xms2G','-Xmx8G','-Duser.language=en','-Djava.awt.headless=true','-Dpr.followups='+str(a.variant=='followups').lower(),'@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui'],cwd=run,stdout=log,stderr=subprocess.STDOUT)
        try:
            wait_for(checks/'ready',process)
            for name in names:request(name,process)
        finally:
            (checks/'stop').touch()
            try:process.wait(timeout=60)
            except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=30)
summary={'passed':True,'candidate_sha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'results':results,
         'limits':['Full GTNH dedicated-server integration; not a full GTNH/Angelica client FPS test','Native Minecraft edit checks are retained; third-party claim protections are not exhaustively tested']}
(run/'verification.json').write_text(json.dumps(summary,indent=2)+'\n')
print('All tests passed:',run/'verification.json',flush=True)
