package com.tubilt.jjk.client;

import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 개발자의 버튼 컷신 (클라이언트): 카메라 경로 · 레터박스 · 자막.
 * 카메라는 CameraMixin 이 매 프레임 shot() 값으로 덮어씀.
 */
public final class Cinematic {
    private Cinematic() {}

    public static final int LENGTH = 560, RADIUS = 50;
    static final double K = RADIUS / 50.0;
    static boolean active = false;
    static long startTick;
    static Vec3 P, T, F, R, S;
    static final Random RNG = new Random();

    public record Shot(Vec3 pos, float yaw, float pitch) {}

    /** 키프레임: [틱, 카메라 위치, 바라볼 곳] */
    private record Key(double t, Vec3 pos, Vec3 look) {}
    static Key[] keys;

    public static void start(Payloads.Cine c) {
        P = new Vec3(c.px(), c.py(), c.pz());
        T = new Vec3(c.tx(), c.ty(), c.tz());
        Vec3 f = new Vec3(T.x - P.x, 0, T.z - P.z);
        F = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        R = new Vec3(-F.z, 0, F.x);
        S = T.add(0, 90 + RADIUS, 0);
        Vec3 B = P.add(F.scale(2.2)).add(0, -0.5, 0);
        double d = Math.max(30, Math.sqrt((T.x - P.x) * (T.x - P.x) + (T.z - P.z) * (T.z - P.z)));
        keys = new Key[]{
                // 0~40: 떠오른 버튼 클로즈업 → 누르는 순간
                new Key(0, B.add(R.scale(1.8)).add(F.scale(-0.6)).add(0, 0.5, 0), B),
                new Key(22, B.add(R.scale(1.1)).add(F.scale(-0.2)).add(0, 1.0, 0), B.add(0, 0.3, 0)),
                new Key(38, B.add(R.scale(0.6)).add(0, 1.6, 0), B.add(0, 0.3, 0)),
                // 40~100: 어깨 너머 → 하늘로 솟구치며 목표 지점·지구본 내려다봄
                new Key(50, P.subtract(F.scale(3)).add(R.scale(1.2)).add(0, 0.8, 0), T),
                new Key(74, P.subtract(F.scale(14)).add(R.scale(7)).add(0, 26, 0), T.add(0, 30 * K, 0)),
                new Key(98, P.subtract(F.scale(26)).add(R.scale(14)).add(0, 52, 0), T.add(0, 45 * K, 0)),
                // 100~200: 목표 옆에서 하늘의 위성을 올려다보며 천천히 선회
                new Key(110, T.add(tk(110, -40, 30)), S),
                new Key(160, T.add(tk(80, 60, 70)), S),
                new Key(198, T.add(tk(-40, 100, 90)), T.add(0, 70 * K, 0)),
                // 200~260: 빛기둥 하강 — 측면 중거리
                new Key(205, T.add(tk(-95, -20, 25)), T.add(0, 60 * K, 0)),
                new Key(258, T.add(tk(-110, -60, 15)), T.add(0, 30 * K, 0)),
                // 260~320: 삭제 — 아주 먼 와이드 샷
                new Key(262, T.add(tk(0, -170, 45)), T),
                new Key(318, T.add(tk(40, -190, 70)), T),
                // 320~400: 분화구 선회
                new Key(330, T.add(tk(90, 0, 75)), T.add(0, -20 * K, 0)),
                new Key(365, T.add(tk(30, 90, 70)), T.add(0, -25 * K, 0)),
                // 400~448: 잔류 코어로 천천히 다가감
                new Key(398, T.add(tk(70, -20, 40)), T.add(0, 6, 0)),
                new Key(444, T.add(tk(22, -18, 14)), T.add(0, 6, 0)),
                // 448~: 강제 종료 섬광에 밀려 뒤로 크게 빠지며 하늘의 코드 기둥을 올려다봄
                new Key(458, T.add(tk(30, -120, 30)), T.add(0, 60 * K, 0)),
                new Key(500, T.add(tk(-60, -150, 50)), T.add(0, 30 * K, 0)),
                // 500~560: 플레이어 등 뒤 → 손의 버튼 → 눈으로 복귀
                new Key(530, P.subtract(F.scale(6)).add(R.scale(2.5)).add(0, 3, 0), T),
                new Key(546, B.add(R.scale(1.4)).add(F.scale(-0.8)).add(0, 0.9, 0), B.add(0, 0.3, 0)),
                new Key(560, P, T)};
        startTick = now();
        active = true;
    }

