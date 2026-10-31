package com.tubilt.jjk.client;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 건축물 소환기 관리 화면 (웅크리고 우클릭).
 * 탭: 고정 설계도 · 사용자 설계도 · 소환 설정 · 영역 선택/저장. 화면은 서버가 보내 준 상태만 보여 주고,
 * 모든 동작은 서버에 요청으로 보낸다(서버가 권한·값을 다시 검사).
 */
public class BlueprintScreen extends Screen {
    // 서버 동기화 상태 (op 번호는 StructureSpawner.OP_* 와 같다)
    static final int OP_SELECT = 1, OP_SPAWN = 3, OP_PREVIEW = 4, OP_CANCEL = 5, OP_RENAME = 6, OP_DESC = 7, OP_COPY = 8,
            OP_DELETE = 9, OP_SAVE = 10, OP_SETTINGS = 11, OP_CLEAR_SEL = 12, OP_MODE = 13;
    static final String[] TABS = {"고정 설계도", "사용자 설계도", "소환 설정", "영역 선택·저장"};
    static final String[] POLICY = {"안전 배치", "빈 공간만", "덮어쓰기"};
    static final String[] POLICY_DESC = {"기존 블록이 있으면 소환 거부 (기본값)", "공기인 위치에만 배치, 나머지는 건너뜀", "기존 블록을 교체 — 개발자 권한 필요"};

    static Payloads.BpList data;
    static Payloads.BpDetail detail;
    static int tab = 0, page = 0;
    static String viewId;
    static String flash = ""; static long flashAt;

    EditBox nameBox, descBox, saveBox;
    String keepName = "", keepDesc = "", keepSave = "", prefilled = "";
    String deleteArmed = "";

    public BlueprintScreen() { super(Component.literal("건축물 소환기")); }

    // ───────────── 수신 ─────────────
    public static void receive(Payloads.BpList p) {
        data = p;
        if (!p.message().isEmpty()) { flash = p.message(); flashAt = System.currentTimeMillis(); }
        Minecraft mc = Minecraft.getInstance();
        if (viewId == null || find(viewId) == null) viewId = p.selected();
        if (mc.screen instanceof BlueprintScreen s) s.refresh();
        else if (p.open()) mc.setScreen(new BlueprintScreen());
    }

    public static void receiveDetail(Payloads.BpDetail d) { detail = d; }

    static Payloads.BpEntry find(String id) {
        if (data == null || id == null) return null;
        for (Payloads.BpEntry e : data.entries()) if (e.id().equals(id)) return e;
        return null;
    }

    static void req(int op, String id, String text) { JjkNet.toServer(new Payloads.BpReq(op, id == null ? "" : id, text == null ? "" : text)); }

    int set(int i) { return data != null && data.settings().size() > i ? data.settings().get(i) : 0; }

    void sendSettings(int rot, int mirror, int policy, int anchor, int ox, int oy, int oz) {
        req(OP_SETTINGS, "", rot + "," + mirror + "," + policy + "," + anchor + "," + ox + "," + oy + "," + oz);
    }

    void refresh() {
        if (nameBox != null) keepName = nameBox.getValue();
        if (descBox != null) keepDesc = descBox.getValue();
        if (saveBox != null) keepSave = saveBox.getValue();
        rebuildWidgets();
    }

    // ───────────── 위젯 ─────────────
    int listW() { return Math.min(160, width / 3); }

