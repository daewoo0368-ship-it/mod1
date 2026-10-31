package com.tubilt.jjk.client;

import com.tubilt.jjk.Techniques;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** 주력 게이지, 선택 술식, 상태 아이콘, 영역/컷신 화면 연출. */
public final class JjkHud {
    private JjkHud() {}

    private static long pulseUntil = 0;
    private static final Random R = new Random();
    private static float shownCe = 1000;

    public static void pulse() { pulseUntil = System.currentTimeMillis() + 900; }

    static String gunState() {
        var pl = Minecraft.getInstance().player;
        if (pl == null || !(pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMech m)) return "";
        if (m.gunOverheated()) return "§c§l과열!";
        int h = m.gunHeatPct();
        return h > 75 ? "§6열 " + h + "%" : "§a준비" + (h > 0 ? " §7(열 " + h + "%)" : "");
    }

    /** 바탕 HUD → 그 위에 명령 콘솔·스킬 컷인 (패널에 가려지지 않게 항상 맨 위). */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        renderBase(g, delta);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || Cinematic.active() || PlanetCine.active()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        PhoneHud.render(g, mc.font, w, h);
        CmdConsole.render(g, mc.font, w, h);
        SkillCutIn.render(g, mc.font, w, h);
        MetaFx.render(g, mc.font, w, h);
    }

    private static void renderBase(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        long now = System.currentTimeMillis();

        // 개발자의 버튼 컷신: 다른 HUD 대신 레터박스·자막만
        if (Cinematic.active()) { Cinematic.render(g, font, w, h); ScreenFx.render(g, w, h); return; }
        if (PlanetCine.active()) { ScreenFx.render(g, w, h); PlanetCine.render(g, font, w, h); return; }
        ScreenFx.render(g, w, h);
        QuickCam.render(g, w, h);

        drawDomain(g, w, h, now);

        // 번쩍임
        if (JjkClient.flashTicks > 0) {
            int a = (JjkClient.flashColor >>> 24) & 0xFF;
            a = a * JjkClient.flashTicks / JjkClient.flashTotal;
            g.fill(0, 0, w, h, (a << 24) | (JjkClient.flashColor & 0xFFFFFF));
        }

        // 컷신: 레터박스만 그리고 HUD 숨김
        if ((JjkClient.flags & 64) != 0) {
            int bar = h / 9;
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            return;
        }
        // ── 탈것 HUD: 서버 신호와 상관없이 "지금 탈것에 타고 있으면" 주술회전 HUD 대신 표시
        if (mc.player.getVehicle() instanceof com.tubilt.jjk.mech.DevVehicle) {
            MechHud.render(g, font, w, h, now);
            return;   // 탑승 중엔 주술회전 HUD(주력·술식·키 안내) 숨김
        }
        if (!JjkClient.hasServerMod) return;

        // ── 커맨드 수호를 들고 있으면 주술 HUD(주력·상태·술식) 대신 명령 목록만
        if ((JjkClient.flags & 262144) != 0 && JjkClient.cooldowns.size() >= 8) {
            int gx = 8, gy = h - 20;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(gx, gy); pose.scale(MechHud.SIDE_SCALE, MechHud.SIDE_SCALE); pose.translate(-gx, -gy);
            guardHud(g, font, gx, gy - 14, now);
            pose.popMatrix();
            return;
        }

        // 직업을 고르기 전에는 술식 창(주력·술식)을 띄우지 않는다
        if (JjkClient.unlockInfo && JjkClient.job == null) return;

        // ── 주력 게이지 (좌하단)
        int x = 8, y = h - 34, bw = 110, bh = 7;
        boolean hr = (JjkClient.flags & 4) != 0, jp = (JjkClient.flags & 8) != 0, six = (JjkClient.flags & 1) != 0;
        shownCe += (JjkClient.ce - shownCe) * 0.35f;
        float ratio = hr ? 0 : jp ? 1 : Math.max(0, Math.min(1, shownCe / Math.max(1, JjkClient.ceMax)));
        int col = hr ? 0xFF2E7D32 : jp ? 0xFFFFC83A : JjkClient.ce < 200 ? 0xFFE04040 : six ? 0xFF8FE3FF : 0xFF3A9BFF;
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xC0000000);
        g.fill(x, y, x + (int) (bw * ratio), y + bh, col);
        for (int i = 1; i < 10; i++) g.fill(x + bw * i / 10, y, x + bw * i / 10 + 1, y + bh, 0x40000000);
        if (jp) { int sx = x + (int) ((now / 6) % bw); g.fill(sx, y, Math.min(x + bw, sx + 8), y + bh, 0x90FFFFFF); }
        String label = hr ? "주력 없음 — 천여주박" : jp ? "주력 ∞ — 대박" : "주력 " + JjkClient.ce + " / " + JjkClient.ceMax;
        g.drawString(font, label, x, y - 10, 0xFFFFFFFF, true);

        // 상태
        StringBuilder st = new StringBuilder();
        if (six) st.append("§b육안 ");
        if ((JjkClient.flags & 2) != 0) st.append("§f무하한 ");
        if ((JjkClient.flags & 16) != 0) st.append("§4봉인 ");
        if ((JjkClient.flags & 32) != 0) st.append("§6몰수 ");
        if (st.length() > 0) g.drawString(font, st.toString().trim(), x + bw + 6, y, 0xFFFFFFFF, true);

        // ── 커맨드 수호를 들고 있으면 술식 대신 명령 목록
        if ((JjkClient.flags & 262144) != 0 && JjkClient.cooldowns.size() >= 8) {
            var pose = g.pose();          // 커맨드 수호 명령 목록: 좌하단 기준 축소
            pose.pushMatrix();
            pose.translate(x, y); pose.scale(MechHud.SIDE_SCALE, MechHud.SIDE_SCALE); pose.translate(-x, -y);
            guardHud(g, font, x, y - 14, now);
            pose.popMatrix();
            return;
        }

        // ── 선택 술식 (좌하단 위쪽)
        Techniques.Tech t = JjkClient.selectedTech();
        if (t == null) return;   // 아이템을 처음 쓰기 전까지는 어떤 능력도 띄우지 않는다
        float cd = JjkClient.cooldownOf(t);
        int cost = six ? t.cost() / 4 : t.cost();
        boolean pulse = now < pulseUntil;
        int ty = y - 34;
        int box = 0xFF000000 | t.color();
        g.fill(x - 1, ty - 1, x + 3, ty + 19, box);
        g.drawString(font, t.name(), x + 7, ty, pulse ? 0xFFFFFF80 : 0xFFFFFFFF, true);
        String sub = t.user() + "  ·  " + (cost > 0 ? "주력 " + cost : "주력 0");
        if (cd > 0) sub += "  ·  §c" + String.format("%.1f초", cd / 20f);
        else sub += "  ·  §a준비";
        g.drawString(font, sub, x + 7, ty + 10, 0xFFB0B0B0, true);
        String keys = "[" + JjkClient.KEY_CAST.getTranslatedKeyMessage().getString() + "] 시전  ["
                + JjkClient.KEY_PREV.getTranslatedKeyMessage().getString() + "/" + JjkClient.KEY_NEXT.getTranslatedKeyMessage().getString()
                + "] 변경  [" + JjkClient.KEY_MENU.getTranslatedKeyMessage().getString() + "] 목록";
        g.drawString(font, keys, x, ty - 11, 0xA0FFFFFF, true);
    }

    static final String[] GUARD_MODES = {"폭탄", "화살 비", "레이저", "/fill 감옥", "/summon 번개 폭풍", "/tp 공중 낙하", "/effect 시간 감속",
            "/summon 커맨드 메테오", "/execute 특이점", "/worldborder 결계", "/clone 분신", "/tick freeze", "/summon 검의 비",
            "/execute 궤도 폭격", "/summon 커맨드 드래곤", "/fill 블록 해일", "/execute 시공 절단", "/summon 커맨드 거인",
            "/execute 천공의 창", "/function 연쇄 번개", "/data 칼날 폭풍",
            "/particle 성운 붕괴", "/execute 시간 역행", "/summon 커맨드 피닉스", "/kill @e 심판의 눈", "/tp 행성 낙하",
            "/gamerule 중력 붕괴", "/attribute 축소", "/data 체력 1", "/tp 천공 추방", "/item 무장 해제",
            "/summon 커맨드 블록 비", "/setblock 큐브 압축", "/scoreboard 처형", "/clone 지형 압착", "/summon 포탑 진지", "/kill 삭제 코드",
            "/team 세뇌", "/give TNT 부착", "/random 운명의 주사위", "/execute 블랙홀→화이트홀", "/summon 거대 손",
            "∞ /kill @e 종말 명령", "∞ 무한 큐브", "∞ 명령의 신 강림", "∞ /reload", "∞ /execute ∞"};
    /** 봉인 해제 전에는 42개까지만. */
    static final String[] INK_MODES = {"/inker 염력 들기", "/inker 염력 투척", "/inker 사슬 속박", "/inker 사슬 선풍"};
    static int guardBase() { var d = JjkClient.cooldowns; return d.size() > 15 && (d.get(15) & 1) != 0 ? GUARD_MODES.length : 42; }
    static int guardCount() { var d = JjkClient.cooldowns; return guardBase() + (d.size() > 15 && (d.get(15) & 2) != 0 ? INK_MODES.length : 0); }
    static String gname(int k) { int b = guardBase(); return k < b ? GUARD_MODES[k] : INK_MODES[Math.min(INK_MODES.length - 1, k - b)]; }
    private static int gLast = -1; private static long gAt = 0;
    /** Z/C 를 누르면 서버 응답을 기다리지 않고 바로 넘김 (예측) + 휠이 부드럽게 굴러감. */
    static int predMode = -1; static long predAt = 0; static double wheel = 0; static long wheelAt = 0;
    public static void predictGuard(int dir) {
        int n = guardCount();
        int base = predMode >= 0 ? predMode : (JjkClient.cooldowns.isEmpty() ? 0 : JjkClient.cooldowns.get(0));
        predMode = ((base + dir) % n + n) % n; predAt = System.currentTimeMillis();
    }

    /** 커맨드 수호 HUD: 위아래로 2칸씩 보이는 명령 휠 + 대기·궁극·골렘. */
    private static void guardHud(GuiGraphics g, Font font, int x, int bottom, long now) {
        var d = JjkClient.cooldowns;
        int srvMode = d.get(0), shield = d.get(4), golems = d.get(5), active = d.get(7);
        if (predMode >= 0 && (srvMode == predMode || now - predAt > 700)) predMode = -1;
        int mode = predMode >= 0 ? predMode : srvMode;
        float atk = JjkClient.cd(1), ult = JjkClient.cd(2), fly = JjkClient.cd(3);
        if (mode != gLast) { gLast = mode; gAt = now; }
        int combo = d.size() > 8 ? d.get(8) : 0, odp = d.size() > 9 ? d.get(9) : 0;
        float odT = d.size() > 10 ? JjkClient.cd(10) : 0;
        int n = guardCount(), R = 11;
        int y = bottom - R * 11;
        int pw = 184;
        MechHud.panel(g, x - 8, y - 7, x + pw, bottom + 38, odT > 0 ? 0xFFD84A : 0xB45CFF);
        String cmdN = "§8" + String.format("%02d/%02d", mode + 1, n);
        g.drawString(font, cmdN, x + pw - 6 - font.width(cmdN), y, 0xFFFFFFFF, false);
        g.drawString(font, "§d§l커맨드 수호" + (active == 1 ? "" : " §7(우클릭 소환)"), x, y, 0xFFFFFFFF, true);
        if (combo >= 2) {
            long ph = now / 80 % 2;
            String cs = (combo >= 10 ? (ph == 0 ? "§c§l" : "§6§l") : combo >= 5 ? "§6§l" : "§f§l") + "COMBO ×" + combo;
            g.drawString(font, cs, x + font.width("커맨드 수호") + 30, y, 0xFFFFFFFF, true);
        }
        y += R + 1;
        // 오버드라이브 게이지 (세그먼트 + 반짝임)
        int bw = 100, bx = x, by = y + 1;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xC0000000);
        if (odT > 0) {
            float k = Math.min(1, odT / 240f);
            int w2 = (int) (bw * k);
            g.fill(bx, by, bx + w2, by + 5, (now / 100) % 2 == 0 ? 0xFFFFD84A : 0xFFFF9020);
            int sx = bx + (int) ((now / 4) % Math.max(1, w2 + 20)) - 10;
            g.fill(Math.max(bx, sx), by, Math.min(bx + w2, sx + 8), by + 5, 0xC0FFFFFF);
            g.drawString(font, String.format("§6§l⚡OD §f%.1fs", odT / 20f), bx + bw + 6, by - 1, 0xFFFFFFFF, true);
        } else {
            for (int i = 0; i < 10; i++) {
                int x0 = bx + i * 10, fillW = Math.max(0, Math.min(9, (odp - i * 10) * 9 / 10));
                g.fill(x0, by, x0 + 9, by + 5, 0x40FFD84A);
                if (fillW > 0) g.fill(x0, by, x0 + fillW, by + 5, odp >= 100 ? 0xFFFFFFFF : 0xFFE0B030);
            }
            g.drawString(font, "§6OD §7" + odp + "%", bx + bw + 6, by - 1, 0xFFFFFFFF, true);
        }
        y += R + 2;
        int domCd = d.size() > 11 ? d.get(11) : 0, domOn = d.size() > 12 ? d.get(12) : 0;
        if (domOn == 1) { g.drawString(font, ((now / 120) % 2 == 0 ? "§b§l" : "§3§l") + "◆ 영역 전개 중 ─ 무한 명령", x, y, 0xFFFFFFFF, true); y += R; }
        else if (odT > 0 && domCd == 0) { g.drawString(font, ((now / 150) % 2 == 0 ? "§b§l" : "§f§l") + "◆ 각성 가능 §7[웅크리고 정면 우클릭]", x, y, 0xFFFFFFFF, true); y += R; }
        float pop = Math.min(1, (now - gAt) / 120f);
        // 휠: 보이는 위치(wheel)를 실제 선택(mode)으로 부드럽게 굴림 (한 바퀴 넘어가는 경우도 가까운 쪽으로)
        double diff = mode - wheel; diff -= Math.round(diff / n) * n;
        double dt = Math.min(100, now - wheelAt); wheelAt = now;
        wheel += diff * Math.min(1, dt / 70.0);
        if (Math.abs(diff) < 0.01) wheel = mode;
        double frac = wheel - Math.floor(wheel);
        int baseIdx = (int) Math.floor(wheel);
        int y0 = y;
        for (int j = -2; j <= 3; j++) {
            double rel = j - frac;                       // 가운데로부터 떨어진 칸 수 (실수)
            if (Math.abs(rel) > 2.4) continue;
            int k = ((baseIdx + j) % n + n) % n;
            int yy = y0 + (int) Math.round((rel + 2) * R);
            int a = (int) (255 * Math.max(0.25, 1 - Math.abs(rel) * 0.36));
            if (k == mode && Math.abs(rel) < 0.5) {
                int wbox = font.width(gname(k)) + 26;
                g.fill(x - 2, yy - 2, x - 2 + (int) (wbox * pop), yy + 10, 0x60B45CFF);
                g.fill(x - 2, yy - 2, x, yy + 10, 0xFFB45CFF);
                g.drawString(font, "§d▶ §f§l" + gname(k), x + 3, yy, 0xFFFFFFFF, true);
                if (atk > 0) g.drawString(font, String.format("§7%.1f초", atk / 20f), x + wbox + 2, yy, 0xFFFFFFFF, true);
            } else g.drawString(font, "   " + gname(k), x + 3, yy, (a << 24) | 0xC0A0E0, true);
        }
        y = y0 + R * 5;
        y += 3;
        String keys = "[" + JjkClient.KEY_PREV.getTranslatedKeyMessage().getString() + "/" + JjkClient.KEY_NEXT.getTranslatedKeyMessage().getString() + "] 선택 [우클릭] 실행";
        g.drawString(font, keys, x, y, 0xA0FFFFFF, true);
        y += R;
        String st = (ult <= 0 ? "§c/kill §a준비" : String.format("§8/kill %.0f초", ult / 20f)) + "  "
                + (fly <= 0 ? "§b/gamemode §a준비" : String.format("§8/gamemode %.0f초", fly / 20f));
        g.drawString(font, st, x, y, 0xFFFFFFFF, true);
        y += R;
        g.drawString(font, (shield == 0 ? "§a방어막 준비" : "§8방어막") + "  §7골렘 §f" + golems + "/3", x, y, 0xFFFFFFFF, true);
        if (d.size() > 14) {
            y += R;
            int ab = d.get(13), rv = d.get(14);
            String as = ab < 0 ? ((now / 150) % 2 == 0 ? "§6§l⬢ 절대 방어 " : "§e§l⬢ 절대 방어 ") + String.format("%.1fs", -ab / 20f)
                    : ab == 0 ? "§6⬢ 절대 방어 §a준비" : String.format("§8⬢ 절대 방어 %.0f초", ab / 20f);
            String rs = rv == 0 ? "§a✚ 부활 준비" : String.format("§8✚ 부활 %.0f초", rv / 20f);
            g.drawString(font, as + "  " + rs, x, y, 0xFFFFFFFF, true);
        }
    }

    /** 영역 안에 있을 때 테두리 색과 효과. */
    private static void drawDomain(GuiGraphics g, int w, int h, long now) {
        int d = JjkClient.domain;
        if (d == 0) return;
        int c = switch (d) {
            case 1 -> 0xE8F4FF; case 2 -> 0xB01010; case 3 -> 0x2E8A90; case 4 -> 0xFF6A00;
            case 5 -> 0xE0B030; case 6 -> 0xF28CC0; case 8 -> 0x40E8FF; default -> 0x7CFF6A;
        };
        int layers = 14;
        for (int i = 0; i < layers; i++) {
            int a = (int) (110 * (1 - i / (float) layers) * (1 - i / (float) layers));
            int argb = (a << 24) | c;
            int t = 2 + i * 3;
            g.fill(0, 0, w, t, argb);
            g.fill(0, h - t, w, h, argb);
            g.fill(0, t, t, h - t, argb);
            g.fill(w - t, t, w, h - t, argb);
        }
        if (d == 1) { // 무량공처: 별빛
            for (int i = 0; i < 40; i++) {
                long seed = (now / 120) * 31 + i * 977L;
                R.setSeed(seed);
                int sx = R.nextInt(w), sy = R.nextInt(h), a = 80 + R.nextInt(160);
                g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFFFFFF);
            }
        } else if (d == 2) { // 복마어주자: 스치는 참격
            R.setSeed(now / 60);
            for (int i = 0; i < 4; i++) {
                int sy = R.nextInt(h), sx = R.nextInt(w), len = 30 + R.nextInt(80);
                g.fill(sx, sy, Math.min(w, sx + len), sy + 1, 0xC0FFFFFF);
            }
        } else if (d == 8) { // 개발자 영역: 주사선 + 글리치 띠 + 디버그 표시
            for (int yy = 0; yy < h; yy += 3) g.fill(0, yy, w, yy + 1, 0x1440E8FF);
            int sweep = (int) ((now / 6) % (h + 40)) - 20;
            g.fill(0, sweep, w, sweep + 2, 0x3040E8FF);
            R.setSeed(now / 90);
            for (int i = 0; i < 5; i++) {
                if (R.nextInt(3) != 0) continue;
                int gy = R.nextInt(h), gh = 1 + R.nextInt(5), gx = R.nextInt(w), gw = 20 + R.nextInt(160);
                g.fill(gx, gy, Math.min(w, gx + gw), gy + gh, R.nextBoolean() ? 0x5040FF70 : 0x50FF40C0);
            }
            var font = Minecraft.getInstance().font;
            boolean rec = (now / 500) % 2 == 0;
            g.drawString(font, (rec ? "§c● " : "§4● ") + "§bDEBUG MODE §7▪ 개발자 영역", 10, 8, 0xFFFFFFFF, true);
            String fr = String.format("§7FRAME §f%06d §7▪ ENTITY SCAN §aON", (now / 50) % 1000000);
            g.drawString(font, fr, w - font.width(fr) - 10, 8, 0xFFFFFFFF, true);
            // 모서리 브래킷
            int L = 24, c2 = 0xC040E8FF;
            g.fill(6, 22, 6 + L, 24, c2); g.fill(6, 22, 8, 22 + L, c2);
            g.fill(w - 6 - L, 22, w - 6, 24, c2); g.fill(w - 8, 22, w - 6, 22 + L, c2);
            g.fill(6, h - 24, 6 + L, h - 22, c2); g.fill(6, h - 22 - L, 8, h - 22, c2);
            g.fill(w - 6 - L, h - 24, w - 6, h - 22, c2); g.fill(w - 8, h - 22 - L, w - 6, h - 22, c2);
        } else if (d == 7) { // 좌살박도: 네온 깜빡임
            int a = (int) (40 + 30 * Math.sin(now / 90.0));
            g.fill(0, 0, w, h, (a << 24) | (now / 400 % 2 == 0 ? 0xFF40D0 : 0x40FFD0));
        }
    }
}
