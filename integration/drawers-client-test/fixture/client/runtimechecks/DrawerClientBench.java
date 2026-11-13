package runtimechecks;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import com.google.gson.GsonBuilder;
import com.jaquadro.minecraft.storagedrawers.StorageDrawers;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.core.ModBlocks;
import com.jaquadro.minecraft.storagedrawers.core.ModItems;
import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.LockAttribute;
import com.jaquadro.minecraft.storagedrawers.network.CountUpdateMessage;
import codechicken.lib.vec.BlockCoord;
import codechicken.multipart.TileMultipart;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.*;
import mrtjp.core.inventory.InvWrapper;
import mrtjp.core.item.ItemKey;
import mrtjp.projectred.ProjectRedTransportation$;
import mrtjp.projectred.transportation.*;

/** Real controllers, real drawer blocks, real packets, real PR chips in an isolated integrated world. */
@Mod(modid="drawerclientbench",name="Isolated Drawer Client Benchmark",version="1",dependencies="after:StorageDrawers;after:ProjRed|Compatibility",acceptableRemoteVersions="*")
public final class DrawerClientBench {
    private static final String VARIANT=System.getProperty("drawertest.variant","baseline");
    private final File output=new File(System.getProperty("drawertest.output"));
    private final int duration=Integer.getInteger("drawertest.ticks",400);
    private final String worldName="DrawerBench_"+VARIANT+"_"+System.currentTimeMillis();
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Metrics> completed=new ArrayList<>();
    private final ThreadMXBean cpu=ManagementFactory.getThreadMXBean();
    private long tickWall,tickCpu,start=System.currentTimeMillis(),lastFrame;
    private volatile Metrics active;
    private final AtomicLong allEmitted=new AtomicLong();
    private final Object packetLock=new Object();
    private final ArrayDeque<Metrics> packetOwners=new ArrayDeque<>();
    private final Metrics unmeasured=new Metrics("unmeasured");
    private volatile boolean countersReady;
    private volatile Throwable failure;
    private volatile boolean finished;
    private boolean launched;
    private int stage,ticks,pulses;
    private TileEntityController source,target;
    private InvWrapper sourceWrapper,targetWrapper;
    private RoutedInterfacePipePart sourcePipe,targetPipe;
    private ItemKey coal;
    private int initialTotal,pulseSource,pulseTarget;
    private static final class Metrics {
        final String label;
        final List<Double> wall=new ArrayList<>(),cpu=new ArrayList<>();
        final ConcurrentLinkedQueue<Double> frames=new ConcurrentLinkedQueue<>();
        final AtomicLong emitted=new AtomicLong(),received=new AtomicLong(),payloadBytes=new AtomicLong(),focusedFrames=new AtomicLong();
        int sourceStart,targetStart,sourceEnd,targetEnd;
        volatile boolean screenshot;
        Metrics(String label){this.label=label;}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent e)throws Exception {
        Field f=SimpleNetworkWrapper.class.getDeclaredField("channels");f.setAccessible(true);
        EnumMap<Side,FMLEmbeddedChannel> channels=(EnumMap<Side,FMLEmbeddedChannel>)f.get(StorageDrawers.network);
        channels.get(Side.SERVER).pipeline().addLast("drawer-bench-count",new ChannelOutboundHandlerAdapter(){
            @Override public void write(ChannelHandlerContext ctx,Object msg,ChannelPromise promise)throws Exception {
                if(msg instanceof CountUpdateMessage){
                    allEmitted.incrementAndGet();Metrics m=active;if(m!=null)m.emitted.incrementAndGet();
                    if(countersReady)synchronized(packetLock){packetOwners.addLast(m==null?unmeasured:m);}
                }ctx.write(msg,promise);
            }
        });
        channels.get(Side.CLIENT).pipeline().addFirst("drawer-bench-receive",new ChannelInboundHandlerAdapter(){
            @Override public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception {
                if(countersReady&&msg instanceof FMLProxyPacket){FMLProxyPacket p=(FMLProxyPacket)msg;
                    if(p.payload().isReadable()&&p.payload().getUnsignedByte(p.payload().readerIndex())==1){
                        Metrics owner;synchronized(packetLock){owner=packetOwners.pollFirst();}
                        if(owner!=null&&owner!=unmeasured){owner.received.incrementAndGet();owner.payloadBytes.addAndGet(p.payload().readableBytes());}
                    }}
                ctx.fireChannelRead(msg);
            }
        });
        FMLCommonHandler.instance().bus().register(this);
    }
    private RoutedInterfacePipePart pipe(WorldServer w,int x) {
        RoutedInterfacePipePart p=new RoutedInterfacePipePart();p.preparePlacement(0,2);
        TileMultipart.addPart(w,new BlockCoord(x,65,0),p);return p;
    }
    private TileEntityController controller(WorldServer w,int x,boolean reverse) {
        w.setBlock(x,65,0,ModBlocks.controller,0,2);
        TileEntityController c=(TileEntityController)w.getTileEntity(x,65,0);c.setDirection(2);
        for(int i=0;i<50;i++) {
            int xx=x+(reverse?-i%5:i%5),yy=65+(i/5)%5,zz=1+i/25;
            require(((ItemBlock)Item.getItemFromBlock(ModBlocks.fullDrawers4)).placeBlockAt(new ItemStack(ModBlocks.fullDrawers4),(EntityPlayerMP)MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(0),w,xx,yy,zz,2,.5f,.5f,.5f,0),"Native drawer placement failed");
            TileEntityDrawers d=(TileEntityDrawers)w.getTileEntity(xx,yy,zz);d.setDirection(2);
        }
        c.updateCache();require(c.getDrawerCount()==200,"Expected 50 four-slot drawers, got "+c.getDrawerCount());return c;
    }
    private void resetStock() {
        for(int i=0;i<200;i++) {
            source.getDrawer(i).setStoredItemRedir(new ItemStack(Items.coal),256);
            target.getDrawer(i).setStoredItemRedir(new ItemStack(Items.coal),1);
        }
        source.updateCache();target.updateCache();
        require(count(source)==51200&&count(target)==200,"Fixture stock capacity differs");
        initialTotal=51400;
    }
    private static int count(TileEntityController c) {int n=0;for(int i=0;i<c.getDrawerCount();i++)n+=c.getDrawer(i).getStoredItemCount();return n;}
    private void regressions(WorldServer w) {
        Map<String,Object> checks=new LinkedHashMap<>();
        long messages=allEmitted.get();
        require(sourceWrapper.extractItem(coal,0)==0&&targetWrapper.injectItem(coal,0)==0,"Zero request returned items");
        require(count(source)+count(target)==51400,"Zero request changed stocks");
        checks.put("zero_request_count_updates",allEmitted.get()-messages);
        ItemStack tagged=new ItemStack(Items.coal);NBTTagCompound tag=new NBTTagCompound();tag.setString("drawerBench","different");tagged.setTagCompound(tag);
        require(sourceWrapper.extractItem(ItemKey.get(tagged),1)==0,"NBT mismatch extracted coal");
        require(sourceWrapper.extractItem(ItemKey.get(new ItemStack(Items.coal,1,1)),1)==0,"Metadata mismatch extracted coal");
        checks.put("nbt_and_metadata_mismatch",true);
        source.getDrawer(0).setStoredItemCount(5);
        int before=count(source);
        require(sourceWrapper.extractItem(coal,17)==17&&count(source)==before-17,"Multi-drawer extraction changed totals");
        checks.put("cross_drawer_partial_extraction",true);resetStock();
        TileEntityDrawers st=(TileEntityDrawers)w.getTileEntity(0,65,1),tt=(TileEntityDrawers)w.getTileEntity(-4,65,1);
        InvWrapper sw=InvWrapper.wrap(st),tw=InvWrapper.wrap(tt);
        tt.getDrawer(0).setStoredItemRedir(null,0);int old=tt.getDrawer(1).getStoredItemCount();
        require(tw.injectItem(coal,17)==17&&tt.getDrawer(0).isEmpty()&&tt.getDrawer(1).getStoredItemCount()==old+17,"Insertion did not prefer populated drawer");
        checks.put("populated_before_empty_insertion",true);resetStock();
        st.setLocked(LockAttribute.LOCK_POPULATED,true);st.getDrawer(0).setStoredItemCount(5);
        require(sw.extractItem(coal,5)==5&&st.getDrawer(0).getStoredItemCount()==0&&st.getDrawer(0).getStoredItemPrototype()!=null,"Locked empty prototype lost");
        st.setLocked(LockAttribute.LOCK_POPULATED,false);checks.put("locked_prototype_preserved",true);resetStock();
        tt.setUpgrade(0,new ItemStack(ModItems.upgradeVoid));
        int total=0;for(int i=0;i<4;i++){tt.getDrawer(i).setStoredItemCount(tt.getDrawer(i).getMaxCapacity());total+=tt.getDrawer(i).getStoredItemCount();}
        require(tw.injectItem(coal,17)==17,"Void did not consume overflow");int after=0;for(int i=0;i<4;i++)after+=tt.getDrawer(i).getStoredItemCount();require(after==total,"Void overflow altered retained stocks");
        tt.setUpgrade(0,null);checks.put("void_overflow",true);resetStock();
        st.setUpgrade(0,new ItemStack(ModItems.upgradeCreative,1,1));
        require(st.getDrawer(0).isVendingUnlimited()&&sw.extractItem(coal,17)==17&&st.getDrawer(0).getStoredItemCount()==Integer.MAX_VALUE,"Vending semantics changed");
        st.setUpgrade(0,null);checks.put("vending_extraction",true);resetStock();
        checks.put("compacting_drawers_enabled",StorageDrawers.config.isBlockEnabled("compdrawers"));
        if(Item.getItemFromBlock(ModBlocks.compDrawers)!=null) {
            ItemStack comp=new ItemStack(ModBlocks.compDrawers);
            require(((ItemBlock)comp.getItem()).placeBlockAt(comp,(EntityPlayerMP)MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(0),w,12,65,-8,2,.5f,.5f,.5f,0),"Compacting fixture placement failed");
            TileEntityDrawers cd=(TileEntityDrawers)w.getTileEntity(12,65,-8);cd.getDrawer(0).setStoredItemRedir(new ItemStack(Items.gold_ingot),100);
            InvWrapper cw=InvWrapper.wrap(cd);ItemKey gold=ItemKey.get(new ItemStack(Items.gold_ingot));int goldBefore=cw.getItemCount(gold);
            require(goldBefore>=17&&cw.extractItem(gold,17)==17&&cw.getItemCount(gold)==goldBefore-17,"Compacting extraction mismatch");
            require(cw.injectItem(gold,17)==17&&cw.getItemCount(gold)==goldBefore,"Compacting insertion mismatch");
            cd.getDrawer(0).setStoredItemRedir(null,0);w.setBlockToAir(12,65,-8);checks.put("compacting_gold_conversion",true);
        }
        checks.put("ore_conversion_enabled",StorageDrawers.config.cache.enableItemConversion);
        resetStock();report.put("regressions",checks);
    }
    private void chip(RoutedInterfacePipePart p,boolean extractor) {
        RoutingChip ch;
        if(extractor){ChipExtractor c=new ChipExtractor();c.filterExclude_$eq(true);ch=c;}
        else{ChipItemResponder c=new ChipItemResponder();c.filterExclude_$eq(true);ch=c;}
        ItemStack s=new ItemStack(ProjectRedTransportation$.MODULE$.itemRoutingChip(),1,extractor?4:0);
        ItemRoutingChip$.MODULE$.saveChipToItemStack(s,ch);p.chipSlots().setInventorySlotContents(0,s);
    }
    private void begin(String label) {
        Metrics m=new Metrics(label);m.sourceStart=count(source);m.targetStart=count(target);active=m;ticks=0;lastFrame=0;
    }
    private void end() {
        Metrics m=active;m.sourceEnd=count(source);m.targetEnd=count(target);active=null;completed.add(m);ticks=0;
        System.out.println("DRAWER_PHASE_DONE "+VARIANT+" "+m.label+" ticks="+m.wall.size()+" emitted="+m.emitted.get());
        try{Map<String,Object> progress=new LinkedHashMap<>();progress.put("variant",VARIANT);progress.put("completed_phase",m.label);progress.put("server_cpu",stats(m.cpu));progress.put("server_wall",stats(m.wall));progress.put("count_updates_emitted",m.emitted.get());Files.write(new File(output,"progress.json").toPath(),new GsonBuilder().setPrettyPrinting().create().toJson(progress).getBytes(StandardCharsets.UTF_8));}catch(Exception x){throw new RuntimeException(x);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void serverStart(TickEvent.ServerTickEvent e) {
        if(e.phase==TickEvent.Phase.START){tickWall=System.nanoTime();tickCpu=cpu.getCurrentThreadCpuTime();}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void serverEnd(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!launched||finished)return;
        try {
            WorldServer w=DimensionManager.getWorld(0);
            MinecraftServer s=MinecraftServer.getServer();
            if(w==null||s.getConfigurationManager().playerEntityList.isEmpty())return;
            if(stage==0) {
                w.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");w.setWorldTime(6000);w.getWorldInfo().setRaining(false);
                for(int x=-10;x<=6;x++)for(int z=-5;z<=4;z++){for(int y=65;y<=70;y++)w.setBlockToAir(x,y,z);w.setBlock(x,64,z,Blocks.stone);}
                source=controller(w,0,false);target=controller(w,-4,true);resetStock();
                sourcePipe=pipe(w,-1);targetPipe=pipe(w,-3);
                BasicPipePart bridge=new BasicPipePart();bridge.preparePlacement(0,0);TileMultipart.addPart(w,new BlockCoord(-2,65,0),bridge);
                sourceWrapper=InvWrapper.wrap(source);targetWrapper=InvWrapper.wrap(target);
                require(sourceWrapper.getClass().getName().contains("StorageDrawersInvWrapper"),"StorageDrawers compatibility wrapper not selected: "+sourceWrapper.getClass());
                coal=ItemKey.get(new ItemStack(Items.coal));regressions(w);
                w.scheduleBlockUpdate(0,65,0,ModBlocks.controller,100);w.scheduleBlockUpdate(-4,65,0,ModBlocks.controller,100);
                EntityPlayerMP p=(EntityPlayerMP)s.getConfigurationManager().playerEntityList.get(0);
                p.capabilities.isFlying=true;p.sendPlayerAbilities();p.playerNetServerHandler.setPlayerLocation(-2,70,-8,0,25);countersReady=true;
                report.put("wrapper",sourceWrapper.getClass().getName());report.put("controller_range",StorageDrawers.config.getControllerRange());report.put("controller_max_drawer_blocks",StorageDrawers.config.getControllerMaxDrawers());
                stage=1;ticks=0;return;
            }
            ticks++;
            if(stage==1&&ticks>=160){begin("idle");stage=2;return;}
            if(stage==2){sample();if(ticks>=duration){end();resetStock();stage=3;}return;}
            if(stage==3&&ticks>=80){pulseSource=count(source);pulseTarget=count(target);begin("one_item_per_tick");stage=4;return;}
            if(stage==4) {
                int got=sourceWrapper.extractItem(coal,1);int put=targetWrapper.injectItem(coal,got);
                require(got==1&&put==1,"One-item transfer failed");pulses++;sample();
                if(ticks>=duration){require(count(source)==pulseSource-duration&&count(target)==pulseTarget+duration,"Pulse count mismatch");end();resetStock();chip(targetPipe,false);chip(sourcePipe,true);stage=5;}
                return;
            }
            if(stage==5&&ticks>=100){require(sourcePipe.getInventory()==source&&targetPipe.getInventory()==target,"Interfaces bound to wrong inventory");begin("native_extractor_chip");stage=6;return;}
            if(stage==6){sample();if(ticks>=duration){end();sourcePipe.chipSlots().setInventorySlotContents(0,null);stage=7;}return;}
            if(stage==7&&ticks>=160) {
                require(count(source)+count(target)==initialTotal,"Native pipe transport lost or duplicated coal: "+(count(source)+count(target)));
                require(count(target)>200,"Native extractor never delivered");
                report.put("final_source_coal",count(source));report.put("final_target_coal",count(target));
                report.put("native_final_total",initialTotal);report.put("one_item_pulses",pulses);
                synchronized(packetLock){require(packetOwners.isEmpty(),"Count-update receive queue did not drain: "+packetOwners.size());}
                for(Metrics m:completed)require(m.emitted.get()==m.received.get(),"Client missed attributed count updates in "+m.label);
                report.put("status","passed");stage=8;finished=true;
            }
        }catch(Throwable t){t.printStackTrace();failure=t;finished=true;}
    }
    private void sample() {
        Metrics m=active;if(m==null)return;
        m.wall.add((System.nanoTime()-tickWall)/1e6);m.cpu.add((cpu.getCurrentThreadCpuTime()-tickCpu)/1e6);
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        try {
            if(!launched&&mc.currentScreen instanceof GuiMainMenu) {
                output.mkdirs();mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.enableVsync=false;mc.gameSettings.limitFramerate=260;mc.gameSettings.renderDistanceChunks=4;mc.gameSettings.fancyGraphics=false;
                launched=true;WorldSettings settings=new WorldSettings(492937,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT);settings.enableCommands();mc.launchIntegratedServer(worldName,"Drawer Client Benchmark",settings);
            }
            if(mc.thePlayer!=null&&mc.theWorld!=null){mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=25;mc.thePlayer.motionX=mc.thePlayer.motionY=mc.thePlayer.motionZ=0;}
            if(System.currentTimeMillis()-start>300000)throw new AssertionError("Benchmark timeout stage "+stage);
            if(failure!=null)throw new RuntimeException("Server fixture failed",failure);
            if(finished) {
                TileEntityController sc=(TileEntityController)mc.theWorld.getTileEntity(0,65,0),tc=(TileEntityController)mc.theWorld.getTileEntity(-4,65,0);
                // Client controller topology is server-side only; read the actual client drawer blocks directly.
                int clientTotal=0;
                for(int origin:new int[]{0,-4})for(int i=0;i<50;i++) {
                    TileEntityDrawers d=(TileEntityDrawers)mc.theWorld.getTileEntity(origin+(origin==0?i%5:-i%5),65+(i/5)%5,1+i/25);
                    require(d!=null,"Client drawer missing");for(int j=0;j<4;j++)clientTotal+=d.getDrawer(j).getStoredItemCount();
                }
                require(clientTotal==initialTotal,"Client quantity synchronization differs: "+clientTotal);
                report.put("client_total_matches",true);write(mc);mc.shutdown();
            }
        }catch(Throwable t){t.printStackTrace();report.put("status","failed");report.put("error",t.toString());if(t.getCause()!=null)report.put("cause",t.getCause().toString());try{write(mc);}catch(Exception ignored){}finished=true;mc.shutdown();}
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();Metrics m=active;
        long now=System.nanoTime();if(m==null||mc.theWorld==null||mc.currentScreen!=null){lastFrame=0;return;}
        if(lastFrame!=0)m.frames.add((now-lastFrame)/1e6);lastFrame=now;if(Display.isActive())m.focusedFrames.incrementAndGet();
        if(!m.screenshot&&m.frames.size()>60){m.screenshot=true;ScreenShotHelper.saveScreenshot(output,m.label+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
    }
    private static Map<String,Object> stats(Collection<Double> values) {
        List<Double> sorted=new ArrayList<>(values);Collections.sort(sorted);Map<String,Object> out=new LinkedHashMap<>();
        double sum=0;for(double x:sorted)sum+=x;out.put("samples",sorted.size());if(sorted.isEmpty())return out;
        out.put("mean_ms",sum/sorted.size());out.put("p50_ms",sorted.get((sorted.size()-1)/2));out.put("p95_ms",sorted.get((sorted.size()-1)*95/100));out.put("p99_ms",sorted.get((sorted.size()-1)*99/100));out.put("max_ms",sorted.get(sorted.size()-1));return out;
    }
    private void write(Minecraft mc)throws Exception {
        output.mkdirs();List<Object> phases=new ArrayList<>();StringBuilder csv=new StringBuilder("phase,frame_ms\n");
        for(Metrics m:completed){Map<String,Object> p=new LinkedHashMap<>();p.put("phase",m.label);p.put("server_tick_wall",stats(m.wall));p.put("server_tick_cpu",stats(m.cpu));p.put("client_frames",stats(m.frames));p.put("focused_frames",m.focusedFrames.get());p.put("count_updates_emitted",m.emitted.get());p.put("count_updates_received",m.received.get());p.put("count_payload_bytes_received",m.payloadBytes.get());p.put("source_start_end",Arrays.asList(m.sourceStart,m.sourceEnd));p.put("target_start_end",Arrays.asList(m.targetStart,m.targetEnd));phases.add(p);for(double f:m.frames)csv.append(m.label).append(',').append(f).append('\n');}
        report.put("variant",VARIANT);report.put("phases",phases);report.put("world",worldName);report.put("java",System.getProperty("java.runtime.version"));report.put("gl_renderer",GL11.glGetString(GL11.GL_RENDERER));report.put("gl_version",GL11.glGetString(GL11.GL_VERSION));report.put("scene_drawer_blocks",100);report.put("scene_logical_drawers",400);report.put("phase_ticks",duration);report.put("view_distance_chunks",4);report.put("vsync",false);report.put("frame_limit_setting",260);report.put("resolution",Arrays.asList(mc.displayWidth,mc.displayHeight));report.put("storage_drawers_version",StorageDrawers.MOD_VERSION);report.put("limits",Arrays.asList("Development client with real drawers, controllers and PR pipes/chips, not full GTNH/Angelica client","Integrated server CPU/wall tick measures and local client frame times; no remote Internet latency measured","Count payload bytes exclude Minecraft framing, compression, IP/TCP and other packet types"));
        Files.write(new File(output,"results.json").toPath(),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));Files.write(new File(output,"frames.csv").toPath(),csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
