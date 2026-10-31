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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * 대지 분쇄 (G): 거대한 몸으로 높이 도약 → 무겁게 내리꽂혀 지면을 부수는 충격파.
 * 도약 자체는 조종하는 클라이언트가 탈것에 힘을 줌(탈것 이동은 탑승자 클라가 계산),
 * 서버는 "공중 → 착지" 를 감지해 충격파를 일으킨다.
 */
public final class Slam {
    private Slam() {}

    public static final int COOLDOWN = 160;
    static final double RADIUS = 24;
    static final DustParticleOptions ORANGE = new DustParticleOptions(0xFF8A20, 3.0f);
    static final DustParticleOptions CYAN = new DustParticleOptions(0x40E8FF, 2.5f);

    public static boolean ready(DevMech m) { return m.slamCd <= 0 && m.slamState == 0; }

    /** 서버: 탑승자가 G 를 눌렀다고 알림. */
    public static void start(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMech m)) return;
        if (!ready(m)) {
            if (m.slamCd > 0)
                p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7대지 분쇄 재사용 대기 §f%.1f초", m.slamCd / 20f)), true);
            return;
        }
        m.slamState = 1;
        m.slamAir = 0;
        m.slamTimeout = 120;
        m.slamCd = COOLDOWN;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 c = m.position();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 3f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 3f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3f, 0.4f);
        dustRing(level, c, 5, ground(level, c));
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.5, c.z, 30, 3, 0.3, 3, 0.05);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 30, 2, 0.4, 2, 0.1);
        p.displayClientMessage(Component.literal("§6§l대지 분쇄!"), true);
    }

    /** DevMech.tick() 서버. */
    static void tick(DevMech m) {
        ServerLevel level = (ServerLevel) m.level();
        if (m.slamState == 1) {
            if (--m.slamTimeout <= 0) { m.slamState = 0; return; }
            if (!m.onGround()) {
                m.slamAir++;
                // 공중: 다리 끝 분사 + 떨어질 때 붉게 달아오름
                Vec3 c = m.position();
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.5, c.z, 6, 1.5, 0.2, 1.5, 0.02);
                if (m.getDeltaMovement().y < 0) level.sendParticles(ORANGE, c.x, c.y + 2, c.z, 8, 2.5, 1.5, 2.5, 0);
            } else if (m.slamAir > 4) {
                impact(m, level);
                m.slamState = 2;
                m.shockT = 0;
                m.shockCenter = m.position();
            }
            return;
        }
        if (m.slamState == 2) {   // 퍼져나가는 충격파 (12틱)
            m.shockT++;
            Vec3 c = m.shockCenter;
            double r = m.shockT * (RADIUS / 16.0);
            BlockState gs = ground(level, c);
            int n = (int) (r * 7) + 8;
            for (int i = 0; i < n; i++) {
                double a = i / (double) n * Math.PI * 2;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                level.sendParticles(ParticleTypes.CLOUD, x, c.y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.06);
                if (i % 2 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gs), x, c.y + 0.5, z, 3, 0.3, 0.6, 0.3, 0.3);
                if (i % 4 == 0) level.sendParticles(ORANGE, x, c.y + 0.6, z, 1, 0.1, 0.3, 0.1, 0);
            }
            if (m.shockT % 3 == 0) level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, r * 0.5, 0.3, r * 0.5, 0);
                        // 두 번째 여진 고리 (반 박자 늦게)
            if (m.shockT > 5) {
                double r2 = (m.shockT - 5) * (RADIUS / 14.0);
                for (int i = 0; i < 36; i++) {
                    double a = i / 36.0 * Math.PI * 2;
                    level.sendParticles(CYAN, c.x + Math.cos(a) * r2, c.y + 0.4, c.z + Math.sin(a) * r2, 1, 0, 0.1, 0, 0);
                }
            }
            if (m.shockT >= 18) m.slamState = 0;
        }
    }

    static void impact(DevMech m, ServerLevel level) {
        Vec3 c = m.position();
        double fall = Math.min(1.0, m.slamAir / 30.0);   // 오래 떨어질수록 강함
        // 소리
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 4f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4f, 0.7f);
        // 중심 폭발 (지형 파괴는 #grief 따름, 탈것·탑승자는 면역)
        boolean grief = MechManager.griefOn(level.getServer());
        level.explode(m.getControllingPassenger() != null ? m.getControllingPassenger() : m, c.x, c.y, c.z,
                (float) (8 + 4 * fall), false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        // 링 모양 2차 폭발 6개
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + m.getRandom().nextDouble() * 0.5;
            level.explode(m.getControllingPassenger() != null ? m.getControllingPassenger() : m, c.x + Math.cos(a) * 8, c.y, c.z + Math.sin(a) * 8,
                    (float) (4 + 2 * fall), false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        debris(level, c, gsAt(level, c), 48);
        // 중심 연출
        BlockState gs = ground(level, c);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 3, 2, 0.3, 2, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gs), c.x, c.y + 1, c.z, 200, 5, 1.5, 5, 0.6);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1, c.z, 40, 5, 1, 5, 0.06);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 1, c.z, 20, 4, 0.5, 4, 0);
        // 8방향 균열: 땅을 따라 갈라지는 불길
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + m.getRandom().nextDouble() * 0.3;
            for (double d = 2; d < RADIUS; d += 0.8) {
                double jx = Math.sin(d * 1.7 + k) * 0.6;
                double x = c.x + Math.cos(a) * d - Math.sin(a) * jx, z = c.z + Math.sin(a) * d + Math.cos(a) * jx;
                level.sendParticles(ParticleTypes.FLAME, x, c.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.01);
                if ((int) (d * 10) % 3 == 0) level.sendParticles(CYAN, x, c.y + 0.25, z, 1, 0, 0, 0, 0);
                if ((int) (d * 10) % 5 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, x, c.y + 0.3, z, 1, 0.1, 0.4, 0.1, 0.02);
            }
            double ex = c.x + Math.cos(a) * RADIUS * 0.8, ez = c.z + Math.sin(a) * RADIUS * 0.8;
            for (int h = 0; h < 8; h++) {   // 균열 끝에서 치솟는 불기둥
                level.sendParticles(ParticleTypes.FLAME, ex, c.y + h * 0.9, ez, 4, 0.3, 0.2, 0.3, 0.03);
                level.sendParticles(ORANGE, ex, c.y + h * 0.9, ez, 1, 0.2, 0.2, 0.2, 0);
            }
            level.sendParticles(ParticleTypes.LAVA, ex, c.y + 0.5, ez, 3, 0.3, 0.2, 0.3, 0);
        }
        // 피해: 반경 16칸, 가까울수록 강함, 공중으로 튕겨냄
        var src = m.getControllingPassenger() instanceof ServerPlayer pl ? level.damageSources().playerAttack(pl) : level.damageSources().mobAttack(m);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIUS, 6, RADIUS),
                e -> e.isAlive() && e != m && e.getVehicle() != m && !(e instanceof Player))) {
            double d = le.position().distanceTo(c);
            if (d > RADIUS) continue;
            double k = 1 - d / RADIUS;
            le.invulnerableTime = 0;
            le.hurtServer(level, src, (float) ((30 + 90 * k) * (0.7 + 0.3 * fall)));
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 80, 3));
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 80, 1));
            Vec3 out = le.position().subtract(c).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? new Vec3(0, 0, 0) : out.normalize();
            le.setDeltaMovement(out.scale(0.8 + 2.2 * k).add(0, 0.8 + 1.6 * k, 0));
            le.hurtMarked = true;
        }
        // 화면 흔들림
        MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..48] add gojo.fxshake", c.x, c.y, c.z));
        if (m.getControllingPassenger() instanceof ServerPlayer pl) pl.addTag("gojo.fxflash_o");
    }

    static BlockState gsAt(ServerLevel level, Vec3 c) { return ground(level, c); }

    /** 날아오르는 지면 파편 (실제 블록 모양, 떨어지면 사라짐 — 지형 변화 없음). */
    static void debris(ServerLevel level, Vec3 c, BlockState gs, int n) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(gs.getBlock()).toString();
        var rnd = level.random;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble() * 8;
            double sp = 0.4 + rnd.nextDouble() * 0.8;
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                    "summon falling_block %.2f %.2f %.2f {BlockState:{Name:\"%s\"},Time:1,DropItem:0b,CancelDrop:1b,HurtEntities:0b,Motion:[%.3f,%.3f,%.3f]}",
                    c.x + Math.cos(a) * r, c.y + 0.6, c.z + Math.sin(a) * r, id,
                    Math.cos(a) * sp, 0.7 + rnd.nextDouble() * 0.9, Math.sin(a) * sp));
        }
    }

    static void dustRing(ServerLevel level, Vec3 c, double r, BlockState gs) {
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gs), c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 3, 0.3, 0.2, 0.3, 0.2);
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.08);
        }
    }

    static BlockState ground(ServerLevel level, Vec3 c) {
        BlockPos bp = BlockPos.containing(c.x, c.y - 0.5, c.z);
        for (int i = 0; i < 4 && level.getBlockState(bp).isAir(); i++) bp = bp.below();
        BlockState s = level.getBlockState(bp);
        return s.isAir() || !s.getFluidState().isEmpty() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : s;
    }
}
