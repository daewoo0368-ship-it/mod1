package com.tubilt.jjk.client;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.mech.DevWalker;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** assets/jjk/geckolib/models/entity/dev_walker.geo.json */
public class DevWalkerModel extends DefaultedEntityGeoModel<DevWalker> {
    public static final DataTicket<float[]> POSE = DataTicket.create("jjk_dev_walker_pose", float[].class);

    public DevWalkerModel() { super(JjkMod.id("dev_walker")); }

    @Override
    public void addAdditionalStateData(DevWalker w, GeoRenderState state) {
        super.addAdditionalStateData(w, state);
        float pt = state.getPartialTick();
        float[] out = new float[w.legs.length];
        for (int i = 0; i < out.length; i++) out[i] = w.legsPrev[i] + (w.legs[i] - w.legsPrev[i]) * pt;
        state.addGeckolibData(POSE, out);
    }

    @Override
    public void setCustomAnimations(AnimationState<DevWalker> state) {
        super.setCustomAnimations(state);
        float[] a = state.getData(POSE);
        if (a == null) return;
        for (int i = 0; i < DevWalker.LEGS; i++) {
            final int o = i * DevWalker.PER;
            getBone("leg" + i).ifPresent(b -> b.setRotY(a[o]));
            getBone("femur" + i).ifPresent(b -> b.setRotX(a[o + 1]));
            getBone("tibia" + i).ifPresent(b -> b.setRotX(a[o + 2]));
            getBone("foot" + i).ifPresent(b -> b.setRotX(a[o + 3]));
        }
        final int B = DevWalker.BODY;
        getBone("body").ifPresent(b -> { b.setRotX(a[B]); b.setRotZ(a[B + 1]); b.setPosY(a[B + 2]); });
        getBone("turret").ifPresent(b -> b.setRotY(a[B + 3]));
        getBone("cannon").ifPresent(b -> b.setRotX(a[B + 4]));
        getBone("root").ifPresent(b -> b.setPosY(a[B + 5]));
        if (a.length >= B + 8) {
            getBone("head").ifPresent(b -> b.setRotY(a[B + 6]));
            getBone("tail").ifPresent(b -> b.setRotY(a[B + 7]));
        }
    }
}
