package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;

/**
 * 적혈조술 (초소) — 피를 다루는 술식. 쓸 때마다 체력(피)을 조금 소모한다.
 *  bind  적박(赤縛)      : 붉은 사슬로 대상을 4초간 속박
 *  pierce 백렴·천혈(穿血): 피를 압축해 1초 충전 후 관통 광선
 *  nova  초신성(超新星)  : 피 구체 5발 연사, 닿으면 폭발
 *  scale 적린(赤鱗)      : 15초간 근력·속도·내성 강화
 */
public final class BloodManip {
    private BloodManip() {}

    public static final String[] IDS = {"bind", "pierce", "nova", "scale"};
    static final String[] NAMES = {"적박", "백렴·천혈", "초신성", "적린"};
    static final int BIND = 0, PIERCE = 1, NOVA = 2, SCALE = 3;
    static final int[] COST = {70, 160, 220, 100};
    static final int[] CD = {200, 140, 360, 400};
    static final float[] BLOOD = {2f, 3f, 4f, 2f};   // 소모 체력
    static final int RED = 0xC01020, BRIGHT = 0xFF3040;

    static final class State {
        final UUID id; final String tag;
        final int[] cd = new int[IDS.length];
        final List<Fx> fx = new ArrayList<>();
        State(UUID id) { this.id = id; this.tag = "jjk.bld" + Integer.toHexString(id.hashCode() & 0xFFFFFF); }
    }
    interface Fx { boolean tick(ServerLevel l, ServerPlayer p, State s); }

