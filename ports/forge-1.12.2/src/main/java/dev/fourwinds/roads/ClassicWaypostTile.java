package dev.fourwinds.roads;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

public final class ClassicWaypostTile extends TileEntity {
    int distance;
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) { super.writeToNBT(nbt);nbt.setInteger("Distance",distance);return nbt; }
    @Override public void readFromNBT(NBTTagCompound nbt) { super.readFromNBT(nbt);distance=nbt.getInteger("Distance"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos,0,getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos.add(-1,0,-1),pos.add(2,2,2)); }
}
