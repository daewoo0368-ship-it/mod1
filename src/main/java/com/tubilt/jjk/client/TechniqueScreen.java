package com.tubilt.jjk.client;

import com.tubilt.jjk.Techniques;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 술식 선택 화면 (기본 V 키).
 *  - 해금된 능력만 보이고, 캐릭터(고죠·코밍·스쿠나 …)별로 머리글과 함께 나뉘어 표시된다.
 *  - 능력이 많으면 열 수를 늘리고 버튼 높이를 줄여 화면에 맞춘다.
 */
public class TechniqueScreen extends Screen {
    public TechniqueScreen() { super(Component.literal("술식 선택")); }

    private final List<int[]> heads = new ArrayList<>();      // {y}
    private final List<String> headText = new ArrayList<>();
    private final List<Integer> headColor = new ArrayList<>();
    private int headX, headW;

    @Override
    protected void init() {
        heads.clear(); headText.clear(); headColor.clear();
        List<Techniques.Tech> list = JjkClient.visible();
        if (list.isEmpty()) return;
        // 캐릭터별 묶음 (등장 순서 유지)
        Map<String, List<Techniques.Tech>> groups = new LinkedHashMap<>();
        for (Techniques.Tech t : list) groups.computeIfAbsent(t.user(), k -> new ArrayList<>()).add(t);

        int gap = 4, HEAD = 12, availH = Math.max(60, height - 70);
        int cols = 2, bh = 20;
        for (cols = 2; cols <= 6; cols++) {
            if (layoutHeight(groups, cols, 20 + gap, HEAD) <= availH) break;
        }
        if (cols > 6) cols = 6;
        int total = layoutHeight(groups, cols, 20 + gap, HEAD);
        if (total > availH) {   // 그래도 넘치면 버튼 높이를 줄인다
            for (bh = 18; bh >= 12; bh -= 2) if (layoutHeight(groups, cols, bh + gap, HEAD) <= availH) break;
            bh = Math.max(12, bh);
            total = layoutHeight(groups, cols, bh + gap, HEAD);
        }
        int bw = Math.min(170, (width - 24 - (cols - 1) * gap) / cols);
        int totalW = cols * bw + (cols - 1) * gap;
        int x0 = (width - totalW) / 2, y = Math.max(26, (height - total) / 2);
        headX = x0; headW = totalW;
        Techniques.Tech sel = JjkClient.selectedTech();
        for (Map.Entry<String, List<Techniques.Tech>> g : groups.entrySet()) {
            heads.add(new int[]{y});
            headText.add(g.getKey() + "  §8(" + g.getValue().size() + ")");
            headColor.add(g.getValue().get(0).color());
            y += HEAD;
            int c = 0;
            for (Techniques.Tech t : g.getValue()) {
                final int idx = Techniques.ALL.indexOf(t);
                int x = x0 + c * (bw + gap);
                Button b = Button.builder(Component.literal((t == sel ? "▶ " : "") + t.name()), btn -> {
                            JjkClient.selected = idx;
                            JjkClient.saveSelected();
                            JjkHud.pulse();
                            onClose();
                        })
                        .bounds(x, y, bw, bh)
                        .tooltip(Tooltip.create(Component.literal(t.user() + "\n" + t.desc() + "\n주력 " + t.cost())))
                        .build();
                addRenderableWidget(b);
                if (++c == cols) { c = 0; y += bh + gap; }
            }
            if (c != 0) y += bh + gap;
        }
    }

    /** 이 열 수로 배치했을 때 필요한 전체 높이. */
    private static int layoutHeight(Map<String, List<Techniques.Tech>> groups, int cols, int rowH, int head) {
        int h = 0;
        for (List<Techniques.Tech> l : groups.values()) h += head + ((l.size() + cols - 1) / cols) * rowH;
        return h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, "§l술식 선택", width / 2, 12, 0xFFFFFFFF);
        for (int i = 0; i < heads.size(); i++) {
            int hy = heads.get(i)[0];
            int col = 0xFF000000 | headColor.get(i);
            g.fill(headX, hy + 9, headX + headW, hy + 10, (col & 0x00FFFFFF) | 0x90000000);
            g.fill(headX, hy + 1, headX + 2, hy + 9, col);
            g.drawString(font, "§l" + headText.get(i), headX + 5, hy + 1, 0xFFFFFFFF, true);
        }
        if (JjkClient.visible().isEmpty()) {
            String msg = JjkClient.unlockInfo && JjkClient.job == null ? "§7직업 선택서로 직업을 먼저 고르세요" : "§7해금된 능력이 없습니다 — 능력 아이템을 우클릭해 해금하세요";
            g.drawCenteredString(font, msg, width / 2, height / 2, 0xFFFFFFFF);
        }
        g.drawCenteredString(font, "§7고른 술식은 시전 키(기본 R)로 사용합니다. 웅크린 채 누르면 영창/강화판.", width / 2, height - 18, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
