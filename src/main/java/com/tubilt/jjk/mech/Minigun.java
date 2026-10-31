package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 턱 밑 6총열 미니건 (C 누르고 있기).
 * 총열 회전이 올라오면(0.4초) 틱당 2발 히트스캔 사격 = 초당 40발.
 * 계속 쏘면 열이 쌓여 10초 뒤 과열 → 3초 냉각.
 */
public final class Minigun {
    private Minigun() {}

    public static final int MAX_SPIN = 20, MAX_HEAT = 200, OVERHEAT_TICKS = 60;
    static final float DAMAGE = 7f;
    static final double RANGE = 96, SPREAD = 0.035;
    static final DustParticleOptions TRACER = new DustParticleOptions(0xFFD040, 1.1f);
    static final DustParticleOptions FLASH = new DustParticleOptions(0xFFF4C0, 2.6f);
    static final BlockParticleOption CASING = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GOLD_BLOCK.defaultBlockState());

    public static void trigger(ServerPlayer p, boolean on) {
        if (p.getVehicle() instanceof DevMech m) {
            if (on && m.gunOverheat > 0)
                p.displayClientMessage(Component.literal(String.format("§c미니건 과열! §7냉각 중 %.1f초", m.gunOverheat / 20f)), true);
            m.gunTrigger = on;
        }
    }

    /** 총구: 총 회전축(앞 1.9칸, 높이 3.1칸)에서 조준 방향으로 총열 길이 5.05칸 (모델과 일치). */
    static Vec3 muzzle(DevMech m, Vec3 aim) {
        double r = Math.toRadians(m.getYRot());
        Vec3 pivot = new Vec3(m.getX() - Math.sin(r) * 1.9, m.getY() + 3.1 - 0.45, m.getZ() + Math.cos(r) * 1.9);
        return pivot.add(aim.scale(5.05));
    }

    /** DevMech.tick() 서버. */
    static void tick(DevMech m) {
        ServerLevel level = (ServerLevel) m.level();
        RandomSource rnd = level.random;
        if (!(m.getControllingPassenger() instanceof ServerPlayer rider)) m.gunTrigger = false;

        if (m.gunOverheat > 0) {   // 과열 냉각: 수증기 + 치익
            m.gunOverheat--;
            m.gunTrigger = m.gunTrigger && m.gunOverheat == 0;
            Vec3 mz = muzzle(m, Laser.aim(m));
            level.sendParticles(ParticleTypes.CLOUD, mz.x, mz.y + 0.3, mz.z, 2, 0.4, 0.2, 0.4, 0.02);
            if (m.gunOverheat % 10 == 0) level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.8f);
            if (m.gunOverheat == 0) m.gunHeat = MAX_HEAT / 2;
        }

        boolean firing = m.gunTrigger && m.gunOverheat == 0;
        int prevSpin = m.gunSpin;
        m.gunSpin = firing ? Math.min(MAX_SPIN, m.gunSpin + 2) : Math.max(0, m.gunSpin - 1);
        if (firing && prevSpin == 0) {
            Vec3 c = m.position();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.6f, 1.8f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 2.0f);
        }
        if (m.gunSpin > 0 && m.gunSpin < MAX_SPIN && m.tickCount % 3 == 0) {   // 총열 회전음
            Vec3 c = m.position();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.MINECART_INSIDE, SoundSource.PLAYERS, 0.5f, 0.6f + m.gunSpin * 0.05f);
        }

        if (firing && m.gunSpin >= 16) {
            Vec3 aim = Laser.aim(m).normalize();
            Vec3 mz = muzzle(m, aim);
            for (int b = 0; b < 2; b++) shoot(m, level, rnd, mz, aim, b == 0 || m.tickCount % 2 == 0);
            // 총구 화염 + 탄피
            level.sendParticles(FLASH, mz.x, mz.y, mz.z, 2, 0.15, 0.15, 0.15, 0);
            level.sendParticles(ParticleTypes.FLAME, mz.x, mz.y, mz.z, 3, 0.12, 0.12, 0.12, 0.04);
            level.sendParticles(ParticleTypes.SMOKE, mz.x, mz.y + 0.2, mz.z, 2, 0.15, 0.1, 0.15, 0.02);
            double r = Math.toRadians(m.getYRot());
            double ex = m.getX() - Math.sin(r) * 3.0 - Math.cos(r) * 1.1, ez = m.getZ() + Math.cos(r) * 3.0 - Math.sin(r) * 1.1;
            level.sendParticles(CASING, ex, m.getY() + 2.6, ez, 2, 0.1, 0.05, 0.1, 0);
            // 소리: 짧고 빠른 연발
            level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.6f, 1.6f + rnd.nextFloat() * 0.3f);
            if (m.tickCount % 2 == 0)
                level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1.2f, 1.9f);
            if (++m.gunHeat >= MAX_HEAT) {
                m.gunOverheat = OVERHEAT_TICKS;
                m.gunTrigger = false;
                level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 2f, 0.6f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, mz.x, mz.y, mz.z, 20, 0.5, 0.4, 0.5, 0.05);
                if (m.getControllingPassenger() instanceof ServerPlayer p)
                    p.displayClientMessage(Component.literal("§c§l미니건 과열! §73초 냉각"), true);
            }
        } else if (m.gunOverheat == 0 && m.gunHeat > 0) {
            m.gunHeat = Math.max(0, m.gunHeat - 2);
        }
    }

    static void shoot(DevMech m, ServerLevel level, RandomSource rnd, Vec3 from, Vec3 aim, boolean tracer) {
        Vec3 dir = aim.add(rnd.nextGaussian() * SPREAD, rnd.nextGaussian() * SPREAD, rnd.nextGaussian() * SPREAD).normalize();
        Vec3 end = from.add(dir.scale(RANGE));
        BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();

        // 맞은 생물 중 가장 가까운 것
        LivingEntity target = null;
        double best = from.distanceTo(stop);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(1.0),
                e -> e.isAlive() && e != m && e.getVehicle() != m)) {
            var hit = le.getBoundingBox().inflate(0.3).clip(from, stop);
            if (hit.isPresent()) {
                double d = from.distanceTo(hit.get());
                if (d < best) { best = d; target = le; }
            }
        }
        Vec3 impact = from.add(dir.scale(best));

        if (tracer)   // 예광탄
            for (double d = 1.0; d < best; d += 1.6)
                level.sendParticles(TRACER, from.x + dir.x * d, from.y + dir.y * d, from.z + dir.z * d, 1, 0, 0, 0, 0);

        if (target != null) {
            var src = m.getControllingPassenger() instanceof ServerPlayer p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(m);
            target.invulnerableTime = 0;
            target.hurtServer(level, src, DAMAGE);
            target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(0.12)));
            level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 4, 0.15, 0.15, 0.15, 0.25);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, impact.x, impact.y, impact.z, 1, 0.1, 0.1, 0.1, 0.1);
        } else if (bh.getType() != HitResult.Type.MISS) {
            BlockPos bp = bh.getBlockPos();
            BlockState st = level.getBlockState(bp);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), impact.x, impact.y, impact.z, 5, 0.15, 0.15, 0.15, 0.15);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, 2, 0.05, 0.05, 0.05, 0.2);
            if (rnd.nextInt(3) == 0) level.sendParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 1, 0.05, 0.05, 0.05, 0.02);
            if (rnd.nextInt(6) == 0) level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.4f, 1.8f);
            // 약한 블록(유리·잎·풀·흙·모래 등)은 부서짐
            float hard = st.getDestroySpeed(level, bp);
            if (hard >= 0 && hard <= 0.6f && rnd.nextInt(4) == 0 && MechManager.griefOn(level.getServer()))
                level.destroyBlock(bp, false);
        }
    }
}
