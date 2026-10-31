package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 개발의 눈 → 보스전 「개발자」 (가상 체력 1600, 3페이지 + 부활 페이지).
 *  몸: 디스플레이 인형 (까치집 머리·뿔테·헤드셋·하늘색 후드티) + 떠 있는 모니터 + 게이밍 의자(1페이지).
 *  PAGE 1 「깍뚝 썰기」 : 의자째 굴러와 개발자 검 휘두르기 · 깍뚝 썰기 · 「형 간다」 · Ctrl+Z 되돌리기
 *  PAGE 2 「패치 노트」 : 일어나서 걸어옴 · 핸드폰 앱(원래 앱) · 레버 정지→커밋 · 심심이 돌진 · 매직 쉴드
 *  PAGE 3 「관리자 권한」: 크리에이티브 비행 · 개발자의 버튼 · /tp 행성 · /kill @a QTE · 「형 불렀냐」
 *  PAGE ∞ 「부활」     : 가짜 퇴장 → /revive → 커맨드 수호와 합동 공격
 *
 * 코드의 대열쇠 → 「개발자 재등장 (롤백)」 어려움: 체력 2600 · 피해 1.5배 · 쿨타임 25% 감소 · 부활 시 체력 전부 회복
 *  + 전용 패턴 「롤백」(3초 전 위치로 강제 되감기 + 폭발) · 「지옥 커밋」(투기장 바닥 용암 격자) · 페이지마다 번개·붉은 기둥 연출
 */
