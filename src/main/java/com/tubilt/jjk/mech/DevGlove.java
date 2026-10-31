package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;

/**
 * 개발자의 장갑 — 들고 있으면 화면에 기계팔(GloveHud). 레이저 능력:
 *  우클릭         : 톱니 충전 0.7초 → 관통 레이저 (CD 8초)
 *  웅크리고 우클릭 : 회전 톱니 난사 3초 — 3틱마다 레이저 (CD 15초)
 */
public final class DevGlove {
    private DevGlove() {}

    static final int CYAN = 0x40E8FF, ORANGE = 0xFFB020, WHITE = 0xFFFFFF;

    interface Fx { boolean tick(ServerLevel l, ServerPlayer p, String tag); }
    static final Map<UUID, List<Fx>> FX = new HashMap<>();
    static final Map<UUID, int[]> CD = new HashMap<>();   // {단발, 난사}

    public static boolean is(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().equals("dev_glove");
    }

    public static boolean onUse(net.minecraft.world.entity.player.Player ent, ItemStack st) {
        if (!is(st)) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        List<Fx> list = FX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (!list.isEmpty()) { Koming.bar(p, "§7기계팔 작동 중"); return true; }
        int[] cd = CD.computeIfAbsent(p.getUUID(), k -> new int[2]);
        int mode = p.isShiftKeyDown() ? 1 : 0;
        if (cd[mode] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 냉각 %.1f초", mode == 0 ? "레이저" : "톱니 난사", cd[mode] / 20f)); return true; }
        cd[mode] = mode == 0 ? 160 : 300;
        list.add(mode == 0 ? new Single() : new Barrage());
        return true;
    }

    /** 총구 위치 (오른손 앞쪽) */
    static Vec3 muzzle(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.35);
        return p.getEyePosition().add(look.scale(1.1)).add(side).add(0, -0.3, 0);
    }
    /** 총구 앞에 회전하는 톱니 (입자) */
    static void gear(ServerLevel l, Vec3 c, Vec3 look, double r, int teeth, double ang, int rgb) {
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize(), v = look.cross(u).normalize();
        DustParticleOptions d = new DustParticleOptions(rgb, 1.0f);
        int n = teeth * 4;
        for (int i = 0; i < n; i++) {
            double a = ang + i * Math.PI * 2 / n;
            boolean tooth = (i % 4) < 2;
            double rr = tooth ? r * 1.25 : r;
            Vec3 q = c.add(u.scale(Math.cos(a) * rr)).add(v.scale(Math.sin(a) * rr));
            l.sendParticles(d, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 4; i++) {   // 바큇살
            double a = ang * -1 + i * Math.PI / 2;
            for (double k = 0.2; k < 1; k += 0.4) {
                Vec3 q = c.add(u.scale(Math.cos(a) * r * k)).add(v.scale(Math.sin(a) * r * k));
                l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 관통 레이저 한 줄기 */
    static void beam(ServerLevel l, ServerPlayer p, String tag, float dmg, double w, boolean big) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        double range = 48;
        BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation();
        Vec3 start = muzzle(p);
        Holo.beam(l, tag, start, end, w, CYAN, 1, big ? 6 : 2, big ? 8 : 3);
        Holo.flare(l, tag, end, big ? 7 : 3, WHITE, big ? 8 : 4);
        if (big) Holo.shock(l, tag, end, 3.5, CYAN, 8);
        Set<LivingEntity> done = new HashSet<>();
        Vec3 d = end.subtract(start);
        int steps = (int) Math.ceil(d.length() / 1.4);
        for (int i = 0; i <= steps; i++) {
            Vec3 q = start.add(d.scale(i / (double) Math.max(1, steps)));
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(big ? 1.2 : 0.8), x -> Koming.foe(x, p)))
                if (done.add(e)) { Projection.hurt(l, p, e, dmg); e.setRemainingFireTicks(60); if (big) Koming.push(e, look.scale(0.8).add(0, 0.3, 0)); }
        }
    }

    static final class Single implements Fx {
        int t = 0; static final int CHARGE = 14;
        public boolean tick(ServerLevel l, ServerPlayer p, String tag) {
            if (!p.isAlive()) return true;
            Vec3 m = muzzle(p), look = p.getLookAngle();
            if (t < CHARGE) {
                double k = t / (double) CHARGE;
                gear(l, m.add(look.scale(0.6)), look, 1.3 - k * 0.8, 10, t * 0.5, CYAN);
                gear(l, m.add(look.scale(1.4)), look, 0.8 - k * 0.4, 8, -t * 0.7, ORANGE);
                if (t == 0) Holo.sfx(l, m, "charge", 1.3f, 1.4f);
                if (t == CHARGE - 1) Holo.flare(l, tag, m.add(look.scale(0.6)), 4, WHITE, 6);
                t++;
                return false;
            }
            beam(l, p, tag, 24f, 0.7, true);
            Holo.sfx(l, m, "laser", 1.5f, 1.2f);
            Koming.push(p, look.scale(-0.4));
            return true;
        }
    }

    static final class Barrage implements Fx {
        int t = 0; static final int LEN = 60;
        public boolean tick(ServerLevel l, ServerPlayer p, String tag) {
            if (!p.isAlive()) return true;
            Vec3 m = muzzle(p), look = p.getLookAngle();
            double ang = t * 0.9;
            gear(l, m.add(look.scale(0.7)), look, 1.1, 12, ang, CYAN);
            gear(l, m.add(look.scale(1.5)), look, 0.7, 8, -ang * 1.6, ORANGE);
            if (t == 0) Holo.sfx(l, m, "riser", 1.2f, 1.5f);
            if (t % 3 == 0) {
                beam(l, p, tag, 5f, 0.28, false);
                Holo.sfx(l, m, "laser", 0.7f, 1.4f + (t % 6) * 0.05f);
            }
            if (t % 10 == 0) l.sendParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 6, 0.3, 0.3, 0.3, 0.2);
            return ++t >= LEN;
        }
    }

    public static void tick(MinecraftServer server) {
        if (CD.isEmpty() && FX.isEmpty()) return;
        for (var en : CD.entrySet()) for (int i = 0; i < en.getValue().length; i++) if (en.getValue()[i] > 0) en.getValue()[i]--;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            List<Fx> list = FX.get(p.getUUID());
            if (list == null || list.isEmpty()) continue;
            ServerLevel l = (ServerLevel) p.level();
            String tag = "jjk.glv" + Integer.toHexString(p.getUUID().hashCode() & 0xFFFFFF);
            list.removeIf(f -> f.tick(l, p, tag));
        }
    }
    public static void stopAll() { FX.clear(); CD.clear(); }
}
