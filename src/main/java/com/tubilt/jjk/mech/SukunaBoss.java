package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 스쿠나 열쇠 → 보스전 「료멘 스쿠나」 (가상 체력 1200, 1페이지).
 *  몸: 디스플레이 인형 (분홍 머리·얼굴 문신·흰 기모노·팔 4개), 걸어서 다가와 근접전.
 *  패턴: 네 팔 연타 · 원래 「해」 연발 · 「팔」 X자 절단 · 원래 「푸가」 · 원래 세계참 · 영역전개 「복마어주자」(50%) · 반전술식(30%, 1회)
 */
public final class SukunaBoss {
    private SukunaBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Suk) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.SUKUNA_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7스쿠나는 이미 강림해 있다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        Suk s = new Suk(l, at);
        if (s.body != null) Bosses.ACTIVE.add(s);
    }

    static final class Suk extends Bosses.Boss {
        static final float MAX = 1200;
        static final double S = 1.3;
        static final int RED = 0xB01010, WHITE = 0xFFFFFF, FIRE = 0xFF9020;
        static final String WHO = "료멘 스쿠나";
        final Random R = new Random();
        final Vec3 home;
        int cd = 40, atk = 0; boolean dying = false, domainUsed = false, healed = false;

        Suk(ServerLevel l, Vec3 at) {
            super(l, "§4§l료멘 스쿠나 §7— 저주의 왕", BossEvent.BossBarColor.RED);
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home, S, MAX, "§4료멘 스쿠나");
            if (body == null) return;
            rig = new BossRig(this, "sukuna", S).fourArms();
            faceTo(nearest());
            intro();
        }

        void say(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§4<스쿠나> §f" + s)); }
        void skill(String name, int rgb) {
            Holo.banner(l, tag, body.position().add(0, 2.9 * S, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(name, rgb, true) + "]}", 1.1, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§4스쿠나 §7▸ §f" + name), true);
        }

        // ═════════ 컷신
        void intro() {
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x300000, 7000, 0.5f));
            sfx("deep", 3f, 0.5f);
            rig.play("kneel", 64);
            Holo.pillar(l, tag, home, 60, 2.5, RED, 6, 30, 12);
            for (int i = 0; i < 12; i++) {   // 쇠사슬 속박이 끊어짐
                double a = i * Math.PI / 6;
                Vec3 q = home.add(Math.cos(a) * 3.5, 0.3, Math.sin(a) * 3.5);
                Holo.block(l, tag, q, "minecraft:iron_chain", -1, Holo.m(Holo.I(), 0.5, 3.0, 0.5, Vec3.ZERO, true), 70, 0)
                        .key(55, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 8);
            }
            later(62, () -> { sfx("shatter", 3f, 0.6f); Holo.shock(l, tag, home, 22, RED, 16); rig.play("raise", 40); Titan.shake(l, home, 50, true); });
            scene(160, WHO, new String[]{"…속박을 풀었나.", "명명백백하게 말해주지. 넌 날 못 이긴다.", "그래도 덤벼라. 꼬마.", "분수를 알게 해주마."}, 36, 5.5f, 2.2f);
            later(130, () -> rig.play("laugh", 30));
            later(156, () -> {
                phase = 1; cd = 30;
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("료멘 스쿠나", "저주의 왕", RED, 1));
                sfx("stinger", 3f, 0.7f);
                music("minecraft:music_disc.pigstep");
            });
        }
        void deathScene() {
            dying = true;
            closeDomain("gojo.ms", "gojo:ms/close");
            rig.play("kneel", 9999);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x200000, 5000, 0.5f));
            sfx("choir", 3f, 0.5f);
            stopMusic();
            scene(140, WHO, new String[]{"……크큭.", "이 몸을 쓰러뜨리다니.", "기억해두지. 다음은 없다."}, 40, 5f, 1.6f);
            later(110, () -> rig.play("fall", 9999));
            later(130, () -> { Holo.flare(l, tag, body.position().add(0, 1, 0), 7, RED, 12); Holo.shards(l, tag, body.position().add(0, 1, 0), "minecraft:red_stained_glass", 30, 6); sfx("shatter", 2f, 0.6f); });
            later(138, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            if (!domainUsed && frac() < 0.5f) { domainUsed = true; domain(); return; }
            if (!healed && frac() < 0.3f) { healed = true; reverse(); return; }
            if (atFloor()) { deathScene(); return; }
            ServerPlayer tg = nearest();
            if (tg == null) return;
            if (!rig.busy()) chase(tg, frac() < 0.5f ? 0.2 : 0.16, 2.6);
            else faceTo(tg);
            if (t % 6 == 0) l.sendParticles(ParticleTypes.SMOKE, body.getX(), body.getY() + 1, body.getZ(), 2, 0.4, 0.6, 0.4, 0.01);
            if (--cd > 0) return;
            double dist = tg.position().distanceTo(body.position());
            int k = dist < 4 ? new int[]{4, 0, 4, 1, 4, 2, 0, 3}[atk++ % 8] : new int[]{0, 2, 0, 1, 3}[atk++ % 5];
            cd = frac() < 0.5f ? 32 : 44;
            switch (k) {
                case 0 -> dismantle(tg);
                case 1 -> cleave(tg);
                case 2 -> fuga(tg);
                case 3 -> worldSlash(tg);
                case 4 -> combo(tg);
                default -> { }
            }
        }

        /** 네 팔 연타: 3번 휘두름. */
        void combo(ServerPlayer tg) {
            melee(tg, 6f, "swing", 7, 3.8);
            later(11, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, 6f, "sweep", 5, 3.8); } });
            later(20, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, 8f, "slam", 9, 3.8); later(9, () -> Holo.shock(l, tag, body.position(), 5, RED, 8)); } });
        }
        /** 「해」: 원래 「해」 참격을 연달아 날림 (체력 50% 아래면 5연발). */
        void dismantle(ServerPlayer tg) {
            skill("「해(解)」", WHITE);
            int n = frac() < 0.5f ? 5 : 3;
            for (int i = 0; i < n; i++) later(i * 6, () -> {
                ServerPlayer tt = nearest();
                if (tt == null) return;
                faceTo(tt);
                rig.play("point", 8);
                castId();
                asBoss(tt, "positioned ^ ^ ^1.5 summon marker run function gojo:kai/init");
                sfx("slash", 1.4f, 1.2f);
            });
        }
        /** 「팔」: 대상 하나를 노려 0.8초 뒤 X자 절단. */
        void cleave(ServerPlayer tg) {
            skill("「팔(捌)」", RED);
            Holo.hitbox(l, tag, tg, RED, 16);
            rig.play("sweep", 22);
            sfx("charge", 1.5f, 1.6f);
            later(16, () -> {
                ServerPlayer tt = tg.isAlive() ? tg : nearest();
                if (tt == null) return;
                Vec3 q = tt.position().add(0, 1, 0);
                Holo.beamGlow(l, tag, q.add(-2, 2, -2), q.add(2, -2, 2), 0.3, RED, 0, 3, 5);
                Holo.beamGlow(l, tag, q.add(-2, 2, 2), q.add(2, -2, -2), 0.3, RED, 0, 3, 5);
                Holo.flare(l, tag, q, 2.5, RED, 5);
                sfx("slash", 2f, 0.6f);
                if (tt.position().add(0, 1, 0).distanceTo(q) < 3) hurtP(tt, frac() < 0.5f ? 16f : 12f);
            });
        }
        /** 「푸가」: 활 시위 → 원래 푸가 화살. */
        void fuga(ServerPlayer tg) {
            line(WHO, "■ ■ ■ ■ ……「개(開)」");
            skill("「푸가」", FIRE);
            rig.play("cast", 34);
            Vec3 a = body.position().add(0, 1.6 * S, 0);
            Holo.flare(l, tag, a.add(body.getLookAngle()), 1.6, FIRE, 26);
            Holo.beamGlow(l, tag, a, tg.position().add(0, 1, 0), 0.06, FIRE, 1, 22, 1);
            sfx("charge", 2f, 0.7f);
            for (int k = 0; k < 6; k++) later(k * 4, () -> l.sendParticles(ParticleTypes.FLAME, body.getX(), body.getY() + 1.5 * S, body.getZ(), 12, 0.6, 0.8, 0.6, 0.02));
            later(24, () -> {
                ServerPlayer tt = tg.isAlive() ? tg : nearest();
                if (tt == null) return;
                castId();
                asBoss(tt, "positioned ^ ^ ^1.5 summon marker run function gojo:fuga/init");
                sfx("whoosh", 2f, 0.6f);
                sfx("boom", 1.5f, 1.4f);
            });
        }
        /** 세계참: 영창 (그동안 공격 기회) → 원래 세계참. */
        void worldSlash(ServerPlayer tg) {
            rig.play("raise", 42);
            String[] chant = {"용의 비늘", "반발", "쌍생상애", "……세계참."};
            for (int i = 0; i < chant.length; i++) { final String ln = chant[i]; final int ii = i; later(i * 10, () -> { line(WHO, ln); sfx("drum", 2f, 0.6f + ii * 0.1f); }); }
            Holo.beamGlow(l, tag, body.position().add(0, 2, 0), tg.position().add(0, 1, 0), 0.05, WHITE, 1, 38, 1);
            later(40, () -> {
                ServerPlayer tt = tg.isAlive() ? tg : nearest();
                faceTo(tt);
                rig.play("sweep", 16);
                asBoss(tt, "run function gojo:ws/cast");
                sfx("laser", 2.5f, 0.7f);
            });
        }
        /** 영역전개 「복마어주자」 — 원래 복마어주자 (12초). */
        void domain() {
            guardFor(24);
            rig.play("raise", 40);
            line(WHO, "영역전개");
            sfx("deep", 3f, 0.5f);
            castId();
            asBoss(nearest(), "run function gojo:ms/open");
            npcDomain("gojo.ms");
            later(1, () -> npcDomain("gojo.ms"));
            later(20, () -> line(WHO, "복마어주자"));
            later(30, () -> say("도망쳐도 좋다. 반경 안이라면 모든 것이 썰린다."));
            later(260, () -> closeDomain("gojo.ms", "gojo:ms/close"));
        }
        /** 반전술식: 크게 회복 (그동안도 맞음). */
        void reverse() {
            skill("반전술식", 0xFFE0E0);
            say("…상처 정도는 반전으로 막을 수 있다.");
            rig.play("laugh", 30);
            Holo.flare(l, tag, body.position().add(0, S, 0), 4, 0xFFE0E0, 30);
            sfx("magic", 2f, 1.2f);
            for (int i = 0; i < 30; i++) later(i, () -> healV(MAX * 0.006f));
        }

        @Override void onDeath() {
            caption("§4§l료멘 스쿠나 격파\n§f「기억해두지.」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "저주의 왕을 이겼다");
            giveCmd("jjk:sukuna_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:dismantle 1");
            giveCmd("jjk:fuga 1");
            giveCmd("jjk:malevolent_shrine 1");
            BossSfx.play(l, body.position(), "choir", 3f, 0.6f);
        }
        @Override void onLeave() { say("…겁쟁이 놈."); }
        @Override void cleanup() {
            closeDomain("gojo.ms", "gojo:ms/close");
            super.cleanup();
        }
    }
}
