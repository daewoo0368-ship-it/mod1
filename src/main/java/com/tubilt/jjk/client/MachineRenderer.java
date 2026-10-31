package com.tubilt.jjk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tubilt.jjk.machine.MachineBE;
import com.tubilt.jjk.machine.MachineBlock;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** 모델은 항상 Y축 기준으로 만들고, 블록의 AXIS 값에 따라 X/Z 축으로 눕힌다. */
public class MachineRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<MachineBE, R> {
    public MachineRenderer() { super(new MachineModel()); }

    @Override
    public void adjustRenderPose(R rs, PoseStack ps, BakedGeoModel model, CameraRenderState cam) {
        BlockState bs = rs.getGeckolibData(DataTickets.BLOCKSTATE);
        Direction.Axis ax = bs != null && bs.hasProperty(MachineBlock.AXIS) ? bs.getValue(MachineBlock.AXIS) : Direction.Axis.Y;
        ps.translate(0, 0.5, 0);
        if (ax == Direction.Axis.X) ps.mulPose(Axis.ZP.rotationDegrees(-90f));
        else if (ax == Direction.Axis.Z) ps.mulPose(Axis.XP.rotationDegrees(90f));
        ps.translate(0, -0.5, 0);
    }
}
