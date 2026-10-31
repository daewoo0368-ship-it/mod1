package com.tubilt.jjk.client;

import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

/**
 * 커맨드 수호의 메타 연출 — 제4의 벽을 넘어 "게임 화면" 자체를 건드림.
 *  0 채팅 대사 (클라이언트 정보로 빈칸 채움)  1 가짜 크래시  2 가짜 일시정지  3 HUD 침입 (화면 가장자리에서 빼꼼)
 *  4 가짜 도전 과제  5 화면에 금  6 가짜 연결 끊김  7 가짜 사망 화면  8 채팅 입력창에 몰래 타이핑  9 큰 자막(퀴즈 등)
 */
public final class MetaFx {
    private MetaFx() {}

    static int kind = -1; static String text = ""; static long at = 0;
    static String dlgWho = "", dlgText = ""; static long dlgAt = 0;
    static String vhud = ""; static long vhudAt = 0;
    static int toastKind = -1; static String toastText = ""; static long toastAt = 0;
    static final Random R = new Random();
    static float cx = 0.6f, cy = 0.4f;

    static long now() { return System.currentTimeMillis(); }

    /** {name} {fov} {rd} {fps} {time} {hour} {gui} 채우기. */
    static String fill(String s) {
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        String name = mc.player == null ? "너" : mc.player.getName().getString();
        java.time.LocalTime t = java.time.LocalTime.now();
        return s.replace("{name}", name)
                .replace("{fov}", String.valueOf(o.fov().get()))
                .replace("{rd}", String.valueOf(o.renderDistance().get()))
                .replace("{fps}", String.valueOf(mc.getFps()))
                .replace("{time}", t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{hour}", String.valueOf(t.getHour()))
                .replace("{gui}", String.valueOf(o.guiScale().get()));
    }

    public static void start(Payloads.Meta m) {
        Minecraft mc = Minecraft.getInstance();
        String s = fill(m.text());
        switch (m.kind()) {
            case 0 -> mc.gui.getChat().addMessage(Component.literal("§d<커맨드 수호> §f" + s));
            case 4 -> { toastKind = 4; toastText = s; toastAt = now(); }
            case 5 -> { }   // 화면 깨짐 효과 제거 (무시)
            case 12 -> { vhud = s; vhudAt = now(); }   // 굴착기 HUD 데이터
            case 11 -> { int b = s.indexOf('|'); dlgWho = b < 0 ? "???" : s.substring(0, b); dlgText = b < 0 ? s : s.substring(b + 1); dlgAt = now(); }   // 보스 대사창
            case 10 -> { kind = 10; text = s; at = now(); }   // 핸드폰 과부하: 가짜 크래시 화면 (게임은 안 꺼짐)
            default -> { kind = m.kind(); text = s; at = now(); cx = 0.3f + R.nextFloat() * 0.4f; cy = 0.3f + R.nextFloat() * 0.4f; }
        }
    }

    static int dur(int k) { return switch (k) { case 1 -> 4200; case 10 -> 6500; case 2 -> 3200; case 3 -> 5000; case 5 -> 4000; case 6 -> 3600; case 7 -> 3800; case 8 -> 5200; case 9 -> 4000; default -> 0; }; }

    public static void render(GuiGraphics g, Font font, int w, int h) {
        long n = now();
        if (toastKind >= 0) toast(g, font, w, (n - toastAt) / 5000.0);
        if (dlgAt > 0) dialogue(g, font, w, h, (n - dlgAt) / 3200.0);
        if (vhudAt > 0 && n - vhudAt < 400) wormHud(g, font, w, h);
        if (kind < 0) return;
        double u = (n - at) / (double) dur(kind);
        if (u > 1) { kind = -1; return; }
        switch (kind) {
            case 1 -> crash(g, font, w, h, u);
            case 10 -> phoneCrash(g, font, w, h, u);
            case 2 -> pause(g, font, w, h, u);
            case 3 -> peek(g, font, w, h, u);
            case 5 -> cracks(g, font, w, h, u);
            case 6 -> disconnect(g, font, w, h, u);
            case 7 -> death(g, font, w, h, u);
            case 8 -> typing(g, font, w, h, u);
            case 9 -> caption(g, font, w, h, u);
            default -> { }
        }
    }

    static void centered(GuiGraphics g, Font font, String s, int x, int y, float sc, int col) {
        var p = g.pose();
        p.pushMatrix(); p.translate(x, y); p.scale(sc, sc);
        g.drawCenteredString(font, s, 0, 0, col);
        p.popMatrix();
    }

    /** 가짜 크래시 화면 → 마지막에 "속았지?" */
    static void crash(GuiGraphics g, Font font, int w, int h, double u) {
        if (u > 0.82) { g.fill(0, 0, w, h, 0xFF000000); centered(g, font, "§d속았지? ㅋㅋ", w / 2, h / 2 - 6, 2.5f, 0xFFFFFFFF); return; }
        g.fill(0, 0, w, h, 0xFF1E1E1E);
        String[] L = {"§fMinecraft has crashed!", "", "§7Minecraft has stopped running because it encountered a problem; " + text,
                "", "§7This error has been saved to C:\\minecraft\\crash-reports\\crash-command-guard.txt", "",
                "§c---- Minecraft Crash Report ----", "§c// " + "Who set us up the TNT?",
                "§cjava.lang.CommandGuardException: player looked away for too long",
                "§c    at jjk.CommandGuard.tick(CommandGuard.java:404)", "§c    at net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1)",
                "§c    at ...wait, are you reading this?"};
        for (int i = 0; i < L.length; i++) g.drawString(font, L[i], 20, 30 + i * 11, 0xFFFFFFFF, false);
        g.fill(w / 2 - 100, h - 50, w / 2 - 4, h - 30, 0xFF555555);
        g.fill(w / 2 + 4, h - 50, w / 2 + 100, h - 30, 0xFF555555);
        g.drawCenteredString(font, "Return to Title", w / 2 - 52, h - 44, 0xFF888888);
        g.drawCenteredString(font, "Copy to Clipboard", w / 2 + 52, h - 44, 0xFF888888);
    }

    /** 핸드폰 과부하 가짜 크래시 화면 → 끝에 "농담이야, 폰만 재부팅했어". */
    static void phoneCrash(GuiGraphics g, Font font, int w, int h, double u) {
        if (u > 0.85) {
            g.fill(0, 0, w, h, 0xFF000000);
            centered(g, font, "§d…농담이야. 핸드폰만 재부팅했어.", w / 2, h / 2 - 10, 2f, 0xFFFFFFFF);
            centered(g, font, "§7(앱 사용 기록 초기화됨)", w / 2, h / 2 + 14, 1f, 0xFFFFFFFF);
            return;
        }
        g.fill(0, 0, w, h, 0xFF1E1E1E);
        String[] L = {"§fMinecraft has crashed!", "",
                "§7Minecraft has stopped running because it encountered a problem; 개발자의 핸드폰 과부하",
                "§7This error has been saved to C:\\minecraft\\crash-reports\\crash-developer-phone.txt", "",
                "§c---- Minecraft Crash Report ----", "§c// 그러니까 하나씩만 쓰라고 했잖아.", "",
                "§cjava.lang.IllegalStateException: PhoneOverloadException: 12/12 apps running at once",
                "§c    at jjk.DevPhone.overload(DevPhone.java:2147483647)", "§c    at jjk.DevPhone.battery(DevPhone.java:-2147483648)", "",
                "§e-- Developer Phone --", "§e    Apps executed: §f" + text, "§e    Battery: §f-2147483648%", "§e    Cause: §f누군가 앱을 전부 써버림"};
        for (int i = 0; i < L.length; i++) g.drawString(font, L[i], 20, 24 + i * 11, 0xFFFFFFFF, false);
        g.fill(w / 2 - 100, h - 46, w / 2 - 4, h - 26, 0xFF555555);
        g.fill(w / 2 + 4, h - 46, w / 2 + 100, h - 26, 0xFF555555);
        g.drawCenteredString(font, "Return to Title", w / 2 - 52, h - 40, 0xFF888888);
        g.drawCenteredString(font, "Copy to Clipboard", w / 2 + 52, h - 40, 0xFF888888);
    }

    /** 가짜 일시정지 메뉴 — 버튼이 하나씩 커맨드 블록으로 바뀜. */
    static void pause(GuiGraphics g, Font font, int w, int h, double u) {
        g.fill(0, 0, w, h, 0x90101010);
        centered(g, font, "게임 메뉴", w / 2, 40, 1, 0xFFFFFFFF);
        String[] B = {"게임으로 돌아가기", "도전 과제", "통계", "설정...", "저장하고 타이틀로 나가기"};
        for (int i = 0; i < B.length; i++) {
            int y = 70 + i * 26;
            boolean swapped = u > 0.3 + i * 0.1;
            g.fill(w / 2 - 100, y, w / 2 + 100, y + 20, swapped ? 0xFF3A1A5A : 0xFF6B6B6B);
            g.drawCenteredString(font, swapped ? "§d/execute as @s run " + (i == 4 ? "stay" : "obey") : B[i], w / 2, y + 6, 0xFFFFFFFF);
        }
        if (u > 0.85) centered(g, font, "§d" + text, w / 2, h - 40, 1.5f, 0xFFFFFFFF);
    }

    /** 화면 가장자리에서 커맨드 블록이 빼꼼 + 말풍선. */
    static void peek(GuiGraphics g, Font font, int w, int h, double u) {
        double in = Math.min(1, Math.min(u / 0.12, (1 - u) / 0.12));
        int size = 48;
        int x = (int) (w - in * (size + 6)), y = h / 2 - size / 2 + (int) (Math.sin(u * 30) * 3);
        var p = g.pose();
        p.pushMatrix(); p.translate(x, y); p.scale(3f, 3f);
        g.renderItem(new ItemStack(Items.COMMAND_BLOCK), 0, 0);
        p.popMatrix();
        // 눈 (깜빡임)
        if (((long) (u * 40)) % 9 != 0) { g.fill(x + 14, y + 18, x + 20, y + 26, 0xFF55FFFF); g.fill(x + 28, y + 18, x + 34, y + 26, 0xFF55FFFF); }
        if (in > 0.9) {
            int bw = font.width(text) + 12;
            int bx = Math.max(4, x - bw - 8), by = y - 6;
            g.fill(bx, by, bx + bw, by + 18, 0xF0FFFFFF);
            g.fill(bx + bw, by + 6, bx + bw + 6, by + 10, 0xF0FFFFFF);
            g.drawString(font, text, bx + 6, by + 5, 0xFF2A0A40, false);
        }
    }

    /** 화면에 금이 감 (안쪽에서 두드린 것처럼). */
    static void cracks(GuiGraphics g, Font font, int w, int h, double u) {
        int ox = (int) (cx * w), oy = (int) (cy * h);
        Random r = new Random(at);
        int arms = (int) (6 + 10 * Math.min(1, u * 3));
        for (int a = 0; a < arms; a++) {
            double ang = r.nextDouble() * Math.PI * 2, len = 40 + r.nextDouble() * Math.min(w, h) * 0.5 * Math.min(1, u * 2.5);
            double x = ox, y = oy;
            for (int s = 0; s < 8; s++) {
                ang += (r.nextDouble() - 0.5) * 0.7;
                double nx = x + Math.cos(ang) * len / 8, ny = y + Math.sin(ang) * len / 8;
                line(g, (int) x, (int) y, (int) nx, (int) ny, 0xE0FFFFFF);
                line(g, (int) x + 1, (int) y + 1, (int) nx + 1, (int) ny + 1, 0x80000000);
                x = nx; y = ny;
            }
        }
        g.fill(ox - 3, oy - 3, ox + 3, oy + 3, 0xFFFFFFFF);
        if (u > 0.35) centered(g, font, "§f" + text, w / 2, h - 60, 1.4f, 0xFFFFFFFF);
    }
    static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) { int x = x0 + (x1 - x0) * i / Math.max(1, n), y = y0 + (y1 - y0) * i / Math.max(1, n); g.fill(x, y, x + 1, y + 1, col); }
    }

    static void disconnect(GuiGraphics g, Font font, int w, int h, double u) {
        if (u > 0.8) { centered(g, font, "§d" + text, w / 2, h / 2, 2f, 0xFFFFFFFF); return; }
        g.fill(0, 0, w, h, 0xFF2B1E14);
        for (int y = 0; y < h; y += 16) for (int x = (y / 16 % 2) * 8; x < w; x += 16) g.fill(x, y, x + 8, y + 8, 0xFF241910);   // 흙 배경 흉내
        centered(g, font, "연결이 끊어졌습니다", w / 2, h / 2 - 30, 1, 0xFFAAAAAA);
        centered(g, font, "§f커맨드 수호가 서버에서 당신을 추방했습니다: 너무 강해서", w / 2, h / 2 - 10, 1, 0xFFFFFFFF);
        g.fill(w / 2 - 100, h / 2 + 20, w / 2 + 100, h / 2 + 40, 0xFF6B6B6B);
        g.drawCenteredString(font, "서버 목록으로 돌아가기", w / 2, h / 2 + 26, 0xFFFFFFFF);
    }

    static void death(GuiGraphics g, Font font, int w, int h, double u) {
        if (u > 0.8) { centered(g, font, "§d" + text, w / 2, h / 2, 2f, 0xFFFFFFFF); return; }
        g.fill(0, 0, w, h, 0x90800000);
        centered(g, font, "사망했습니다!", w / 2, h / 2 - 50, 2, 0xFFFFFFFF);
        String name = Minecraft.getInstance().player == null ? "너" : Minecraft.getInstance().player.getName().getString();
        centered(g, font, name + "이(가) 커맨드 수호의 장난에 당했습니다", w / 2, h / 2 - 22, 1, 0xFFFFFFFF);
        centered(g, font, "점수: §e2147483647", w / 2, h / 2 - 8, 1, 0xFFFFFFFF);
        g.fill(w / 2 - 100, h / 2 + 14, w / 2 + 100, h / 2 + 34, 0xFF4A4A4A);
        g.drawCenteredString(font, u > 0.4 ? "§8리스폰 (권한 없음)" : "리스폰", w / 2, h / 2 + 20, 0xFFFFFFFF);
    }

    /** 채팅 입력창이 열리고 누군가 몰래 타이핑했다가 지움. */
    static void typing(GuiGraphics g, Font font, int w, int h, double u) {
        String cmd = text;
        int nType = (int) (cmd.length() * Math.min(1, u / 0.45));
        int nDel = (int) (cmd.length() * Math.max(0, (u - 0.6) / 0.3));
        String s = cmd.substring(0, Math.max(0, Math.min(cmd.length(), nType) - nDel));
        g.fill(2, h - 14, w - 2, h - 2, 0x80000000);
        g.drawString(font, s + ((now() / 300) % 2 == 0 ? "_" : ""), 4, h - 12, 0xFFFFFFFF, false);
        if (u > 0.45 && u < 0.6) centered(g, font, "§d......", w / 2, h - 34, 1, 0xFFFFFFFF);
        if (u > 0.9) centered(g, font, "§d아냐, 안 할게.", w / 2, h - 34, 1.2f, 0xFFFFFFFF);
    }

    static void caption(GuiGraphics g, Font font, int w, int h, double u) {
        double a = Math.min(1, Math.min(u / 0.08, (1 - u) / 0.12));
        int al = Math.max(8, (int) (255 * a));
        String[] L = text.split("\n");
        int y = h / 4;
        g.fill(0, y - 10, w, y + L.length * 14 + 6, ((int) (0xA0 * a)) << 24);
        for (int i = 0; i < L.length; i++) centered(g, font, L[i], w / 2, y + i * 14, i == 0 ? 1.3f : 1f, (al << 24) | 0xFFFFFF);
    }

    /** 보스 대사창: 화면 아래 타자기 효과 + 화자 이름표. */
    static void dialogue(GuiGraphics g, Font font, int w, int h, double u) {
        if (u > 1) { dlgAt = 0; return; }
        double a = Math.min(1, Math.min(u / 0.06, (1 - u) / 0.1));
        int bw = Math.min(w - 40, 360), bx = (w - bw) / 2, by = h - 92, bh = 46;
        int al = Math.max(8, (int) (255 * a));
        g.fill(bx, by, bx + bw, by + bh, ((int) (0xD0 * a)) << 24 | 0x0A0E14);
        g.fill(bx, by, bx + bw, by + 1, (al << 24) | 0x55FFFF);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, (al << 24) | 0x55FFFF);
        int nw = font.width(dlgWho) + 12;
        g.fill(bx + 8, by - 11, bx + 8 + nw, by + 1, (al << 24) | 0x1A4A5A);
        g.drawString(font, dlgWho, bx + 14, by - 9, (al << 24) | 0x55FFFF, false);
        g.renderItem(new ItemStack(Items.PLAYER_HEAD), bx + 8, by + 15);
        int shown = (int) Math.min(dlgText.length(), (now() - dlgAt) / 55);
        String cur = dlgText.substring(0, shown) + (shown < dlgText.length() && (now() / 120) % 2 == 0 ? "▌" : "");
        g.drawString(font, cur, bx + 32, by + 19, (al << 24) | 0xFFFFFF, true);
    }

    /** 굴착기 MK-0 HUD: 속도·고도·상태·드릴·포탑·처치 + 조작 안내. 데이터 = "속도|최고|y|상태|드릴%|포탑%|처치|체력%" */
    static void wormHud(GuiGraphics g, Font font, int w, int h) {
        String[] d = vhud.split("\\|");
        if (d.length < 7) return;
        double spd, max, y; float drill, laser; int kills;
        try { spd = Double.parseDouble(d[0]); max = Double.parseDouble(d[1]); y = Double.parseDouble(d[2]); drill = Float.parseFloat(d[4]); laser = Float.parseFloat(d[5]); kills = Integer.parseInt(d[6]); }
        catch (NumberFormatException ex) { return; }
        int pw = 220, x0 = w / 2 - pw / 2, y0 = h - 88, x1 = x0 + pw, y1 = h - 34;
        int red = 0xFF3030;
        MechHud.panel(g, x0, y0, x1, y1, red);
        g.drawString(font, "§c§l굴착기 MK-0 §7· " + d[3], x0 + 8, y0 + 5, 0xFFFFFFFF, true);
        g.drawString(font, String.format(java.util.Locale.ROOT, "§7처치 §f%d", kills), x1 - 44, y0 + 5, 0xFFFFFFFF, true);
        // 속도 게이지
        int bx = x0 + 8, bw = pw - 16;
        float sv = (float) Math.min(1, spd / Math.max(0.01, max));
        g.drawString(font, String.format(java.util.Locale.ROOT, "§7속도 §f%.1f §7블록/초", spd * 20), bx, y0 + 17, 0xFFFFFFFF, false);
        g.drawString(font, String.format(java.util.Locale.ROOT, "§7고도 §f%.0f", y), x1 - 52, y0 + 17, 0xFFFFFFFF, false);
        g.fill(bx, y0 + 27, bx + bw, y0 + 30, 0x40FFFFFF);
        g.fill(bx, y0 + 27, bx + Math.round(bw * sv), y0 + 30, sv > 0.8f ? 0xFFFFD84A : 0xFFFF5050);
        // 드릴 · 포탑
        int half = bw / 2 - 4;
        g.drawString(font, drill >= 1 ? "§a드릴 준비" : "§7드릴 충전", bx, y0 + 34, 0xFFFFFFFF, false);
        g.fill(bx, y0 + 44, bx + half, y0 + 46, 0x40FFFFFF); g.fill(bx, y0 + 44, bx + Math.round(half * Math.min(1, drill)), y0 + 46, 0xFF55FF55);
        g.drawString(font, "§7포탑 ×6", bx + half + 8, y0 + 34, 0xFFFFFFFF, false);
        g.fill(bx + half + 8, y0 + 44, bx + half * 2 + 8, y0 + 46, 0x40FFFFFF); g.fill(bx + half + 8, y0 + 44, bx + half + 8 + Math.round(half * Math.min(1, laser)), y0 + 46, 0xFFFF3030);
        String keys = "§8W §7전진  §8Ctrl §7부스트  §8Space §7상승  §8Shift §7하강  §8우클릭 §7드릴  §8웅크리고 우클릭 §7하차";
        g.drawString(font, keys, w / 2 - font.width(keys) / 2, y1 + 4, 0xFFFFFFFF, true);
    }

    /** 가짜 도전 과제 토스트 (오른쪽 위). */
    static void toast(GuiGraphics g, Font font, int w, double u) {
        if (u > 1) { toastKind = -1; return; }
        double in = Math.min(1, Math.min(u / 0.08, (1 - u) / 0.08));
        int tw = Math.max(160, font.width(toastText) + 40), x = (int) (w - in * tw), y = 4;
        g.fill(x, y, x + tw, y + 32, 0xF0202020);
        g.fill(x, y, x + tw, y + 1, 0xFF8040C0); g.fill(x, y + 31, x + tw, y + 32, 0xFF8040C0);
        g.renderItem(new ItemStack(Items.COMMAND_BLOCK), x + 8, y + 8);
        g.drawString(font, "§e도전 과제 달성!", x + 30, y + 7, 0xFFFFFFFF, false);
        g.drawString(font, toastText, x + 30, y + 18, 0xFFFFFFFF, false);
    }
}
