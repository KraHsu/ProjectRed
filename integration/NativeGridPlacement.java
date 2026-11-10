package runtimechecks;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual GT selection + native item-use/placement and FMP retracing regressions. */
public final class NativeGridPlacement extends NativeConnections {
    static Object pipeItem,player,inventory;
    static Object item(int meta,int count)throws Exception {
        return c("net.minecraft.item.ItemStack").getConstructor(c("net.minecraft.item.Item"),int.class,int.class).newInstance(pipeItem,count,meta);
    }
    static Object partAt(int x,int y,int z)throws Exception {
        Object tile=invoke(world,"func_147438_o",new Class<?>[]{int.class,int.class,int.class},x,y,z);
        return tile==null?null:invoke(tile,"partMap",new Class<?>[]{int.class},6);
    }
    static boolean place(Object stack,int x,int y,int z,int side,float hx,float hy,float hz)throws Exception {
        invoke(inventory,"func_70299_a",new Class<?>[]{int.class,c("net.minecraft.item.ItemStack")},0,stack);
        // Also verifies that client icon declarations are stripped correctly
        // when the concrete item is inspected on a dedicated server.
        Method method=pipeItem.getClass().getMethod("func_77648_a",c("net.minecraft.item.ItemStack"),c("net.minecraft.entity.player.EntityPlayer"),c("net.minecraft.world.World"),int.class,int.class,int.class,int.class,float.class,float.class,float.class);
        return (Boolean)method.invoke(pipeItem,stack,player,world,x,y,z,side,hx,hy,hz);
    }
    static float[] facePoint(int side,float u,float v,float normal){
        if(side<2)return new float[]{u,normal,v};
        if(side<4)return new float[]{u,v,normal};
        return new float[]{normal,v,u};
    }
    static int mask(Object p)throws Exception {int m=0;for(int s=0;s<6;s++)if(open(p,s))m|=1<<s;return m;}
    static Object solid(int x,int y,int z)throws Exception {
        Object registry=get(c("net.minecraft.block.Block"),"field_149771_c");Object block=invoke(registry,"func_82594_a",new Class<?>[]{String.class},"minecraft:stone");
        invoke(world,"func_147465_d",new Class<?>[]{int.class,int.class,int.class,c("net.minecraft.block.Block"),int.class,int.class},x,y,z,block,0,3);return block;
    }
    static void cover(int x,int y,int z,int side)throws Exception {
        Object face=get(c("codechicken.microblock.FaceMicroClass$"),"MODULE$");
        int stone=((Number)invoke(c("codechicken.microblock.MicroMaterialRegistry"),"materialID",new Class<?>[]{String.class},"minecraft:stone")).intValue();
        Object cover=invoke(face,"create",new Class<?>[]{boolean.class,int.class},false,stone);invoke(cover,"setShape",new Class<?>[]{int.class,int.class},1,side);add(cover,x,y,z);
    }
    public static void run(String output)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();List<String> cases=new ArrayList<>();List<Object> players=null;boolean added=false;
        try {
            world=invoke(c("net.minecraftforge.common.DimensionManager"),"getWorld",new Class<?>[]{int.class},0);
            player=invoke(c("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",new Class<?>[]{c("net.minecraft.world.WorldServer")},world);
            inventory=get(player,"field_71071_by");fld(inventory.getClass(),"field_70461_c").setInt(inventory,0);
            players=(List<Object>)get(world,"field_73010_i");if(!players.contains(player)){players.add(player);added=true;}
            pipeItem=no(get(c("mrtjp.projectred.ProjectRedTransportation$"),"MODULE$"),"itemPartPipe");
            Object registry=get(c("net.minecraft.item.Item"),"field_150901_e");coal=invoke(registry,"func_82594_a",new Class<?>[]{String.class},"minecraft:coal");
            Object grid=get(c(NS+"PipeWrenchGrid$"),"MODULE$");Class<?> direction=c("net.minecraftforge.common.util.ForgeDirection");
            float[] samples={-2.125f,-1f,-.75f,-.25f,-0f,0f,.125f,Math.nextDown(.25f),.25f,Math.nextUp(.25f),.5f,Math.nextDown(.75f),.75f,.875f,Math.nextDown(1f),1f,1.125f,Float.NaN};
            int compared=0;
            for(int face=0;face<6;face++)for(float x:samples)for(float y:samples)for(float z:samples){
                Object side=invoke(direction,"getOrientation",new Class<?>[]{int.class},face);
                int expected=((Enum<?>)invoke(c("gregtech.api.util.GTUtility"),"determineWrenchingSide",new Class<?>[]{direction,float.class,float.class,float.class},side,x,y,z)).ordinal();
                int actual=((Number)invoke(grid,"side",new Class<?>[]{int.class,float.class,float.class,float.class},face,x,y,z)).intValue();
                check(expected==actual,"GT grid mismatch at "+face+":"+x+","+y+","+z);compared++;
            }
            result.put("native_gt_coordinate_comparisons",compared);cases.add("gt_quarter_boundaries_and_float_wrapping");
            Object held=wrench();int x=704,y=200,z=704;clear(x,y,z);Object p=fresh(x,y,z,"BasicPipePart",0);
            float[] mid={.125f,.5f,.875f};
            for(int face=0;face<6;face++)for(int cell=0;cell<9;cell++){
                float[] xyz=facePoint(face,mid[cell%3],mid[cell/3],face%2==0?0f:1f);
                Object point=invoke(c("net.minecraft.util.Vec3"),"func_72443_a",new Class<?>[]{double.class,double.class,double.class},x+(double)xyz[0],y+(double)xyz[1],z+(double)xyz[2]);
                Object hit=c("net.minecraft.util.MovingObjectPosition").getConstructor(int.class,int.class,int.class,int.class,point.getClass()).newInstance(x,y,z,face,point);
                int selected=((Number)invoke(grid,"sideForCell",new Class<?>[]{int.class,int.class},face,cell)).intValue();
                activate(p,player,hit,held);check(mask(p)==(1<<selected),"Wrong actual face toggled for grid cell");
                activate(p,player,hit,held);check(mask(p)==0,"Grid cell did not toggle off");
            }
            cases.add("all_54_cells_toggle_actual_connections");
            // The real server retrace must reach a grid corner outside the thin body.
            invoke(inventory,"func_70299_a",new Class<?>[]{int.class,c("net.minecraft.item.ItemStack")},0,held);
            double eye=((Number)no(player,"func_70047_e")).doubleValue();
            invoke(player,"func_70080_a",new Class<?>[]{double.class,double.class,double.class,float.class,float.class},x+3.0,y+.875-eye,z+.875,90f,0f);
            Object start=invoke(c("codechicken.lib.raytracer.RayTracer"),"getStartVec",new Class<?>[]{c("net.minecraft.entity.player.EntityPlayer")},player),end=invoke(c("codechicken.lib.raytracer.RayTracer"),"getEndVec",new Class<?>[]{c("net.minecraft.entity.player.EntityPlayer")},player);
            Object rayHit=invoke(p,"collisionRayTrace",new Class<?>[]{start.getClass(),end.getClass()},start,end);
            check(rayHit!=null,"Wrench corner ray missed the full grid");
            double hitDistance=((Number)get(rayHit,"dist")).doubleValue();
            check(hitDistance>0&&Math.abs(hitDistance-((Number)invoke(start,"func_72436_e",new Class<?>[]{start.getClass()},get(rayHit,"field_72307_f"))).doubleValue())<1e-9,"Multipart hit distance metadata is incorrect");
            Object manager=get(player,"field_71134_c");
            check((Boolean)invoke(manager,"func_73078_a",new Class<?>[]{c("net.minecraft.entity.player.EntityPlayer"),c("net.minecraft.world.World"),c("net.minecraft.item.ItemStack"),int.class,int.class,int.class,int.class,float.class,float.class,float.class},player,world,held,x,y,z,5,1f,.875f,.875f),"Native corner activation failed");
            check(mask(p)==(1<<4),"Native corner did not select the opposite face");toggle(p,4);
            invoke(inventory,"func_70299_a",new Class<?>[]{int.class,c("net.minecraft.item.ItemStack")},0,stack(1));
            check(invoke(p,"collisionRayTrace",new Class<?>[]{start.getClass(),end.getClass()},start,end)==null,"Non-wrench interaction box changed");
            invoke(inventory,"func_70299_a",new Class<?>[]{int.class,c("net.minecraft.item.ItemStack")},0,held);
            Object shiftedStart=invoke(start,"func_72441_c",new Class<?>[]{double.class,double.class,double.class},0d,-.01d,0d),shiftedEnd=invoke(end,"func_72441_c",new Class<?>[]{double.class,double.class,double.class},0d,-.01d,0d);
            check(invoke(p,"collisionRayTrace",new Class<?>[]{start.getClass(),end.getClass()},shiftedStart,shiftedEnd)==null,"Non-player ray received a wrench selection box");
            double physicalVolume=0;
            for(Object box:(Iterable<?>)no(p,"getCollisionBoxes")){
                Object min=get(box,"min"),max=get(box,"max");physicalVolume+=(((Number)get(max,"x")).doubleValue()-((Number)get(min,"x")).doubleValue())*(((Number)get(max,"y")).doubleValue()-((Number)get(min,"y")).doubleValue())*(((Number)get(max,"z")).doubleValue()-((Number)get(min,"z")).doubleValue());
            }
            check(Math.abs(physicalVolume-.125)<1e-9,"Physical pipe collision was expanded");cases.add("native_corner_click_nonwrench_and_projectile_selection");
            // Actual item placement, seven pipe classes on all six supporting faces.
            String[] types={"BasicPipePart","RoutedJunctionPipePart","RoutedInterfacePipePart","PressureTube","ResistanceTube","NetworkValvePipePart","NetworkLatencyPipePart"};int[] metas={0,1,2,7,8,9,10};int fixture=0;
            Object caps=get(player,"field_71075_bZ");boolean creative=fld(caps.getClass(),"field_75098_d").getBoolean(caps);fld(caps.getClass(),"field_75098_d").setBoolean(caps,false);
            try {
                for(int type=0;type<types.length;type++)for(int face=0;face<6;face++){
                    x=720+(fixture++%12)*12;z=720+(fixture/12)*12;clear(x,y,z);int[] d=D[face];
                    Object support=fresh(x,y,z,types[type],metas[type]);int nx=x+d[0],ny=y+d[1],nz=z+d[2];
                    int extra=face<2?5:1;int[] e=D[extra];Object unrelated=pipe(nx+e[0],ny+e[1],nz+e[2],types[type],metas[type]);
                    Object stack=item(metas[type],2);float[] hit=facePoint(face,.5f,.5f,face%2==0?.25f:.75f);
                    check(place(stack,x,y,z,face,hit[0],hit[1],hit[2]),"Native pipe placement rejected");Object placed=partAt(nx,ny,nz);check(placed!=null,"Placement did not create a pipe");
                    check(mask(placed)==(1<<(face^1))&&mask(support)==(1<<face),"Placement enabled faces other than its support");
                    check(connected(placed,face^1)&&connected(support,face),"Placement support was not reciprocal");
                    check(!connected(placed,extra)&&!connected(unrelated,extra^1)&&mask(unrelated)==63,"Unrelated adjacent legacy network was connected/changed");
                    check(((Number)get(stack,"field_77994_a")).intValue()==1,"Survival placement consumed the wrong item count");
                }
                cases.add("42_native_pipe_support_placements");
                for(int meta:new int[]{2,7,8})for(int face=0;face<6;face++){
                    x=880+face*12;z=800+meta*12;clear(x,y,z);int[] d=D[face];chest(x,y,z);Object stack=item(meta,2);float[] hit=facePoint(face,.5f,.5f,face%2==0?0f:1f);
                    check(place(stack,x,y,z,face,hit[0],hit[1],hit[2]),"Inventory-supported placement rejected");Object placed=partAt(x+d[0],y+d[1],z+d[2]);
                    check(mask(placed)==(1<<(face^1))&&connected(placed,face^1),"Inventory support did not connect exactly one side");
                    if(meta==2)check(integer(placed,"inOutSide")== (face^1),"Placed interface did not select its inventory");
                }
                cases.add("18_native_inventory_support_placements");
                x=920;z=920;clear(x,y,z);solid(x,y,z);check(place(item(0,2),x,y,z,5,1f,.5f,.5f),"Stone-supported placement failed");check(mask(partAt(x+1,y,z))==0,"Stone was treated as an inventory");cases.add("solid_support_stays_disconnected");
                x+=12;clear(x,y,z);Object pressure=fresh(x,y,z,"PressureTube",7);check(place(item(0,2),x,y,z,5,.75f,.5f,.5f),"Incompatible pipe placement failed");check(mask(pressure)==0&&mask(partAt(x+1,y,z))==0,"Incompatible pipe systems were connected");cases.add("incompatible_support_stays_disconnected");
                x+=12;clear(x,y,z);Object covered=fresh(x,y,z,"BasicPipePart",0);cover(x,y,z,5);check(place(item(0,2),x,y,z,5,1f,.5f,.5f),"Covered support placement failed");check(mask(covered)==0&&mask(partAt(x+1,y,z))==0,"Placement bypassed a solid cover");cases.add("covered_support_stays_disconnected");
                x+=12;clear(x,y,z);cover(x,y,z,0);Object other=pipe(x+1,y,z,"BasicPipePart",0);check(place(item(0,2),x,y,z,1,.5f,.125f,.5f),"Inside-multipart placement failed");check(mask(partAt(x,y,z))==0&&mask(other)==63&&!connected(other,4),"Same-cell placement invented a supporting neighbour");cases.add("same_cell_multipart_does_not_auto_link");
                x+=12;clear(x,y,z);Object original=fresh(x,y,z,"BasicPipePart",0);solid(x+1,y,z);Object failed=item(0,2);check(!place(failed,x,y,z,5,.75f,.5f,.5f),"Occupied placement unexpectedly succeeded");check(mask(original)==0&&((Number)get(failed,"field_77994_a")).intValue()==2,"Failed placement changed network or consumed item");cases.add("failed_placement_is_side_effect_free");
                x=1010;z=920;clear(x,y,z);Object pending=fresh(x,y,z,"BasicPipePart",0);check(place(item(0,2),x,y,z,5,.75f,.5f,.5f),"Persistence fixture placement failed");toggle(pending,5);check(mask(pending)==0&&mask(partAt(x+1,y,z))==0,"Could not disable the auto-connected fixture");cases.add("disabled_placement_link_prepared_for_cold_reload");
            } finally {fld(caps.getClass(),"field_75098_d").setBoolean(caps,creative);}
            result.put("passed",true);result.put("assertions",assertions);result.put("cases",cases);
        }catch(Throwable error){error.printStackTrace();result.put("passed",false);result.put("assertions",assertions);result.put("completed_cases",cases);result.put("error",error.toString());if(error.getCause()!=null)result.put("cause",error.getCause().toString());}
        finally {if(added&&players!=null)players.remove(player);}
        Object gson=c("com.google.gson.Gson").newInstance();Files.write(Paths.get(output),((String)invoke(gson,"toJson",new Class<?>[]{Object.class},result)).getBytes("UTF-8"));
    }
}
