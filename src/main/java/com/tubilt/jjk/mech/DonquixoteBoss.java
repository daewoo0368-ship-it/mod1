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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 돈키호테의 열쇠 → 보스전 「돈키호테」 — 라 만차의 늙은 편력기사 (가상 체력 1200, 2페이지). 처치 시 worldview.code.
 *  몸: 디스플레이 인형 (금빛 대야 투구·흰 수염·녹슨 갑옷) + 애마 로시난테 + 기사의 창.
 *  PAGE 1 「편력기사」: 창 돌격 · 3연 찌르기 · 맘브리노의 투구 · 기사도 소설 비 · 로시난테 뒷발차기 · 종자 산초 판사(살아 있으면 회복)
 *  PAGE 2 「풍차 거인」: 투기장에 풍차 3기 (날개에 닿으면 피해) — 풍차를 거인으로 착각해 돌격,
 *                       부딪히면 풍차가 부서지고 돈키호테 기절 (받는 피해 1.5배) · 둘시네아의 함성
 */
public final class DonquixoteBoss {
    private DonquixoteBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Don) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DONQUIXOTE_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7돈키호테는 이미 모험 중이다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(10));
        Don d = new Don(l, at);
        if (d.body != null) Bosses.ACTIVE.add(d);
    }

    static final class Don extends Bosses.Boss {
        static final float MAX = 1200;
        static final double S = 1.2, HB = 1.6;
        static final int GOLD = 0xFFD050, RED = 0xE03030, SKY = 0x9AD0FF, DUST = 0xC8B48C;
        static final int LIFE = 20 * 60 * 30;
        static final String WHO = "Don Quixote";
        final Random R = new Random();
        final Vec3 home;
        int cd = 40, atk = 0, stunT = 0, rebuildT = 0; boolean dying = false, charging = false;
        Mob sancho;
        final List<Mill> mills = new ArrayList<>();

        Don(ServerLevel l, Vec3 at) {
            super(l, "§6§lDon Quixote §7— 편력기사 등장…", BossEvent.BossBarColor.YELLOW);
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home, HB, MAX, "§6Don Quixote");
            if (body == null) return;
            rig = new BossRig(this, "donquixote", S);
            rig.ride("rocinante", 1.0);
            rig.lance("lance");
            faceTo(nearest());
            intro();
        }

        void say(String s) { chat("§6<Don Quixote>", s); }
        void skill(String name, int rgb) {
            Holo.banner(l, tag, body.position().add(0, 3.6 * S, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(name, rgb, true) + "]}", 1.0, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§6Don Quixote §7▸ §f" + name), true);
            sfx("whoosh", 1.2f, 1.0f + R.nextFloat() * 0.3f);
        }
        void neigh(float pitch) { l.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 2f, pitch); }
        @Override float floor() { return MAX * (phase <= 1 ? 0.5f : 0f); }
        @Override float adjust(DamageSource src, float amt) { return stunT > 0 ? amt * 1.5f : amt; }
        Vec3 chest() { return body.position().add(0, 2.9 * S, 0); }
        Vec3 ground(Vec3 q) { return new Vec3(q.x, groundY(q.x, q.y + 3, q.z), q.z); }
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

        // ═════════ 컷신
        void intro() {
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x302010, 7000, 0.35f));
            Holo.smoke(l, tag, home.add(0, 0.3, 0), 14, 2.5, DUST, 0.6, 40);
            neigh(0.9f);
            rig.play("raise", 50);
            later(30, () -> { l.playSound(null, home.x, home.y, home.z, SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 2f, 0.9f); });
            scene(170, WHO, new String[]{"멈춰라, 거기 수상한 자여!", "나는 라 만차의 편력기사, 돈키호테!", "그대에게서 사악한 마법사의 기운이 느껴지는구나.", "둘시네아 공주의 이름으로 — 결투를 청하노라!"}, 38, 6.0f, 2.6f);
            later(166, () -> {
                phase = 1; cd = 30;
                bar.setName(Component.literal("§6§lDon Quixote §7— PAGE 1 · 편력기사"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("Don Quixote", "PAGE 1 — 편력기사", GOLD, 1));
                sfx("stinger", 3f, 1.0f);
                music("minecraft:music_disc.precipice");
            });
        }
        void page2() {
            stopMusic();
            if (sancho != null && sancho.isAlive()) sancho.discard();
            rig.play("point", 120);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x101828, 9000, 0.4f));
            later(50, () -> { buildMills(); sfx("deep", 3f, 0.7f); });
            scene(200, WHO, new String[]{"…산초! 저기를 보아라!", "팔이 넷 달린 거대한 거인들이 몰려온다!", "겁먹지 마라. 이것은 정의로운 싸움이다!", "PAGE 2 — 풍차 거인."}, 44, 6.0f, 2.6f);
            later(196, () -> {
                phase = 2; cd = 20;
                bar.setName(Component.literal("§c§lDon Quixote §7— PAGE 2 · 풍차 거인"));
                bar.setColor(BossEvent.BossBarColor.RED);
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("Don Quixote", "PAGE 2 — 풍차 거인", RED, 1));
                sfx("stinger", 3f, 0.7f);
                music("minecraft:music_disc.creator");
            });
        }
        void deathScene() {
            dying = true; stunT = 0;
            stopMusic();
            for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 3000, 1.4f)); JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFF0D0, 6000, 0.3f)); }
            neigh(0.6f);
            Holo.smoke(l, tag, body.position().add(0, 0.5, 0), 12, 2.0, DUST, 0.5, 30);
            rig.ride(null, 1.0);
            rig.lance(null);
            rig.play("fall", 9999);
            BossSfx.play(l, body.position(), "impact", 2f, 0.6f);
            scene(170, WHO, new String[]{"…산초. 이제야 보이는구나.", "저건 거인이 아니라… 그저 풍차였어.", "나는 기사 돈키호테가 아니라… 꿈을 꾸던 늙은이였네.", "그래도… 즐거운 모험이었지."}, 40, 5.0f, 1.4f);
            later(140, () -> {
                Holo.flare(l, tag, body.position().add(0, 0.6, 0), 5, GOLD, 12);
                Holo.shards(l, tag, body.position().add(0, 0.6, 0), "minecraft:gold_block", 18, 4);
                sfx("choir", 2f, 1.0f);
            });
            later(168, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            if (phase == 1 && atFloor() && !charging) { page2(); return; }
            if (phase == 2 && atFloor() && !charging) { deathScene(); return; }
            tickMills();
            if (sancho != null) {
                if (!sancho.isAlive()) { sancho = null; say("산초! 이 비겁한 마법사 놈들!"); }
                else if (t % 20 == 0) {
                    healV(MAX * 0.004f);
                    l.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 3.2 * S, body.getZ(), 2, 0.4, 0.2, 0.4, 0);
                }
            }
            if (stunT > 0) {
                if (--stunT % 10 == 0) l.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 3.8 * S, body.getZ(), 6, 0.4, 0.1, 0.4, 0.1);
                if (stunT == 0) { rig.play("idle", 1); say("…크흠. 거인 놈, 제법이구나!"); }
                return;
            }
            if (charging) return;
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            if (!rig.busy()) chase(tgt, phase == 2 ? 0.26 : 0.22, 3.2);
            else faceTo(tgt);
            if (--cd > 0) return;
            double dist = tgt.position().distanceTo(body.position());
            boolean behind = isBehind(tgt);
            int k;
            if (behind && dist < 5) k = 4;
            else if (phase == 1) k = dist < 4 ? new int[]{1, 9, 1, 3}[atk++ % 4] : new int[]{0, 2, 5, 0, 3, 2, 5}[atk++ % 7];
            else {
                boolean millUp = mills.stream().anyMatch(m -> !m.broken);
                k = dist < 4 ? new int[]{1, 7, 9, 1}[atk++ % 4] : new int[]{0, 6, 5, 2, 7, 0, 6, 5}[atk++ % 8];
                if (k == 6 && !millUp) k = 0;
            }
            cd = phase == 2 ? 36 : 44;
            switch (k) {
                case 0 -> charge(tgt);
                case 1 -> thrusts(tgt);
                case 2 -> helmet(tgt);
                case 3 -> callSancho(tgt);
                case 4 -> rearKick();
                case 5 -> books(tgt);
                case 6 -> millCharge();
                case 7 -> dulcinea();
                case 9 -> { melee(tgt, 9f, "swing", 9, 4.2); later(9, () -> sfx("slash", 1.5f, 0.9f)); }
                default -> { }
            }
        }
        boolean isBehind(ServerPlayer p) {
            double yr = Math.toRadians(body.getYRot());
            Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
            return p.position().subtract(body.position()).multiply(1, 0, 1).normalize().dot(fwd) < -0.5;
        }

        /** 창 돌격: 땅에 예고선 → 로시난테로 일직선 질주. */
        void charge(ServerPlayer tgt) {
            skill("창 돌격", RED);
            faceTo(tgt);
            Vec3 a = body.position(), dir = tgt.position().subtract(a).multiply(1, 0, 1).normalize();
            Vec3 b = a.add(dir.scale(phase == 2 ? 20 : 16));
            Holo.beamGlow(l, tag, a.add(0, 0.15, 0), b.add(0, 0.15, 0), 1.6, RED, 0, 18, 0);
            neigh(1.1f);
            rig.play("charge", 36);
            dash(a, b, 18, 12, phase == 2 ? 16f : 13f, null);
        }
        /** a→b 질주 (delay 후 n틱). 끝나면 onEnd. */
        void dash(Vec3 a, Vec3 b, int delay, int n, float dmg, Runnable onEnd) {
            charging = true;
            Set<ServerPlayer> hit = new HashSet<>();
            later(delay, () -> l.playSound(null, a.x, a.y, a.z, SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 2.5f, 1.2f));
            for (int i = 0; i < n; i++) {
                final double u = (i + 1) / (double) n;
                later(delay + i, () -> {
                    if (dying) return;
                    Vec3 q = ground(a.lerp(b, u));
                    moveTo(q);
                    Holo.smoke(l, tag, q.add(0, 0.2, 0), 2, 1.2, DUST, 0.3, 14);
                    for (ServerPlayer p : players(80)) if (!hit.contains(p) && p.position().distanceTo(q) < 2.4) {
                        hit.add(p); hurtP(p, dmg); knock(p, q, 1.4, 0.5);
                        BossSfx.play(l, q, "impact", 1.6f, 1.0f);
                    }
                });
            }
            later(delay + n, () -> { charging = false; if (onEnd != null && !dying) onEnd.run(); });
        }
        /** 3연 찌르기. */
        void thrusts(ServerPlayer tgt) {
            skill("기사의 3연창", GOLD);
            for (int i = 0; i < 3; i++) {
                later(i * 9, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null) return;
                    faceTo(tt);
                    rig.play("point", 8);
                    Vec3 a = chest().subtract(0, 0.8, 0), b = a.add(tt.position().add(0, 1, 0).subtract(a).normalize().scale(6));
                    Holo.beamGlow(l, tag, a, b, 0.25, 0xFFFFFF, 0, 2, 3);
                    sfx("slash", 1.2f, 1.5f);
                    for (ServerPlayer p : players(80)) if (nearLine(p.position().add(0, 1, 0), a, b, 1.2)) hurtP(p, 6f);
                });
            }
        }
        /** 맘브리노의 투구: 금 대야 투구가 부메랑처럼 날아갔다 돌아옴. */
        void helmet(ServerPlayer tgt) {
            skill("맘브리노의 투구", GOLD);
            say("보아라, 전설의 맘브리노 투구다!");
            rig.play("swing", 14);
            Vec3 a = chest(), b = tgt.position().add(0, 1.2, 0);
            Vec3 d = b.subtract(a);
            Holo.item(l, tag, a, "minecraft:golden_helmet", true, GOLD, Holo.m(Holo.I(), 1.4, 1.4, 1.4, Vec3.ZERO, true), 34, 0)
                    .key(2, Holo.m(Holo.I(), 1.4, 1.4, 1.4, d, true), 12)
                    .key(16, Holo.m(Holo.I(), 1.4, 1.4, 1.4, Vec3.ZERO, true), 14).spin(45);
            Set<ServerPlayer> hit = new HashSet<>();
            for (int i = 0; i < 28; i += 2) {
                final double u = i < 14 ? i / 14.0 : 1 - (i - 14) / 14.0;
                later(2 + i, () -> {
                    Vec3 q = a.add(d.scale(u));
                    for (ServerPlayer p : players(80)) if (!hit.contains(p) && p.position().add(0, 1, 0).distanceTo(q) < 1.8) { hit.add(p); hurtP(p, 10f); }
                });
            }
            later(2, () -> sfx("whoosh", 1.5f, 1.6f));
        }
        /** 종자 산초 판사: 살아 있는 동안 주인을 치료. */
        void callSancho(ServerPlayer tgt) {
            if (sancho != null && sancho.isAlive()) { thrusts(tgt); return; }
            skill("종자 산초 판사", 0x60C060);
            say("산초! 내 상처를 돌보아라!");
            Vec3 q = ground(body.position().add(body.position().subtract(tgt.position()).multiply(1, 0, 1).normalize().scale(5)));
            Mob m = spawn(EntityType.ZOMBIE_VILLAGER, q, 45, 1.0, false);
            if (m == null) return;
            m.setCustomName(Component.literal("§e§l산초 판사 §7(돈키호테 회복 중)"));
            m.setCustomNameVisible(true);
            m.addTag("jjk.boss");
            m.setTarget(tgt);
            m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
            adds.add(m);
            sancho = m;
            Holo.smoke(l, tag, q.add(0, 0.5, 0), 8, 1.4, DUST, 0.5, 20);
            caption("§e산초 판사가 돈키호테를 치료한다!\n§f먼저 쓰러뜨려라");
        }
        /** 로시난테 뒷발차기: 뒤에 선 자를 걷어참. */
        void rearKick() {
            skill("로시난테 뒷발차기", DUST);
            neigh(1.3f);
            rig.play("guard", 12);
            later(6, () -> {
                double yr = Math.toRadians(body.getYRot());
                Vec3 back = body.position().subtract(new Vec3(-Math.sin(yr), 0, Math.cos(yr)).scale(2.2));
                Holo.dustRing(l, tag, back, 2.5, 14, 14);
                BossSfx.play(l, back, "impact", 2f, 0.8f);
                for (ServerPlayer p : players(80)) if (p.position().distanceTo(back) < 3.2) { hurtP(p, 12f); knock(p, body.position(), 1.8, 0.7); }
            });
        }
        /** 기사도 소설 비: 하늘에서 책이 쏟아짐 (원형 예고). */
        void books(ServerPlayer tgt) {
            skill("기사도 소설 낙하", 0xC08040);
            say("이 모든 이야기가 진실이란 말이다!");
            rig.play("raise", 26);
            int n = phase == 2 ? 14 : 9;
            for (int i = 0; i < n; i++) {
                Vec3 q = ground(tgt.position().add((R.nextDouble() - 0.5) * 10, 0, (R.nextDouble() - 0.5) * 10));
                final int dl = 16 + i * 2;
                Holo.circle(l, tag, q.add(0, 0.1, 0), 1.4, GOLD, dl, 0);
                Holo.item(l, tag, q.add(0, 14, 0), "minecraft:written_book", true, -1, Holo.m(Holo.I(), 1.8, 1.8, 1.8, Vec3.ZERO, true), dl + 2, 0)
                        .key(dl - 12, Holo.m(Holo.I(), 1.8, 1.8, 1.8, new Vec3(0, -13.4, 0), true), 12).spin(30);
                later(dl, () -> {
                    Holo.flare(l, tag, q.add(0, 0.4, 0), 1.3, GOLD, 5);
                    l.playSound(null, q.x, q.y, q.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.5f, 0.6f);
                    l.sendParticles(ParticleTypes.ENCHANT, q.x, q.y + 0.5, q.z, 20, 0.5, 0.5, 0.5, 0.5);
                    for (ServerPlayer p : players(80)) if (p.position().distanceTo(q) < 1.6) hurtP(p, 8f);
                });
            }
        }
        /** 둘시네아의 함성: 주변 충격파 + 잠깐 가속. */
        void dulcinea() {
            skill("둘시네아여, 보고 계십니까!", 0xFF80B0);
            rig.play("raise", 20);
            neigh(0.8f);
            later(8, () -> {
                Holo.shock(l, tag, body.position().add(0, 0.2, 0), 7, GOLD, 12);
                BossSfx.play(l, body.position(), "boom", 2f, 1.2f);
                for (ServerPlayer p : players(80)) if (p.position().distanceTo(body.position()) < 7) { hurtP(p, 8f); knock(p, body.position(), 1.6, 0.6); }
            });
            cd = 24;
        }

        // ═════════ 풍차 (PAGE 2)
        final class Mill {
            final Vec3 base, hub, n, side;
            final List<Holo.Elem> parts = new ArrayList<>(), sails = new ArrayList<>(), spars = new ArrayList<>();
            boolean broken; double ang;
            Mill(Vec3 base) {
                this.base = base;
                Vec3 toC = home.subtract(base).multiply(1, 0, 1).normalize();
                n = toC; side = new Vec3(-toC.z, 0, toC.x);
                hub = base.add(0, 9.2, 0).add(toC.scale(1.9));
                ang = R.nextDouble() * Math.PI;
                parts.add(Holo.block(l, tag, base, "minecraft:stone_bricks", -1, Holo.m(Holo.I(), 3.2, 9, 3.2, new Vec3(0, 4.5, 0), true), LIFE, 0));
                parts.add(Holo.block(l, tag, base, "minecraft:spruce_planks", -1, Holo.m(Holo.I(), 3.8, 1.4, 3.8, new Vec3(0, 9.6, 0), true), LIFE, 0));
                parts.add(Holo.block(l, tag, base, "minecraft:dark_oak_planks", -1, Holo.m(Holo.I(), 2.6, 1.2, 2.6, new Vec3(0, 10.8, 0), true), LIFE, 0));
                parts.add(Holo.block(l, tag, base, "minecraft:dark_oak_planks", -1, Holo.m(Holo.I(), 1.4, 1.0, 1.4, new Vec3(0, 11.9, 0), true), LIFE, 0));
                parts.add(Holo.block(l, tag, base, "minecraft:spruce_door", -1, Holo.m(Holo.I(), 1.0, 2.0, 0.2, toC.scale(1.62).add(0, 1.0, 0), true), LIFE, 0));
                parts.add(Holo.block(l, tag, hub, "minecraft:dark_oak_log", -1, Holo.m(Holo.I(), 0.8, 0.8, 0.8, Vec3.ZERO, true), LIFE, 0));
                for (int i = 0; i < 4; i++) {
                    sails.add(Holo.block(l, tag, hub, "minecraft:white_wool", -1, Holo.zero(true), LIFE, 0));
                    spars.add(Holo.block(l, tag, hub, "minecraft:dark_oak_planks", -1, Holo.zero(true), LIFE, 0));
                }
                Holo.smoke(l, tag, base.add(0, 0.5, 0), 12, 3, DUST, 0.8, 30);
                Holo.dustRing(l, tag, base, 3, 18, 20);
            }
            Vec3 dir(int i) { double a = ang + i * Math.PI / 2; return side.scale(Math.cos(a)).add(0, Math.sin(a), 0); }
            float[] bladeM(Vec3 x, double len, double w, double h, double off) {
                Vec3 z = n.cross(x).normalize(), y = z.cross(x).normalize();
                double[][] r = {{x.x, y.x, z.x}, {x.y, y.y, z.y}, {x.z, y.z, z.z}};
                return Holo.m(r, len, h, w, x.scale(len / 2 + 0.5).add(z.scale(off)).add(n.scale(0.25)), true);
            }
            void tick(double speed) {
                if (broken) return;
                ang += speed;
                for (int i = 0; i < 4; i++) {
                    Vec3 d = dir(i);
                    sails.get(i).keyframe(bladeM(d, 6.0, 1.7, 0.08, 1.0), 0);
                    spars.get(i).keyframe(bladeM(d, 7.0, 0.22, 0.22, 0), 0);
                }
                if (t % 2 == 0) for (ServerPlayer p : players(80)) {
                    Vec3 q = p.position().add(0, 0.9, 0);
                    for (int i = 0; i < 4; i++) if (nearLine(q, hub, hub.add(dir(i).scale(7.4)), 1.1)) {
                        hurtP(p, 6f); knock(p, hub, 1.3, 0.5);
                        l.playSound(null, q.x, q.y, q.z, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.2f, 0.7f);
                        break;
                    }
                }
            }
            void breakDown() {
                if (broken) return;
                broken = true;
                for (int i = 0; i < 4; i++) {   // 날개가 뜯겨 날아감
                    Vec3 d = dir(i).add(n.scale(-0.6)).normalize();
                    sails.get(i).keyIn(1, Holo.m(Holo.rotY(R.nextDouble() * 3), 1.7, 6.0, 0.08, d.scale(9).add(0, -6, 0), true), 14);
                    spars.get(i).keyIn(1, Holo.m(Holo.rotY(R.nextDouble() * 3), 0.22, 7.0, 0.22, d.scale(9).add(0, -6, 0), true), 14);
                    sails.get(i).endIn(16); spars.get(i).endIn(16);
                }
                for (Holo.Elem e : parts) e.endIn(2);
                Holo.shards(l, tag, base.add(0, 4, 0), "minecraft:stone_bricks", 24, 6);
                Holo.shards(l, tag, hub, "minecraft:white_wool", 14, 6);
                Holo.explosion(l, tag, base.add(0, 3, 0), 4, DUST);
                Holo.smoke(l, tag, base.add(0, 1, 0), 16, 3.5, DUST, 1.0, 40);
                for (ServerPlayer p : players(80)) if (p.position().distanceTo(base) < 6) { hurtP(p, 10f); knock(p, base, 1.6, 0.7); }
            }
            void remove() { for (Holo.Elem e : parts) e.kill(); for (Holo.Elem e : sails) e.kill(); for (Holo.Elem e : spars) e.kill(); }
        }
        void buildMills() {
            for (Mill m : mills) m.remove();
            mills.clear();
            double a0 = R.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 3; i++) {
                double a = a0 + i * Math.PI * 2 / 3;
                mills.add(new Mill(ground(home.add(Math.cos(a) * 14, 0, Math.sin(a) * 14))));
            }
            BossSfx.play(l, home, "deep", 3f, 0.6f);
        }
        void tickMills() {
            if (mills.isEmpty()) return;
            double sp = phase == 2 ? (0.05 + (1 - frac() / 0.5) * 0.05) : 0.04;
            for (Mill m : mills) m.tick(sp);
            if (phase == 2 && !dying && mills.stream().allMatch(m -> m.broken)) {
                if (++rebuildT == 160) { rebuildT = 0; say("거인들이 또 나타났다! 마법사 프레스톤의 짓이구나!"); buildMills(); }
            }
        }
        /** 풍차 돌격: 풍차를 거인으로 보고 돌진 → 풍차 파괴 + 본인 기절. 경로에 서 있으면 큰 피해. */
        void millCharge() {
            Mill best = null; double bd = 1e9;
            for (Mill m : mills) if (!m.broken) { double d = m.base.distanceTo(body.position()); if (d < bd) { bd = d; best = m; } }
            if (best == null) return;
            final Mill mill = best;
            skill("거인이다! 돌격!", RED);
            say("비겁하게 도망치지 마라, 이 거인 놈아!");
            Vec3 a = body.position(), dir = mill.base.subtract(a).multiply(1, 0, 1).normalize();
            Vec3 b = mill.base.subtract(dir.scale(1.6));
            body.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            Holo.beamGlow(l, tag, a.add(0, 0.15, 0), ground(b).add(0, 0.15, 0), 2.0, RED, 0, 24, 0);
            caption("§c돈키호테가 풍차로 돌격한다!\n§f경로에서 피해라 — 부딪히면 기절한다");
            neigh(1.0f);
            rig.play("charge", 44);
            int n = Math.max(8, (int) (a.distanceTo(b) / 1.3));
            dash(a, b, 24, n, 18f, () -> {
                mill.breakDown();
                stunT = 80;
                rig.play("guard", 80);
                BossSfx.play(l, body.position(), "boom", 3f, 0.7f);
                caption("§e§l돈키호테 기절!\n§f받는 피해 1.5배");
                say("으아악…! 이, 이 거인 놈의 팔이 어찌나 단단한지…");
            });
        }

        @Override void onDeath() {
            caption("§6§lDon Quixote 격파\n§f「즐거운 모험이었지」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "기사의 꿈이 끝났다");
            giveCmd("jjk:worldview_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("minecraft:golden_helmet[minecraft:custom_name={\"text\":\"맘브리노의 투구\",\"color\":\"gold\",\"italic\":false},minecraft:enchantment_glint_override=true] 1");
            giveCmd("minecraft:diamond_block 4");
            giveCmd("minecraft:emerald_block 4");
            BossSfx.play(l, body.position(), "choir", 3f, 1.0f);
        }
        @Override void onLeave() { say("…다들 어디로 갔지? 산초, 다음 모험을 떠나자꾸나."); }
        @Override void cleanup() {
            for (Mill m : mills) m.remove();
            mills.clear();
            super.cleanup();
        }
    }
}
