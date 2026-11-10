package runtimechecks;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Runs only inside the loopback-only disposable GTNH harness. */
public class NativeRouting {
    static Class<?> c(String name)throws Exception{return Class.forName(name);}
    static Field fld(Class<?> type,String...names)throws Exception {
        for(Class<?> k=type;k!=null;k=k.getSuperclass())for(String n:names)try{Field f=k.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(type+Arrays.toString(names));
    }
    static Object get(Object o,String...names)throws Exception{return fld(o instanceof Class?(Class<?>)o:o.getClass(),names).get(o instanceof Class?null:o);}
    static Object call(Object o,String a,String b,Class<?>[]types,Object...args)throws Exception {
        Class<?> type=o instanceof Class?(Class<?>)o:o.getClass();Method m;try{m=type.getMethod(a,types);}catch(NoSuchMethodException ex){m=type.getMethod(b,types);}
        m.setAccessible(true);return m.invoke(o instanceof Class?null:o,args);
    }
    static final String NS="mrtjp.projectred.transportation.";
    static Object invoke(Object o,String n,Class<?>[] t,Object...a)throws Exception {
        Method m=(o instanceof Class?(Class<?>)o:o.getClass()).getMethod(n,t);m.setAccessible(true);return m.invoke(o instanceof Class?null:o,a);
    }
    static Object no(Object o,String n)throws Exception{return invoke(o,n,new Class<?>[]{});}
    static Object world,coal;
    static Object stack(int count)throws Exception{return c("net.minecraft.item.ItemStack").getConstructor(c("net.minecraft.item.Item"),int.class,int.class).newInstance(coal,count,0);}
    static Object chest(int x,int y,int z)throws Exception {
        Object registry=get(c("net.minecraft.block.Block"),"field_149771_c");Object block=call(registry,"getObject","func_82594_a",new Class<?>[]{String.class},"minecraft:chest");
        invoke(world,"func_147465_d",new Class<?>[]{int.class,int.class,int.class,c("net.minecraft.block.Block"),int.class,int.class},x,y,z,block,0,3);
        return invoke(world,"func_147438_o",new Class<?>[]{int.class,int.class,int.class},x,y,z);
    }
    static void fill(Object chest,int free)throws Exception {
        int slots=((Number)invoke(chest,"func_70302_i_",new Class<?>[]{})).intValue();
        for(int i=0;i<slots;i++)invoke(chest,"func_70299_a",new Class<?>[]{int.class,c("net.minecraft.item.ItemStack")},i,i==0&&free==64?null:stack(i==0?64-free:64));
    }
    static Object pipe(int x,int y,int z,String type,int meta)throws Exception {
        Object part=c(NS+type).newInstance();invoke(part,"preparePlacement",new Class<?>[]{int.class,int.class},0,meta);
        // Model a saved legacy network; new item placements intentionally start disconnected.
        Object legacy=c("net.minecraft.nbt.NBTTagCompound").newInstance();
        invoke(part,"save",new Class<?>[]{legacy.getClass()},legacy);
        invoke(legacy,"func_82580_o",new Class<?>[]{String.class},"manualConnections");
        invoke(part,"load",new Class<?>[]{legacy.getClass()},legacy);
        Object coord=c("codechicken.lib.vec.BlockCoord").getConstructor(int.class,int.class,int.class).newInstance(x,y,z);
        invoke(c("codechicken.multipart.TileMultipart"),"addPart",new Class<?>[]{c("net.minecraft.world.World"),coord.getClass(),c("codechicken.multipart.TMultiPart")},world,coord,part);
        return part;
    }
    static void responder(Object part)throws Exception {
        Object chip=c(NS+"ChipItemResponder").newInstance();
        invoke(chip,"filterExclude_$eq",new Class<?>[]{boolean.class},true);
        invoke(chip,"setEnvironment",new Class<?>[]{c(NS+"IInventoryProvider"),c(NS+"TRouteLayer"),int.class},part,part,0);
        Array.set(no(part,"chips"),0,chip);
        invoke(part,"mrtjp$projectred$transportation$RoutedInterfacePipePart$$chipsNeedRefresh_$eq",new Class<?>[]{boolean.class},false);
    }
    static int ip(Object part)throws Exception{return ((Number)no(no(part,"getRouter"),"getIPAddress")).intValue();}
    static int transit(Object part,Object itemKey)throws Exception {return ((Number)invoke(no(part,"transitQueue"),"apply",new Class<?>[]{c("mrtjp.core.item.ItemKey")},itemKey)).intValue();}
    public static void run(String output)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            world=invoke(c("net.minecraftforge.common.DimensionManager"),"getWorld",new Class<?>[]{int.class},0);
            Object registry=get(c("net.minecraft.item.Item"),"field_150901_e");coal=call(registry,"getObject","func_82594_a",new Class<?>[]{String.class},"minecraft:coal");
            int x=112,y=200,z=112;
            Object provider=invoke(world,"func_72863_F",new Class<?>[]{});
            for(int cx=6;cx<=8;cx++)for(int cz=6;cz<=8;cz++)invoke(provider,"func_73158_c",new Class<?>[]{int.class,int.class},cx,cz);
            for(int xx=x-2;xx<=x+3;xx++)for(int yy=y-1;yy<=y+2;yy++)for(int zz=z-1;zz<=z+2;zz++)invoke(world,"func_147468_f",new Class<?>[]{int.class,int.class,int.class},xx,yy,zz);
            Object sourceChest=chest(x-1,y-1,z),fallbackChest=chest(x,y+1,z),targetChest=chest(x+2,y,z);
            Object source=pipe(x-1,y,z,"RoutedInterfacePipePart",2),fallback=pipe(x,y,z,"RoutedInterfacePipePart",2),target=pipe(x+1,y,z,"RoutedInterfacePipePart",2);
            Object branch=pipe(x,y,z+1,"RoutedJunctionPipePart",1);
            Object[] pipes={source,fallback,target,branch};
            for(int t=0;t<3;t++)for(Object p:pipes)no(p,"update");
            responder(target);responder(fallback);
            for(int i=0;i<2;i++)for(Object p:pipes)invoke(no(p,"getRouter"),"update",new Class<?>[]{boolean.class},true);
            result.put("source_ip",ip(source));result.put("fallback_ip",ip(fallback));result.put("target_ip",ip(target));
            Object key=invoke(c("mrtjp.core.item.ItemKey"),"get",new Class<?>[]{c("net.minecraft.item.ItemStack")},stack(1));
            Object priorities=get(c(NS+"Priorities$"),"MODULE$");Object passive=no(priorities,"PASSIVE");
            int sourceHop=((Number)invoke(no(source,"getRouter"),"getDirection",new Class<?>[]{int.class,c("mrtjp.core.item.ItemKey"),c(NS+"Priorities$Priority")},ip(target),key,passive)).intValue();
            int branchHop=((Number)invoke(no(fallback,"getRouter"),"getDirection",new Class<?>[]{int.class,c("mrtjp.core.item.ItemKey"),c(NS+"Priorities$Priority")},ip(branch),key,passive)).intValue();
            result.put("adjacent_interface_hop_to_target",sourceHop);result.put("interface_branch_hop",branchHop);
            fill(targetChest,0);fill(fallbackChest,64);
            Object r=c(NS+"NetworkPayload").getConstructor(int.class).newInstance(24001);
            invoke(r,"setItemStack",new Class<?>[]{c("net.minecraft.item.ItemStack")},stack(1));
            invoke(r,"setDestination",new Class<?>[]{int.class,c(NS+"Priorities$Priority")},ip(target),passive);
            invoke(target,"itemExpected",new Class<?>[]{c("mrtjp.core.item.ItemKeyStack")},no(r,"payload"));
            invoke(target,"injectPayload",new Class<?>[]{c(NS+"NetworkPayload"),int.class},r,5);
            boolean accepted=(Boolean)invoke(target,"passToInventory",new Class<?>[]{c(NS+"NetworkPayload")},r);
            invoke(target,"bounceStack",new Class<?>[]{c(NS+"AbstractPipePayload")},r);
            result.put("full_inventory_accepted",accepted);result.put("bounce_output",no(r,"output"));result.put("bounce_destination",no(r,"destinationIP"));
            result.put("bounce_travel_log",no(r,"travelLog").toString());result.put("old_target_reserved_after_bounce",transit(target,key));
            // Keep the first payload in the isolated world; clear just the transient reservation for the next case.
            invoke(no(target,"transitQueue"),"clear",new Class<?>[]{});
            fill(targetChest,2);
            Object partial=c(NS+"NetworkPayload").getConstructor(int.class).newInstance(24005);
            invoke(partial,"setItemStack",new Class<?>[]{c("net.minecraft.item.ItemStack")},stack(5));
            invoke(partial,"setDestination",new Class<?>[]{int.class,c(NS+"Priorities$Priority")},ip(target),passive);
            invoke(target,"itemExpected",new Class<?>[]{c("mrtjp.core.item.ItemKeyStack")},no(partial,"payload"));
            invoke(target,"injectPayload",new Class<?>[]{c(NS+"NetworkPayload"),int.class},partial,5);
            boolean fullyAccepted=(Boolean)invoke(target,"passToInventory",new Class<?>[]{c(NS+"NetworkPayload")},partial);
            result.put("partial_fully_accepted",fullyAccepted);result.put("partial_remaining",no(no(partial,"payload"),"stackSize"));
            result.put("partial_remaining_reservation",transit(target,key));
            // Match the reported layout exactly: chest - interface - interface - chest.
            for(int xx=x+7;xx<=x+10;xx++)invoke(world,"func_147468_f",new Class<?>[]{int.class,int.class,int.class},xx,y,z+6);
            Object leftChest=chest(x+7,y,z+6),rightChest=chest(x+10,y,z+6);
            Object left=pipe(x+8,y,z+6,"RoutedInterfacePipePart",2),right=pipe(x+9,y,z+6,"RoutedInterfacePipePart",2);
            for(int i=0;i<3;i++){no(left,"update");no(right,"update");}
            responder(left);responder(right);
            invoke(no(left,"getRouter"),"update",new Class<?>[]{boolean.class},true);
            invoke(no(right,"getRouter"),"update",new Class<?>[]{boolean.class},true);
            result.put("two_interfaces_left_inventory_side",no(left,"inOutSide"));result.put("two_interfaces_right_inventory_side",no(right,"inOutSide"));
            boolean[] delivered=new boolean[2];
            for(int i=0;i<2;i++) {
                Object receiver=i==0?right:left;
                Object item=c(NS+"NetworkPayload").getConstructor(int.class).newInstance(24100+i);
                invoke(item,"setItemStack",new Class<?>[]{c("net.minecraft.item.ItemStack")},stack(1));
                invoke(item,"setDestination",new Class<?>[]{int.class,c(NS+"Priorities$Priority")},ip(receiver),passive);
                invoke(receiver,"itemExpected",new Class<?>[]{c("mrtjp.core.item.ItemKeyStack")},no(item,"payload"));
                invoke(receiver,"injectPayload",new Class<?>[]{c(NS+"NetworkPayload"),int.class},item,i==0?5:4);
                delivered[i]=(Boolean)invoke(receiver,"passToInventory",new Class<?>[]{c(NS+"NetworkPayload")},item);
            }
            result.put("two_interfaces_deliver_both_directions",delivered);
            // Exercise native Forge version checks with an unmodified 4.12.50 peer.
            Object network=get(c("cpw.mods.fml.common.network.NetworkRegistry"),"INSTANCE");
            Map<?,?> holders=(Map<?,?>)no(network,"registry");Map<String,String> versions=new HashMap<>();
            for(Map.Entry<?,?> entry:holders.entrySet())versions.put((String)no(entry.getKey(),"getModId"),(String)no(entry.getKey(),"getVersion"));
            Map<String,Object> negotiated=new LinkedHashMap<>();
            for(Map.Entry<?,?> entry:holders.entrySet()) {
                String id=(String)no(entry.getKey(),"getModId");if(!id.toLowerCase(Locale.ROOT).contains("projred"))continue;
                versions.put(id,"4.12.50-GTNH");
                negotiated.put(id,invoke(entry.getValue(),"check",new Class<?>[]{Map.class,c("cpw.mods.fml.relauncher.Side")},versions,get(c("cpw.mods.fml.relauncher.Side"),"CLIENT")));
            }
            result.put("original_peer_version_accepted",negotiated);
            result.put("completed",true);
        } catch(Throwable e) {e.printStackTrace();result.put("error",e.toString());if(e.getCause()!=null)result.put("cause",e.getCause().toString());}
        Object gson=c("com.google.gson.Gson").newInstance();Files.write(Paths.get(output),((String)invoke(gson,"toJson",new Class<?>[]{Object.class},result)).getBytes("UTF-8"));
    }
}
