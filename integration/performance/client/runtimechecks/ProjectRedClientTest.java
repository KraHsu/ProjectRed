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
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;

import org.lwjgl.opengl.GL11;

import com.google.gson.GsonBuilder;

import codechicken.lib.vec.BlockCoord;
import codechicken.lib.vec.Vector3;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mrtjp.projectred.ProjectRedTransportation$;
import mrtjp.projectred.transportation.BasicPipePart;
import mrtjp.projectred.transportation.PayloadPipePart;
import mrtjp.projectred.transportation.PressurePayload;
import mrtjp.projectred.transportation.PressureTube;
import mrtjp.projectred.transportation.RenderPipe$;
import mrtjp.projectred.transportation.RoutedJunctionPipePart;
import mrtjp.projectred.transportation.SubcorePipePart;

/** Runs a real client and local integrated server. Excluded from release artifacts. */
@Mod(modid = "prclienttest", name = "Isolated ProjectRed Client Test", version = "1", acceptableRemoteVersions = "*")
public final class ProjectRedClientTest {

    private final Map<String, Object> results = new LinkedHashMap<String, Object>();
    private final File output = new File(System.getProperty("projectred.testOutput", "build/client-test"));
    private volatile int phase = 0;
    private int serverTicks, frames, seenActive, emptyChecks;
    private PressureTube first;
    private volatile Throwable failure;
    private long started = System.currentTimeMillis();
    private boolean cold;
    private boolean activeScreenshot;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("projectred.selfTest")) FMLCommonHandler.instance().bus().register(this);
    }

    private static void check(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }

    private SubcorePipePart pipe(WorldServer w, int x, int z, SubcorePipePart p, int meta, boolean legacy) {
        p.preparePlacement(0, meta);
        if (legacy) {
            NBTTagCompound n = new NBTTagCompound();
            p.save(n);
            n.removeTag("manualConnections");
            p.load(n);
        }
        if ((Object) p instanceof PressureTube) ((PressureTube) (Object) p).colour_$eq((byte) 14);
        if ((Object) p instanceof PressureTube) ((PressureTube) (Object) p).material_$eq(true);
        TileMultipart.addPart(w, new BlockCoord(x, 65, z), p);
        return p;
    }

    private SubcorePipePart get(net.minecraft.world.World w, int x, int z) {
        Object tile = w.getTileEntity(x, 65, z);
        return tile instanceof TileMultipart ? (SubcorePipePart) ((TileMultipart) tile).partMap(6) : null;
    }

    private int coal(WorldServer w) {
        TileEntityChest chest = (TileEntityChest) w.getTileEntity(8, 65, 2);
        int n = 0;
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            ItemStack s = chest.getStackInSlot(i);
            if (s != null && s.getItem() == Items.coal) n += s.stackSize;
        }
        return n;
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase < 1 || phase > 2) return;
        try {
            WorldServer w = DimensionManager.getWorld(0);
            MinecraftServer server = MinecraftServer.getServer();
            if (w == null || server.getConfigurationManager().playerEntityList.isEmpty()) return;
            if (serverTicks++ == 0) {
                EntityPlayerMP p = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
                if (!cold) {
                    for (int x = -3; x <= 18; x++) for (int z = -5; z <= 19; z++) {
                        for (int y = 65; y <= 69; y++) w.setBlockToAir(x, y, z);
                        w.setBlock(x, 64, z, Blocks.stone);
                    }
                    w.setWorldTime(6000);
                    w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                    w.getWorldInfo().setRaining(false);
                    w.setBlock(2, 64, 2, Blocks.redstone_block);
                    for (int color = 0; color < 16; color++) {
                        PressureTube sample = new PressureTube();
                        // Set the sample before registration so the initial description contains its color.
                        ((SubcorePipePart) (Object) sample).preparePlacement(0, 7);
                        sample.colour_$eq((byte) color);
                        sample.material_$eq(true);
                        TileMultipart.addPart(w, new BlockCoord(color, 65, 4), (TMultiPart) (Object) sample);
                    }
                    w.setBlock(1, 65, 2, Blocks.chest);
                    w.setBlock(8, 65, 2, Blocks.chest);
                    for (int x = 2; x <= 7; x++) {
                        // Scala 2.11 emits an erased generic superclass signature here.
                        PressureTube t = (PressureTube) (Object) pipe(
                                w,
                                x,
                                2,
                                (SubcorePipePart) (Object) new PressureTube(),
                                7,
                                true);
                        t.colour_$eq((byte) 14);
                        if (x == 2) first = t;
                    }
                    for (int z = 6; z <= 14; z++) for (int x = 0; x <= 15; x++) {
                        if (z == 14 && x < 12) pipe(w, x, z, new RoutedJunctionPipePart(), 1, true);
                        else pipe(w, x, z, new BasicPipePart(), 0, true);
                    }
                    SubcorePipePart closed = pipe(w, 11, 2, new BasicPipePart(), 0, false);
                    pipe(w, 12, 2, new BasicPipePart(), 0, false);
                    check((closed.clientConnMap() & 1 << 5) != 0, "Adjacent pipes did not connect");
                    results.put("native_automatic_connections", true);
                } else {
                    check(coal(w) == 64, "Delivered inventory not saved");
                    check(((PressureTube) (Object) get(w, 2, 2)).colour() == 14, "Wool color not saved");
                    check((get(w, 11, 2).clientConnMap() & 1 << 5) != 0, "Automatic connection not saved");
                    results.put("cold_reload_inventory_color_connections", true);
                }
                p.inventory.setInventorySlotContents(
                        0,
                        new ItemStack(ProjectRedTransportation$.MODULE$.itemPartPipe(), 1, 7));
                p.playerNetServerHandler.setPlayerLocation(7.5, 69, -4, 0, 25);
            }
            if (!cold && serverTicks >= 20 && serverTicks <= 200 && serverTicks % 20 == 0) {
                PressurePayload item = new PressurePayload(30000 + serverTicks);
                item.setItemStack(new ItemStack(Items.coal, serverTicks == 200 ? 55 : 1));
                item.colour_$eq((byte) 14);
                first.injectPayload(item, 5);
            }
            if (serverTicks == 380 && !cold) {
                check(coal(w) == 64, "Transport lost or duplicated items: " + coal(w));
                results.put("transport_64_coal_exactly_once", true);
                phase = 3;
            }
            if (cold && serverTicks == 60) phase = 7;
        } catch (Throwable t) {
            failure = t;
            phase = 99;
        }
    }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (failure != null) throw new RuntimeException("Server check failed", failure);
            if (System.currentTimeMillis() - started > 240000)
                throw new AssertionError("Client test timed out at phase " + phase);
            if (phase == 0 && mc.currentScreen instanceof GuiMainMenu) {
                output.mkdirs();
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.enableVsync = false;
                mc.gameSettings.renderDistanceChunks = 4;
                mc.gameSettings.limitFramerate = 120;
                phase = 1;
                WorldSettings settings = new WorldSettings(
                        1381784,
                        WorldSettings.GameType.CREATIVE,
                        false,
                        false,
                        WorldType.FLAT);
                settings.enableCommands();
                mc.launchIntegratedServer("PerformanceContributionTest", "ProjectRed isolated test", settings);
            }
            if (mc.theWorld != null && phase <= 3 && phase >= 1) {
                Object p = get(mc.theWorld, 2, 2);
                if (p instanceof PressureTube) {
                    PressureTube tube = (PressureTube) p;
                    if (!((PayloadPipePart) (Object) tube).itemFlow().delegate().isEmpty()) seenActive++;
                    check(tube.colour() == 14, "Client did not receive wool overlay");
                    check(tube.material(), "Client did not receive redstone material");
                    phase = Math.max(phase, 2);
                }
            }
            if (phase == 3) {
                check(seenActive > 0, "Client never received/rendered transported items");
                results.put("client_ticks_with_synced_payloads", seenActive);
                check(activeScreenshot, "No active payload screenshot captured");
                results.put("real_integrated_world", true);
                for (int color = 0; color < 16; color++) {
                    Object sample = get(mc.theWorld, color, 4);
                    check(
                            sample instanceof PressureTube && ((PressureTube) sample).colour() == color,
                            "Client wool sample missing color " + color);
                }
                check(
                        mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem()
                                == ProjectRedTransportation$.MODULE$.itemPartPipe(),
                        "Inventory pipe renderer fixture missing");
                results.put("wool_colors_received", 16);
                results.put("redstone_and_inventory_render_fixture", true);
                mc.refreshResources();
                results.put("resource_reload_completed", true);
                phase = 4;
                frames = 0;
            } else if (phase == 5) {
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMainMenu());
                phase = 6;
                frames = 0;
            } else if (phase == 6 && ++frames > 40 && !mc.isIntegratedServerRunning()) {
                cold = true;
                serverTicks = 0;
                phase = 1;
                mc.launchIntegratedServer("PerformanceContributionTest", "ProjectRed isolated test", null);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (mc.theWorld != null && phase >= 1 && phase <= 4) {
                SubcorePipePart p = get(mc.theWorld, 0, 6);
                if (p instanceof PayloadPipePart) {
                    boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                    RenderPipe$.MODULE$.renderItemFlow((PayloadPipePart) p, new Vector3(), 0);
                    boolean optimized = "followups".equals(System.getProperty("projectred.testVariant"));
                    check(GL11.glIsEnabled(GL11.GL_LIGHTING) != optimized, "Unexpected empty-flow lighting behavior");
                    check(depth == GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH), "Empty flow changed matrix stack");
                    emptyChecks++;
                    if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
                }
            }
            if (phase == 2 && !activeScreenshot) {
                Object tube = get(mc.theWorld, 2, 2);
                if (tube instanceof PayloadPipePart && !((PayloadPipePart) tube).itemFlow().delegate().isEmpty()) {
                    screenshot(mc, "active-pipes.png");
                    activeScreenshot = true;
                }
            }
            if (phase == 4 && ++frames > 60) {
                screenshot(mc, "after-resource-reload.png");
                results.put("empty_render_state_checks", emptyChecks);
                results.put("variant", System.getProperty("projectred.testVariant"));
                results.put("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
                results.put("gl_version", GL11.glGetString(GL11.GL_VERSION));
                phase = 5;
            }
            if (phase == 7) {
                screenshot(mc, "after-world-reload.png");
                results.put("status", "passed");
                write();
                phase = 8;
                mc.shutdown();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void screenshot(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(output, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private void write() throws Exception {
        Files.write(
                new File(output, "client-results.json").toPath(),
                new GsonBuilder().setPrettyPrinting().create().toJson(results).getBytes(StandardCharsets.UTF_8));
    }

    private void fail(Throwable t) {
        if (phase == 100) return;
        t.printStackTrace();
        phase = 100;
        results.put("status", "failed");
        results.put("error", t.toString());
        if (t.getCause() != null) results.put("cause", t.getCause().toString());
        try {
            output.mkdirs();
            write();
        } catch (Exception ignored) {}
        Minecraft.getMinecraft().shutdown();
    }
}
