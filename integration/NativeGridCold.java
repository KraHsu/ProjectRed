package runtimechecks;
import java.nio.file.*;
import java.util.*;

public final class NativeGridCold extends NativeConnections {
    public static void run(String output)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            world=invoke(c("net.minecraftforge.common.DimensionManager"),"getWorld",new Class<?>[]{int.class},0);
            Object provider=invoke(world,"func_72863_F",new Class<?>[]{});int fixture=0;
            for(int type=0;type<7;type++)for(int side=0;side<6;side++){
                int x=720+(fixture++%12)*12,z=720+(fixture/12)*12,y=200;int[] d=D[side];
                for(int cx=(x-3)>>4;cx<=(x+3)>>4;cx++)for(int cz=(z-3)>>4;cz<=(z+3)>>4;cz++)invoke(provider,"func_73158_c",new Class<?>[]{int.class,int.class},cx,cz);
                Object support=NativeGridPlacement.partAt(x,y,z),placed=NativeGridPlacement.partAt(x+d[0],y+d[1],z+d[2]);
                check(support!=null&&placed!=null,"Saved placed pipe is missing");no(support,"onNeighborChanged");no(placed,"onNeighborChanged");
                check(NativeGridPlacement.mask(placed)==(1<<(side^1))&&NativeGridPlacement.mask(support)==(1<<side),"Cold reload changed support-only masks");
                check(connected(support,side)&&connected(placed,side^1),"Cold reload lost support connection");
            }
            for(int cx=62;cx<=63;cx++)invoke(provider,"func_73158_c",new Class<?>[]{int.class,int.class},cx,57);
            Object a=NativeGridPlacement.partAt(1010,200,920),b=NativeGridPlacement.partAt(1011,200,920);no(a,"onNeighborChanged");no(b,"onNeighborChanged");
            check(NativeGridPlacement.mask(a)==0&&NativeGridPlacement.mask(b)==0&&!connected(a,5)&&!connected(b,4),"Cold reload replayed an old placement and reconnected a disabled link");
            result.put("passed",true);result.put("assertions",assertions);result.put("saved_support_pairs",42);result.put("disabled_link_stays_disabled",true);
        }catch(Throwable error){error.printStackTrace();result.put("passed",false);result.put("error",error.toString());if(error.getCause()!=null)result.put("cause",error.getCause().toString());}
        Object gson=c("com.google.gson.Gson").newInstance();Files.write(Paths.get(output),((String)invoke(gson,"toJson",new Class<?>[]{Object.class},result)).getBytes("UTF-8"));
    }
}
