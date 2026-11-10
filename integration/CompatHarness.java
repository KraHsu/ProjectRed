package runtimechecks;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.nio.file.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;

/** Runs replaceable test helpers in a fresh classloader; no pack restart per case. */
@Mod(modid="runtimecompatchecks",name="Isolated Runtime Compatibility Harness",version="1",acceptableRemoteVersions="*")
public class CompatHarness {
    @Mod.EventHandler public void started(FMLServerStartedEvent event)throws Exception {
        FMLCommonHandler.instance().bus().register(this);
        Files.createDirectories(Paths.get("checks"));Files.write(Paths.get("checks/ready"),new byte[0]);
        System.out.println("RUNTIME_CHECKS_READY");
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        try {
            if(Files.exists(Paths.get("checks/stop"))) {
                Class<?> c=Class.forName("net.minecraft.server.MinecraftServer");
                Object s=c.getMethod("func_71276_C").invoke(null);c.getMethod("func_71263_m").invoke(s);return;
            }
            Path request=Paths.get("checks/request.properties");if(!Files.exists(request))return;
            Properties p=new Properties();try(java.io.InputStream in=Files.newInputStream(request)){p.load(in);}Files.delete(request);
            Path output=Paths.get(p.getProperty("output")).toAbsolutePath();
            try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(p.getProperty("jar")).toUri().toURL()},getClass().getClassLoader())) {
                loader.loadClass(p.getProperty("class","runtimechecks.NativeChecks")).getMethod("run",String.class).invoke(null,output.toString());
                System.out.println("RUNTIME_CHECKS_DONE "+output);
            } catch(Throwable error) {error.printStackTrace();Files.write(Paths.get(output+".error"),error.toString().getBytes("UTF-8"));}
        } catch(Throwable error){error.printStackTrace();}
    }
}
