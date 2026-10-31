package com.tubilt.jjk.client;

import com.tubilt.jjk.mech.DevTitan;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** 약 20칸 모델을 DevTitan.SCALE 배로 그림. */
public class DevTitanRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DevTitan, R> {
    public DevTitanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DevTitanModel());
        withScale(DevTitan.SCALE);
        this.shadowRadius = 0f;
    }

    public AABB getBoundingBoxForCulling(DevTitan entity) {
        return entity.getBoundingBox().inflate(24.0, 40.0, 24.0);
    }
}
