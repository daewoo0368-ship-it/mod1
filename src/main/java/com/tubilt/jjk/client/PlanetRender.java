package com.tubilt.jjk.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tubilt.jjk.JjkMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;

/**
 * 「/tp 행성 낙하」 클라이언트 3D 렌더 (디스플레이 엔티티가 아닌 실제 메시).
 *  · 우주 돔: 은하수 파노라마(ESO/S. Brunier, CC BY 4.0)를 안쪽에 입힌 거대한 구 → 하늘이 우주로 바뀜
 *  · 행성: 위경도 메시(64×32) + NASA 달 지도(LRO/LOLA)를 재채색한 텍스처, 정점 조명(낮·밤 경계) + 자전
 *  · 용암 균열 껍질(가산 합성) · 대기 산란 림(프레넬) · 돌입 시 플라스마 충격파
 *  · /tp 견인 광선 4줄(원기둥) · 충돌 화구 · 충격파 고리
 * 시간축은 서버 연출(CommandPlanet)과 같은 게임 틱.
 */
public final class PlanetRender {
    private PlanetRender() {}

    static final ResourceLocation TEX_PLANET = JjkMod.id("textures/fx3d/planet.png");
    static final ResourceLocation TEX_LAVA = JjkMod.id("textures/fx3d/planet_lava.png");
    static final ResourceLocation TEX_SPACE = JjkMod.id("textures/fx3d/space.png");
    static final ResourceLocation TEX_WHITE = JjkMod.id("textures/fx3d/white.png");
    static final int FULL = 0xF000F0, OV = OverlayTexture.NO_OVERLAY;
    static final Vec3 SUN = new Vec3(0.45, 0.55, -0.70).normalize();

    public static void init() {
        AddFramePassEvent.BUS.addListener(e -> e.addPass(JjkMod.id("planet3d"), new FramePassManager.PassDefinition() {
            // 포지 빌드마다 이름이 달라서(targets / extracts 2·3인자) 셋 다 구현 — @Override 없이
            public void targets(net.minecraft.client.renderer.LevelTargetBundle bundle, com.mojang.blaze3d.framegraph.FramePass pass) {
                bundle.main = pass.readsAndWrites(bundle.main);
            }
            public void extracts(net.minecraft.client.renderer.LevelTargetBundle bundle, com.mojang.blaze3d.framegraph.FramePass pass) {
                bundle.main = pass.readsAndWrites(bundle.main);
            }
            public void extracts(net.minecraft.client.renderer.LevelTargetBundle bundle, com.mojang.blaze3d.framegraph.FramePass pass, net.minecraft.client.DeltaTracker dt) {
                bundle.main = pass.readsAndWrites(bundle.main);
            }
            @Override
            public void executes(net.minecraft.client.renderer.state.LevelRenderState state) {
                try { frame(); } catch (Throwable ex) { JjkMod.LOG.warn("[JJK] 행성 렌더 오류", ex); PlanetCine.active = false; }
            }
        }));
    }

    static final PlanetShader.Frame FRAME = new PlanetShader.Frame();
    /** 마지막으로 그린 셰이더 프레임 (HUD 표지 투영용). */
    static PlanetShader.Frame LAST;

    /** 화면 셰이더로 그림 (안 되면 예전 메시 렌더). 카메라는 실제 카메라 위치 기준. */
    static void frame() {
        if (!PlanetCine.renderActive()) { LAST = null; return; }
        Minecraft mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cp = camera.getPosition();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (PlanetShader.ensure()) {
            if (PlanetCine.fill(FRAME, cp, camera.getYRot(), camera.getXRot(), pt, ScreenFx.lastFov, mc.gameRenderer.getDepthFar())) {
                PlanetShader.draw(FRAME);
                LAST = FRAME;
            } else LAST = null;
        } else render(cp);
    }

