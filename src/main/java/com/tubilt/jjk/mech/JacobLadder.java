package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;

/**
 * 야곱의 사다리 (쿠루스 하나 / 천사) — 하늘에서 빛의 사다리가 내려와 술식을 지운다.
 *  ladder 사다리 강하 : 대상 위로 빛의 사다리가 내려와 내리꽂힘 (피해 + 강화 효과 소거)
 *  climb  승천        : 빛의 사다리를 타고 하늘로 솟구침 (낙하 무효)
 *  judge  심판의 사다리: 주변에 사다리 여러 줄을 연달아 내림 (궁극기)
 */
public final class JacobLadder {
    private JacobLadder() {}

    public static final String[] IDS = {"ladder", "climb", "judge"};
    static final String[] NAMES = {"야곱의 사다리", "승천의 사다리", "심판의 사다리"};
    static final int LADDER = 0, CLIMB = 1, JUDGE = 2;
    static final int[] COST = {140, 100, 380};
    static final int[] CD = {160, 120, 900};
    static final int GOLD = 0xFFE070, WHITE = 0xFFFFFF;

    static final class State {
        final UUID id; final String tag;
        final int[] cd = new int[IDS.length];
        final List<Fx> fx = new ArrayList<>();
        State(UUID id) { this.id = id; this.tag = "jjk.jcb" + Integer.toHexString(id.hashCode() & 0xFFFFFF); }
    }
    interface Fx { boolean tick(ServerLevel l, ServerPlayer p, State s); }
    static final Map<UUID, State> STATES = new HashMap<>();
    static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), State::new); }

    static boolean isItem(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().startsWith("jacob_");
    }
    public static boolean onUse(Player ent, ItemStack st) {
        if (!isItem(st)) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        cast(p, BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().substring("jacob_".length()));
        return true;
    }

    public static void cast(ServerPlayer p, String id) {
        int idx = Arrays.asList(IDS).indexOf(id);
        if (idx < 0) return;
        if (p.getTags().contains("jjk.hunsu")) { Koming.bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        State s = st(p);
        ServerLevel l = (ServerLevel) p.level();
        if (s.fx.stream().anyMatch(f -> f instanceof Judge || f instanceof Climb)) { Koming.bar(p, "§7시전 중"); return; }
        if (s.cd[idx] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", NAMES[idx], s.cd[idx] / 20f)); return; }
        Vec3 at = null; LivingEntity lockT = null;
        if (idx == LADDER) {
            LivingEntity t = Projection.pick(l, p, 40);
            lockT = t;
            if (t != null) at = t.position();
            else {
                Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
                BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(40)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                at = hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
            }
            if (at == null) { Koming.bar(p, "§7사다리를 내릴 곳이 없다"); return; }
        }
        if (!Koming.spend(p, COST[idx])) { Koming.bar(p, "§c주력이 부족하다 §7(필요 " + COST[idx] + ")"); return; }
        s.cd[idx] = CD[idx];
        switch (idx) {
            case LADDER -> { s.fx.add(new Strike(at, 20, 30f, lockT)); Koming.bar(p, "§e야곱의 사다리"); }
            case CLIMB -> { s.fx.add(new Climb()); Koming.bar(p, "§e승천 §7— 하늘로 이어진 사다리"); }
            case JUDGE -> { s.fx.add(new Judge()); Koming.bar(p, "§e심판의 사다리"); }
            default -> { }
        }
    }

    static void rung(ServerLevel l, Vec3 base, double y, double w) {
        DustParticleOptions d = new DustParticleOptions(GOLD, 1.3f);
        for (int i = -3; i <= 3; i++) l.sendParticles(d, base.x + i * w / 3, y, base.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.END_ROD, base.x - w, y, base.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.END_ROD, base.x + w, y, base.z, 1, 0, 0, 0, 0);
    }

    // ═════════════════════════ 사다리 내리꽂기
    static final class Strike implements Fx {
        Vec3 at; final int delay; final float dmg; int t = 0; final LivingEntity follow;
        Strike(Vec3 at, int delay, float dmg) { this(at, delay, dmg, null); }
        Strike(Vec3 at, int delay, float dmg, LivingEntity follow) { this.at = at; this.delay = delay; this.dmg = dmg; this.follow = follow; }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            // 대상을 따라가다 내리꽂히기 직전(6틱)에 고정 — 움직이는 대상도 맞는다
            if (follow != null && follow.isAlive() && t < delay - 6) at = follow.position();
            if (t == 0) {
                Holo.pillar(l, s.tag, at, 34, 1.3, GOLD, delay, 8, 10);
                Holo.ring(l, s.tag, at.add(0, 0.1, 0), 3.5, 0.4, 24, 0.15, 0.04, GOLD, delay, 0, 2, 0, 6, true);
                Holo.sfx(l, at, "choir", 1.1f, 1.2f);
            }
            // 위에서 아래로 내려오는 가로대
            double h = 26 * (1 - t / (double) delay);
            for (int k = 0; k < 3; k++) rung(l, at, at.y + h + k * 2.0, 0.7);
            if (t % 3 == 0) l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.2, at.z, 3, 1.5, 0.05, 1.5, 0.02);
            if (++t < delay) return false;
            for (LivingEntity e : Projection.targets(l, p, at, 7)) {
                double dx = e.getX() - at.x, dz = e.getZ() - at.z;
                if (dx * dx + dz * dz > 3.8 * 3.8 || e.getY() < at.y - 2 || e.getY() > at.y + 6) continue;
                Projection.hurt(l, p, e, dmg);
                // 강화 효과 소거 (술식·버프를 지운다)
                List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> rm = new ArrayList<>();
                for (MobEffectInstance m : e.getActiveEffects()) if (m.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) rm.add(m.getEffect());
                for (var h2 : rm) e.removeEffect(h2);
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                Koming.push(e, new Vec3(0, 0.6, 0));
            }
            Holo.flare(l, s.tag, at.add(0, 1, 0), 10, WHITE, 10);
            Holo.shock(l, s.tag, at, 4, GOLD, 10);
            l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 40, 1.2, 1.5, 1.2, 0.2);
            Holo.sfx(l, at, "thunder", 1.3f, 1.4f);
            return true;
        }
    }

    // ═════════════════════════ 승천
    static final class Climb implements Fx {
        int t = 0;
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (!p.isAlive()) return true;
            if (t == 0) {
                Holo.sfx(l, p.position(), "whoosh", 1.2f, 1.0f);
                Holo.pillar(l, s.tag, p.position(), 30, 1.0, GOLD, 4, 14, 10);
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 2, false, false));
            }
            Vec3 look = p.getLookAngle();
            Koming.push(p, new Vec3(look.x * 0.25, 0.85, look.z * 0.25));
            p.fallDistance = 0;
            rung(l, p.position(), p.getY() - 0.3, 0.6);
            if (++t >= 14) { Holo.flare(l, s.tag, p.position().add(0, 1, 0), 5, WHITE, 8); return true; }
            return false;
        }
    }

    // ═════════════════════════ 심판의 사다리
    static final class Judge implements Fx {
        int t = 0; final List<Strike> strikes = new ArrayList<>();
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (p.isAlive()) {
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 6, 2, false, false));
                if (t < 160 && t % 2 == 0) {
                    List<LivingEntity> fs = Projection.targets(l, p, p.position(), 18);
                    Vec3 at; LivingEntity ft = null;
                    if (!fs.isEmpty()) { ft = fs.get(p.getRandom().nextInt(fs.size())); at = ft.position(); }
                    else {
                        double a = p.getRandom().nextDouble() * 6.28, r = 3 + p.getRandom().nextDouble() * 9;
                        at = new Vec3(p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r);
                    }
                    strikes.add(new Strike(at, 12, 9f, ft));
                }
            }
            t++;
            strikes.removeIf(k -> k.tick(l, p, s));
            return t >= 160 && strikes.isEmpty();
        }
    }

    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            if (s == null) continue;
            if (!p.isAlive()) { s.fx.clear(); continue; }
            ServerLevel l = (ServerLevel) p.level();
            for (int i = 0; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
            s.fx.removeIf(f -> f.tick(l, p, s));
        }
    }
    public static void stopAll() { STATES.clear(); }
}
