package runtimechecks;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.core.ModBlocks;

import codechicken.lib.vec.BlockCoord;
import codechicken.multipart.TileMultipart;
import mrtjp.core.inventory.InvWrapper;
import mrtjp.core.item.ItemKey;
import mrtjp.projectred.ProjectRedTransportation$;
import mrtjp.projectred.transportation.BasicPipePart;
import mrtjp.projectred.transportation.ChipExtractor;
import mrtjp.projectred.transportation.ChipItemResponder;
import mrtjp.projectred.transportation.ItemRoutingChip$;
import mrtjp.projectred.transportation.RoutedInterfacePipePart;
import mrtjp.projectred.transportation.RoutingChip;

final class DrawerScene {
    static final Map<String,Object> results=new LinkedHashMap<>();
    static TileEntityController source,target;
    static RoutedInterfacePipePart sourcePipe;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int count(TileEntityController c){int n=0;for(int i=0;i<c.getDrawerCount();i++)n+=c.getDrawer(i).getStoredItemCount();return n;}
    private static TileEntityController controller(WorldServer w,EntityPlayerMP p,int x,boolean reverse){
        w.theChunkProviderServer.loadChunk(x>>4,0);
        ItemStack controllerStack=new ItemStack(ModBlocks.controller);
        check(((ItemBlock)controllerStack.getItem()).placeBlockAt(controllerStack,p,w,x,65,0,2,.5f,.5f,.5f,0),"Native controller placement failed");
        check(w.getBlock(x,65,0)==ModBlocks.controller,"Controller block not visible after placement at "+x+": "+w.getBlock(x,65,0));
        TileEntityController c=ModBlocks.controller.getTileEntitySafe(w,x,65,0);c.setDirection(2);
        for(int i=0;i<8;i++){
            int xx=x+(reverse?-(i%2):i%2),yy=65+(i/2)%2,zz=1+i/4;
            ItemStack stack=new ItemStack(ModBlocks.fullDrawers4);
            check(((ItemBlock)Item.getItemFromBlock(ModBlocks.fullDrawers4)).placeBlockAt(stack,p,w,xx,yy,zz,2,.5f,.5f,.5f,0),"Native drawer placement failed");
            ModBlocks.fullDrawers4.getTileEntitySafe(w,xx,yy,zz).setDirection(2);
        }
        return c;
    }
    private static void chip(RoutedInterfacePipePart p,boolean extractor){
        RoutingChip chip;
        if(extractor){ChipExtractor c=new ChipExtractor();c.filterExclude_$eq(true);chip=c;}
        else{ChipItemResponder c=new ChipItemResponder();c.filterExclude_$eq(true);chip=c;}
        ItemStack stack=new ItemStack(ProjectRedTransportation$.MODULE$.itemRoutingChip(),1,extractor?4:0);
        ItemRoutingChip$.MODULE$.saveChipToItemStack(stack,chip);p.chipSlots().setInventorySlotContents(0,stack);
    }
    static void setupOrCheck(WorldServer w,EntityPlayerMP p,boolean cold){
        if(cold)return;
        for(int x=-25;x<=-13;x++)for(int z=-2;z<=4;z++){
            w.theChunkProviderServer.loadChunk(x>>4,z>>4);
            for(int y=65;y<=70;y++)w.setBlockToAir(x,y,z);
            w.setBlock(x,64,z,Blocks.stone);
        }
        source=controller(w,p,-16,false);target=controller(w,p,-22,true);
    }
    static void initialize(WorldServer w,boolean cold){
        source=(TileEntityController)w.getTileEntity(-16,65,0);target=(TileEntityController)w.getTileEntity(-22,65,0);
        check(source!=null && target!=null,"Controller tile entities not available after settling");
        source.updateCache();target.updateCache();
        check(source.getDrawerCount()==32 && target.getDrawerCount()==32,"Expected 32 drawers per controller after settling: "+source.getDrawerCount()+"/"+target.getDrawerCount());
        if(cold){check(count(source)+count(target)==8224,"Drawer stocks not preserved after reload");results.put("cold_reload_total",8224);return;}
        for(int i=0;i<32;i++){source.getDrawer(i).setStoredItemRedir(new ItemStack(Items.coal),256);target.getDrawer(i).setStoredItemRedir(new ItemStack(Items.coal),1);}
        InvWrapper from=InvWrapper.wrap(source),to=InvWrapper.wrap(target);
        check(from.getClass().getName().contains("StorageDrawersInvWrapper"),"Wrong controller wrapper: "+from.getClass());
        ItemKey coal=ItemKey.get(new ItemStack(Items.coal));
        check(from.extractItem(coal,0)==0 && to.injectItem(coal,0)==0,"Zero transfer changed quantity");
        check(from.extractItem(coal,1)==1 && to.injectItem(coal,1)==1,"One-item adapter transfer failed");
        check(count(source)+count(target)==8224,"Adapter lost items");
        sourcePipe=new RoutedInterfacePipePart();sourcePipe.preparePlacement(0,2);TileMultipart.addPart(w,new BlockCoord(-17,65,0),sourcePipe);
        RoutedInterfacePipePart destination=new RoutedInterfacePipePart();destination.preparePlacement(0,2);TileMultipart.addPart(w,new BlockCoord(-21,65,0),destination);
        for(int x=-20;x<=-18;x++){BasicPipePart bridge=new BasicPipePart();bridge.preparePlacement(0,0);TileMultipart.addPart(w,new BlockCoord(x,65,0),bridge);}
        chip(destination,false);chip(sourcePipe,true);
        results.put("logical_drawers_per_controller",32);results.put("initial_total",8224);results.put("one_item_adapter_transfer",true);
    }
    static void stopExtraction(){sourcePipe.chipSlots().setInventorySlotContents(0,null);}
    static void verify(WorldServer w){
        source.updateCache();target.updateCache();int a=count(source),b=count(target);
        check(a+b==8224,"Native drawer transport lost or duplicated items: "+(a+b));
        check(b>33,"Native extraction did not deliver through pipes");
        results.put("native_source",a);results.put("native_target",b);results.put("native_total",a+b);results.put("native_extractor_delivery",true);
    }
}
