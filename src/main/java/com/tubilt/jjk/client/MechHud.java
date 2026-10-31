package com.tubilt.jjk.client;

import com.tubilt.jjk.mech.DevMech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 탈것 HUD — 화면 좌하단에 무기 4종을 정리해서 표시 (배경 창 없음).
 *  내구도 / 미니건(열) / 미사일(장전) / 레이저(냉각) / 대지 분쇄(냉각)
 *  각 줄: 이름 · [키] · 상태 막대. 준비되면 밝게, 대기 중엔 흐리게.
 *  조작 안내는 탑승 직후 5초만 화면 아래에 잠깐 표시.
 */
final class MechHud {
    private MechHud() {}

    // 쿨타임 진행 추정 (준비 → 대기 로 바뀐 시각 기록)
    private static long missileAt = 0, laserAt = 0, slamAt = 0, mountedAt = 0;
    private static boolean missileWas = true, laserWas = true, slamWas = true, riding = false;
    private static final long MISSILE_MS = 3000, LASER_MS = 15000, SLAM_MS = 8000;

    static final int BAR = 56, ROW = 13;

    static void render(GuiGraphics g, Font font, int w, int h, long now) {
        var pl = Minecraft.getInstance().player;
        if (pl != null && pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMachine dm) { renderMachine(g, font, w, h, now, dm); return; }
        if (pl != null && pl.getVehicle() instanceof com.tubilt.jjk.mech.DevTitan titan) { renderTitan(g, font, w, h, now, titan); return; }
        if (pl != null && pl.getVehicle() instanceof com.tubilt.jjk.mech.DevWalker walker) { renderWalker(g, font, w, h, now, walker); return; }
        if (pl == null || !(pl.getVehicle() instanceof DevMech mech)) { riding = false; return; }
        if (!riding) { riding = true; mountedAt = now; }

        int f = JjkClient.flags;
        boolean mReady = (f & 256) != 0, lReady = (f & 512) != 0, lOn = (f & 1024) != 0, sReady = (f & 2048) != 0;
        if (missileWas && !mReady) missileAt = now;
        if (laserWas && !lReady) laserAt = now;
        if (slamWas && !sReady) slamAt = now;
        missileWas = mReady; laserWas = lReady; slamWas = sReady;

        // 왼쪽 정렬: 이름 칸 · 키 칸 · 막대 (가장 긴 이름/키에 맞춰 열 정렬)
        int left = 10;
        nameW = font.width("대지 분쇄");
        keyW = 0;
        for (var k : new net.minecraft.client.KeyMapping[]{JjkClient.KEY_NEXT, JjkClient.KEY_PREV, JjkClient.KEY_LASER, JjkClient.KEY_SLAM})
            keyW = Math.max(keyW, font.width("[" + k.getTranslatedKeyMessage().getString() + "]"));
        int barX = left + nameW + 5 + keyW + 6;
        int y = h - 10 - ROW * 6;

        // 제목
        String title = "§b§l개발자의 귀여운 탑승물";
        g.drawString(font, title, left, y, 0xFFFFFFFF, true);
        y += ROW + 2;

        // 내구도
        float hp = Math.max(0, Math.min(1, mech.getHealth() / mech.getMaxHealth()));
        row(g, font, barX, y, "내구도", null, true);
        bar(g, barX, y + 3, hp, hp > 0.5f ? 0xFF60E070 : hp > 0.25f ? 0xFFFFB030 : 0xFFFF4040);
        y += ROW;

        // 미니건
        int heat = mech.gunHeatPct(); boolean oh = mech.gunOverheated();
        boolean blink = (now / 150) % 2 == 0;
        row(g, font, barX, y, "미니건", JjkClient.KEY_NEXT, !oh);
        if (oh) g.drawString(font, blink ? "§c과열!" : "§4과열!", barX + BAR + 5, y, 0xFFFFFFFF, true);
        bar(g, barX, y + 3, heat / 100f, oh ? (blink ? 0xFFFF2020 : 0xFF801010) : heat > 75 ? 0xFFFF6020 : heat > 40 ? 0xFFFFB030 : 0xFF60E0FF);
        y += ROW;

        // 미사일 (8칸 탄)
        row(g, font, barX, y, "미사일", JjkClient.KEY_PREV, mReady);
        float mp = mReady ? 1 : Math.min(1, (now - missileAt) / (float) MISSILE_MS);
        int lit = mReady ? 8 : (int) (mp * 8);
        for (int i = 0; i < 8; i++) {
            int x0 = barX + i * 7;
            g.fill(x0, y + 2, x0 + 5, y + 7, i < lit ? (mReady ? 0xFFFFB030 : 0xFF9A6A20) : 0x60FFFFFF);
        }
        y += ROW;

        // 레이저
        row(g, font, barX, y, "레이저", JjkClient.KEY_LASER, lReady || lOn);
        if (lOn) g.drawString(font, "§b발사 중", barX + BAR + 5, y, 0xFFFFFFFF, true);
        if (lOn) bar(g, barX, y + 3, 1, blink ? 0xFFFFFFFF : 0xFF40E8FF);
        else bar(g, barX, y + 3, lReady ? 1 : Math.min(1, (now - laserAt) / (float) LASER_MS), lReady ? 0xFF40E8FF : 0xFF2A6A80);
        y += ROW;

        // 대지 분쇄
        row(g, font, barX, y, "대지 분쇄", JjkClient.KEY_SLAM, sReady);
        bar(g, barX, y + 3, sReady ? 1 : Math.min(1, (now - slamAt) / (float) SLAM_MS), sReady ? 0xFFFF8A20 : 0xFF804515);

        // 탑승 직후 5초: 조작 안내 (화면 아래 가운데, 서서히 사라짐)
        long since = now - mountedAt;
        if (since < 5000) {
            int a = since < 4000 ? 255 : (int) (255 * (5000 - since) / 1000f);
            String hint = "WASD 이동 · 달리기 가속 · 벽에 붙으면 기어오름 · 웅크리기 하차";
            g.drawCenteredString(font, hint, w / 2, h - 62, (Math.max(8, a) << 24) | 0xC8C8C8);
        }

        // 레이저 발사 중: 화면 가장자리 하늘색 빛
        if (lOn) {
            int al = 0x30 + (int) (Math.abs(Math.sin(now / 60.0)) * 0x30);
            int c = (al << 24) | 0x40E8FF;
            g.fill(0, 0, w, 6, c); g.fill(0, h - 6, w, h, c); g.fill(0, 0, 6, h, c); g.fill(w - 6, 0, w, h, c);
        }
    }

