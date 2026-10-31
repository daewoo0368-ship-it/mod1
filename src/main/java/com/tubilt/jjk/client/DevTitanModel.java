package com.tubilt.jjk.client;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.mech.DevTitan;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** assets/jjk/geckolib/models/entity/dev_titan.geo.json (1/5 크기, 렌더러가 5배). */
public class DevTitanModel extends DefaultedEntityGeoModel<DevTitan> {
    public static final DataTicket<float[]> POSE = DataTicket.create("jjk_dev_titan_pose", float[].class);

    public DevTitanModel() { super(JjkMod.id("dev_titan")); }

    @Override
    public void addAdditionalStateData(DevTitan t, GeoRenderState state) {
        super.addAdditionalStateData(t, state);
        float pt = state.getPartialTick();
        float[] out = new float[DevTitan.N];
        for (int i = 0; i < out.length; i++) out[i] = t.posePrev[i] + (t.pose[i] - t.posePrev[i]) * pt;
        state.addGeckolibData(POSE, out);
    }

    @Override
    public void setCustomAnimations(AnimationState<DevTitan> state) {
        super.setCustomAnimations(state);
        float[] a = state.getData(POSE);
        if (a == null) return;
        String[] sides = {"l", "r"};
        for (int i = 0; i < 2; i++) {
            final int o = DevTitan.LEG + i * 3, q = DevTitan.ARMS + i * 2;
            String s = sides[i];
            getBone("thigh_" + s).ifPresent(b -> b.setRotX(a[o]));
            getBone("shin_" + s).ifPresent(b -> b.setRotX(a[o + 1]));
            getBone("foot_" + s).ifPresent(b -> b.setRotX(a[o + 2]));
            getBone("arm_" + s).ifPresent(b -> b.setRotX(a[q]));
            getBone("forearm_" + s).ifPresent(b -> b.setRotX(a[q + 1]));
        }
        final int B = DevTitan.BODY;
        getBone("root").ifPresent(b -> b.setPosY(a[B]));
        getBone("torso").ifPresent(b -> { b.setRotZ(a[B + 1]); b.setRotX(a[B + 2]); });
        getBone("head").ifPresent(b -> { b.setRotY(a[B + 3]); b.setRotX(a[B + 4]); });
    }
}
