package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.Techniques;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 직업 · 능력 해금 시스템.
 *  - 모든 술식(Techniques.ALL)은 해금해야 쓸 수 있다.
 *  - 해금 방법: 그 능력의 아이템을 우클릭 (직업과 상관없이 어느 직업의 능력이든 가능) / 만물의 구 / 자기 직업의 고유 능력은 처음부터.
 *  - 직업은 '직업 선택서'로 고른다 (우클릭 = 다음 직업, 웅크리고 우클릭 = 확정).
 *  - 저장은 플레이어 태그: jjk.unl.<기술id> / jjk.job.<직업>  (사망·재접속해도 유지)
 *  - 만물의 구는 Techniques.ALL 전체를 해금하므로, 능력이 추가될 때마다 자동으로 포함된다.
 */
public final class Abilities {
    private Abilities() {}

    static final String UNL = "jjk.unl.", JOB = "jjk.job.";
    static final String COMMON = "범용";

    /** 데이터팩으로 동작하는 기존 아이템 → 술식 id */
    static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("blue", "blue"), Map.entry("red", "red"), Map.entry("purple", "purple"),
            Map.entry("infinity", "infinity"), Map.entry("unlimited_void", "void"),
            Map.entry("dismantle", "kai"), Map.entry("fuga", "fuga"), Map.entry("malevolent_shrine", "shrine"),
            Map.entry("mahoraga", "maho"), Map.entry("self_embodiment", "jp"), Map.entry("idle_transfiguration", "jp"),
            Map.entry("maximum_meteor", "meteor"), Map.entry("coffin_iron_mountain", "jg"),
            Map.entry("heavenly_restriction", "hr"), Map.entry("prison_realm", "prison"),
            Map.entry("deadly_sentencing", "court"), Map.entry("mokugu", "wood"), Map.entry("kuka", "flowers"),
            Map.entry("taikokai", "tk"), Map.entry("zasatsu", "hk"));

    /** 이 아이템이 가리키는 술식 id (없으면 null) */
    public static String techFor(ItemStack s) {
        if (s.isEmpty()) return null;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!key.getNamespace().equals(JjkMod.MOD_ID)) return null;
        String p = key.getPath(), id = null;
        if (p.startsWith("koming_") && !p.equals("koming_key") && !p.equals("koming_eye")) id = "k_" + p.substring(7);
        else if (p.startsWith("projection_")) id = "p_" + p.substring(11);
        else if (p.startsWith("blood_")) id = "b_" + p.substring(6);
        else if (p.startsWith("jacob_")) id = "j_" + p.substring(6);
        else if (p.startsWith("reverse_")) id = "r_" + p.substring(8);
        else if ((id = Chars.techIdForPath(p)) != null) { }
        else id = LEGACY.get(p);
        return id != null && Techniques.byId(id) != null ? id : null;
    }

    // ═════════════════════════ 직업
    private static List<String> JOBS;
    public static List<String> jobs() {
        if (JOBS == null) {
            List<String> out = new ArrayList<>();
            for (Techniques.Tech t : Techniques.ALL) if (!t.user().equals(COMMON) && !out.contains(t.user())) out.add(t.user());
            JOBS = List.copyOf(out);
        }
        return JOBS;
    }
    static String tagOf(String user) { return JOB + user.replace(' ', '_'); }
    static final String ALL_JOB = "만물", ALL_TAG = JOB + "_all";
    public static String job(Player p) {
        if (p.getTags().contains(ALL_TAG)) return ALL_JOB;   // 만물의 구: 모든 직업
        for (String j : jobs()) if (p.getTags().contains(tagOf(j))) return j;
        return null;
    }
    static void setJob(Player p, String j) {
        for (String x : jobs()) p.removeTag(tagOf(x));
        p.removeTag(ALL_TAG);
        p.addTag(tagOf(j));
    }

    // ═════════════════════════ 해금
    public static boolean unlocked(Player p, Techniques.Tech t) {
        return p.getTags().contains(UNL + t.id());   // 아이템을 처음 우클릭해야 해금 (직업과 무관)
    }
    /** 클라이언트에 보낼 해금 id 목록 (쉼표 구분). */
    public static String unlockedIds(Player p) {
        StringJoiner sj = new StringJoiner(",");
        for (Techniques.Tech t : Techniques.ALL) if (p.getTags().contains(UNL + t.id())) sj.add(t.id());
        return sj.toString();
    }
    static boolean unlock(Player p, Techniques.Tech t) {
        if (p.getTags().contains(UNL + t.id())) return false;
        p.addTag(UNL + t.id());
        return true;
    }
    static int unlockedCount(Player p) {
        int n = 0;
        for (Techniques.Tech t : Techniques.ALL) if (unlocked(p, t)) n++;
        return n;
    }

    /**
     * 술식 아이템 우클릭 게이트. 아직 잠겨 있으면 해금하고(그 클릭은 소모) true,
     * 이미 해금돼 있으면 false (→ 평소처럼 사용).
     */
    public static boolean gate(ServerPlayer p, ItemStack st) {
        String id = techFor(st);
        if (id == null) return false;
        Techniques.Tech t = Techniques.byId(id);
        if (unlocked(p, t)) return false;
        unlock(p, t);
        if (p.level() instanceof ServerLevel l) {
            Vec3 c = p.position().add(0, 1, 0);
            Holo.flare(l, "jjk.unlock", c, 5, t.color(), 8);
            Holo.sfx(l, c, "magic", 1.0f, 1.5f);
        }
        p.displayClientMessage(Component.literal("§a능력 해금 §f「" + t.name() + "」 §7(" + t.user() + ") — 다시 우클릭해서 사용"), false);
        return true;
    }

    /** 만물의 구 · 직업 선택서 우클릭. 처리했으면 true. */
    public static boolean onUse(Player ent, ItemStack st) {
        if (st.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(st.getItem());
        if (!key.getNamespace().equals(JjkMod.MOD_ID)) return false;
        String path = key.getPath();
        if (!path.equals("all_orb") && !path.equals("job_scroll") && !path.equals("jujutsu_guide") && !path.equals("dev_weapon_guide")) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        if (path.equals("jujutsu_guide")) { guide(p); return true; }
        if (path.equals("dev_weapon_guide")) { devGuide(p); return true; }
        if (path.equals("all_orb")) { orb(p); return true; }
        scroll(p);
        return true;
    }

    static void orb(ServerPlayer p) {
        int added = 0;
        for (Techniques.Tech t : Techniques.ALL) if (unlock(p, t)) added++;
        for (String x : jobs()) p.removeTag(tagOf(x));
        p.addTag(ALL_TAG);   // 모든 직업 보유 → 주술창·HUD 활성
        if (p.level() instanceof ServerLevel l) {
            Vec3 c = p.position().add(0, 1, 0);
            Holo.flare(l, "jjk.unlock", c, 12, 0xFFFFFF, 12);
            Holo.shock(l, "jjk.unlock", p.position(), 8, 0xFFE070, 12);
            Holo.sfx(l, c, "stinger", 1.2f, 1.2f);
        }
        p.displayClientMessage(Component.literal(added > 0
                ? "§e만물의 구 §f— 능력 " + added + "개 해금 §7(전체 " + Techniques.ALL.size() + "개)"
                : "§e만물의 구 §7— 이미 모든 능력(" + Techniques.ALL.size() + "개)이 해금돼 있다"), false);
    }

    static final Map<UUID, Integer> PREVIEW = new HashMap<>();
    static void scroll(ServerPlayer p) {
        List<String> jobs = jobs();
        String cur = job(p);
        int idx = PREVIEW.getOrDefault(p.getUUID(), cur == null ? -1 : jobs.indexOf(cur));
        if (p.isShiftKeyDown()) {
            if (idx < 0) { p.displayClientMessage(Component.literal("§7먼저 우클릭으로 직업을 고르세요"), true); return; }
            String j = jobs.get(idx);
            setJob(p, j);
            PREVIEW.remove(p.getUUID());
            if (p.level() instanceof ServerLevel l) { Holo.flare(l, "jjk.unlock", p.position().add(0, 1, 0), 6, 0xFFD84A, 8); Holo.sfx(l, p.position(), "magic", 1.0f, 1.0f); }
            p.displayClientMessage(Component.literal("§6직업 확정 §f" + j + " §7— 직업 능력: " + names(j) + " §8(아이템을 처음 우클릭하면 해금)"), false);
            return;
        }
        idx = (idx + 1) % jobs.size();
        PREVIEW.put(p.getUUID(), idx);
        String j = jobs.get(idx);
        p.displayClientMessage(Component.literal("§e직업 " + (idx + 1) + "/" + jobs.size() + " §f" + j + (j.equals(cur) ? " §a(현재)" : "") + " §7— " + names(j) + " §8| 웅크리고 우클릭: 확정"), true);
    }
    static String names(String job) {
        StringJoiner sj = new StringJoiner(", ");
        for (Techniques.Tech t : Techniques.ALL) if (t.user().equals(job)) sj.add(t.name());
        return sj.toString();
    }

    // ═════════════════════════ 주술 설명서
    static final Map<UUID, Integer> GUIDE_PAGE = new HashMap<>();
    /** 우클릭 = 다음 쪽, 웅크리고 우클릭 = 이전 쪽. 0쪽 = 시스템 설명, 이후 직업별 능력, 마지막 = 범용. */
    static void guide(ServerPlayer p) {
        List<String> pages = new ArrayList<>(jobs());
        pages.add(COMMON);
        int total = pages.size() + 1;
        int cur = GUIDE_PAGE.getOrDefault(p.getUUID(), -1);
        cur = p.isShiftKeyDown() ? (cur <= 0 ? total - 1 : cur - 1) : (cur + 1) % total;
        GUIDE_PAGE.put(p.getUUID(), cur);
        p.sendSystemMessage(Component.literal("§8━━━━━━━━━━━━━━━━━━━━"));
        p.sendSystemMessage(Component.literal("§6§l주술 설명서 §7(" + (cur + 1) + "/" + total + ")  §8우클릭: 다음 · 웅크리고 우클릭: 이전"));
        if (cur == 0) {
            p.sendSystemMessage(Component.literal("§e■ 직업"));
            p.sendSystemMessage(Component.literal("§7 직업 선택서 우클릭 = 다음 직업 · 웅크리고 우클릭 = 확정"));
            p.sendSystemMessage(Component.literal("§7 직업을 고르면 술식 창이 나타난다 (능력은 아이템을 처음 우클릭해야 목록에 뜬다)"));
            p.sendSystemMessage(Component.literal("§e■ 해금"));
            p.sendSystemMessage(Component.literal("§7 능력 아이템을 우클릭하면 해금 — 직업과 다른 능력이어도 된다"));
            p.sendSystemMessage(Component.literal("§7 해금하는 클릭은 사용이 아님 — 한 번 더 우클릭해서 발동"));
            p.sendSystemMessage(Component.literal("§7 V 메뉴(키 시전)도 해금된 능력만 쓸 수 있다"));
            p.sendSystemMessage(Component.literal("§e■ 만물의 구 §7— 우클릭 한 번으로 모든 능력 해금"));
            p.sendSystemMessage(Component.literal("§e■ 반전술식 §7— 직업과 상관없는 범용 능력 (마지막 쪽)"));
            p.sendSystemMessage(Component.literal("§7 현재 직업: §f" + (job(p) == null ? "없음" : job(p)) + " §7· 해금 §f" + unlockedCount(p) + "/" + Techniques.ALL.size()));
            p.sendSystemMessage(Component.literal("§8 ※ 개발자 아이템은 주력을 쓰지 않는다"));
        } else {
            String j = pages.get(cur - 1);
            boolean mine = j.equals(job(p));
            p.sendSystemMessage(Component.literal("§b■ " + j + (mine ? " §a(내 직업)" : "")));
            for (Techniques.Tech t : Techniques.ALL) {
                if (!t.user().equals(j)) continue;
                boolean ok = unlocked(p, t);
                p.sendSystemMessage(Component.literal((ok ? "§a✔ " : "§c✘ ") + "§f" + t.name() + " §7(주력 " + t.cost() + ")"));
                p.sendSystemMessage(Component.literal("§8   " + t.desc()));
            }
        }
        p.sendSystemMessage(Component.literal("§8━━━━━━━━━━━━━━━━━━━━"));
        if (p.level() instanceof ServerLevel l) Holo.sfx(l, p.position(), "data", 0.6f, 1.6f);
    }

    // ═════════════════════════ 개발자 무기 설명서
    static final String[][] DEV_PAGES = {
        {"§e■ 개요", "§7 개발자 아이템은 §f주력을 쓰지 않는다§7 (냉각시간만 있음)", "§7 보스 열쇠는 우클릭으로 보스전 시작, 격파하면 code 드랍",
         "§7 모든 code 9개 → §fall/all§7 · 열쇠 9개 → §f코드의 대열쇠§7 (개발자 재등장)", "§7 이 설명서: 우클릭 다음 쪽 · 웅크리고 우클릭 이전 쪽"},
        {"§b■ 커맨드 계열", "§f커맨드 수호 §7— 개발자 토큰을 모두 합친 것. 우클릭 소환·공격, 웅크리고 우클릭 「/kill」, 하늘 보며 웅크리고 우클릭 해제 · Z/C 명령 선택",
         "§f커맨드 인커 §7— 작은 체인 커맨드가 떠돌며 염력·사슬. 우클릭 소환/발동 · 웅크리고 우클릭 능력 바꾸기 (들기·투척·속박·선풍)",
         "§f커맨드 수호.체인 §7— 인커와 수호를 양손에 들고 우클릭 → 합체. 수호 명령 휠 맨 끝에 인커 능력 4개",
         "§f황금 걸쇠 §7— 수호 소환 중 우클릭, 잠긴 명령 개방 (금 주괴 4 + 금 조각 4 + 철사 덫 갈고리)",
         "§f인피니티 키 §7— 수호 소환 중 우클릭, 숨겨진 필살기 5종 봉인 해제 (커맨데이션 = 오류 셋 + 정화의 물체)"},
        {"§b■ 장비", "§f개발자의 장갑 §7— 들면 기계팔 화면 · 우클릭 톱니 충전 레이저 · 웅크리고 우클릭 톱니 난사 3초",
         "§f개발자의 키캡 Ctrl+Z §7— 우클릭 10초 되감기 · 웅크리고 우클릭 Ctrl+Y(내 공격 재생 ×1.5)",
         "§f개발자의 레버 §7— 우클릭(내림) 반경 48 정지 · 다시 우클릭 COMMIT(로그 ×2·투사체 반사)",
         "§f개발자의 핸드폰 §7— Z/C 앱 넘기기 · 우클릭 실행 · 들면 힘 II·신속",
         "§f개발자의 버튼 §7— 우클릭: 바라보는 곳 지름 100블럭 삭제 (절대 누르지 마시오)"},
        {"§b■ 손 · 탈것", "§f가위·보자기·주먹 §7— 웅크리고 우클릭 거대한 손 소환/해제 · 우클릭 싹둑(×2) / 덮쳐 감싸기 / 로켓 펀치",
         "§f개발자의 귀여운 탑승물 1~3 §7— 개발자 토큰 우클릭으로 소환·탑승",
         "§f기계 토큰 §7— 우클릭: 개발자의 기계 소환·탑승·귀환",
         "§f굴착기 MK-0 §7— 우클릭 소환·탑승 · 드릴 돌진 · 웅크리고 우클릭 내리기",
         "§f레전드 소드 §7— 선택받은 자만 듦. 우클릭 슬래시 · 웅크리고 우클릭 전설의 심판 (선택의 빛 우클릭으로 선택받기)"},
        {"§b■ 보스 열쇠 · 조합", "§f고죠의 열쇠 §7육안 + 다이아 블록 + 네더라이트 주괴 + 황금걸쇠",
         "§f코밍의 열쇠 §7레고 3색 + 지옥의 열쇠 (지옥의 열쇠 = 팬텀 막 + 엔더의 눈 + 블레이즈 가루)",
         "§f스쿠나 열쇠 §7속박 2 + 명명백백 열쇠 (명명백백 = 빨간 콘크리트 + 지옥의 열쇠)",
         "§f박사의 열쇠 §7우민 열쇠 + 기계장치 · §f해커의 열쇠 §7코드 4 + 양자블록 4 + 황금걸쇠",
         "§f돈키호테의 열쇠 §7책 4 + 철 블록 4 + 황금 투구 · §f크랄루의 열쇠 §7황금걸쇠 + 태양석 + 저주",
         "§f차원 신호기 §7우는 흑요석 4 + 엔더의 눈 4 + 신호기 · §f개발의 눈 §7어 형이야~ 4 + 인피니티 키 4 + 코드 1"}};
    static void devGuide(ServerPlayer p) {
        int total = DEV_PAGES.length;
        int cur = DEV_PAGE.getOrDefault(p.getUUID(), -1);
        cur = p.isShiftKeyDown() ? (cur <= 0 ? total - 1 : cur - 1) : (cur + 1) % total;
        DEV_PAGE.put(p.getUUID(), cur);
        p.sendSystemMessage(Component.literal("§8━━━━━━━━━━━━━━━━━━━━"));
        p.sendSystemMessage(Component.literal("§6§l개발자 무기 설명서 §7(" + (cur + 1) + "/" + total + ")"));
        for (String line : DEV_PAGES[cur]) p.sendSystemMessage(Component.literal(line));
        p.sendSystemMessage(Component.literal("§8━━━━━━━━━━━━━━━━━━━━"));
        if (p.level() instanceof ServerLevel l) Holo.sfx(l, p.position(), "data", 0.6f, 1.4f);
    }
    static final Map<UUID, Integer> DEV_PAGE = new HashMap<>();

    public static void stopAll() { PREVIEW.clear(); GUIDE_PAGE.clear(); DEV_PAGE.clear(); }
}