    private static int nameW = 40, keyW = 16;
    private static long cannonAt = 0, heavyAt = 0;
    private static boolean cannonWas = true, heavyWas = true;

    private static long tMisAt = 0, tBombAt = 0, tSmallAt = 0, tArcAt = 0;
    private static boolean tMisWas = true, tBombWas = true, tSmallWas = true, tArcWas = true;

    /** 거인봇 HUD. */
    private static void renderTitan(GuiGraphics g, Font font, int w, int h, long now, com.tubilt.jjk.mech.DevTitan t) {
        if (!riding) { riding = true; mountedAt = now; }
        int f = JjkClient.flags;
        boolean mis = (f & 256) != 0, arcR = (f & 512) != 0, arcOn = (f & 1024) != 0, bomb = (f & 2048) != 0,
                small = (f & 16384) != 0, fly = (f & 32768) != 0, flame = (f & 65536) != 0;
        if (tMisWas && !mis) tMisAt = now;
        if (tBombWas && !bomb) tBombAt = now;
        if (tSmallWas && !small) tSmallAt = now;
        if (tArcWas && !arcR) tArcAt = now;
        tMisWas = mis; tBombWas = bomb; tSmallWas = small; tArcWas = arcR;

        int left = 10;
        nameW = font.width("소형 아크레이저");
        keyW = 0;
        for (var k : new net.minecraft.client.KeyMapping[]{JjkClient.KEY_NEXT, JjkClient.KEY_PREV, JjkClient.KEY_MENU, JjkClient.KEY_CAST, JjkClient.KEY_LASER, JjkClient.KEY_SLAM})
            keyW = Math.max(keyW, font.width("[" + k.getTranslatedKeyMessage().getString() + "]"));
        int barX = left + nameW + 5 + keyW + 6;
        int y = h - 10 - ROW * 9;
        boolean blink = (now / 150) % 2 == 0;
        g.drawString(font, "§6§l개발자의 매우 귀여운 탑승물", left, y, 0xFFFFFFFF, true);
        y += ROW + 2;

        float hp = Math.max(0, Math.min(1, t.getHealth() / t.getMaxHealth()));
        row(g, font, barX, y, "내구도", null, true);
        bar(g, barX, y + 3, hp, hp > 0.5f ? 0xFF60E070 : hp > 0.25f ? 0xFFFFB030 : 0xFFFF4040);
        y += ROW;

        row(g, font, barX, y, "화염방사기", JjkClient.KEY_NEXT, true);
        bar(g, barX, y + 3, flame ? 1 : 0, blink ? 0xFFFF8A20 : 0xFFFF4010);
        if (flame) g.drawString(font, "§6분사 중", barX + BAR + 5, y, 0xFFFFFFFF, true);
        y += ROW;

        row(g, font, barX, y, "미사일", JjkClient.KEY_PREV, mis);
        bar(g, barX, y + 3, mis ? 1 : Math.min(1, (now - tMisAt) / 5000f), mis ? 0xFFFFB030 : 0xFF8A6020);
        y += ROW;

        row(g, font, barX, y, "폭탄", JjkClient.KEY_MENU, bomb);
        bar(g, barX, y + 3, bomb ? 1 : Math.min(1, (now - tBombAt) / 8000f), bomb ? 0xFFFF5040 : 0xFF802018);
        y += ROW;

        row(g, font, barX, y, "소형 아크레이저", JjkClient.KEY_CAST, small);
        bar(g, barX, y + 3, small ? 1 : Math.min(1, (now - tSmallAt) / 8000f), small ? 0xFF60E0FF : 0xFF2A6A80);
        y += ROW;

        row(g, font, barX, y, "아크레이저", JjkClient.KEY_LASER, arcR || arcOn);
        if (arcOn) { bar(g, barX, y + 3, 1, blink ? 0xFFFFFFFF : 0xFFB45CFF); g.drawString(font, "§d§l발사", barX + BAR + 5, y, 0xFFFFFFFF, true); }
        else bar(g, barX, y + 3, arcR ? 1 : Math.min(1, (now - tArcAt) / 30000f), arcR ? 0xFFB45CFF : 0xFF4A2A70);
        y += ROW;

        row(g, font, barX, y, "비행", JjkClient.KEY_SLAM, true);
        g.drawString(font, fly ? "§b§lON" : "§7OFF", barX, y, 0xFFFFFFFF, true);

        long since = now - mountedAt;
        if (since < 6000) {
            int a = since < 5000 ? 255 : (int) (255 * (6000 - since) / 1000f);
            g.drawCenteredString(font, "WASD 이동 · 비행 중엔 보는 방향으로 W · 달리기 가속 · 웅크리기 하차(느린 낙하)", w / 2, h - 62, (Math.max(8, a) << 24) | 0xC8C8C8);
        }
        if (arcOn) {   // 화면 가장자리 보라·하늘 빛
            int al = 0x40 + (int) (Math.abs(Math.sin(now / 50.0)) * 0x40);
            int c = (al << 24) | (blink ? 0xB45CFF : 0x40E8FF);
            g.fill(0, 0, w, 10, c); g.fill(0, h - 10, w, h, c); g.fill(0, 0, 10, h, c); g.fill(w - 10, 0, w, h, c);
        }
    }

