package com.tubilt.jjk.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * 커맨드 수호 명령 콘솔 (내 화면): 명령어가 입력창에 한 글자씩 타이핑되고 → Enter → 「실행됨」 스탬프와 함께
 * 위로 밀려 올라가 기록으로 남았다가 사라짐. 실시간(ms) 기준이라 렉·슬로모션 영향 없음.
 */
public final class CmdConsole {
    private CmdConsole() {}

    static final class Line {
        final String text; final int color; final long start;
        long done = -1; int lastTyped = 0;
        Line(String t, int c) { text = t; color = c & 0xFFFFFF; start = System.currentTimeMillis(); }
    }
    static final List<Line> QUEUE = new ArrayList<>();   // 입력 대기
    static final List<Line> HIST = new ArrayList<>();    // 실행된 기록
    static Line cur;
    static final double CPS = 70;                         // 초당 타이핑 글자 수
    static final long ENTER = 140, KEEP = 1500, FADE = 400;
    static final int MAX_HIST = 2;
    static String lastText = ""; static long lastAt = 0;

    public static void push(String text, int color) {
        if (text == null || text.isBlank() || !text.startsWith("/")) return;     // 명령어만
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastAt < 4000) return;              // 같은 명령 반복은 생략
        lastText = text; lastAt = now;
        if (QUEUE.size() >= 1) QUEUE.remove(0);                                 // 밀리면 최신 것만
        QUEUE.add(new Line(text, color));
    }

    static void click(float pitch) {
        try { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.12f)); } catch (Exception ignored) { }
    }

    static void render(GuiGraphics g, Font font, int w, int h) {
        long now = System.currentTimeMillis();
        if (cur == null && !QUEUE.isEmpty()) { cur = QUEUE.remove(0); cur = new Line(cur.text, cur.color); }
        int bw = Math.min(w - 12, Math.max(200, w / 3)), bx = (w - bw) / 2 + Math.min(40, w / 20), by = h - 66;   // 화면 아래 가운데 (체력·방어구 줄 위)

        // ── 기록 (위로 쌓임, 오래된 것부터 사라짐)
        HIST.removeIf(l -> now - l.done > KEEP + FADE);
        int y = by - 14;
        while (HIST.size() > MAX_HIST) HIST.remove(0);
        for (int i = HIST.size() - 1; i >= 0; i--) {
            Line l = HIST.get(i);
            long age = now - l.done;
            float a = age > KEEP ? 1 - (age - KEEP) / (float) FADE : 1;
            float slide = Math.min(1, age / 120f);
            int al = (int) (a * 255);
            if (al < 8) continue;
            int yy = (int) (y + (1 - slide) * 12);
            g.fill(bx, yy - 2, bx + Math.min(bw, font.width(l.text) + 60), yy + 9, ((int) (a * 0x90)) << 24);
            g.fill(bx, yy - 2, bx + 2, yy + 9, (al << 24) | l.color);
            g.drawString(font, l.text, bx + 6, yy, (al << 24) | 0xC8C8C8, false);
            g.drawString(font, "✔", bx + 10 + font.width(l.text), yy, (al << 24) | 0x55FF55, false);
            y -= 12;
        }

        if (cur == null) return;
        // ── 입력창
        long el = now - cur.start;
        int n = (int) Math.min(cur.text.length(), el / 1000.0 * CPS + 1);
        if (n > cur.lastTyped) { if (n / 3 != cur.lastTyped / 3) click(1.6f + (n % 5) * 0.08f); cur.lastTyped = n; }
        long typedAt = (long) (cur.text.length() / CPS * 1000);
        boolean entered = el > typedAt + ENTER;
        float pop = entered ? Math.min(1, (el - typedAt - ENTER) / 90f) : 0;
        int col = 0xFF000000 | cur.color;
        g.fill(bx - 1, by - 3, bx + bw + 1, by + 12, entered ? (0xC0000000 | (cur.color & 0x303030)) : 0xC0000000);
        g.fill(bx - 1, by - 3, bx + bw + 1, by - 2, col);
        g.fill(bx - 1, by + 11, bx + (int) ((bw + 2) * Math.min(1, el / (double) Math.max(1, typedAt))), by + 12, col);
        String shown = cur.text.substring(0, n);
        boolean caret = !entered && (now / 250) % 2 == 0;
        g.drawString(font, "§7>", bx + 3, by + 1, 0xFFFFFFFF, false);
        g.drawString(font, shown + (caret ? "_" : ""), bx + 13, by + 1, 0xFFFFFFFF, true);
        if (entered) {
            // Enter: 스탬프 + 번쩍
            String stamp = "↵ 실행됨";
            int sx = bx + bw - font.width(stamp) - 6;
            g.fill(sx - 4, by - 2, sx + font.width(stamp) + 3, by + 11, ((int) (pop * 200)) << 24 | (cur.color & 0xFFFFFF));
            g.drawString(font, stamp, sx, by + 1, 0xFFFFFFFF, true);
            if (pop < 1) g.fill(bx - 1, by - 3, bx + bw + 1, by + 12, ((int) ((1 - pop) * 120)) << 24 | 0xFFFFFF);
            if (cur.done < 0) { cur.done = now; click(0.7f); }
            if (now - cur.done > 260) { HIST.add(cur); cur = null; }
        }
    }
}
