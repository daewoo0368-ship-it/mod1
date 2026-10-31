package com.tubilt.jjk.client;

import com.tubilt.jjk.mech.DevMachine;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** 약 12칸 모델을 DevMachine.SCALE 배(약 30칸)로 그림. */
public class DevMachineRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DevMachine, R> {
    public DevMachineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DevMachineModel());
        withScale(DevMachine.SCALE);
        withRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));   // dev_machine_glowmask.png: 발광 부위
        this.shadowRadius = 0f;
    }

    public AABB getBoundingBoxForCulling(DevMachine entity) {
        return entity.getBoundingBox().inflate(36.0, 26.0, 36.0);
    }
}
