package com.tubilt.jjk.client;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.mech.DevMachine;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** assets/jjk/geckolib/models/entity/dev_machine.geo.json (1/2.5 크기, 렌더러가 확대). */
public class DevMachineModel extends DefaultedEntityGeoModel<DevMachine> {
    public static final DataTicket<float[]> POSE = DataTicket.create("jjk_dev_machine_pose", float[].class);

    public DevMachineModel() { super(JjkMod.id("dev_machine")); }

    @Override
    public void addAdditionalStateData(DevMachine m, GeoRenderState state) {
        super.addAdditionalStateData(m, state);
        float pt = state.getPartialTick();
        float[] out = new float[DevMachine.N];
        for (int i = 0; i < out.length; i++) out[i] = m.posePrev[i] + (m.pose[i] - m.posePrev[i]) * pt;
        state.addGeckolibData(POSE, out);
    }

    @Override
    public void setCustomAnimations(AnimationState<DevMachine> state) {
        super.setCustomAnimations(state);
        float[] a = state.getData(POSE);
        if (a == null) return;
        for (int i = 0; i < DevMachine.LEGS; i++) {
            final int o = i * DevMachine.PER;
            getBone("leg" + i).ifPresent(b -> b.setRotY(a[o]));
            getBone("coxa" + i).ifPresent(b -> b.setRotX(a[o + 1]));
            getBone("femur" + i).ifPresent(b -> b.setRotX(a[o + 2]));
            getBone("tibia" + i).ifPresent(b -> b.setRotX(a[o + 3]));
            getBone("tarsus" + i).ifPresent(b -> b.setRotX(a[o + 4]));
        }
        final int B = DevMachine.BODY;
        getBone("root").ifPresent(b -> b.setPosY(a[B + 3] + a[B + 2]));
        getBone("abdomen").ifPresent(b -> { b.setRotX(a[B]); b.setRotZ(a[B + 1]); });
        getBone("torso").ifPresent(b -> { b.setRotX(a[B] * 0.6f); b.setRotZ(a[B + 1] * 0.6f); b.setRotY(a[B + 4]); });
        getBone("arm_l").ifPresent(b -> b.setRotX(a[B + 5]));
        getBone("fore_l").ifPresent(b -> b.setRotX(a[B + 6]));
        getBone("arm_r").ifPresent(b -> b.setRotX(a[B + 7]));
        getBone("fore_r").ifPresent(b -> b.setRotX(a[B + 8]));
        getBone("gat_spin").ifPresent(b -> b.setRotY(a[B + 9]));
        getBone("cannon").ifPresent(b -> b.setRotX(a[B + 10]));
        getBone("head").ifPresent(b -> b.setRotX(a[B + 11]));
        getBone("halo").ifPresent(b -> b.setRotZ(a[B + 12]));
        float w = a[B + 13];
        for (String n : new String[]{"wing_l", "wing_r"}) getBone(n).ifPresent(b -> {
            b.setHidden(w < 0.03f);
            float k = 0.05f + 0.95f * w;
            b.setScaleX(k); b.setScaleY(k); b.setScaleZ(k);
        });
        getBone("core").ifPresent(b -> { b.setScaleX(a[B + 14]); b.setScaleY(a[B + 14]); });
        getBone("turbine").ifPresent(b -> b.setRotY(a[B + 15]));
    }
}
