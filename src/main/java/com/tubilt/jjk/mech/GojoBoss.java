package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 고죠의 열쇠 → 보스전 「고죠 사토루」 (가상 체력 1100, 1페이지).
 *  몸: 디스플레이 인형 (흰 머리·안대·교복), 살짝 떠서 플레이어 곁을 맴돎 (근접 사거리 안).
 *  무하한: 투사체 무효 · 근접 피해 40%만 → 무라사키·무량공처 직후 「빈틈」엔 풀 피해.
 *  패턴: 근접 연타 · 원래 창 · 원래 혁 · 순간이동 3연타 · 허식 「무라사키」(영창판) · 영역전개 「무량공처」(60%·25%)
 */
public final class GojoBoss {
    private GojoBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Gojo) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.GOJO_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7고죠는 이미 와 있다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        Gojo g = new Gojo(l, at);
        if (g.body != null) Bosses.ACTIVE.add(g);
    }

    static final class Gojo extends Bosses.Boss {
        static final float MAX = 1100;
        static final double S = 1.25;
        static final String WHO = "고죠 사토루";
        static final int ICE = 0xBFE6FF;
        final Random R = new Random();
        int cd = 40, atk = 0, open = 0, lastInf = -99; boolean dying = false, void1 = false, void2 = false;
        double ang = 0;

        Gojo(ServerLevel l, Vec3 at) {
            super(l, "§f§l고죠 사토루 §7— 최강", BossEvent.BossBarColor.WHITE);
            phase = 0;
            Vec3 g = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(g.add(0, 12, 0), S, MAX, "§f고죠 사토루");
            if (body == null) return;
            rig = new BossRig(this, "gojo", S);
            rig.floating = true;
            faceTo(nearest());
            intro(g);
        }

        static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }
        void say(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§f<고죠> " + s)); }

        @Override float adjust(DamageSource src, float amt) {
            if (open > 0 || dying) return amt;
            if (t - lastInf > 8) {
                lastInf = t;
                Holo.banner(l, tag, body.position().add(0, 2.9 * S, 0), banner("∞ 무하한", ICE), 0.9, 14);
                Holo.flare(l, tag, body.position().add(0, S, 0), 1.6 * S, ICE, 4);
                sfx("block", 0.8f, 1.6f);
            }
            if (src.getDirectEntity() instanceof Projectile pr) { pr.setDeltaMovement(Vec3.ZERO); return 0; }
            return amt * 0.4f;
        }

        // ═════════ 컷신
        void intro(Vec3 g) {
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x0A1430, 7000, 0.45f));
            sfx("deep", 3f, 0.8f);
            camFocus = g;   // 카메라는 내려앉을 자리를 비춤
            later(170, () -> camFocus = null);
            for (int i = 1; i <= 24; i++) { final int k = i; later(i, () -> moveTo(g.add(0, 12 - k * 0.5 + 0.3, 0))); }   // 하늘에서 천천히 내려옴
            later(26, () -> { Holo.shock(l, tag, g, 14, ICE, 16); sfx("stinger", 2f, 1.2f); });
            later(40, () -> rig.play("laugh", 40));
            scene(170, WHO, new String[]{"어라? 열쇠를 쓴 게 너야?", "괜찮아. 걱정 마.", "난 최강이니까.", "……천상천하 유아독존."}, 38, 5.5f, 2.2f);
            later(120, () -> { Holo.flare(l, tag, body.position().add(0, 1.9 * S, 0.3), 2.5, 0x6FD8FF, 20); sfx("magic", 2f, 1.4f); rig.play("point", 40); });
            later(166, () -> {
                phase = 1; cd = 30;
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("고죠 사토루", "최강의 주술사", ICE, 1));
                music("minecraft:music_disc.precipice");
            });
        }
        void deathScene() {
            dying = true;
            closeDomain("gojo.domain", "gojo:domain/close");
            rig.floating = false;
            rig.play("kneel", 9999);
            moveTo(new Vec3(body.getX(), groundY(body.getX(), body.getY(), body.getZ()), body.getZ()));
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFFFFF, 5000, 0.4f));
            sfx("choir", 2.5f, 0.9f);
            stopMusic();
            scene(150, WHO, new String[]{"……하하. 진짜로?", "꽤 하잖아.", "다음엔 진심으로 할게. 즐거웠어."}, 44, 5f, 1.6f);
            later(140, () -> { Holo.flare(l, tag, body.position().add(0, S, 0), 6, ICE, 12); Holo.shards(l, tag, body.position().add(0, S, 0), "minecraft:light_blue_stained_glass", 30, 6); sfx("shatter", 2f, 0.8f); });
            later(148, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            if (open > 0) open--;
            if (!void1 && frac() < 0.6f) { void1 = true; domain(); return; }
            if (!void2 && frac() < 0.25f) { void2 = true; domain(); return; }
            if (atFloor()) { deathScene(); return; }
            ServerPlayer tg = nearest();
            if (tg == null) return;
            faceTo(tg);
            // 플레이어 곁 2.5~3.5칸을 천천히 맴돎 (근접으로 때릴 수 있는 거리), 살짝 떠 있음
            if (!rig.busy()) {
                ang += 0.03;
                Vec3 want = tg.position().add(Math.cos(ang) * 3.0, 0, Math.sin(ang) * 3.0);
                want = new Vec3(want.x, groundY(want.x, tg.getY() + 1, want.z) + 0.3, want.z);
                glide(want, 0.12);
            }
            if (t % 5 == 0) l.sendParticles(ParticleTypes.END_ROD, body.getX(), body.getY() + S, body.getZ(), 1, 0.4, 0.6, 0.4, 0.01);
            if (--cd > 0) return;
            int k = new int[]{4, 0, 4, 2, 1, 4, 0, 2, 3}[atk++ % 9];
            cd = frac() < 0.4f ? 34 : 46;
            switch (k) {
                case 0 -> blue(tg);
                case 1 -> red(tg);
                case 2 -> blink(tg);
                case 3 -> purple(tg);
                case 4 -> jab(tg);
                default -> { }
            }
        }

        /** 근접: 손등치기 2연타 (빈틈 없음, 피하기 쉬움). */
        void jab(ServerPlayer tg) {
            if (tg.position().distanceTo(body.position()) > 4.5) { blink(tg); return; }
            melee(tg, 6f, "swing", 9, 3.6);
            later(14, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, 6f, "sweep", 6, 3.6); } });
        }
        /** 술식순전 「창」 — 원래 창 (체력 40% 아래면 영창판). */
        void blue(ServerPlayer tg) {
            say("술식순전 「창」");
            rig.play("cast", 26);
            sfx("charge", 1.5f, 1.3f);
            later(10, () -> {
                ServerPlayer tt = tg.isAlive() ? tg : nearest();
                if (tt == null) return;
                strong(frac() < 0.4f);
                asBoss(tt, "run function gojo:cast/blue");
                strong(false);
            });
        }
        /** 술식반전 「혁」 — 원래 혁. */
        void red(ServerPlayer tg) {
            say("술식반전 「혁」");
            rig.play("point", 26);
            Holo.flare(l, tag, body.position().add(0, 1.3 * S, 0).add(body.getLookAngle().scale(0.9)), 0.9, 0xFF2A2A, 12);
            Holo.beamGlow(l, tag, body.position().add(0, 1.3 * S, 0), tg.position().add(0, 1, 0), 0.05, 0xFF2A2A, 1, 12, 1);
            sfx("charge", 1.5f, 1.6f);
            later(12, () -> {
                ServerPlayer tt = tg.isAlive() ? tg : nearest();
                if (tt == null) return;
                strong(frac() < 0.4f);
                asBoss(tt, "run function gojo:cast/red");
                strong(false);
            });
        }
        /** 순간이동 → 뒤에서 연타 3회. */
        void blink(ServerPlayer tg) {
            say("느려.");
            for (int i = 0; i < 3; i++) {
                later(i * 10, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null) return;
                    Vec3 back = tt.position().subtract(tt.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
                    Holo.flare(l, tag, body.position().add(0, S, 0), 1.2, ICE, 4);
                    moveTo(new Vec3(back.x, groundY(back.x, tt.getY() + 1, back.z) + 0.2, back.z));
                    faceTo(tt);
                    sfx("whoosh", 1.2f, 1.4f);
                    melee(tt, 5f, "swing", 4, 3.2);
                });
            }
        }
        /** 허식 「무라사키」: 영창 (그동안 때릴 수 있음) → 원래 무라사키(영창판) → 3초 빈틈. */
        void purple(ServerPlayer tg) {
            rig.play("raise", 46);
            String[] chant = {"구현", "「조복」", "「상향」", "팔면체의 신", "허식 「무라사키」"};
            for (int i = 0; i < chant.length; i++) { final String ln = chant[i]; final int ii = i; later(i * 10, () -> { line(WHO, ln); sfx(ii < 4 ? "drum" : "stinger", 1.8f, 0.7f + ii * 0.12f); }); }
            Vec3 c = body.position().add(0, 1.4 * S, 0);
            Vec3 side = body.getLookAngle().cross(new Vec3(0, 1, 0)).normalize();
            Holo.flare(l, tag, c.add(side.scale(1.6)), 1.3, 0x3A7BFF, 44);
            Holo.flare(l, tag, c.subtract(side.scale(1.6)), 1.3, 0xFF2A2A, 44);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x200030, 2500, 0.35f));
            later(44, () -> {
                ServerPlayer tt = tg.isAlive() ? tg : nearest();
                rig.play("cast", 20);
                strong(true);
                asBoss(tt, "run function gojo:cast/purple");
                strong(false);
                sfx("boom", 2.5f, 0.6f);
            });
            later(66, () -> { open = 70; say("……좀 지쳤네."); rig.play("laugh", 30); });
        }
        /** 영역전개 「무량공처」 — 원래 무량공처 (NPC 소유로 7초) → 갇힌 대상 연타 → 해제 후 빈틈. */
        void domain() {
            guardFor(24);
            rig.play("raise", 30);
            line(WHO, "영역전개");
            sfx("deep", 3f, 0.6f);
            Vec3 c = body.position();
            castId();
            asBoss(nearest(), "run function gojo:domain/open");
            npcDomain("gojo.domain");
            later(1, () -> npcDomain("gojo.domain"));
            later(20, () -> line(WHO, "무량공처"));
            later(40, () -> say("아무것도 할 수 없지? 괜찮아, 금방 끝나."));
            for (int i = 0; i < 6; i++) later(50 + i * 12, () -> {
                ServerPlayer tt = nearest();
                if (tt == null || tt.position().distanceTo(c) > 14) return;
                Vec3 back = tt.position().add((R.nextDouble() - 0.5) * 3, 0, (R.nextDouble() - 0.5) * 3);
                moveTo(new Vec3(back.x, tt.getY() + 0.2, back.z));
                faceTo(tt);
                melee(tt, 5f, i2pose(), 3, 3.5);
            });
            later(140, () -> {
                closeDomain("gojo.domain", "gojo:domain/close");
                open = 90;
                say("……영역 유지하는 거 은근 피곤하단 말이야.");
                cd = 50;
            });
        }
        String i2pose() { return R.nextBoolean() ? "swing" : "sweep"; }

        @Override void onDeath() {
            caption("§b§l고죠 사토루 격파\n§f「다음엔 진심으로 할게.」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "최강을 이겼다");
            giveCmd("jjk:gojo_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:unlimited_void 1");
            giveCmd("jjk:purple 1");
            giveCmd("jjk:six_eyes 1");
            giveCmd("jjk:gojo_blindfold 1");
            giveCmd("minecraft:diamond_block 8");
            BossSfx.play(l, body.position(), "choir", 3f, 1.1f);
        }
        @Override void onLeave() { say("다들 갔네. 디저트나 먹으러 가야지~"); }
        @Override void cleanup() {
            closeDomain("gojo.domain", "gojo:domain/close");
            super.cleanup();
        }
    }
}
