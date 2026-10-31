package com.tubilt.jjk.client;

import com.tubilt.jjk.mech.DevMech;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class DevMechRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DevMech, R> {
    public DevMechRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DevMechModel());
        this.shadowRadius = 3.0f;
    }

    /** 다리가 몸통 밖으로 길게 뻗으므로 화면 가장자리에서 사라지지 않게 컬링 박스 확장. */
    public AABB getBoundingBoxForCulling(DevMech entity) {
        return entity.getBoundingBox().inflate(9.0, 6.0, 9.0);
    }
}
