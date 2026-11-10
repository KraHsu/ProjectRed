package runtimechecks;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Full server right-click path plus negative-coordinate, chunk-boundary persistence. */
public final class NativeInteractions extends NativeConnections {
    public static void run(String output)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            world=invoke(c("net.minecraftforge.common.DimensionManager"),"getWorld",new Class<?>[]{int.class},0);
            Object player=invoke(c("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",new Class<?>[]{c("net.minecraft.world.WorldServer")},world);
            Object held=wrench();int x=608,y=200,z=352;clear(x,y,z);
            Object p=fresh(x,y,z,"RoutedInterfacePipePart",2);
            Object inv=get(player,"field_71071_by");invoke(inv,"func_70299_a",new Class<?>[]{int.class,c("net.minecraft.item.ItemStack")},0,held);
            fld(inv.getClass(),"field_70461_c").setInt(inv,0);
            invoke(player,"func_70080_a",new Class<?>[]{double.class,double.class,double.class,float.class,float.class},x+3.0,y-1.12,z+.5,90f,0f);
            Object manager=get(player,"field_71134_c");
            Object current=no(player,"func_70694_bm");result.put("current_held",current==null?"null":current.toString());result.put("wrench_usable",invoke(no(held,"func_77973_b"),"isItemStackUsable",new Class<?>[]{c("net.minecraft.item.ItemStack")},held));
            boolean handled=(Boolean)invoke(manager,"func_73078_a",new Class<?>[]{c("net.minecraft.entity.player.EntityPlayer"),c("net.minecraft.world.World"),c("net.minecraft.item.ItemStack"),int.class,int.class,int.class,int.class,float.class,float.class,float.class},player,world,held,x,y,z,5,.75f,.5f,.5f);
            check(handled&&open(p,5),"Native ItemInWorldManager/GT wrench/FMP right-click did not enable east face");
            handled=(Boolean)invoke(manager,"func_73078_a",new Class<?>[]{c("net.minecraft.entity.player.EntityPlayer"),c("net.minecraft.world.World"),c("net.minecraft.item.ItemStack"),int.class,int.class,int.class,int.class,float.class,float.class,float.class},player,world,held,x,y,z,5,.75f,.5f,.5f);
            check(handled&&!open(p,5),"Native right-click did not disable east face");result.put("native_right_click_pipeline",true);
            Object capabilities=get(player,"field_71075_bZ");boolean editing=fld(capabilities.getClass(),"field_75099_e").getBoolean(capabilities);
            fld(capabilities.getClass(),"field_75099_e").setBoolean(capabilities,false);
            try{boolean allowed=(Boolean)invoke(player,"func_82247_a",new Class<?>[]{int.class,int.class,int.class,int.class,c("net.minecraft.item.ItemStack")},x,y,z,5,held);activate(p,player,hit(x,y,z,5),held);check(open(p,5)==allowed,"Native edit permission check was ignored");if(open(p,5))toggle(p,5);}
            finally{fld(capabilities.getClass(),"field_75099_e").setBoolean(capabilities,editing);}
            result.put("native_edit_check_respected",true);
            check(!toggle(p,-1)&&!toggle(p,6),"Invalid direction accepted");for(int s=0;s<6;s++)check(!open(p,s),"Invalid direction changed mask");
            // Isolated native world fixture for save/stop/start/load verification.
            x=-17;y=200;z=-17;clear(x,y,z);Object left=fresh(x,y,z,"RoutedInterfacePipePart",2),right=fresh(x+1,y,z,"RoutedInterfacePipePart",2);
            Object inventory=chest(x-1,y,z);toggle(left,4);toggle(left,5);
            check(connected(left,5)&&connected(right,4)&&integer(left,"inOutSide")==4,"Negative coordinate / chunk-boundary connection failed");
            Object tag=save(left);check(((Number)invoke(tag,"func_74771_c",new Class<?>[]{String.class},"manualConnections")).intValue()==48,"Unexpected persisted fixture mask");
            result.put("persistence_fixture_prepared",true);result.put("assertions",assertions);result.put("passed",true);
        } catch(Throwable error){error.printStackTrace();result.put("passed",false);result.put("error",error.toString());if(error.getCause()!=null)result.put("cause",error.getCause().toString());}
        Object gson=c("com.google.gson.Gson").newInstance();Files.write(Paths.get(output),((String)invoke(gson,"toJson",new Class<?>[]{Object.class},result)).getBytes("UTF-8"));
    }
    public static void cold(String output)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            world=invoke(c("net.minecraftforge.common.DimensionManager"),"getWorld",new Class<?>[]{int.class},0);Object provider=invoke(world,"func_72863_F",new Class<?>[]{});
            for(int x=-2;x<=-1;x++)for(int z=-2;z<=-1;z++)invoke(provider,"func_73158_c",new Class<?>[]{int.class,int.class},x,z);
            Object tile=invoke(world,"func_147438_o",new Class<?>[]{int.class,int.class,int.class},-17,200,-17),other=invoke(world,"func_147438_o",new Class<?>[]{int.class,int.class,int.class},-16,200,-17);
            Object left=invoke(tile,"partMap",new Class<?>[]{int.class},6),right=invoke(other,"partMap",new Class<?>[]{int.class},6);
            for(Object p:new Object[]{left,right}){no(p,"onNeighborChanged");no(p,"update");}
            for(int s=0;s<6;s++){check(open(left,s)==(s==4||s==5),"Cold-load left mask changed");check(open(right,s)==(s==4),"Cold-load right mask changed");}
            check(connected(left,5)&&connected(right,4)&&connected(left,4),"Cold-loaded physical connections not reciprocal");
            check(integer(left,"inOutSide")==4,"Cold-load inventory orientation changed");
            result.put("passed",true);result.put("native_save_stop_start_load",true);result.put("assertions",assertions);
        }catch(Throwable error){error.printStackTrace();result.put("passed",false);result.put("error",error.toString());if(error.getCause()!=null)result.put("cause",error.getCause().toString());}
        Object gson=c("com.google.gson.Gson").newInstance();Files.write(Paths.get(output),((String)invoke(gson,"toJson",new Class<?>[]{Object.class},result)).getBytes("UTF-8"));
    }
}
