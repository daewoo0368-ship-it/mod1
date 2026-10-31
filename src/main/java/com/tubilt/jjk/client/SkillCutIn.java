package com.tubilt.jjk.client;

import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * 스킬 컷인 (실시간 ms, 슬로모션 영향 없음).
 *  kind 0: 화면 아래쪽, 사선 띠 이름판이 왼쪽에서 미끄러져 들어와 머물다 오른쪽으로 빠짐
 *  kind 1: 궁극기 — 화면이 어두워지고 사선 띠 2줄이 화면을 가로지르며, 집중선 + 거대한 기술명 + 부제
 */
public final class SkillCutIn {
    private SkillCutIn() {}

    static String title = "", sub = ""; static int color, kind; static long start = 0;
    static final Random R = new Random();

    public static void start(Payloads.CutIn c) {
        if (c.kind() == 2) { CmdConsole.push(c.title(), c.color()); return; }   // 명령 콘솔 타이핑
        title = c.title(); sub = c.sub(); color = c.color() & 0xFFFFFF; kind = c.kind(); start = System.currentTimeMillis();
    }

    static long dur() { return kind == 1 ? 1700 : 1300; }

    static void render(GuiGraphics g, Font font, int w, int h) {
        long el = System.currentTimeMillis() - start;
        if (start == 0 || el > dur()) return;
        double u = el / (double) dur();
        if (kind == 1) ult(g, font, w, h, u, el); else plate(g, font, w, h, u);
    }

    static double ease(double x) { x = Math.max(0, Math.min(1, x)); return 1 - Math.pow(1 - x, 3); }

    /** 사선 띠: y0~y0+th, 기울기 slope(px/px), 왼쪽 x0 ~ 오른쪽 x1. */
    static void slant(GuiGraphics g, int x0, int x1, int y0, int th, double slope, int argb) {
        for (int y = 0; y < th; y++) {
            int off = (int) ((th - y) * slope);
            g.fill(x0 + off, y0 + y, x1 + off, y0 + y + 1, argb);
        }
    }

    static void plate(GuiGraphics g, Font font, int w, int h, double u) {
        double in = ease(u / 0.18), out = u > 0.8 ? ease((u - 0.8) / 0.2) : 0;
        int tw = Math.max(font.width(title) * 2, font.width(sub)) + 60;
        int cx = (int) (-tw + (w / 2.0 + tw / 2.0) * in + (w / 2.0 + tw) * out);
        int y = (int) (h * 0.56);        // 명령 콘솔·핫바보다 위
        int a = (int) (230 * (1 - out));
        slant(g, cx - tw / 2 - 6, cx + tw / 2 + 6, y - 4, 34, 0.35, (a << 24) | 0x080A12);
        slant(g, cx - tw / 2 - 6, cx - tw / 2 + 2, y - 4, 34, 0.35, (a << 24) | color);
        slant(g, cx - tw / 2, cx + tw / 2, y + 30, 2, 0.35, (a << 24) | color);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, y + 2);
        pose.scale(2f, 2f);
        g.drawCenteredString(font, "§l" + title, 0, 0, ((int) (255 * (1 - out)) << 24) | 0xFFFFFF);
        pose.popMatrix();
        g.drawCenteredString(font, sub, cx, y + 21, ((int) (200 * (1 - out)) << 24) | color);
    }

    static void ult(GuiGraphics g, Font font, int w, int h, double u, long el) {
        double in = ease(u / 0.12), out = u > 0.82 ? ease((u - 0.82) / 0.18) : 0;
        int dim = (int) (110 * Math.min(1, u / 0.08) * (1 - out));
        g.fill(0, 0, w, h, dim << 24);
        // 레터박스
        int bar = (int) (h / 9 * (1 - out) * Math.min(1, u / 0.1));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // 집중선 (가로 속도선)
        R.setSeed(el / 40);
        for (int i = 0; i < 26; i++) {
            int y = R.nextInt(h), len = 60 + R.nextInt(w / 2), x = (int) ((R.nextInt(w) + el * 3) % (w + len)) - len;
            g.fill(x, y, x + len, y + 1, (int) (90 * (1 - out)) << 24 | 0xFFFFFF);
        }
        // 사선 띠 2줄이 반대 방향으로 가로지름
        int cy = h / 2, band = Math.max(46, h / 7);
        int sweep = (int) (w * 1.4 * in) - (int) (w * 0.2), back = (int) (w * 1.2 * out);
        slant(g, -w + sweep + back, sweep + back, cy - band / 2 - 8, band + 16, 0.5, (int) (225 * (1 - out)) << 24 | 0x06070C);
        slant(g, -w + sweep + back, sweep + back, cy - band / 2 - 8, 4, 0.5, (int) (255 * (1 - out)) << 24 | color);
        slant(g, w - sweep - back, 2 * w - sweep - back, cy + band / 2 + 4, 4, 0.5, (int) (255 * (1 - out)) << 24 | color);
        slant(g, w - sweep - back, 2 * w - sweep - back, cy + band / 2 + 12, 10, 0.5, (int) (120 * (1 - out)) << 24 | color);
        // 기술명 (살짝 커지며 등장, 그림자 + 색 번짐)
        if (u > 0.06) {
            float sc = (float) (Math.max(2.4, h / 60.0) * (1.25 - 0.25 * ease((u - 0.06) / 0.2)));
            int ta = (int) (255 * Math.min(1, (u - 0.06) / 0.08) * (1 - out));
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(w / 2f + (float) (out * w * 0.6), cy - 6);
            pose.scale(sc, sc);
            g.drawCenteredString(font, "§l" + title, -1, -4, (ta / 2 << 24) | color);
            g.drawCenteredString(font, "§l" + title, 1, -3, (ta / 2 << 24) | 0x000000);
            g.drawCenteredString(font, "§l" + title, 0, -4, (ta << 24) | 0xFFFFFF);
            pose.popMatrix();
            g.drawCenteredString(font, sub, w / 2 - (int) (out * w * 0.6), cy + band / 2 - 14, (ta << 24) | color);
        }
        // 등장 순간 섬광
        if (u < 0.06) g.fill(0, 0, w, h, ((int) (160 * (1 - u / 0.06)) << 24) | color);
    }
}
