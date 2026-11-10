#!/usr/bin/env python3
"""Run the real client grid renderer under Xvfb. Requires a Gradle test classpath file."""
from pathlib import Path
import argparse,json,os,subprocess
p=argparse.ArgumentParser();p.add_argument('--classpath-file',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parent;repo=root.parent;classes=repo/'build/grid-gl-test';classes.mkdir(parents=True,exist_ok=True)
classpath=a.classpath_file.read_text().strip()
compiled=subprocess.run(['javac','--release','8','-proc:none','-Xlint:-options','-cp',classpath,'-d',str(classes),str(root/'GridGLChecks.java')],capture_output=True,text=True)
if compiled.returncode:print(compiled.stdout,compiled.stderr);raise SystemExit(compiled.returncode)
result=subprocess.run(['xvfb-run','-a','java','-Djava.library.path='+str(repo/'run/natives/lwjgl2'),'-cp',str(classes)+os.pathsep+classpath,'runtimechecks.GridGLChecks',str(root/'results/wrench-grid-preview.png')],capture_output=True,text=True,timeout=60)
if result.returncode:print(result.stdout,result.stderr);raise SystemExit(result.returncode)
report=json.loads(result.stdout.strip().splitlines()[-1]);assert report['passed'];(root/'results/grid-gl-verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
