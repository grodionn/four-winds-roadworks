package dev.fourwinds.roads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=ClassicRoadMod.ID,value=Side.CLIENT)
public final class ClassicWaypostClient extends TileEntitySpecialRenderer<ClassicWaypostTile> {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        ClientRegistry.bindTileEntitySpecialRenderer(ClassicWaypostTile.class,new ClassicWaypostClient());
    }
    @Override public void render(ClassicWaypostTile tile,double x,double y,double z,float partial,int destroy,float alpha) {
        if(tile.getWorld()==null) return;
        net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if(!(state.getBlock() instanceof ClassicWaypost))return;
        EnumFacing face=state.getValue(ClassicWaypost.FACING);
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y,z+.5);
        if(state.getValue(ClassicWaypost.VILLAGE)) {
            label(face,20.85f,"village",18.75f,.96f);
            label(face,17.9f,face.getName(),18.75f,.9f);
            draw(face,10f,I18n.format("sign.fourwindsroadworks.distance",tile.distance),19.5f,.82f);
        } else {
            label(EnumFacing.EAST,20.5f,"east",18.5f,.88f);label(EnumFacing.WEST,19.5f,"west",18.5f,.88f);
            label(EnumFacing.NORTH,12.5f,"north",18.5f,.88f);label(EnumFacing.SOUTH,13.5f,"south",18.5f,.88f);
        }
        GlStateManager.popMatrix();
    }
    private static void label(EnumFacing face,float h,String label,float center,float width) {
        draw(face,h,I18n.format("sign.fourwindsroadworks."+label),center,width);
    }
    private static void draw(EnumFacing face,float height,String text,float center,float width) {
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        int pixels=font.getStringWidth(text);
        float scale=Math.min(.01953125f,width/Math.max(1,pixels));
        GlStateManager.pushMatrix();
        GlStateManager.rotate(270-face.getHorizontalAngle(),0,1,0);
        GlStateManager.translate((center-8)/16f,height/16f,0);
        for(int i=0;i<2;i++){
            GlStateManager.pushMatrix();
            if(i!=0)GlStateManager.rotate(180,0,1,0);
            GlStateManager.translate(0,0,1f/16+.002f);
            GlStateManager.scale(scale,-scale,scale);
            font.drawString(text,-pixels/2f,-3.7f,0xff241b12,false);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
