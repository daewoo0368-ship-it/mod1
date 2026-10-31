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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 박사의 열쇠 → 보스전 「우민 박사」 (가상 체력 1000, 1페이지).
 *  몸: 디스플레이 인형 (우민 얼굴·이마 고글·흰 가운·제트팩), 걸어서 다가와 렌치 근접 + 기계 패턴.
 *  패턴: 렌치 휘두르기 · 드론 편대 레이저 · 자동 포탑 · 미사일 폭격 · 테슬라 코일 · 로봇 조수
 *  체력 50%: 메카 슈트 MK-Ⅸ 탑승 (히트박스도 커짐, 받는 피해 60%) → 쿵쾅 밟기 · 로켓 펀치 · 하이퍼 빔
 */
public final class DoctorBoss {
    private DoctorBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Doc) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DOCTOR_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7박사는 이미 실험 중이다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        Doc d = new Doc(l, at);
        if (d.body != null) Bosses.ACTIVE.add(d);
    }

    static final class Doc extends Bosses.Boss {
        static final float MAX = 1000;
        static final double S = 1.15;
        static final int CYAN = 0x40E0FF, RED = 0xFF4030, YEL = 0xFFD84A;
        static final String WHO = "우민 박사";
        final Random R = new Random();
        final Vec3 home;
        int cd = 40, atk = 0, lastClank = -99; boolean dying = false, mech = false;
        final List<Vec3> turrets = new ArrayList<>();

        Doc(ServerLevel l, Vec3 at) {
            super(l, "§b§l우민 박사 §7— 실험 개시", BossEvent.BossBarColor.BLUE);
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home, S, MAX, "§b우민 박사");
            if (body == null) return;
            rig = new BossRig(this, "doctor", S).prop("jetpack", new Vec3(0, -0.36, 0), 1.0, true);
            rig.hold("minecraft:iron_axe");
            faceTo(nearest());
            intro();
        }

        static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }
        void say(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§b<우민 박사> §f" + s)); }
        void skill(String name, int rgb) {
            Holo.banner(l, tag, body.position().add(0, mech ? 7.6 : 2.8 * S, 0), banner(name, rgb), 1.0, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§b박사 §7▸ §f" + name), true);
        }
        @Override float adjust(DamageSource src, float amt) {
            if (!mech) return amt;
            if (t - lastClank > 10) { lastClank = t; Holo.banner(l, tag, body.position().add(0, 5.5, 0), banner("CLANK!", 0xAAAAAA), 0.9, 10); sfx("block", 1f, 0.7f); }
            return amt * 0.6f;
        }

        // ═════════ 컷신
        void intro() {
            sfx("data", 3f, 0.8f);
            Holo.pillar(l, tag, home, 40, 1.5, CYAN, 4, 20, 10);
            for (int i = 0; i < 8; i++) {   // 실험실 장비가 땅에서 솟음
                double a = i * Math.PI / 4;
                Vec3 q = home.add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
                String blk = new String[]{"minecraft:piston", "minecraft:dispenser", "minecraft:observer", "minecraft:redstone_lamp"}[i % 4];
                Holo.block(l, tag, q, blk, -1, Holo.m(Holo.I(), 0.9, 0.01, 0.9, new Vec3(0, 0.45, 0), true), 150, 0)
                        .key(4 + i * 3, Holo.m(Holo.I(), 0.9, 0.9, 0.9, new Vec3(0, 0.45, 0), true), 6).key(140, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 0.45, 0), true), 8);
            }
            later(30, () -> rig.play("point", 40));
            scene(150, WHO, new String[]{"흠, 실험체가 제 발로 걸어 들어왔군.", "흐음… 근력 평범, 지능 평범.", "좋아. 신작 기계들 성능 테스트에 딱이야.", "실험 개시!"}, 34, 5f, 2f);
            later(120, () -> rig.play("raise", 26));
            later(146, () -> {
                phase = 1; cd = 30;
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("우민 박사", "실험 개시", CYAN, 1));
                sfx("stinger", 3f, 1.0f);
                music("minecraft:music_disc.blocks");
            });
        }
        /** 50%: 메카 슈트 탑승. */
        void mechUp() {
            Vec3 c = body.position();
            rig.play("raise", 100);
            scene(130, WHO, new String[]{"크윽… 생각보다 튼튼하잖아?", "그렇다면 최종 병기다.", "MK-Ⅸ 「철의 우민」, 기동!"}, 36, 9f, 3f);
            for (int i = 0; i < 10; i++) {   // 부품이 날아와 조립됨
                Vec3 from = c.add((R.nextDouble() - 0.5) * 30, 15 + R.nextDouble() * 10, (R.nextDouble() - 0.5) * 30);
                Holo.block(l, tag, from, i % 2 == 0 ? "minecraft:iron_block" : "minecraft:redstone_block", -1, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, true), 100, 0)
                        .key(20 + i * 5, Holo.m(Holo.I(), 1.2, 1.2, 1.2, c.add(0, 2, 0).subtract(from), true), 14).key(96, Holo.m(Holo.I(), 0.01, 0.01, 0.01, c.add(0, 2, 0).subtract(from), true), 4);
                final int ii = i;
                later(34 + i * 5, () -> { sfx("block", 1.2f, 0.8f + ii * 0.05f); l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 2, c.z, 20, 1, 1, 1, 0.2); });
            }
            later(100, () -> {
                mech = true;
                buildMech();
                var sc = body.getAttribute(Attributes.SCALE); if (sc != null) sc.setBaseValue(2.3);   // 히트박스 = 메카 크기
                rig.dropProp();
                rig.hold(null);
                rig.sitting = true;
                rig.liftGoal = 4.6 / S - 0.75;   // 조종석에 앉음
                bar.setName(Component.literal("§c§l우민 박사 §7— MK-Ⅸ 철의 우민"));
                bar.setColor(BossEvent.BossBarColor.RED);
                Holo.shock(l, tag, c, 18, RED, 12);
                sfx("boom", 3f, 0.6f);
                Titan.shake(l, c, 50, true);
            });
            later(128, () -> {
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("MK-Ⅸ", "철의 우민", RED, 1));
                cd = 30;
            });
        }
        /** 메카 몸체 (지면 기준): 다리 · 몸통 · 반응로 · 조종석 유리 · 팔 · 어깨 포. */
        void buildMech() {
            final Doc b = this;
            java.util.function.Supplier<Vec3> at = () -> b.body.position();
            java.util.function.DoubleSupplier yaw = () -> rig.yaw;
            int L = 99999999;
            String mt = tag + "m";
            for (int s = -1; s <= 1; s += 2) {
                Holo.block(l, mt, home, "minecraft:iron_block", -1, Holo.m(Holo.I(), 1.1, 2.4, 1.1, new Vec3(s * 1.0, 1.3, 0), true), L, 1).attach(at, yaw);
                Holo.block(l, mt, home, "minecraft:polished_deepslate", -1, Holo.m(Holo.I(), 1.4, 0.5, 1.8, new Vec3(s * 1.0, 0.25, 0.2), true), L, 1).attach(at, yaw);
                Holo.block(l, mt, home, "minecraft:iron_block", -1, Holo.m(Holo.I(), 0.9, 2.4, 0.9, new Vec3(s * 2.3, 3.6, 0.3), true), L, 1).attach(at, yaw).wobble(10, 0.2);
                Holo.block(l, mt, home, "minecraft:dispenser", RED, Holo.m(Holo.I(), 1.0, 1.0, 1.0, new Vec3(s * 2.3, 2.2, 0.6), true), L, 1).attach(at, yaw);
            }
            Holo.block(l, mt, home, "minecraft:iron_block", -1, Holo.m(Holo.I(), 3.2, 2.0, 2.2, new Vec3(0, 3.5, 0), true), L, 1).attach(at, yaw);
            Holo.block(l, mt, home, "minecraft:redstone_block", 0xFF2020, Holo.m(Holo.I(), 1.0, 1.0, 0.2, new Vec3(0, 3.5, 1.12), true), L, 1).attach(at, yaw);
            for (int i = 0; i < 4; i++) {   // 열린 조종석: 모서리 기둥 4 + 지붕 (박사가 잘 보이게)
                double x = (i % 2 == 0 ? -0.95 : 0.95), z = (i < 2 ? -0.85 : 0.85);
                Holo.block(l, mt, home, "minecraft:iron_bars", -1, Holo.m(Holo.I(), 0.2, 2.3, 0.2, new Vec3(x, 5.65, z), true), L, 1).attach(at, yaw);
            }
            Holo.block(l, mt, home, "minecraft:iron_block", -1, Holo.m(Holo.I(), 2.2, 0.25, 2.0, new Vec3(0, 6.9, 0), true), L, 1).attach(at, yaw);
            Holo.block(l, mt, home, "minecraft:observer", CYAN, Holo.m(Holo.I(), 0.8, 0.8, 1.6, new Vec3(1.3, 5.0, 0.2), true), L, 1).attach(at, yaw);
        }
        void deathScene() {
            dying = true;
            stopMusic();
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x101820, 5000, 0.5f));
            sfx("siren", 3f, 0.6f);
            scene(140, WHO, new String[]{"경고… 경고… 반응로 과부하…", "말도 안 돼! 계산은 완벽했는데!", "……다음 실험은 반드시 성공한다. 기억해 둬!"}, 40, mech ? 9f : 5f, mech ? 3f : 1.6f);
            later(60, () -> { for (int i = 0; i < 6; i++) { final int ii = i; later(i * 6, () -> { Holo.explosion(l, tag, body.position().add((R.nextDouble() - 0.5) * 3, 2 + (R.nextDouble() - 0.5) * 3, (R.nextDouble() - 0.5) * 3), 1.5 + ii * 0.3, RED); sfx("boom", 1.2f, 1.2f); }); } });
            later(120, () -> {
                Holo.killOwner(tag + "m");
                Holo.explosion(l, tag, body.position().add(0, 2, 0), 6, RED);
                Holo.shards(l, tag, body.position().add(0, 2, 0), "minecraft:iron_block", 30, 7);
                sfx("boom", 3f, 0.6f);
                rig.sitting = false; rig.liftGoal = 0;
                rig.play("fall", 9999);
            });
            later(138, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            if (!mech && frac() < 0.5f) { mechUp(); return; }
            if (atFloor()) { deathScene(); return; }
            ServerPlayer tg = nearest();
            if (tg == null) return;
            if (mech) {   // 메카: 쿵쾅 걸어옴
                if (!rig.busy()) {
                    boolean near = chase(tg, 0.12, 3.5);
                    if (!near && t % 14 == 0) { l.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 2f, 0.5f); Titan.shake(l, body.position(), 14, false); }
                } else faceTo(tg);
            } else {
                if (!rig.busy()) chase(tg, 0.17, 2.4);
                else faceTo(tg);
                if (t % 4 == 0) l.sendParticles(ParticleTypes.FLAME, body.getX(), body.getY() + 1.1 * S, body.getZ(), 1, 0.15, 0.05, 0.15, 0.01);
            }
            if (t % 25 == 0) for (Vec3 tp : turrets) { ServerPlayer tt = nearestTo(tp); if (tt != null) turretShot(tp, tt); }
            if (--cd > 0) return;
            double dist = tg.position().distanceTo(body.position());
            int k;
            if (mech) k = dist < 5 ? new int[]{5, 6, 5, 7}[atk++ % 4] : new int[]{6, 2, 7, 3}[atk++ % 4];
            else k = dist < 3.5 ? new int[]{8, 0, 8, 1, 8, 3}[atk++ % 6] : new int[]{0, 2, 1, 4, 3, 2}[atk++ % 6];
            cd = mech ? 40 : 42;
            switch (k) {
                case 0 -> drones(tg);
                case 1 -> turret(tg);
                case 2 -> missiles(tg);
                case 3 -> tesla();
                case 4 -> robots(tg);
                case 5 -> stomp();
                case 6 -> rocketPunch(tg);
                case 7 -> mechBeam(tg);
                case 8 -> { melee(tg, 7f, "swing", 9, 3.4); later(14, () -> { ServerPlayer t2 = nearest(); if (t2 != null) { faceTo(t2); melee(t2, 7f, "swing", 7, 3.4); } }); }
                default -> { }
            }
        }
        ServerPlayer nearestTo(Vec3 c) {
            ServerPlayer best = null; double bd = 24 * 24;
            for (ServerPlayer p : players(80)) { double d = p.position().distanceToSqr(c); if (d < bd) { bd = d; best = p; } }
            return best;
        }

        /** 드론 편대: 6기가 둘러싸고 순차 레이저. */
        void drones(ServerPlayer tg) {
            skill("드론 편대 출격", CYAN);
            say("가라, 드론들!");
            rig.play("point", 30);
            sfx("data", 1.5f, 1.4f);
            for (int i = 0; i < 6; i++) {
                final int ii = i;
                java.util.function.Supplier<Vec3> pos = () -> { double a = ii * Math.PI / 3 + t * 0.08; ServerPlayer tt = nearest(); Vec3 c = tt == null ? home : tt.position(); return c.add(Math.cos(a) * 5, 3.5, Math.sin(a) * 5); };
                Holo.block(l, tag, body.position(), "minecraft:observer", CYAN, Holo.m(Holo.I(), 0.6, 0.35, 0.6, Vec3.ZERO, true), 90, 1).follow(pos, Vec3.ZERO);
                later(25 + i * 8, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null) return;
                    Vec3 a = pos.get(), b = tt.position().add(0, 1, 0);
                    Holo.beamGlow(l, tag, a, b, 0.15, RED, 0, 2, 4);
                    BossSfx.play(l, a, "laser", 0.6f, 1.8f);
                    hurtP(tt, 4f);
                });
            }
        }
        /** 포탑 설치 (최대 3기): 주기적으로 사격. */
        void turret(ServerPlayer tg) {
            skill("자동 포탑 설치", YEL);
            rig.play("slam", 18);
            Vec3 q = tg.position().add((R.nextDouble() - 0.5) * 12, 0, (R.nextDouble() - 0.5) * 12);
            Vec3 tp = new Vec3(q.x, groundY(q.x, tg.getY() + 2, q.z), q.z);
            if (turrets.size() >= 3) turrets.remove(0);
            turrets.add(tp);
            Holo.block(l, tag, tp, "minecraft:iron_block", -1, Holo.m(Holo.I(), 0.8, 0.8, 0.8, new Vec3(0, 0.4, 0), true), 600, 0);
            Holo.block(l, tag, tp, "minecraft:dispenser", YEL, Holo.m(Holo.I(), 0.7, 0.7, 0.7, new Vec3(0, 1.15, 0), true), 600, 0).spin(8);
            Holo.pillar(l, tag, tp, 6, 0.6, YEL, 1, 2, 4);
            sfx("block", 1.2f, 1.4f);
            later(600, () -> turrets.remove(tp));
        }
        void turretShot(Vec3 tp, ServerPlayer tt) {
            Vec3 a = tp.add(0, 1.2, 0), b = tt.position().add(0, 1, 0);
            Holo.beamGlow(l, tag, a, b, 0.08, YEL, 0, 1, 3);
            l.playSound(null, a.x, a.y, a.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 0.8f, 1.6f);
            hurtP(tt, 2.5f);
        }
        /** 미사일 폭격. */
        void missiles(ServerPlayer tg) {
            skill("미사일 일제 사격", RED);
            say("좌표 입력 완료. 발사!");
            rig.play("raise", 24);
            sfx("whoosh", 2f, 0.7f);
            rain(tg.position(), mech ? 14 : 9, 7, "minecraft:tnt", RED, 8f);
        }
        /** 테슬라 코일: 4개가 솟아 전기 감옥. */
        void tesla() {
            skill("테슬라 코일", CYAN);
            rig.play("raise", 30);
            Vec3 c = body.position();
            List<Vec3> coils = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                Vec3 q = c.add(Math.cos(a) * 9, 0, Math.sin(a) * 9);
                q = new Vec3(q.x, groundY(q.x, c.y + 2, q.z), q.z);
                coils.add(q);
                Holo.block(l, tag, q, "minecraft:lightning_rod", CYAN, Holo.m(Holo.I(), 1.5, 0.01, 1.5, new Vec3(0, 0, 0), true), 100, 0)
                        .key(6, Holo.m(Holo.I(), 1.5, 4, 1.5, new Vec3(0, 2, 0), true), 8);
            }
            for (int k = 0; k < 8; k++) later(20 + k * 10, () -> {
                for (int i = 0; i < 4; i++) Holo.lightning(l, tag, coils.get(i).add(0, 4, 0), coils.get((i + 1) % 4).add(0, 4, 0), CYAN, 0.12, 6);
                for (ServerPlayer p : players(80)) {
                    Vec3 rel = p.position().subtract(c);
                    if (Math.abs(rel.x) < 6.5 && Math.abs(rel.z) < 6.5) {
                        Holo.lightning(l, tag, coils.get(R.nextInt(4)).add(0, 4, 0), p.position().add(0, 1, 0), CYAN, 0.1, 5);
                        hurtP(p, 2.5f);
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1));
                    }
                }
                BossSfx.play(l, c, "thunder", 0.6f, 1.6f);
            });
        }
        /** 로봇 하수인 3기. */
        void robots(ServerPlayer tg) {
            skill("로봇 하수인 생산", 0xAAAAAA);
            say("조수들, 저 실험체를 붙잡아!");
            rig.play("point", 24);
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                Vec3 q = tg.position().add(Math.cos(a) * 6, 0.5, Math.sin(a) * 6);
                var z = EntityType.ZOMBIE.create(l, EntitySpawnReason.MOB_SUMMONED);
                if (z == null) continue;
                z.snapTo(q.x, q.y, q.z, 0, 0);
                z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.OBSERVER));
                z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                z.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) z.setDropChance(s, 0);
                z.setCustomName(Component.literal("§7로봇 조수 #" + (R.nextInt(900) + 100)));
                z.setCustomNameVisible(true);
                z.addTag("jjk.boss");
                z.setTarget(tg);
                l.addFreshEntity(z);
                adds.add(z);
                Holo.pillar(l, tag, q, 8, 0.8, 0xAAAAAA, 1, 2, 5);
            }
            sfx("data", 1.5f, 0.8f);
        }
        /** 메카: 밟기 충격파. */
        void stomp() {
            skill("쿵쾅 밟기", RED);
            Vec3 c = body.position();
            Holo.circle(l, tag, c.add(0, 0.1, 0), 8, RED, 20, 0);
            sfx("charge", 1.5f, 0.6f);
            later(20, () -> {
                Holo.shock(l, tag, c, 14, RED, 12);
                sfx("boom", 3f, 0.5f);
                Titan.shake(l, c, 40, true);
                for (ServerPlayer p : players(80)) if (p.position().distanceTo(c) < 8) { hurtP(p, 12f); Vec3 dd = p.position().subtract(c).normalize(); p.push(dd.x * 1.4, 0.8, dd.z * 1.4); p.hurtMarked = true; }
            });
        }
        /** 메카: 로켓 펀치 (주먹이 날아감). */
        void rocketPunch(ServerPlayer tg) {
            skill("로켓 펀치!!", RED);
            Vec3 a = body.position().add(0, 2.2, 0), b = tg.position().add(0, 1, 0);
            Holo.beamGlow(l, tag, a, b, 0.06, RED, 1, 14, 1);
            Holo.block(l, tag, a, "minecraft:iron_block", RED, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, true), 40, 0)
                    .key(14, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, true), 1).key(22, Holo.m(Holo.I(), 1.2, 1.2, 1.2, b.subtract(a), true), 8).spin(30);
            sfx("whoosh", 2f, 0.8f);
            later(22, () -> {
                Holo.explosion(l, tag, b, 3, RED);
                BossSfx.play(l, b, "boom", 1.5f, 1.1f);
                playersInRange(b, 3.2, 15f);
            });
        }
        /** 메카: 어깨 포 빔 (좌→우로 쓸기). */
        void mechBeam(ServerPlayer tg) {
            skill("MK-Ⅸ 하이퍼 빔", CYAN);
            say("에너지 충전 100%! 발사!");
            Vec3 a = body.position().add(0, 5.0, 0);
            Vec3 d = tg.position().subtract(a).multiply(1, 0, 1).normalize();
            double base = Math.atan2(d.z, d.x);
            Holo.flare(l, tag, a, 2, CYAN, 20);
            sfx("charge", 2f, 0.6f);
            for (int k = 0; k < 10; k++) {
                final double ang = base - 0.7 + k * 0.14;
                later(20 + k * 2, () -> {
                    Vec3 dir = new Vec3(Math.cos(ang), -0.15, Math.sin(ang)), b = a.add(dir.scale(30));
                    Holo.beamGlow(l, tag, a, b, 0.9, CYAN, 0, 2, 3);
                    for (ServerPlayer p : players(80)) {
                        Vec3 q = p.position().add(0, 1, 0), ab = b.subtract(a);
                        double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
                        if (q.distanceTo(a.add(ab.scale(s))) < 1.6) hurtP(p, 5f);
                    }
                });
            }
            later(20, () -> sfx("laser", 3f, 0.6f));
        }

        @Override void onDeath() {
            caption("§b§l우민 박사 격파\n§f「다음 실험은 반드시 성공한다!」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "미친 과학자를 막았다");
            giveCmd("jjk:robot_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:machine_part 2");
            giveCmd("minecraft:emerald_block 6");
            giveCmd("minecraft:redstone_block 16");
            BossSfx.play(l, body.position(), "choir", 3f, 1.0f);
        }
        @Override void onLeave() { say("실험체가 도망쳤군… 데이터는 충분하다."); }
        @Override void cleanup() {
            Holo.killOwner(tag + "m");
            turrets.clear();
            super.cleanup();
        }
    }
}
