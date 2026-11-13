#!/usr/bin/env python3
"""Serial paired real-client runs; never connects to the production server."""
from pathlib import Path
import hashlib,json,os,signal,subprocess,time
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
OUT=Path(os.environ.get('PR_DRAWER_OUTPUT', ROOT/'build/drawers-client-test')).resolve()
PROJECTS={variant:Path(os.environ['PR_DRAWER_'+variant.upper()]).resolve() for variant in ('baseline','candidate')}
env=os.environ.copy();env['VERSION']='4.12.50-GTNH'
order=[(1,'baseline'),(1,'candidate'),(2,'candidate'),(2,'baseline'),(3,'baseline'),(3,'candidate')]
records=[]
def write_status(**kw):
    (OUT/'pair-status.json').write_text(json.dumps({'records':records,**kw},indent=2)+'\n')
for pair,variant in order:
    name=f'pair-{pair}-{variant}';output=OUT/name;output.mkdir(parents=True,exist_ok=False)
    project=PROJECTS[variant]
    command=[str(project/'gradlew'),'--no-daemon','--no-configuration-cache','-I',str(HERE/'client.init.gradle'),'-PdrawerTestRoot='+str(HERE),'-PdrawerVariant='+variant,'-PdrawerTicks=400','-PdrawerOutput='+str(output),'runClient']
    print('START',name,flush=True);write_status(current=name,status='running')
    with (output/'run.log').open('w') as log:
        process=subprocess.Popen(command,cwd=project,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try: code=process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGTERM);process.wait(timeout=30);raise
    report=json.loads((output/'results.json').read_text())
    assert code==0 and report['status']=='passed',(name,report.get('error'),report.get('cause'))
    assert report['controller_range']==4 and report['controller_max_drawer_blocks']==50
    assert report['client_total_matches'] and report['native_final_total']==51400
    assert len(report['phases'])==3
    for phase in report['phases']:
        assert phase['server_tick_cpu']['samples']==400 and phase['server_tick_wall']['samples']==400
        assert phase['count_updates_emitted']==phase['count_updates_received']
        assert phase['client_frames']['samples']>500
        assert phase['focused_frames']>=phase['client_frames']['samples']*.95,('Lost window focus',name,phase['phase'])
    jar=project/'build/libs/ProjRed-4.12.50-GTNH.jar'
    record={'pair':pair,'variant':variant,'output':str(output),'artifact_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'status':'passed'}
    records.append(record);write_status(current=name,status='finished_run')
    print('PASS',name,flush=True)
write_status(status='all_passed')
print('ALL SIX REAL CLIENT RUNS PASSED',flush=True)
