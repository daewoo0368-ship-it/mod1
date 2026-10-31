package com.tubilt.jjk.mech;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Locale;

/**
 * 초 하이테크놀로지 레이저 (강화판)
 *  충전 1.5초: 눈으로 빨려드는 에너지 + 맥동하는 구체 + 회전 고리 + 지면 충격파
 *  발사 3초: 160칸 굵은 빔(흰 심·시안 광륜·이중 나선·번개 아크·충격 고리), 범위 피해, 착탄 연쇄 대폭발
 *  종료: 착탄점 최종 폭발
 */
public final class Laser {
    private Laser() {}

    public static final int CHARGE = 30, BEAM = 80, COOLDOWN = 300, RANGE = 256;
    static final float DAMAGE = 80f;
    static final double RADIUS = 4.5;

    static final DustParticleOptions CORE = new DustParticleOptions(0xFFFFFF, 4.0f);
    static final DustParticleOptions GLOW = new DustParticleOptions(0x40E8FF, 4.0f);
    static final DustParticleOptions HALO = new DustParticleOptions(0x1E7BFF, 3.0f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(0xB45CFF, 2.4f);

    /** 클라이언트가 X(레이저) 키를 눌렀을 때. */
    public static void start(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMech m)) return;
        if (m.laserCharge > 0 || m.laserTime > 0) return;
        if (m.laserCd > 0) {
            p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7레이저 냉각 중 §f%.1f초", m.laserCd / 20f)), true);
            return;
        }
        m.laserCharge = CHARGE;
        m.laserCd = COOLDOWN;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 e = eye(m);
        LaserFx.clear(level, m);
        LaserFx.spawn(level, m, LaserFx.ORB, e);
        level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.8f);
        level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4f, 0.9f);
        level.playSound(null, e.x, e.y, e.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 4f, 1.2f);
        p.displayClientMessage(Component.literal("§b§l초 하이테크놀로지 레이저 §f— §3에너지 충전 중..."), true);
    }

    /** 눈 위치 (모델 eye 뼈 = 앞으로 2.5칸, 높이 4.3칸). */
    static Vec3 eye(DevMech m) {
        double r = Math.toRadians(m.getYRot());
        return new Vec3(m.getX() - Math.sin(r) * 2.7, m.getY() + 4.3, m.getZ() + Math.cos(r) * 2.7);
    }

    static Vec3 aim(DevMech m) {
        if (m.getControllingPassenger() instanceof ServerPlayer p) return p.getLookAngle();
        double r = Math.toRadians(m.getYRot());
        return new Vec3(-Math.sin(r), -0.15, Math.cos(r)).normalize();
    }

    static void p(ServerLevel l, ParticleOptions o, Vec3 v, int n, double s, double sp) {
        l.sendParticles(o, v.x, v.y, v.z, n, s, s, s, sp);
    }

    /** DevMech.tick() (서버)에서 매 틱 호출. */
    static void tick(DevMech m) {
        ServerLevel level = (ServerLevel) m.level();
        RandomSource rnd = level.random;
        Vec3 e = eye(m);

        // ───────────── 충전
        if (m.laserCharge > 0) {
            m.laserCharge--;
            int c = CHARGE - m.laserCharge;               // 1..CHARGE
            double k = c / (double) CHARGE;
            // 빨려드는 에너지 (점점 많아지고 멀리서)
            int n = 4 + (int) (k * 14);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2, rr = 3 + rnd.nextDouble() * (3 + k * 5);
                Vec3 o = e.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
                Vec3 v = e.subtract(o).scale(0.16);
                level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
            }
            // 맥동하는 에너지 구체
            double rad = 0.4 + k * 1.3 + Math.sin(c * 0.9) * 0.15;
            for (int i = 0; i < 10 + (int) (k * 16); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = Math.acos(2 * rnd.nextDouble() - 1);
                Vec3 q = e.add(Math.sin(b) * Math.cos(a) * rad, Math.cos(b) * rad, Math.sin(b) * Math.sin(a) * rad);
                p(level, i % 2 == 0 ? GLOW : CORE, q, 1, 0, 0);
            }
            // 실체 에너지 구체: 겹친 3중 큐브가 서로 다른 축으로 회전하며 커짐
            double fl = 1 + Math.sin(c * 1.3) * 0.12;
            LaserFx.set(level, m, "o1", e, LaserFx.cube((0.3 + 1.1 * k) * fl, c * 0.31, c * 0.23));
            LaserFx.set(level, m, "o2", e, LaserFx.cube((0.7 + 2.0 * k) * fl, -c * 0.19, c * 0.37));
            LaserFx.set(level, m, "o3", e, LaserFx.cube((1.0 + 2.8 * k) / fl, c * 0.11, -c * 0.29));
            // 눈 주위 회전 고리 2개
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2 + c * 0.35, rr = 1.6 + k * 1.2;
                p(level, HALO, e.add(Math.cos(a) * rr, Math.sin(a) * rr * 0.35, Math.sin(a) * rr), 1, 0, 0);
                p(level, VIOLET, e.add(Math.cos(-a) * rr * 0.4, Math.sin(-a) * rr, Math.sin(-a) * rr * 0.4 + 0), 1, 0, 0);
            }
            if (c % 6 == 0) {
                level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3f, 0.8f + (float) k);
                // 지면 충격파 링
                double gy = m.getY() + 0.2, R = 4 + k * 8;
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.CLOUD, m.getX() + Math.cos(a) * R, gy, m.getZ() + Math.sin(a) * R, 1, 0, 0.05, 0, 0.02);
                }
            }
            if (m.laserCharge == 0) fireStart(m, level, e);
            return;
        }
        if (m.laserTime <= 0) return;

        // ───────────── 발사
        m.laserTime--;
        int t = BEAM - m.laserTime;
        Vec3 dir = aim(m).normalize();
        Vec3 end = e.add(dir.scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(e, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 stop = blocked ? hit.getLocation() : end;
        double len = stop.distanceTo(e);

        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        double pulse = 1 + Math.sin(t * 0.8) * 0.18;     // 빔 굵기 맥동

        // 0) 실체 빔: 흰 심 / 하늘 / 시안(발광 테두리) / 파랑 4겹 각기둥, 바깥 겹은 서로 반대로 회전
        double flick = 1 + (rnd.nextDouble() - 0.5) * 0.16;
        LaserFx.set(level, m, "core", e, LaserFx.beam(dir, len, 1.2 * pulse * flick, t * 0.2));
        LaserFx.set(level, m, "in", e, LaserFx.beam(dir, len, 2.6 * pulse, -t * 0.12));
        LaserFx.set(level, m, "mid", e, LaserFx.beam(dir, len, 4.3 * pulse, t * 0.15));
        LaserFx.set(level, m, "out", e, LaserFx.beam(dir, len, 6.2 * (2 - pulse) * flick, -t * 0.08));
        // 발사구 구체 (크게 요동)
        LaserFx.set(level, m, "o1", e, LaserFx.cube(2.2 * flick, t * 0.4, t * 0.3));
        LaserFx.set(level, m, "o2", e, LaserFx.cube(3.6 * pulse, -t * 0.25, t * 0.45));
        LaserFx.set(level, m, "o3", e, LaserFx.cube(5.0 / pulse, t * 0.15, -t * 0.35));
        // 착탄 에너지 돔
        double hs = blocked ? 1 : 0.001;
        LaserFx.set(level, m, "h1", stop, LaserFx.cube((4.5 + rnd.nextDouble() * 1.5) * hs, t * 0.5, t * 0.33));
        LaserFx.set(level, m, "h2", stop, LaserFx.cube((7.0 + Math.sin(t * 0.7) * 1.5) * hs, -t * 0.3, t * 0.2));

        // 1) 흰 심 + 시안 광륜 + 파란 외곽 (굵은 원통)
        for (double d = 0.5; d < len; d += 1.0) {
            Vec3 q = e.add(dir.scale(d));
            p(level, CORE, q, 2, 0.12 * pulse, 0);
            p(level, GLOW, q, 2, 0.45 * pulse, 0);
            if (((int) d + t) % 2 == 0) p(level, HALO, q, 2, 0.9 * pulse, 0);
        }
        // 2) 이중 나선 (시안 + 보라)
        for (double d = 0.5; d < len; d += 1.2) {
            double ang = d * 0.7 + t * 0.55, r = 1.5 * pulse;
            Vec3 base = e.add(dir.scale(d));
            p(level, GLOW, base.add(side.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r)), 1, 0, 0);
            p(level, VIOLET, base.add(side.scale(Math.cos(ang + Math.PI) * r)).add(up.scale(Math.sin(ang + Math.PI) * r)), 1, 0, 0);
        }
        // 3) 충격 고리: 빔을 따라 흘러가는 링
        for (double d = (t * 3) % 12; d < len; d += 12) {
            Vec3 base = e.add(dir.scale(d));
            for (int i = 0; i < 14; i++) {
                double a = i / 14.0 * Math.PI * 2, r = 2.2 * pulse;
                p(level, ParticleTypes.END_ROD, base.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)), 1, 0, 0.0);
            }
        }
        // 4) 소닉붐 고리 (큰 원형 파동)
        if (t % 3 == 0)
            for (double d = 3 + (t % 9); d < len; d += 9) p(level, ParticleTypes.SONIC_BOOM, e.add(dir.scale(d)), 1, 0, 0);
        // 5) 번개 아크: 빔에서 사방으로 튀는 지그재그 전류
        for (int arc = 0; arc < 5; arc++) {
            Vec3 from = e.add(dir.scale(rnd.nextDouble() * len));
            Vec3 cur = from;
            Vec3 out = side.scale(rnd.nextDouble() * 2 - 1).add(up.scale(rnd.nextDouble() * 2 - 1)).normalize();
            for (int s = 0; s < 7; s++) {
                cur = cur.add(out.scale(0.6)).add((rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.7);
                p(level, ParticleTypes.ELECTRIC_SPARK, cur, 1, 0.02, 0.0);
                if (s % 2 == 0) p(level, CORE, cur, 1, 0, 0);
            }
        }
        // 6) 발사구: 눈 앞 섬광 원반
        for (int i = 0; i < 18; i++) {
            double a = i / 18.0 * Math.PI * 2 + t * 0.4, r = 1.8 + Math.sin(t) * 0.3;
            p(level, i % 2 == 0 ? CORE : GLOW, e.add(dir.scale(0.6)).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)), 1, 0, 0);
        }
        p(level, ParticleTypes.END_ROD, e, 6, 0.3, 0.25);

        // 소리
        if (t % 10 == 1) level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 1.4f);
        if (t % 6 == 0) level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3f, 2.0f);
        if (t % 4 == 0) level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3f, 0.6f + rnd.nextFloat() * 0.3f);

        // 피해: 빔 반경 안의 모든 생물
        Entity owner = m.getControllingPassenger() != null ? m.getControllingPassenger() : m;
        if (t % 2 == 0) {
            AABB box = new AABB(e, stop).inflate(RADIUS + 1);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> x != m && x.getVehicle() != m && x.isAlive())) {
                if (le.getBoundingBox().inflate(RADIUS).clip(e, stop).isEmpty()) continue;
                var src = m.getControllingPassenger() instanceof ServerPlayer pl
                        ? level.damageSources().playerAttack(pl) : level.damageSources().mobAttack(m);
                le.invulnerableTime = 0;
                le.hurtServer(level, src, DAMAGE);
                le.igniteForSeconds(10);
                le.setDeltaMovement(le.getDeltaMovement().add(dir.scale(1.4)).add(0, 0.35, 0));
                le.hurtMarked = true;
                p(level, ParticleTypes.ELECTRIC_SPARK, le.position().add(0, le.getBbHeight() * 0.5, 0), 24, 0.5, 0.5);
                p(level, CORE, le.position().add(0, le.getBbHeight() * 0.5, 0), 6, 0.4, 0);
            }
        }

        // 착탄: 연쇄 대폭발 + 불기둥 + 충격파
        if (blocked) {
            p(level, ParticleTypes.EXPLOSION_EMITTER, stop, 1, 0.5, 0);
            p(level, ParticleTypes.ELECTRIC_SPARK, stop, 30, 1.2, 0.6);
            p(level, GLOW, stop, 12, 1.4, 0);
            p(level, ParticleTypes.SOUL_FIRE_FLAME, stop, 14, 1.0, 0.15);
            p(level, ParticleTypes.LAVA, stop, 4, 0.8, 0);
            for (int i = 0; i < 6; i++)   // 솟구치는 에너지 기둥
                p(level, ParticleTypes.END_ROD, stop.add(0, i * 1.5, 0), 3, 0.5, 0.05);
            boolean grief = MechManager.griefOn(level.getServer());
            if (t % 2 == 0)
                level.explode(owner, stop.x, stop.y, stop.z, 8.0f, grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            if (grief) carve(level, stop, dir, 3.4, rnd);   // 빔이 지형을 녹이며 파고듦
            if (t % 7 == 0) bolt(level, stop.add((rnd.nextDouble() - 0.5) * 6, 0, (rnd.nextDouble() - 0.5) * 6));
            if (t % 5 == 0) ring(level, stop, 3 + (t % 15) * 0.6);
            if (t % 8 == 0) MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                    "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..40] add gojo.fxshake_s", stop.x, stop.y, stop.z));
        }

        if (m.laserTime == 0) {   // 마지막 일격
            LaserFx.clear(level, m);
            level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4f, 1.2f);
            if (blocked) {
                boolean grief = MechManager.griefOn(level.getServer());
                level.explode(owner, stop.x, stop.y, stop.z, 16.0f, grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                if (grief) carve(level, stop, dir, 7.0, rnd);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + rnd.nextDouble() * 0.4;
                    level.explode(owner, stop.x + Math.cos(a) * 9, stop.y, stop.z + Math.sin(a) * 9, 8.0f, grief,
                            grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                    bolt(level, stop.add(Math.cos(a) * 5, 0, Math.sin(a) * 5));
                }
                bolt(level, stop);
                // 버섯구름: 솟구치는 기둥 + 머리
                for (int i = 0; i < 14; i++) p(level, ParticleTypes.EXPLOSION_EMITTER, stop.add(0, i * 1.6, 0), 1, 0.4, 0);
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    p(level, ParticleTypes.EXPLOSION, stop.add(Math.cos(a) * 7, 22, Math.sin(a) * 7), 2, 1.0, 0);
                    p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, stop.add(Math.cos(a) * 6, 20, Math.sin(a) * 6), 1, 1.0, 0.02);
                }
                level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6f, 0.6f);
                for (int i = 0; i < 4; i++) ring(level, stop, 5 + i * 5);
                p(level, ParticleTypes.EXPLOSION_EMITTER, stop, 6, 3, 0);
                MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                        "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] add gojo.fxshake", stop.x, stop.y, stop.z));
            }
        }
    }

    /** 발사 순간: 섬광 + 천둥 + 큰 흔들림 + 탈것 주위 폭풍 고리. */
    static void fireStart(DevMech m, ServerLevel level, Vec3 e) {
        m.laserTime = BEAM;
        m.triggerAnim("main", "fire");
        LaserFx.spawn(level, m, LaserFx.BEAM, e);
        LaserFx.spawn(level, m, LaserFx.HIT, e);
        bolt(level, new Vec3(m.getX(), m.getY(), m.getZ()));
        level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 0.9f);
        level.playSound(null, e.x, e.y, e.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5f, 1.3f);
        level.playSound(null, e.x, e.y, e.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4f, 1.6f);
        p(level, ParticleTypes.EXPLOSION_EMITTER, e, 1, 0, 0);
        p(level, CORE, e, 40, 1.2, 0);
        ring(level, new Vec3(m.getX(), m.getY() + 0.3, m.getZ()), 6);
        ring(level, new Vec3(m.getX(), m.getY() + 0.3, m.getZ()), 10);
        MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..36] add gojo.fxshake", e.x, e.y, e.z));
        if (m.getControllingPassenger() instanceof ServerPlayer pl) pl.addTag("gojo.fxflash_w");
    }

    /** 지형 용해: 반경 r 구를 증발시키고, 테두리는 마그마·흑암·불로 그을림. */
    static void carve(ServerLevel level, Vec3 c, Vec3 dir, double r, RandomSource rnd) {
        Vec3 ctr = c.add(dir.scale(r * 0.4));        // 빔 진행 방향으로 살짝 앞을 파냄
        int R = (int) Math.ceil(r + 1.5);
        net.minecraft.core.BlockPos o = net.minecraft.core.BlockPos.containing(ctr);
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d > r + 1.5) continue;
            net.minecraft.core.BlockPos bp = o.offset(x, y, z);
            var st = level.getBlockState(bp);
            if (st.isAir() || st.getDestroySpeed(level, bp) < 0) continue;      // 기반암 등 파괴 불가
            if (d <= r) {
                level.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            } else if (rnd.nextFloat() < 0.35f) {                               // 그을린 테두리
                var scorch = switch (rnd.nextInt(4)) {
                    case 0 -> net.minecraft.world.level.block.Blocks.MAGMA_BLOCK;
                    case 1 -> net.minecraft.world.level.block.Blocks.OBSIDIAN;
                    default -> net.minecraft.world.level.block.Blocks.BLACKSTONE;
                };
                level.setBlock(bp, scorch.defaultBlockState(), 3);
                net.minecraft.core.BlockPos up = bp.above();
                if (rnd.nextFloat() < 0.4f && level.getBlockState(up).isAir())
                    level.setBlock(up, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
            }
        }
        level.sendParticles(ParticleTypes.LAVA, ctr.x, ctr.y, ctr.z, 6, r * 0.5, r * 0.5, r * 0.5, 0);
    }

    /** 연출용 번개 (피해·불 없음). */
    static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (b == null) return;
        b.snapTo(at.x, at.y, at.z, 0, 0);
        b.setVisualOnly(true);
        level.addFreshEntity(b);
    }

    /** 탈것이 사라지거나 서버 종료 시 정리. */
    static void stop(DevMech m) {
        if (m.level() instanceof ServerLevel level) LaserFx.clear(level, m);
        m.laserCharge = 0;
        m.laserTime = 0;
    }

    /** 수평 충격파 고리. */
    static void ring(ServerLevel level, Vec3 c, double r) {
        int n = (int) (r * 8);
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            level.sendParticles(ParticleTypes.CLOUD, x, c.y, z, 1, 0, 0, 0, 0.05);
            if (i % 2 == 0) level.sendParticles(GLOW, x, c.y + 0.3, z, 1, 0, 0, 0, 0);
        }
    }
}
