package com.tubilt.jjk.client;

import com.tubilt.jjk.mech.DevWalker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class DevWalkerRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DevWalker, R> {
    public DevWalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DevWalkerModel());
        this.shadowRadius = 4.0f;
    }

    /** 다리가 몸통 밖으로 넓게 벌어지므로 컬링 박스 확장. */
    public AABB getBoundingBoxForCulling(DevWalker entity) {
        return entity.getBoundingBox().inflate(10.0, 4.0, 10.0);
    }
}
