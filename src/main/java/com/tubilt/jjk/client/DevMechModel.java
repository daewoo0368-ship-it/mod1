package com.tubilt.jjk.client;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.mech.DevMech;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** assets/jjk/geckolib/models/entity/dev_mech.geo.json 등 기본 경로 사용. */
public class DevMechModel extends DefaultedEntityGeoModel<DevMech> {
    public static final DataTicket<float[]> LEGS = DataTicket.create("jjk_dev_mech_legs", float[].class);

    public DevMechModel() { super(JjkMod.id("dev_mech")); }

    @Override
    public void addAdditionalStateData(DevMech mech, GeoRenderState state) {
        super.addAdditionalStateData(mech, state);
        float pt = state.getPartialTick();
        float[] out = new float[mech.legs.length];
        for (int i = 0; i < out.length; i++) out[i] = mech.legsPrev[i] + (mech.legs[i] - mech.legsPrev[i]) * pt;
        state.addGeckolibData(LEGS, out);
    }

    @Override
    public void setCustomAnimations(AnimationState<DevMech> state) {
        super.setCustomAnimations(state);
        float[] a = state.getData(LEGS);
        if (a == null) return;
        for (int i = 0; i < DevMech.LEGS; i++) {
            final int o = i * DevMech.PER;
            getBone("leg" + i).ifPresent(b -> b.setRotY(a[o]));
            getBone("coxa" + i).ifPresent(b -> b.setRotX(a[o + 1]));
            getBone("femur" + i).ifPresent(b -> b.setRotX(a[o + 2]));
            getBone("tibia" + i).ifPresent(b -> b.setRotX(a[o + 3]));
            getBone("tarsus" + i).ifPresent(b -> b.setRotX(a[o + 4]));
        }
        // 몸통: 지형 기울기 + 걸음 출렁임 (애니메이션 대신 코드가 담당)
        if (a.length >= DevMech.BODY + 6) {
            getBone("gun").ifPresent(b -> b.setRotX(a[DevMech.BODY + 4]));
            getBone("gun_barrels").ifPresent(b -> b.setRotZ(a[DevMech.BODY + 5]));
        }
        if (a.length >= DevMech.BODY + 4) getBone("root").ifPresent(b -> b.setPosY(a[DevMech.BODY + 3]));
        if (a.length >= DevMech.BODY + 3)
            getBone("body").ifPresent(b -> {
                b.setRotX(a[DevMech.BODY]);
                b.setRotZ(a[DevMech.BODY + 1]);
                b.setPosY(a[DevMech.BODY + 2]);
            });
    }
}