public final class DevBoss {
    private DevBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Dev) return true; return false; }
    public static boolean isEye(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DEV_EYE.get()); }

    public static boolean isMasterKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.CODE_MASTER_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) { summon(p, l, false); }
    public static void summon(ServerPlayer p, ServerLevel l, boolean hard) {
        if (running()) { p.displayClientMessage(Component.literal("§7개발자는 이미 접속해 있다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(9));
        Dev d = new Dev(l, at, hard);
        if (d.body != null) Bosses.ACTIVE.add(d);
    }

    static final class Dev extends Bosses.Boss {
        final float MAX;
        final boolean hard;
        static final int HELL = 0xFF2020;
        static final double S = 1.25;
        static final String WHO = "개발자";
        final java.util.Random R = new java.util.Random();
        final Vec3 home;
        int cd = 40, atk = 0, ctrlzCd = 0, qte = 0, qteT = 0, app = 0; boolean dying = false;
        double ang = 0;
        final List<float[]> hpLog = new ArrayList<>();
        final List<Mob> esc = new ArrayList<>();

        Dev(ServerLevel l, Vec3 at, boolean hard) {
            super(l, hard ? "§4§l개발자 §7— 롤백 중…" : "§b§l개발자 §7— PAGE 0 · 로딩 중…", hard ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
            this.hard = hard;
            MAX = hard ? 2600 : 1600;
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home.add(0, 30, 0), S, MAX, "§b개발자");
            if (body == null) return;
            rig = new BossRig(this, "dev", S);
            rig.sit(true);
            faceTo(nearest());
            buildMonitor();
            if (hard) hardScene(); else startScene();
        }

        static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }
        void say(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§b<개발자> §f" + s)); }
        @Override void hurtP(ServerPlayer p, float dmg) { super.hurtP(p, hard ? dmg * 1.5f : dmg); }
        String tagName() { return hard ? "§4§l개발자 §7(롤백)" : "§b§l개발자"; }
        @Override float floor() { return MAX * (phase <= 1 ? 0.66f : phase == 2 ? 0.33f : 0f); }

        /** 떠 있는 모니터 (코드가 흐름) — 몸 앞쪽 위. */
        void buildMonitor() {
            final Dev b = this;
            Holo.text(l, tag, body.position(), "{text:\"\"}", "fixed", 0xF0101418, false, Holo.m(Holo.I(), 1.1, 1.1, 1.1, new Vec3(0, 2.75 * S, 1.3), false), 99999999, 2)
                    .attach(() -> b.body.position(), () -> rig.yaw).text(age -> monitor(age));
            Holo.text(l, tag, body.position(), "{text:\"\"}", "center", 0, false, Holo.m(Holo.I(), 1.0, 1.0, 1.0, Vec3.ZERO, false), 99999999, 2)
                    .follow(() -> b.body.position().add(0, 2.55 * S + rig.lift * S, 0), Vec3.ZERO)
                    .text(age -> "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("[개발자] ", 0x55FFFF, true) + "," + Holo.tx(phase == 4 ? "PAGE ∞" : "PAGE " + phase, 0xFFFFFF, false) + "]}");
        }
        String monitor(int age) {
            String[][] code = {{"while (true) {", "  game.play();", "  // TODO: 일하기", "}"},
                    {"if (boss.hp < 66%) {", "  patch(\"nerf player\");", "  reload();", "}"},
                    {"sudo rm -rf /player", "chmod 777 world", "/gamemode creative", "ERROR ERROR ERROR"},
                    {"/revive @s", "/summon command_guard", "/execute as guard run", "  kill @a  // 진심"}};
            String[] c = hard && phase <= 1 ? new String[]{"git reset --hard HEAD~999", "// 코드는 롤백됐다", "hell.open();", "player.escape = false;"} : code[Math.max(0, Math.min(3, phase - 1))];
            int k = (age / 6) % (c.length + 1);
            StringBuilder sb = new StringBuilder("{text:\"\",font:\"minecraft:uniform\",extra:[");
            int col = phase == 4 ? 0xD070FF : phase == 3 ? 0xFF4040 : phase == 2 ? 0xFFD84A : 0x55FF55;
            for (int i = 0; i < c.length; i++) {
                if (i > 0) sb.append(",{text:\"\\n\"},");
                sb.append(Holo.tx(i < k ? c[i] : i == k ? c[i].substring(0, Math.min(c[i].length(), (age % 6) * 4)) + "▌" : "", col, false));
            }
            return sb.append("]}").toString();
        }

        // ═════════ 컷신
        void startScene() {
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x05060A, 9000, 0.7f));
            Holo.pillar(l, tag, home, 120, 3, 0x55FFFF, 10, 40, 10);
            sfx("deep", 3f, 0.6f);
            sfx("riser", 3f, 0.5f);
            camFocus = home;   // 카메라는 내려앉을 자리를 비춤
            later(190, () -> camFocus = null);
            for (int i = 1; i <= 30; i++) { final int k = i; later(i, () -> moveTo(home.add(0, 30 - k, 0))); }   // 의자째 내려옴
            later(31, () -> { Holo.shock(l, tag, home, 14, 0x55FFFF, 12); sfx("boom", 1.5f, 1.2f); });
            later(40, () -> rig.play("type", 60));
            later(110, () -> rig.play("laugh", 50));
            scene(190, WHO, new String[]{"만들기 귀찮다.", "그냥 게임하고 싶운되.", "으으으으음....", "그냥 플레이어 깍뚝 썰기하고 놀아야지~"}, 42, 5.5f, 1.6f);
            later(186, () -> {
                phase = 1;
                bar.setName(Component.literal("§b§l개발자 §7— PAGE 1 · 깍뚝 썰기"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("개발자", "PAGE 1 — 깍뚝 썰기", 0x55FFFF, 1));
                sfx("stinger", 3f, 0.8f);
                music("minecraft:music_disc.creator");
                rig.hold("jjk:dev_sword");
                cd = 30;
            });
        }
        /** 어려움 등장: 세계가 되감기는 연출 → 의자 없이 서서 등장. */
        void hardScene() {
            for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(5, 0x200000, 11000, 0.75f)); JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 2500, 2f)); }
            rig.sit(false);
            camFocus = home;
            later(200, () -> camFocus = null);
            sfx("glitch", 3f, 0.4f);
            sfx("deep", 3f, 0.4f);
            String[] vers = {"v0.63.0", "v0.50.0", "v0.40.0", "v0.30.0", "v0.20.0", "v0.10.0", "v0.0.1"};
            for (int i = 0; i < vers.length; i++) {   // 버전이 거꾸로 감기는 배너
                final int k = i;
                later(4 + i * 7, () -> {
                    Holo.banner(l, tag, home.add(0, 6 + k * 0.4, 0), banner("◀◀ ROLLBACK  " + vers[k], 0xFF4040), 2.2, 10);
                    BossSfx.play(l, home, "data", 2f, 1.6f - k * 0.12f);
                });
            }
            for (int i = 0; i < 12; i++) {   // 붉은 기둥 원
                double a = i * Math.PI / 6;
                Vec3 q = home.add(Math.cos(a) * 12, 0, Math.sin(a) * 12);
                Holo.pillar(l, tag, new Vec3(q.x, groundY(q.x, home.y + 3, q.z), q.z), 60, 0.8, HELL, 20 + i * 3, 120, 20);
            }
            for (int i = 1; i <= 30; i++) { final int k = i; later(50 + i, () -> moveTo(home.add(0, 30 - k, 0))); }   // 하늘에서 내려옴
            later(81, () -> {
                Holo.shock(l, tag, home, 22, HELL, 14);
                Holo.explosion(l, tag, home.add(0, 1, 0), 6, HELL);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; Holo.lightning(l, tag, home.add(Math.cos(a) * 9, 40, Math.sin(a) * 9), home.add(Math.cos(a) * 9, 0, Math.sin(a) * 9), HELL, 0.5, 12); }
                sfx("boom", 3f, 0.5f);
                sfx("thunder", 3f, 0.8f);
                Titan.shake(l, home, 60, true);
                rig.play("raise", 50);
            });
            later(140, () -> { rig.play("point", 50); rig.hold("jjk:dev_sword"); });
            scene(200, WHO, new String[]{"……", "코드는 롤백 됬어.", "이 앞은 지옥이고...", "널 여기서 끝낼거야."}, 46, 6f, 1.8f);
            later(196, () -> {
                phase = 1;
                bar.setName(Component.literal("§4§l개발자 §7(롤백) — PAGE 1 · 지옥의 입구"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("개발자 (롤백)", "PAGE 1 — 지옥의 입구", HELL, 1));
                sfx("stinger", 3f, 0.6f);
                music("minecraft:music_disc.pigstep");
                cd = 24;
            });
        }
        /** 어려움 페이지 전환 연출: 번개 + 붉은 기둥. */
        void hellFx() {
            if (!hard) return;
            Vec3 c = body.position();
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                Vec3 q = c.add(Math.cos(a) * 8, 0, Math.sin(a) * 8);
                Holo.lightning(l, tag, q.add(0, 35, 0), q, HELL, 0.45, 10);
                Holo.pillar(l, tag, q, 50, 0.7, HELL, 4, 30, 10);
            }
            Holo.ring(l, tag, c.add(0, 0.2, 0), 2, 20, 60, 0.8, 0.05, HELL, 10, 20, 10, Math.PI / 2, 4, true);
            sfx("thunder", 3f, 0.7f);
        }
        void pageScene(int to) {
            String[] lines = hard ? (to == 2 ? new String[]{"…아직 멀었어.", "지옥은 두 번째 층부터가 진짜야.", "패치: 플레이어 너프."}
                    : new String[]{"여기까지 온 건 처음이네.", "그래도 못 나가.", "/gamemode creative  …관리자 권한, 전부."})
                    : to == 2 ? new String[]{"어? 아프네. 버그인가?", "…패치 들어갑니다~", "의자에서 일어날게."}
                    : new String[]{"이거 밸런스 망했네…", "개발자 권한 사용합니다.", "/gamemode creative"};
            hellFx();
            Holo.shock(l, tag, body.position(), 30, to == 2 ? 0xFFD84A : 0xFF4040, 14);
            sfx("glitch", 3f, 0.6f);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 1500, 1.4f));
            rig.play("laugh", 60);
            scene(130, WHO, lines, 38, 5.5f, 1.8f);
            if (to == 2) later(80, () -> { rig.sit(false); rig.play("raise", 40); sfx("whoosh", 1.5f, 1f); });
            if (to == 3) later(80, () -> { rig.floating = true; rig.play("raise", 40); sfx("riser", 2f, 1.2f); moveTo(body.position().add(0, 1, 0)); });
            later(126, () -> {
                phase = to;
                bar.setName(Component.literal(tagName() + (to == 2 ? " §7— PAGE 2 · 패치 노트" : " §7— PAGE 3 · 관리자 권한")));
                bar.setColor(to == 2 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("개발자", to == 2 ? "PAGE 2 — 패치 노트" : "PAGE 3 — 관리자 권한", to == 2 ? 0xFFD84A : 0xFF4040, 1));
                sfx("stinger", 3f, 0.7f);
                cd = 24;
            });
        }
        // ═════════ 부활 페이지: 쓰러진 척 → /revive → 커맨드 수호 소환
        void gSay(String cmd) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§d<커맨드 수호> §f" + cmd)); }
        Vec3 gPos() { double a = t * 0.06; return body.position().add(Math.cos(a) * 3.5, 4.5 + Math.sin(t * 0.08), Math.sin(a) * 3.5); }
        void reviveScene() {
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFFFFF, 2500, 0.5f));
            sfx("choir", 3f, 0.6f);
            stopMusic();
            rig.floating = false;
            moveTo(new Vec3(body.getX(), groundY(body.getX(), body.getY(), body.getZ()), body.getZ()));
            rig.play("fall", 100);
            for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§e개발자가 게임을 떠났습니다"));
            scene(240, WHO, hard ? new String[]{"……", "롤백했다고 했지.", "죽음도 롤백돼.", "/revive @s", "커맨드 수호. 지옥 문 닫아.", "아무도 못 나가게."}
                    : new String[]{"아… 졌다.", "……", "…라고 할 줄 알았지?", "/revive @s", "커맨드 수호. 나와.", "이번엔 진짜 깍뚝 썰어줄게~"}, 38, 6f, 2f);
            if (hard) later(100, this::hellFx);
            later(80, () -> {   // 가짜 퇴장 직후 화면 지지직 → 부활
                for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 2500, 2f)); p.sendSystemMessage(Component.literal("§e개발자가 게임에 참여했습니다")); }
                sfx("glitch", 3f, 0.5f);
            });
            later(110, () -> rig.play("raise", 80));
            later(120, () -> {
                Holo.pillar(l, tag, body.position(), 140, 4, 0xD070FF, 6, 60, 12);
                Holo.shock(l, tag, body.position(), 30, 0xD070FF, 16);
                for (int i = 0; i < 40; i++) { final int k = i; later(i, () -> { healV(MAX * (hard ? 1.0f : 0.6f) / 40f); if (k % 8 == 0) Holo.flare(l, tag, body.position().add(0, S, 0), 3 + k * 0.1, 0xD070FF, 6); }); }
                sfx("riser", 3f, 0.6f);
            });
            later(160, () -> {   // 커맨드 수호 강림
                Vec3 g = gPos();
                Holo.pillar(l, tag, g, 100, 2.5, 0xB45CFF, 2, 10, 10);
                Holo.block(l, tag, g, "minecraft:command_block", 0xB45CFF, Holo.m(Holo.I(), 3, 3, 3, Vec3.ZERO, true), 999999, 1)
                        .follow(this::gPos, Vec3.ZERO).spin(6);
                for (int i = 0; i < 3; i++) {
                    final int ii = i;
                    Holo.block(l, tag, g, i == 0 ? "minecraft:repeating_command_block" : "minecraft:chain_command_block", 0x55FFFF, Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), 999999, 1)
                            .follow(() -> { double a = ii * Math.PI * 2 / 3 + t * 0.12; return gPos().add(Math.cos(a) * 3, Math.sin(t * 0.1 + ii), Math.sin(a) * 3); }, Vec3.ZERO).spin(15);
                }
                Holo.text(l, tag, g, "{text:\"\"}", "center", 0, false, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, false), 999999, 1)
                        .follow(() -> gPos().add(0, 2.8, 0), Vec3.ZERO).text(age -> "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("◉ 커맨드 수호 ", 0xD070FF, true) + "," + Holo.tx("(개발자 권한)", 0xAAAAAA, false) + "]}");
                gSay("/execute as @s[name=개발자] run summon command_guard");
                sfx("stinger", 3f, 0.6f);
                Titan.shake(l, g, 60, true);
            });
            later(236, () -> {
                phase = 4;
                rig.floating = true;
                bar.setName(Component.literal(tagName() + " §7— PAGE ∞ · 부활 / 커맨드 수호"));
                bar.setColor(BossEvent.BossBarColor.PURPLE);
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("개발자 + 커맨드 수호", "PAGE ∞ — 부활", 0xD070FF, 1));
                music("minecraft:music_disc.creator_music_box");
                ctrlzCd = 0; cd = 24;
            });
        }
        void deathScene() {
            dying = true;
            stopMusic();
            rig.floating = false;
            moveTo(new Vec3(body.getX(), groundY(body.getX(), body.getY(), body.getZ()), body.getZ()));
            rig.play("kneel", 9999);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFFFFF, 6000, 0.4f));
            sfx("choir", 3f, 0.6f);
            gSay("/kill @s   …수호 종료.");
            scene(170, WHO, hard ? new String[]{"…롤백도, 부활도 안 되네.", "그래. 지옥에서 나가도 좋아.", "…다음 버전에서 기다릴게."}
                    : new String[]{"…진짜 졌네. 수호까지 불렀는데.", "…재밌네. 이 게임.", "다음 업데이트 때 보자~"}, 46, 5.5f, 1.4f);
            later(130, () -> rig.play("fall", 9999));
            later(150, () -> {
                for (ServerPlayer p : l.players()) p.sendSystemMessage(Component.literal("§e개발자가 게임을 떠났습니다"));
                Holo.flare(l, tag, body.position().add(0, 0.6, 0), 8, 0xFFFFFF, 12);
                Holo.shards(l, tag, body.position().add(0, 0.6, 0), "minecraft:light_blue_concrete", 40, 8);
                sfx("shatter", 2.5f, 1f);
            });
            later(168, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            hpLog.add(new float[]{t, hp});
            hpLog.removeIf(h -> h[0] < t - 100);
            if (phase == 1 && atFloor()) { pageScene(2); return; }
            if (phase == 2 && atFloor()) { pageScene(3); return; }
            if (phase == 3 && atFloor()) { reviveScene(); return; }
            if (phase == 4 && atFloor()) { deathScene(); return; }
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            ServerPlayer fp = BossPuppet.BY_NAME.get("개발자");
            if (fp != null) BossPuppet.aim(fp, body, tgt);
            if (qte > 0) { faceTo(tgt); tickQte(); return; }
            // 이동: 1페이지 의자째 굴러옴, 2페이지 걸어옴, 3·∞페이지 낮게 날며 곁을 맴돎 (전부 근접 사거리 안)
            if (!rig.busy()) {
                if (phase <= 2) chase(tgt, phase == 1 ? 0.15 : 0.2, 2.4);
                else {
                    ang += 0.035;
                    Vec3 want = tgt.position().add(Math.cos(ang) * 2.8, 0, Math.sin(ang) * 2.8);
                    want = new Vec3(want.x, groundY(want.x, tgt.getY() + 1, want.z) + 0.8, want.z);
                    glide(want, 0.14);
                    faceTo(tgt);
                }
            } else faceTo(tgt);
            // Ctrl+Z: 5초 안에 12% 넘게 깎이면 되돌리기 (페이지마다 한 번)
            if (ctrlzCd != phase && hpLog.size() > 1 && hpLog.get(0)[1] - hp > MAX * 0.12f) {
                ctrlzCd = phase;
                float back = Math.min(MAX * 0.08f, hpLog.get(0)[1] - hp);
                healV(back);
                rig.hold("jjk:ctrl_z");
                rig.play("raise", 24);
                Holo.banner(l, tag, body.position().add(0, 3.2 * S, 0), banner("◀◀ Ctrl+Z  +" + (int) back, 0x55DDFF), 2, 40);
                for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(5, 0xC8A070, 1500, 0.4f)); JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 1500, 1.4f)); }
                sfx("glitch", 2f, 0.7f);
                say("실행 취소~ 방금 그 피해는 없던 걸로.");
                return;
            }
            if (--cd > 0) return;
            double dist = tgt.position().distanceTo(body.position());
            int k;
            if (phase == 1) { k = dist < 3.5 ? new int[]{14, 0, 14, 1}[atk++ % 4] : new int[]{0, 1}[atk++ % 2]; cd = 44; }
            else if (phase == 2) { k = dist < 3.5 ? new int[]{14, 2, 14, 3, 14, 5}[atk++ % 6] : new int[]{2, 0, 3, 4, 1, 5}[atk++ % 6]; cd = 40; }
            else if (phase == 3) { k = dist < 3.5 ? new int[]{14, 6, 14, 1, 14, 8}[atk++ % 6] : new int[]{6, 0, 7, 1, 8, 2}[atk++ % 6]; cd = 36; }
            else { k = dist < 3.5 ? new int[]{14, 9, 14, 10, 14, 12}[atk++ % 6] : new int[]{9, 11, 10, 0, 12, 9, 8, 11, 13}[atk++ % 9]; cd = 34; }
            if (hard) {
                cd = cd * 3 / 4;
                if (phase >= 2 && atk % 5 == 0) k = 15;
                else if (phase >= 3 && atk % 7 == 3) k = 16;
            }
            switch (k) {
                case 0 -> { rig.play("point", 24); dice(tgt); }
                case 1 -> hyung(tgt);
                case 2 -> { rig.play("phone", 30); phoneApps(tgt); }
                case 3 -> { rig.play("raise", 30); lever(); }
                case 4 -> { rig.play("point", 26); worm(tgt); }
                case 5 -> shield();
                case 6 -> { rig.play("slam", 20); button(tgt); }
                case 7 -> { rig.play("raise", 40); planet(); }
                case 8 -> killQte(tgt);
                case 9 -> { rig.play("point", 20); gLaser(tgt); }
                case 10 -> { rig.play("cast", 24); gCage(tgt); }
                case 11 -> { rig.play("raise", 24); gSay("/summon command_block_rain ~ ~20 ~ {count:18}"); rain(tgt.position(), 18, 8, "minecraft:command_block", 0xB45CFF, 10f); }
                case 12 -> { rig.play("slam", 24); gCube(tgt); }
                case 13 -> { rig.play("raise", 50); gPlanet(tgt); }
                case 14 -> slash(tgt);
                case 15 -> rollback();
                case 16 -> hellCommit();
                default -> { }
            }
        }
        /** 롤백 (어려움): 지금 위치 저장 → 3초 뒤 그 자리로 강제 되감기, 있던 자리는 폭발. */
        void rollback() {
            rig.play("raise", 30);
            say("롤백. 3초 전으로.");
            Holo.banner(l, tag, body.position().add(0, 3.4 * S, 0), banner("◀◀ ROLLBACK", HELL), 2.2, 40);
            List<Object[]> snap = new ArrayList<>();
            for (ServerPlayer p : players(40)) {
                Vec3 q = p.position();
                snap.add(new Object[]{p, q});
                Holo.circle(l, tag, q.add(0, 0.1, 0), 1.2, 0xFFFFFF, 60, 0);
                Holo.block(l, tag, q, "minecraft:white_stained_glass", 0xFFFFFF, Holo.m(Holo.I(), 0.6, 1.8, 0.6, new Vec3(0, 0.9, 0), true), 60, 0);   // 잔상
            }
            sfx("data", 2f, 0.6f);
            later(60, () -> {
                for (Object[] o : snap) {
                    ServerPlayer p = (ServerPlayer) o[0]; Vec3 q = (Vec3) o[1];
                    if (!p.isAlive()) continue;
                    Vec3 was = p.position();
                    Holo.explosion(l, tag, was.add(0, 1, 0), 3, HELL);
                    playersInRange(was, 3, 12f);
                    p.teleportTo(q.x, q.y, q.z);
                    Holo.flare(l, tag, q.add(0, 1, 0), 2, 0xFFFFFF, 6);
                    JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 800, 1.6f));
                }
                BossSfx.play(l, body.position(), "glitch", 2.5f, 0.5f);
            });
        }
        /** 지옥 커밋 (어려움): 투기장 바닥에 격자로 용암이 차오름 (안전한 칸으로). */
        void hellCommit() {
            rig.play("slam", 26);
            say("git commit -m \"지옥\"");
            Vec3 c = home;
            double cell = 4;
            for (int i = -4; i <= 4; i++) for (int j = -4; j <= 4; j++) {
                if ((i + j) % 2 == 0) continue;   // 체크무늬: 절반만 용암
                Vec3 q = c.add(i * cell, 0, j * cell);
                q = new Vec3(q.x, groundY(q.x, c.y + 3, q.z), q.z);
                Holo.block(l, tag, q, "minecraft:red_stained_glass", HELL, Holo.m(Holo.I(), cell * 0.95, 0.06, cell * 0.95, new Vec3(0, 0.08, 0), true), 30, 0);
                final Vec3 qq = q;
                later(30, () -> {
                    Holo.block(l, tag, qq, "minecraft:magma_block", HELL, Holo.m(Holo.I(), cell * 0.95, 0.3, cell * 0.95, new Vec3(0, 0.15, 0), true), 40, 0);
                    l.sendParticles(ParticleTypes.LAVA, qq.x, qq.y + 0.4, qq.z, 4, 1.2, 0.1, 1.2, 0);
                });
                for (int w = 0; w < 4; w++) later(32 + w * 10, () -> {
                    for (ServerPlayer p : players(80)) { Vec3 dd = p.position().subtract(qq); if (Math.abs(dd.x) < cell / 2 && Math.abs(dd.z) < cell / 2 && Math.abs(dd.y) < 2) { hurtP(p, 6f); p.igniteForSeconds(3); } }
                });
            }
            BossSfx.play(l, c, "deep", 2.5f, 0.6f);
        }
        /** 근접: 개발자 검 2연타. */
        void slash(ServerPlayer tgt) {
            rig.hold("jjk:dev_sword");
            melee(tgt, phase >= 3 ? 9f : 7f, "swing", 8, 3.6);
            later(13, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, phase >= 3 ? 9f : 7f, "sweep", 6, 3.6); } });
        }

        /** 수호: /execute … laser ^ ^ ^40 */
        void gLaser(ServerPlayer tgt) {
            Vec3 a = gPos(), aim = tgt.position().add(0, 1, 0);
            Vec3 b = a.add(aim.subtract(a).normalize().scale(45));
            gSay("/execute anchored eyes run laser ^ ^ ^45");
            Holo.beamGlow(l, tag, a, b, 0.08, 0xB45CFF, 1, 18, 1);
            BossSfx.play(l, a, "charge", 2f, 1.4f);
            later(20, () -> {
                Holo.beamGlow(l, tag, a, b, 1.5, 0xB45CFF, 1, 4, 5);
                Holo.beamGlow(l, tag, a, b, 0.5, 0xFFFFFF, 1, 4, 5);
                BossSfx.play(l, a, "laser", 2.5f, 0.9f);
                for (ServerPlayer p : players(80)) {
                    Vec3 q = p.position().add(0, 1, 0), ab = b.subtract(a);
                    double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
                    if (q.distanceTo(a.add(ab.scale(s))) < 1.8) hurtP(p, 22f);
                }
            });
        }
        /** 수호: /fill 유리 감옥 → 폭발. */
        void gCage(ServerPlayer tgt) {
            Vec3 c = tgt.position();
            gSay("/fill ~-2 ~ ~-2 ~2 ~4 ~2 purple_stained_glass hollow");
            Holo.block(l, tag, c.add(0, 2, 0), "minecraft:purple_stained_glass", 0xB45CFF, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), 52, 0)
                    .key(1, Holo.m(Holo.I(), 5, 5, 5, Vec3.ZERO, true), 5).key(44, Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), 8);
            tgt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 4));
            tgt.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 50, 128, false, false));
            later(28, () -> { say("깍뚝~"); dice(tgt); });   // 갇힌 채로 깍뚝 썰기 콤보
            later(50, () -> { Holo.explosion(l, tag, c.add(0, 1, 0), 4, 0xB45CFF); playersInRange(c, 3.2, 14f); Holo.shards(l, tag, c.add(0, 1, 0), "minecraft:purple_stained_glass", 24, 5); });
        }
        /** 수호: 거대 커맨드 블록 압살. */
        void gCube(ServerPlayer tgt) {
            Vec3 c = tgt.position();
            gSay("/summon falling_block ~ ~40 ~ {BlockState:{Name:command_block},Size:9}");
            Holo.circle(l, tag, c.add(0, 0.1, 0), 6, 0xB45CFF, 40, 0);
            Holo.block(l, tag, c, "minecraft:command_block", 0xB45CFF, Holo.m(Holo.I(), 9, 9, 9, new Vec3(0, 45, 0), true), 60, 0)
                    .key(10, Holo.m(Holo.I(), 9, 9, 9, new Vec3(0, 0, 0), true), 26).key(50, Holo.m(Holo.I(), 0.1, 0.1, 0.1, new Vec3(0, 0, 0), true), 8);
            later(36, () -> {
                Holo.shock(l, tag, c, 20, 0xB45CFF, 12);
                Holo.shards(l, tag, c.add(0, 2, 0), "minecraft:command_block", 30, 7);
                playersInRange(c, 6, 30f);
                BossSfx.play(l, c, "boom", 3f, 0.6f);
                Titan.shake(l, c, 40, true);
            });
        }
        /** 수호 + 개발자 합동: /tp 행성 (대형, 3개 연속). */
        void gPlanet(ServerPlayer tgt) {
            say("커맨드 수호, 그거 하자.");
            gSay("/tp @e[type=planet,limit=3,sort=random] @p");
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                later(i * 25, () -> {
                    ServerPlayer tt = nearest();
                    Vec3 c = tt == null ? home : tt.position();
                    String blk = new String[]{"minecraft:magma_block", "minecraft:prismarine", "minecraft:purpur_block"}[ii];
                    int rgb = new int[]{0xFF6020, 0x40E0D0, 0xD070FF}[ii];
                    Holo.circle(l, tag, c.add(0, 0.1, 0), 11, rgb, 40, 0);
                    Holo.block(l, tag, c, blk, rgb, Holo.m(Holo.I(), 10, 10, 10, new Vec3(0, 80, 0), true), 40, 0)
                            .key(4, Holo.m(Holo.I(), 10, 10, 10, new Vec3(0, 3, 0), true), 32).spin(8);
                    later(36, () -> {
                        Holo.explosion(l, tag, c, 11, rgb);
                        Holo.shock(l, tag, c, 24, rgb, 14);
                        playersInRange(c, 11, 26f);
                        BossSfx.play(l, c, "boom", 3f, 0.4f + ii * 0.15f);
                        Titan.shake(l, c, 60, true);
                    });
                });
            }
        }

        /** 깍뚝 썰기: 플레이어 발밑 5×5 격자 중 9칸이 빨갛게 → 썰림. */
        void dice(ServerPlayer tgt) {
            rig.hold("jjk:dev_sword");
            say(phase >= 3 ? "깍뚝깍뚝깍뚝~" : "깍뚝 썰기 시간~");
            Vec3 c = tgt.position();
            double cell = 3;
            List<int[]> cells = new ArrayList<>();
            for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) cells.add(new int[]{i, j});
            java.util.Collections.shuffle(cells);
            int n = phase >= 3 ? 14 : 9, warn = phase >= 3 ? 14 : 22;
            for (int i = -2; i <= 3; i++) {   // 격자선
                Holo.beamGlow(l, tag, c.add((i - 0.5) * cell, 0.1, -2.5 * cell), c.add((i - 0.5) * cell, 0.1, 2.5 * cell), 0.05, 0xFFFFFF, 1, warn, 4);
                Holo.beamGlow(l, tag, c.add(-2.5 * cell, 0.1, (i - 0.5) * cell), c.add(2.5 * cell, 0.1, (i - 0.5) * cell), 0.05, 0xFFFFFF, 1, warn, 4);
            }
            for (int q = 0; q < n; q++) {
                int[] ce = cells.get(q);
                Vec3 cc = c.add(ce[0] * cell, 0, ce[1] * cell);
                Holo.block(l, tag, cc, "minecraft:red_stained_glass", 0xFF3040, Holo.m(Holo.I(), cell * 0.95, 0.06, cell * 0.95, new Vec3(0, 0.08, 0), true), warn + 2, 0);
                final Vec3 at = cc;
                later(warn + q % 3, () -> {
                    Holo.beamGlow(l, tag, at.add(-1.6, 1, -1.6), at.add(1.6, 1, 1.6), 0.18, 0xFFFFFF, 1, 1, 3);
                    Holo.beamGlow(l, tag, at.add(-1.6, 1, 1.6), at.add(1.6, 1, -1.6), 0.18, 0xFFFFFF, 1, 1, 3);
                    for (ServerPlayer p : players(80)) { Vec3 dd = p.position().subtract(at); if (Math.abs(dd.x) < cell / 2 && Math.abs(dd.z) < cell / 2 && Math.abs(dd.y) < 3) hurtP(p, 12f); }
                });
            }
            later(warn + 2, () -> {
                Holo.banner(l, tag, c.add(0, 4, 0), banner("깍뚝!", 0xFFFFFF), 2.5, 20);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.7f);
            });
        }
        // ── 원래 개발자 아이템: 꼭두각시(보스 몸과 겹침)를 시전자로 원래 코드 호출
        ServerPlayer puppet(ServerPlayer tgt) {
            ServerPlayer fp = BossPuppet.get(l, "개발자");
            BossPuppet.aim(fp, body, tgt);
            return fp;
        }
        /** 어 형이야~: 원래 「형 간다」 돌진 (3페이지부터 가끔 「형 불렀냐」 거대 머리 내리찍기). */
        void hyung(ServerPlayer tgt) {
            rig.hold("jjk:hyung_sword");
            boolean call = phase >= 3 && atk % 2 == 0;
            Holo.beamGlow(l, tag, body.position().add(0, 1.5, 0), tgt.position().add(0, 1, 0), 0.08, 0xFFD84A, 1, 14, 1);   // 예고선
            say(call ? "형 불러올게~" : "어 형이야~");
            later(15, () -> {
                ServerPlayer tt = tgt.isAlive() ? tgt : nearest();
                if (tt == null) return;
                try {
                    ServerPlayer fp = puppet(tt);
                    if (call) HyungSword.called(fp, l);
                    else {
                        Vec3 from = fp.position(), flat = fp.getLookAngle().multiply(1, 0, 1).normalize();
                        HyungSword.dash(fp, l);
                        Vec3 to = fp.position();
                        if (to.distanceTo(from) < 0.5) to = from.add(flat.scale(Math.min(16, tt.position().distanceTo(from) + 3)));
                        fp.setPos(to.x, to.y, to.z);
                        moveTo(new Vec3(to.x, groundY(to.x, to.y + 1, to.z) + (phase >= 3 ? 1.0 : 0), to.z));   // 꼭두각시가 돌진한 곳으로 몸도 이동
                    }
                } catch (Throwable e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 개발자 보스 형 실패", e); }
            });
        }
        /** 핸드폰: 원래 앱을 돌아가며 실행 (Ω 천벌 · 레볼루션 · 전화 지원 사격 · 네임드 · 더 엔드). */
        void phoneApps(ServerPlayer tgt) {
            rig.hold("jjk:dev_phone");
            int[] apps = phase >= 3 ? new int[]{2, 5, 11, 3, 6} : new int[]{2, 5, 11, 6};
            int a = apps[app++ % apps.length];
            say("앱 하나 켜볼까~ " + DevPhone.ICON[a] + " " + DevPhone.APPS[a]);
            Holo.banner(l, tag, body.position().add(0, 3.2 * S, 0), banner(DevPhone.ICON[a] + " " + DevPhone.APPS[a], DevPhone.COLOR[a]), 1.6, 30);
            try { DevPhone.run(puppet(tgt), l, a, 1.0); }
            catch (Throwable e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 개발자 보스 앱 실패", e); }
        }
        /** 개발자의 레버: 정지 → 커밋. */
        void lever() {
            rig.hold("jjk:dev_lever");
            say("디버그 모드 켭니다~ 움직이지 마세요~");
            for (ServerPlayer p : players(40)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 128, false, false));
                Holo.hitbox(l, tag, p, 0x55FF55, 60);
                JjkNet.toPlayer(p, new Payloads.Screen(5, 0x55FF55, 3000, 0.2f));
            }
            Holo.circle(l, tag, home.add(0, 0.1, 0), 30, 0x55FF55, 64, 0);
            later(60, () -> {
                Holo.banner(l, tag, body.position().add(0, 3.2 * S, 0), banner("COMMIT", 0xFF3040), 2.5, 30);
                for (ServerPlayer p : players(40)) { hurtP(p, 16f); Holo.explosion(l, tag, p.position().add(0, 1, 0), 2, 0xFF3040); }
                BossSfx.play(l, home, "boom", 2.5f, 0.8f);
            });
        }
        /** 심심이 돌진: 지렁이가 투기장을 가로지름. */
        void worm(ServerPlayer tgt) {
            rig.hold("minecraft:command_block");
            say("심심이~ 저기 물어!");
            Vec3 dir = tgt.position().subtract(home).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : dir.normalize();
            Vec3 from = tgt.position().subtract(dir.scale(30)), to = tgt.position().add(dir.scale(30));
            Holo.beamGlow(l, tag, from.add(0, 0.2, 0), to.add(0, 0.2, 0), 1.2, 0xFF55FF, 1, 20, 2);   // 예고: 바닥 띠
            for (int i = 0; i < 14; i++) {
                double s = 2.6 - i * 0.12;
                Vec3 st = from.subtract(dir.scale(i * 2.2));
                Holo.block(l, tag, st, i == 0 ? "minecraft:dispenser" : i % 2 == 0 ? "minecraft:iron_block" : "minecraft:polished_deepslate", i == 0 ? 0xFF55FF : -1,
                        Holo.m(Holo.I(), s, s, s, Vec3.ZERO, true), 62, 0).key(20, Holo.m(Holo.I(), s, s, s, to.subtract(from).add(0, Math.sin(i) * 0.5, 0), true), 36);
            }
            for (int k = 0; k < 12; k++) {
                final double u = k / 11.0;
                later(20 + k * 3, () -> { Vec3 h = from.lerp(to, u); playersInRange(h, 3.2, 14f); l.sendParticles(ParticleTypes.CLOUD, h.x, h.y + 1, h.z, 10, 1, 0.5, 1, 0.05); });
            }
        }
        /** 매직 쉴드: 4초 무적 거품. */
        void shield() {
            rig.hold("jjk:dev_phone");
            say("매직 쉴드~ 지금은 안 아파요~");
            guardFor(70);
            rig.play("guard", 60);
            final Mob b = body;
            Holo.bubble(l, tag, () -> b.position().add(0, S, 0), 1.9 * S, 0x55DDFF, 70);
            later(70, () -> { Holo.shards(l, tag, body.position().add(0, S, 0), "minecraft:light_blue_stained_glass", 20, 4); });
        }
        /** 개발자의 버튼: 3초 뒤 지름 18 삭제. */
        void button(ServerPlayer tgt) {
            rig.hold("jjk:dev_button");
            say("이 버튼 한번 눌러볼까~");
            Vec3 c = tgt.position();
            Holo.circle(l, tag, c.add(0, 0.1, 0), 9, 0xFF2020, 64, 0);
            Holo.block(l, tag, c.add(0, 12, 0), "minecraft:red_concrete", 0xFF2020, Holo.m(Holo.I(), 3, 0.8, 3, Vec3.ZERO, true), 64, 0)
                    .key(50, Holo.m(Holo.I(), 3, 0.3, 3, new Vec3(0, -0.25, 0), true), 4);
            for (int i = 0; i < 3; i++) { final int n = 3 - i; later(i * 20, () -> Holo.banner(l, tag, c.add(0, 15, 0), banner(String.valueOf(n), 0xFF2020), 3, 18)); }
            later(60, () -> {
                Holo.pillar(l, tag, c, 120, 9, 0xFFFFFF, 2, 6, 10);
                Holo.shock(l, tag, c, 12, 0xFF2020, 10);
                playersInRange(c, 9, 40f);
                BossSfx.play(l, c, "boom", 3f, 0.5f);
            });
        }
        /** /tp 행성 (소형): 투기장 중앙에 마그마 행성. */
        void planet() {
            say("/tp @e[type=planet,limit=1] ~ ~ ~   (소형 버전)");
            Vec3 c = home;
            Holo.circle(l, tag, c.add(0, 0.1, 0), 13, 0xFF6020, 46, 0);
            Holo.block(l, tag, c, "minecraft:magma_block", 0xFF6020, Holo.m(Holo.I(), 8, 8, 8, new Vec3(0, 70, 0), true), 46, 0)
                    .key(4, Holo.m(Holo.I(), 8, 8, 8, new Vec3(0, 3, 0), true), 36).spin(6);
            later(40, () -> {
                Holo.explosion(l, tag, c, 12, 0xFF6020);
                Holo.shock(l, tag, c, 26, 0xFF8030, 14);
                playersInRange(c, 13, 32f);
                BossSfx.play(l, c, "boom", 3f, 0.4f);
                Titan.shake(l, c, 60, true);
            });
        }
        /** /kill @a 취소 QTE: 8초 안에 ESC 3개. */
        void killQte(ServerPlayer tgt) {
            say("/kill @a  …엔터 칠까 말까~");
            qte = 1; qteT = 160;
            guardFor(170);
            rig.play("type", 160);
            caption("§4§l/kill @a §f입력 중…\n§eESC 키 3개를 8초 안에 부숴!");
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                Vec3 q = tgt.position().add(Math.cos(a) * 7, 0.5, Math.sin(a) * 7);
                Mob m = spawn(EntityType.IRON_GOLEM, q, 30, 0.5, true);
                if (m == null) continue;
                m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
                m.setSilent(true);
                m.setCustomName(Component.literal("§c§l[ ESC ]"));
                m.setCustomNameVisible(true);
                esc.add(m); adds.add(m);
                final Mob mm = m;
                Holo.block(l, tag, q, "minecraft:white_concrete", 0xFF3040, Holo.m(Holo.I(), 1.3, 0.6, 1.3, Vec3.ZERO, true), 170, 1)
                        .follow(() -> mm.isAlive() ? mm.position().add(0, 0.8, 0) : new Vec3(0, -999, 0), Vec3.ZERO);
            }
        }
        void tickQte() {
            qteT--;
            esc.removeIf(m -> !m.isAlive());
            if (qteT % 20 == 0 && qteT > 0) for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§4§l/kill @a §f%d초 · §eESC %d개", qteT / 20, esc.size())), true);
            if (esc.isEmpty()) {
                qte = 0; guardT = 0; cd = 50;
                say("어?! ESC 눌렀어? …아 그거 진짜 되는구나.");
                rig.play("kneel", 30);
                damageV(MAX * 0.06f);
                Holo.explosion(l, tag, body.position().add(0, S, 0), 5, 0x55FF55);
                return;
            }
            if (qteT <= 0) {
                qte = 0; guardT = 0; cd = 40; rig.play("laugh", 30);
                for (Mob m : esc) m.discard();
                esc.clear();
                say("엔터~");
                for (ServerPlayer p : players(80)) { p.invulnerableTime = 0; p.hurtServer(l, l.damageSources().magic(), p.getMaxHealth() * 0.7f); }
                BossSfx.play(l, home, "boom", 3f, 0.5f);
            }
        }

        @Override void onDeath() {
            caption("§b§l개발자 격파\n§f「다음 업데이트 때 보자~」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "개발자를 이겼다");
            giveCmd("jjk:dev_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:dev_signature 1");
            giveCmd("jjk:code " + (hard ? 32 : 16));
            giveCmd("jjk:infinity_key " + (hard ? 8 : 4));
            if (hard) { giveCmd("jjk:chosen_light 1"); giveCmd("minecraft:netherite_block 2"); }
            BossSfx.play(l, body.position(), "choir", 3f, 1.0f);
        }
        @Override void onLeave() { say("다들 나갔네… 나도 게임하러 간다~"); }
        @Override void cleanup() {
            for (Mob m : esc) if (m.isAlive()) m.discard();
            super.cleanup();
        }
    }
}