    static final Map<UUID, State> STATES = new HashMap<>();
    static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), State::new); }

    static boolean isItem(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().startsWith("blood_");
    }
    public static boolean isBlood(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isItem(inv.getItem(i))) return true;
        return false;
    }
    public static boolean onUse(Player ent, ItemStack st) {
        if (!isItem(st)) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        cast(p, BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().substring("blood_".length()));
        return true;
    }

    public static void cast(ServerPlayer p, String id) {
        int idx = Arrays.asList(IDS).indexOf(id);
        if (idx < 0) return;
        if (!isBlood(p)) { Koming.bar(p, "§7적혈조술 아이템이 있어야 쓸 수 있다"); return; }
        if (p.getTags().contains("jjk.hunsu")) { Koming.bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        State s = st(p);
        ServerLevel l = (ServerLevel) p.level();
        if (s.fx.stream().anyMatch(f -> f instanceof Pierce || f instanceof Nova)) { Koming.bar(p, "§7시전 중"); return; }
        if (s.cd[idx] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", NAMES[idx], s.cd[idx] / 20f)); return; }
        if (p.getHealth() <= BLOOD[idx] + 2f) { Koming.bar(p, "§c피가 모자라다"); return; }
        LivingEntity target = null;
        if (idx == BIND) {
            target = pick(l, p, 24);
            if (target == null) { Koming.bar(p, "§7시선 앞에 대상이 없다"); return; }
        }
        if (!Koming.spend(p, COST[idx])) { Koming.bar(p, "§c주력이 부족하다 §7(필요 " + COST[idx] + ")"); return; }
        s.cd[idx] = CD[idx];
        p.setHealth(p.getHealth() - BLOOD[idx]);
        Vec3 c = p.position().add(0, 1, 0);
        l.sendParticles(new DustParticleOptions(RED, 1.4f), c.x, c.y, c.z, 14, 0.4, 0.6, 0.4, 0);

        switch (idx) {
            case BIND -> {
                Projection.Frozen f = Projection.FROZEN.get(target.getUUID());
                if (f != null) f.left = 80;
                else {
                    Projection.FROZEN.put(target.getUUID(), new Projection.Frozen(target, 80));
                    if (target instanceof Mob m) m.setNoAi(true);
                }
                Vec3 t = target.position().add(0, target.getBbHeight() / 2, 0);
                Holo.beam(l, s.tag, p.getEyePosition().add(p.getLookAngle().scale(0.6)), t, 0.18, RED, 2, 6, 6);
                Holo.flare(l, s.tag, t, 5, BRIGHT, 8);
                Holo.sfx(l, t, "plate", 1.2f, 0.8f);
                Koming.bar(p, "§c적박 §7— 붉은 사슬");
            }
            case PIERCE -> { s.fx.add(new Pierce()); Koming.bar(p, "§c백렴 §7— 압축"); }
            case NOVA -> { s.fx.add(new Nova()); Koming.bar(p, "§c초신성"); }
            case SCALE -> {
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 300, 2));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 300, 1));
                Holo.flare(l, s.tag, c, 6, BRIGHT, 8);
                Holo.shock(l, s.tag, p.position(), 4, RED, 8);
                Holo.sfx(l, c, "magic", 1.2f, 0.7f);
                Koming.bar(p, "§c적린 §7— 피가 몸을 감싼다");
            }
            default -> { }
        }
    }

    static LivingEntity pick(ServerLevel l, ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null; double bestDot = 0.9;
        for (LivingEntity e : Projection.targets(l, p, eye, range)) {
            Vec3 d = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double len = d.length();
            if (len < 0.5) continue;
            double dot = d.scale(1 / len).dot(look);
            if (dot > bestDot) { bestDot = dot; best = e; }
        }
        return best;
    }

    // ═════════════════════════ 백렴·천혈
    static final class Pierce implements Fx {
        int t = 0; static final int CHARGE = 20;
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (!p.isAlive()) return true;
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            if (t < CHARGE) {
                Vec3 hand = eye.add(look.scale(1.2));
                double r = 1.2 * (1 - t / (double) CHARGE) + 0.1;
                for (int i = 0; i < 6; i++) {
                    double a = p.getRandom().nextDouble() * 6.28, b = p.getRandom().nextDouble() * 3.14 - 1.57;
                    l.sendParticles(new DustParticleOptions(RED, 1.0f), hand.x + Math.cos(a) * Math.cos(b) * r, hand.y + Math.sin(b) * r, hand.z + Math.sin(a) * Math.cos(b) * r, 1, 0, 0, 0, 0);
                }
                if (t == 0) Holo.sfx(l, hand, "charge", 1.2f, 1.3f);
                if (t == CHARGE - 1) Holo.flare(l, s.tag, hand, 3, BRIGHT, 6);
                t++;
                return false;
            }
            double range = 45;
            BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation();
            Vec3 start = eye.add(look.scale(0.8)).add(0, -0.2, 0);
            Holo.beam(l, s.tag, start, end, 0.55, RED, 1, 5, 6);
            Holo.flare(l, s.tag, end, 5, BRIGHT, 8);
            Holo.sfx(l, start, "laser", 1.4f, 1.4f);
            Set<LivingEntity> done = new HashSet<>();
            Vec3 d = end.subtract(start);
            int steps = (int) Math.ceil(d.length() / 1.5);
            for (int i = 0; i <= steps; i++) {
                Vec3 q = start.add(d.scale(i / (double) Math.max(1, steps)));
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(1.1), x -> Projection.ok(x, p)))
                    if (done.add(e)) { Projection.hurt(l, p, e, 22f); Koming.push(e, look.scale(1.0).add(0, 0.3, 0)); l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.4, 0.3, 0.1); }
            }
            Koming.push(p, look.scale(-0.6));
            return true;
        }
    }

    // ═════════════════════════ 초신성
    static final class Orb {
        Vec3 pos, vel; int life = 50;
        Orb(Vec3 pos, Vec3 vel) { this.pos = pos; this.vel = vel; }
    }
    static final class Nova implements Fx {
        int t = 0; final List<Orb> orbs = new ArrayList<>();
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (p.isAlive() && t < 25 && t % 5 == 0) {
                Vec3 look = p.getLookAngle();
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
                double off = (t / 5 - 2) * 0.9;
                Vec3 o = p.getEyePosition().add(side.scale(off)).add(0, 0.5 + Math.abs(off) * 0.15, 0).add(look.scale(0.8));
                orbs.add(new Orb(o, look.add(side.scale(off * 0.03)).normalize().scale(1.4)));
                Holo.sfx(l, o, "magic", 0.8f, 1.4f);
            }
            t++;
            Iterator<Orb> it = orbs.iterator();
            while (it.hasNext()) {
                Orb o = it.next();
                Vec3 nxt = o.pos.add(o.vel);
                BlockHitResult hit = l.clip(new ClipContext(o.pos, nxt, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                boolean boom = hit.getType() != HitResult.Type.MISS || --o.life <= 0;
                if (!boom) for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(nxt, nxt).inflate(0.8), x -> Projection.ok(x, p))) { boom = true; break; }
                if (boom) {
                    Vec3 at = hit.getType() == HitResult.Type.MISS ? nxt : hit.getLocation();
                    for (LivingEntity e : Projection.targets(l, p, at, 4)) {
                        Projection.hurt(l, p, e, 9f);
                        Koming.push(e, e.position().subtract(at).normalize().scale(0.9).add(0, 0.35, 0));
                    }
                    Holo.flare(l, s.tag, at, 6, BRIGHT, 8);
                    Holo.shock(l, s.tag, at, 3.5, RED, 8);
                    l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
                    Holo.sfx(l, at, "boom", 1.0f, 1.4f);
                    it.remove();
                    continue;
                }
                o.pos = nxt;
                l.sendParticles(new DustParticleOptions(RED, 1.8f), nxt.x, nxt.y, nxt.z, 4, 0.12, 0.12, 0.12, 0);
                l.sendParticles(new DustParticleOptions(BRIGHT, 1.0f), nxt.x, nxt.y, nxt.z, 2, 0.05, 0.05, 0.05, 0);
            }
            return t >= 25 && orbs.isEmpty();
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
