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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 크랄루의 열쇠 → 보스전 「저주의 신 크랄루」 (가상 체력 1300, 2페이지). 처치 시 Ugh.code.
 *  몸: 큰 키·마른 체형, 붉은 넝마, 머리 대신 햇살 달린 태양 가면.
 *  PAGE 1 「태양의 저주」: 태양 광선 · 저주 낙인 · 넝마 채찍 · 작열 구체 비 · 일식(어둠 + 그림자 손) · 근접
 *  PAGE 2 「흑태양」    : 가면이 검게 물듦 — 흑점 폭발 · 흑태양 낙하(멀리 피하기) · 저주 전염 · 회전 광선 + 1페이지 패턴 강화
 */
public final class KraluBoss {
    private KraluBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Kralu) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.KRALU_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7크랄루는 이미 강림해 있다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(10));
        Kralu k = new Kralu(l, at);
        if (k.body != null) Bosses.ACTIVE.add(k);
    }

    static final class Kralu extends Bosses.Boss {
        static final float MAX = 1300;
        static final double S = 2.2;
        static final int SUN = 0xFFC830, ORANGE = 0xFF7A18, RAG = 0xA01218, VOID = 0x200810, CURSE = 0x7A1030;
        static final String WHO = "Kralu";
        final Random R = new Random();
        final Vec3 home;
        int cd = 40, atk = 0, spinT = 0; double spinA = 0; boolean dying = false;

        Kralu(ServerLevel l, Vec3 at) {
            super(l, "§6§lKralu §7— 저주의 신 강림…", BossEvent.BossBarColor.YELLOW);
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home, S, MAX, "§6Kralu");
            if (body == null) return;
            rig = new BossRig(this, "kralu", S).prop("kralu", Vec3.ZERO, 1.0, true);
            rig.thin = 0.6;
            faceTo(nearest());
            intro();
        }

        void say(String s) { chat(phase == 2 ? "§4<Kralu>" : "§6<Kralu>", s); }
        void skill(String name, int rgb) {
            Holo.banner(l, tag, body.position().add(0, 2.6 * S, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(name, rgb, true) + "]}", 1.1, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§6Kralu §7▸ §f" + name), true);
            sfx("magic", 1.4f, 0.6f + R.nextFloat() * 0.2f);
        }
        @Override float floor() { return MAX * (phase <= 1 ? 0.5f : 0f); }
        Vec3 mask() { return body.position().add(0, 1.75 * S, 0); }
        Vec3 ground(Vec3 q) { return new Vec3(q.x, groundY(q.x, q.y + 3, q.z), q.z); }
        int sunCol() { return phase == 2 ? CURSE : SUN; }
        static boolean nearLine(Vec3 q, Vec3 a, Vec3 b, double r) {
            Vec3 ab = b.subtract(a);
            double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr())));
            return q.distanceTo(a.add(ab.scale(s))) < r;
        }
        void knock(ServerPlayer p, Vec3 from, double k, double up) {
            Vec3 d = p.position().subtract(from).multiply(1, 0, 1);
            if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
            d = d.normalize().scale(k);
            p.setDeltaMovement(d.x, up, d.z); p.hurtMarked = true;
        }
        /** 저주: 위더 + (2페이지) 나약함. */
        void curse(ServerPlayer p, int ticks) {
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, phase == 2 ? 1 : 0, false, true));
            if (phase == 2) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, true));
        }

        // ═════════ 컷신
        void intro() {
            for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(5, 0x200800, 8000, 0.55f)); JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 1200, 1.2f)); }
            rig.floating = true; rig.liftGoal = 5;
            Holo.pillar(l, tag, home, 40, 2.4, SUN, 10, 60, 30);
            Holo.ring(l, tag, home.add(0, 0.1, 0), 1, 9, 48, 0.5, 0.05, ORANGE, 20, 80, 20, Math.PI / 2, 1.5, true);
            sfx("riser", 3f, 0.5f);
            l.playSound(null, home.x, home.y, home.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.5f);
            later(70, () -> { rig.liftGoal = 0; });
            later(90, () -> { rig.floating = false; rig.play("raise", 50); sfx("impact", 2.5f, 0.6f); Holo.dustRing(l, tag, home, 5, 24, 20); });
            scene(180, WHO, new String[]{"…빛을 우러러보는 자여.", "너희가 숭배하던 태양은, 나의 가면에 불과하다.", "그 빛 아래에서 — 저주받아라.", "나는 크랄루. 저주의 신이다."}, 40, 9.0f, 4.0f);
            later(176, () -> {
                phase = 1; cd = 30;
                bar.setName(Component.literal("§6§lKralu §7— PAGE 1 · 태양의 저주"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("Kralu", "PAGE 1 — 태양의 저주", SUN, 1));
                sfx("stinger", 3f, 0.8f);
                music("minecraft:music_disc.ward");
            });
        }
        void page2() {
            stopMusic();
            rig.play("kneel", 100);
            for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(5, 0x000000, 3000, 0.85f)); }
            later(70, () -> {
                rig.dropProp();
                rig.prop("kralu_black", Vec3.ZERO, 1.0, true);
                rig.play("raise", 80);
                bar.setColor(BossEvent.BossBarColor.RED);
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x300008, 12000, 0.5f));
                Holo.orb(l, tag, mask(), 4, VOID, 6, 20, 10);
                Holo.ring(l, tag, mask(), 2, 12, 48, 0.6, 0.05, CURSE, 8, 30, 15, 0, 3, true);
                sfx("deep", 3f, 0.4f);
                l.playSound(null, home.x, home.y, home.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2f, 0.6f);
            });
            scene(220, WHO, new String[]{"…태양이 저문다.", "보아라. 이것이 빛의 진짜 얼굴이다.", "검은 태양 아래, 살아 있는 것은 없다.", "PAGE 2 — 흑태양."}, 48, 9.0f, 4.0f);
            later(216, () -> {
                phase = 2; cd = 20;
                bar.setName(Component.literal("§4§lKralu §7— PAGE 2 · 흑태양"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("Kralu", "PAGE 2 — 흑태양", CURSE, 1));
                sfx("stinger", 3f, 0.5f);
                music("minecraft:music_disc.5");
            });
        }
        void deathScene() {
            dying = true; spinT = 0;
            stopMusic();
            for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 3000, 1.6f)); JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFF4C0, 6000, 0.35f)); }
            rig.play("kneel", 9999);
            sfx("shatter", 3f, 0.5f);
            scene(170, WHO, new String[]{"…가면에, 금이…", "빛도, 저주도… 결국 같은 것이었나.", "기억해라. 해는 다시 뜬다. 그리고 나도…"}, 46, 8.0f, 3.0f);
            later(120, () -> {
                rig.dropProp();
                Holo.shards(l, tag, mask(), "minecraft:gold_block", 26, 6);
                Holo.shards(l, tag, mask(), "minecraft:red_wool", 18, 5);
                Holo.flare(l, tag, mask(), 7, SUN, 14);
                rig.play("fall", 9999);
                BossSfx.play(l, mask(), "boom", 3f, 0.6f);
            });
            later(150, () -> sfx("choir", 2.5f, 0.8f));
            later(168, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            if (phase == 1 && atFloor()) { page2(); return; }
            if (phase == 2 && atFloor()) { deathScene(); return; }
            if (spinT > 0) tickSpin();
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            if (!rig.busy() && spinT == 0) chase(tgt, phase == 2 ? 0.19 : 0.16, 4.4);
            else faceTo(tgt);
            if (phase == 2 && t % 40 == 0) for (ServerPlayer p : players(6)) curse(p, 60);   // 흑태양의 기운
            if (--cd > 0) return;
            double dist = tgt.position().distanceTo(body.position());
            int k = phase == 1 ? (dist < 5.5 ? new int[]{2, 9, 2, 1}[atk++ % 4] : new int[]{0, 1, 3, 0, 4, 2, 3}[atk++ % 7])
                    : (dist < 5.5 ? new int[]{2, 5, 9, 2}[atk++ % 4] : new int[]{0, 6, 3, 7, 1, 8, 4, 5, 0, 7}[atk++ % 10]);
            cd = phase == 2 ? 36 : 46;
            switch (k) {
                case 0 -> sunBeam(tgt);
                case 1 -> brand(tgt);
                case 2 -> lash(tgt);
                case 3 -> orbs(tgt);
                case 4 -> eclipse(tgt);
                case 5 -> sunspot();
                case 6 -> blackSun(tgt);
                case 7 -> contagion(tgt);
                case 8 -> spinStart();
                case 9 -> { melee(tgt, 12f, "swing", 10, 5.4); later(10, () -> sfx("slash", 1.6f, 0.7f)); }
                default -> { }
            }
        }

        /** 태양 광선: 가면에서 예고선 → 굵은 광선. */
        void sunBeam(ServerPlayer tgt) {
            skill("태양 광선", sunCol());
            rig.play("cast", 30);
            Vec3 a = mask(), b = a.add(tgt.position().add(0, 1, 0).subtract(a).normalize().scale(34));
            Holo.beamGlow(l, tag, a, b, 0.1, sunCol(), 0, 20, 0);
            Holo.orb(l, tag, a, 1.4, sunCol(), 20, 2, 4);
            sfx("charge", 2f, 0.9f);
            later(20, () -> {
                Holo.beam(l, tag, a, b, 1.3, sunCol(), 1, 8, 6);
                BossSfx.play(l, a, "laser", 3f, 0.6f);
                for (ServerPlayer p : players(80)) if (nearLine(p.position().add(0, 1, 0), a, b, 1.8)) { hurtP(p, phase == 2 ? 18f : 14f); p.igniteForSeconds(5); curse(p, 60); }
            });
        }
        /** 저주 낙인: 표식 → 3초 뒤 폭발 (주변 동료도 휘말림). */
        void brand(ServerPlayer tgt) {
            skill("저주 낙인", CURSE);
            say(tgt.getName().getString() + "… 너에게 저주를.");
            Holo.reticle(l, tag, tgt, CURSE, 60);
            Holo.bubble(l, tag, () -> tgt.position().add(0, 1, 0), 2.2, CURSE, 60);
            tgt.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            GuardMeta.send(tgt, 5, "저주 낙인 — 3초 뒤 폭발! 동료에게서 떨어져라");
            later(60, () -> {
                if (!tgt.isAlive() || dying) return;
                Vec3 c = tgt.position().add(0, 1, 0);
                Holo.explosion(l, tag, c, 3.5, CURSE);
                BossSfx.play(l, c, "boom", 2f, 0.8f);
                for (ServerPlayer p : players(80)) if (p.position().add(0, 1, 0).distanceTo(c) < 4) { hurtP(p, p == tgt ? 12f : 16f); curse(p, 100); }
            });
        }
        /** 넝마 채찍: 붉은 천이 앞쪽 부채꼴을 두 번 휩씀. */
        void lash(ServerPlayer tgt) {
            skill("넝마 채찍", RAG);
            for (int n = 0; n < 2; n++) {
                final int nn = n;
                later(n * 12, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null || dying) return;
                    faceTo(tt);
                    rig.play(nn == 0 ? "sweep" : "swing", 12);
                    Vec3 c = body.position().add(0, 1.2 * S, 0), f = tt.position().subtract(body.position()).multiply(1, 0, 1).normalize();
                    for (int i = -3; i <= 3; i++) {
                        double a = i * 0.28;
                        Vec3 d = new Vec3(f.x * Math.cos(a) - f.z * Math.sin(a), 0, f.x * Math.sin(a) + f.z * Math.cos(a));
                        Holo.beamLayer(l, tag, c, c.add(d.scale(8)).add(0, -1.2, 0), 0.45, "minecraft:red_wool", -1, 1, 3, 3, a);
                    }
                    sfx("whoosh", 1.8f, 0.6f);
                    for (ServerPlayer p : players(80)) {
                        Vec3 rel = p.position().subtract(body.position()).multiply(1, 0, 1);
                        if (rel.length() < 8.5 && rel.normalize().dot(f) > 0.45) { hurtP(p, 9f); knock(p, body.position(), 1.1, 0.4); }
                    }
                });
            }
        }
        /** 작열 구체 비: 하늘에서 작은 태양이 떨어짐. */
        void orbs(ServerPlayer tgt) {
            skill(phase == 2 ? "흑염 구체 비" : "작열 구체 비", sunCol());
            rig.play("raise", 30);
            int n = phase == 2 ? 14 : 9;
            for (int i = 0; i < n; i++) {
                Vec3 q = ground(tgt.position().add((R.nextDouble() - 0.5) * 12, 0, (R.nextDouble() - 0.5) * 12));
                final int dl = 16 + i * 3;
                Holo.circle(l, tag, q.add(0, 0.1, 0), 1.8, sunCol(), dl, 0);
                Holo.block(l, tag, q.add(0, 16, 0), phase == 2 ? "minecraft:black_concrete" : "minecraft:shroomlight", sunCol(), Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, true), dl + 1, 0)
                        .key(dl - 10, Holo.m(Holo.I(), 1.2, 1.2, 1.2, new Vec3(0, -15.4, 0), true), 10).spin(25);
                later(dl, () -> {
                    Holo.flare(l, tag, q.add(0, 0.6, 0), 2.2, sunCol(), 6);
                    l.sendParticles(ParticleTypes.FLAME, q.x, q.y + 0.4, q.z, 16, 0.6, 0.2, 0.6, 0.04);
                    l.playSound(null, q.x, q.y, q.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.7f);
                    for (ServerPlayer p : players(80)) if (p.position().distanceTo(q) < 2.0) { hurtP(p, 8f); p.igniteForSeconds(3); }
                });
            }
        }
        /** 일식: 어둠이 깔리고 그림자 손이 땅에서 붙잡음. */
        void eclipse(ServerPlayer tgt) {
            skill("일식", VOID);
            say("빛이 사라지면, 남는 것은 저주뿐.");
            rig.play("raise", 40);
            for (ServerPlayer p : players(30)) {
                JjkNet.toPlayer(p, new Payloads.Screen(5, 0x000000, 3500, 0.7f));
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            }
            for (ServerPlayer p : players(30)) {
                for (int i = 0; i < 3; i++) {
                    final int ii = i;
                    later(16 + i * 14, () -> {
                        if (!p.isAlive() || dying) return;
                        Vec3 q = ground(p.position());
                        Holo.circle(l, tag, q.add(0, 0.1, 0), 1.4, VOID, 10, 0);
                        later(10, () -> {
                            for (int f = 0; f < 5; f++) {   // 손가락 5개
                                double a = f * Math.PI * 2 / 5 + ii;
                                Vec3 base = q.add(Math.cos(a) * 0.8, 0, Math.sin(a) * 0.8);
                                Holo.beamLayer(l, tag, base.add(0, -0.3, 0), base.add(Math.cos(a) * -0.4, 2.2, Math.sin(a) * -0.4), 0.3, "minecraft:black_concrete", CURSE, 2, 8, 6, a);
                            }
                            for (ServerPlayer pp : players(80)) if (pp.position().distanceTo(q) < 1.6) {
                                hurtP(pp, 7f); curse(pp, 60);
                                pp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, false, false));
                            }
                            l.playSound(null, q.x, q.y, q.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8f, 0.6f);
                        });
                    });
                }
            }
        }
        /** 흑점 폭발: 몸에서 검은 충격파 3겹. */
        void sunspot() {
            skill("흑점 폭발", CURSE);
            rig.play("raise", 26);
            guardFor(20);
            Holo.orb(l, tag, mask(), 2.5, VOID, 20, 4, 6);
            sfx("charge", 2.5f, 0.5f);
            for (int w = 0; w < 3; w++) {
                final double r = 4 + w * 3;
                later(20 + w * 6, () -> {
                    Holo.shock(l, tag, body.position().add(0, 0.2, 0), r, CURSE, 10);
                    BossSfx.play(l, body.position(), "boom", 2f, 0.7f + (float) r * 0.03f);
                    for (ServerPlayer p : players(80)) {
                        double d = p.position().distanceTo(body.position());
                        if (d < r && d > r - 3.2) { hurtP(p, 10f); knock(p, body.position(), 1.2, 0.5); curse(p, 60); }
                    }
                });
            }
        }
        /** 흑태양 낙하: 거대한 검은 태양이 내려옴 → 착지 반경 9 큰 피해 (멀리 도망). */
        void blackSun(ServerPlayer tgt) {
            skill("흑태양 낙하", CURSE);
            say("모두 함께, 그림자가 되어라.");
            Vec3 q = ground(tgt.position());
            caption("§4흑태양이 떨어진다!\n§f원 밖으로 도망쳐라");
            Holo.circle(l, tag, q.add(0, 0.1, 0), 9, CURSE, 50, 0);
            Holo.circle(l, tag, q.add(0, 0.12, 0), 6, VOID, 50, 0);
            Holo.orb(l, tag, q.add(0, 24, 0), 5, VOID, 10, 34, 6);
            Holo.block(l, tag, q.add(0, 24, 0), "minecraft:black_concrete", CURSE, Holo.m(Holo.I(), 5, 5, 5, Vec3.ZERO, true), 52, 0)
                    .key(14, Holo.m(Holo.I(), 6, 6, 6, new Vec3(0, -21, 0), true), 36).spin(6);
            sfx("riser", 3f, 0.4f);
            later(50, () -> {
                Holo.explosion(l, tag, q.add(0, 1, 0), 8, CURSE);
                Holo.shock(l, tag, q.add(0, 0.2, 0), 10, VOID, 14);
                BossSfx.play(l, q, "boom", 4f, 0.4f);
                for (ServerPlayer p : players(80)) if (p.position().distanceTo(q) < 9) { hurtP(p, 26f); knock(p, q, 1.8, 0.8); curse(p, 120); }
            });
        }
        /** 저주 전염: 저주 덩어리가 플레이어 사이를 튀어 다님 (가까이 붙어 있으면 연쇄). */
        void contagion(ServerPlayer tgt) {
            skill("저주 전염", CURSE);
            jump(tgt, body.position().add(0, 2 * S, 0), 4, new HashSet<>());
        }
        void jump(ServerPlayer to, Vec3 from, int left, Set<ServerPlayer> done) {
            if (to == null || left <= 0 || dying) return;
            done.add(to);
            Vec3 b = to.position().add(0, 1, 0);
            Holo.beamGlow(l, tag, from, b, 0.4, CURSE, 0, 4, 6);
            Holo.flare(l, tag, b, 1.5, CURSE, 6);
            hurtP(to, 7f); curse(to, 80);
            l.playSound(null, b.x, b.y, b.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1f, 0.8f);
            ServerPlayer next = null; double bd = 7;
            for (ServerPlayer p : players(80)) if (!done.contains(p)) { double d = p.position().distanceTo(to.position()); if (d < bd) { bd = d; next = p; } }
            final ServerPlayer nx = next;
            if (nx != null) later(8, () -> jump(nx, b, left - 1, done));
        }
        /** 회전 광선: 가면에서 광선 4줄이 3초간 회전 (넘거나 피하기). */
        void spinStart() {
            skill("회전 광선", CURSE);
            rig.play("raise", 70);
            guardFor(20);
            spinT = 70; spinA = R.nextDouble() * Math.PI;
            sfx("charge", 2.5f, 0.7f);
        }
        void tickSpin() {
            spinT--;
            if (spinT > 56) return;   // 예열
            spinA += 0.07;
            Vec3 c = body.position().add(0, 1.0, 0);
            for (int i = 0; i < 4; i++) {
                double a = spinA + i * Math.PI / 2;
                Vec3 b = c.add(Math.cos(a) * 16, 0, Math.sin(a) * 16);
                if (spinT % 2 == 0) Holo.beamGlow(l, tag, c, b, 0.6, CURSE, 0, 2, 1);
                for (ServerPlayer p : players(80)) if (p.getY() - c.y < 0.9 && nearLine(p.position().add(0, 0.5, 0), c, b, 0.9) && t % 5 == 0) { hurtP(p, 6f); curse(p, 40); }
            }
            if (spinT % 10 == 0) BossSfx.play(l, c, "laser", 1.5f, 0.5f);
        }

        @Override void onDeath() {
            caption("§6§lKralu 격파\n§f「해는 다시 뜬다…」");
            for (ServerPlayer p : players(80)) { GuardMeta.send(p, 4, "태양의 가면이 깨졌다"); p.removeEffect(MobEffects.WITHER); }
            giveCmd("jjk:ugh_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:chosen_light 1");
            giveCmd("minecraft:gold_block 16");
            giveCmd("minecraft:netherite_ingot 2");
            giveCmd("minecraft:totem_of_undying 1");
            BossSfx.play(l, body.position(), "choir", 3f, 1.0f);
        }
        @Override void onLeave() { say("…도망쳐도 소용없다. 해가 뜨는 곳이면 어디든."); }
    }
}