    /** 탑승물2 HUD: 내구도 / 주포 연사(6발) / 중포. */
    private static void renderWalker(GuiGraphics g, Font font, int w, int h, long now, com.tubilt.jjk.mech.DevWalker walker) {
        if (!riding) { riding = true; mountedAt = now; }
        int f = JjkClient.flags;
        boolean cReady = (f & 256) != 0, hReady = (f & 512) != 0, hOn = (f & 1024) != 0;
        if (cannonWas && !cReady) cannonAt = now;
        if (heavyWas && !hReady) heavyAt = now;
        cannonWas = cReady; heavyWas = hReady;

        int left = 10;
        nameW = font.width("내구도");
        keyW = Math.max(font.width("[" + JjkClient.KEY_PREV.getTranslatedKeyMessage().getString() + "]"),
                font.width("[" + JjkClient.KEY_LASER.getTranslatedKeyMessage().getString() + "]"));
        int barX = left + nameW + 5 + keyW + 6;
        int y = h - 10 - ROW * 4;
        g.drawString(font, "§6§l개발자의 귀여운 탑승물2", left, y, 0xFFFFFFFF, true);
        y += ROW + 2;

        float hp = Math.max(0, Math.min(1, walker.getHealth() / walker.getMaxHealth()));
        row(g, font, barX, y, "내구도", null, true);
        bar(g, barX, y + 3, hp, hp > 0.5f ? 0xFF60E070 : hp > 0.25f ? 0xFFFFB030 : 0xFFFF4040);
        y += ROW;

        row(g, font, barX, y, "주포", JjkClient.KEY_PREV, cReady);
        float cp = cReady ? 1 : Math.min(1, (now - cannonAt) / 4200f);
        int lit = cReady ? 6 : (int) (cp * 6);
        for (int i = 0; i < 6; i++) {
            int x0 = barX + i * 9;
            g.fill(x0, y + 2, x0 + 7, y + 7, i < lit ? (cReady ? 0xFFFF7A30 : 0xFF8A4418) : 0x60FFFFFF);
        }
        y += ROW;

        row(g, font, barX, y, "중포", JjkClient.KEY_LASER, hReady || hOn);
        boolean blink = (now / 150) % 2 == 0;
        if (hOn) { bar(g, barX, y + 3, 1, blink ? 0xFFFFFFFF : 0xFF40E8FF); g.drawString(font, "§b충전 중", barX + BAR + 5, y, 0xFFFFFFFF, true); }
        else bar(g, barX, y + 3, hReady ? 1 : Math.min(1, (now - heavyAt) / 12000f), hReady ? 0xFF40E8FF : 0xFF2A6A80);

        long since = now - mountedAt;
        if (since < 5000) {
            int a = since < 4000 ? 255 : (int) (255 * (5000 - since) / 1000f);
            g.drawCenteredString(font, "WASD 이동 · 달리기 가속 · 포탑은 시선을 따라감 · 웅크리기 하차", w / 2, h - 62, (Math.max(8, a) << 24) | 0xC8C8C8);
        }
    }

