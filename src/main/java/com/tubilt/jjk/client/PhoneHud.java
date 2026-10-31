package com.tubilt.jjk.client;

import com.tubilt.jjk.mech.DevPhone;
import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 개발자의 (긴 이름) 핸드폰 — 들고 있으면 오른쪽 아래에 폰 화면: 상태 표시줄 · 흐르는 이름 · 앱 12개 · 선택 앱 설명. */
public final class PhoneHud {
    private PhoneHud() {}

    static int app = 0, sent = -1;
    static long pulseAt = 0;

    static boolean holding() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() == null && p.getMainHandItem().is(com.tubilt.jjk.JjkItems.DEV_PHONE.get());
    }

    static void step(int dir) {
        int n = DevPhone.APPS.length;
        app = ((app + dir) % n + n) % n;
        pulseAt = System.currentTimeMillis();
        sync();
    }

    static void sync() {
        if (sent != app && JjkClient.canSend()) { JjkNet.toServer(new Payloads.Fire(100 + app)); sent = app; }
    }

    public static void render(GuiGraphics g, Font font, int w, int h) {
        if (!holding()) { sent = -1; return; }
        sync();
        long now = System.currentTimeMillis();
        int pw = 96, ph = 176, x = w - pw - 10, y = h - ph - 30;
        // 몸체 + 베젤
        g.fill(x + 3, y, x + pw - 3, y + ph, 0xF0101014);
        g.fill(x, y + 3, x + pw, y + ph - 3, 0xF0101014);
        g.fill(x + 1, y + 1, x + pw - 1, y + 3, 0xFF2A2A34);
        int sx = x + 4, sy = y + 10, sw = pw - 8, sh = ph - 22;
        // 화면: 위→아래 그라데이션
        for (int i = 0; i < sh; i += 2) {
            double u = i / (double) sh;
            int r = (int) (40 + 60 * u), gg = (int) (20 + 10 * u), b = (int) (90 - 40 * u);
            g.fill(sx, sy + i, sx + sw, Math.min(sy + sh, sy + i + 2), 0xFF000000 | r << 16 | gg << 8 | b);
        }
        g.fill(x + pw / 2 - 10, y + 4, x + pw / 2 + 10, y + 6, 0xFF050506);   // 카메라 노치
        // 상태 표시줄
        var pose = g.pose();
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        pose.pushMatrix(); pose.translate(sx + 2, sy + 2); pose.scale(0.6f, 0.6f);
        g.drawString(font, "§f" + time, 0, 0, 0xFFFFFFFF, false);
        g.drawString(font, "§f∞G ▂▄▆█ §a▮∞%", (int) (sw / 0.6f) - font.width("∞G ▂▄▆█ ▮∞%") - 4, 0, 0xFFFFFFFF, false);
        pose.popMatrix();
        // 흐르는 이름 (마퀴)
        String name = DevPhone.NAME + "      ";
        int nw = font.width(name);
        int off = (int) ((now / 25) % nw);
        g.enableScissor(sx, sy + 9, sx + sw, sy + 19);
        g.drawString(font, "§d§l" + name + name, sx + 2 - off, sy + 10, 0xFFFFFFFF, false);
        g.disableScissor();
        // 앱 격자 3×4
        int cell = 26, gx = sx + (sw - cell * 3) / 2, gy = sy + 24;
        boolean blink = (now / 300) % 2 == 0;
        double pop = Math.max(0, 1 - (now - pulseAt) / 200.0);
        for (int i = 0; i < DevPhone.APPS.length; i++) {
            int cx = gx + (i % 3) * cell, cy = gy + (i / 3) * cell;
            int col = DevPhone.COLOR[i];
            boolean sel = i == app;
            int grow = sel ? (int) (2 + 2 * pop) : 0;
            int x0 = cx + 3 - grow, y0 = cy + 1 - grow, x1 = cx + cell - 3 + grow, y1 = cy + cell - 5 + grow;
            if (sel) g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, blink ? 0xFFFFFFFF : 0xFFAAAAAA);
            g.fill(x0, y0, x1, y1, 0xFF000000 | col);
            g.fill(x0, y0, x1, y0 + (y1 - y0) / 2, 0x30FFFFFF);   // 유리 광택
            g.drawCenteredString(font, DevPhone.ICON[i], (x0 + x1) / 2, (y0 + y1) / 2 - 4, (col & 0xFFFFFF) > 0xC0C0C0 ? 0xFF202020 : 0xFFFFFFFF);
        }
        // 선택 앱 이름·설명
        int ty = gy + cell * 4 + 2;
        g.drawCenteredString(font, "§l" + DevPhone.ICON[app] + " " + DevPhone.APPS[app], sx + sw / 2, ty, 0xFF000000 | DevPhone.COLOR[app]);
        pose.pushMatrix(); pose.translate(sx + sw / 2f, ty + 11); pose.scale(0.55f, 0.55f);
        var lines = font.split(net.minecraft.network.chat.Component.literal(DevPhone.DESC[app]), (int) ((sw - 4) / 0.55f));
        for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawCenteredString(font, lines.get(i), 0, i * 10, 0xFFDDDDDD);
        pose.popMatrix();
        // 홈 바 + 조작 안내
        g.fill(x + pw / 2 - 14, y + ph - 7, x + pw / 2 + 14, y + ph - 5, 0xFFCCCCCC);
        pose.pushMatrix(); pose.translate(x + pw / 2f, y + ph + 4); pose.scale(0.6f, 0.6f);
        g.drawCenteredString(font, "§7Z/C 앱 넘기기 · 우클릭 실행", 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
