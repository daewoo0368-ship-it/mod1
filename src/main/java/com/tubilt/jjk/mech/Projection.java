package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 투사주법 (젠인 나오야) — 1초를 24컷으로 나누어 투사하는 술식.
 *  frame  투사 가속   : 20초간 24프레임 가속. 잔상을 남기며 달리고, 공격을 일정 확률로 피함 (토글)
 *  slice  24프레임 연격: 대상을 24컷(1초) 동안 사방에서 베고 지나감
 *  freeze 프레임 고정 : 룰 — 대상 둘레에 틀을 그리고 3초간 멈춤 (틀 밖으로 못 나감)
 *  rush   24fps 폭주  : 6초간 주변 전원을 24fps 로 묶고 초고속 난타 (궁극기)
 */
public final class Projection {
    private Projection() {}

    public static final String[] IDS = {"frame", "slice", "freeze", "rush"};
    static final String[] NAMES = {"투사 가속", "24프레임 연격", "프레임 고정", "24fps 폭주"};
    static final int FRAME = 0, SLICE = 1, FREEZE = 2, RUSH = 3;
    static final int[] COST = {60, 150, 120, 300};
    static final int[] CD = {40, 160, 300, 1200};
    static final int GREEN = 0x7CFF6A, WHITE = 0xFFFFFF;

    static final class State {
        final UUID id; final String tag;
        final int[] cd = new int[IDS.length];
        int accel = 0;
        final List<Fx> fx = new ArrayList<>();
        State(UUID id) { this.id = id; this.tag = "jjk.prj" + Integer.toHexString(id.hashCode() & 0xFFFFFF); }
    }
    interface Fx { boolean tick(ServerLevel l, ServerPlayer p, State s); }

    /** 프레임에 갇힌 대상 */
    static final class Frozen {
        final LivingEntity e; final Vec3 at; int left; final boolean wasNoAi;
        Frozen(LivingEntity e, int left) { this.e = e; this.at = e.position(); this.left = left; this.wasNoAi = e instanceof Mob m && m.isNoAi(); }
    }

    static final Map<UUID, State> STATES = new HashMap<>();
    static final Map<UUID, Frozen> FROZEN = new HashMap<>();