    static int argb(double a, double r, double g, double b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v * 255))); }
    static double sm(double e0, double e1, double x) { double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); }

    static void render(Vec3 cam) {
        if (!PlanetCine.renderActive() || PlanetCine.T == null) return;
        Minecraft mc = Minecraft.getInstance();
        double t = PlanetCine.elapsed(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        Vec3 T = PlanetCine.T;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        double R = PlanetCine.SIZE / 2;
        Vec3 C = T.add(0, Math.min(600, PlanetCine.alt(t)), 0);
        double heat = sm(PlanetCine.DESCENT, PlanetCine.HIT, t);
        boolean alive = t < PlanetCine.HIT;

        // ── 1. 우주 돔 (카메라 중심, 렌더 거리 안쪽)
        double domeA = sm(8, 50, t) * (1 - sm(PlanetCine.HIT - 10, PlanetCine.HIT + 30, t));
        if (domeA > 0.01) {
            double far = mc.options.getEffectiveRenderDistance() * 16.0 * 4.0;
            double rad = Math.min(520, far * 0.8);
            RenderType rt = RenderType.entityTranslucent(TEX_SPACE);
            VertexConsumer vc = buf.getBuffer(rt);
            double red = heat * (1 - sm(240, 300, t));
            int col = argb(domeA, 1, 1 - red * 0.45, 1 - red * 0.6);
            sphere(vc, Vec3.ZERO, rad, 48, 24, col, true, 0, null, 0);    // 안쪽 면
            sphere(vc, Vec3.ZERO, rad, 48, 24, col, false, 0, null, 0);   // 컬링 방향이 반대여도 보이게
            buf.endBatch(rt);
        }
        if (alive) {
            double spin = t * 0.0025;
            // ── 2. 행성 본체 (정점 조명: 태양 쪽 밝고 밤 쪽 어두움, 돌입할수록 그을림)
            RenderType rt = RenderType.entityCutoutNoCull(TEX_PLANET);
            VertexConsumer vc = buf.getBuffer(rt);
            sphereLit(vc, C.subtract(cam), R, spin, heat);
            buf.endBatch(rt);
            // ── 3. 용암 균열 (가산 합성, 돌입하면서 점점 밝아짐)
            double lava = Math.pow(heat, 1.3);
            if (lava > 0.02) {
                RenderType rl = RenderType.eyes(TEX_LAVA);
                VertexConsumer lv = buf.getBuffer(rl);
                int lc = argb(1, lava, lava, lava);
                sphere(lv, C.subtract(cam), R * 1.003, 64, 32, lc, false, spin, null, 0);
                buf.endBatch(rl);
            }
            // ── 4. 대기 림 3겹 (프레넬) + 돌입 시 아래쪽 플라스마 충격파
            RenderType ra = RenderType.eyes(TEX_WHITE);
            VertexConsumer av = buf.getBuffer(ra);
            double[] shells = {1.03, 1.08, 1.16};
            for (int i = 0; i < shells.length; i++) {
                double k = 1 - i * 0.3;
                double r0 = 0.55 + heat * 0.45, g0 = 0.72 - heat * 0.35, b0 = 1.0 - heat * 0.85;
                rim(av, C.subtract(cam), R * shells[i], cam, C, r0 * k * 0.55, g0 * k * 0.55, b0 * k * 0.55, 3.0 + i, 0);
            }
            if (heat > 0.05) {
                double h = heat * (0.8 + 0.2 * Math.sin(t * 0.9));
                rim(av, C.subtract(cam).add(0, -R * 0.12 * heat, 0), R * (1.2 + heat * 0.2), cam, C, h, h * 0.45, h * 0.12, 1.6, -1);
                rim(av, C.subtract(cam).add(0, -R * 0.2 * heat, 0), R * (1.35 + heat * 0.3), cam, C, h * 0.8, h * 0.28, h * 0.05, 1.2, -1);
            }
            buf.endBatch(ra);
            // ── 5. /tp 견인 광선 4줄 (앵커 → 행성 아래)
            if (t > 40 && t < PlanetCine.ENTRY) {
                RenderType rb = RenderType.eyes(TEX_WHITE);
                VertexConsumer bv = buf.getBuffer(rb);
                double pulse = 0.75 + 0.25 * Math.sin(t * 0.6);
                double fade = sm(40, 60, t) * (1 - sm(PlanetCine.ENTRY - 20, PlanetCine.ENTRY, t));
                Vec3 bottom = C.add(0, -R * 0.92, 0);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + 0.4;
                    Vec3 an = T.add(Math.cos(a) * 40, 3, Math.sin(a) * 40);
                    Vec3 tip = bottom.add((an.x - T.x) * 0.35, 0, (an.z - T.z) * 0.35);
                    tube(bv, an.subtract(cam), tip.subtract(cam), 2.6, argb(1, 0.25 * fade * pulse, 0.85 * fade * pulse, 1.0 * fade * pulse));
                    tube(bv, an.subtract(cam), tip.subtract(cam), 0.8, argb(1, fade, fade, fade));
                    // 패킷: 광선을 타고 올라가는 밝은 마디
                    for (int k = 0; k < 3; k++) {
                        double f = ((t * 0.012 + k / 3.0) % 1.0);
                        Vec3 p0 = an.lerp(tip, f), p1 = an.lerp(tip, Math.min(1, f + 0.025));
                        tube(bv, p0.subtract(cam), p1.subtract(cam), 4.0, argb(1, fade * 0.8, fade, fade));
                    }
                }
                buf.endBatch(rb);
            }
        }
        // ── 6. 충돌 화구 + 충격파 고리
        if (t >= PlanetCine.HIT && t < PlanetCine.HIT + 90) {
            double k = (t - PlanetCine.HIT) / 90.0;
            RenderType rf = RenderType.eyes(TEX_WHITE);
            VertexConsumer fv = buf.getBuffer(rf);
            double fr = 20 + 190 * (1 - Math.pow(1 - k, 3));
            double br = Math.pow(1 - k, 1.6);
            rim(fv, T.subtract(cam), fr, cam, T, br, br * (0.75 - k * 0.4), br * (0.45 - k * 0.4), 0.7, 0);
            rim(fv, T.subtract(cam), fr * 0.7, cam, T, br, br * 0.95, br * 0.8, 0.35, 0);
            double sr = 30 + 420 * k, sw = 10 + 30 * k;
            ring(fv, T.add(0, 1.5, 0).subtract(cam), sr, sw, argb(1, (1 - k) * 1.0, (1 - k) * 0.6, (1 - k) * 0.3));
            ring(fv, T.add(0, 2.5, 0).subtract(cam), sr * 0.62, sw * 0.6, argb(1, (1 - k), (1 - k) * 0.9, (1 - k) * 0.8));
            buf.endBatch(rf);
        }
    }

    /** 위경도 구 (텍스처 등장방형). inside = 안쪽 면. */
    static void sphere(VertexConsumer vc, Vec3 c, double r, int nLon, int nLat, int col, boolean inside, double spin, Object unused, int x) {
        for (int j = 0; j < nLat; j++) {
            double la0 = Math.PI / 2 - Math.PI * j / nLat, la1 = Math.PI / 2 - Math.PI * (j + 1) / nLat;
            for (int i = 0; i < nLon; i++) {
                double lo0 = -Math.PI + 2 * Math.PI * i / nLon, lo1 = -Math.PI + 2 * Math.PI * (i + 1) / nLon;
                float u0 = (float) i / nLon, u1 = (float) (i + 1) / nLon, v0 = (float) j / nLat, v1 = (float) (j + 1) / nLat;
                if (inside) {
                    vtx(vc, c, r, la0, lo0, spin, col, u0, v0); vtx(vc, c, r, la0, lo1, spin, col, u1, v0);
                    vtx(vc, c, r, la1, lo1, spin, col, u1, v1); vtx(vc, c, r, la1, lo0, spin, col, u0, v1);
                } else {
                    vtx(vc, c, r, la0, lo0, spin, col, u0, v0); vtx(vc, c, r, la1, lo0, spin, col, u0, v1);
                    vtx(vc, c, r, la1, lo1, spin, col, u1, v1); vtx(vc, c, r, la0, lo1, spin, col, u1, v0);
                }
            }
        }
    }
    static Vec3 dir(double lat, double lon, double spin) {
        double cl = Math.cos(lat);
        return new Vec3(cl * Math.cos(lon + spin), Math.sin(lat), cl * Math.sin(lon + spin));
    }
    static void vtx(VertexConsumer vc, Vec3 c, double r, double lat, double lon, double spin, int col, float u, float v) {
        Vec3 n = dir(lat, lon, spin);
        vc.addVertex((float) (c.x + n.x * r), (float) (c.y + n.y * r), (float) (c.z + n.z * r), col, u, v, OV, FULL, 0f, 1f, 0f);
    }

    /** 행성 본체: 정점마다 태양 조명 + 가장자리 감쇠 + 열기 그을림. */
    static void sphereLit(VertexConsumer vc, Vec3 c, double r, double spin, double heat) {
        int nLon = 64, nLat = 32;
        for (int j = 0; j < nLat; j++) {
            double la0 = Math.PI / 2 - Math.PI * j / nLat, la1 = Math.PI / 2 - Math.PI * (j + 1) / nLat;
            for (int i = 0; i < nLon; i++) {
                double lo0 = -Math.PI + 2 * Math.PI * i / nLon, lo1 = -Math.PI + 2 * Math.PI * (i + 1) / nLon;
                float u0 = (float) i / nLon, u1 = (float) (i + 1) / nLon, v0 = (float) j / nLat, v1 = (float) (j + 1) / nLat;
                litV(vc, c, r, la0, lo0, spin, heat, u0, v0); litV(vc, c, r, la1, lo0, spin, heat, u0, v1);
                litV(vc, c, r, la1, lo1, spin, heat, u1, v1); litV(vc, c, r, la0, lo1, spin, heat, u1, v0);
            }
        }
    }
    static void litV(VertexConsumer vc, Vec3 c, double r, double lat, double lon, double spin, double heat, float u, float v) {
        Vec3 n = dir(lat, lon, spin);
        double lam = Math.max(0, n.dot(SUN));
        double term = sm(-0.08, 0.25, n.dot(SUN));                     // 낮·밤 경계를 부드럽게
        double l = 0.06 + 1.05 * Math.pow(lam, 0.85) * term;
        double under = Math.max(0, -n.y);                                // 돌입 시 아래쪽이 달아오름
        double rr = l * (1 - heat * 0.35) + heat * under * 0.9, gg = l * (1 - heat * 0.5) + heat * under * 0.35, bb = l * (1 - heat * 0.6) + heat * under * 0.1;
        vc.addVertex((float) (c.x + n.x * r), (float) (c.y + n.y * r), (float) (c.z + n.z * r), argb(1, rr, gg, bb), u, v, OV, FULL, 0f, 1f, 0f);
    }

    /** 프레넬 림 껍질: 가장자리일수록 밝음 (가산). bias -1 이면 아래쪽 반구만 강하게. */
    static void rim(VertexConsumer vc, Vec3 c, double r, Vec3 cam, Vec3 world, double R0, double G0, double B0, double pow, int bias) {
        int nLon = 40, nLat = 20;
        Vec3 toCam = cam.subtract(world);
        for (int j = 0; j < nLat; j++) {
            double la0 = Math.PI / 2 - Math.PI * j / nLat, la1 = Math.PI / 2 - Math.PI * (j + 1) / nLat;
            for (int i = 0; i < nLon; i++) {
                double lo0 = -Math.PI + 2 * Math.PI * i / nLon, lo1 = -Math.PI + 2 * Math.PI * (i + 1) / nLon;
                rimV(vc, c, r, la0, lo0, toCam, R0, G0, B0, pow, bias); rimV(vc, c, r, la1, lo0, toCam, R0, G0, B0, pow, bias);
                rimV(vc, c, r, la1, lo1, toCam, R0, G0, B0, pow, bias); rimV(vc, c, r, la0, lo1, toCam, R0, G0, B0, pow, bias);
                rimV(vc, c, r, la0, lo0, toCam, R0, G0, B0, pow, bias); rimV(vc, c, r, la0, lo1, toCam, R0, G0, B0, pow, bias);
                rimV(vc, c, r, la1, lo1, toCam, R0, G0, B0, pow, bias); rimV(vc, c, r, la1, lo0, toCam, R0, G0, B0, pow, bias);
            }
        }
    }
    static void rimV(VertexConsumer vc, Vec3 c, double r, double lat, double lon, Vec3 toCam, double R0, double G0, double B0, double pow, int bias) {
        Vec3 n = dir(lat, lon, 0);
        Vec3 p = n.scale(r);
        Vec3 v = toCam.subtract(p);
        double ndv = Math.abs(n.dot(v.normalize()));
        double f = Math.pow(1 - ndv, pow);
        if (bias < 0) f *= 0.25 + 0.75 * Math.max(0, -n.y);
        vc.addVertex((float) (c.x + p.x), (float) (c.y + p.y), (float) (c.z + p.z), argb(1, R0 * f, G0 * f, B0 * f), 0.5f, 0.5f, OV, FULL, 0f, 1f, 0f);
    }

    /** a→b 원기둥 (8면). */
    static void tube(VertexConsumer vc, Vec3 a, Vec3 b, double w, int col) {
        Vec3 d = b.subtract(a); double len = d.length(); if (len < 1e-3) return;
        Vec3 z = d.scale(1 / len);
        Vec3 x = Math.abs(z.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(z).normalize();
        Vec3 y = z.cross(x).normalize();
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            Vec3 o0 = x.scale(Math.cos(a0) * w).add(y.scale(Math.sin(a0) * w)), o1 = x.scale(Math.cos(a1) * w).add(y.scale(Math.sin(a1) * w));
            quad(vc, a.add(o0), b.add(o0), b.add(o1), a.add(o1), col, col, col, col);
        }
    }
    /** 수평 고리 (반지름 r, 폭 w). */
    static void ring(VertexConsumer vc, Vec3 c, double r, double w, int col) {
        int n = 96;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            Vec3 i0 = new Vec3(Math.cos(a0) * (r - w), 0, Math.sin(a0) * (r - w)), i1 = new Vec3(Math.cos(a1) * (r - w), 0, Math.sin(a1) * (r - w));
            Vec3 o0 = new Vec3(Math.cos(a0) * r, 0, Math.sin(a0) * r), o1 = new Vec3(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            quad(vc, c.add(i0), c.add(o0), c.add(o1), c.add(i1), 0, col, col, 0);
            quad(vc, c.add(o0), c.add(o0.scale((r + w) / r)), c.add(o1.scale((r + w) / r)), c.add(o1), col, 0, 0, col);
        }
    }
    /** 양면 쿼드 (컬링 방향과 무관하게 보임). */
    static void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        put(vc, a, ca); put(vc, b, cb); put(vc, c, cc); put(vc, d, cd);
        put(vc, a, ca); put(vc, d, cd); put(vc, c, cc); put(vc, b, cb);
    }
    static void put(VertexConsumer vc, Vec3 p, int col) {
        vc.addVertex((float) p.x, (float) p.y, (float) p.z, col, 0.5f, 0.5f, OV, FULL, 0f, 1f, 0f);
    }
}
