package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Random;

/**
 * 차원 신호기 (설치형) — 바라보는 땅에 설치 → 컷신: 신호기가 하늘로 빛을 쏘고 차원 균열이 열림 →
 *  「파괴자」 「몰살자」 「파수꾼」 중 하나가 내려옴. 쓰러뜨리면 균열이 닫히고 dimension.code 드랍.
 *  파괴자: 4블록 거구 · 내려찍기 · 지면 파동 · 돌진 · 운석 · 균열 분출 (50% 분노: 용암 오라)
 *  몰살자: 보라 암살자 · 칼날 3연격 · 등 뒤 기습 · X자 공허 참격 · 몰살 표식 · 잔상 폭발 (50%: 균열에서 칼날 비)
 *  파수꾼: 떠다니는 흰 기사 · 창 찌르기 · 방패 반격 · 심판의 빛 기둥 · 감시 구체 · 눈 레이저 (쏘고 나면 과열 = 약점)
 */
public final class DimensionBoss {
    private DimensionBoss() {}

    static final Random R = new Random();
    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Dim) return true; return false; }
    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DIMENSION_BEACON.get()); }

    /** 설치: 바라보는 블록 위 (6칸 안), 없으면 발 앞. true = 설치됨(아이템 소모). */
    public static boolean place(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§5차원 균열이 이미 열려 있다"), true); return false; }
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(6));
        var hit = l.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2)) : hit.getLocation();
        at = new Vec3(Math.floor(at.x) + 0.5, Math.round(at.y), Math.floor(at.z) + 0.5);
        int k = R.nextInt(3);
        Dim d = k == 0 ? new Destroyer(l, at) : k == 1 ? new Exterminator(l, at) : new Sentinel(l, at);
        if (d.body == null) return false;
        Bosses.ACTIVE.add(d);
        return true;
    }

    static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }

    // ═════════════════════════ 공통: 신호기 · 균열 · 등장/퇴장 컷신
    abstract static class Dim extends Bosses.Boss {
        final Vec3 beacon, rift, land;
        final String riftTag = "jjk.rift" + (Holo.uid++);
        final String who; final int col; final double S; final float MAX;
        int cd = 40, atk = 0; boolean dying = false, enraged = false;

        Dim(ServerLevel l, Vec3 beacon, String who, String title, int col, BossEvent.BossBarColor bc, String skin, double S, float max, String held) {
            super(l, title, bc);
            this.who = who; this.col = col; this.S = S; this.MAX = max;
            phase = 0;
            this.beacon = beacon;
            this.rift = beacon.add(0, 22, 0);
            Vec3 off = new Vec3(R.nextDouble() - 0.5, 0, R.nextDouble() - 0.5);
            off = off.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : off.normalize();
            Vec3 lz = beacon.add(off.scale(6));
            this.land = new Vec3(lz.x, groundY(lz.x, beacon.y + 3, lz.z), lz.z);
            body = hurtbox(rift, S, max, "§l" + who);
            if (body == null) return;
            rig = new BossRig(this, skin, S);
            if (held != null) rig.hold(held);
            rig.hide(true);
            camFocus = beacon.add(0, 8, 0);
            summonScene();
        }

        void say(String s) { for (ServerPlayer p : players(90)) p.sendSystemMessage(Component.literal("§5<" + who + "> §f" + s)); }
        void skill(String name) {
            Holo.banner(l, tag, body.position().add(0, 2.7 * S, 0), banner(name, col), 1.1, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§5" + who + " §7▸ §f" + name), true);
        }
        abstract String[] introLines();
        abstract String[] deathLines();
        abstract String music();
        abstract void fight(ServerPlayer tg, double dist);

        void summonScene() {
            // 신호기 설치
            Holo.model(l, riftTag, beacon, "jjk:boss/prop/beacon", Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 99999999, 1, 0.8f)
                    .key(2, Holo.m(Holo.I(), 1.3, 1.3, 1.3, new Vec3(0, 0.43, 0), false), 8);
            BossSfx.play(l, beacon, "block", 2f, 0.6f);
            for (ServerPlayer p : players(90)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x100018, 9000, 0.6f));
            later(20, () -> { BossSfx.play(l, beacon, "charge", 3f, 0.5f); Holo.pillar(l, riftTag, beacon.add(0, 1, 0), 22, 0.5, 0xD050FF, 30, 9999, 10); });
            later(30, () -> caption("§5§l⚠ 차원 균열 감지\n§7신호기가 다른 차원과 연결되고 있다…"));
            // 균열: 고리 3겹 + 섬광 + 번개
            later(50, () -> {
                BossSfx.play(l, rift, "deep", 3f, 0.5f);
                BossSfx.play(l, rift, "thunder", 3f, 0.7f);
                Titan.shake(l, beacon, 60, true);
                for (int i = 0; i < 3; i++) Holo.circle(l, riftTag, rift.add(0, i * 0.3, 0), 5 + i * 2.5, i == 1 ? 0xFFFFFF : 0xB040FF, 99999999, 0);
                Holo.flare(l, riftTag, rift, 7, 0x8020FF, 99999999);
                Holo.flare(l, tag, rift, 14, 0xFFFFFF, 10);
                for (int i = 0; i < 6; i++) { final int ii = i; later(i * 4, () -> Holo.lightning(l, tag, rift, rift.add((R.nextDouble() - 0.5) * 20, -10 - ii * 2, (R.nextDouble() - 0.5) * 20), 0xD050FF, 0.15, 6)); }
            });
            // 보스 강림: 균열에서 떨어져 착지
            later(90, () -> { rig.hide(false); rig.play("raise", 40); BossSfx.play(l, rift, "stinger", 3f, 0.6f); });
            for (int i = 1; i <= 20; i++) { final int k = i; later(90 + i, () -> moveTo(rift.lerp(land, k / 20.0))); }
            later(111, () -> { Holo.explosion(l, tag, land, 4, col); Holo.shock(l, tag, land, 18, col, 14); Titan.shake(l, land, 60, true); camFocus = land; rig.play("kneel", 30); });
            scene(220, who, introLines(), 40, 6f + (float) S, 1.6f * (float) S);
            later(140, () -> rig.play("laugh", 40));
            later(216, () -> {
                phase = 1; cd = 30; camFocus = null;
                for (ServerPlayer p : players(90)) JjkNet.toPlayer(p, new Payloads.CutIn(who, "차원 너머에서 온 자", col, 1));
                music(music());
            });
        }
        void deathScene() {
            dying = true;
            stopMusic();
            rig.play("kneel", 9999);
            for (ServerPlayer p : players(90)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFFFFF, 5000, 0.35f));
            BossSfx.play(l, body.position(), "choir", 3f, 0.6f);
            camFocus = body.position();
            scene(170, who, deathLines(), 42, 6f + (float) S, 1.4f * (float) S);
            later(110, () -> rig.play("fall", 9999));
            later(125, () -> {   // 균열이 빨아들이며 닫힘
                Holo.flare(l, tag, body.position().add(0, S, 0), 6, col, 10);
                Holo.shards(l, tag, body.position().add(0, S, 0), "minecraft:crying_obsidian", 30, 6);
                Holo.killOwner(riftTag);
                Vec3 r = rift;
                for (int i = 0; i < 3; i++) Holo.circle(l, "jjk.riftclose" + Holo.uid, r.add(0, i * 0.3, 0), 6 + i * 2, 0xB040FF, 30, 0);
                Holo.flare(l, "jjk.riftclose" + Holo.uid, r, 12, 0xFFFFFF, 16);
                BossSfx.play(l, r, "shatter", 3f, 0.5f);
                BossSfx.play(l, r, "deep", 3f, 1.2f);
                caption("§5§l차원 문이 닫혔다");
            });
            later(166, this::kill);
        }

        @Override void step() {
            if (phase == 0 || dying) return;
            if (atFloor()) { deathScene(); return; }
            if (!enraged && frac() < 0.5f) { enraged = true; enrage(); }
            ServerPlayer tg = nearest();
            if (tg == null) return;
            fight(tg, tg.position().distanceTo(body.position()));
        }
        void enrage() {
            Holo.shock(l, tag, body.position(), 20, col, 14);
            BossSfx.play(l, body.position(), "stinger", 3f, 0.5f);
            rig.play("raise", 30);
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn(who, "분노", col, 0));
        }

        @Override void onDeath() {
            caption("§5§l" + who + " 격파\n§7차원 문이 닫혔다");
            for (ServerPlayer p : players(90)) GuardMeta.send(p, 4, "차원의 침입자를 물리쳤다");
            giveCmd("jjk:dimension_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("minecraft:diamond_block 4");
            giveCmd("minecraft:netherite_ingot 2");
            giveCmd("minecraft:ender_eye 8");
            BossSfx.play(l, body.position(), "choir", 3f, 1.0f);
        }
        @Override void onLeave() { say("…겁쟁이들. 차원 너머로 돌아가겠다."); }
        @Override void cleanup() {
            Holo.killOwner(riftTag);
            super.cleanup();
        }
    }

    // ═════════════════════════ 파괴자
    static final class Destroyer extends Dim {
        Destroyer(ServerLevel l, Vec3 at) { super(l, at, "파괴자", "§4§l파괴자 §7— 차원의 침입자", 0xFF5020, BossEvent.BossBarColor.RED, "destroyer", 2.0, 1500, "minecraft:netherite_axe"); }
        String[] introLines() { return new String[]{"……이 세계도 무르군.", "부순다.", "전부 부순다."}; }
        String[] deathLines() { return new String[]{"…크윽… 이 몸이…", "부서지는 쪽이… 나라고…?", "다음 차원에서… 기다리겠다."}; }
        String music() { return "minecraft:music_disc.ward"; }
        @Override void enrage() { super.enrage(); say("분노가… 끓어오른다!!"); }

        void fight(ServerPlayer tg, double dist) {
            if (!rig.busy()) chase(tg, enraged ? 0.2 : 0.15, 3.6);
            else faceTo(tg);
            if (enraged && t % 20 == 0) {   // 용암 오라
                l.sendParticles(ParticleTypes.LAVA, body.getX(), body.getY() + 1, body.getZ(), 6, 1.5, 0.5, 1.5, 0);
                playersInRange(body.position(), 4.5, 2f);
            }
            if (--cd > 0) return;
            int k = dist < 5 ? new int[]{0, 1, 0, 4, 0, 1}[atk++ % 6] : new int[]{2, 3, 4, 2, 1}[atk++ % 5];
            cd = enraged ? 32 : 42;
            switch (k) {
                case 0 -> { melee(tg, 14f, "slam", 12, 5.2); later(12, () -> Holo.shock(l, tag, body.position(), 6, col, 8)); }
                case 1 -> pound();
                case 2 -> charge(tg);
                case 3 -> { skill("운석 낙하"); rig.play("raise", 26); rain(tg.position(), enraged ? 14 : 9, 8, "minecraft:magma_block", col, 9f); }
                case 4 -> fissure(tg);
                default -> { }
            }
        }
        /** 지면 파동: 뛰어올라 내려찍기 → 고리 3개가 퍼짐 (점프로 피하기). */
        void pound() {
            skill("지면 파동");
            rig.play("slam", 24);
            Vec3 c = body.position();
            for (int i = 1; i <= 6; i++) { final int k = i; later(i, () -> moveTo(c.add(0, Math.sin(k / 6.0 * Math.PI) * 3, 0))); }
            later(12, () -> {
                moveTo(c);
                BossSfx.play(l, c, "boom", 3f, 0.6f);
                Titan.shake(l, c, 40, true);
                for (int r = 0; r < 3; r++) {
                    final double rad = 4 + r * 4;
                    later(r * 6, () -> {
                        Holo.shock(l, tag, c, rad * 2, col, 8);
                        for (ServerPlayer p : players(40)) {
                            double d = p.position().multiply(1, 0, 1).distanceTo(c.multiply(1, 0, 1));
                            if (Math.abs(d - rad) < 1.6 && p.onGround()) { hurtP(p, 10f); p.push(0, 0.7, 0); p.hurtMarked = true; }
                        }
                    });
                }
            });
        }
        /** 돌진: 예고선 → 14칸 들이받기. */
        void charge(ServerPlayer tg) {
            skill("돌진");
            Vec3 a = body.position(), dir = tg.position().subtract(a).multiply(1, 0, 1).normalize(), b = a.add(dir.scale(14));
            Holo.beamGlow(l, tag, a.add(0, 0.2, 0), b.add(0, 0.2, 0), 2.2, col, 1, 16, 1);
            faceTo(tg);
            rig.play("charge", 30);
            BossSfx.play(l, a, "charge", 2f, 0.6f);
            for (int i = 1; i <= 10; i++) {
                final int k = i;
                later(16 + i, () -> {
                    Vec3 q = a.lerp(b, k / 10.0);
                    moveTo(new Vec3(q.x, groundY(q.x, q.y + 1, q.z), q.z));
                    for (ServerPlayer p : players(30)) if (p.position().distanceTo(body.position()) < 2.6 * S / 2 + 1) { hurtP(p, 16f); p.push(dir.x * 2, 0.8, dir.z * 2); p.hurtMarked = true; }
                    if (k % 3 == 0) BossSfx.play(l, q, "impact", 1.2f, 0.8f);
                });
            }
        }
        /** 균열 분출: 보스 → 대상 직선으로 땅이 차례로 터짐. */
        void fissure(ServerPlayer tg) {
            skill("균열 분출");
            rig.play("slam", 18);
            Vec3 a = body.position(), dir = tg.position().subtract(a).multiply(1, 0, 1).normalize();
            for (int i = 1; i <= 10; i++) {
                Vec3 q0 = a.add(dir.scale(i * 2.2));
                Vec3 q = new Vec3(q0.x, groundY(q0.x, q0.y + 2, q0.z), q0.z);
                Holo.circle(l, tag, q.add(0, 0.1, 0), 1.6, col, 14 + i * 2, 0);
                later(14 + i * 2, () -> {
                    Holo.pillar(l, tag, q, 6, 1.2, col, 1, 3, 6);
                    l.sendParticles(ParticleTypes.LAVA, q.x, q.y + 0.5, q.z, 8, 0.5, 0.5, 0.5, 0);
                    BossSfx.play(l, q, "impact", 1f, 1.1f);
                    playersInRange(q, 1.8, 10f);
                });
            }
        }
    }

    // ═════════════════════════ 몰살자
    static final class Exterminator extends Dim {
        Exterminator(ServerLevel l, Vec3 at) { super(l, at, "몰살자", "§5§l몰살자 §7— 차원의 침입자", 0xC040FF, BossEvent.BossBarColor.PURPLE, "exterminator", 1.3, 1200, "minecraft:netherite_sword"); }
        String[] introLines() { return new String[]{"…목표 확인.", "이 차원의 생명체, 전원.", "몰살한다."}; }
        String[] deathLines() { return new String[]{"…임무… 실패.", "이 차원은… 예상보다 강하군.", "…보고는… 못 하겠어."}; }
        String music() { return "minecraft:music_disc.5"; }
        @Override void enrage() { super.enrage(); say("놀이는 끝이다."); }

        void fight(ServerPlayer tg, double dist) {
            if (!rig.busy()) chase(tg, enraged ? 0.3 : 0.25, 2.2);
            else faceTo(tg);
            if (--cd > 0) return;
            int k = dist < 3.5 ? new int[]{0, 1, 0, 3, 0, 2}[atk++ % 6] : new int[]{1, 2, 4, 3, 1}[atk++ % 5];
            if (enraged && atk % 5 == 0) k = 5;
            cd = enraged ? 26 : 34;
            switch (k) {
                case 0 -> combo(tg);
                case 1 -> blink(tg);
                case 2 -> voidX(tg);
                case 3 -> mark(tg);
                case 4 -> afterimages(tg);
                case 5 -> { skill("칼날 비"); rig.play("raise", 24); rain(tg.position(), 12, 7, "minecraft:crying_obsidian", col, 8f); }
                default -> { }
            }
        }
        void combo(ServerPlayer tg) {
            melee(tg, 6f, "swing", 5, 3.4);
            later(8, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, 6f, "sweep", 4, 3.4); } });
            later(15, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, 8f, "swing", 4, 3.6); } });
        }
        void blink(ServerPlayer tg) {
            skill("기습");
            for (int i = 0; i < 3; i++) later(i * 9, () -> {
                ServerPlayer tt = nearest();
                if (tt == null) return;
                Vec3 back = tt.position().subtract(tt.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
                Holo.flare(l, tag, body.position().add(0, S, 0), 1.4, col, 4);
                moveTo(new Vec3(back.x, groundY(back.x, tt.getY() + 1, back.z), back.z));
                faceTo(tt);
                BossSfx.play(l, back, "whoosh", 1.2f, 1.5f);
                melee(tt, 6f, "swing", 3, 3.2);
            });
        }
        void voidX(ServerPlayer tg) {
            skill("공허 참격");
            rig.play("sweep", 22);
            Vec3 c = tg.position().add(0, 1, 0);
            Holo.beamGlow(l, tag, c.add(-5, 0, -5), c.add(5, 0, 5), 0.06, col, 1, 15, 1);
            Holo.beamGlow(l, tag, c.add(-5, 0, 5), c.add(5, 0, -5), 0.06, col, 1, 15, 1);
            later(15, () -> {
                Holo.beamGlow(l, tag, c.add(-5, 0, -5), c.add(5, 0, 5), 0.5, col, 0, 3, 6);
                Holo.beamGlow(l, tag, c.add(-5, 0, 5), c.add(5, 0, -5), 0.5, col, 0, 3, 6);
                BossSfx.play(l, c, "slash", 2f, 0.6f);
                for (ServerPlayer p : players(40)) {
                    Vec3 r = p.position().add(0, 1, 0).subtract(c);
                    double d1 = Math.abs(r.x - r.z) / Math.sqrt(2), d2 = Math.abs(r.x + r.z) / Math.sqrt(2);
                    if ((d1 < 1.3 || d2 < 1.3) && Math.abs(r.x) < 5.5 && Math.abs(r.z) < 5.5) hurtP(p, 16f);
                }
            });
        }
        /** 몰살 표식: 2초 뒤 표식 자리에서 4칸 안이면 큰 피해 (움직이면 피함). */
        void mark(ServerPlayer tg) {
            skill("몰살 표식");
            rig.play("point", 20);
            Vec3 at = tg.position();
            Holo.hitbox(l, tag, tg, col, 40);
            Holo.circle(l, tag, at.add(0, 0.1, 0), 4, col, 40, 0);
            later(40, () -> {
                Holo.flare(l, tag, at.add(0, 1, 0), 4, col, 6);
                BossSfx.play(l, at, "slash", 2f, 0.5f);
                if (tg.isAlive() && tg.position().distanceTo(at) < 4) { hurtP(tg, 28f); Holo.banner(l, tag, tg.position().add(0, 2.5, 0), banner("몰살", col), 1.2, 20); }
            });
        }
        /** 잔상: 보라 잔상 4개가 플레이어에게 달려가 터짐. */
        void afterimages(ServerPlayer tg) {
            skill("잔상");
            rig.play("cast", 20);
            for (int i = 0; i < 4; i++) {
                final int ii = i;
                Vec3 s0 = body.position().add(Math.cos(i * Math.PI / 2) * 2, 1, Math.sin(i * Math.PI / 2) * 2);
                Holo.flare(l, tag, s0, 1.2, col, 26);
                later(6 + i * 4, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null) return;
                    Vec3 e = tt.position().add(0, 1, 0);
                    Holo.beamGlow(l, tag, s0, e, 0.3, col, 0, 2, 5);
                    Holo.explosion(l, tag, e, 1.8, col);
                    playersInRange(e, 2.4, 8f);
                });
            }
        }
    }

    // ═════════════════════════ 파수꾼
    static final class Sentinel extends Dim {
        int overheat = 0; double ang = 0;
        Sentinel(ServerLevel l, Vec3 at) { super(l, at, "파수꾼", "§b§l파수꾼 §7— 차원의 침입자", 0x60E0FF, BossEvent.BossBarColor.BLUE, "sentinel", 1.7, 1400, "minecraft:trident"); if (rig != null) rig.floating = true; }
        String[] introLines() { return new String[]{"이 문을 연 것은 너인가.", "나는 차원의 경계를 지키는 자.", "침입자여, 심판을 받아라."}; }
        String[] deathLines() { return new String[]{"…경계가… 무너진다.", "훌륭하다, 이 차원의 전사여.", "문을… 닫아라. 다시는 열지 마라."}; }
        String music() { return "minecraft:music_disc.relic"; }
        @Override float adjust(DamageSource src, float amt) { return overheat > 0 ? amt * 1.5f : amt; }
        @Override void enrage() { super.enrage(); say("심판의 시간을 앞당긴다."); }

        void fight(ServerPlayer tg, double dist) {
            if (overheat > 0) { overheat--; if (overheat % 10 == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, body.getX(), body.getY() + 2 * S, body.getZ(), 4, 0.3, 0.3, 0.3, 0.02); }
            if (!rig.busy()) {
                ang += 0.03;
                Vec3 want = tg.position().add(Math.cos(ang) * 3.2, 0, Math.sin(ang) * 3.2);
                want = new Vec3(want.x, groundY(want.x, tg.getY() + 1, want.z) + 0.6, want.z);
                glide(want, 0.12);
            }
            faceTo(tg);
            if (--cd > 0) return;
            int k = dist < 4 ? new int[]{0, 1, 0, 2, 0, 4}[atk++ % 6] : new int[]{2, 3, 0, 4, 3}[atk++ % 5];
            cd = enraged ? 30 : 40;
            switch (k) {
                case 0 -> thrust(tg);
                case 1 -> shield();
                case 2 -> judgment();
                case 3 -> orbs();
                case 4 -> eyeSweep(tg);
                default -> { }
            }
        }
        void thrust(ServerPlayer tg) {
            skill("창 찌르기");
            rig.play("point", 22);
            Vec3 a = body.position().add(0, 1.3 * S, 0), dir = tg.position().add(0, 1, 0).subtract(a).normalize(), b = a.add(dir.scale(9));
            Holo.beamGlow(l, tag, a, b, 0.06, col, 1, 12, 1);
            later(12, () -> {
                Holo.beamGlow(l, tag, a, b, 0.7, col, 0, 3, 5);
                BossSfx.play(l, a, "whoosh", 1.6f, 1.3f);
                for (ServerPlayer p : players(30)) {
                    Vec3 q = p.position().add(0, 1, 0), ab = b.subtract(a);
                    double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
                    if (q.distanceTo(a.add(ab.scale(s))) < 1.5) hurtP(p, 15f);
                }
            });
        }
        /** 방패: 3초 무적 → 충격파 반격. */
        void shield() {
            skill("수호의 방패");
            rig.play("guard", 60);
            guardFor(60);
            final Mob b = body;
            Holo.bubble(l, tag, () -> b.position().add(0, S, 0), 1.4 * S, col, 60);
            BossSfx.play(l, body.position(), "magic", 2f, 0.8f);
            later(60, () -> {
                Holo.shock(l, tag, body.position(), 14, col, 10);
                BossSfx.play(l, body.position(), "boom", 2f, 1.2f);
                for (ServerPlayer p : players(30)) if (p.position().distanceTo(body.position()) < 6.5) { hurtP(p, 12f); Vec3 d = p.position().subtract(body.position()).normalize(); p.push(d.x * 1.5, 0.6, d.z * 1.5); p.hurtMarked = true; }
            });
        }
        /** 심판의 빛: 플레이어마다 원 3개 → 1.5초 뒤 하늘에서 빛기둥. */
        void judgment() {
            skill("심판의 빛");
            rig.play("raise", 34);
            for (ServerPlayer p : players(40)) for (int i = 0; i < (enraged ? 4 : 3); i++) {
                Vec3 q0 = p.position().add((R.nextDouble() - 0.5) * (i == 0 ? 0 : 7), 0, (R.nextDouble() - 0.5) * (i == 0 ? 0 : 7));
                Vec3 q = new Vec3(q0.x, groundY(q0.x, q0.y + 2, q0.z), q0.z);
                Holo.circle(l, tag, q.add(0, 0.1, 0), 2.2, col, 30, 0);
                later(30, () -> {
                    Holo.pillar(l, tag, q, 40, 2.2, 0xFFFFFF, 1, 4, 8);
                    BossSfx.play(l, q, "thunder", 1.2f, 1.3f);
                    playersInRange(q, 2.4, 18f);
                });
            }
        }
        /** 감시 구체 3개: 플레이어를 돌다 차례로 레이저. */
        void orbs() {
            skill("감시 구체");
            rig.play("cast", 24);
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                java.util.function.Supplier<Vec3> pos = () -> { ServerPlayer tt = nearest(); Vec3 c = tt == null ? body.position() : tt.position(); double a = ii * Math.PI * 2 / 3 + t * 0.07; return c.add(Math.cos(a) * 4.5, 3.2, Math.sin(a) * 4.5); };
                Holo.block(l, tag, body.position(), "minecraft:sea_lantern", col, Holo.m(Holo.I(), 0.7, 0.7, 0.7, Vec3.ZERO, true), 80, 1).follow(pos, Vec3.ZERO).spin(12);
                later(30 + i * 12, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null) return;
                    Vec3 a = pos.get(), b = tt.position().add(0, 1, 0);
                    Holo.beamGlow(l, tag, a, b, 0.18, col, 0, 2, 4);
                    BossSfx.play(l, a, "laser", 0.8f, 1.6f);
                    hurtP(tt, 8f);
                });
            }
        }
        /** 눈 레이저: 좌→우로 쓸고 나면 과열 3초 (받는 피해 1.5배). */
        void eyeSweep(ServerPlayer tg) {
            skill("감시의 눈");
            rig.play("cast", 40);
            Vec3 a = body.position().add(0, 1.7 * S, 0);
            Vec3 d = tg.position().subtract(a).multiply(1, 0, 1).normalize();
            double base = Math.atan2(d.z, d.x);
            Holo.flare(l, tag, a, 1.6, col, 18);
            BossSfx.play(l, a, "charge", 2f, 0.8f);
            for (int k = 0; k < 12; k++) {
                final double an = base - 0.8 + k * 0.145;
                later(18 + k * 2, () -> {
                    Vec3 dir = new Vec3(Math.cos(an), -0.2, Math.sin(an)), b = a.add(dir.scale(26));
                    Holo.beamGlow(l, tag, a, b, 0.6, col, 0, 2, 3);
                    for (ServerPlayer p : players(30)) {
                        Vec3 q = p.position().add(0, 1, 0), ab = b.subtract(a);
                        double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
                        if (q.distanceTo(a.add(ab.scale(s))) < 1.4) hurtP(p, 5f);
                    }
                });
            }
            later(18, () -> BossSfx.play(l, a, "laser", 2.5f, 0.7f));
            later(44, () -> { overheat = 60; say("…과열. 냉각 중."); Holo.banner(l, tag, body.position().add(0, 2.8 * S, 0), banner("과열! (약점)", 0xFF5050), 1.2, 50); });
        }
    }
}
