package dev.fourwinds.roads;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid=ClassicRoadMod.ID)
public final class ClassicWaypost extends BlockContainer {
    static final PropertyDirection FACING=net.minecraft.block.BlockHorizontal.FACING;
    static final PropertyBool VILLAGE=PropertyBool.create("village");
    static ClassicWaypost oak,tfcOak;
    private static final AxisAlignedBB POST=new AxisAlignedBB(.36,0,.36,.64,1.5,.64);

    private ClassicWaypost(String name) {
        super(Material.WOOD);setRegistryName(ClassicRoadMod.ID,name);setHardness(2);setSoundType(SoundType.WOOD);
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.EAST).withProperty(VILLAGE,false));
    }
    @SubscribeEvent public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(oak=new ClassicWaypost("waypost"));
        if(Loader.isModLoaded("tfc")) event.getRegistry().register(tfcOak=new ClassicWaypost("tfc_waypost"));
        GameRegistry.registerTileEntity(ClassicWaypostTile.class,new ResourceLocation(ClassicRoadMod.ID,"waypost"));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,FACING,VILLAGE); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(meta&3)).withProperty(VILLAGE,(meta&4)!=0); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex()|(state.getValue(VILLAGE)?4:0); }
    @Override public TileEntity createNewTileEntity(World world,int metadata) { return new ClassicWaypostTile(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) { return POST; }

    static void place(World world,BlockPos pos,boolean tfc,RoadLayout.Spur target) {
        int dx=target==null?1:target.village().x()-pos.getX();
        int dz=target==null?0:target.village().z()-pos.getZ();
        EnumFacing face=Math.abs(dx)>=Math.abs(dz)?(dx>=0?EnumFacing.EAST:EnumFacing.WEST):(dz>=0?EnumFacing.SOUTH:EnumFacing.NORTH);
        ClassicWaypost post=tfc?tfcOak:oak;
        IBlockState state=post.getDefaultState().withProperty(FACING,face).withProperty(VILLAGE,target!=null);
        world.setBlockState(pos,state,18);
        TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof ClassicWaypostTile) {
            ((ClassicWaypostTile)tile).distance=(int)Math.round(Math.hypot(dx,dz));
            tile.markDirty();
            world.notifyBlockUpdate(pos,state,state,2);
        }
    }
}