    /** 이름(왼쪽 정렬) · [키] · 막대 순서. */
    private static void row(GuiGraphics g, Font font, int barX, int y, String name, net.minecraft.client.KeyMapping key, boolean ready) {
        int left = 10;
        // 상태 글자(과열/발사 중)가 붙은 이름은 이름 칸을 넘을 수 있어 키 대신 막대 앞까지 사용
        g.drawString(font, name, left, y, ready ? 0xFFFFFFFF : 0xFF8A8A8A, true);
        if (key != null && font.width(name) <= nameW) {
            String k = "[" + key.getTranslatedKeyMessage().getString() + "]";
            g.drawString(font, k, left + nameW + 5, y, ready ? 0xFFFFE070 : 0xFF8A7A40, true);
        }
    }

    /** 얇은 진행 막대 (테두리만, 배경 채움 없음). */
    private static void bar(GuiGraphics g, int x, int y, float v, int col) {
        g.fill(x, y, x + BAR, y + 3, 0x40FFFFFF);
        g.fill(x, y, x + Math.round(BAR * v), y + 3, col);
    }


    // ───────────── 개발자의 기계 HUD v2: 좌측 무장 패널 · 상단 내구도/방어막 · 우측 코어 게이지 · 중앙 조준
    private static int lastSel = -1; private static long selAt = 0;
    /** 옆 패널 크기 배율. */
    static final float SIDE_SCALE = 0.8f;
    private static float hpShown = 1, hpLag = 1;

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0, y0, x1, y1, 0x9A06080C);
        g.fill(x0, y0, x1, y0 + 1, 0x60FFFFFF & (0xFF000000 | accent));
        g.fill(x0, y0, x0 + 2, y1, 0xFF000000 | accent);
        g.fill(x1 - 6, y1 - 1, x1, y1, 0xFF000000 | accent);
    }

    private static void renderMachine(GuiGraphics g, Font font, int w, int h, long now, com.tubilt.jjk.mech.DevMachine m) {
        java.util.List<Integer> d = JjkClient.cooldowns;
        var W = com.tubilt.jjk.mech.Machine.WEAPONS;
        boolean ok = (JjkClient.flags & 131072) != 0 && d.size() >= 7 + W.length;
        int sel = ok ? d.get(0) : m.weapon();
        if (sel != lastSel) { lastSel = sel; selAt = now; }
        boolean blink = (now / 150) % 2 == 0;
        int R = 11;

        // ── 상단 중앙: 내구도 (지연 감소 막대) + 방어막
        float hp = Math.max(0, Math.min(1, m.getHealth() / m.getMaxHealth()));
        hpShown += (hp - hpShown) * 0.3f; if (hp < hpLag) hpLag += (hp - hpLag) * 0.03f; else hpLag = hp;
        int bw = Math.min(320, w / 2), bx = (w - bw) / 2, by = 10;
        panel(g, bx - 8, by - 6, bx + bw + 8, by + 22, 0xFF3030);
        g.drawCenteredString(font, "§c§lDEVELOPER MACHINE §8│ " + (ok && d.get(5) == 1 ? "§b§lFLIGHT" : "§7§lWALKER"), w / 2, by - 3, 0xFFFFFFFF);
        int hy = by + 9;
        g.fill(bx, hy, bx + bw, hy + 6, 0xFF1A1A1E);
        g.fill(bx, hy, bx + (int) (bw * hpLag), hy + 6, 0xFFFFE0A0);
        int hc = hp > 0.5f ? 0xFF50E070 : hp > 0.25f ? 0xFFFFB030 : (blink ? 0xFFFF3030 : 0xFFA01010);
        g.fill(bx, hy, bx + (int) (bw * hpShown), hy + 6, hc);
        for (int i = 1; i < 20; i++) g.fill(bx + bw * i / 20, hy, bx + bw * i / 20 + 1, hy + 6, 0x80000000);
        g.drawString(font, String.format("%d / %d", (int) Math.ceil(m.getHealth()), (int) m.getMaxHealth()), bx + bw + 12, hy - 1, 0xFFDDDDDD, true);
        int shield = ok && d.size() > 7 + W.length ? d.get(7 + W.length) : 0;
        if (shield > 0) {
            g.fill(bx, hy + 7, bx + (int) (bw * shield / 160f), hy + 9, blink ? 0xFF80D8FF : 0xFF60C0FF);
            g.drawCenteredString(font, String.format("§b§l⬡ HARDLIGHT SHIELD %.1fs", shield / 20f), w / 2, hy + 12, 0xFFFFFFFF);
        }

        // ── 좌측: 무장 14종 (좌하단 기준 축소)
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(0, h); pose.scale(SIDE_SCALE, SIDE_SCALE); pose.translate(0, -h);
        int nw = 0;
        for (var wp : W) nw = Math.max(nw, font.width(wp.name()));
        int left = 10, barX = left + 22 + nw + 6, BW = 50;
        int top = h - 18 - R * (W.length + 5);
        panel(g, left - 6, top - 8, barX + BW + 34, h - 8, 0x40E8FF);
        g.drawString(font, "§b§lARMAMENT", left, top - 3, 0xFFFFFFFF, true);
        g.drawString(font, "§8" + (sel + 1) + "/" + W.length, barX + BW + 6, top - 3, 0xFFFFFFFF, true);
        int y = top + 10;
        for (int i = 0; i < W.length; i++) {
            boolean s = i == sel;
            float cd = ok ? JjkClient.cd(7 + i) : 0;
            int col = 0xFF000000 | W[i].color();
            if (s) {
                float pop = Math.min(1, (now - selAt) / 140f);
                g.fill(left - 2, y - 2, (int) (left - 2 + (barX + BW + 28 - left) * pop), y + 9, 0x60000000 | (W[i].color() & 0xFFFFFF));
                g.fill(left - 2, y - 2, left, y + 9, col);
            }
            g.fill(left + 4, y + 1, left + 10, y + 7, cd > 0 ? 0xFF404040 : col);              // 색 칩
            g.drawString(font, String.format("%02d", i + 1), left + 12, y, 0xFF707070, false);
            g.drawString(font, W[i].name(), left + 24, y, s ? 0xFFFFFFFF : cd > 0 ? 0xFF6A6A6A : 0xFFB8B8B8, true);
            if (i == com.tubilt.jjk.mech.Machine.W_GUN && ok) {
                int heat = d.get(1); boolean oh = d.get(2) == 1;
                g.fill(barX, y + 2, barX + BW, y + 6, 0x40FFFFFF);
                g.fill(barX, y + 2, barX + BW * heat / 100, y + 6, oh ? (blink ? 0xFFFF2020 : 0xFF801010) : heat > 75 ? 0xFFFF6020 : 0xFF60E0FF);
                if (oh) g.drawString(font, "§c§lHOT", barX + BW + 4, y, 0xFFFFFFFF, true);
            } else if (W[i].hold()) {
                g.drawString(font, s ? "§a● HOLD" : "§8○ HOLD", barX, y, 0xFFFFFFFF, false);
            } else {
                float v = cd > 0 ? 1 - cd / (float) W[i].cd() : 1;
                g.fill(barX, y + 2, barX + BW, y + 6, 0x40FFFFFF);
                g.fill(barX, y + 2, barX + (int) (BW * v), y + 6, cd > 0 ? 0xFF606060 : col);
                if (cd > 0) g.drawString(font, String.format("§7%.1f", cd / 20f), barX + BW + 4, y, 0xFFFFFFFF, true);
                else g.drawString(font, "§aRDY", barX + BW + 4, y, 0xFFFFFFFF, true);
            }
            y += R;
        }
        y += 4;
        String k = "[" + JjkClient.KEY_PREV.getTranslatedKeyMessage().getString() + "/" + JjkClient.KEY_NEXT.getTranslatedKeyMessage().getString() + "]선택 ["
                + JjkClient.KEY_CAST.getTranslatedKeyMessage().getString() + "]발사 [" + JjkClient.KEY_SLAM.getTranslatedKeyMessage().getString() + "]비행 ["
                + JjkClient.KEY_MENU.getTranslatedKeyMessage().getString() + "]포탑";
        String st = (ok && d.get(4) == 1 ? "§a⌖포탑 " : "§8⌖포탑 ") + (ok && d.get(6) > 0 ? "§a✈" + d.get(6) + " " : "");
        g.drawString(font, st + "§7" + k, left, y, 0xFFFFFFFF, true);
        pose.popMatrix();

        // ── 우측 하단: 코어 과부하 게이지 (세로)
        float ult = ok ? (d.get(3) < 0 ? -1 : JjkClient.cd(3)) : 0;
        pose.pushMatrix();
        pose.translate(w, h); pose.scale(SIDE_SCALE, SIDE_SCALE); pose.translate(-w, -h);
        int gx = w - 34, gy0 = h - 150, gh = 110;
        panel(g, gx - 10, gy0 - 22, w - 8, gy0 + gh + 26, 0xFF3030);
        float v = ult < 0 ? 1 : ult > 0 ? 1 - ult / (float) com.tubilt.jjk.mech.Machine.ULT_CD : 1;
        g.fill(gx, gy0, gx + 14, gy0 + gh, 0xFF1A1A1E);
        int fillH = (int) (gh * v);
        int uc = ult < 0 ? (blink ? 0xFFFFFFFF : 0xFFFF3030) : ult == 0 ? (blink ? 0xFFFF3030 : 0xFFFF8030) : 0xFF803030;
        g.fill(gx, gy0 + gh - fillH, gx + 14, gy0 + gh, uc);
        for (int i = 1; i < 10; i++) g.fill(gx, gy0 + gh * i / 10, gx + 14, gy0 + gh * i / 10 + 1, 0x90000000);
        g.drawCenteredString(font, "§c§lCORE", gx + 7, gy0 - 16, 0xFFFFFFFF);
        g.drawCenteredString(font, ult < 0 ? "§f§l!!" : ult == 0 ? "§c§lRDY" : String.format("§7%d%%", (int) (v * 100)), gx + 7, gy0 - 7, 0xFFFFFFFF);
        g.drawCenteredString(font, "[" + JjkClient.KEY_LASER.getTranslatedKeyMessage().getString() + "]", gx + 7, gy0 + gh + 6, ult == 0 ? 0xFFFF8080 : 0xFF707070);
        pose.popMatrix();
        if (ult < 0) g.drawCenteredString(font, (blink ? "§4§l" : "§c§l") + "⚠ CORE OVERLOAD ⚠", w / 2, by + 30, 0xFFFFFFFF);

        // ── 중앙 조준: 무기별 레티클
        int cx = w / 2, cy = h / 2, wc = 0xE0000000 | (W[sel].color() & 0xFFFFFF);
        double rot = now / 400.0;
        int rr = sel == com.tubilt.jjk.mech.Machine.W_GUN ? 10 + (ok ? d.get(1) / 10 : 0) : sel == com.tubilt.jjk.mech.Machine.W_BLADE ? 24 : 14;
        for (int i = 0; i < 4; i++) {
            double a = rot + i * Math.PI / 2;
            int x0 = cx + (int) (Math.cos(a) * rr), y0 = cy + (int) (Math.sin(a) * rr);
            g.fill(x0 - 2, y0 - 2, x0 + 2, y0 + 2, wc);
        }
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        g.drawString(font, W[sel].name(), cx + rr + 8, cy + rr + 2, wc, true);
    }
}
