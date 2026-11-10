package runtimechecks;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Native multipart/world integration tests, run only in the disposable pack. */
public class NativeConnections extends NativeRouting {
    static final int[][] D={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    static int assertions;
    static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    static int integer(Object o,String name)throws Exception{return ((Number)no(o,name)).intValue();}
    static boolean connected(Object p,int s)throws Exception{return (Boolean)invoke(p,"maskConnects",new Class<?>[]{int.class},s);}
    static boolean open(Object p,int s)throws Exception{return (Boolean)invoke(p,"connectionOpen",new Class<?>[]{int.class},s);}
    static boolean toggle(Object p,int s)throws Exception{return (Boolean)invoke(p,"toggleConnection",new Class<?>[]{int.class},s);}
    static Object save(Object part)throws Exception {
        Object tag=c("net.minecraft.nbt.NBTTagCompound").newInstance();invoke(part,"save",new Class<?>[]{tag.getClass()},tag);return tag;
    }
    static void add(Object part,int x,int y,int z)throws Exception {
        Object coord=c("codechicken.lib.vec.BlockCoord").getConstructor(int.class,int.class,int.class).newInstance(x,y,z);
        invoke(c("codechicken.multipart.TileMultipart"),"addPart",new Class<?>[]{c("net.minecraft.world.World"),coord.getClass(),c("codechicken.multipart.TMultiPart")},world,coord,part);
    }
    static Object fresh(int x,int y,int z,String type,int meta)throws Exception {
        Object part=c(NS+type).newInstance();invoke(part,"preparePlacement",new Class<?>[]{int.class,int.class},0,meta);add(part,x,y,z);return part;
    }
    static void clear(int x,int y,int z)throws Exception {
        Object provider=invoke(world,"func_72863_F",new Class<?>[]{});
        for(int cx=(x-4)>>4;cx<=(x+4)>>4;cx++)for(int cz=(z-4)>>4;cz<=(z+4)>>4;cz++)invoke(provider,"func_73158_c",new Class<?>[]{int.class,int.class},cx,cz);
        for(int a=x-3;a<=x+3;a++)for(int b=y-3;b<=y+3;b++)for(int d=z-3;d<=z+3;d++)invoke(world,"func_147468_f",new Class<?>[]{int.class,int.class,int.class},a,b,d);
    }
    static Object wrench()throws Exception {
        Object tool=get(c("gregtech.common.items.MetaGeneratedTool01"),"INSTANCE");
        Map<?,?> stats=(Map<?,?>)get(tool,"mToolStats");int id=-1;
        for(Map.Entry<?,?> e:stats.entrySet())if(e.getValue().getClass().getSimpleName().equals("ToolWrench")){id=((Number)e.getKey()).intValue();break;}
        if(id<0)throw new AssertionError("Native GT wrench not found: "+stats.values());
        Object iron=get(c("gregtech.api.enums.Materials"),"Iron");
        return invoke(tool,"getToolWithStats",new Class<?>[]{int.class,int.class,iron.getClass(),iron.getClass(),long[].class},id,1,iron,iron,(Object)null);
    }
    static Object hit(int x,int y,int z,int side)throws Exception {
        Object vector=invoke(c("net.minecraft.util.Vec3"),"func_72443_a",new Class<?>[]{double.class,double.class,double.class},x+.5,y+.5,z+.5);
        return c("net.minecraft.util.MovingObjectPosition").getConstructor(int.class,int.class,int.class,int.class,vector.getClass()).newInstance(x,y,z,side,vector);
    }
    static void activate(Object part,Object player,Object hit,Object held)throws Exception {
        check((Boolean)invoke(part,"activate",new Class<?>[]{c("net.minecraft.entity.player.EntityPlayer"),c("net.minecraft.util.MovingObjectPosition"),c("net.minecraft.item.ItemStack")},player,hit,held),"Wrench activation was not handled");
    }
    public static void run(String output)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();List<String> cases=new ArrayList<>();
        try {
            world=invoke(c("net.minecraftforge.common.DimensionManager"),"getWorld",new Class<?>[]{int.class},0);
            Object registry=get(c("net.minecraft.item.Item"),"field_150901_e");coal=invoke(registry,"func_82594_a",new Class<?>[]{String.class},"minecraft:coal");
            Object player=invoke(c("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",new Class<?>[]{c("net.minecraft.world.WorldServer")},world);
            Object held=wrench();Object ore=c("net.minecraftforge.oredict.OreDictionary");
            int[] ids=(int[])invoke(ore,"getOreIDs",new Class<?>[]{c("net.minecraft.item.ItemStack")},held);boolean registered=false;
            for(int id:ids)if("craftingToolWrench".equals(invoke(ore,"getOreName",new Class<?>[]{int.class},id)))registered=true;
            check(registered,"GT wrench is not registered as craftingToolWrench");result.put("gt_wrench_metadata",invoke(held,"func_77960_j",new Class<?>[]{}));
            String[] types={"BasicPipePart","RoutedJunctionPipePart","RoutedInterfacePipePart","PressureTube","ResistanceTube","NetworkValvePipePart","NetworkLatencyPipePart"};
            int[] metas={0,1,2,7,8,9,10};int fixture=0;
            for(int kind=0;kind<types.length;kind++)for(int side=0;side<6;side++){
                int x=320+(fixture++%12)*12,z=320+(fixture/12)*12,y=200;clear(x,y,z);
                Object a=fresh(x,y,z,types[kind],metas[kind]);int[] d=D[side];
                Object b=fresh(x+d[0],y+d[1],z+d[2],types[kind],metas[kind]);
                check((integer(a,"clientConnMap")&63)==0&&(integer(b,"clientConnMap")&63)==0,"New adjacent pipes auto-connected: "+types[kind]+side);
                activate(a,player,hit(x,y,z,side),held);
                check(open(a,side)&&open(b,side^1),"Wrench did not enable both faces");
                check(connected(a,side)&&connected(b,side^1),"Enabled connection is not reciprocal");
                for(int other=0;other<6;other++)if(other!=side)check(!open(a,other),"Wrench changed an unrelated face");
                Object saved=save(a);Object copy=c(NS+types[kind]).newInstance();invoke(copy,"load",new Class<?>[]{saved.getClass()},saved);
                check(open(copy,side),"Manual mask did not survive NBT reload");for(int s=0;s<6;s++)if(s!=side)check(!open(copy,s),"NBT reload enabled another face");
                activate(b,player,hit(x+d[0],y+d[1],z+d[2],side^1),held);
                for(int i=0;i<3;i++){no(a,"onNeighborChanged");no(b,"onNeighborChanged");no(a,"updateInward");no(b,"updateInward");}
                check(!open(a,side)&&!open(b,side^1)&&!connected(a,side)&&!connected(b,side^1),"Disabled connection returned after block updates");
                saved=save(a);copy=c(NS+types[kind]).newInstance();invoke(copy,"load",new Class<?>[]{saved.getClass()},saved);check(!open(copy,side),"Disabled face did not persist");
                invoke(saved,"func_82580_o",new Class<?>[]{String.class},"manualConnections");
                copy=c(NS+types[kind]).newInstance();invoke(copy,"load",new Class<?>[]{saved.getClass()},saved);for(int s=0;s<6;s++)check(open(copy,s),"Legacy missing key closed a face");
                cases.add(types[kind]+"/face"+side);
            }
            // Six inventory directions, including selection reset and reconnection.
            for(int side=0;side<6;side++) {
                int x=512+side*12,y=200,z=320;clear(x,y,z);int[] d=D[side];
                Object inventory=chest(x+d[0],y+d[1],z+d[2]);Object p=fresh(x,y,z,"RoutedInterfacePipePart",2);
                check(!connected(p,side)&&integer(p,"inOutSide")==6,"New pipe auto-connected to inventory");
                toggle(p,side);check(connected(p,side)&&integer(p,"inOutSide")==side,"Inventory face was not selected");
                toggle(p,side);check(!connected(p,side)&&integer(p,"inOutSide")==6,"Disabled inventory still selected");
                no(p,"onNeighborChanged");check(!connected(p,side),"Inventory auto-reconnected");
                toggle(p,side);check(connected(p,side)&&integer(p,"inOutSide")==side,"Inventory could not reconnect");
                cases.add("inventory/face"+side);
            }
            // Real microblock cover must continue to veto an enabled connection.
            int x=512,y=200,z=350;clear(x,y,z);Object a=fresh(x,y,z,"BasicPipePart",0),b=fresh(x+1,y,z,"BasicPipePart",0);
            Object face=get(c("codechicken.microblock.FaceMicroClass$"),"MODULE$");
            int stone=((Number)invoke(c("codechicken.microblock.MicroMaterialRegistry"),"materialID",new Class<?>[]{String.class},"minecraft:stone")).intValue();
            Object cover=invoke(face,"create",new Class<?>[]{boolean.class,int.class},false,stone);invoke(cover,"setShape",new Class<?>[]{int.class,int.class},1,5);add(cover,x,y,z);
            toggle(a,5);check(open(a,5)&&!connected(a,5)&&!connected(b,4),"Wrench bypassed cover occlusion");cases.add("solid_cover_still_blocks");
            // A new neighbour must not inherit the open policy of an old pipe.
            x=536;clear(x,y,z);a=pipe(x,y,z,"BasicPipePart",0);b=fresh(x+1,y,z,"BasicPipePart",0);
            check(open(a,5)&&!connected(a,5)&&!connected(b,4),"Legacy/new pair auto-connected");
            toggle(a,5);check(connected(a,5)&&connected(b,4),"Clicking the legacy end did not connect a new neighbour");
            toggle(b,4);check(!connected(a,5)&&!connected(b,4),"Legacy/new pair could not disconnect");
            toggle(b,4);check(connected(a,5)&&connected(b,4),"Clicking the new end did not reconnect");cases.add("legacy_new_pair");
            // A routed bridge cut must disappear from the native routing table.
            x=560;clear(x,y,z);Object left=fresh(x-1,y,z,"RoutedJunctionPipePart",1),middle=fresh(x,y,z,"BasicPipePart",0),right=fresh(x+1,y,z,"RoutedJunctionPipePart",1);
            toggle(left,5);toggle(middle,5);Object[] pipes={left,middle,right};
            for(int t=0;t<3;t++)for(Object p:pipes)no(p,"update");
            for(Object p:new Object[]{left,right})invoke(no(p,"getRouter"),"update",new Class<?>[]{boolean.class},true);
            Object key=invoke(c("mrtjp.core.item.ItemKey"),"get",new Class<?>[]{c("net.minecraft.item.ItemStack")},stack(1));Object passive=no(get(c(NS+"Priorities$"),"MODULE$"),"PASSIVE");
            Class<?>[] directionArgs={int.class,c("mrtjp.core.item.ItemKey"),c(NS+"Priorities$Priority")};
            check(((Number)invoke(no(left,"getRouter"),"getDirection",directionArgs,ip(right),key,passive)).intValue()==5,"Connected bridge did not route");
            toggle(middle,5);for(Object p:new Object[]{left,right})invoke(no(p,"getRouter"),"update",new Class<?>[]{boolean.class},true);
            check(((Number)invoke(no(left,"getRouter"),"getDirection",directionArgs,ip(right),key,passive)).intValue()==6,"Disconnected bridge remained routable");
            toggle(middle,5);for(Object p:new Object[]{left,right})invoke(no(p,"getRouter"),"update",new Class<?>[]{boolean.class},true);
            check(((Number)invoke(no(left,"getRouter"),"getDirection",directionArgs,ip(right),key,passive)).intValue()==5,"Reconnected bridge did not route");cases.add("native_route_cut_and_restore");
            // An item already in flight cannot cross a newly closed connection.
            Object item=c(NS+"NetworkPayload").getConstructor(int.class).newInstance(25999);invoke(item,"setItemStack",new Class<?>[]{c("net.minecraft.item.ItemStack")},stack(3));
            invoke(item,"output_$eq",new Class<?>[]{int.class},5);invoke(item,"input_$eq",new Class<?>[]{int.class},5);
            invoke(item,"bind",new Class<?>[]{c(NS+"PayloadPipePart")},middle);toggle(middle,5);
            invoke(middle,"endReached",new Class<?>[]{c(NS+"AbstractPipePayload")},item);
            check(integer(no(item,"payload"),"stackSize")==3,"Disconnect lost in-flight items");check(integer(item,"output")!=5,"In-flight item selected closed face");cases.add("in_flight_disconnect_preserves_items");
            result.put("passed",true);result.put("assertions",assertions);result.put("cases",cases);
        }catch(Throwable error){error.printStackTrace();result.put("passed",false);result.put("assertions",assertions);result.put("completed_cases",cases);result.put("error",error.toString());if(error.getCause()!=null)result.put("cause",error.getCause().toString());}
        Object gson=c("com.google.gson.Gson").newInstance();Files.write(Paths.get(output),((String)invoke(gson,"toJson",new Class<?>[]{Object.class},result)).getBytes("UTF-8"));
    }
}