    static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), State::new); }

    // ═════════════════════════ 아이템 · 판정
    static boolean isItem(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().startsWith("projection_");
    }
    public static boolean isProj(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isItem(inv.getItem(i))) return true;
        return false;
    }
    /** 우클릭 아이템 → true 면 처리함. */
    public static boolean onUse(Player ent, ItemStack st) {
        if (!isItem(st)) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        cast(p, BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().substring("projection_".length()));
        return true;
    }

    // ═════════════════════════ 시전
    public static void cast(ServerPlayer p, String id) {
        int idx = Arrays.asList(IDS).indexOf(id);
        if (idx < 0) return;
        if (!isProj(p)) { Koming.bar(p, "§7투사주법 아이템이 있어야 쓸 수 있다"); return; }
        if (p.getTags().contains("jjk.hunsu")) { Koming.bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        State s = st(p);
        ServerLevel l = (ServerLevel) p.level();
        if (s.fx.stream().anyMatch(f -> f instanceof Slice || f instanceof Rush)) { Koming.bar(p, "§7투사 중"); return; }
        if (s.cd[idx] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", NAMES[idx], s.cd[idx] / 20f)); return; }

        LivingEntity target = null;
        if (idx == SLICE || idx == FREEZE) {
            target = pick(l, p, idx == SLICE ? 16 : 26);
            if (target == null) { Koming.bar(p, "§7시선 앞에 대상이 없다"); return; }
        }
        boolean off = idx == FRAME && s.accel > 0;
        if (!off && !Koming.spend(p, COST[idx])) { Koming.bar(p, "§c주력이 부족하다 §7(필요 " + COST[idx] + ")"); return; }
        s.cd[idx] = CD[idx];

        switch (idx) {
            case FRAME -> {
                if (off) { s.accel = 0; p.removeEffect(MobEffects.SPEED); p.removeEffect(MobEffects.JUMP_BOOST); Koming.bar(p, "§7투사 가속 해제"); }
                else {
                    s.accel = 400;
                    Koming.bar(p, "§a투사 가속 §f— 1초를 24컷으로. 내 움직임은 이미 그려져 있다");
                    Holo.flare(l, s.tag, p.position().add(0, 1, 0), 4, GREEN, 8);
                    Holo.sfx(l, p.position(), "whoosh", 1.2f, 1.6f);
                }
            }
            case SLICE -> {
                s.fx.add(new Slice(p, target));
                Koming.bar(p, "§a24프레임 연격");
            }
            case FREEZE -> {
                freeze(l, p, s, target, 60);
                Koming.bar(p, "§a프레임 고정 §7— 틀 밖으로 나가는 순간 멈춘다");
            }
            case RUSH -> {
                s.fx.add(new Rush(p));
                Koming.bar(p, "§a24fps 폭주");
            }
            default -> { }
        }
    }

    // ═════════════════════════ 대상 · 피해
    /** 시선 앞(원뿔) 에서 가장 정면에 있는 적. */
    /** 살아있는 모든 생물(적·중립·동물·다른 플레이어). 자신·방어대·창작/관전 플레이어·내 길들인 동물은 제외. */
    static boolean ok(net.minecraft.world.entity.Entity x, ServerPlayer p) {
        if (!(x instanceof LivingEntity e) || e == p || !e.isAlive() || e instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return false;
        return !(e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isOwnedBy(p));
    }
    static List<LivingEntity> targets(ServerLevel l, ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r), x -> x != p && x.isAlive())) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isOwnedBy(p)) continue;
            if (e.position().distanceToSqr(c) <= r * r) out.add(e);
        }
        return out;
    }
    static LivingEntity pick(ServerLevel l, ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null; double bestDot = 0.82;
        for (LivingEntity e : targets(l, p, eye, range)) {
            Vec3 d = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double len = d.length();
            if (len < 0.5) continue;
            double dot = d.scale(1 / len).dot(look);
            if (dot > bestDot) { bestDot = dot; best = e; }
        }
        return best;
    }
    static void hurt(ServerLevel l, ServerPlayer p, LivingEntity e, float dmg) {
        if (e == null || e == p || !e.isAlive()) return;
        DamageSource src = l.damageSources().playerAttack(p);
        e.invulnerableTime = 0;
        e.hurtServer(l, src, dmg);
    }
    /** 대상 곁 빈자리로 순간이동 (잔상 남김). 성공 여부. */
    static boolean blink(ServerLevel l, ServerPlayer p, State s, Vec3 center, double r, double ang) {
        for (int k = 0; k < 6; k++) {
            double a = ang + k * 1.0471976;
            Vec3 to = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            var box = p.getBoundingBox().move(to.x - p.getX(), to.y - p.getY(), to.z - p.getZ());
            if (!l.noCollision(p, box)) continue;
            Vec3 from = p.position();
            Holo.sparks(l, s.tag, from.add(0, 1, 0), 3, 1.2, GREEN, 5);
            l.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 1, from.z, 6, 0.25, 0.5, 0.25, 0.02);
            p.teleportTo(to.x, to.y, to.z);
            p.setYRot((float) (Math.toDegrees(Math.atan2(-(center.x - to.x), center.z - to.z))));
            return true;
        }
        return false;
    }

    // ═════════════════════════ 프레임 고정
    static void freeze(ServerLevel l, ServerPlayer p, State s, LivingEntity t, int ticks) {
        Frozen old = FROZEN.get(t.getUUID());
        if (old != null) old.left = ticks;
        else {
            Frozen f = new Frozen(t, ticks);
            FROZEN.put(t.getUUID(), f);
            if (t instanceof Mob m) m.setNoAi(true);
        }
        Holo.flare(l, s.tag, t.position().add(0, t.getBbHeight() / 2, 0), 5, WHITE, 8);
        Holo.sfx(l, t.position(), "plate", 1.3f, 1.3f);
    }
    static void frameBox(ServerLevel l, LivingEntity e, Vec3 face) {
        // 대상 둘레를 감싸는 필름 틀 (입자)
        double w = Math.max(0.9, e.getBbWidth() * 1.1), h = e.getBbHeight() + 0.5;
        Vec3 c = e.position();
        Vec3 side = new Vec3(-face.z, 0, face.x).normalize().scale(w);
        DustParticleOptions dust = new DustParticleOptions(0xFFFFFF, 0.9f);
        int n = 8;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            for (double[] corner : new double[][]{{-1, t * h - 0.1}, {1, t * h - 0.1}}) {
                Vec3 q = c.add(side.scale(corner[0])).add(0, corner[1], 0);
                l.sendParticles(dust, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            Vec3 a = c.add(side.scale(-1 + 2 * t)).add(0, -0.1, 0), b = c.add(side.scale(-1 + 2 * t)).add(0, h - 0.1, 0);
            l.sendParticles(dust, a.x, a.y, a.z, 1, 0, 0, 0, 0);
            l.sendParticles(dust, b.x, b.y, b.z, 1, 0, 0, 0, 0);
        }
    }

    // ═════════════════════════ 24프레임 연격
    static final class Slice implements Fx {
        final LivingEntity t; int n = 0; final double a0;
        Slice(ServerPlayer p, LivingEntity t) { this.t = t; this.a0 = p.getRandom().nextDouble() * 6.28; }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (!p.isAlive() || !t.isAlive() || t.level() != l) return true;
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 4, 3, false, false));
            Vec3 c = t.position();
            if (n < 24) {
                if (blink(l, p, s, c, 1.8 + (n % 3) * 0.3, a0 + n * 2.4)) {
                    hurt(l, p, t, 1.6f);
                    Vec3 mid = t.position().add(0, t.getBbHeight() / 2, 0);
                    l.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 4, 0.3, 0.4, 0.3, 0.3);
                    if (n % 4 == 0) Holo.sfx(l, mid, "slash", 0.8f, 1.2f + n * 0.02f);
                }
                if (n % 6 == 0) frameBox(l, t, p.getLookAngle());
            }
            if (++n >= 24) {
                Vec3 mid = t.position().add(0, t.getBbHeight() / 2, 0);
                Holo.flare(l, s.tag, mid, 8, GREEN, 8);
                Holo.shock(l, s.tag, t.position(), 3, GREEN, 8);
                Holo.sfx(l, mid, "impact", 1.3f, 1.2f);
                hurt(l, p, t, 6f);
                Vec3 push = t.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(1.2).add(0, 0.4, 0);
                Koming.push(t, push);
                return true;
            }
            return false;
        }
    }

    // ═════════════════════════ 24fps 폭주
    static final class Rush implements Fx {
        int t = 0; static final int LEN = 120;
        Rush(ServerPlayer p) {}
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (!p.isAlive()) return true;
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 6, 4, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 6, 2, false, false));
            List<LivingEntity> fs = Koming.foes(l, p, p.position(), 12);
            if (t == 0) {
                Holo.flare(l, s.tag, p.position().add(0, 1, 0), 10, GREEN, 10);
                Holo.shock(l, s.tag, p.position(), 12, GREEN, 12);
                Holo.sfx(l, p.position(), "riser", 1.4f, 1.0f);
            }
            for (LivingEntity e : fs) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8, 3, false, false));   // 24fps 로 묶임
                if (t % 5 == 0) frameBox(l, e, p.getLookAngle());
            }
            if (t % 4 == 0 && !fs.isEmpty()) {
                LivingEntity e = fs.get(p.getRandom().nextInt(fs.size()));
                if (blink(l, p, s, e.position(), 1.7, p.getRandom().nextDouble() * 6.28)) {
                    hurt(l, p, e, 2.0f);
                    Vec3 mid = e.position().add(0, e.getBbHeight() / 2, 0);
                    l.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 5, 0.3, 0.4, 0.3, 0.35);
                    Holo.sfx(l, mid, "slash", 0.9f, 1.0f + (t % 8) * 0.05f);
                }
            }
            if (++t >= LEN) {
                for (LivingEntity e : fs) {
                    hurt(l, p, e, 8f);
                    Koming.push(e, e.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(1.5).add(0, 0.5, 0));
                }
                Holo.flare(l, s.tag, p.position().add(0, 1, 0), 14, WHITE, 10);
                Holo.shock(l, s.tag, p.position(), 12, WHITE, 12);
                Holo.sfx(l, p.position(), "boom", 1.4f, 1.1f);
                return true;
            }
            return false;
        }
    }

    // ═════════════════════════ 피해 회피 (투사 가속 중)
    public static boolean dodge(ServerPlayer p, DamageSource src) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.accel <= 0 || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (src.getEntity() == null && src.getDirectEntity() == null) return false;
        if (p.getRandom().nextFloat() > 0.4f) return false;
        ServerLevel l = (ServerLevel) p.level();
        Vec3 at = p.position().add(0, 1, 0);
        l.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.3, 0.6, 0.3, 0.05);
        Holo.sfx(l, at, "whoosh", 0.8f, 1.8f);
        return true;
    }

    // ═════════════════════════ 매 틱
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty() && FROZEN.isEmpty()) return;
        // 고정된 대상
        Iterator<Frozen> it = FROZEN.values().iterator();
        while (it.hasNext()) {
            Frozen f = it.next();
            LivingEntity e = f.e;
            if (!e.isAlive() || e.isRemoved() || --f.left <= 0) {
                if (e instanceof Mob m && !f.wasNoAi) m.setNoAi(false);
                it.remove();
                continue;
            }
            if (e.level() instanceof ServerLevel l) {
                e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                e.hurtMarked = true;
                if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 3, 9, false, false));
                if (f.left % 3 == 0) frameBox(l, e, new Vec3(0, 0, 1));
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            if (s == null) continue;
            if (!p.isAlive()) { s.fx.clear(); s.accel = 0; continue; }
            ServerLevel l = (ServerLevel) p.level();
            for (int i = 0; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
            if (s.accel > 0) {
                s.accel--;
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 6, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 6, 1, false, false));
                Vec3 v = p.getDeltaMovement();
                if (v.horizontalDistanceSqr() > 0.01) {
                    Vec3 q = p.position().add(0, 1, 0);
                    l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 2, 0.2, 0.5, 0.2, 0.0);
                    if (p.tickCount % 3 == 0) l.sendParticles(new DustParticleOptions(GREEN, 1.2f), q.x, q.y, q.z, 3, 0.3, 0.6, 0.3, 0);
                }
                if (s.accel == 0) Koming.bar(p, "§7투사 가속 종료");
            }
            s.fx.removeIf(f -> f.tick(l, p, s));
        }
    }

    public static void stopAll() {
        for (Frozen f : FROZEN.values()) if (f.e instanceof Mob m && m.isAlive() && !f.wasNoAi) m.setNoAi(false);
        FROZEN.clear();
        STATES.clear();
    }
}
