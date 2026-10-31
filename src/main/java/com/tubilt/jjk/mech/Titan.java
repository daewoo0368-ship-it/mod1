package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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

/**
 * 거인봇 무기.
 *  C(누르기) 화염방사기 — 오른팔 / Z 미사일 24발 — 양어깨 / V 폭탄 8발 — 등 박격포
 *  R 소형 아크레이저 — 양손 / X 아크레이저 — 가슴 아크 코어 / G 비행
 */
public final class Titan {
    private Titan() {}

    public static final int MISSILE_CD = 100, BOMB_CD = 160, SMALL_CD = 160, SMALL_TIME = 50, ARC_CD = 600, ARC_CHARGE = 60, ARC_TIME = 100;
    static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 4.0f);
    static final DustParticleOptions CYAN = new DustParticleOptions(0x40E8FF, 4.0f);
    static final DustParticleOptions BLUE = new DustParticleOptions(0x2A6BFF, 4.0f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(0xB45CFF, 4.0f);
    static final DustParticleOptions ORANGE = new DustParticleOptions(0xFF7A20, 3.0f);
    static final List<Bomb> BOMBS = new ArrayList<>();

    static final String[][] ARC_BEAM = {
            {"c0", "minecraft:white_concrete", "16777215"},
            {"c1", "minecraft:light_blue_stained_glass", "-1"},
            {"c2", "minecraft:cyan_stained_glass", "4253951"},
            {"c3", "minecraft:blue_stained_glass", "-1"},
            {"c4", "minecraft:purple_stained_glass", "11820287"},
            {"c5", "minecraft:white_stained_glass", "-1"}};
    static final String[][] ARC_RINGS = {
            {"r0", "minecraft:light_blue_stained_glass", "4253951"}, {"r1", "minecraft:purple_stained_glass", "11820287"},
            {"r2", "minecraft:light_blue_stained_glass", "4253951"}, {"r3", "minecraft:purple_stained_glass", "11820287"},
            {"r4", "minecraft:light_blue_stained_glass", "4253951"}, {"r5", "minecraft:purple_stained_glass", "11820287"}};
    static final String[][] ARC_ORB = {
            {"a1", "minecraft:white_concrete", "16777215"}, {"a2", "minecraft:light_blue_stained_glass", "-1"},
            {"a3", "minecraft:purple_stained_glass", "11820287"}, {"a4", "minecraft:white_stained_glass", "-1"}};
    static final String[][] ARC_HIT = {
            {"h1", "minecraft:white_stained_glass", "16777215"}, {"h2", "minecraft:light_blue_stained_glass", "4253951"},
            {"h3", "minecraft:purple_stained_glass", "-1"}};
    static final String[][] SMALL = {
            {"sl0", "minecraft:white_concrete", "16777215"}, {"sl1", "minecraft:light_blue_stained_glass", "4253951"},
            {"sr0", "minecraft:white_concrete", "16777215"}, {"sr1", "minecraft:light_blue_stained_glass", "4253951"}};

    static Vec3 aim(DevTitan t) {
        if (t.getControllingPassenger() instanceof ServerPlayer p) return p.getLookAngle().normalize();
        return t.fwd();
    }

    static Vec3 target(ServerLevel level, Vec3 from, Vec3 dir, double range) {
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? from.add(dir.scale(range)) : hit.getLocation();
    }

    static Entity owner(DevTitan t) { return t.getControllingPassenger() != null ? t.getControllingPassenger() : t; }

    static net.minecraft.world.damagesource.DamageSource src(ServerLevel level, DevTitan t) {
        return t.getControllingPassenger() instanceof ServerPlayer p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(t);
    }

    static void msg(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s), true); }

    static void shake(ServerLevel level, Vec3 at, int dist, boolean big) {
        MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..%d] add %s",
                at.x, at.y, at.z, dist, big ? "gojo.fxshake" : "gojo.fxshake_s"));
    }

    // ───────────── 입력
    public static void flame(ServerPlayer p, boolean on) { if (p.getVehicle() instanceof DevTitan t) t.flameOn = on; }

    public static void flight(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevTitan t)) return;
        t.setFlying(!t.flying());
        ServerLevel level = (ServerLevel) t.level();
        Vec3 c = t.position();
        if (t.flying()) {
            level.playSound(null, c.x, c.y + 50, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 6f, 0.3f);
            level.playSound(null, c.x, c.y + 50, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6f, 0.5f);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1, c.z, 80, 7, 1, 7, 0.08);
            msg(p, "§b§l비행 모드 §7— 보는 방향으로 W, 달리기로 가속, G로 착지");
        } else {
            level.playSound(null, c.x, c.y + 50, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 6f, 0.5f);
            msg(p, "§7비행 해제 — 낙하");
        }
    }

    public static void missiles(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevTitan t)) return;
        if (t.missileCd > 0) { msg(p, String.format(Locale.ROOT, "§7미사일 재장전 §f%.1f초", t.missileCd / 20f)); return; }
        t.missileCd = MISSILE_CD;
        ServerLevel level = (ServerLevel) t.level();
        Vec3 a = aim(t), eye = p.getEyePosition();
        Vec3 tgt = target(level, eye, a, 300);
        List<LivingEntity> locks = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(tgt, tgt).inflate(24),
                e -> e != p && e != t && e.isAlive() && !(e instanceof Player)));
        RandomSource rnd = level.random;
        for (int i = 0; i < 24; i++) {
            int side = i % 2 == 0 ? -1 : 1;
            Vec3 pod = t.position().add(0, 285 * DevTitan.S, 0).add(t.right().scale(52 * DevTitan.S * side))
                    .add(t.fwd().scale((rnd.nextDouble() - 0.5) * 8));
            Vec3 launch = new Vec3(0, 1.4 + rnd.nextDouble() * 0.5, 0).add(t.right().scale(side * (0.3 + rnd.nextDouble() * 0.4)));
            Entity lock = locks.isEmpty() ? null : locks.get(i % locks.size());
            MechManager.MISSILES.add(new Missile(level, p.getUUID(), pod, launch,
                    tgt.add((rnd.nextDouble() - 0.5) * 24, 0, (rnd.nextDouble() - 0.5) * 24), lock, i));
        }
        level.playSound(null, t.getX(), t.getY() + 80, t.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 6f, 0.5f);
        msg(p, "§6§l미사일 24발 발사!" + (locks.isEmpty() ? "" : " §c락온 " + Math.min(24, locks.size())));
    }

    public static void bombs(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevTitan t)) return;
        if (t.bombCd > 0) { msg(p, String.format(Locale.ROOT, "§7폭탄 장전 §f%.1f초", t.bombCd / 20f)); return; }
        t.bombCd = BOMB_CD;
        ServerLevel level = (ServerLevel) t.level();
        Vec3 tgt = target(level, p.getEyePosition(), aim(t), 400);
        Vec3 mouth = t.position().add(0, 302 * DevTitan.S, 0).add(t.fwd().scale(-32 * DevTitan.S));
        RandomSource rnd = level.random;
        for (int i = 0; i < 8; i++) {
            Vec3 to = tgt.add((rnd.nextDouble() - 0.5) * 30, 0, (rnd.nextDouble() - 0.5) * 30);
            BOMBS.add(new Bomb(level, owner(t).getUUID(), t.getUUID(), mouth, to, i * 5));
        }
        msg(p, "§c§l폭탄 8발 발사!");
    }

    public static void smallArc(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevTitan t)) return;
        if (t.smallCd > 0 || t.smallT > 0) { if (t.smallCd > 0) msg(p, String.format(Locale.ROOT, "§7소형 아크 냉각 §f%.1f초", t.smallCd / 20f)); return; }
        t.smallCd = SMALL_CD;
        t.smallT = SMALL_TIME + 10;
        t.setHands(3);
        ServerLevel level = (ServerLevel) t.level();
        LaserFx.spawn(level, t, SMALL, t.position());
        level.playSound(null, t.getX(), t.getY() + 70, t.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5f, 1.4f);
        msg(p, "§b§l소형 아크레이저");
    }

    public static void arc(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevTitan t)) return;
        if (t.arcCd > 0 || t.arcCharge > 0 || t.arcTime > 0) { if (t.arcCd > 0) msg(p, String.format(Locale.ROOT, "§7아크레이저 냉각 §f%.1f초", t.arcCd / 20f)); return; }
        t.arcCd = ARC_CD;
        t.arcCharge = ARC_CHARGE;
        ServerLevel level = (ServerLevel) t.level();
        LaserFx.clear(level, t);
        LaserFx.spawn(level, t, ARC_ORB, t.chest());
        if (!level.isThundering()) { MechManager.cmd(level.getServer(), level, "weather thunder 600"); t.weatherSet = true; }
        Vec3 c = t.chest();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 10f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 10f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 10f, 0.6f);
        msg(p, "§b§l아크레이저 §f— §3아크 코어 임계 돌파 중...");
    }

    // ───────────── 틱
    static void tick(DevTitan t) {
        ServerLevel level = (ServerLevel) t.level();
        if (t.missileCd > 0) t.missileCd--;
        if (t.bombCd > 0) t.bombCd--;
        if (t.smallCd > 0) t.smallCd--;
        if (t.arcCd > 0) t.arcCd--;
        if (t.flying()) thrusters(t, level);
        if (t.flameOn) flameTick(t, level);
        if (t.smallT > 0) smallTick(t, level);
        if (t.arcCharge > 0) arcCharge(t, level);
        else if (t.arcTime > 0) arcBeam(t, level);
        // 비행 해제 후 착지 충격
        if (!t.flying() && !t.onGround()) t.fallT++;
        else { if (t.fallT > 15) landing(t, level); t.fallT = 0; }
    }

    static void tickGlobal(MinecraftServer server) {
        for (Iterator<Bomb> it = BOMBS.iterator(); it.hasNext(); ) {
            Bomb b = it.next();
            try { b.tick(); } catch (Exception e) { b.dead = true; b.discard(); }
            if (b.dead) it.remove();
        }
    }

    static void cleanup(DevTitan t, ServerLevel level) {
        LaserFx.clear(level, t);
        if (t.weatherSet) { MechManager.cmd(level.getServer(), level, "weather clear"); t.weatherSet = false; }
    }

    static void thrusters(DevTitan t, ServerLevel level) {
        Vec3 up = new Vec3(0, 1, 0);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 n = t.position().add(0, 184 * DevTitan.S, 0).add(t.fwd().scale(-42 * DevTitan.S)).add(t.right().scale(26 * DevTitan.S * s));
            level.sendParticles(ParticleTypes.FLAME, n.x, n.y, n.z, 10, 1.0, 0.5, 1.0, 0.05);
            level.sendParticles(CYAN, n.x, n.y - 1, n.z, 4, 1.2, 0.8, 1.2, 0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, n.x, n.y - 4, n.z, 2, 1.5, 1.5, 1.5, 0.02);
            Vec3 f = t.position().add(t.right().scale(30 * DevTitan.S * s)).add(0, 1, 0);       // 발바닥 분사
            level.sendParticles(ParticleTypes.FLAME, f.x, f.y, f.z, 6, 1.5, 0.3, 1.5, 0.04);
        }
        if (t.tickCount % 8 == 0) level.playSound(null, t.getX(), t.getY() + 50, t.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 6f, 0.4f);
        if (t.tickCount % 20 == 0) level.playSound(null, t.getX(), t.getY() + 50, t.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 5f, 0.5f);
    }

    static void landing(DevTitan t, ServerLevel level) {
        Vec3 c = t.position();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 8f, 0.3f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 6f, 0.3f);
        for (int i = 0; i < 3; i++) Laser.ring(level, c.add(0, 0.5, 0), 8 + i * 8);
        Slam.dustRing(level, c, 14, Slam.ground(level, c));
        stomp(t, level, c.x, c.y, c.z);
        shake(level, c, 120, true);
    }

    /** 발 디딤: 반경 5칸 몹 즉사 + 땅울림. */
    static void stomp(DevTitan t, ServerLevel level, double x, double y, double z) {
        AABB box = new AABB(x - 5, y - 2, z - 5, x + 5, y + 4, z + 5);
        var src = src(level, t);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != t && !(e instanceof Player) && e.getVehicle() != t && !e.isInvulnerable())) {
            v.invulnerableTime = 0;
            v.hurtServer(level, src, Math.max(1000f, v.getMaxHealth() * 2));
        }
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 1, z, 6, 4, 0.5, 4, 0);
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 40, 6, 0.2, 6, 0.1);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Slam.ground(level, new Vec3(x, y, z))),
                x, y + 1, z, 60, 5, 1, 5, 0.4);
        shake(level, new Vec3(x, y, z), 60, false);
    }

    // ── 화염방사기 (오른팔)
    static void flameTick(DevTitan t, ServerLevel level) {
        Vec3 a = aim(t), n = t.nozzle(a);
        RandomSource rnd = level.random;
        for (int i = 0; i < 30; i++) {
            Vec3 v = a.add(rnd.nextGaussian() * 0.08, rnd.nextGaussian() * 0.08, rnd.nextGaussian() * 0.08).scale(1.6 + rnd.nextDouble() * 1.4);
            level.sendParticles(i % 5 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, n.x, n.y, n.z, 0, v.x, v.y, v.z, 1.0);
        }
        for (int i = 0; i < 6; i++) {
            Vec3 q = n.add(a.scale(6 + rnd.nextDouble() * 34)).add(rnd.nextGaussian() * 2, rnd.nextGaussian() * 2, rnd.nextGaussian() * 2);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 1, 0.5, 0.5, 0.5, 0.03);
            level.sendParticles(ORANGE, q.x, q.y, q.z, 2, 1.0, 1.0, 1.0, 0);
        }
        if (t.tickCount % 3 == 0) level.playSound(null, n.x, n.y, n.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 4f, 0.5f);
        if (t.tickCount % 5 == 0) level.playSound(null, n.x, n.y, n.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 4f, 0.4f);
        // 원뿔 판정 (사거리 45칸, 반각 약 14도)
        if (t.tickCount % 2 == 0) {
            var src = level.damageSources().inFire();
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(n, n.add(a.scale(45))).inflate(12),
                    e -> e.isAlive() && e != t && e.getVehicle() != t)) {
                Vec3 to = le.position().add(0, le.getBbHeight() / 2, 0).subtract(n);
                double d = to.length();
                if (d > 45 || to.normalize().dot(a) < 0.97) continue;
                le.invulnerableTime = 0;
                le.hurtServer(level, t.getControllingPassenger() instanceof ServerPlayer p ? level.damageSources().playerAttack(p) : src, 14f);
                le.igniteForSeconds(10);
            }
        }
        // 지형에 불 (#grief)
        if (MechManager.griefOn(level.getServer()) && t.tickCount % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                Vec3 dir = a.add(rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12).normalize();
                BlockHitResult h = level.clip(new ClipContext(n, n.add(dir.scale(45)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (h.getType() == HitResult.Type.MISS) continue;
                BlockPos fp = h.getBlockPos().relative(h.getDirection());
                if (level.getBlockState(fp).isAir()) level.setBlock(fp, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    // ── 소형 아크레이저 (양손)
    static void smallTick(DevTitan t, ServerLevel level) {
        t.smallT--;
        Vec3 a = aim(t);
        RandomSource rnd = level.random;
        int fireT = SMALL_TIME + 10 - t.smallT;           // 0~10 충전, 이후 발사
        Vec3 tgt = target(level, t.getControllingPassenger() instanceof ServerPlayer p ? p.getEyePosition() : t.chest(), a, 300);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 palm = t.palm(s, a);
            String L = s < 0 ? "sl" : "sr";
            if (fireT < 10) {      // 손바닥에 에너지
                level.sendParticles(CYAN, palm.x, palm.y, palm.z, 6, 1.0, 1.0, 1.0, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, palm.x, palm.y, palm.z, 8, 1.4, 1.4, 1.4, 0.2);
                continue;
            }
            Vec3 dir = tgt.subtract(palm).normalize();
            BlockHitResult hit = level.clip(new ClipContext(palm, palm.add(dir.scale(300)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean blocked = hit.getType() != HitResult.Type.MISS;
            Vec3 stop = blocked ? hit.getLocation() : palm.add(dir.scale(300));
            double len = stop.distanceTo(palm), pulse = 1 + Math.sin(fireT * 0.9) * 0.15;
            LaserFx.set(level, t, L + "0", palm, LaserFx.beam(dir, len, 1.6 * pulse, fireT * 0.2));
            LaserFx.set(level, t, L + "1", palm, LaserFx.beam(dir, len, 4.0 * pulse, -fireT * 0.15));
            for (double d = 2; d < len; d += 3) {
                Vec3 q = palm.add(dir.scale(d));
                level.sendParticles(CYAN, q.x, q.y, q.z, 1, 0.6, 0.6, 0.6, 0);
                if (rnd.nextInt(3) == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.8, 0.8, 0.8, 0.1);
            }
            if (t.tickCount % 2 == 0) for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(palm, stop).inflate(4),
                    e -> e.isAlive() && e != t && e.getVehicle() != t)) {
                if (le.getBoundingBox().inflate(3.5).clip(palm, stop).isEmpty()) continue;
                le.invulnerableTime = 0;
                le.hurtServer(level, src(level, t), 150f);
                le.igniteForSeconds(8);
            }
            if (blocked) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y, stop.z, 1, 0, 0, 0, 0);
                level.sendParticles(CYAN, stop.x, stop.y, stop.z, 10, 2, 2, 2, 0);
                boolean grief = MechManager.griefOn(level.getServer());
                if (fireT % 3 == 0) level.explode(owner(t), stop.x, stop.y, stop.z, 7f, false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                if (grief) carve(level, stop, 4, rnd);
            }
        }
        if (fireT == 10) level.playSound(null, t.getX(), t.getY() + 70, t.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 1.2f);
        if (fireT > 10 && fireT % 6 == 0) level.playSound(null, t.getX(), t.getY() + 70, t.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 5f, 1.6f);
        if (t.smallT == 0) {
            LaserFx.kill(level, t, "sl0"); LaserFx.kill(level, t, "sl1"); LaserFx.kill(level, t, "sr0"); LaserFx.kill(level, t, "sr1");
            t.setHands(0);
        }
    }

    // ── 아크레이저 (가슴): 충전 3초
    static void arcCharge(DevTitan t, ServerLevel level) {
        int c = ARC_CHARGE - --t.arcCharge;
        double k = c / (double) ARC_CHARGE;
        Vec3 e = t.chest();
        RandomSource rnd = level.random;
        double fl = 1 + Math.sin(c * 1.1) * 0.12;
        LaserFx.set(level, t, "a1", e, LaserFx.cube((2 + 8 * k) * fl, c * 0.3, c * 0.21));
        LaserFx.set(level, t, "a2", e, LaserFx.cube((4 + 14 * k) * fl, -c * 0.17, c * 0.33));
        LaserFx.set(level, t, "a3", e, LaserFx.cube((6 + 20 * k) / fl, c * 0.11, -c * 0.27));
        LaserFx.set(level, t, "a4", e, LaserFx.cube((8 + 26 * k), -c * 0.07, c * 0.09));
        // 사방에서 빨려드는 에너지 (반경 수십 칸)
        for (int i = 0; i < 20 + (int) (k * 40); i++) {
            double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2, rr = 20 + rnd.nextDouble() * 60;
            Vec3 o = e.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
            Vec3 v = e.subtract(o).scale(0.12);
            level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
        }
        // 땅에서 솟는 잔해 + 충격파 고리
        if (c % 4 == 0) {
            Vec3 g = t.position();
            for (int i = 0; i < 30; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 10 + rnd.nextDouble() * 50;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, g.x + Math.cos(a) * r, g.y + 0.5, g.z + Math.sin(a) * r, 0, 0, 0.15, 0, 1.0);
            }
            Laser.ring(level, g.add(0, 0.5, 0), 10 + k * 50);
        }
        if (c % 5 == 0) {     // 주변에 번개
            double a = rnd.nextDouble() * Math.PI * 2, r = 15 + rnd.nextDouble() * 40;
            Laser.bolt(level, t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        if (c % 10 == 0) level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 10f, 0.5f + (float) k);
        if (c % 20 == 0) shake(level, e, 150, false);
        if (t.arcCharge == 0) {   // 발사!
            t.arcTime = ARC_TIME;
            LaserFx.spawn(level, t, ARC_BEAM, e);
            LaserFx.spawn(level, t, ARC_RINGS, e);
            LaserFx.spawn(level, t, ARC_HIT, e);
            t.triggerAnim("main", "recoil");
            level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 12f, 0.4f);
            level.playSound(null, e.x, e.y, e.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 12f, 0.6f);
            level.playSound(null, e.x, e.y, e.z, SoundEvents.END_GATEWAY_SPAWN, SoundSource.PLAYERS, 12f, 0.5f);
            level.playSound(null, e.x, e.y, e.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 12f, 0.4f);
            for (int i = 0; i < 4; i++) Laser.ring(level, t.position().add(0, 1, 0), 15 + i * 15);
            for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; Laser.bolt(level, t.position().add(Math.cos(a) * 30, 0, Math.sin(a) * 30)); }
            shake(level, e, 400, true);
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..400] add gojo.fxflash_w", e.x, e.y, e.z));
        }
    }

    // ── 아크레이저 발사 5초
    static void arcBeam(DevTitan t, ServerLevel level) {
        t.arcTime--;
        int k = ARC_TIME - t.arcTime;
        Vec3 e = t.chest(), a = aim(t);
        RandomSource rnd = level.random;
        Vec3 from = t.getControllingPassenger() instanceof ServerPlayer p ? p.getEyePosition() : e;
        Vec3 tgt = target(level, from, a, 600);
        Vec3 dir = tgt.subtract(e).normalize();
        BlockHitResult hit = level.clip(new ClipContext(e, e.add(dir.scale(600)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 stop = blocked ? hit.getLocation() : e.add(dir.scale(600));
        double len = stop.distanceTo(e);
        double pulse = 1 + Math.sin(k * 0.7) * 0.15, fl = 1 + (rnd.nextDouble() - 0.5) * 0.15;

        // 6겹 실체 빔 (최대 지름 약 40칸)
        LaserFx.set(level, t, "c0", e, LaserFx.beam(dir, len, 6 * pulse * fl, k * 0.25));
        LaserFx.set(level, t, "c1", e, LaserFx.beam(dir, len, 12 * pulse, -k * 0.15));
        LaserFx.set(level, t, "c2", e, LaserFx.beam(dir, len, 19 * pulse, k * 0.12));
        LaserFx.set(level, t, "c3", e, LaserFx.beam(dir, len, 26 * (2 - pulse), -k * 0.09));
        LaserFx.set(level, t, "c4", e, LaserFx.beam(dir, len, 33 * fl, k * 0.07));
        LaserFx.set(level, t, "c5", e, LaserFx.beam(dir, len, 40 * (2 - pulse) * fl, -k * 0.05));
        // 빔을 따라 흘러가는 회전 고리 6개
        for (int i = 0; i < 6; i++) {
            double d = ((k * 6 + i * len / 6) % Math.max(1, len));
            LaserFx.set(level, t, "r" + i, e.add(dir.scale(d)), LaserFx.cube(46 + Math.sin(k * 0.3 + i) * 6, k * 0.2 + i, k * 0.13 - i));
        }
        // 가슴 코어 구체 (요동)
        LaserFx.set(level, t, "a1", e, LaserFx.cube(10 * fl, k * 0.4, k * 0.3));
        LaserFx.set(level, t, "a2", e, LaserFx.cube(18 * pulse, -k * 0.3, k * 0.5));
        LaserFx.set(level, t, "a3", e, LaserFx.cube(26 / pulse, k * 0.2, -k * 0.4));
        LaserFx.set(level, t, "a4", e, LaserFx.cube(34 * fl, -k * 0.1, k * 0.15));
        // 착탄 에너지 돔
        double hs = blocked ? 1 : 0.001;
        LaserFx.set(level, t, "h1", stop, LaserFx.cube((30 + rnd.nextDouble() * 8) * hs, k * 0.5, k * 0.3));
        LaserFx.set(level, t, "h2", stop, LaserFx.cube((44 + Math.sin(k * 0.6) * 8) * hs, -k * 0.3, k * 0.2));
        LaserFx.set(level, t, "h3", stop, LaserFx.cube((58 + Math.sin(k * 0.4) * 10) * hs, k * 0.15, -k * 0.25));

        // 파티클: 나선·번개 아크·소닉붐
        Vec3 side = dir.cross(new Vec3(0, 1, 0)); if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0); side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (double d = 4; d < len; d += 4) {
            double ang = d * 0.08 + k * 0.5, r = 24;
            Vec3 b = e.add(dir.scale(d));
            level.sendParticles(CYAN, b.x + side.x * Math.cos(ang) * r + up.x * Math.sin(ang) * r, b.y + side.y * Math.cos(ang) * r + up.y * Math.sin(ang) * r,
                    b.z + side.z * Math.cos(ang) * r + up.z * Math.sin(ang) * r, 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(VIOLET, b.x - side.x * Math.cos(ang) * r - up.x * Math.sin(ang) * r, b.y - side.y * Math.cos(ang) * r - up.y * Math.sin(ang) * r,
                    b.z - side.z * Math.cos(ang) * r - up.z * Math.sin(ang) * r, 2, 0.5, 0.5, 0.5, 0);
        }
        for (int arc = 0; arc < 12; arc++) {
            Vec3 cur = e.add(dir.scale(rnd.nextDouble() * len));
            Vec3 out = side.scale(rnd.nextDouble() * 2 - 1).add(up.scale(rnd.nextDouble() * 2 - 1)).normalize();
            for (int s = 0; s < 10; s++) {
                cur = cur.add(out.scale(3)).add((rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 3);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cur.x, cur.y, cur.z, 2, 0.2, 0.2, 0.2, 0.1);
                if (s % 2 == 0) level.sendParticles(WHITE, cur.x, cur.y, cur.z, 1, 0, 0, 0, 0);
            }
        }
        if (k % 3 == 0) for (double d = 10 + (k % 20); d < len; d += 20) {
            Vec3 b = e.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SONIC_BOOM, b.x, b.y, b.z, 3, 4, 4, 4, 0);
        }

        // 피해: 빔 반경 20칸 안 모든 생물 (0.1초마다 5000)
        if (k % 2 == 0) for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(e, stop).inflate(22),
                x -> x.isAlive() && x != t && x.getVehicle() != t)) {
            if (le.getBoundingBox().inflate(20).clip(e, stop).isEmpty()) continue;
            le.invulnerableTime = 0;
            le.hurtServer(level, src(level, t), 5000f);
            le.igniteForSeconds(20);
            le.setDeltaMovement(dir.scale(3).add(0, 1.5, 0));
            le.hurtMarked = true;
        }
        // 착탄: 초대형 연쇄 폭발 + 지형 증발 + 번개
        if (blocked) {
            boolean grief = MechManager.griefOn(level.getServer());
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y, stop.z, 8, 10, 6, 10, 0);
            level.sendParticles(ParticleTypes.LAVA, stop.x, stop.y, stop.z, 30, 10, 4, 10, 0);
            level.sendParticles(WHITE, stop.x, stop.y, stop.z, 40, 14, 10, 14, 0);
            if (k % 4 == 0) level.explode(owner(t), stop.x, stop.y, stop.z, 20f, grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            if (grief && k % 2 == 0) carve(level, stop.add(dir.scale(4)), 11, rnd);
            if (k % 5 == 0) Laser.bolt(level, stop.add((rnd.nextDouble() - 0.5) * 40, 0, (rnd.nextDouble() - 0.5) * 40));
            if (k % 6 == 0) Laser.ring(level, stop, 10 + (k % 30));
        }
        if (k % 4 == 0) level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 10f, 0.5f + rnd.nextFloat() * 0.3f);
        if (k % 3 == 0) level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 10f, 0.4f);
        if (k % 10 == 0) shake(level, e, 400, true);

        if (t.arcTime == 0) {    // 종말의 일격
            LaserFx.clear(level, t);
            level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 10f, 0.5f);
            if (blocked) {
                boolean grief = MechManager.griefOn(level.getServer());
                var lt = grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
                level.explode(owner(t), stop.x, stop.y, stop.z, 40f, grief, lt);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4;
                    level.explode(owner(t), stop.x + Math.cos(ang) * 25, stop.y, stop.z + Math.sin(ang) * 25, 20f, grief, lt);
                    Laser.bolt(level, stop.add(Math.cos(ang) * 20, 0, Math.sin(ang) * 20));
                }
                if (grief) carve(level, stop, 18, rnd);
                for (int i = 0; i < 6; i++) Laser.ring(level, stop.add(0, 1, 0), 10 + i * 12);
                for (int i = 0; i < 30; i++) level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y + i * 3, stop.z, 2, 3, 1, 3, 0);
                for (int i = 0; i < 80; i++) {
                    double ang = i / 80.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.EXPLOSION, stop.x + Math.cos(ang) * 30, stop.y + 90, stop.z + Math.sin(ang) * 30, 3, 6, 3, 6, 0);
                    level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, stop.x + Math.cos(ang) * 26, stop.y + 85, stop.z + Math.sin(ang) * 26, 2, 5, 3, 5, 0.02);
                }
                level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 16f, 0.3f);
                shake(level, stop, 500, true);
            }
            if (t.weatherSet) { MechManager.cmd(level.getServer(), level, "weather clear"); t.weatherSet = false; }
        }
    }

    /** 지형 증발 (갱신 최소화: flag 2) + 테두리 그을림. */
    static void carve(ServerLevel level, Vec3 c, double r, RandomSource rnd) {
        int R = (int) Math.ceil(r + 2);
        BlockPos o = BlockPos.containing(c);
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d > r + 2) continue;
            BlockPos bp = o.offset(x, y, z);
            var st = level.getBlockState(bp);
            if (st.isAir() || st.getDestroySpeed(level, bp) < 0) continue;
            if (d <= r) level.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
            else if (rnd.nextFloat() < 0.3f) level.setBlock(bp, (rnd.nextBoolean() ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState(), 2);
        }
    }

    /** 박격포 폭탄: 포물선 비행 → 착탄 대폭발. */
    static class Bomb {
        static int counter = 0;
        final ServerLevel level; final java.util.UUID owner, vehicle; final String tag;
        Vec3 pos, vel; int delay, life; boolean dead, spawned;
        Bomb(ServerLevel level, java.util.UUID owner, java.util.UUID vehicle, Vec3 from, Vec3 to, int delay) {
            this.level = level; this.owner = owner; this.vehicle = vehicle; this.pos = from; this.delay = delay;
            this.tag = "jjk.bomb" + (counter++);
            double T = 50, g = 0.06;
            this.vel = new Vec3((to.x - from.x) / T, (to.y - from.y + 0.5 * g * T * T) / T, (to.z - from.z) / T);
        }
        void tick() {
            if (delay > 0) { delay--; return; }
            if (!spawned) {
                spawned = true;
                MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                        "summon item_display %.2f %.2f %.2f {Tags:[\"jjk.mechpart\",\"%s\"],item:{id:\"minecraft:tnt\",count:1},teleport_duration:1,view_range:8f,"
                                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[5f,5f,5f]}}",
                        pos.x, pos.y, pos.z, tag));
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 5f, 1.4f);
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 1, 1, 1, 0);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 20, 2, 1, 2, 0.05);
            }
            life++;
            vel = vel.add(0, -0.06, 0);
            Vec3 next = pos.add(vel);
            BlockHitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS || life > 200) { explode(hit.getType() != HitResult.Type.MISS ? hit.getLocation() : next); return; }
            pos = next;
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.4, 0.4, 0.4, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 2, 0.5, 0.5, 0.5, 0.01);
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tp @e[tag=%s,limit=1] %.2f %.2f %.2f %.1f %.1f",
                    tag, pos.x, pos.y, pos.z, (float) (life * 12 % 360), (float) (life * 7 % 180 - 90)));
        }
        void explode(Vec3 at) {
            dead = true;
            discard();
            boolean grief = MechManager.griefOn(level.getServer());
            level.explode(level.getEntity(owner), at.x, at.y, at.z, 9f, grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 3, 3, 2, 3, 0);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, 4, 2, 4, 0.2);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 12, 3, 1, 3, 0);
            Laser.ring(level, at, 8);
            shake(level, at, 60, false);
        }
        void discard() { MechManager.cmd(level.getServer(), level, "kill @e[tag=" + tag + "]"); }
    }
}
