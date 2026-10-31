package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 코밍의 열쇠 → 보스전 「코밍」 (가상 체력 1100, 2페이지).
 *  몸: 디스플레이 인형 (바가지 머리·흰 셔츠·빨간 넥타이·레고 책가방), 통통 뛰어다니며 근접 + 술식 연속기.
 *  원래 코밍 술식(식·교·무려(n+2)끼·레고사주자)을 꼭두각시 시전자로 그대로 사용.
 *  PAGE 1 「급식 시간」 / PAGE 2 「레고사주자」(원래 영역 + 연속기 가속 + 점심시간 유아독존)
 */
public final class KomingBoss {
    private KomingBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Kom) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.KOMING_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7코밍은 이미 와 있다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(7));
        Kom k = new Kom(l, at);
        if (k.body != null) Bosses.ACTIVE.add(k);
    }

    static final class Kom extends Bosses.Boss {
        static final float MAX = 1100;
        static final double S = 1.0;
        static final int BLUE = 0x3A7BFF, RED = 0xFF3030, PURPLE = 0xB050FF, GOLD = 0xFFD84A;
        static final String WHO = "코밍";
        static final String[] LEGO = {"minecraft:red_concrete", "minecraft:yellow_concrete", "minecraft:blue_concrete"};
        static final String[] HUNSU = {"아저씨 그거 왜 써요?", "와드 좀 박아요~", "그냥 제가 할게요", "궁 아껴요!!", "브롤 몇 렙이에요?", "트로피 몇 개예요?",
                "그 각 아니잖아요", "스킬 다 빼고 들어가네", "판단이 너무 느려요", "손이 안 따라주시네~", "제가 해드릴까요?", "그거 너프됐어요"};
        final Random R = new Random();
        final Vec3 home;
        int cd = 40, atk = 0, combo = 0, lunchUsed = 0, hop = 0; boolean dying = false;

        Kom(ServerLevel l, Vec3 at) {
            super(l, "§e§l코밍 §7— PAGE 0", BossEvent.BossBarColor.YELLOW);
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home, S, MAX, "§e코밍");
            if (body == null) return;
            rig = new BossRig(this, "koming", S).prop("bag", new Vec3(0, -0.3, 0), 1.0, true);
            faceTo(nearest());
            intro();
        }

        static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }
        void say(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§e<코밍> §f" + s)); }
        void skill(String name, int rgb) {
            Holo.banner(l, tag, body.position().add(0, 2.6 * S, 0), banner(name, rgb), 0.9, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§e코밍 §7▸ §f" + name), true);
        }
        @Override float floor() { return MAX * (phase <= 1 ? 0.5f : 0f); }

        // ═════════ 컷신
        void intro() {
            sfx("drum", 3f, 0.8f);
            for (int i = 0; i < 18; i++) {   // 레고가 쏟아지며 등장
                Vec3 q = home.add((R.nextDouble() - 0.5) * 6, 0.05, (R.nextDouble() - 0.5) * 6);
                Holo.block(l, tag, q, LEGO[i % 3], -1, Holo.m(Holo.I(), 0.6, 0.35, 0.4, new Vec3(0, 14 + i, 0), true), 120, 0)
                        .key(2 + i, Holo.m(Holo.I(), 0.6, 0.35, 0.4, new Vec3(0, 0.18, 0), true), 10);
            }
            later(30, () -> rig.play("raise", 30));
            scene(130, WHO, new String[]{"아저씨 안녕하세요!", "근데 브롤 실력은 좀 그렇네요~", "급식안으로 다 보여요. 시작할게요~"}, 40, 4.5f, 1.4f);
            later(80, () -> rig.play("laugh", 40));
            later(126, () -> {
                phase = 1; cd = 30;
                bar.setName(Component.literal("§e§l코밍 §7— PAGE 1 · 급식 시간"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("코밍", "PAGE 1 — 급식 시간", GOLD, 1));
                sfx("stinger", 2.5f, 1.2f);
                music("minecraft:music_disc.chirp");
            });
        }
        void page2() {
            sfx("glitch", 2f, 1.2f);
            rig.play("kneel", 40);
            scene(150, WHO, new String[]{"어… 아저씨 생각보다 좀 하시네요?", "뇌 파괴와 재생~ 다시 풀충전!", "그럼 진짜로 할게요. 술식전개——", "「레고사주자」"}, 34, 5f, 1.4f);
            later(40, () -> {
                Holo.flare(l, tag, body.position().add(0, 1.8, 0), 3, 0xFF8080, 10);
                sfx("glitch", 2f, 1.3f);
                rig.play("raise", 60);
                for (int i = 0; i < 20; i++) later(i, () -> healV(MAX * 0.006f));
            });
            later(110, () -> kcast("lego", nearest()));
            later(146, () -> {
                phase = 2; cd = 20;
                bar.setName(Component.literal("§c§l코밍 §7— PAGE 2 · 레고사주자"));
                bar.setColor(BossEvent.BossBarColor.RED);
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("술식전개", "레고사주자", RED, 1));
                sfx("stinger", 3f, 0.8f);
                music("minecraft:music_disc.blocks");
            });
        }
        void deathScene() {
            dying = true;
            BossPuppet.releaseKoming(l);
            rig.play("fall", 9999);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFF0C0, 5000, 0.4f));
            sfx("choir", 3f, 1.2f);
            stopMusic();
            scene(140, WHO, new String[]{"으악! 아저씨 핵 쓰죠?!", "……아니 진짜 잘하시네.", "인정할게요. 브롤은 제가 이기지만요~"}, 40, 4.5f, 1f);
            later(130, () -> { Holo.flare(l, tag, body.position().add(0, 0.6, 0), 5, GOLD, 12); Holo.shards(l, tag, body.position().add(0, 0.6, 0), "minecraft:yellow_concrete", 30, 6); });
            later(138, this::kill);
        }

        // ═════════ 전투: 근접 + 연속기
        @Override void step() {
            if (phase == 0 || dying) return;
            if (phase == 1 && atFloor()) { page2(); return; }
            if (phase == 2 && atFloor()) { deathScene(); return; }
            ServerPlayer tg = nearest();
            if (tg == null) return;
            ServerPlayer fp = BossPuppet.BY_NAME.get("코밍");
            if (fp != null) BossPuppet.aim(fp, body, tg);
            if (!rig.busy()) {   // 통통 튀며 다가감
                chase(tg, phase == 2 ? 0.24 : 0.2, 2.2);
                hop++;
                rig.liftGoal = Math.abs(Math.sin(hop * 0.35)) * 0.25;
            } else { faceTo(tg); rig.liftGoal = 0; }
            if (phase == 2 && lunchUsed == 0 && frac() < 0.25f) { lunchUsed = 1; lunch(); return; }
            if (--cd > 0) return;
            double dist = tg.position().distanceTo(body.position());
            int[][] chains = phase == 1
                    ? new int[][]{{7, 0}, {3, 1, 7}, {0, 1, 2}, {4, 7}, {1, 1, 5}, {7, 7, 2}}
                    : new int[][]{{0, 1, 2}, {7, 3, 1}, {4, 0, 1, 2}, {7, 7, 5}, {0, 2, 2}, {6, 1}};
            int[] ch = chains[atk++ % chains.length];
            if (dist > 6 && ch[0] == 7) ch = new int[]{0, 1};
            int gap = phase == 2 ? 8 : 11;
            for (int i = 0; i < ch.length; i++) {
                final int k = ch[i];
                later(i * gap, () -> { ServerPlayer tt = nearest(); if (tt != null) cast(k, tt); });
            }
            cd = ch.length * gap + (phase == 2 ? 24 : 36);
        }
        void cast(int k, ServerPlayer tg) {
            faceTo(tg);
            switch (k) {
                case 0 -> { rig.play("cast", 16); skill("술식순전 「식(吸)」", BLUE); kcast("sik", tg); }
                case 1 -> { rig.play("point", 14); skill("술식반전 「교(膠)」", RED); kcast("gyo", tg); }
                case 2 -> { combo++; rig.play("cast", 30); skill("급식 「무려" + (combo + 2) + "끼」", PURPLE); kcast("muryeo", tg); }
                case 3 -> { if (phase == 2) { rig.play("cast", 30); kcast("muryeo", tg); } else legoRain(tg); }
                case 4 -> hunsu();
                case 5 -> gupsikhan();
                case 6 -> { gupsikhan(); legoRain(tg); }
                case 7 -> { melee(tg, 5f, "swing", 6, 3.0); later(9, () -> { ServerPlayer t2 = nearest(); if (t2 != null) melee(t2, 5f, "sweep", 5, 3.0); }); }
                default -> { }
            }
        }
        // ── 원래 코밍 술식: 꼭두각시(보스 몸과 겹침)를 시전자로 Koming.cast 호출
        boolean kcast(String id, ServerPlayer tg) {
            try {
                ServerPlayer fp = BossPuppet.koming(l);
                BossPuppet.aim(fp, body, tg);
                Koming.cast(fp, id);
                sfx("magic", 1.2f, 1.2f);
                return true;
            } catch (Throwable e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 코밍 보스 술식 실패: " + id, e); return false; }
        }
        /** 레고 폭격: 떨어진 레고를 밟으면 "레고 밟음!" (구속·피해). */
        void legoRain(ServerPlayer tg) {
            skill("레고 투척", RED);
            rig.play("raise", 20);
            int n = phase == 2 ? 14 : 8;
            for (int i = 0; i < n; i++) {
                Vec3 q = tg.position().add((R.nextDouble() - 0.5) * 9, 0.05, (R.nextDouble() - 0.5) * 9);
                Holo.block(l, tag, q, LEGO[R.nextInt(3)], -1, Holo.m(Holo.I(), 1.2, 0.5, 0.8, new Vec3(0, 16, 0), true), 70, 0)
                        .key(2 + i, Holo.m(Holo.I(), 1.2, 0.5, 0.8, new Vec3(0, 0.25, 0), true), 12);
                final Vec3 qq = q;
                later(14 + i, () -> {
                    l.playSound(null, qq.x, qq.y, qq.z, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.4f, 1.6f);
                    for (ServerPlayer p : players(80)) if (p.position().distanceTo(qq) < 1.6) {
                        hurtP(p, 5f);
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                        Holo.banner(l, tag, p.position().add(0, 2.4, 0), banner("레고 밟음!", GOLD), 0.8, 18);
                    }
                });
            }
        }
        /** 무료훈수 (보스용 약한 버전 — 원래 것은 수십 분 행동 불가라서). */
        void hunsu() {
            skill("술식전개 「무료훈수」", 0x55FFFF);
            rig.play("point", 30);
            for (ServerPlayer p : players(30)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                for (int i = 0; i < 6; i++) {
                    final int ii = i;
                    later(i * 5, () -> {
                        Vec3 at = p.position().add((R.nextDouble() - 0.5) * 3, 2.4 + R.nextDouble(), (R.nextDouble() - 0.5) * 3);
                        Holo.banner(l, tag, at, banner(HUNSU[R.nextInt(HUNSU.length)], ii % 2 == 0 ? 0xFFFFFF : 0x55FFFF), 0.6, 30);
                        hurtP(p, 1.5f);
                    });
                }
                p.sendSystemMessage(Component.literal("§b<코밍> §f" + HUNSU[R.nextInt(HUNSU.length)]));
            }
            sfx("data", 2f, 1.0f);
        }
        /** 급식한: 2.5초 무적 막. */
        void gupsikhan() {
            skill("급식한", GOLD);
            rig.play("guard", 50);
            guardFor(50);
            final Mob b = body;
            Holo.bubble(l, tag, () -> b.position().add(0, 1, 0), 1.5, GOLD, 50);
            sfx("magic", 1.5f, 1.4f);
        }
        /** 점심시간 유아독존: 음식이 돌며 회복 (2페이지 1회, 그동안 맞음). */
        void lunch() {
            skill("점심시간 유아독존", GOLD);
            say("점심시간이다~! 잠깐만요, 밥 먹고 할게요.");
            rig.play("laugh", 60);
            final Mob b = body;
            String[] food = {"minecraft:bread", "minecraft:cooked_chicken", "minecraft:mushroom_stew", "minecraft:apple", "minecraft:milk_bucket", "minecraft:cookie"};
            for (int i = 0; i < food.length; i++) {
                final int ii = i;
                Holo.item(l, tag, b.position(), food[i], false, GOLD, Holo.m(Holo.I(), 0.6, 0.6, 0.6, Vec3.ZERO, false), 60, 1)
                        .follow(() -> { double a = ii * Math.PI / 3 + t * 0.2; return b.position().add(Math.cos(a) * 1.4, 1.2, Math.sin(a) * 1.4); }, Vec3.ZERO).spin(20);
            }
            for (int i = 0; i < 30; i++) later(i * 2, () -> healV(MAX * 0.004f));
            later(60, () -> { say("잘 먹었습니다~ 이제 다시 놀아요!"); cd = 10; });
            l.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 2f, 1f);
        }

        @Override void onDeath() {
            caption("§e§l코밍 격파\n§f「브롤은 제가 이기지만요~」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "급식을 이겼다");
            giveCmd("jjk:jammin_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:koming_eye 1");
            giveCmd("jjk:red_lego 4");
            giveCmd("jjk:yellow_lego 4");
            giveCmd("jjk:blue_lego 4");
            BossSfx.play(l, body.position(), "choir", 3f, 1.2f);
        }
        @Override void onLeave() { say("아저씨 어디 가요? …브롤이나 해야지~"); }
        @Override void cleanup() {
            BossPuppet.releaseKoming(l);
            super.cleanup();
        }
    }
}
