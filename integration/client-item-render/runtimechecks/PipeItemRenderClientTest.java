package runtimechecks;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.DimensionManager;

import org.lwjgl.opengl.GL11;

import com.google.gson.GsonBuilder;

import codechicken.lib.vec.BlockCoord;
import codechicken.multipart.TileMultipart;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mrtjp.projectred.ProjectRedTransportation$;
import mrtjp.projectred.transportation.SubcorePipePart;

@Mod(
        modid = "pipeitemrenderclienttest",
        name = "Custom Pipe Item Render Probe",
        version = "1",
        acceptableRemoteVersions = "*")
public final class PipeItemRenderClientTest {

    final File out = new File(System.getProperty("projectred.itemTestOutput"));
    final String world = "PipeItemRender_" + System.currentTimeMillis();
    final String[] partNames = { "pr_pipe", "pr_rbasic", "pr_rinterface", "pr_rcrafting", "pr_rrequest",
            "pr_rextension", "pr_rfire", "pr_pt", "pr_rpt", "pr_netvalve", "pr_netlatency" };
    int renderMode = -1;
    final Map<String, Object> results = new LinkedHashMap<>();
    boolean launched;
    volatile int stage;
    int ticks, frames;
    long start = System.currentTimeMillis();
    volatile Throwable failure;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        final IItemRenderer delegate = MinecraftForgeClient
                .getItemRenderer(stack(0), IItemRenderer.ItemRenderType.INVENTORY);
        MinecraftForgeClient
                .registerItemRenderer(ProjectRedTransportation$.MODULE$.itemPartPipe(), new IItemRenderer() {

                    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
                        return delegate.handleRenderType(item, type);
                    }

                    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item,
                            ItemRendererHelper helper) {
                        return delegate.shouldUseRenderHelper(type, item, helper);
                    }

                    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
                        if (type != ItemRenderType.INVENTORY || renderMode < 0) {
                            delegate.renderItem(type, item, data);
                            return;
                        }
                        // Same renderer and geometry; vary only the incoming atlas sampling state.
                        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                        int oldMin = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                        int oldMag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
                        int min = renderMode == 0 ? GL11.GL_NEAREST
                                : renderMode == 1 ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_LINEAR_MIPMAP_LINEAR;
                        int mag = renderMode == 2 ? GL11.GL_LINEAR : GL11.GL_NEAREST;
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
                        boolean hasAnisotropy = org.lwjgl.opengl.GLContext
                                .getCapabilities().GL_EXT_texture_filter_anisotropic;
                        int anisoKey = org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;
                        float oldAnisotropy = hasAnisotropy ? GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, anisoKey)
                                : 1f;
                        float anisotropy = hasAnisotropy ? Math.min(
                                1 << renderMode,
                                GL11.glGetFloat(
                                        org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT))
                                : 1f;
                        if (hasAnisotropy) GL11.glTexParameterf(GL11.GL_TEXTURE_2D, anisoKey, anisotropy);
                        try {
                            delegate.renderItem(type, item, data);
                            if (GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) != min
                                    || GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) != mag
                                    || (hasAnisotropy && Float
                                            .compare(anisotropy, GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, anisoKey))
                                            != 0))
                                throw new AssertionError(
                                        "Atlas sampling state leaked: " + renderMode + ":" + item.getItemDamage());
                            results.put(
                                    "sampler-restored-" + stage + ":" + renderMode + ":" + item.getItemDamage(),
                                    true);
                        } finally {
                            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, oldMin);
                            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, oldMag);
                            if (hasAnisotropy) GL11.glTexParameterf(GL11.GL_TEXTURE_2D, anisoKey, oldAnisotropy);
                        }
                    }
                });
    }

    ItemStack stack(int meta) {
        return new ItemStack(ProjectRedTransportation$.MODULE$.itemPartPipe(), 1, meta);
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || stage != 0 || !launched) return;
        try {
            WorldServer w = DimensionManager.getWorld(0);
            MinecraftServer s = MinecraftServer.getServer();
            if (w == null || s.getConfigurationManager().playerEntityList.isEmpty()) return;
            EntityPlayerMP p = (EntityPlayerMP) s.getConfigurationManager().playerEntityList.get(0);
            w.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
            w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
            w.setWorldTime(6000);
            for (int meta = 0; meta < 11; meta++) {
                p.inventory.setInventorySlotContents(meta, stack(meta));
                int x = (meta % 6) * 2, z = (meta / 6) * 3;
                w.setBlock(x, 64, z, Blocks.stone);
                SubcorePipePart pipe = (SubcorePipePart) codechicken.multipart.MultiPartRegistry
                        .createPart(partNames[meta], false);
                if (pipe != null) {
                    pipe.preparePlacement(0, meta);
                    TileMultipart.addPart(w, new BlockCoord(x, 65, z), pipe);
                }
            }
            p.inventory.setInventorySlotContents(3, stack(0));
            p.inventory.setInventorySlotContents(4, stack(1));
            p.inventory.currentItem = 8;
            p.sendContainerToPlayer(p.inventoryContainer);
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
            p.playerNetServerHandler.setPlayerLocation(4, 69, -8, 0, 25);
            stage = 1;
        } catch (Throwable t) {
            t.printStackTrace();
            failure = t;
            stage = 99;
        }
    }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (!launched && mc.currentScreen instanceof GuiMainMenu) {
                out.mkdirs();
                mc.gameSettings.guiScale = 1;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.enableVsync = false;
                mc.gameSettings.limitFramerate = 120;
                mc.gameSettings.renderDistanceChunks = 4;
                launched = true;
                WorldSettings ws = new WorldSettings(
                        492937,
                        WorldSettings.GameType.SURVIVAL,
                        false,
                        false,
                        WorldType.FLAT);
                ws.enableCommands();
                mc.launchIntegratedServer(world, "Pipe Item Render Test", ws);
            }
            if (failure != null) throw new RuntimeException(failure);
            if (System.currentTimeMillis() - start > 180000) throw new AssertionError("Timeout stage=" + stage);
            if (stage == 1 && ++ticks > 80) {
                ScreenShotHelper
                        .saveScreenshot(out, "hotbar.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                mc.displayGuiScreen(new Gallery());
                stage = 2;
                frames = 0;
                ticks = 0;
            }
            if (stage == 3) {
                mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer));
                stage = 4;
                frames = 0;
            }
            if (stage == 5) {
                mc.refreshResources();
                mc.displayGuiScreen(new Gallery());
                stage = 6;
                frames = 0;
            }
            if (stage == 7) {
                results.put("status", "completed");
                results.put(
                        "scope",
                        "Real Forge development client: native Forge inventory item renderer plus actual vanilla inventory; no production connection");
                results.put("java", System.getProperty("java.runtime.version"));
                Files.write(
                        new File(out, "results.json").toPath(),
                        new GsonBuilder().setPrettyPrinting().create().toJson(results)
                                .getBytes(StandardCharsets.UTF_8));
                mc.shutdown();
                stage = 8;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                results.put("error", t.toString());
                Files.write(
                        new File(out, "results.json").toPath(),
                        new GsonBuilder().setPrettyPrinting().create().toJson(results)
                                .getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            mc.shutdown();
            stage = 8;
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if ((stage == 2 || stage == 4 || stage == 6) && ++frames == 100) {
            String name = stage == 2 ? "gallery-before.png"
                    : stage == 4 ? "vanilla-inventory.png" : "gallery-after-reload.png";
            ScreenShotHelper.saveScreenshot(out, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            results.put(name + "_gl_error", GL11.glGetError());
            stage++;
        }
    }

    final class Gallery extends GuiScreen {

        final RenderItem renderer = new RenderItem();

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partial) {
            drawDefaultBackground();
            drawCenteredString(
                    fontRendererObj,
                    "Rows: reference nearest / incoming mipmap nearest / incoming mipmap linear",
                    width / 2,
                    12,
                    0xffffff);
            for (int row = 0; row < 3; row++) {
                int scale = stage == 2 ? 1 : 2, y = 45 + row * 65, startX = (width - 11 * 28 * scale) / 2;
                renderMode = row;
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glColor4f(1, 1, 1, 1);
                for (int meta = 0; meta < 11; meta++) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(startX + meta * 28 * scale, y, 0);
                    GL11.glScalef(scale, scale, scale);
                    drawRect(-4, -4, 20, 20, 0xff3a3a3a);
                    GL11.glEnable(GL11.GL_ALPHA_TEST);
                    GL11.glColor4f(1, 1, 1, 1);
                    renderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack(meta), 0, 0);
                    GL11.glPopMatrix();
                }
                RenderHelper.disableStandardItemLighting();
                for (int meta = 0; meta < 11; meta++) drawString(
                        fontRendererObj,
                        Integer.toString(meta),
                        startX + meta * 28 * scale,
                        y + 17 * scale,
                        0xffffff);
            }
            renderMode = -1;
        }
    }
}