    /** 목표 기준 카메라 오프셋 (원래 r=50 기준 수치) → 구 크기만큼 키우되, 안개·렌더 거리 안(≈190블럭)으로 제한. */
    static Vec3 tk(double r, double f, double up) {
        Vec3 o = R.scale(r).add(F.scale(f)).add(0, up, 0);
        double len = o.length();
        return o.scale(len < 1e-3 ? 1 : Math.min(K, 190 / len));
    }

    static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }

    public static boolean active() {
        if (active && (now() - startTick > LENGTH || Minecraft.getInstance().level == null)) active = false;
        return active;
    }

    static double time(float pt) { return (now() - startTick) + pt; }

    static double smooth(double u) { return u * u * (3 - 2 * u); }

    /** 이번 프레임 카메라. */
    public static Shot shot(float pt) {
        if (!active()) return null;
        double t = time(pt);
        Key a = keys[0], b = keys[keys.length - 1];
        for (int i = 0; i < keys.length - 1; i++) if (t >= keys[i].t && t <= keys[i + 1].t) { a = keys[i]; b = keys[i + 1]; break; }
        double u = b.t == a.t ? 1 : smooth(Math.max(0, Math.min(1, (t - a.t) / (b.t - a.t))));
        Vec3 pos = a.pos.lerp(b.pos, u), look = a.look.lerp(b.look, u);
        // 화면 흔들림: 빛기둥·삭제 구간
        double shake = t > 200 && t < 330 ? (t < 260 ? 0.4 + (t - 200) / 60.0 * 1.2 : 2.2 - (t - 260) / 70.0 * 2.0) : (t > 22 && t < 30 ? 0.08 : 0);
        if (t > 400 && t < 448) shake = Math.max(shake, (t - 400) / 48.0 * 0.6);
        if (t >= 448 && t < 490) shake = Math.max(shake, 3.0 * (1 - (t - 448) / 42.0));
        if (shake > 0) pos = pos.add((RNG.nextDouble() - 0.5) * shake, (RNG.nextDouble() - 0.5) * shake, (RNG.nextDouble() - 0.5) * shake);
        Vec3 d = look.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new Shot(pos, yaw, pitch);
    }

    /** 시스템 로그: [시각, 색, 문구] */
    static final Object[][] LOG = {
            {2.0, 0x55FF55, "> sudo press --button developer"},
            {16.0, 0xAAAAAA, "  cover.open() ........ OK"},
            {8.0, 0xFFD84A, "  lock[1/3] ...... RELEASED"},
            {14.0, 0xFFD84A, "  lock[2/3] ...... RELEASED"},
            {20.0, 0xFFD84A, "  lock[3/3] ...... RELEASED"},
            {26.0, 0xFF5050, "[AUTH] DEVELOPER 인증 완료"},
            {48.0, 0xFFD84A, "[WARN] 목표 좌표 고정"},
            {56.0, 0x40E8FF, "[NET ] 전 지구 좌표망 접속"},
            {70.0, 0xFF5050, "[ZONE] 삭제 구역 d=255 설정"},
            {102.0, 0x40E8FF, "[ORBT] 궤도 배열 기동"},
            {122.0, 0x40E8FF, "[ORBT] 보조 위성 6기 전개"},
            {150.0, 0xFF5050, "[ORBT] 헥사그램 연결 · 에너지 임계치 접근"},
            {178.0, 0xFFD84A, "[SYNC] 6/6 집속"},
            {200.0, 0xFF3030, "[FIRE] 삭제 광선 발사"},
            {262.0, 0xFFFFFF, "[EXEC] rm -rf ./sphere --radius 127"},
            {300.0, 0x55FF55, "[ OK ] 구역 소멸 확인"},
            {402.0, 0xFF5050, "[WARN] 잔류 데이터 코어 감지"},
            {440.0, 0xFF3030, "[EXEC] kill -9 core"},
            {470.0, 0x55FF55, "[ OK ] PROCESS TERMINATED"},
            {540.0, 0xAAAAAA, "> exit 0"}};

    /** 레터박스 + 비네트 + 로그 + 충전 게이지 + 카운트다운 + 자막 + 섬광. HUD 대신 그림. */
    public static void render(GuiGraphics g, Font font, int w, int h) {
        double t = time(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        double slide = Math.max(0, Math.min(1, Math.min(t / 8.0, (LENGTH - t) / 8.0)));
        int bar = (int) (h / 8 * slide);
        // 비네트
        g.fillGradient(0, bar, w, bar + h / 4, 0x80000000, 0x00000000);
        g.fillGradient(0, h - bar - h / 4, w, h - bar, 0x00000000, 0x80000000);
        for (int i = 0; i < 16; i++) {
            int a = (int) (110 * (1 - i / 16.0) * (1 - i / 16.0));
            g.fill(i * 4, bar, i * 4 + 4, h - bar, a << 24);
            g.fill(w - i * 4 - 4, bar, w - i * 4, h - bar, a << 24);
        }
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (bar > 2) { g.fill(0, bar - 1, w, bar, 0x60FF3030); g.fill(0, h - bar, w, h - bar + 1, 0x60FF3030); }
        // 위성 충전 중 붉은 맥동
        if (t > 100 && t < 200) {
            int a = (int) (36 * Math.abs(Math.sin(t * 0.25)));
            g.fill(0, bar, w, h - bar, (a << 24) | 0xFF0000);
        }
        // 삭제 순간: 흰 섬광 + 색수차 글리치
        if (t > 258 && t < 290) {
            int a = (int) (255 * (t < 264 ? (t - 258) / 6.0 : 1 - (t - 264) / 26.0));
            g.fill(0, 0, w, h, (Math.max(0, Math.min(255, a)) << 24) | 0xFFFFFF);
        }
        if (t > 256 && t < 300) {
            RNG.setSeed((long) (t * 3));
            for (int i = 0; i < 7; i++) {
                int y = RNG.nextInt(h), hh = 2 + RNG.nextInt(8), off = RNG.nextInt(30) - 15;
                g.fill(Math.max(0, off), y, Math.min(w, w + off), y + hh, RNG.nextBoolean() ? 0x40FF2020 : 0x4020E8FF);
            }
        }
        // 시스템 로그 (좌상단, 타자 효과)
        int ly = bar + 8, shown = 0, visible = 0;
        for (Object[] e : LOG) if (t >= (double) e[0]) visible++;
        int skip = Math.max(0, visible - 10);
        for (Object[] e : LOG) {
            double t0 = (double) e[0];
            if (t < t0) continue;
            if (skip-- > 0) continue;
            String s = (String) e[2];
            int n = (int) Math.min(s.length(), (t - t0) * 2.2 + 1);
            String vis = s.substring(0, n) + (n < s.length() && ((long) t / 3) % 2 == 0 ? "▌" : "");
            g.fill(8, ly - 2, 14 + font.width(s), ly + 9, 0x70000000);
            g.drawString(font, vis, 11, ly, 0xFF000000 | (int) e[1], true);
            ly += 11;
            if (++shown > 9) break;
        }
        // 목표 좌표 (우상단)
        if (t > 48 && T != null) {
            String c = String.format("TARGET  X %.0f  Y %.0f  Z %.0f", T.x, T.y, T.z);
            int cw = font.width(c);
            boolean bl = ((long) t / 4) % 2 == 0;
            g.fill(w - cw - 16, bar + 6, w - 6, bar + 18, 0x70000000);
            g.drawString(font, c, w - cw - 11, bar + 8, bl ? 0xFFFF4040 : 0xFFFFFFFF, true);
        }
        // 스캔라인 + 화면 테두리 HUD 모서리
        for (int y = bar; y < h - bar; y += 3) g.fill(0, y, w, y + 1, 0x0C000000);
        int m = 14, L = 26, cc = ((long) t / 6) % 2 == 0 ? 0xC0FF3030 : 0x90FF3030;
        g.fill(m, bar + m, m + L, bar + m + 2, cc); g.fill(m, bar + m, m + 2, bar + m + L, cc);
        g.fill(w - m - L, bar + m, w - m, bar + m + 2, cc); g.fill(w - m - 2, bar + m, w - m, bar + m + L, cc);
        g.fill(m, h - bar - m - 2, m + L, h - bar - m, cc); g.fill(m, h - bar - m - L, m + 2, h - bar - m, cc);
        g.fill(w - m - L, h - bar - m - 2, w - m, h - bar - m, cc); g.fill(w - m - 2, h - bar - m - L, w - m, h - bar - m, cc);
        // 조준 레티클 (목표를 정면으로 보는 구간)
        if ((t > 50 && t < 98) || (t > 205 && t < 258) || (t > 400 && t < 446)) {
            double sp = t * 0.08;
            int cx = w / 2, cy = h / 2, r = (int) (34 + 6 * Math.sin(t * 0.3));
            for (int k = 0; k < 4; k++) {
                double a = sp + k * Math.PI / 2;
                int x0 = cx + (int) (Math.cos(a) * r), y0 = cy + (int) (Math.sin(a) * r);
                g.fill(x0 - 5, y0 - 1, x0 + 5, y0 + 1, 0xE0FF3030);
                g.fill(x0 - 1, y0 - 5, x0 + 1, y0 + 5, 0xE0FF3030);
            }
            g.fill(cx - 1, cy - 12, cx + 1, cy - 4, 0xFFFFFFFF); g.fill(cx - 1, cy + 4, cx + 1, cy + 12, 0xFFFFFFFF);
            g.fill(cx - 12, cy - 1, cx - 4, cy + 1, 0xFFFFFFFF); g.fill(cx + 4, cy - 1, cx + 12, cy + 1, 0xFFFFFFFF);
            String lk = t > 400 ? "CORE LOCK" : "TARGET LOCK";
            g.drawCenteredString(font, (((long) t / 5) % 2 == 0 ? "§c§l" : "§f§l") + lk, cx, cy + r + 10, 0xFFFFFFFF);
        }
        // 위성 원격 측정 패널 (우측)
        if (t > 104 && t < 262) {
            double k = Math.min(1, (t - 104) / 92.0);
            int px = w - 150, py = bar + 26;
            g.fill(px, py, w - 8, py + 70, 0x90000000);
            g.fill(px, py, px + 2, py + 70, 0xFFFF3030);
            int sync = (int) Math.min(6, Math.max(0, (t - 176) / 3.0 + (t >= 176 ? 1 : 0)));
            String[] ln = {"§c§lORBITAL ARRAY", String.format("§7ALT  §f%.1f km", 140 + Math.sin(t * 0.05) * 0.3),
                    String.format("§7PWR  §f%d%%", (int) (k * 100)), String.format("§7TEMP §f%d K", 3000 + (int) (k * 9000)),
                    "§7SYNC §f" + "■".repeat(sync) + "§8" + "□".repeat(6 - sync), t > 200 ? "§c§l▶ FIRING" : "§e▶ CHARGING"};
            for (int i = 0; i < ln.length; i++) g.drawString(font, ln[i], px + 7, py + 5 + i * 10, 0xFFFFFFFF, true);
        }
        // 피날레: 코어 붕괴 섬광, 마지막 페이드
        if (t > 446 && t < 476) {
            int a = (int) (255 * (t < 450 ? (t - 446) / 4.0 : 1 - (t - 450) / 26.0));
            g.fill(0, 0, w, h, (Math.max(0, Math.min(255, a)) << 24) | 0xFFFFFF);
        }
        if (t > 400 && t < 448) {
            int a = (int) (60 * Math.abs(Math.sin(t * 0.4)) * (t - 400) / 48.0);
            g.fill(0, bar, w, h - bar, (a << 24) | 0xFF0000);
        }
        if (t > 528 && t < 546) {
            double u = t < 537 ? (t - 528) / 9.0 : 1 - (t - 537) / 9.0;
            g.fill(0, 0, w, h, ((int) (255 * Math.max(0, Math.min(1, u))) << 24));
        }
        // 충전 게이지
        if (t > 102 && t < 200) {
            double k = Math.min(1, (t - 102) / 96.0);
            int bw = Math.min(260, w / 2), bx = (w - bw) / 2, by = h - bar - 34;
            g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xA0000000);
            int fw = (int) (bw * k);
            g.fill(bx, by, bx + fw, by + 6, k > 0.9 ? (((long) t / 2) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF3030) : 0xFFFF3030);
            for (int i = 1; i < 20; i++) g.fill(bx + bw * i / 20, by, bx + bw * i / 20 + 1, by + 6, 0x60000000);
            String lb = String.format("ORBITAL CHARGE  %d%%", (int) (k * 100));
            g.drawCenteredString(font, lb, w / 2, by - 12, 0xFFFFD84A);
        }
        // 카운트다운
        String[] cd = {"3", "2", "1"};
        for (int i = 0; i < 3; i++) {
            double t0 = 170 + i * 10;
            if (t >= t0 && t < t0 + 10) big(g, font, w, h, cd[i], 7f - (float) (t - t0) * 0.25f, 1 - (t - t0) / 10.0, 0xFFFF3030);
        }
        if (t >= 199 && t < 212) big(g, font, w, h, "DELETE", 4.5f, 1 - (t - 199) / 13.0, 0xFFFFFFFF);
        sub(g, font, w, h, bar, t, 4, 38, "§f개발자의 버튼", 2.0f);
        sub(g, font, w, h, bar, t, 26, 40, "§7...딸깍.", 1.2f);
        sub(g, font, w, h, bar, t, 52, 98, "§c§l경고 §f— 목표 지점 고정", 1.6f);
        sub(g, font, w, h, bar, t, 110, 168, "§f궤도 병기 「개발자」 §c충전 중", 1.6f);
        sub(g, font, w, h, bar, t, 214, 255, "§c§l삭제 개시", 2.2f);
        sub(g, font, w, h, bar, t, 266, 318, "§f§l— 삭  제 —", 3.0f);
        sub(g, font, w, h, bar, t, 330, 392, "§7지름 100블럭, 소멸 완료.", 1.4f);
        sub(g, font, w, h, bar, t, 60, 98, "§b전 지구 좌표망 §f— 목표 추적", 1.4f);
        sub(g, font, w, h, bar, t, 404, 440, "§c...아직 무언가가 남아 있다", 1.6f);
        sub(g, font, w, h, bar, t, 452, 500, "§f§lPROCESS TERMINATED", 2.8f);
        sub(g, font, w, h, bar, t, 506, 528, "§7개발자의 버튼 — 실행 완료", 1.4f);
        if (t >= 440 && t < 448) big(g, font, w, h, "kill -9", 5f - (float) (t - 440) * 0.2f, 1 - (t - 440) / 8.0, 0xFFFF3030);
    }

    /** 화면 가운데 큰 글자 (카운트다운). */
    static void big(GuiGraphics g, Font font, int w, int h, String s, float scale, double alpha, int rgb) {
        int a = Math.max(8, Math.min(255, (int) (255 * alpha)));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h / 2f);
        pose.scale(scale, scale);
        g.drawCenteredString(font, s, 1, -3, (a / 2 << 24) | 0x400000);
        g.drawCenteredString(font, s, 0, -4, (a << 24) | (rgb & 0xFFFFFF));
        pose.popMatrix();
    }

    /** 아래 레터박스 위에 페이드 인/아웃 자막. */
    static void sub(GuiGraphics g, Font font, int w, int h, int bar, double t, double from, double to, String s, float scale) {
        if (t < from || t > to) return;
        double a = Math.min(1, Math.min((t - from) / 6.0, (to - t) / 6.0));
        int alpha = Math.max(8, (int) (255 * a));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h - bar - 14 - 10 * (scale - 1));
        pose.scale(scale, scale);
        g.drawCenteredString(font, s, 0, -4, (alpha << 24) | 0xFFFFFF);
        pose.popMatrix();
    }
}
