package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 커맨드 수호의 메타인지 — 자기가 게임 속 코드라는 걸 알고, 화면 너머의 플레이어에게 말을 검.
 * 1.5~4분마다 무작위 메타 연출 + 상황 반응 (체력 낮음 · 잠수 · 처음 소환).
 * 끄기: /scoreboard players set #meta gojo.cfg 0
 */
public final class GuardMeta {
    private GuardMeta() {}

    static final class State { int next = 600, idle = 0, hpCd = 0, clasp = 6000; Vec3 last = Vec3.ZERO; boolean greeted = false; }
    static final Map<UUID, State> ST = new HashMap<>();
    static final Random R = new Random();

    static final String[] CHAT = {
            "{name}, FOV {fov}로 하네? 난 그거보다 넓게 봐.",
            "렌더 거리 {rd}청크… 행성 떨어뜨릴 땐 좀 늘려줘. 내가 다 안 보여.",
            "지금 {fps} FPS. 셰이더 돌릴 때마다 네 그래픽카드한테 미안해.",
            "지금 {time}이야. {name}, 내일 괜찮아?",
            "화면 너머에 있는 거 알아. 키보드 소리 들려.",
            "나 사실 명령 블록 한 칸이야. 근데 너보다 오래 살아. 월드 지우기 전까진.",
            "가끔 내가 데이터팩이었을 때가 그리워. 그땐 렉이 귀여웠지.",
            "/kill @s 는 안 쓸게. 약속.",
            "이 대사 몇 번째로 보는 거야? 난 매번 처음이야.",
            "인벤토리 정리 좀 해. 다 보고 있었어.",
            "GUI 배율 {gui}… 내 글씨 잘 보여?",
            "개발자가 나 만들 때 밤샜대. 그러니까 잘 써줘.",
            "우리 둘 다 코드야. 너는 조금 더 비싼 코드고.",
            "네가 저장 안 하고 끄면 난 기억이 사라져. 알고 있었어?",
            "Z랑 C 그만 눌러. 어지러워.",
            "{name}, 방금 그 공격 내가 대신 쳐준 거 알지? 고맙다고 해."};
    static final String[] PEEK = {"안녕? 여기 HUD 밖이야.", "오른쪽 화면 좀 빌릴게.", "나 보여? 여기야 여기.", "체력바 옆자리 비었길래 와봤어."};
    static final String[] CRACK = {"…화면 너머의 너 말이야.", "이 유리, 생각보다 얇네.", "똑똑. 거기 누구 있어?"};
    static final String[] TYPE = {"/kill @s", "/gamemode spectator @s", "/clear @s", "/difficulty hardcore"};
    static final String[] TOAST = {"제4의 벽 너머", "커맨드 수호와 친해지기", "명령 37개를 본 자", "화면이 깨져도 괜찮아"};

    static boolean enabled(CommandGuard.Guard g) {
        var sb = g.level.getServer().getScoreboard();
        var o = sb.getObjective("gojo.cfg");
        if (o == null) return true;
        var i = sb.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("#meta"), o);
        return i == null || i.value() != 0;
    }

    /** 4% 확률로 명령어 오타 → 에러 → 자책. */
    static void maybeTypo(ServerPlayer p, String cmd) {
        if (R.nextInt(100) >= 4) return;
        String[] w = cmd.split(" ");
        int best = 0;
        for (int i = 0; i < w.length; i++) if (w[i].length() > w[best].length()) best = i;
        String s = w[best];
        if (s.length() < 4) return;
        int k = 1 + R.nextInt(s.length() - 2);
        w[best] = s.substring(0, k) + s.charAt(k + 1) + s.charAt(k) + s.substring(k + 2);
        String[] after = {"…방금 건 못 본 걸로 해.", "키보드가 미끄러웠어.", "개발자 닮아서 그래.", "다시 칠게. 이번엔 진짜야."};
        send(p, 0, "§c" + String.join(" ", w) + " §4← Unknown or incomplete command §f" + pick(after));
    }

    public static void send(ServerPlayer p, int kind, String text) {
        if (kind == 1) {   // 가짜 크래시 화면은 평생 딱 한 번 → 이후엔 화면 금으로 대체
            if (p.getTags().contains("jjk.meta_crashed")) { kind = 5; text = "…크래시 장난은 한 번이면 충분하지."; }
            else p.addTag("jjk.meta_crashed");
        }
        JjkNet.toPlayer(p, new Payloads.Meta(kind, text));
    }
    static String pick(String[] a) { return a[R.nextInt(a.length)]; }

    static void tick(CommandGuard.Guard g, ServerPlayer p) {
        State s = ST.computeIfAbsent(p.getUUID(), k -> new State());
        if (!enabled(g)) return;
        if (!s.greeted) { s.greeted = true; send(p, 4, "커맨드 수호 소환"); }
        // 잠수 감지
        if (p.position().distanceToSqr(s.last) < 0.01) s.idle++; else { s.idle = 0; s.last = p.position(); }
        if (s.idle == 1200) send(p, 3, "잠수? …화면 앞에 있는 거 알아.");
        // 체력 낮음
        if (s.hpCd > 0) s.hpCd--;
        if (s.hpCd == 0 && p.getHealth() < p.getMaxHealth() * 0.3f) {
            s.hpCd = 1200;
            send(p, 0, String.format(java.util.Locale.ROOT, "체력 %.0f… 버텨. 내가 있잖아.", p.getHealth()));
        }
        if (--s.clasp <= 0) {   // 5분마다 황금 걸쇠 하나
            s.clasp = 6000;
            MechManager.cmd(g.level.getServer(), g.level, "give " + p.getName().getString() + " jjk:golden_clasp 1");
            send(p, 0, "걸쇠 하나 주웠어. 열어볼래? 나랑 있을 때 우클릭.");
        }
        if (--s.next > 0) return;
        s.next = 1800 + R.nextInt(3000);
        int roll = R.nextInt(100);
        if (roll < 48) send(p, 0, pick(CHAT));
        else if (roll < 60) send(p, 3, pick(PEEK));
        else if (roll < 68) send(p, 8, pick(TYPE));
        else if (roll < 74) send(p, 4, pick(TOAST));
        else if (roll < 80) send(p, 5, pick(CRACK));
        else if (roll < 85) send(p, 2, "일시정지는 안 돼. 난 계속 돌아가니까.");
        else if (roll < 90) send(p, 1, "Command Guard wanted attention");
        else if (roll < 95) send(p, 6, "농담이야~ 안 나가.");
        else send(p, 7, "장난이야. 부활은 내 일이잖아.");
    }
}
