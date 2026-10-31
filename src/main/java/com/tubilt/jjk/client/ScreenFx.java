package com.tubilt.jjk.client;

import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.Random;

/**
 * 화면 반응 효과 (실시간 ms, 슬로모션과 무관):
 *  FOV 펀치(확 벌어졌다 돌아옴) / FOV 줌인(충전) / 카메라 롤 흔들림 / 임팩트 프레임(흑→백 깜빡) / 색수차 / 하늘·안개 색 / 방사형 집중선
 */
public final class ScreenFx {
    private ScreenFx() {}

    static final Random R = new Random();
    static long fovS, fovD; static float fovA;
    /** 마지막으로 적용된 FOV (도). */
    static float lastFov = 70f;
    static long rollS, rollD; static float rollA;
    static long frameS;
    static long chromaS, chromaD; static float chromaA;
    static long tintS, tintD; static int tintC; static float tintA;
    static long linesS, linesD; static int linesC;

    static long now() { return System.currentTimeMillis(); }
    static double k(long s, long d) { long e = now() - s; return d <= 0 || e < 0 || e > d ? -1 : e / (double) d; }

    public static void start(Payloads.Screen s) {
        long n = now();
        switch (s.kind()) {
            case 1 -> { if (k(fovS, fovD) < 0 || s.power() >= fovA) { fovS = n; fovD = s.ms(); fovA = s.power(); } }
            case 7 -> { fovS = n; fovD = s.ms(); fovA = -s.power(); }
            case 2 -> { if (k(rollS, rollD) < 0 || s.power() >= rollA * (1 - k(rollS, rollD))) { rollS = n; rollD = s.ms(); rollA = s.power(); } }
            case 3 -> frameS = n;
            case 4 -> { chromaS = n; chromaD = s.ms(); chromaA = s.power(); }
            case 5 -> { tintS = n; tintD = s.ms(); tintC = s.color(); tintA = s.power(); }
            case 6 -> { linesS = n; linesD = s.ms(); linesC = s.color(); }
            default -> { }
        }
    }

    static void init() {
        ViewportEvent.ComputeFov.BUS.addListener(e -> {
            boolean planet = PlanetCine.renderActive();   // 행성 연출 중엔 FOV 고정 → 셰이더 광선과 월드가 정확히 겹침
            double base = planet ? PlanetCine.fov() : e.getFOV(), out = base;
            double u = k(fovS, fovD);
            if (u >= 0) {
                double f = fovA > 0 ? (u < 0.12 ? u / 0.12 : Math.pow(1 - (u - 0.12) / 0.88, 2)) : Math.sin(Math.PI * Math.min(1, u * 1.2));
                out = base * (1 + fovA * f);
            }
            if (planet || u >= 0) e.setFOV((float) out);
            lastFov = (float) out;
        });
        ViewportEvent.ComputeCameraAngles.BUS.addListener(e -> {
            if (PlanetCine.renderActive()) return;   // 행성 연출 중엔 셰이더 광선과 어긋나지 않게 롤 흔들림 생략
            double u = k(rollS, rollD);
            if (u < 0) return;
            double amp = rollA * Math.pow(1 - u, 2);
            double t = (now() - rollS) / 1000.0;
            e.setRoll((float) (e.getRoll() + amp * Math.sin(t * 38) ));
            e.setPitch((float) (e.getPitch() + amp * 0.25 * Math.sin(t * 51 + 1)));
            e.setYaw((float) (e.getYaw() + amp * 0.25 * Math.sin(t * 44 + 2)));
        });
        ViewportEvent.ComputeFogColor.BUS.addListener(e -> {
            double u = k(tintS, tintD);
            if (u < 0) return;
            double a = tintA * Math.min(1, Math.min(u / 0.1, (1 - u) / 0.2));
            float r = (tintC >> 16 & 255) / 255f, g = (tintC >> 8 & 255) / 255f, b = (tintC & 255) / 255f;
            e.setRed((float) (e.getRed() + (r - e.getRed()) * a));
            e.setGreen((float) (e.getGreen() + (g - e.getGreen()) * a));
            e.setBlue((float) (e.getBlue() + (b - e.getBlue()) * a));
        });
    }

    static void render(GuiGraphics g, int w, int h) {
        long n = now();
        // 하늘·안개 색에 맞춘 화면 가장자리 물듦
        double ut = k(tintS, tintD);
        if (ut >= 0) {
            int a = (int) (90 * tintA * Math.min(1, Math.min(ut / 0.1, (1 - ut) / 0.2)));
            for (int i = 0; i < 12; i++) {
                int aa = (int) (a * (1 - i / 12.0) * (1 - i / 12.0));
                int c = (aa << 24) | (tintC & 0xFFFFFF);
                g.fill(0, i * 5, w, i * 5 + 5, c); g.fill(0, h - i * 5 - 5, w, h - i * 5, c);
                g.fill(i * 5, 0, i * 5 + 5, h, c); g.fill(w - i * 5 - 5, 0, w - i * 5, h, c);
            }
        }
        // 방사형 집중선
        double ul = k(linesS, linesD);
        if (ul >= 0) {
            R.setSeed(n / 50);
            var pose = g.pose();
            int a = (int) (170 * Math.sin(Math.PI * ul));
            for (int i = 0; i < 42; i++) {
                float ang = (float) (R.nextDouble() * Math.PI * 2);
                int inner = (int) (Math.min(w, h) * (0.28 + R.nextDouble() * 0.18)), len = (int) (Math.max(w, h) * 0.8);
                int th = 1 + R.nextInt(3);
                pose.pushMatrix();
                pose.translate(w / 2f, h / 2f);
                pose.rotate(ang);
                g.fill(inner, -th, inner + len, th, (a << 24) | (R.nextInt(3) == 0 ? (linesC & 0xFFFFFF) : 0xFFFFFF));
                pose.popMatrix();
            }
        }
        // 색수차: 붉은·청록 가장자리가 어긋나며 떨림
        double uc = k(chromaS, chromaD);
        if (uc >= 0) {
            float p = (float) (chromaA * (1 - uc));
            int off = (int) (6 * p) + 1, a = (int) (110 * p);
            R.setSeed(n / 30);
            int jx = R.nextInt(off * 2 + 1) - off;
            g.fill(0, 0, Math.max(1, off * 4 + jx), h, (a << 24) | 0xFF2020);
            g.fill(w - Math.max(1, off * 4 - jx), 0, w, h, (a << 24) | 0x20E8FF);
            for (int i = 0; i < 6; i++) {
                int y = R.nextInt(h), hh = 1 + R.nextInt(4), sh = R.nextInt(off * 6 + 1) - off * 3;
                g.fill(Math.max(0, sh), y, Math.min(w, w + sh), y + hh, ((a / 2) << 24) | (R.nextBoolean() ? 0xFF3030 : 0x30E8FF));
            }
        }
        // 임팩트 프레임: 검게 → 하얗게 → 사라짐 (약 0.16초)
        long fe = n - frameS;
        if (frameS > 0 && fe < 170) {
            if (fe < 55) g.fill(0, 0, w, h, 0xE6000000);
            else if (fe < 110) g.fill(0, 0, w, h, 0xD0FFFFFF);
            else g.fill(0, 0, w, h, ((int) (200 * (1 - (fe - 110) / 60.0)) << 24) | 0xFFFFFF);
        }
    }
}
