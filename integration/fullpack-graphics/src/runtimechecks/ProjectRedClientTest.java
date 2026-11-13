package runtimechecks;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;
import org.lwjgl.opengl.GL11;
import com.google.gson.GsonBuilder;
import codechicken.lib.vec.BlockCoord;
import codechicken.lib.vec.Vector3;
import codechicken.multipart.TileMultipart;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mrtjp.projectred.ProjectRedTransportation$;
import mrtjp.projectred.transportation.BasicPipePart;
import mrtjp.projectred.transportation.PayloadPipePart;
import mrtjp.projectred.transportation.RenderPipe$;
import mrtjp.projectred.transportation.RoutedJunctionPipePart;
import mrtjp.projectred.transportation.SubcorePipePart;

/** Real full-pack world; no production world or process is touched. */
public final class ProjectRedClientTest {
    final Map<String,Object> result=new LinkedHashMap<>();
    final File out=new File(System.getProperty("projectred.testOutput"));
    volatile int phase;
    volatile Throwable failure;
    int ticks,frames,seenActive,emptyChecks;
    boolean cold,activeScreenshot;
    long start=System.currentTimeMillis();
    public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static SubcorePipePart pipe(net.minecraft.world.World w,int x,int z){Object t=w.getTileEntity(x,65,z);return t instanceof TileMultipart?(SubcorePipePart)((TileMultipart)t).partMap(6):null;}
    void recipes(){
        int[] counts=new int[11];int bad=0;
        for(Object raw:CraftingManager.getInstance().getRecipeList())try{
            ItemStack s=((IRecipe)raw).getRecipeOutput();
            if(s!=null && s.getItem()==ProjectRedTransportation$.MODULE$.itemPartPipe() && s.getItemDamage()>=0 && s.getItemDamage()<11)counts[s.getItemDamage()]++;
        }catch(Throwable ignored){bad++;}
        Map<Integer,Integer> map=new LinkedHashMap<>();for(int i=0;i<11;i++)map.put(i,counts[i]);
        result.put("fullpack_pipe_crafting_recipe_counts",map);result.put("unrelated_recipe_output_errors",bad);
        result.put("wool_overlay_scope","Excluded from full-pack pass criteria; previously covered in development regression tests");
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || phase<1 || phase>2)return;
        try{
            WorldServer w=DimensionManager.getWorld(0);MinecraftServer s=MinecraftServer.getServer();
            if(w==null || s.getConfigurationManager().playerEntityList.isEmpty())return;
            EntityPlayerMP p=(EntityPlayerMP)s.getConfigurationManager().playerEntityList.get(0);
            if(ticks++==0){
                for(int cx=-2;cx<=1;cx++)for(int cz=-1;cz<=1;cz++)w.theChunkProviderServer.loadChunk(cx,cz);
                if(!cold){
                    w.getGameRules().setOrCreateGameRule("doMobSpawning","false");w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");w.setWorldTime(6000);w.getWorldInfo().setRaining(false);
                    for(int x=-3;x<=18;x++)for(int z=-5;z<=18;z++){for(int y=65;y<=70;y++)w.setBlockToAir(x,y,z);w.setBlock(x,64,z,Blocks.stone);}
                    for(int z=6;z<=14;z++)for(int x=0;x<16;x++){
                        SubcorePipePart part=z==14?new RoutedJunctionPipePart():new BasicPipePart();part.preparePlacement(0,z==14?1:0);TileMultipart.addPart(w,new BlockCoord(x,65,z),part);
                    }
                    for(int x=0;x<16;x++){BasicPipePart part=new BasicPipePart();part.preparePlacement(0,0);part.material_$eq(true);TileMultipart.addPart(w,new BlockCoord(x,65,4),part);}
                    w.setBlock(0,64,4,Blocks.redstone_block);
                    result.put("world_basic_and_junction_samples",144);result.put("redstone_overlay_samples",16);recipes();
                }
                DrawerScene.setupOrCheck(w,p,cold);
                p.inventory.setInventorySlotContents(0,new ItemStack(ProjectRedTransportation$.MODULE$.itemPartPipe(),1,0));
                p.capabilities.isFlying=true;p.sendPlayerAbilities();p.playerNetServerHandler.setPlayerLocation(cold?7.5:-19,70,cold?-4:-8,0,25);
            }
            if(ticks==40)DrawerScene.initialize(w,cold);
            if(!cold && ticks==220)DrawerScene.stopExtraction();
            if(!cold && ticks==420){DrawerScene.verify(w);p.playerNetServerHandler.setPlayerLocation(7.5,70,-4,0,25);phase=3;}
            if(cold && ticks==100){DrawerScene.verify(w);phase=7;}
        }catch(Throwable t){failure=t;phase=99;}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        try{
            if(failure!=null)throw new RuntimeException("Server check failed",failure);
            if(System.currentTimeMillis()-start>900000)throw new AssertionError("Scene timeout at "+phase);
            if(phase==0 && FullpackSuite.isMenu(mc)){
                out.mkdirs();mc.gameSettings.guiScale=1;mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.enableVsync=false;mc.gameSettings.limitFramerate=120;mc.gameSettings.renderDistanceChunks=4;
                WorldSettings settings=new WorldSettings(1381784,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT);settings.enableCommands();phase=1;mc.launchIntegratedServer("PerformanceContributionTest","ProjectRed isolated test",settings);
            }
            if(mc.theWorld!=null && phase>=1 && phase<=2){SubcorePipePart p=pipe(mc.theWorld,-19,0);if(p instanceof PayloadPipePart && !((PayloadPipePart)p).itemFlow().delegate().isEmpty())seenActive++;if(p!=null)phase=2;}
            if(phase==3){
                check(seenActive>0 && activeScreenshot,"Client did not observe native item transport");
                for(int x=0;x<16;x++){SubcorePipePart p=pipe(mc.theWorld,x,4);check(p instanceof BasicPipePart && ((BasicPipePart)p).material(),"Missing redstone overlay at "+x);}
                result.put("native_item_flow_client_ticks",seenActive);result.put("redstone_overlay_client_sync",true);mc.refreshResources();result.put("resource_reload_completed",true);phase=4;frames=0;
            }else if(phase==5){mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());phase=6;frames=0;}
            else if(phase==6 && ++frames>40 && !mc.isIntegratedServerRunning()){cold=true;ticks=0;phase=1;mc.launchIntegratedServer("PerformanceContributionTest","ProjectRed isolated test",null);}
        }catch(Throwable t){fail(t);}
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        try{
            if(mc.theWorld!=null && phase>=1 && phase<=4){SubcorePipePart p=pipe(mc.theWorld,0,6);if(p instanceof PayloadPipePart){
                boolean light=GL11.glIsEnabled(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_LIGHTING);int depth=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                RenderPipe$.MODULE$.renderItemFlow((PayloadPipePart)p,new Vector3(),0);
                check(GL11.glIsEnabled(GL11.GL_LIGHTING)!= "followups".equals(System.getProperty("projectred.testVariant")),"Unexpected empty-flow lighting state");check(depth==GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),"Empty flow changed matrix depth");emptyChecks++;if(light)GL11.glEnable(GL11.GL_LIGHTING);
            }}
            if(phase==2 && !activeScreenshot){SubcorePipePart p=pipe(mc.theWorld,-19,0);if(p instanceof PayloadPipePart && !((PayloadPipePart)p).itemFlow().delegate().isEmpty()){screenshot(mc,"active-pipes.png");activeScreenshot=true;}}
            if(phase==4 && ++frames>80){screenshot(mc,"after-resource-reload.png");result.put("empty_render_state_checks",emptyChecks);phase=5;}
            if(phase==7){for(int x=0;x<16;x++)check(((BasicPipePart)pipe(mc.theWorld,x,4)).material(),"Redstone material not saved");screenshot(mc,"after-world-reload.png");result.put("save_reopen_redstone_and_transport",true);result.put("status","passed");write();phase=8;FullpackSuite.sceneCompleted();}
        }catch(Throwable t){fail(t);}
    }
    void screenshot(Minecraft mc,String name){ScreenShotHelper.saveScreenshot(out,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
    void write()throws Exception{Files.write(new File(out,"client-results.json").toPath(),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}
    void fail(Throwable t){if(phase==100)return;t.printStackTrace();phase=100;result.put("status","failed");result.put("error",t.toString());if(t.getCause()!=null)result.put("cause",t.getCause().toString());try{out.mkdirs();write();}catch(Exception ignored){}Minecraft.getMinecraft().shutdown();}
}
