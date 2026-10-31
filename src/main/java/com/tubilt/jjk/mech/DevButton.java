package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 개발자의 버튼 — 우클릭 → 20초 컷신 → 바라보던 곳을 지름 100블럭 구로 삭제.
 * 서버: 연출용 디스플레이(버튼·궤도 위성·빛기둥·돔)·소리·파티클·실제 삭제.
 * 클라이언트(Cinematic): 카메라 경로·레터박스·자막.
 * 시간표(틱): 0 버튼 / 40 경고 / 100 위성 충전 / 200 빛기둥 하강 / 260 삭제 / 320 여파 / 400 종료
 */
public final class DevButton {
    private DevButton() {}

    public static final String NAME = "개발자의 버튼";
    public static final int LENGTH = 560, RADIUS = 50;     // 지름 100
    /** 연출 배율 (원래 r=50 기준으로 만든 크기들을 키움). */
    static final double K = RADIUS / 50.0;
    static final List<Cine> ACTIVE = new ArrayList<>();
    static final DustParticleOptions RED = new DustParticleOptions(0xFF2020, 4.0f);
    static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 4.0f);
    static final DustParticleOptions ORANGE = new DustParticleOptions(0xFF8A20, 4.0f);

    static final String[][] BUTTON = {{"bb", "minecraft:polished_blackstone", "-1"}, {"bc", "minecraft:red_concrete", "16711680"},
            {"bg", "minecraft:gold_block", "-1"}};
    static final String[][] SAT = {{"s1", "minecraft:black_concrete", "-1"}, {"s2", "minecraft:red_stained_glass", "16711680"},
            {"s3", "minecraft:white_stained_glass", "16777215"}, {"s4", "minecraft:orange_stained_glass", "-1"}};
    static final String[][] PILLAR = {{"p0", "minecraft:white_concrete", "16777215"}, {"p1", "minecraft:red_stained_glass", "16711680"},
            {"p2", "minecraft:orange_stained_glass", "-1"}, {"p3", "minecraft:white_stained_glass", "-1"}};
    static final String[][] DOME = {{"d1", "minecraft:white_stained_glass", "16777215"}, {"d2", "minecraft:red_stained_glass", "16711680"},
            {"d3", "minecraft:black_stained_glass", "-1"}};

    public static boolean is(ItemStack s) { return com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.HEART_OF_THE_SEA) && NAME.equals(s.getHoverName().getString()); }

    public static boolean busy(Player p) { for (Cine c : ACTIVE) if (c.player.equals(p.getUUID())) return true; return false; }

    /** 우클릭. */
    public static void press(ServerPlayer p, ServerLevel level) {
        if (busy(p)) return;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(160)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 t = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(160)) : hit.getLocation();
        Cine c = new Cine(level, p.getUUID(), p.position(), eye, t);
        ACTIVE.add(c);
        p.addTag("jjk.cine");
        JjkNet.toPlayer(p, new Payloads.Cine(eye.x, eye.y, eye.z, t.x, t.y, t.z));
    }

    static void tickAll() {
        for (Iterator<Cine> it = ACTIVE.iterator(); it.hasNext(); ) {
            Cine c = it.next();
            try { c.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 버튼 컷신 오류", e); c.t = LENGTH + 1; c.end(); }
            if (c.t > LENGTH) it.remove();
        }
    }

    static void stopAll() { for (Cine c : ACTIVE) c.end(); ACTIVE.clear(); }

    static final class Cine {
        final ServerLevel level; final UUID player; final Vec3 hold, eye, T, S, B, F;
        int t = 0, slice = -RADIUS;
        boolean weather = false;
        final String tagOwner;
        final Entity owner;

        Cine(ServerLevel level, UUID player, Vec3 hold, Vec3 eye, Vec3 target) {
            this.level = level; this.player = player; this.hold = hold; this.eye = eye; this.T = target;
            this.S = target.add(0, 90 + RADIUS, 0);                       // 궤도 위성 위치
            Vec3 f = new Vec3(target.x - eye.x, 0, target.z - eye.z);
            this.F = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
            this.B = eye.add(F.scale(2.2)).add(0, -0.5, 0);      // 공중에 떠오르는 버튼
            this.owner = level.getEntity(player);
            this.tagOwner = owner == null ? "x" : LaserFx.tag(owner);
        }

        ServerPlayer p() { return level.getServer().getPlayerList().getPlayer(player); }

        void sound(Vec3 at, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
            level.playSound(null, at.x, at.y, at.z, ev, SoundSource.MASTER, vol, pitch);
        }

        void cmd(String c) { MechManager.cmd(level.getServer(), level, c); }

        // ═════ 홀로 연출 (Holo 엔진: 보간 키프레임 → 끊김 없음)
        final String own = "jjk.btn" + (Holo.uid++);
        final java.util.Random rnd2 = new java.util.Random();
        final List<Holo.Elem> btn = new ArrayList<>(), sat = new ArrayList<>(), beam = new ArrayList<>(), dome = new ArrayList<>();
        double[][] RF;

        /** 버튼 부품 (버튼 기준 지역 좌표 → 월드 방향). */
        Holo.Elem bpart(String block, int glow, double sx, double sy, double sz, Vec3 c, double[][] r, int appear) {
            float[] full = Holo.m(Holo.mul(RF, r), sx, sy, sz, Holo.apply(RF, c), true);
            Holo.Elem e = Holo.block(level, own, B, block, glow, Holo.m(Holo.mul(RF, r), 0.01, 0.01, 0.01, Holo.apply(RF, c.add(0, -0.4, 0)), true), 60, 0);
            e.key(appear, full, 6);
            btn.add(e);
            return e;
        }
        float[] cover(double open) {
            Vec3 hinge = new Vec3(0, 0.16, 0.5);
            double[][] r = Holo.rotX(open);
            Vec3 c = hinge.add(Holo.apply(r, new Vec3(0, 0.35, -0.5)));
            return Holo.m(Holo.mul(RF, r), 1.0, 0.7, 1.0, Holo.apply(RF, c), true);
        }
        float[] cap(int i, double press) {
            return Holo.m(Holo.mul(RF, Holo.rotY(i * Math.PI / 4)), 0.68, 0.24, 0.68, Holo.apply(RF, new Vec3(0, 0.3 - 0.14 * press, 0)), true);
        }

        void tick() {
            ServerPlayer p = p();
            if (p == null || owner == null) { t = LENGTH + 1; end(); return; }
            if (p.position().distanceToSqr(hold) > 0.04) p.teleportTo(hold.x, hold.y, hold.z);
            p.setDeltaMovement(Vec3.ZERO);
            RandomSource rnd = level.random;

            // ── 0~40: 버튼 — 받침이 솟고, 유리 덮개가 열리고, 눌림 → 하늘로 신호
            if (t == 0) {
                RF = Holo.rotY(Math.atan2(F.x, F.z));
                bpart("minecraft:polished_blackstone", -1, 1.3, 0.22, 1.3, Vec3.ZERO, Holo.I(), 2);
                bpart("minecraft:gold_block", -1, 1.42, 0.07, 1.42, new Vec3(0, 0.13, 0), Holo.I(), 4);
                bpart("minecraft:black_concrete", -1, 0.9, 0.1, 0.9, new Vec3(0, 0.19, 0), Holo.I(), 5);
                for (int i = 0; i < 2; i++) {
                    Holo.Elem c = bpart("minecraft:red_concrete", 0xFF2020, 0.68, 0.24, 0.68, new Vec3(0, 0.3, 0), Holo.rotY(i * Math.PI / 4), 7);
                    c.key(24, cap(i, 1), 2).key(30, cap(i, 0), 4);
                }
                Holo.Elem cov = Holo.block(level, own, B, "minecraft:white_stained_glass", 0xFFFFFF, Holo.m(RF, 0.01, 0.01, 0.01, Vec3.ZERO, true), 60, 0);
                cov.key(8, cover(0), 5).key(14, cover(1.95), 7);
                btn.add(cov);
                // 경고 줄무늬 (노랑·검정) 받침 옆면
                for (int i = -2; i <= 2; i++)
                    bpart(i % 2 == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete", -1, 0.24, 0.16, 0.02,
                            new Vec3(i * 0.24, 0.0, -0.66), Holo.I(), 6);
                Holo.circle(level, own, B.add(0, -0.12, 0), 1.6, 0xFF2020, 40, 0);
                Holo.ringT(level, own, B.add(0, 0.35, 0), 0.2, 1.05, 18, 0.05, 0.02, 0xFF2020, 8, 26, 5, Holo.rotX(1.2), 6, true);
                Holo.ringT(level, own, B.add(0, 0.35, 0), 0.2, 1.2, 18, 0.04, 0.02, 0xFFFFFF, 10, 24, 5, Holo.rotX(-1.0), -5, false);
                // 인증 패널
                Holo.Elem panel = Holo.text(level, own, B.add(0, 1.05, 0), "{text:\"\"}", "center", 0xD0100406, false,
                        Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 40, 0);
                panel.key(3, Holo.m(Holo.I(), 0.32, 0.32, 0.32, Vec3.ZERO, false), 5).key(34, Holo.m(Holo.I(), 0.5, 0.01, 0.32, Vec3.ZERO, false), 4)
                        .text(a -> {
                            String l1 = "AUTH ▸ " + (a > 6 ? "DEVELOPER" : "........".substring(0, Math.min(8, a + 1)));
                            int k = Math.max(0, Math.min(10, (a - 8) / 1));
                            String l2 = "KEY  " + "█".repeat(k) + "░".repeat(10 - k) + (k >= 10 ? " OK" : "");
                            String l3 = a < 20 ? "ARM  ▯▯▯" : a < 24 ? ((a / 2) % 2 == 0 ? "ARM  ▮▮▮ READY" : "ARM  ▯▯▯ READY") : "▶ EXECUTE";
                            return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(l1, 0xFF4040, true) + ",{text:\"\\n\"}," + Holo.tx(l2, 0xFFD84A, false)
                                    + ",{text:\"\\n\"}," + Holo.tx(l3, a >= 24 ? 0xFFFFFF : 0x55FF55, a >= 24) + "]}";
                        });
                sound(eye, SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
                sound(eye, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.5f);
            }
            if (t == 14) { sound(B, SoundEvents.IRON_TRAPDOOR_OPEN, 1.5f, 0.6f); sound(B, SoundEvents.PISTON_EXTEND, 1f, 1.4f); }
            if (t < 40 && t % 2 == 0) level.sendParticles(RED, B.x, B.y + 0.5, B.z, 1, 0.5, 0.3, 0.5, 0);
            if (t == 24) {
                sound(B, SoundEvents.STONE_BUTTON_CLICK_ON, 2f, 0.6f);
                sound(B, SoundEvents.WARDEN_HEARTBEAT, 2f, 0.6f);
                Holo.orb(level, own, B.add(0, 0.35, 0), 0.9, 0xFF2020, 1, 2, 5);
                Holo.flare(level, own, B.add(0, 0.4, 0), 3, 0xFF2020, 8);
                Holo.sfx(level, B, "impact", 1.2f, 1.4f);
                Holo.ringT(level, own, B.add(0, 0.3, 0), 0.3, 3.5, 24, 0.12, 0.03, 0xFF2020, 6, 0, 4, Holo.I(), 0, true);
                level.sendParticles(ParticleTypes.END_ROD, B.x, B.y + 0.6, B.z, 30, 0.2, 0.2, 0.2, 0.25);
            }
            if (t == 32) sound(eye, SoundEvents.WARDEN_SONIC_CHARGE, 1.5f, 0.5f);
            if (t == 35) for (Holo.Elem e : btn) e.keyIn(1, Holo.m(RF, 0.01, 0.01, 0.01, new Vec3(0, 0.3, 0), true), 4).endIn(6);
            if (t == 38) {     // 하늘로 쏘아 올리는 신호
                Holo.beamLayer(level, own, B.add(0, 0.3, 0), B.add(0, 170, 0), 0.5, "minecraft:red_stained_glass", 0xFF2020, 2, 12, 8, 0);
                Holo.beamLayer(level, own, B.add(0, 0.3, 0), B.add(0, 170, 0), 0.15, "minecraft:white_concrete", -1, 2, 12, 8, 0.5);
                sound(B, SoundEvents.FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
                Holo.sfx(level, B, "whoosh", 2f, 0.6f);
            }
            if (t == 40 || t == 43 || t == 46) {
                int k = (t - 40) / 3;
                Holo.ringT(level, own, B.add(0, 10 + k * 18, 0), 0.5, 4 + k * 2, 24, 0.3, 0.05, 0xFF2020, 6, 2, 5, Holo.I(), 0, true);
            }

            // ── 40~260: 삭제 구역 표식
            if (t == 40) {
                if (!level.isThundering()) { cmd("weather thunder 600"); weather = true; }
                forceload(true);
                Holo.circle(level, own, T, RADIUS, 0xFF2020, 225, 0);
                Holo.circle(level, own, T.add(0, 0.2, 0), RADIUS * 0.6, 0xFFFFFF, 225, 0);
                Holo.circle(level, own, T.add(0, 0.4, 0), RADIUS * 0.24, 0xFF2020, 225, 0);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    Vec3 q = T.add(Math.cos(a) * RADIUS, 0, Math.sin(a) * RADIUS);
                    Holo.pillar(level, own, q, 70 * K, 0.7 * K, 0xFF2020, 6 + i, 200 - i, 10);
                }
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + 0.2;
                    Vec3 q = T.add(Math.cos(a) * (RADIUS + 3), 6, Math.sin(a) * (RADIUS + 3));
                    Holo.text(level, own, q, "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⚠ DELETE ZONE ⚠", 0xFF3030, true) + "]}", "center", 0xB0200000, true,
                            Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 220, 0).key(4 + i * 2, Holo.m(Holo.I(), 5 * K, 5 * K, 5 * K, Vec3.ZERO, false), 6)
                            .key(212, Holo.m(Holo.I(), 8, 0.01, 5, Vec3.ZERO, false), 6);
                }
            }
            if (t == 40) {
                for (int k = 0; k < 2; k++) {       // 구역을 쓸고 지나가는 붉은 탐조등 2줄
                    double[][] r = Holo.mul(Holo.rotY(k * Math.PI / 2), Holo.rotZ(Math.PI / 2));
                    Holo.sprite(level, own, T.add(0, 2.5, 0), "beam", 0xFF2020, "fixed", Holo.m(r, 0.01, RADIUS * 2, 1, Vec3.ZERO, false), 222, 4)
                            .key(4, Holo.m(r, 7, RADIUS * 2.1, 1, Vec3.ZERO, false), 8).key(210, Holo.m(r, 0.01, RADIUS * 2.1, 1, Vec3.ZERO, false), 8).spin(k == 0 ? 7 : -5);
                }
                Holo.pillar(level, own, T, 60 * K, 4 * K, 0xFF2020, 6, 200, 12);
            }
            if (t >= 40 && t < 220 && (t - 40) % 46 == 0) Holo.sfx(level, eye, "siren", 1.4f, 1f);
            if (t >= 40 && t < 260) {
                if (t % 20 == 0) {
                    Holo.shock(level, own, T, RADIUS, 0xFF2020, 18);
                    sound(T, SoundEvents.NOTE_BLOCK_BELL.value(), 6f, 0.5f);
                    sound(eye, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 3f, 0.5f);
                }
            }

            // ── 100~200: 궤도 위성 — 코어·태양광 날개·회전 고리·충전 구체·에너지 흐름
            if (t == 100) {
                int life = 205;
                Holo.Elem core = Holo.item(level, own, S, "minecraft:command_block", true, 0xFF2020, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), life, 4);
                core.key(1, Holo.m(Holo.I(), 16 * K, 16 * K, 16 * K, Vec3.ZERO, false), 14).key(life - 10, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 8).spin(1.2).wobble(6, 0.05);
                sat.add(core);
                for (int sd = -1; sd <= 1; sd += 2) {
                    for (int k = 0; k < 3; k++) {    // 날개 3장씩
                        Vec3 a0 = new Vec3(sd * (10 + k * 17), 0, 0), a1 = new Vec3(sd * (26 + k * 17), 0, 0);
                        Holo.Elem w = Holo.block(level, own, S, "minecraft:light_blue_stained_glass", 0x40E8FF, Holo.bar(a0, a0.add(sd * 0.1, 0, 0), 0.01, 0.01, 0), life, 4);
                        w.key(8 + k * 4, Holo.bar(a0, a1, 14, 0.3, 0), 8).key(life - 12, Holo.bar(a0, a0.add(sd * 0.1, 0, 0), 0.01, 0.01, 0), 8).spin(1.2).wobble(6, 0.05);
                        Holo.Elem fr = Holo.block(level, own, S, "minecraft:white_concrete", -1, Holo.bar(a0, a0.add(sd * 0.1, 0, 0), 0.01, 0.01, 0), life, 4);
                        fr.key(8 + k * 4, Holo.bar(a0.add(0, 0.25, 0), a1.add(0, 0.25, 0), 0.5, 0.2, 0), 8).key(life - 12, Holo.bar(a0, a0.add(sd * 0.1, 0, 0), 0.01, 0.01, 0), 8).spin(1.2).wobble(6, 0.05);
                        sat.add(w); sat.add(fr);
                    }
                    Holo.Elem arm = Holo.block(level, own, S, "minecraft:polished_blackstone", -1, Holo.bar(Vec3.ZERO, new Vec3(sd * 0.1, 0, 0), 0.01, 0.01, 0), life, 4);
                    arm.key(4, Holo.bar(new Vec3(sd * 7, 0, 0), new Vec3(sd * 78, 0, 0), 1.2, 1.2, 0), 8).key(life - 12, Holo.bar(Vec3.ZERO, new Vec3(sd * 0.1, 0, 0), 0.01, 0.01, 0), 8).spin(1.2).wobble(6, 0.05);
                    sat.add(arm);
                }
                Holo.ringT(level, own, S, 2, 30, 48, 1.6, 0.4, 0xFF2020, 20, 165, 12, Holo.rotX(0.45), 3, true);
                Holo.ringT(level, own, S, 2, 22, 40, 0.9, 0.3, 0xFFFFFF, 24, 160, 12, Holo.rotZ(1.15), -4.5, false);
                Holo.ringT(level, own, S, 2, 42, 56, 0.6, 0.3, 0xFF8A20, 28, 158, 12, Holo.I(), 2, true);
                Holo.orb(level, own, S.add(0, -14, 0), 7 * K, 0xFF2020, 20, 32, 4);
                sound(S, SoundEvents.END_PORTAL_SPAWN, 10f, 0.5f);
                Holo.sfx(level, eye, "deep", 2f, 0.8f);
                Holo.sfx(level, eye, "charge", 1.5f, 0.5f);
                Holo.screen(level, eye, 30, 5, 0xFF1010, 6000, 0.8f);
                sound(S, SoundEvents.BEACON_ACTIVATE, 10f, 0.4f);
            }
            if (t == 156) Holo.orb(level, own, S.add(0, -14, 0), 14 * K, 0xFF2020, 10, 28, 4);
            if (t == 110) for (int k = 0; k < 3; k++) {          // 위성 드론 3기
                double a = k * Math.PI * 2 / 3;
                Vec3 dp = S.add(Math.cos(a) * 34, -8, Math.sin(a) * 34);
                Holo.item(level, own, dp, "minecraft:repeating_command_block", true, 0xFF2020, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 190, 4)
                        .key(1, Holo.m(Holo.I(), 6, 6, 6, Vec3.ZERO, false), 10).key(180, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 8).spin(4).wobble(12, 0.12);
                Holo.ringT(level, own, dp, 1, 7, 20, 0.5, 0.15, 0xFF2020, 10, 160, 8, Holo.rotX(0.5), 6, true);
                Holo.glow(level, own, dp, 3, 0xFF2020, 10, 160, 8);
            }
            if (t >= 150 && t < 200 && t % 5 == 0) for (int k = 0; k < 3; k++) {     // 유도 레이저
                double a = k * Math.PI * 2 / 3;
                Vec3 dp = S.add(Math.cos(a) * 34, -8, Math.sin(a) * 34);
                Vec3 hitp = T.add((rnd2.nextDouble() - 0.5) * RADIUS, 0, (rnd2.nextDouble() - 0.5) * RADIUS);
                Holo.beamLayer(level, own, dp, hitp, 0.6, "minecraft:red_stained_glass", 0xFF2020, 1, 3, 3, k);
                Holo.beamGlow(level, own, dp, hitp, 3, 0xFF2020, 1, 3, 3);
                Holo.decal(level, own, hitp.add(0, 0.1, 0), "ring", 0xFF2020, 1, 8, 6, 2, 4, 0, Holo.I());
            }
            if (t == 196) Holo.orb(level, own, S.add(0, -14, 0), 22 * K, 0xFFFFFF, 4, 6, 8);
            if (t >= 100 && t < 200) {
                int k = t - 100;
                if (k % 4 == 0) {      // 하늘 사방에서 위성으로 빨려드는 에너지 줄기
                    double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * 1.2 - 0.3, rr = 90 + rnd.nextDouble() * 50;
                    Vec3 o = S.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
                    Holo.beamLayer(level, own, o, S, 0.8, k % 8 == 0 ? "minecraft:white_concrete" : "minecraft:red_stained_glass", 0xFF2020, 3, 2, 5, k);
                }
                for (int i = 0; i < 16; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2, rr = 40 + rnd.nextDouble() * 60;
                    Vec3 o = S.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
                    Vec3 v = S.subtract(o).scale(0.1);
                    level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
                }
                if (k % 10 == 0) sound(S, SoundEvents.BEACON_AMBIENT, 10f, 0.5f + k / 120f);
                if (k % 22 == 0) {
                    Vec3 g0 = T.add((rnd.nextDouble() - 0.5) * RADIUS * 1.6, 0, (rnd.nextDouble() - 0.5) * RADIUS * 1.6);
                    Holo.lightning(level, own, g0.add((rnd.nextDouble() - 0.5) * 20, 70, (rnd.nextDouble() - 0.5) * 20), g0, 0xFF3030, 1.4, 7);
                    sound(g0, SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.7f);
                }
            }

            // ── 200~300: 삭제 광선 하강 (실제로 위에서부터 내려오고 점점 굵어짐)
            if (t == 200) {
                sound(T, SoundEvents.WARDEN_SONIC_BOOM, 12f, 0.4f);
                Holo.sfx(level, eye, "boom", 2.5f, 0.5f);
                Holo.sfx(level, eye, "thunder", 2f, 0.7f);
                Holo.flare(level, own, S, 60 * K, 0xFF2020, 14);
                Holo.screen(level, eye, 30, 3, 0, 170, 1);
                Holo.screen(level, eye, 30, 4, 0, 1500, 1f);
                sound(T, SoundEvents.LIGHTNING_BOLT_THUNDER, 12f, 0.5f);
                shake(T, 300, true);
                Vec3 dn = T.subtract(S);
                String[] bl = {"minecraft:white_concrete", "minecraft:red_stained_glass", "minecraft:orange_stained_glass", "minecraft:white_stained_glass"};
                int[] gl = {0xFFFFFF, 0xFF2020, -1, 0xFFFFFF};
                double[] w0 = {2 * K, 4 * K, 6 * K, 8 * K}, w1 = {26 * K, 55 * K, 80 * K, 104 * K};
                for (int i = 0; i < 4; i++) {
                    Holo.Elem e = Holo.block(level, own, S, bl[i], gl[i], Holo.bar(Vec3.ZERO, dn.scale(0.01), w0[i] * 0.3, w0[i] * 0.3, i), 112, 0);
                    e.key(1, Holo.bar(Vec3.ZERO, dn, w0[i], w0[i], i * 0.6), 12).key(14, Holo.bar(Vec3.ZERO, dn, w1[i], w1[i], i * 0.6 + 0.4), 46)
                            .key(96, Holo.bar(dn, dn.scale(1.001), 0.01, 0.01, 0), 12);
                    beam.add(e);
                }
                Holo.light(level, T.add(0, 1, 0), 140);
            }
            if (t >= 200 && t < 300) {
                int k = t - 200;
                double w = 2 + Math.min(1, k / 60.0) * 98 * K;
                if (k > 12 && k % 5 == 0) {   // 광선을 타고 내려오는 고리
                    double hgt = (S.y - T.y - 20) * (1 - (k * 6 % 120) / 120.0);
                    Holo.ringT(level, own, T.add(0, hgt, 0), w * 0.45, w * 0.62, 40, 2.2, 0.4, k % 10 == 0 ? 0xFFFFFF : 0xFF8A20, 6, 0, 5, Holo.I(), 0, true);
                }
                if (k > 6 && k % 4 == 0) {
                    double s0 = (w * 0.62) / 0.43;
                    Holo.sprite(level, own, S.add(0, -2, 0), "ring", k % 8 == 0 ? 0xFFFFFF : 0xFF5020, "fixed", Holo.m(Holo.FLAT, s0 * 0.6, s0 * 0.6, s0 * 0.6, Vec3.ZERO, false), 16, 0)
                            .key(1, Holo.m(Holo.FLAT, s0, s0, s0, new Vec3(0, -(S.y - T.y) * 0.92, 0), false), 12).key(13, Holo.m(Holo.FLAT, s0 * 1.6, s0 * 1.6, s0, new Vec3(0, -(S.y - T.y) + 1, 0), false), 3);
                }
                if (k > 12 && k % 3 == 0) {
                    Vec3 g0 = T.add((rnd.nextDouble() - 0.5) * w, 0, (rnd.nextDouble() - 0.5) * w);
                    Holo.lightning(level, own, g0.add(0, 40 + rnd.nextDouble() * 40, 0), g0, k % 2 == 0 ? 0xFF3030 : 0xFFFFFF, 1.2, 6);
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, T.x, T.y + 1, T.z, 3, w * 0.3, 2, w * 0.3, 0);
                if (k % 5 == 0) { sound(T, SoundEvents.GENERIC_EXPLODE.value(), 12f, 0.4f); shake(T, 300, k > 50); }
            }

            // ── 260~340: 삭제 — 지름 100 구체 돔이 감싸고, 산산조각
            if (t == 256) {
                int made = 0;
                for (int tries = 0; tries < 600 && made < 90; tries++) {
                    double a = rnd2.nextDouble() * Math.PI * 2, b = Math.asin(rnd2.nextDouble() * 2 - 1), r = Math.cbrt(rnd2.nextDouble()) * RADIUS;
                    BlockPos bp = BlockPos.containing(T.x + Math.cos(a) * Math.cos(b) * r, T.y + Math.sin(b) * r, T.z + Math.sin(a) * Math.cos(b) * r);
                    var st = level.getBlockState(bp);
                    if (st.isAir() || !st.getFluidState().isEmpty() || st.getDestroySpeed(level, bp) < 0) continue;
                    String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                    Vec3 c = Vec3.atCenterOf(bp);
                    double[][] r1 = Holo.mul(Holo.rotX(rnd2.nextDouble() * 3), Holo.rotZ(rnd2.nextDouble() * 3));
                    Vec3 up = new Vec3((rnd2.nextDouble() - 0.5) * 6, 10 + rnd2.nextDouble() * 25, (rnd2.nextDouble() - 0.5) * 6);
                    int d0 = 4 + rnd2.nextInt(26);
                    Holo.block(level, own, c, name, made % 4 == 0 ? 0xFF3030 : -1, Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), d0 + 46, 0)
                            .key(d0, Holo.m(r1, 0.9, 0.9, 0.9, up.scale(0.5), true), 18).key(d0 + 18, Holo.m(Holo.mul(r1, Holo.rotY(2)), 0.01, 0.01, 0.01, up, true), 24);
                    if (made % 3 == 0) {
                        String bit = rnd2.nextBoolean() ? "1" : "0";
                        Holo.text(level, own, c.add(up), "{text:\"" + bit + "\",color:\"#FF4040\",bold:1b,font:\"minecraft:uniform\"}", "center", 0, true,
                                Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), d0 + 70, 0)
                                .key(d0 + 40, Holo.m(Holo.I(), 4, 4, 4, Vec3.ZERO, false), 3).key(d0 + 58, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 4, 0), false), 8);
                    }
                    made++;
                }
                Holo.sfx(level, eye, "data", 2f, 1f);
            }
            if (t >= 262 && t < 330 && t % 2 == 0)
                for (int k = 0; k < 3; k++) {
                    double a = rnd2.nextDouble() * Math.PI * 2, r = rnd2.nextDouble() * RADIUS;
                    Holo.codeDrop(level, own, T.add(Math.cos(a) * r, 30 + rnd2.nextDouble() * 20, Math.sin(a) * r), 30, k == 0 ? 0xFFFFFF : 0xFF3030, rnd2);
                }
            if (t == 280 || t == 300) Holo.sfx(level, eye, "data", 2f, 0.8f);
            if (t == 260) {
                sound(T, SoundEvents.END_GATEWAY_SPAWN, 16f, 0.4f);
                sound(T, SoundEvents.LIGHTNING_BOLT_THUNDER, 16f, 0.3f);
                cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..400] add gojo.fxflash_w", T.x, T.y, T.z));
                Holo.slowmo(level, 7, 22);
                Holo.sfx(level, eye, "boom", 3f, 0.4f);
                Holo.explosion(level, own, T, 16 * K, 0xFF3030);
                Holo.embers(level, own, T, 60, 30 * K, 0xFF5020, 120);
                Holo.screen(level, eye, 30, 2, 0, 2500, 9f);
                Holo.smoke(level, own, T.add(0, 4, 0), 30, 22 * K, -1, 40 * K, 90);
                double R = RADIUS + 2;
                for (int i = 0; i < 9; i++) {
                    double phi = Math.toRadians(-80 + 20 * i), dphi = Math.toRadians(20);
                    int n = Math.max(6, (int) Math.round(34 * Math.cos(phi)));
                    for (int j = 0; j < n; j++) {
                        double lam = j * Math.PI * 2 / n + i * 0.2;
                        double cp = Math.cos(phi), sp = Math.sin(phi), cl = Math.cos(lam), sl = Math.sin(lam);
                        Vec3 radial = new Vec3(cp * cl, sp, cp * sl), east = new Vec3(-sl, 0, cl), north = new Vec3(-sp * cl, cp, -sp * sl);
                        double[][] rm = {{east.x, north.x, radial.x}, {east.y, north.y, radial.y}, {east.z, north.z, radial.z}};
                        double pw = 2 * Math.PI * R * cp / n * 1.05, ph = R * dphi * 1.05;
                        Holo.Elem e = Holo.sprite(level, own, T, "hex", (i + j) % 3 == 0 ? 0xFF2020 : 0xFFFFFF, "fixed",
                                Holo.m(rm, 0.01, 0.01, 1, radial.scale(R * 0.2), false), 90, 0);
                        e.key(1 + Math.abs(i - 4), Holo.m(rm, pw, ph, 1, radial.scale(R), false), 6);
                        dome.add(e);
                    }
                }
                Holo.shock(level, own, T, 130 * K, 0xFFFFFF, 22);
                Holo.shock(level, own, T, 95 * K, 0xFF2020, 16);
                purgeEntities();
            }
            if (t >= 260 && slice <= RADIUS) {      // 틱당 약 4만 칸씩 나눠 지움 (서버 멈춤 방지) → 약 10초에 걸쳐 완료
                int budget = 40000;
                while (budget > 0 && slice <= RADIUS) budget -= deleteSlice(slice++);
            }
            if (t == 292) {   // 돔 산산조각
                for (Holo.Elem e : dome) {
                    double[][] rr = Holo.mul(Holo.rotX(rnd2.nextDouble() * 6), Holo.rotZ(rnd2.nextDouble() * 6));
                    Vec3 out = new Vec3(rnd2.nextDouble() - 0.5, rnd2.nextDouble() * 0.7, rnd2.nextDouble() - 0.5).normalize().scale(RADIUS * (1.4 + rnd2.nextDouble()));
                    double sz = 2 + rnd2.nextDouble() * 6;
                    e.keyIn(1, Holo.m(rr, sz, sz, 1, out, false), 22).keyIn(24, Holo.m(rr, 0.01, 0.01, 0.01, out.scale(1.2), false), 8).endIn(34);
                }
                for (int i = 0; i < 40; i++)
                    level.playSound(null, T.x + (rnd.nextDouble() - 0.5) * 100, T.y + rnd.nextDouble() * 50, T.z + (rnd.nextDouble() - 0.5) * 100,
                            SoundEvents.GLASS_BREAK, SoundSource.MASTER, 6f, 0.4f + rnd.nextFloat() * 0.6f);
                Holo.shock(level, own, T, 140 * K, 0xFFFFFF, 18);
                shake(T, 400, true);
                Holo.sfx(level, eye, "shatter", 3f, 0.5f);
            }
            if (t >= 260 && t < 340 && t % 6 == 0) shake(T, 400, true);

            // ── 320~400: 여파 — 달아오른 분화구 테두리, DELETED
            if (t == 320) {
                Holo.ringT(level, own, T.add(0, 0.5, 0), RADIUS - 2, RADIUS + 1, 64, 2.6, 0.5, 0xFF8A20, 10, 60, 10, Holo.I(), 0.3, true);
                Holo.ringT(level, own, T.add(0, 0.6, 0), RADIUS - 1, RADIUS + 2, 64, 0.8, 0.5, 0xFFD84A, 12, 58, 10, Holo.I(), -0.3, false);
            }
            if (t == 330) {
                Holo.circle(level, own, T.add(0, 1, 0), RADIUS * 0.8, 0xFF8A20, 66, 0);
                Holo.banner(level, own, T.add(0, 22 * K, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("DELETED", 0xFFFFFF, true) + "]}", 16 * K, 62);
                Holo.banner(level, own, T.add(0, 15 * K, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("d = " + (RADIUS * 2 + 1) + "  ·  rm -rf ./sphere  ✔", 0xFF5050, true) + "]}", 6 * K, 60);
                sound(T, SoundEvents.BEACON_DEACTIVATE, 10f, 0.4f);
            }
            if (t >= 320 && t < 400 && t % 3 == 0) {
                for (int i = 0; i < 20; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = RADIUS + rnd.nextDouble() * 10;
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, T.x + Math.cos(a) * r, T.y - rnd.nextDouble() * 30, T.z + Math.sin(a) * r, 0, 0, 0.1, 0, 1.0);
                }
                level.sendParticles(ParticleTypes.ASH, T.x, T.y + 10, T.z, 60, 40, 20, 40, 0);
                level.sendParticles(ParticleTypes.LAVA, T.x, T.y, T.z, 6, RADIUS * 0.7, 2, RADIUS * 0.7, 0);
            }
            if (t == 345) sound(T, SoundEvents.AMBIENT_CAVE.value(), 8f, 0.5f);

            extra(rnd);
            t++;
            if (t > LENGTH) end();
        }

        // ═══════════════════════════════════════ 확장 연출 (v0.22)
        final Vec3[] subSat = new Vec3[6];
        Vec3 G;                                   // 홀로 지구본 중심
        static double rGlobe() { return 18 * K; }

        /** 기존 장면 위에 겹쳐지는 추가 연출 + 400~560 피날레. */
        void extra(RandomSource rnd) {
            // ── 0~36: 보안 잠금 3겹이 차례로 맞물려 풀림 + 지문 스캔
            if (t == 1) {
                double[][] face = Holo.facing(F.scale(-1));
                int[] cols = {0xFF2020, 0xFFD84A, 0xFFFFFF};
                for (int k = 0; k < 3; k++) {
                    double sz = 1.8 + k * 0.7;
                    Holo.decal(level, own, B.add(0, 0.45, 0).subtract(F.scale(0.15 + k * 0.05)), k == 1 ? "circle" : "runering", cols[k], 0.1, sz, 4 + k * 2, 26 - k * 3, 5, (k % 2 == 0 ? 9 : -12) * (1 + k), face);
                }
                Holo.Elem scan = Holo.sprite(level, own, B.add(0, 0.25, 0), "beam", 0x55FF55, "fixed",
                        Holo.m(Holo.mul(Holo.facing(new Vec3(0, 1, 0)), Holo.rotZ(Math.PI / 2)), 0.01, 1.3, 1, Vec3.ZERO, false), 24, 0);
                for (int k = 0; k < 4; k++)
                    scan.key(2 + k * 5, Holo.m(Holo.mul(Holo.facing(new Vec3(0, 1, 0)), Holo.rotZ(Math.PI / 2)), 0.12, 1.3, 1, new Vec3(0, k % 2 == 0 ? 0.55 : -0.05, 0), false), 5);
                Holo.sfx(level, B, "data", 0.8f, 1.6f);
            }
            if (t == 8 || t == 14 || t == 20) {           // 잠금 해제 딸깍
                int k = (t - 8) / 6;
                Holo.flare(level, own, B.add(0, 0.45, 0), 1.6 + k * 0.6, k == 2 ? 0xFFFFFF : 0xFFD84A, 5);
                Holo.ringT(level, own, B.add(0, 0.45, 0), 0.4, 1.4 + k * 0.6, 16, 0.08, 0.02, 0xFFD84A, 3, 0, 3, Holo.facing(F), 0, true);
                sound(B, SoundEvents.IRON_DOOR_OPEN, 1f, 1.4f + k * 0.2f);
                Holo.sfx(level, B, "plate", 0.6f, 1.6f + k * 0.15f);
            }
            if (t == 24) {     // 누르는 순간: 임팩트 프레임 + 정적
                Holo.screen(level, eye, 10, 3, 0, 120, 1);
                Holo.screen(level, eye, 10, 4, 0, 500, 0.7f);
                Holo.sfx(level, B, "stinger", 1.2f, 0.7f);
            }

            // ── 44~210: 하늘에 떠오른 홀로 지구본 — 위도·경도 고리, 목표에 핑, 좌표망 데이터 줄기
            if (t == 44) {
                G = T.add(0, 62 * K, 0);
                double rG = 18 * K;
                int life = 170;
                for (int i = -2; i <= 2; i++) {     // 위도선
                    double lat = i * 0.5, rr = rG * Math.cos(lat);
                    Holo.ringT(level, own, G.add(0, rG * Math.sin(lat), 0), 0.5, rr, 30, 0.22, 0.05, i == 0 ? 0xFF2020 : 0x40E8FF, 10 + Math.abs(i) * 3, life - 30, 10, Holo.I(), 1.5, true);
                }
                for (int j = 0; j < 4; j++)          // 경도선
                    Holo.ringT(level, own, G, 0.5, rG, 30, 0.18, 0.05, 0x40E8FF, 14 + j * 3, life - 34, 10, Holo.mul(Holo.rotY(j * Math.PI / 4), Holo.rotZ(Math.PI / 2)), 1.5, false);
                Holo.ringT(level, own, G, 1, rG * 1.5, 40, 0.6, 0.08, 0xFF2020, 20, life - 40, 10, Holo.rotX(0.35), -3, true);   // 궤도 고리
                Holo.glow(level, own, G, 6, 0x40E8FF, 12, life - 24, 12);
                Holo.decal(level, own, G.add(0, -rG - 1, 0), "runering", 0x40E8FF, 1, rG * 2.6, 14, life - 28, 12, 2, Holo.I());
                for (int i = 0; i < 10; i++) {       // 지구본 둘레를 도는 좌표 문자열
                    double a = i * Math.PI / 5;
                    String hx = String.format(Locale.ROOT, "%04X:%04X", rnd2.nextInt(65536), rnd2.nextInt(65536));
                    Holo.text(level, own, G.add(Math.cos(a) * rG * 1.75, Math.sin(a * 2) * 3 * K, Math.sin(a) * rG * 1.75),
                            "{text:\"" + hx + "\",color:\"#40E8FF\",font:\"minecraft:uniform\"}", "center", 0x60000000, true,
                            Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), life, 0).key(10 + i * 2, Holo.m(Holo.I(), 6 * K, 6 * K, 6 * K, Vec3.ZERO, false), 6)
                            .key(life - 12, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 8);
                }
                Holo.sfx(level, eye, "magic", 1.6f, 0.6f);
                Holo.sfx(level, eye, "data", 1.6f, 0.7f);
            }
            if (G != null && t > 50 && t < 200) {
                if (t % 6 == 0) {                   // 목표로 내려가는 핑 + 착지 고리
                    Holo.beamGlow(level, own, G.add(0, -rGlobe(), 0), T.add(0, 0.5, 0), 0.8 * K, 0xFF2020, 1, 2, 4);
                    Holo.decal(level, own, T.add(0, 0.12, 0), "ring", 0xFF2020, 1, 14 * K, 4, 0, 6, 0, Holo.I());
                }
                if (t % 9 == 0) {                   // 지평선 너머에서 지구본으로 모이는 데이터 줄기
                    double a = rnd.nextDouble() * Math.PI * 2;
                    Vec3 o = T.add(Math.cos(a) * 160 * K, 10 + rnd.nextDouble() * 30, Math.sin(a) * 160 * K);
                    Holo.beamLayer(level, own, o, G, 0.5, "minecraft:light_blue_stained_glass", 0x40E8FF, 4, 2, 5, a);
                }
                if (t % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, G.x, G.y, G.z, 10, 12 * K, 12 * K, 12 * K, 0.02);
            }

            // ── 120~205: 보조 위성 6기 — 육각 배열, 헥사그램 연결, 거대 마법진, 중앙 집속
            if (t == 120) {
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    subSat[k] = S.add(Math.cos(a) * 62 * K, -4, Math.sin(a) * 62 * K);
                    Holo.item(level, own, subSat[k], k % 2 == 0 ? "minecraft:chain_command_block" : "minecraft:command_block", true, 0xFF2020,
                            Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 86, 2).key(2 + k * 3, Holo.m(Holo.I(), 9 * K, 9 * K, 9 * K, Vec3.ZERO, false), 10)
                            .key(78, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 6).spin(5).wobble(10, 0.1);
                    Holo.ringT(level, own, subSat[k], 1, 9, 18, 0.5, 0.12, 0xFFD84A, 10 + k * 3, 60, 8, Holo.rotX(0.6 + k * 0.2), 7, true);
                    Holo.glow(level, own, subSat[k], 4, 0xFF2020, 8, 66, 8);
                    Holo.flare(level, own, subSat[k], 14, 0xFF2020, 10);
                }
                Holo.sfx(level, eye, "riser", 2f, 0.5f);
            }
            if (t == 150) {                         // 육각 테두리 + 헥사그램
                for (int k = 0; k < 6; k++) {
                    Holo.beamLayer(level, own, subSat[k], subSat[(k + 1) % 6], 0.9, "minecraft:red_stained_glass", 0xFF2020, 6, 40, 6, k);
                    Holo.beamLayer(level, own, subSat[k], subSat[(k + 2) % 6], 0.5, "minecraft:white_concrete", 0xFFFFFF, 10 + k, 34 - k, 6, k);
                }
                Holo.decal(level, own, S.add(0, -6, 0), "circle", 0xFF2020, 1, 150 * K, 16, 34, 6, 1.5, Holo.I());
                Holo.decal(level, own, S.add(0, -5.8, 0), "runering", 0xFFFFFF, 1, 170 * K, 18, 32, 6, -2, Holo.I());
                Holo.sfx(level, eye, "choir", 2.4f, 0.6f);
                Holo.sfx(level, eye, "magic", 2f, 0.5f);
            }
            if (t >= 176 && t < 198 && t % 3 == 0) {          // 6기 동기화: 한 기씩 중앙으로 사격
                int k = (t - 176) / 3 % 6;
                if (subSat[k] != null) {
                    Holo.beamLayer(level, own, subSat[k], S.add(0, -14, 0), 1.4, "minecraft:white_concrete", 0xFF2020, 1, 22 - (t - 176), 4, k);
                    Holo.beamGlow(level, own, subSat[k], S.add(0, -14, 0), 5, 0xFF2020, 1, 22 - (t - 176), 4);
                    Holo.sfx(level, subSat[k], "laser", 3f, 0.6f + k * 0.08f);
                }
            }

            // ── 200~300: 광선을 감고 내려오는 나선 + 구역 가장자리에서 뜯겨 솟는 지면
            if (t >= 204 && t < 290 && t % 2 == 0) {
                double w = 2 + Math.min(1, (t - 200) / 60.0) * 98 * K;
                for (int k = 0; k < 2; k++) {
                    double hs = S.y - T.y - 10, a = t * 0.35 + k * Math.PI, h = hs - ((t * 7 * K + k * 60) % hs);
                    Vec3 q = T.add(Math.cos(a) * w * 0.55, h, Math.sin(a) * w * 0.55);
                    Holo.sprite(level, own, q, "orb", k == 0 ? 0xFFFFFF : 0xFF5020, "center", Holo.bb(w * 0.06 + 2, 0, Vec3.ZERO), 8, 0)
                            .key(1, Holo.bb(0.2, 0, new Vec3(0, -8, 0)), 7);
                }
            }
            if (t >= 214 && t < 262 && t % 4 == 0) {
                for (int k = 0; k < 3; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = RADIUS * (0.75 + rnd.nextDouble() * 0.25);
                    Vec3 c = T.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    String gb = com.tubilt.jjk.mech.CommandGuard.groundName(level, c.add(0, -0.5, 0));
                    double sz = (2 + rnd.nextDouble() * 3) * K;
                    double[][] r1 = Holo.mul(Holo.rotX(rnd.nextDouble() * 2), Holo.rotZ(rnd.nextDouble() * 2));
                    Vec3 up = new Vec3((T.x - c.x) * 0.3, (25 + rnd.nextDouble() * 30) * K, (T.z - c.z) * 0.3);
                    Holo.block(level, own, c, gb, -1, Holo.m(Holo.I(), sz, sz, sz, Vec3.ZERO, true), 34, 0)
                            .key(1, Holo.m(r1, sz, sz, sz, up.scale(0.6), true), 18).key(19, Holo.m(Holo.mul(r1, Holo.rotY(2)), 0.01, 0.01, 0.01, up, true), 14);
                }
            }

            // ── 400~560: 피날레 — 잔류 데이터 코어 → 강제 종료 → 하늘로 치솟는 코드 기둥 → 복귀
            if (t == 400) {
                Vec3 C = T.add(0, 6, 0);
                Holo.orb(level, own, C, 5, 0xFF2020, 20, 22, 4);
                Holo.ringT(level, own, C, 22, 2, 36, 0.4, 0.06, 0xFF2020, 40, 0, 4, Holo.rotX(0.5), 8, true);
                Holo.ringT(level, own, C, 18, 2, 30, 0.3, 0.06, 0xFFFFFF, 40, 0, 4, Holo.rotZ(1.1), -10, true);
                for (int i = 0; i < 14; i++) {
                    double a = i * Math.PI * 2 / 14;
                    Holo.text(level, own, C.add(Math.cos(a) * 12, Math.sin(a * 3) * 2, Math.sin(a) * 12),
                            "{text:\"" + (rnd2.nextBoolean() ? "0x" + Integer.toHexString(rnd2.nextInt(0xFFFF)).toUpperCase() : "NULL") + "\",color:\"#FF4040\",bold:1b,font:\"minecraft:uniform\"}",
                            "center", 0, true, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 48, 0)
                            .key(2 + i, Holo.m(Holo.I(), 5, 5, 5, Vec3.ZERO, false), 6).key(38, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 0, 0), false), 8);
                }
                Holo.sfx(level, eye, "deep", 2.5f, 0.5f);
                Holo.sfx(level, eye, "glitch", 2f, 0.6f);
                sound(T, SoundEvents.WARDEN_HEARTBEAT, 10f, 0.5f);
            }
            if (t > 400 && t < 444) {
                Vec3 C = T.add(0, 6, 0);
                if (t % 2 == 0) for (int i = 0; i < 20; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2, rr = 25 + rnd.nextDouble() * 25;
                    Vec3 o = C.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
                    Vec3 v = C.subtract(o).scale(0.08);
                    level.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
                }
                if (t % 10 == 0) sound(T, SoundEvents.WARDEN_HEARTBEAT, 10f, 0.5f + (t - 400) * 0.012f);
                if (t % 7 == 0) Holo.lightning(level, own, C, C.add((rnd.nextDouble() - 0.5) * 40, (rnd.nextDouble() - 0.3) * 20, (rnd.nextDouble() - 0.5) * 40), 0xFF3030, 0.8, 5);
            }
            if (t == 440) { Holo.orb(level, own, T.add(0, 6, 0), 1, 0xFFFFFF, 6, 1, 1); Holo.sfx(level, eye, "charge", 2.5f, 0.4f); }
            if (t == 448) {                       // kill -9: 강제 종료 섬광
                Vec3 C = T.add(0, 6, 0);
                Holo.flare(level, own, C, 90 * K, 0xFFFFFF, 18);
                Holo.shockTex(level, own, T.add(0, 1, 0), 170 * K, 0xFF2020, 20);
                Holo.shockTex(level, own, T.add(0, 2, 0), 120 * K, 0xFFFFFF, 14);
                Holo.ringT(level, own, C, 1, 80 * K, 64, 2.5 * K, 0.3, 0xFFFFFF, 8, 2, 12, Holo.I(), 0, true);
                Holo.ringT(level, own, C, 1, 60 * K, 56, 1.5 * K, 0.3, 0xFF2020, 9, 2, 12, Holo.rotX(1.57), 0, true);
                for (int k = 0; k < 3; k++)
                    Holo.beamLayer(level, own, T, T.add(0, 260, 0), new double[]{14 * K, 7 * K, 2.5 * K}[k], new String[]{"minecraft:red_stained_glass", "minecraft:white_stained_glass", "minecraft:white_concrete"}[k],
                            k == 1 ? -1 : 0xFFFFFF, 3, 40, 18, k);
                Holo.light(level, T.add(0, 2, 0), 60);
                Holo.screen(level, eye, 400, 3, 0, 200, 1);
                Holo.screen(level, eye, 400, 4, 0, 1600, 1);
                Holo.screen(level, eye, 400, 2, 0, 2000, 9f);
                cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..400] add gojo.fxflash_w", T.x, T.y, T.z));
                shake(T, 400, true);
                Holo.sfx(level, eye, "boom", 3f, 0.35f);
                Holo.sfx(level, eye, "stinger", 2.5f, 0.6f);
                Holo.sfx(level, eye, "choir", 2.5f, 0.5f);
                sound(T, SoundEvents.WARDEN_SONIC_BOOM, 16f, 0.5f);
                sound(T, SoundEvents.LIGHTNING_BOLT_THUNDER, 16f, 0.4f);
            }
            if (t >= 450 && t < 520 && t % 2 == 0)   // 하늘로 거꾸로 치솟는 코드 비
                for (int k = 0; k < 4; k++) {
                    double a = rnd2.nextDouble() * Math.PI * 2, r = rnd2.nextDouble() * 14 * K;
                    Vec3 b0 = T.add(Math.cos(a) * r, 2, Math.sin(a) * r);
                    Holo.text(level, own, b0, "{text:\"" + (rnd2.nextBoolean() ? "1" : "0") + "\",color:\"#" + (k == 0 ? "FFFFFF" : "FF4040") + "\",bold:1b,font:\"minecraft:uniform\"}", "center", 0, true,
                            Holo.m(Holo.I(), 4, 4, 4, Vec3.ZERO, false), 24, 0).key(1, Holo.m(Holo.I(), 6, 6, 6, new Vec3(0, 90 + rnd2.nextDouble() * 60, 0), false), 22);
                }
            if (t == 470) {
                Holo.banner(level, own, T.add(0, 34 * K, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("PROCESS TERMINATED", 0xFFFFFF, true) + "]}", 14 * K, 70);
                Holo.banner(level, own, T.add(0, 27 * K, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("exit code 0  ·  developer@button", 0xFF5050, true) + "]}", 5 * K, 68);
                for (int i = 0; i < 3; i++)
                    Holo.ringT(level, own, T.add(0, 3 + i * 4, 0), RADIUS * 0.4, RADIUS * (1.1 + i * 0.25), 60, 1.2, 0.2, i == 1 ? 0xFFFFFF : 0xFF2020, 30 + i * 8, 30, 14, Holo.I(), i % 2 == 0 ? 0.6 : -0.6, true);
                sound(T, SoundEvents.BEACON_DEACTIVATE, 12f, 0.3f);
            }
            if (t >= 470 && t < 540 && t % 3 == 0) level.sendParticles(ParticleTypes.FIREWORK, T.x, T.y + 40 * K, T.z, 30, 30 * K, 10 * K, 30 * K, 0.02);
            if (t == 536) {                        // 손에 쥔 버튼이 다시 접혀 인벤토리로
                Holo.flare(level, own, B.add(0, 0.3, 0), 3, 0xFF2020, 8);
                Holo.ringT(level, own, B.add(0, 0.3, 0), 1.6, 0.1, 18, 0.08, 0.02, 0xFF2020, 8, 0, 3, Holo.I(), 10, true);
                Holo.orb(level, own, B.add(0, 0.3, 0), 0.8, 0xFF2020, 3, 4, 4);
                sound(B, SoundEvents.IRON_TRAPDOOR_CLOSE, 1.2f, 0.7f);
                Holo.sfx(level, B, "plate", 0.8f, 0.9f);
            }
        }

        /** x = 중심 + dx 인 한 장의 원판을 지움. */
        int deleteSlice(int dx) {
            int cx = (int) Math.floor(T.x), cy = (int) Math.floor(T.y), cz = (int) Math.floor(T.z);
            int r2 = RADIUS * RADIUS - dx * dx, visited = 0;
            if (r2 < 0) return 1;
            int rr = (int) Math.sqrt(r2);
            int minY = level.getMinY(), maxY = level.getMaxY();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dy = -rr; dy <= rr; dy++) {
                int y = cy + dy;
                if (y < minY || y > maxY) continue;
                int rz = (int) Math.sqrt(Math.max(0, r2 - dy * dy));
                for (int dz = -rz; dz <= rz; dz++) {
                    visited++;
                    m.set(cx + dx, y, cz + dz);
                    var st = level.getBlockState(m);
                    if (st.isAir() || st.getDestroySpeed(level, m) < 0) continue;
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            return Math.max(1, visited);
        }

        /** 구역 청크 강제 로드 (forceload 는 한 번에 256청크까지 → 둘로 나눔). */
        boolean forced = false;
        void forceload(boolean add) {
            int x0 = (int) Math.floor(T.x) - RADIUS - 1, x1 = (int) Math.floor(T.x) + RADIUS + 1, z0 = (int) Math.floor(T.z) - RADIUS - 1, z1 = (int) Math.floor(T.z) + RADIUS + 1;
            int xm = (x0 + x1) / 2;
            cmd(String.format(Locale.ROOT, "forceload %s %d %d %d %d", add ? "add" : "remove", x0, z0, xm, z1));
            cmd(String.format(Locale.ROOT, "forceload %s %d %d %d %d", add ? "add" : "remove", xm + 1, z0, x1, z1));
            forced = add;
        }

        /** 구 안의 생물(플레이어 제외)·아이템·경험치 삭제. */
        void purgeEntities() {
            AABB box = new AABB(T, T).inflate(RADIUS);
            for (Entity e : level.getEntities((Entity) null, box, e -> e.position().distanceTo(T) <= RADIUS)) {
                if (e instanceof Player || e instanceof DevVehicle) continue;
                if (e instanceof LivingEntity le) { le.invulnerableTime = 0; le.hurtServer(level, level.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
                else if (e instanceof ItemEntity || e instanceof ExperienceOrb) e.discard();
            }
        }

        void ring(Vec3 c, double r, DustParticleOptions o, int step) {
            int n = (int) (r * 2.5);
            for (int i = 0; i < n; i += step) {
                double a = i / (double) n * Math.PI * 2;
                level.sendParticles(o, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }

        void shake(Vec3 at, int dist, boolean big) {
            cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..%d] add %s", at.x, at.y, at.z, dist, big ? "gojo.fxshake" : "gojo.fxshake_s"));
        }

        /** 버튼 덮개 같은 납작한 상자 (중심 기준). */
        static float[] slab(double w, double h) {
            return new float[]{(float) w, 0, 0, (float) (-w / 2), 0, (float) h, 0, (float) (-h / 2), 0, 0, (float) w, (float) (-w / 2), 0, 0, 0, 1};
        }

        void end() {
            if (owner != null) LaserFx.clear(level, owner);
            Holo.killOwner(own);
            if (weather) { cmd("weather clear"); weather = false; }
            if (forced) forceload(false);
            ServerPlayer p = p();
            if (p != null) {
                p.removeTag("jjk.cine");
                if (t >= LENGTH) p.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "§c§l삭제 완료 §7— 지름 %d블럭", RADIUS * 2 + 1)), true);
            }
        }
    }
}
