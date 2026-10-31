package com.tubilt.jjk.client;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.machine.MachineBE;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * 기계 블록 공용 GeckoLib 모델. 블록 종류(id)마다 모델이 다르고(assets/jjk/geckolib/models/block/&lt;id&gt;.geo.json),
 * 회전하는 뼈 "rotor" 의 각도는 서버 물리 엔진이 보낸 (각도, 각속도)에서 계산한다.
 */
public class MachineModel extends GeoModel<MachineBE> {
    public static final DataTicket<String> KIND = DataTicket.create("jjk_machine_kind", String.class);
    public static final DataTicket<float[]> ROT = DataTicket.create("jjk_machine_rot", float[].class);
    static final ResourceLocation ANIM = JjkMod.id("block/machine");
    static final ResourceLocation TEX = JjkMod.id("textures/block/machine.png");

    @Override public ResourceLocation getModelResource(GeoRenderState s) { return JjkMod.id("block/" + s.getOrDefaultGeckolibData(KIND, "mech_shaft")); }
    @Override public ResourceLocation getTextureResource(GeoRenderState s) { return TEX; }
    @Override public ResourceLocation getAnimationResource(MachineBE be) { return ANIM; }

    @Override
    public void addAdditionalStateData(MachineBE be, GeoRenderState state) {
        super.addAdditionalStateData(be, state);
        state.addGeckolibData(KIND, be.kind());
        // 기어 이빨 위상: 이웃한 기어끼리 이빨이 어긋나 맞물리도록 격자 홀짝에 따라 반 피치만큼 돌려 둔다 (잇수는 4의 배수)
        var p = be.getBlockPos();
        int n = be.block().teeth();
        double off = n > 0 && (((p.getX() + p.getY() + p.getZ()) & 1) != 0) ? Math.PI / n : 0;
        state.addGeckolibData(ROT, new float[]{(float) (be.angleNow() + off)});
    }

    @Override
    public void setCustomAnimations(AnimationState<MachineBE> state) {
        super.setCustomAnimations(state);
        float[] a = state.getData(ROT);
        if (a == null) return;
        getBone("rotor").ifPresent(b -> b.setRotY(a[0]));
    }
}
