from pathlib import Path
import subprocess,zipfile,json,os,argparse,tempfile,hashlib
parser=argparse.ArgumentParser()
parser.add_argument('--pack',type=Path,required=True)
parser.add_argument('--candidate',type=Path,required=True)
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--srg-minecraft',type=Path,required=True)
parser.add_argument('--report',type=Path,required=True)
options=parser.parse_args()
root=Path(__file__).resolve().parent
runtime=options.pack.resolve();build=Path(tempfile.mkdtemp(prefix='projectred-render-fixture-'))
classes=build/'test-classes';classes.mkdir()
java=options.java_home/'bin/java';javac=options.java_home/'bin/javac'
asm=runtime/'libraries/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar'
original=runtime/'mods/ProjRed-4.12.50-GTNH.jar';patched=options.candidate.resolve()
deps=[runtime/'mods/CodeChickenCore-1.4.22.jar',runtime/'libraries/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar',
      options.srg_minecraft.resolve(),runtime/'minecraft_server.1.7.10.jar',
      runtime/'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar']
sources={
'mrtjp/projectred/core/TCenterConnectable.java': '''package mrtjp.projectred.core;public interface TCenterConnectable {int connMap();}''',
'net/minecraft/client/renderer/Tessellator.java': '''package net.minecraft.client.renderer;
public class Tessellator {public static final Tessellator field_78398_a=new Tessellator();public boolean capture=false;
private long hash=0xcbf29ce484222325L;private int r=255,g=255,b=255,a=255,light;private float nx,ny,nz;
void mix(long x){hash=(hash^x)*0x100000001b3L;}public long takeHash(){long x=hash;hash=0xcbf29ce484222325L;return x;}
public void func_78375_b(float x,float y,float z){nx=x;ny=y;nz=z;}public void func_78370_a(int rr,int gg,int bb,int aa){r=rr;g=gg;b=bb;a=aa;}
public void func_78380_c(int x){light=x;}
public void func_78374_a(double x,double y,double z,double u,double v){if(!capture)return;
mix(Double.doubleToLongBits(x));mix(Double.doubleToLongBits(y));mix(Double.doubleToLongBits(z));mix(Double.doubleToLongBits(u));mix(Double.doubleToLongBits(v));
mix(Float.floatToIntBits(nx));mix(Float.floatToIntBits(ny));mix(Float.floatToIntBits(nz));mix(r);mix(g);mix(b);mix(a);mix(light);}}
''',
'mrtjp/projectred/transportation/PipeDefs$PipeVal.java': '''package mrtjp.projectred.transportation;
import net.minecraft.util.IIcon;
public class PipeDefs$PipeVal {public IIcon[] icons=new IIcon[17];public IIcon[] sprites(){return icons;}}''',
'mrtjp/projectred/transportation/PipeDefs$.java': '''package mrtjp.projectred.transportation;
public class PipeDefs$ {public static final PipeDefs$ MODULE$=new PipeDefs$();private PipeDefs$PipeVal value=new SubcorePipePart().getPipeType();
public PipeDefs$PipeVal BASIC(){return value;}public PipeDefs$PipeVal PRESSURETUBE(){return value;}}''',
'mrtjp/projectred/transportation/TRedstonePipe.java': '''package mrtjp.projectred.transportation; public interface TRedstonePipe {int connMap();}''',
'mrtjp/projectred/transportation/TColourFilterPipe.java': '''package mrtjp.projectred.transportation; public interface TColourFilterPipe {int connMap();}''',
'mrtjp/projectred/transportation/SubcorePipePart.java': '''package mrtjp.projectred.transportation;
import net.minecraft.util.IIcon;import java.lang.reflect.Proxy;
public class SubcorePipePart implements TRedstonePipe,TColourFilterPipe,mrtjp.projectred.core.TCenterConnectable {public int mask=3,texture=0;private PipeDefs$PipeVal type=new PipeDefs$PipeVal();
public SubcorePipePart(){for(int i=0;i<type.icons.length;i++){final int index=i;type.icons[i]=(IIcon)Proxy.newProxyInstance(IIcon.class.getClassLoader(),new Class[]{IIcon.class},(p,m,a)->{
if(m.getName().equals("func_94214_a"))return (float)(((Number)a[0]).doubleValue()/16*.125+index*.015625);
if(m.getName().equals("func_94207_b"))return (float)(((Number)a[0]).doubleValue()/16*.125+texture*.25);
if(m.getReturnType()==int.class)return 16;if(m.getReturnType()==float.class)return 0f;return "test-icon";});}}
public int connMap(){return mask;}public PipeDefs$PipeVal getPipeType(){return type;}public IIcon getIcon(int side){return type.icons[side+1];}}
'''
}
stubs=build/'test-src'
for name,text in sources.items():
    p=stubs/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
subprocess.run([str(javac),'-cp',str(asm),'-d',str(classes),str(root/'FixtureClasses.java')],check=True)
for label,jar in [('original',original),('patched',patched)]:
    directory=build/('fixture-'+label);directory.mkdir(exist_ok=True)
    subprocess.run([str(java),'-cp',f'{classes}:{asm}','FixtureClasses',str(jar),str(directory)],check=True)
cp=os.pathsep.join(map(str,[build/'fixture-original',classes,original,*deps]))
subprocess.run([str(javac),'-cp',cp,'-d',str(classes),*map(str,stubs.rglob('*.java')),str(root/'RenderChecks.java')],check=True)
results=[]
for label,jar in [('original',original),('patched',patched)]:
    cp=os.pathsep.join(map(str,[build/('fixture-'+label),classes,jar,*deps]))
    result=subprocess.run([str(java),'-Xms512m','-Xmx512m','-cp',cp,'RenderChecks',label],text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:print(result.stdout,result.stderr);raise SystemExit(result.returncode)
    value=json.loads(result.stdout.strip().splitlines()[-1]);results.append(value);print(json.dumps(value),flush=True)
a,b=results
assert a['vertex_fingerprint']==b['vertex_fingerprint'],'Rendered vertex attributes changed'
assert a['geometry_fingerprint']==b['geometry_fingerprint'],'Model geometry changed'
assert b['check_model_constructions']==b['benchmark_model_constructions']==0
assert a['benchmark_model_constructions']==a['measured_pipe_calls']*3
report={'passed':True,'variants':results,'cpu_path_speedup':a['median_ns_per_pipe']/b['median_ns_per_pipe'],
        'allocation_reduction_percent':100*(1-b['allocated_bytes_per_pipe']/a['allocated_bytes_per_pipe']),
        'limits':['Synthetic world/icons and Tessellator sink, real ProjectRed/CCL CPU code','Not a full GTNH/Angelica GPU test or measured Windows FPS']}
report['runtime']={'java':subprocess.run([str(java),'-version'],capture_output=True,text=True,check=True).stderr.strip(),'artifacts':{str(p.name):hashlib.sha256(p.read_bytes()).hexdigest() for p in [asm,*deps]},'original_sha256':hashlib.sha256(original.read_bytes()).hexdigest(),'candidate_sha256':hashlib.sha256(patched.read_bytes()).hexdigest()}
options.report.parent.mkdir(parents=True,exist_ok=True)
options.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))
