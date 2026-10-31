package com.tubilt.jjk.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tubilt.jjk.JjkMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 개발자의 장갑 — 진짜 3D 기계팔.
 *  · 1인칭: 카메라 오른쪽 아래에 입체 기계팔(톱니 · 피스톤 · 발광 이음매 · 총구)이 솟아 있고, 시점을 돌리면 같이 입체로 움직인다.
 *  · 3인칭/다른 플레이어: 어깨에서 시선 방향으로 같은 기계팔이 뻗어 나온다.
 * PlanetRender 와 같은 프레임 패스(카메라 기준 좌표)로 그린다.
 */
public final class GloveRender {
    private GloveRender() {}

    static final int STEEL = 0x4A5260, STEEL2 = 0x646E7E, STEEL3 = 0x8E9AAC, DARK = 0x1E2229, CYAN = 0x40E8FF, ORANGE = 0xFFB020, WHITE = 0xFFFFFF;
    static final Vec3 LIGHT = new Vec3(0.35, 0.85, 0.40).normalize();
    static final double[] FR = {0, 0.15, 0.45, 0.6};

    public static void init() {
        AddFramePassEvent.BUS.addListener(e -> e.addPass(JjkMod.id("glove3d"), new FramePassManager.PassDefinition() {
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
                try { frame(); } catch (Throwable ex) { JjkMod.LOG.warn("[JJK] 장갑 렌더 오류", ex); }
            }
        }));
    }

    static final class Arm { Vec3 o, R, U, F; double s; float fire; int kind; float q; }

    static long lastNs = 0, gestT0 = 0;
    static boolean prevUse = false;
    static float ease(float x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    /** 두 손: 손바닥이 마주 보게(안쪽으로 살짝 기울여 가운뎃손가락 끝이 맞닿게). o = 두 손 사이 중심. */
    static void addHands(List<Arm> arms, Vec3 o, Vec3 F0, Vec3 U0, Vec3 R0, double s, float q) {
        double a = 0.26;
        for (int side = -1; side <= 1; side += 2) {
            Arm h = new Arm();
            Vec3 toC = R0.scale(-side);   // 중심 쪽
            Vec3 hu = U0.scale(Math.cos(a)).add(toC.scale(Math.sin(a))).normalize();
            h.R = F0.normalize();                         // 손바닥 가로 (엄지는 −쪽 = 몸 쪽)
            h.U = hu;                                     // 손가락 방향
            Vec3 f = h.R.cross(h.U).normalize();
            if (f.dot(toC) < 0) f = f.scale(-1);          // 손바닥 면: 마주 보는 쪽
            h.F = f;
            h.o = o.add(R0.scale(side * 0.05 * s));
            h.s = s; h.kind = 1; h.q = q;
            arms.add(h);
        }
    }

    // ── 손 모델: 손바닥 + 손가락 4(3마디) + 엄지(2마디) + 소매. 로컬: r=가로(엄지 −), u=손가락 방향, f=손바닥 면(마주 보는 쪽)
    static final int SKIN = 0xE8B48C, SKIN2 = 0xD49E78, CUFF = 0x2A2D3A, SLEEVE = 0x15161E;
    static final double[] LEN_I = {.040, .025, .022}, LEN_M = {.043, .028, .024}, LEN_R = {.040, .026, .022}, LEN_P = {.032, .020, .019};
    // 복마어주자의 인: 가운뎃손가락만 곧게 세우고, 나머지 손가락은 말아 쥠 (끝 마디까지 안쪽으로)
    static final double[] BEND_I = {1.25, 1.5, 0.9}, BEND_M = {0.04, 0.0, 0.0}, BEND_R = {1.45, 1.6, 1.1}, BEND_P = {1.5, 1.7, 1.2};

    static void drawHand(Ctx c, float q, float t) {
        float g = ease(q * 1.15f), b = ease(Math.max(0, q * 1.25f - 0.15f));
        if (!c.glow) {
            obox(c, 0, 0, 0, .043, .046, .0125, 0, SKIN, 255);                       // 손바닥
            obox(c, 0, -.052, 0, .036, .012, .016, 0, SKIN2, 255);                   // 손목
            obox(c, 0, -.085, 0, .041, .020, .022, 0, CUFF, 255);                    // 커프스
            obox(c, 0, -.16, -.003, .05, .06, .032, 0, SLEEVE, 255);                 // 소매
            obox(c, .052, -.085, .0, .003, .006, .006, 0, 0xD8B040, 255);            // 금단추
            double[] rs = {-.0315, -.0105, .0105, .0315};
            double[][] lens = {LEN_I, LEN_M, LEN_R, LEN_P}, bends = {BEND_I, BEND_M, BEND_R, BEND_P};
            for (int i = 0; i < 4; i++) finger(c, rs[i], .046, lens[i], bends[i], g, b, i == 1 ? .0105 : .0095);
            finger(c, -.046, -.018, new double[]{.034, .028, 0}, new double[]{0.55, 0.85, 0}, g, b, .0105);   // 엄지
        } else if (q > 0.93f) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(t * 9);
            double tipU = .046 + (LEN_M[0] + LEN_M[1] + LEN_M[2]) * g;
            obox(c, -.0105, tipU + .012, 0, .008, .008, .008, 0, 0xFF3020, (int) (200 * pulse));
            obox(c, -.0105, tipU + .018, 0, .016, .016, .016, 0, 0xFF6040, (int) (70 * pulse));
            obox(c, -.0105, .046 + (LEN_M[0]) * g, .0, .0125, .002, .0125, 0, 0xFF3020, (int) (120 * pulse));   // 마디 빛
        }
    }

    static void finger(Ctx c, double r, double baseU, double[] len, double[] bend, double g, double b, double hw) {
        double u = baseU, f = 0, ang = 0;
        for (int i = 0; i < len.length; i++) {
            if (len[i] <= 0) break;
            ang += bend[i] * b;
            double L = len[i] * g, du = Math.cos(ang), df = Math.sin(ang);
            if (L < 1e-4) break;
            obox(c, r, u + du * L / 2, f + df * L / 2, hw, L / 2, hw * 0.9, ang, i == 2 ? SKIN2 : SKIN, 255);
            u += du * L; f += df * L;
        }
    }

    /** 회전된 상자: 중심(cr,cu,cf), 가로 반폭 hr, (u,f)평면에서 phi 만큼 기울어진 긴 축 반길이 hl · 두께 반폭 ht. phi=0 이면 +u 방향. */
    static void obox(Ctx c, double cr, double cu, double cf, double hr, double hl, double ht, double phi, int rgb, int a) {
        double cs = Math.cos(phi), sn = Math.sin(phi);
        double dU = cs, dF = sn, pU = -sn, pF = cs;   // d: 긴 축, p: 두께 축 ((u,f) 평면)
        double ndl = c.ul * cs + c.fl * sn, npl = -c.ul * sn + c.fl * cs;
        for (int sg = -1; sg <= 1; sg += 2) {
            // ±r 면
            int col = c.col(rgb, sg * c.rl, a);
            for (int[] k : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}})
                c.v(cr + sg * hr, cu + dU * hl * k[0] + pU * ht * k[1], cf + dF * hl * k[0] + pF * ht * k[1], col);
            // ±d 면
            col = c.col(rgb, sg * ndl, a);
            for (int[] k : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}})
                c.v(cr + hr * k[0], cu + dU * hl * sg + pU * ht * k[1], cf + dF * hl * sg + pF * ht * k[1], col);
            // ±p 면
            col = c.col(rgb, sg * npl, a);
            for (int[] k : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}})
                c.v(cr + hr * k[0], cu + dU * hl * k[1] + pU * ht * sg, cf + dF * hl * k[1] + pF * ht * sg, col);
        }
    }

    static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (ns - lastNs) / 1e9f);
        lastNs = ns;
        boolean using = mc.options.keyUse.isDown() && mc.screen == null && GloveHud.held(mc.player);
        GloveHud.fire += ((using ? 1f : 0f) - GloveHud.fire) * (1f - (float) Math.exp(-dt * 8));

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        boolean first = mc.options.getCameraType().isFirstPerson();
        float t = (System.currentTimeMillis() % 100000000L) / 1000f;

        List<Arm> arms = new ArrayList<>();
        // ── 개발자의 손동작: 우클릭 순간부터 손이 나타나 손가락이 돋아나며 인을 맺음
        boolean holdG = GloveHud.isGesture(mc.player.getMainHandItem()) || GloveHud.isGesture(mc.player.getOffhandItem());
        boolean useDown = mc.options.keyUse.isDown() && mc.screen == null;
        if (holdG && useDown && !prevUse) gestT0 = ns;
        prevUse = useDown;
        if (!holdG) gestT0 = 0;
        float gt = gestT0 == 0 ? -1 : (ns - gestT0) / 1e9f;
        float gq = gt < 0 || gt > 4.9 ? 0 : gt < 0.9 ? ease(gt / 0.9f) : gt < 4.0 ? 1 : 1 - ease((gt - 4.0f) / 0.9f);
        if (gq > 0.002f) {
            if (first) {
                float yr = camera.getYRot(), xr = camera.getXRot();
                Vec3 F0 = Vec3.directionFromRotation(xr, yr), U0 = Vec3.directionFromRotation(xr - 90, yr), R0 = F0.cross(U0);
                Vec3 o = cam.add(F0.scale(0.40)).add(U0.scale(-0.14 - 0.30 * (1 - ease(Math.min(1f, gq * 1.5f))))).add(R0.scale(0));
                addHands(arms, o, F0, U0, R0, 1.0, gq);
            } else {
                float yaw = mc.player.getViewYRot(pt);
                Vec3 Fh = Vec3.directionFromRotation(0, yaw), Uh = new Vec3(0, 1, 0), Rh = Fh.cross(Uh);
                Vec3 o = mc.player.getPosition(pt).add(0, mc.player.getBbHeight() * 0.66, 0).add(Fh.scale(0.6));
                addHands(arms, o, Fh, Uh, Rh, 2.2, gq);
            }
        }
        for (Player p : mc.level.players()) {
            if (p.isSpectator()) continue;
            boolean main = GloveHud.isGlove(p.getMainHandItem()), off = GloveHud.isGlove(p.getOffhandItem());
            if (!main && !off) continue;
            boolean rightSide = (p.getMainArm() == HumanoidArm.RIGHT) == main;
            double side = rightSide ? 1 : -1;
            Arm a = new Arm();
            if (p == mc.player && first) {
                float yr = camera.getYRot(), xr = camera.getXRot();
                Vec3 F0 = Vec3.directionFromRotation(xr, yr);
                Vec3 U0 = Vec3.directionFromRotation(xr - 90, yr);
                Vec3 R0 = F0.cross(U0);
                float fire = GloveHud.fire;
                a.F = F0.subtract(R0.scale(0.22 * side)).add(U0.scale(0.10)).normalize();
                a.R = a.F.cross(U0).normalize();
                a.U = a.R.cross(a.F).normalize();
                double kick = fire * 0.04 * (0.5 + 0.5 * Math.sin(t * 34));
                double bob = Math.sin(t * 1.3) * 0.004;
                a.o = cam.add(R0.scale(0.40 * side)).add(U0.scale(-0.36 + bob)).add(F0.scale(0.16 - kick));
                a.s = 1.0;
                a.fire = fire;
            } else {
                if (p.distanceToSqr(cam.x, cam.y, cam.z) > 96 * 96) continue;
                float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
                Vec3 F = Vec3.directionFromRotation(pitch, yaw);
                Vec3 U0 = Vec3.directionFromRotation(pitch - 90, yaw);
                Vec3 Fh = Vec3.directionFromRotation(0, yaw);
                Vec3 Rh = Fh.cross(new Vec3(0, 1, 0));
                double h = p.getBbHeight() * (p.isCrouching() ? 0.70 : 0.80);
                Vec3 pos = p.getPosition(pt);
                a.F = F;
                a.R = F.cross(U0).normalize();
                a.U = a.R.cross(a.F).normalize();
                a.o = pos.add(0, h, 0).add(Rh.scale(0.30 * side)).add(F.scale(0.12));
                a.s = 0.8;
                a.fire = p == mc.player ? GloveHud.fire : 0f;
            }
            a.o = a.o.subtract(cam);
            arms.add(a);
        }
        if (arms.isEmpty()) return;

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            RenderType rt = glow ? RenderType.eyes(PlanetRender.TEX_WHITE) : RenderType.entityCutoutNoCull(PlanetRender.TEX_WHITE);
            VertexConsumer vc = buf.getBuffer(rt);
            for (Arm a : arms) {
                Ctx c = new Ctx(vc, a, glow);
                if (a.kind == 1) { drawHand(c, a.q, t); continue; }
                float spin = t * (1.1f + a.fire * 5f);
                float pulse = 0.65f + 0.35f * (float) Math.sin(t * (4 + a.fire * 14));
                draw(c, t, spin, pulse, a.fire);
            }
            buf.endBatch(rt);
        }
    }

    // ── 지오메트리 컨텍스트: 팔 로컬(r=오른쪽, u=위, f=앞) → 카메라 기준 월드 좌표
    static final class Ctx {
        final VertexConsumer vc; final boolean glow; final double s;
        final double bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz;
        final double rl, ul, fl;   // 각 축과 빛 방향의 내적
        Ctx(VertexConsumer vc, Arm a, boolean glow) {
            this.vc = vc; this.glow = glow; this.s = a.s;
            bx = a.o.x; by = a.o.y; bz = a.o.z;
            rx = a.R.x; ry = a.R.y; rz = a.R.z; ux = a.U.x; uy = a.U.y; uz = a.U.z; fx = a.F.x; fy = a.F.y; fz = a.F.z;
            rl = a.R.dot(LIGHT); ul = a.U.dot(LIGHT); fl = a.F.dot(LIGHT);
        }
        void v(double r, double u, double f, int col) {
            r *= s; u *= s; f *= s;
            vc.addVertex((float) (bx + rx * r + ux * u + fx * f), (float) (by + ry * r + uy * u + fy * f), (float) (bz + rz * r + uz * u + fz * f),
                    col, 0.5f, 0.5f, PlanetRender.OV, PlanetRender.FULL, 0f, 1f, 0f);
        }
        /** dot = 면 법선·빛. 발광 패스는 음영 없이. */
        int col(int rgb, double dot, int a) {
            double k = glow ? 1.0 : 0.55 + 0.45 * (0.5 + 0.5 * dot);
            int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
            return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
        }
    }

    static void box(Ctx c, double cr, double cu, double cf, double hr, double hu, double hf, int rgb, int a) {
        for (int sg = -1; sg <= 1; sg += 2) {
            int col = c.col(rgb, sg * c.rl, a);
            c.v(cr + sg * hr, cu - hu, cf - hf, col); c.v(cr + sg * hr, cu + hu, cf - hf, col);
            c.v(cr + sg * hr, cu + hu, cf + hf, col); c.v(cr + sg * hr, cu - hu, cf + hf, col);
            col = c.col(rgb, sg * c.ul, a);
            c.v(cr - hr, cu + sg * hu, cf - hf, col); c.v(cr + hr, cu + sg * hu, cf - hf, col);
            c.v(cr + hr, cu + sg * hu, cf + hf, col); c.v(cr - hr, cu + sg * hu, cf + hf, col);
            col = c.col(rgb, sg * c.fl, a);
            c.v(cr - hr, cu - hu, cf + sg * hf, col); c.v(cr + hr, cu - hu, cf + sg * hf, col);
            c.v(cr + hr, cu + hu, cf + sg * hf, col); c.v(cr - hr, cu + hu, cf + sg * hf, col);
        }
    }

    /** 오른쪽(r) 축 원기둥/톱니바퀴. teeth=0 이면 둥근 원판. */
    static void prism(Ctx c, double cr, double cu, double cf, double rad, double th, int teeth, double ang, int rgb) {
        int n = teeth > 0 ? teeth * 4 : 14;
        double[] pu = new double[n], pf = new double[n];
        for (int i = 0; i < n; i++) {
            double a, rr;
            if (teeth > 0) {
                int k = i / 4, j = i % 4;
                a = ang + (k + FR[j]) * Math.PI * 2 / teeth;
                rr = (j == 1 || j == 2) ? rad : rad * 0.80;
            } else { a = ang + i * Math.PI * 2 / n; rr = rad; }
            pf[i] = cf + Math.cos(a) * rr; pu[i] = cu + Math.sin(a) * rr;
        }
        double h = th / 2;
        int capP = c.col(rgb, c.rl, 255), capN = c.col(rgb, -c.rl, 255);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double nf = (pf[i] + pf[j]) / 2 - cf, nu = (pu[i] + pu[j]) / 2 - cu, len = Math.max(1e-6, Math.hypot(nf, nu));
            int wall = c.col(rgb, (c.fl * nf + c.ul * nu) / len, 255);
            c.v(cr - h, pu[i], pf[i], wall); c.v(cr + h, pu[i], pf[i], wall); c.v(cr + h, pu[j], pf[j], wall); c.v(cr - h, pu[j], pf[j], wall);
            c.v(cr + h, cu, cf, capP); c.v(cr + h, pu[i], pf[i], capP); c.v(cr + h, pu[j], pf[j], capP); c.v(cr + h, pu[j], pf[j], capP);
            c.v(cr - h, cu, cf, capN); c.v(cr - h, pu[i], pf[i], capN); c.v(cr - h, pu[j], pf[j], capN); c.v(cr - h, pu[j], pf[j], capN);
        }
    }

    /** 톱니 한 쌍(양옆) — 몸통 + 돌출 허브. 발광 패스에선 회전하는 점 6개. */
    static void gear(Ctx c, double r, double cu, double cf, double rad, double th, int teeth, double ang, int body, int glowRgb, int glowA) {
        for (int sg = -1; sg <= 1; sg += 2) {
            double cr = sg * r;
            if (!c.glow) {
                prism(c, cr, cu, cf, rad, th, teeth, ang, body);
                prism(c, cr + sg * th * 0.1, cu, cf, rad * 0.55, th * 1.25, 0, 0, STEEL);
                prism(c, cr + sg * th * 0.2, cu, cf, rad * 0.22, th * 1.6, 0, 0, DARK);
            } else {
                for (int i = 0; i < 6; i++) {
                    double a = ang + i * Math.PI / 3;
                    double hh = rad * 0.07;
                    box(c, cr + sg * (th * 0.62), cu + Math.sin(a) * rad * 0.40, cf + Math.cos(a) * rad * 0.40, 0.003, hh, hh, glowRgb, glowA);
                }
            }
        }
    }

    static void draw(Ctx c, float t, float spin, float pulse, float fire) {
        int glowA = (int) (150 + 105 * pulse);
        double pis = Math.sin(t * (2 + fire * 8)) * 0.012;
        if (!c.glow) {
            box(c, 0, 0, -0.03, .085, .075, .06, STEEL, 255);          // 어깨
            box(c, 0, 0, .16, .062, .062, .13, STEEL2, 255);           // 위팔
            box(c, 0, 0, .50, .052, .052, .14, STEEL2, 255);           // 아래팔
            box(c, 0, .05, .47, .058, .018, .08, STEEL3, 255);         // 윗덮개
            box(c, 0, -.058, .30, .05, .012, .16, DARK, 255);          // 아랫 배관
            for (int sg = -1; sg <= 1; sg += 2) {                      // 피스톤
                box(c, sg * .05, .075, .16 + pis * sg, .008, .008, .09, 0xC0CCDC, 255);
                box(c, sg * .05, .075, .11, .014, .014, .04, DARK, 255);
            }
            box(c, 0, 0, .72, .04, .04, .10, DARK, 255);               // 총구
            for (int i = 0; i < 3; i++) box(c, 0, 0, .66 + i * .045, .047, .047, .008, STEEL3, 255);
        }
        gear(c, .085, 0, .31, .115, .028, 14, spin, STEEL2, CYAN, glowA);                  // 팔꿈치 거대 톱니
        gear(c, .085, .11, .17, .06, .026, 8, -spin * 1.75, STEEL, CYAN, glowA);          // 맞물린 작은 톱니들
        gear(c, .085, -.14, .39, .055, .026, 8, -spin * 2.0, STEEL, CYAN, glowA);
        gear(c, .07, .10, .52, .05, .024, 8, spin * 2.6, STEEL2, CYAN, glowA);
        if (c.glow) {
            for (double f : new double[]{.10, .22, .46, .56}) {                              // 발광 이음매
                double hw = f < .3 ? .066 : .054;
                box(c, 0, 0, f, hw, hw, .006, CYAN, glowA / 2);
            }
            double ms = .018 + fire * .07 + .008 * pulse;                                     // 총구 섬광
            int core = (int) (120 + 135 * (0.5f + 0.5f * fire) * pulse);
            box(c, 0, 0, .83, .022, .022, .014, CYAN, core);
            box(c, 0, 0, .85, ms, ms, ms, WHITE, (int) (90 + 120 * fire));
            box(c, 0, 0, .85 + ms * 1.5, ms * .35, ms * .35, ms * 2.6, CYAN, (int) (60 + 130 * fire));
            box(c, 0, 0, .85, ms * 2.2, ms * .3, ms * .3, CYAN, (int) (40 + 100 * fire));
            box(c, 0, 0, .85, ms * .3, ms * 2.2, ms * .3, CYAN, (int) (40 + 100 * fire));
            long seed = System.currentTimeMillis() / 90;                                       // 불꽃
            for (int i = 0; i < 6 + (int) (fire * 14); i++) {
                long h = (seed * 31 + i * 7919L) * 2654435761L;
                double r = ((h >> 3) % 200) / 1000.0 - .1, u = ((h >> 11) % 220) / 1000.0 - .1, f = ((h >> 17) % 850) / 1000.0;
                double sz = .005 + ((h >> 25) % 5) / 1000.0;
                box(c, r, u, f, sz, sz, sz, i % 3 == 0 ? ORANGE : CYAN, 230);
            }
        }
    }
}
