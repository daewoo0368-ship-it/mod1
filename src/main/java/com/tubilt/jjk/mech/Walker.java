package com.tubilt.jjk.mech;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * 탑승물2 무기.
 *  Z 주포 연사: 쌍열 주포가 번갈아 6발 (0.2초 간격), 착탄 폭발 위력 4. 재장전 3초.
 *  X 중포: 1.5초 충전(포구에 에너지 집속, 선체 웅크림) → 거대 포탄 1발, 착탄 위력 10 + 충격파 + 번개. 재사용 12초.
 */
public final class Walker {
    private Walker() {}

    public static final int CANNON_CD = 60, HEAVY_CD = 240, HEAVY_CHARGE = 30;
    static final DustParticleOptions HOT = new DustParticleOptions(0xFF6A20, 2.2f);
    static final DustParticleOptions CORE = new DustParticleOptions(0xFFF0C0, 3.0f);
    static final DustParticleOptions BLUE = new DustParticleOptions(0x40E8FF, 3.2f);

    /** 조준 방향 (탑승자 시선). */
    static Vec3 aim(DevWalker w) {
        if (w.getControllingPassenger() instanceof ServerPlayer p) return p.getLookAngle().normalize();
        double r = Math.toRadians(w.getYRot());
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** 포구: 어깨 위 포탑(앞 3.1칸, 높이 11.7칸) → 포가 1칸 → 포신 끝 5.5칸, 좌우 ±0.56칸. */
    static Vec3 muzzle(DevWalker w, Vec3 aim, int side) {
        Vec3 flat = new Vec3(aim.x, 0, aim.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        double r = Math.toRadians(w.getYRot());
        Vec3 body = new Vec3(-Math.sin(r), 0, Math.cos(r));
        Vec3 pivot = w.position().add(0, 11.7, 0).add(body.scale(3.1)).add(flat.scale(1.0));   // 어깨 혹 위 포탑
        return pivot.add(aim.scale(5.5)).add(right.scale(0.56 * side));
    }

    public static void cannon(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevWalker w)) return;
        if (w.cannonCd > 0 || w.volley > 0) {
            p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7주포 재장전 중 §f%.1f초", w.cannonCd / 20f)), true);
            return;
        }
        w.volley = 6;
        w.volleyT = 0;
    }

    public static void heavy(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevWalker w)) return;
        if (w.heavyCd > 0 || w.heavyCharge > 0) {
            if (w.heavyCd > 0) p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7중포 냉각 중 §f%.1f초", w.heavyCd / 20f)), true);
            return;
        }
        w.heavyCharge = HEAVY_CHARGE;
        w.heavyCd = HEAVY_CD;
        ServerLevel level = (ServerLevel) w.level();
        Vec3 c = w.position();
        level.playSound(null, c.x, c.y + 9, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.6f);
        level.playSound(null, c.x, c.y + 9, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.6f);
        p.displayClientMessage(Component.literal("§b§l중포 §f— §3에너지 집속 중..."), true);
    }

    public static boolean cannonReady(DevWalker w) { return w.cannonCd <= 0 && w.volley == 0; }
    public static boolean heavyReady(DevWalker w) { return w.heavyCd <= 0 && w.heavyCharge == 0; }

    static void tick(DevWalker w) {
        ServerLevel level = (ServerLevel) w.level();
        if (w.cannonCd > 0) w.cannonCd--;
        if (w.heavyCd > 0) w.heavyCd--;
        if (w.recoilT > 0) w.recoilT--;

        // 주포 연사
        if (w.volley > 0 && --w.volleyT <= 0) {
            int side = w.volley % 2 == 0 ? 1 : -1;
            Vec3 a = aim(w), mz = muzzle(w, a, side);
            MechManager.SHELLS.add(new Shell(level, w.getControllingPassenger() != null ? w.getControllingPassenger().getUUID() : w.getUUID(),
                    w.getUUID(), mz, a.scale(3.4), false));
            w.triggerAnim("main", "fire");
            level.sendParticles(ParticleTypes.EXPLOSION, mz.x, mz.y, mz.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, mz.x, mz.y, mz.z, 12, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mz.x, mz.y, mz.z, 6, 0.4, 0.4, 0.4, 0.03);
            level.sendParticles(HOT, mz.x, mz.y, mz.z, 6, 0.3, 0.3, 0.3, 0);
            level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.5f, 1.5f);
            level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.5f, 0.6f);
            if (w.getControllingPassenger() instanceof ServerPlayer p) p.addTag("gojo.fxshake_s");
            w.volleyT = 4;
            if (--w.volley == 0) w.cannonCd = CANNON_CD;
        }

        // 중포 충전 → 발사
        if (w.heavyCharge > 0) {
            int c = HEAVY_CHARGE - --w.heavyCharge;
            double k = c / (double) HEAVY_CHARGE;
            Vec3 a = aim(w);
            Vec3 m1 = muzzle(w, a, 1), m2 = muzzle(w, a, -1), mid = m1.add(m2).scale(0.5).add(a.scale(1.2));
            for (int i = 0; i < 6 + (int) (k * 12); i++) {
                double ang = level.random.nextDouble() * Math.PI * 2, b = level.random.nextDouble() * Math.PI - Math.PI / 2, rr = 2 + level.random.nextDouble() * 4;
                Vec3 o = mid.add(Math.cos(ang) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(ang) * Math.cos(b) * rr);
                Vec3 v = mid.subtract(o).scale(0.18);
                level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
            }
            level.sendParticles(BLUE, mid.x, mid.y, mid.z, 3 + (int) (k * 8), 0.2 + k * 0.6, 0.2 + k * 0.6, 0.2 + k * 0.6, 0);
            level.sendParticles(CORE, mid.x, mid.y, mid.z, 2, 0.1, 0.1, 0.1, 0);
            if (c % 6 == 0) level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3f, 0.6f + (float) k);
            if (w.heavyCharge == 0) {
                MechManager.SHELLS.add(new Shell(level, w.getControllingPassenger() != null ? w.getControllingPassenger().getUUID() : w.getUUID(),
                        w.getUUID(), mid, a.scale(2.8), true));
                w.triggerAnim("main", "heavy");
                w.recoilT = 16;
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
                level.sendParticles(BLUE, mid.x, mid.y, mid.z, 40, 1.2, 1.2, 1.2, 0);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 0.6f);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4f, 1.2f);
                Vec3 c0 = w.position();
                Slam.dustRing(level, c0, 7, Slam.ground(level, c0));
                MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                        "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..40] add gojo.fxshake", c0.x, c0.y, c0.z));
                if (w.getControllingPassenger() instanceof ServerPlayer p) p.addTag("gojo.fxflash_w");
            }
        }
    }
}
