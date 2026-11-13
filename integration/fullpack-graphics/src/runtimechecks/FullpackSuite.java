package runtimechecks;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;

import org.lwjgl.opengl.GL11;

import com.google.gson.GsonBuilder;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

@Mod(modid="prfullpackgraphics",name="Isolated Fullpack Graphics Verification",version="1",acceptableRemoteVersions="*",dependencies="after:ProjRed|Transportation;after:StorageDrawers")
public final class FullpackSuite {
    private static FullpackSuite instance;
    private final Map<String,Object> result=new LinkedHashMap<>();
    private final File root=new File(System.getProperty("fullpack.output"));
    private final PipeItemRenderClientTest inventory=new PipeItemRenderClientTest();
    private boolean sceneDone,disconnected,inventoryStarted;
    private int ticks;

    public static boolean isMenu(Minecraft mc){
        return mc.currentScreen instanceof GuiMainMenu || (mc.currentScreen!=null && mc.currentScreen.getClass().getName().equals("lumien.custommainmenu.gui.GuiCustom"));
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event){
        instance=this;
        FMLCommonHandler.instance().bus().register(this);
        new ProjectRedClientTest().init(event);
    }
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event){
        root.mkdirs();
        Map<String,String> mods=new LinkedHashMap<>();
        for(ModContainer mod:Loader.instance().getModList())mods.put(mod.getModId(),mod.getVersion());
        if(!mods.containsKey("angelica"))throw new AssertionError("Full-pack Angelica missing");
        if(mods.size()<200)throw new AssertionError("Incomplete full-pack mod list: "+mods.size());
        result.put("loaded_mods",mods);
        result.put("loaded_mod_count",mods.size());
        result.put("variant",System.getProperty("fullpack.variant"));
        result.put("java",System.getProperty("java.runtime.version"));
        result.put("scope","Official complete GTNH 2.9.0 Java 17-26; original pack mods/config and one isolated verification helper; no production connection");
        inventory.postInit(event);
        write("loaded");
    }
    public static void sceneCompleted(){instance.sceneDone=true;}
    public static void inventoryCompleted(){
        instance.result.put("gl_renderer",GL11.glGetString(GL11.GL_RENDERER));
        instance.result.put("gl_version",GL11.glGetString(GL11.GL_VERSION));
        instance.result.put("scene_transport_overlays_reload",true);
        instance.result.put("inventory_gallery_completed",true);
        instance.result.put("drawer_transport",DrawerScene.results);
        instance.write("passed");
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(!sceneDone)return;
        if(!disconnected){
            ScreenShotHelper.saveScreenshot(root,"scene-completed.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());
            disconnected=true;ticks=0;
        }else if(!inventoryStarted && !mc.isIntegratedServerRunning() && ++ticks>40 && isMenu(mc)){
            inventoryStarted=true;
            inventory.start=System.currentTimeMillis();
            FMLCommonHandler.instance().bus().register(inventory);
        }
    }
    private void write(String status){
        try{result.put("status",status);Files.write(new File(root,"suite.json").toPath(),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