    @Override
    protected void init() {
        nameBox = descBox = saveBox = null;
        int tw = Math.min(96, (width - 16) / 4 - 2);
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            addRenderableWidget(Button.builder(Component.literal((tab == i ? "§e▶ " : "") + TABS[i]), b -> { tab = t; page = 0; deleteArmed = ""; refresh(); })
                    .bounds(8 + i * (tw + 2), 6, tw, 18).build());
        }
        if (data == null) return;
        switch (tab) {
            case 0, 1 -> initList(tab == 0);
            case 2 -> initSettings();
            default -> initSelection();
        }
    }

    List<Payloads.BpEntry> entries(boolean fixed) {
        List<Payloads.BpEntry> l = new ArrayList<>();
        for (Payloads.BpEntry e : data.entries()) if (e.fixed() == fixed) l.add(e);
        return l;
    }

    int perPage() { return Math.max(3, (height - 30 - 84) / 20); }

    void initList(boolean fixed) {
        List<Payloads.BpEntry> l = entries(fixed);
        int per = perPage(), pages = Math.max(1, (l.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        int y = 30, lw = listW();
        for (int k = page * per; k < Math.min(l.size(), (page + 1) * per); k++) {
            Payloads.BpEntry e = l.get(k);
            String mark = e.id().equals(data.selected()) ? "§a★ " : e.id().equals(viewId) ? "§e▶ " : "";
            String label = mark + e.name();
            if (font.width(label) > lw - 8) label = font.plainSubstrByWidth(label, lw - 14) + "…";
            addRenderableWidget(Button.builder(Component.literal(label), b -> { viewId = e.id(); deleteArmed = ""; prefilled = ""; req(OP_SELECT, e.id(), ""); })
                    .bounds(8, y, lw, 18)
                    .tooltip(Tooltip.create(Component.literal(e.name() + "\n" + e.sx() + "×" + e.sy() + "×" + e.sz() + " · 블록 " + e.blocks() + "\n" + e.desc())))
                    .build());
            y += 20;
        }
        int py = 30 + per * 20 + 2;
        Button prev = Button.builder(Component.literal("◀"), b -> { page--; refresh(); }).bounds(8, py, 24, 16).build();
        Button next = Button.builder(Component.literal("▶"), b -> { page++; refresh(); }).bounds(8 + lw - 24, py, 24, 16).build();
        prev.active = page > 0; next.active = page < pages - 1;
        addRenderableWidget(prev); addRenderableWidget(next);

        Payloads.BpEntry v = find(viewId);
        int by = height - 24, x = 8;
        x = button(x, by, 64, "소환", "이 설계도를 선택하고 바라보는 곳에 소환", v != null, () -> { req(OP_SELECT, v.id(), ""); req(OP_SPAWN, v.id(), ""); onClose(); });
        x = button(x, by, 70, "미리보기", "배치될 영역 윤곽과 충돌 위치를 월드에 표시", v != null, () -> req(OP_PREVIEW, v.id(), ""));
        x = button(x, by, 70, fixed ? "사용자로 복사" : "복사", "이 설계도를 내 사용자 설계도로 복사", v != null, () -> req(OP_COPY, v.id(), ""));
        x = button(x, by, 76, "작업 취소", "진행 중인 소환·저장 작업 취소", !data.job().isEmpty(), () -> req(OP_CANCEL, "", ""));
        if (!fixed) {
            boolean ed = v != null && v.editable();
            boolean armed = v != null && v.id().equals(deleteArmed);
            button(x, by, 70, armed ? "§c정말 삭제" : "삭제", "두 번 누르면 삭제 (월드 폴더 deleted 에 보관)", ed, () -> {
                if (v.id().equals(deleteArmed)) { req(OP_DELETE, v.id(), ""); deleteArmed = ""; } else { deleteArmed = v.id(); refresh(); }
            });
            int ey = height - 48, ew = Math.max(80, (width - 8 - 8 - 2 * 74) / 2 - 4);
            if (v != null && !v.id().equals(prefilled)) { keepName = v.name(); keepDesc = v.desc(); prefilled = v.id(); }
            nameBox = box(8, ey, ew, keepName, 40, "이름");
            button(8 + ew + 2, ey, 70, "이름 변경", "설계도 이름 변경", ed, () -> req(OP_RENAME, v.id(), nameBox.getValue()));
            int dx = 8 + ew + 76;
            descBox = box(dx, ey, ew, keepDesc, 200, "설명");
            button(dx + ew + 2, ey, 70, "설명 수정", "설계도 설명 수정", ed, () -> req(OP_DESC, v.id(), descBox.getValue()));
        }
    }

    void initSettings() {
        int rot = set(0), mir = set(1), pol = set(2), anc = set(3), ox = set(4), oy = set(5), oz = set(6);
        boolean admin = set(8) == 1;
        int x0 = 8, y = 44;
        for (int r = 0; r < 4; r++) {
            final int rr = r;
            addRenderableWidget(Button.builder(Component.literal((rot == r ? "§e" : "") + (r * 90) + "°"), b -> sendSettings(rr, mir, pol, anc, ox, oy, oz)).bounds(x0 + 70 + r * 46, y, 44, 18).build());
        }
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("좌우 반전: " + (mir == 1 ? "§e켬" : "끔")), b -> sendSettings(rot, 1 - mir, pol, anc, ox, oy, oz)).bounds(x0 + 70, y, 182, 18).build());
        y += 24;
        int nextPol = (pol + 1) % 3;
        if (nextPol == 2 && !admin) nextPol = 0;
        final int np = nextPol;
        addRenderableWidget(Button.builder(Component.literal("기존 블록: " + POLICY[Math.max(0, Math.min(2, pol))]), b -> sendSettings(rot, mir, np, anc, ox, oy, oz))
                .bounds(x0 + 70, y, 182, 18).tooltip(Tooltip.create(Component.literal(String.join("\n", POLICY[0] + " — " + POLICY_DESC[0], POLICY[1] + " — " + POLICY_DESC[1], POLICY[2] + " — " + POLICY_DESC[2] + (admin ? "" : " §c(권한 없음)"))))).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("기준점: " + (anc == 1 ? "내 위치" : "바라보는 블록")), b -> sendSettings(rot, mir, pol, 1 - anc, ox, oy, oz)).bounds(x0 + 70, y, 182, 18).build());
        y += 24;
        String[] ax = {"X", "Y", "Z"};
        int[] steps = {-10, -1, 1, 10};
        for (int a = 0; a < 3; a++) {
            final int aa = a;
            for (int k = 0; k < 4; k++) {
                final int d = steps[k];
                int bx = x0 + 70 + (k < 2 ? k * 32 : 118 + (k - 2) * 32);
                addRenderableWidget(Button.builder(Component.literal(d > 0 ? "+" + d : String.valueOf(d)), b -> {
                    int[] w = {ox, oy, oz}; w[aa] += d;
                    sendSettings(rot, mir, pol, anc, w[0], w[1], w[2]);
                }).bounds(bx, y, 30, 18).tooltip(Tooltip.create(Component.literal(ax[aa] + " 오프셋 " + (d > 0 ? "+" : "") + d))).build());
            }
            y += 22;
        }
        y += 6;
        int x = x0;
        x = button(x, y, 110, "배치 전 미리보기", "윤곽·충돌 위치 표시 + 배치 가능 여부 검사", true, () -> req(OP_PREVIEW, "", ""));
        x = button(x, y, 80, "소환", "선택한 설계도를 지금 설정으로 소환", true, () -> { req(OP_SPAWN, "", ""); onClose(); });
        button(x, y, 110, "소환 취소", "진행 중인 소환 작업 취소 (놓인 부분까지 마무리)", !data.job().isEmpty(), () -> req(OP_CANCEL, "", ""));
    }

    void initSelection() {
        boolean sel = set(7) == 1;
        int y = 44;
        addRenderableWidget(Button.builder(Component.literal("모드: " + (sel ? "§e영역 선택" : "소환")), b -> req(OP_MODE, "", sel ? "spawn" : "select"))
                .bounds(8, y, 160, 18).tooltip(Tooltip.create(Component.literal("영역 선택 모드: 소환기로 블록을 우클릭해 시작점·끝점 지정\n소환 모드: 우클릭으로 선택한 설계도 소환"))).build());
        button(172, y, 90, "선택 해제", "지정한 모서리 지우기", true, () -> req(OP_CLEAR_SEL, "", ""));
        y += 60;
        saveBox = box(8, y, Math.min(220, width - 120), keepSave, 40, "설계도 이름");
        button(8 + Math.min(220, width - 120) + 4, y, 90, "저장", "선택 영역을 사용자 설계도로 저장 (월드는 바뀌지 않음)", true, () -> { req(OP_SAVE, "", saveBox.getValue()); saveBox.setValue(""); });
        button(8, y + 24, 90, "저장 취소", "진행 중인 저장 작업 취소", !data.job().isEmpty(), () -> req(OP_CANCEL, "", ""));
    }

    int button(int x, int y, int w, String label, String tip, boolean active, Runnable r) {
        Button b = Button.builder(Component.literal(label), btn -> r.run()).bounds(x, y, w, 18).tooltip(Tooltip.create(Component.literal(tip))).build();
        b.active = active;
        addRenderableWidget(b);
        return x + w + 4;
    }

    EditBox box(int x, int y, int w, String value, int max, String hint) {
        EditBox e = new EditBox(font, x, y, w, 18, Component.literal(hint));
        e.setMaxLength(max);
        e.setValue(value == null ? "" : value);
        e.setHint(Component.literal("§8" + hint));
        addRenderableWidget(e);
        return e;
    }

    // ───────────── 그리기 ─────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (data == null) { g.drawCenteredString(font, "서버 응답을 기다리는 중…", width / 2, height / 2, 0xFFFFFFFF); return; }
        switch (tab) {
            case 0, 1 -> renderList(g);
            case 2 -> renderSettings(g);
            default -> renderSelection(g);
        }
        // 작업 진행률 + 최근 메시지
        if (data.jobFrac() >= 0) {
            int w = width - 16, x = 8, y = height - 6;
            g.fill(x, y, x + w, y + 3, 0xFF202830);
            g.fill(x, y, x + (int) (w * Math.min(1f, data.jobFrac())), y + 3, 0xFF40C8FF);
        }
        if (!flash.isEmpty() && System.currentTimeMillis() - flashAt < 8000) {
            String f = font.plainSubstrByWidth(flash.split("\n")[0], width - 16);
            int fy = tab == 0 ? height - 36 : tab == 1 ? height - 60 : height - 34;
            g.drawString(font, f, 8, fy, 0xFFFFFFFF, true);
        }
    }

    void renderList(GuiGraphics g) {
        boolean fixed = tab == 0;
        int lw = listW();
        if (entries(fixed).isEmpty()) g.drawString(font, fixed ? "§7고정 설계도가 없습니다" : "§7저장한 설계도가 없습니다 — 「영역 선택·저장」 탭에서 만드세요", 8, 34, 0xFFFFFFFF, true);
        Payloads.BpEntry e = find(viewId);
        if (e == null || e.fixed() != fixed) return;
        int x = 8 + lw + 10, w = width - x - 8, y = 30;
        g.drawString(font, "§l" + e.name() + (e.id().equals(data.selected()) ? "  §a(소환 대상)" : ""), x, y, 0xFFFFFFFF, true); y += 12;
        g.drawString(font, "§8" + e.id() + (e.author().isEmpty() ? "" : " · " + e.author()) + (e.version().isEmpty() ? "" : " · v" + e.version()), x, y, 0xFFFFFFFF, false); y += 11;
        for (FormattedCharSequence line : font.split(Component.literal("§7" + e.desc()), w)) { if (y > height / 2) break; g.drawString(font, line, x, y, 0xFFFFFFFF, false); y += 10; }
        y += 2;
        g.drawString(font, String.format("§f크기 §b%d×%d×%d §f블록 §b%d §f기계 §b%d §f재료 §b%d", e.sx(), e.sy(), e.sz(), e.blocks(), e.machines(), e.materials()) + (e.warnings() > 0 ? " §e⚠" + e.warnings() : ""), x, y, 0xFFFFFFFF, true); y += 12;
        // 예상 기능
        for (String line : e.features().split("\n")) {
            if (line.isBlank() || y > height - 120) continue;
            String[] f = line.split("\\|", 3);
            String icon = f[0].equals("working") ? "§a✔" : f[0].equals("partial") ? "§e◐" : "§c✖";
            String txt = icon + " §f" + (f.length > 1 ? f[1] : "") + (f.length > 2 ? " §7" + f[2] : "");
            g.drawString(font, font.plainSubstrByWidth(txt, w), x, y, 0xFFFFFFFF, false); y += 10;
        }
        g.drawString(font, "§8✔ 작동  ◐ 일부/실험적  ✖ 미구현", x, y, 0xFFFFFFFF, false); y += 12;

        // 미리보기 + 재료
        if (detail != null && detail.id().equals(e.id())) {
            int bottom = height - (fixed ? 30 : 54), size = Math.max(32, Math.min(96, Math.min(bottom - y - 12, (w - 12) / 3)));
            if (size >= 32 && y + 12 + size <= bottom) {
                g.drawString(font, "§7위", x, y, 0xFFFFFFFF, false);
                g.drawString(font, "§7앞(남쪽)", x + size + 6, y, 0xFFFFFFFF, false);
                grid(g, detail.top(), x, y + 10, size);
                grid(g, detail.side(), x + size + 6, y + 10, size);
                int mx = x + 2 * size + 14, my = y + 10;
                g.drawString(font, "§7필요한 재료", mx, my - 10, 0xFFFFFFFF, false);
                for (String ml : detail.materials().split("\n")) {
                    if (ml.isBlank() || my > y + 10 + size - 9) continue;
                    String[] kv = ml.split("\t");
                    String nm = kv[0].equals("#other") ? "기타(아이템 없음)" : I18n.get(kv[0]);
                    g.drawString(font, font.plainSubstrByWidth("§f" + (kv.length > 1 ? kv[1] : "?") + " §7" + nm, Math.max(20, w - (mx - x))), mx, my, 0xFFFFFFFF, false);
                    my += 9;
                }
            }
        }
    }

    void grid(GuiGraphics g, int[] d, int x, int y, int size) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF101418);
        if (d == null || d.length < 2) return;
        int w = d[0], h = d[1];
        if (w <= 0 || h <= 0 || d.length < 2 + w * h) return;
        float px = Math.min(size / (float) w, size / (float) h);
        int ox = x + (int) ((size - w * px) / 2), oy = y + (int) ((size - h * px) / 2);
        for (int v = 0; v < h; v++) for (int u = 0; u < w; u++) {
            int c = d[2 + v * w + u];
            if ((c >>> 24) == 0) continue;
            int x0 = ox + (int) (u * px), y0 = oy + (int) (v * px);
            g.fill(x0, y0, Math.max(x0 + 1, ox + (int) ((u + 1) * px)), Math.max(y0 + 1, oy + (int) ((v + 1) * px)), c);
        }
    }

    void renderSettings(GuiGraphics g) {
        Payloads.BpEntry e = find(data.selected());
        g.drawString(font, "§f소환 대상: " + (e == null ? "§c없음" : "§b" + e.name() + " §7(" + e.sx() + "×" + e.sy() + "×" + e.sz() + ")"), 8, 30, 0xFFFFFFFF, true);
        int y = 49;
        g.drawString(font, "§7회전", 8, y, 0xFFFFFFFF, false); y += 24;
        g.drawString(font, "§7반전", 8, y, 0xFFFFFFFF, false); y += 24;
        g.drawString(font, "§7기존 블록", 8, y, 0xFFFFFFFF, false);
        g.drawString(font, "§8" + POLICY_DESC[Math.max(0, Math.min(2, set(2)))], 8 + 70 + 188, y, 0xFFFFFFFF, false); y += 24;
        g.drawString(font, "§7기준점", 8, y, 0xFFFFFFFF, false);
        g.drawString(font, "§8건축물 바닥 중심을 기준점에 맞춤", 8 + 70 + 188, y, 0xFFFFFFFF, false); y += 24;
        String[] ax = {"X", "Y", "Z"};
        for (int a = 0; a < 3; a++) {
            g.drawString(font, "§7" + ax[a] + " 오프셋", 8, y + 5, 0xFFFFFFFF, false);
            g.drawCenteredString(font, "§f" + set(4 + a), 8 + 70 + 91, y + 5, 0xFFFFFFFF);
            y += 22;
        }
        if (!data.job().isEmpty()) g.drawString(font, "§b" + data.job(), 8, height - 20, 0xFFFFFFFF, true);
        else g.drawString(font, "§8우클릭(소환 모드) 또는 「소환」 버튼으로 소환합니다. 소환은 서버가 검사한 뒤 여러 틱에 나눠 진행됩니다.", 8, height - 20, 0xFFFFFFFF, false);
    }

    void renderSelection(GuiGraphics g) {
        boolean sel = set(7) == 1;
        g.drawString(font, "§f선택 영역: §b" + data.selection(), 8, 70, 0xFFFFFFFF, true);
        g.drawString(font, sel ? "§a영역 선택 모드 — 화면을 닫고 소환기로 블록을 우클릭해 시작점 → 끝점을 지정하세요"
                : "§7「모드」를 영역 선택으로 바꾼 뒤 블록을 우클릭해 두 모서리를 지정하세요", 8, 84, 0xFFFFFFFF, false);
        g.drawString(font, "§7저장 항목: 블록·방향·블록 엔티티(허용 데이터)·기계 상태·크기·이름·제작자·버전 · 최대 256×256×256", 8, 154, 0xFFFFFFFF, false);
        g.drawString(font, "§7명령 블록·구조물 블록 등은 저장되지 않습니다. 저장은 월드를 바꾸지 않습니다.", 8, 166, 0xFFFFFFFF, false);
        if (!data.job().isEmpty()) g.drawString(font, "§b" + data.job(), 8, height - 20, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
