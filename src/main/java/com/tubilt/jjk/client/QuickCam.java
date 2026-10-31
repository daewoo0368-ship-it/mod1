package com.tubilt.jjk.client;

import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * 궁극기 순간을 보여주는 짧은 시네마틱 카메라: 초점 주위를 천천히 돌며 살짝 다가감 (실시간 ms 기준).
 * 끝나면 원래 시점으로. 위아래 얇은 레터박스.
 */
public final class QuickCam {
    private QuickCam() {}

    static Vec3 focus; static double dist, height, yaw0, span; static long start, dur;

    public static void start(Payloads.Cam c) {
        var pl = Minecraft.getInstance().player;
        if (pl == null) return;
        focus = new Vec3(c.fx(), c.fy(), c.fz());
        dist = c.dist(); height = c.height();
        dur = Math.max(300, c.meta() & 0xFFFF);
        span = ((c.meta() >>> 16) & 0x7FF) - 720;
        // 플레이어 쪽에서 시작 (플레이어 → 초점 방향의 반대편 약간 옆)
        double dx = pl.getX() - focus.x, dz = pl.getZ() - focus.z;
        yaw0 = Math.toDegrees(Math.atan2(dx, dz)) - span * 0.35;
        start = System.currentTimeMillis();
    }

    static double u() { return focus == null ? 2 : (System.currentTimeMillis() - start) / (double) dur; }
    public static boolean active() { double u = u(); return u >= 0 && u <= 1; }

    public static Cinematic.Shot shot() {
        if (!active() || Minecraft.getInstance().player == null) return null;
        double u = u(), e = u * u * (3 - 2 * u);
        double yaw = Math.toRadians(yaw0 + span * e);
        double d = dist * (1.08 - 0.2 * e), hh = height * (1.1 - 0.25 * e);
        Vec3 pos = focus.add(Math.sin(yaw) * d, hh, Math.cos(yaw) * d);
        // 시작·끝 0.15 구간은 플레이어 시점과 섞어서 부드럽게 전환
        var pl = Minecraft.getInstance().player;
        Vec3 eye = pl.getEyePosition();
        double blend = u < 0.12 ? 1 - u / 0.12 : u > 0.88 ? (u - 0.88) / 0.12 : 0;
        blend = blend * blend * (3 - 2 * blend);
        pos = pos.lerp(eye, blend);
        Vec3 look = focus.lerp(eye.add(pl.getLookAngle().scale(10)), blend);
        Vec3 dv = look.subtract(pos);
        float yw = (float) Math.toDegrees(Math.atan2(-dv.x, dv.z));
        float pt = (float) -Math.toDegrees(Math.atan2(dv.y, Math.sqrt(dv.x * dv.x + dv.z * dv.z)));
        return new Cinematic.Shot(pos, yw, pt);
    }

    static void render(GuiGraphics g, int w, int h) {
        if (!active()) return;
        double u = u();
        double k = Math.min(1, Math.min(u / 0.1, (1 - u) / 0.1));
        int bar = (int) (h / 11 * k);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }
}
