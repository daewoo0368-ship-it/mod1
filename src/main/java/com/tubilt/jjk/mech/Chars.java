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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * 새 캐릭터 5명 (각 3술식) — 아이템 우클릭 / V 메뉴로 사용.
 *  후시구로 메구미(십종영법): 옥견 · 탈토 · 대사
 *  쿠기사키 노바라(공명): 못박기 · 공명 · 철정
 *  나나미 켄토: 십수할 · 초과근무 · 해체
 *  오코츠 유타: 리카 완전현현 · 모방 · 순애빔
 *  후시구로 토우지: 천역모 투척 · 순보 · 주구 해방
 * 아이템 이름 접두사 → 술식 id 접두사: meg_→m_ · nob_→n_ · nan_→na_ · yut_→y_ · toj_→t_
 */
public final class Chars {
    private Chars() {}

    static final String[] ITEM_PREFIX = {"meg_", "nob_", "nan_", "yut_", "toj_"};
    static final String[] ID_PREFIX = {"m_", "n_", "na_", "y_", "t_"};

    // id, 이름, 주력, 쿨타임(틱)
    static final Object[][] DEF = {
        {"m_dogs", "옥견", 120, 240}, {"m_rabbits", "탈토", 150, 360}, {"m_serpent", "대사", 260, 500},
        {"n_nail", "못박기", 40, 40}, {"n_resonance", "공명", 120, 120}, {"n_hairpin", "철정", 200, 360},
        {"na_ratio", "십수할", 90, 120}, {"na_overtime", "초과근무", 120, 600}, {"na_cleave", "해체", 180, 260},
        {"y_rika", "리카 완전현현", 300, 900}, {"y_copy", "모방", 60, 200}, {"y_love", "순애빔", 350, 600},
        {"t_halberd", "천역모 투척", 60, 100}, {"t_dash", "순보", 40, 60}, {"t_heaven", "주구 해방", 100, 800},
    };
    static final int N = DEF.length;
    static int idx(String id) { for (int i = 0; i < N; i++) if (DEF[i][0].equals(id)) return i; return -1; }
    static String name(int i) { return (String) DEF[i][1]; }
    static int cost(int i) { return (Integer) DEF[i][2]; }
    static int cdOf(int i) { return (Integer) DEF[i][3]; }

    static final class State {
        final UUID id; final String tag;
        final int[] cd = new int[N];
        final List<Fx> fx = new ArrayList<>();
        UUID mark; int markT; int last = -1;
        State(UUID id) { this.id = id; this.tag = "jjk.chr" + Integer.toHexString(id.hashCode() & 0xFFFFFF); }
    }
    interface Fx { boolean tick(ServerLevel l, ServerPlayer p, State s); }

    static final Map<UUID, State> STATES = new HashMap<>();
    static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), State::new); }

    /** 아이템 경로 → 술식 id (아니면 null) */
    static String idOf(ItemStack s) {
        if (s.isEmpty()) return null;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!key.getNamespace().equals(JjkMod.MOD_ID)) return null;
        String p = key.getPath();
        for (int i = 0; i < ITEM_PREFIX.length; i++)
            if (p.startsWith(ITEM_PREFIX[i])) { String id = ID_PREFIX[i] + p.substring(ITEM_PREFIX[i].length()); return idx(id) >= 0 ? id : null; }
        return null;
    }
    /** Abilities.techFor 용: 아이템 경로 → 술식 id */
    public static String techIdForPath(String p) {
        for (int i = 0; i < ITEM_PREFIX.length; i++)
            if (p.startsWith(ITEM_PREFIX[i])) return ID_PREFIX[i] + p.substring(ITEM_PREFIX[i].length());
        return null;
    }

    public static boolean onUse(Player ent, ItemStack st) {
        String id = idOf(st);
        if (id == null) return false;
        if (ent instanceof ServerPlayer p) cast(p, id);
        return true;
    }

    // ═════════════════════════ 공통 도우미
    static void hurt(ServerLevel l, ServerPlayer p, LivingEntity e, float dmg, boolean magic) {
        if (e == null || e == p || !e.isAlive()) return;
        e.invulnerableTime = 0;
        e.hurtServer(l, magic ? l.damageSources().indirectMagic(p, p) : l.damageSources().playerAttack(p), dmg);
    }
    static Vec3 mid(Entity e) { return e.position().add(0, e.getBbHeight() / 2, 0); }
    static Vec3 flat(Vec3 v) { Vec3 f = v.multiply(1, 0, 1); return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize(); }
    static Vec3 rotY(Vec3 v, double a) { double c = Math.cos(a), s = Math.sin(a); return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c); }
    static double yawOf(Vec3 d) { return Math.toDegrees(Math.atan2(-d.x, d.z)); }
    static void dust(ServerLevel l, int rgb, float size, Vec3 c, int n, double sp) { l.sendParticles(new DustParticleOptions(rgb, size), c.x, c.y, c.z, n, sp, sp, sp, 0); }
    static Vec3 clipEnd(ServerLevel l, Vec3 a, Vec3 b) {
        BlockHitResult hit = l.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    /** 블록 디스플레이 여러 개로 이루어진 생물/물체. parts: {block, glow, sx,sy,sz, ox,oy,oz} (z = 앞). */
    static Object[] P(String block, int glow, double sx, double sy, double sz, double ox, double oy, double oz) {
        return new Object[]{block, glow, new double[]{sx, sy, sz, ox, oy, oz}};
    }
    static List<Holo.Elem> creature(ServerLevel l, String tag, Supplier<Vec3> pos, DoubleSupplier yaw, double sc, List<Object[]> parts, int life) {
        List<Holo.Elem> out = new ArrayList<>();
        for (Object[] q : parts) {
            double[] d = (double[]) q[2];
            out.add(Holo.block(l, tag, pos.get(), "minecraft:" + q[0], (Integer) q[1],
                    Holo.m(Holo.I(), d[0] * sc, d[1] * sc, d[2] * sc, new Vec3(d[3] * sc, d[4] * sc, d[5] * sc), true), life, 2).attach(pos, yaw));
        }
        return out;
    }
    static void killAll(List<Holo.Elem> els) { for (Holo.Elem e : els) e.kill(); }

    // ═════════════════════════ 시전
    public static void cast(ServerPlayer p, String id) {
        int i = idx(id);
        if (i < 0) return;
        if (p.getTags().contains("jjk.hunsu")) { Koming.bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        State s = st(p);
        ServerLevel l = (ServerLevel) p.level();
        if (s.cd[i] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", name(i), s.cd[i] / 20f)); return; }
        boolean copy = id.equals("y_copy");
        if (copy && s.last < 0) { Koming.bar(p, "§7모방할 술식이 없다 (먼저 다른 술식을 써야 한다)"); return; }
        int use = copy ? s.last : i;
        if (!precheck(p, l, s, use)) return;
        if (!Koming.spend(p, cost(i))) { Koming.bar(p, "§c주력이 부족하다 §7(필요 " + cost(i) + ")"); return; }
        s.cd[i] = cdOf(i);
        if (copy) {
            s.cd[use] = 0;   // 모방은 상대 술식을 공짜로 복사
            Holo.flare(l, s.tag, p.position().add(0, 1, 0), 4, 0xFFFFFF, 8);
            Koming.bar(p, "§f모방 §7— 「" + name(use) + "」");
        } else s.last = i;
        exec(p, l, s, use);
    }

    /** 대상이 필요한 술식은 대상이 없으면 주력을 쓰기 전에 거절 */
    static boolean precheck(ServerPlayer p, ServerLevel l, State s, int i) {
        String id = (String) DEF[i][0];
        if (id.equals("y_rika") && s.fx.stream().anyMatch(f -> f instanceof Rika)) { Koming.bar(p, "§7리카가 이미 현현해 있다"); return false; }
        switch (id) {
            case "n_nail", "na_ratio" -> {
                if (Projection.pick(l, p, id.equals("n_nail") ? 36 : 22) == null) { Koming.bar(p, "§7시선 앞에 대상이 없다"); return false; }
            }
            case "n_resonance" -> {
                Entity m = s.mark == null ? null : l.getEntity(s.mark);
                if (!(m instanceof LivingEntity le) || !le.isAlive() || s.markT <= 0) { Koming.bar(p, "§7공명시킬 표식이 없다 (먼저 못박기)"); return false; }
            }
            default -> { }
        }
        return true;
    }

    static void exec(ServerPlayer p, ServerLevel l, State s, int i) {
        String id = (String) DEF[i][0];
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        switch (id) {
            case "m_dogs" -> { s.fx.add(new Dogs(p)); Koming.bar(p, "§f옥견 §7— 흰 개와 검은 개"); }
            case "m_rabbits" -> { s.fx.add(new Rabbits(p.position())); Koming.bar(p, "§f탈토 §7— 토끼 떼"); }
            case "m_serpent" -> { s.fx.add(new Serpent(p)); Koming.bar(p, "§2대사 §7— 큰 뱀"); }
            case "n_nail" -> nail(p, l, s);
            case "n_resonance" -> resonance(p, l, s);
            case "n_hairpin" -> { s.fx.add(new Hairpin()); Koming.bar(p, "§6철정"); }
            case "na_ratio" -> ratio(p, l, s);
            case "na_overtime" -> { s.fx.add(new Overtime()); Koming.bar(p, "§e초과근무 §7— 잔업 시작"); }
            case "na_cleave" -> cleave(p, l, s);
            case "y_rika" -> { s.fx.add(new Rika(p)); Koming.bar(p, "§f리카 §7— 완전현현"); }
            case "y_love" -> { s.fx.add(new Love()); Koming.bar(p, "§d순애빔 §7— 충전"); }
            case "t_halberd" -> { s.fx.add(new Halberd(p)); Holo.sfx(l, eye, "whoosh", 1.2f, 1.4f); Koming.bar(p, "§a천역모"); }
            case "t_dash" -> dash(p, l, s);
            case "t_heaven" -> {
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 2));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 2));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 0));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 400, 1));
                Vec3 c = p.position().add(0, 1, 0);
                Holo.flare(l, s.tag, c, 6, 0x2E7D32, 8); Holo.shock(l, s.tag, p.position(), 5, 0x7CFF6A, 8);
                Holo.sfx(l, c, "magic", 1.2f, 0.6f);
                Koming.bar(p, "§a주구 해방 §7— 20초간 육체 강화");
            }
            default -> { }
        }
    }

    // ═════════════════════════ 후시구로 메구미
    static final class Dog {
        Vec3 pos; double yaw; final boolean white; List<Holo.Elem> els; int bite = 0;
        Dog(Vec3 p, boolean w) { pos = p; white = w; }
    }
    static List<Object[]> dogParts(boolean white) {
        String b = white ? "white_concrete" : "black_concrete";
        List<Object[]> out = new ArrayList<>();
        out.add(P(b, -1, .8, .7, 1.4, 0, .75, 0)); out.add(P(b, -1, .6, .55, .6, 0, 1.05, .9)); out.add(P(b, -1, .3, .25, .4, 0, .95, 1.3));
        for (int k = -1; k <= 1; k += 2) {
            out.add(P(b, -1, .14, .3, .14, k * .2, 1.45, .85));
            out.add(P("red_concrete", 0xFF2020, .1, .1, .05, k * .15, 1.15, 1.21));
            for (int z = -1; z <= 1; z += 2) out.add(P(b, -1, .22, .5, .22, k * .28, .25, z * .5));
        }
        out.add(P(b, -1, .14, .14, .6, 0, .95, -.95));
        return out;
    }
    static final class Dogs implements Fx {
        final Dog[] dogs = new Dog[2]; int t = 0; final int LIFE = 120;
        Dogs(ServerPlayer p) {
            Vec3 side = rotY(flat(p.getLookAngle()), Math.PI / 2).scale(1.8);
            dogs[0] = new Dog(p.position().add(side), true); dogs[1] = new Dog(p.position().subtract(side), false);
            dogs[0].yaw = dogs[1].yaw = p.getYRot();
        }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) for (Dog d : dogs) {
                final Dog dd = d;
                d.els = creature(l, s.tag + "d", () -> dd.pos, () -> dd.yaw, 1.5, dogParts(d.white), LIFE + 20);
                Holo.flare(l, s.tag, d.pos.add(0, 1, 0), 4, d.white ? 0xFFFFFF : 0x303040, 8);
                Holo.sfx(l, d.pos, "magic", 1f, 0.9f);
            }
            for (Dog d : dogs) {
                LivingEntity tg = null; double bd = 26 * 26;
                for (LivingEntity e : Projection.targets(l, p, p.position(), 26)) { double q = e.distanceToSqr(d.pos.x, d.pos.y, d.pos.z); if (q < bd) { bd = q; tg = e; } }
                Vec3 goal = tg != null ? tg.position() : p.position().add(rotY(flat(p.getLookAngle()), d.white ? 1.2 : -1.2).scale(2.5));
                Vec3 dv = goal.subtract(d.pos);
                double dl = dv.length();
                if (dl > 0.1) {
                    double sp = tg != null ? 1.1 : Math.min(0.8, dl * 0.2);
                    if (dl > 2.2 || tg == null) d.pos = d.pos.add(dv.scale(Math.min(sp, dl) / dl));
                    if (dv.x * dv.x + dv.z * dv.z > 0.04) d.yaw = yawOf(dv);
                }
                if (tg != null && dl < 3.0 && --d.bite <= 0) {
                    d.bite = 8;
                    hurt(l, p, tg, 7f, false);
                    Holo.flare(l, s.tag, mid(tg), 1.6, d.white ? 0xFFFFFF : 0x8040FF, 4);
                    Holo.sfx(l, mid(tg), "slash", 0.8f, 1.3f);
                    Koming.push(tg, d.pos.subtract(tg.position()).multiply(1, 0, 1).normalize().scale(-0.25).add(0, 0.15, 0));
                }
            }
            if (t >= LIFE) {
                for (Dog d : dogs) { killAll(d.els); Holo.flare(l, s.tag, d.pos.add(0, 1, 0), 3, 0x8080A0, 6); }
                return true;
            }
            return false;
        }
    }

    static final class Rabbits implements Fx {
        final Vec3 c; int t = 0; List<Holo.Elem> els = new ArrayList<>(); final Vec3[] pos = new Vec3[14];
        Rabbits(Vec3 c) { this.c = c; Arrays.fill(pos, c); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) for (int i = 0; i < pos.length; i++) {
                final int ii = i;
                els.add(Holo.block(l, s.tag + "r", c, "minecraft:white_concrete", -1, Holo.m(Holo.I(), 0.55, 0.45, 0.8, Vec3.ZERO, true), 80, 2).follow(() -> pos[ii], Vec3.ZERO));
                els.add(Holo.block(l, s.tag + "r", c, "minecraft:white_concrete", -1, Holo.m(Holo.I(), 0.14, 0.5, 0.14, new Vec3(0, 0.45, 0.2), true), 80, 2).follow(() -> pos[ii], Vec3.ZERO));
            }
            double u = Math.min(1, t / 36.0), R = 15 * Math.pow(u, 0.6);
            for (int i = 0; i < pos.length; i++) {
                double a = i * Math.PI * 2 / pos.length + t * 0.05;
                pos[i] = c.add(Math.cos(a) * R, Math.abs(Math.sin(t * 0.55 + i)) * 1.4, Math.sin(a) * R);
            }
            if (t % 3 == 0) Holo.ring(l, s.tag, c.add(0, 0.2, 0), R, R + 0.5, 22, 0.3, 0.04, 0xFFFFFF, 2, 0, 3, 0, 0, true);
            if (t == 30) {
                for (LivingEntity e : Projection.targets(l, p, c, 15)) {
                    hurt(l, p, e, 6f, true);
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                    Holo.flare(l, s.tag, mid(e), 2, 0xFFFFFF, 6);
                }
                Holo.shock(l, s.tag, c, 15, 0xE8E8FF, 10);
                Holo.sfx(l, c, "impact", 1.2f, 1.4f);
            }
            if (t >= 44) { killAll(els); return true; }
            return false;
        }
    }

    static final class Serpent implements Fx {
        final Vec3 dir; Vec3 head; final Vec3[] seg = new Vec3[14]; int t = 0, stop = -1; final Set<UUID> hit = new HashSet<>(); List<Holo.Elem> els = new ArrayList<>();
        Serpent(ServerPlayer p) {
            dir = p.getLookAngle().normalize(); head = p.getEyePosition().add(dir.scale(1.5)).subtract(0, 0.6, 0);
            Arrays.fill(seg, head);
        }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) {
                Holo.sfx(l, head, "deep", 1.5f, 0.7f);
                for (int i = 0; i < seg.length; i++) {
                    final int ii = i; double sz = i == 0 ? 2.4 : 1.8 - i * 0.07;
                    els.add(Holo.block(l, s.tag + "s", head, i == 0 ? "minecraft:green_concrete" : i % 2 == 0 ? "minecraft:black_concrete" : "minecraft:green_terracotta", -1,
                            Holo.m(Holo.I(), sz, sz, sz, Vec3.ZERO, true), 70, 2).follow(() -> seg[ii], Vec3.ZERO));
                    if (i == 0) for (int k = -1; k <= 1; k += 2) {
                        final int kk = k;
                        els.add(Holo.block(l, s.tag + "s", head, "minecraft:red_concrete", 0xFF2020, Holo.m(Holo.I(), 0.35, 0.35, 0.2, Vec3.ZERO, true), 70, 2)
                                .follow(() -> seg[0], new Vec3(kk * 0.7, 0.5, 0)));
                    }
                }
            }
            if (stop < 0) {
                Vec3 nx = head.add(dir.scale(2.4));
                Vec3 cl = clipEnd(l, head, nx);
                if (cl != nx || t > 16) stop = t;
                head = cl;
                for (LivingEntity e : Projection.targets(l, p, head, 3.4)) if (hit.add(e.getUUID())) {
                    hurt(l, p, e, 32f, true);
                    Koming.push(e, dir.scale(1.4).add(0, 0.4, 0));
                    Holo.flare(l, s.tag, mid(e), 3, 0x2E7D32, 6);
                }
                if (t % 2 == 0) l.sendParticles(ParticleTypes.POOF, head.x, head.y, head.z, 6, 0.8, 0.8, 0.8, 0.02);
            }
            seg[0] = head;
            for (int i = 1; i < seg.length; i++) {
                Vec3 d = seg[i - 1].subtract(seg[i]); double dl = d.length();
                if (dl > 1.5) seg[i] = seg[i - 1].subtract(d.scale(1.5 / dl));
            }
            if (stop > 0 && t - stop > 10) { killAll(els); Holo.flare(l, s.tag, head, 5, 0x2E7D32, 8); return true; }
            return false;
        }
    }

    // ═════════════════════════ 쿠기사키 노바라
    static void nail(ServerPlayer p, ServerLevel l, State s) {
        LivingEntity t = Projection.pick(l, p, 36);
        Vec3 a = p.getEyePosition().add(p.getLookAngle().scale(0.7)).subtract(0, 0.15, 0), b = mid(t);
        Holo.beam(l, s.tag, a, b, 0.07, 0xC8D0E0, 1, 3, 4);
        Holo.beam(l, s.tag, a, b, 0.2, 0xFF9020, 1, 2, 4);
        hurt(l, p, t, 12f, false);
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
        s.mark = t.getUUID(); s.markT = 300;
        Holo.flare(l, s.tag, b, 2.4, 0xFF9020, 8);
        Holo.sfx(l, b, "impact", 1f, 1.6f);
        Koming.bar(p, "§6못박기 §7— 표식 부착");
    }
    static void resonance(ServerPlayer p, ServerLevel l, State s) {
        LivingEntity m = (LivingEntity) l.getEntity(s.mark);
        Vec3 c = mid(m);
        s.mark = null; s.markT = 0;
        for (LivingEntity e : Projection.targets(l, p, c, 6)) hurt(l, p, e, e == m ? 40f : 18f, true);
        Holo.flare(l, s.tag, c, 8, 0xFF9020, 10);
        Holo.shock(l, s.tag, m.position(), 6, 0xFFD080, 10);
        Holo.ring(l, s.tag, c, 1, 6, 28, 0.5, 0.06, 0xFF9020, 3, 2, 6, 0, 5, true);
        Holo.sfx(l, c, "boom", 1.5f, 1.2f);
        l.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 40, 1.2, 1.2, 1.2, 0.4);
        Koming.bar(p, "§6공명!");
    }
    static final class Hairpin implements Fx {
        int t = 0;
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            Vec3 c = p.getEyePosition();
            if (t < 16) {
                double a = t * 0.9;
                for (int k = 0; k < 7; k++) {
                    Vec3 q = c.add(rotY(new Vec3(1.6, 0.2 + 0.25 * Math.sin(a + k), 0), a * 0.4 + k * Math.PI * 2 / 7));
                    l.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
                if (t % 4 == 0) Holo.sfx(l, c, "charge", 0.6f, 1.4f + t * 0.03f);
                return false;
            }
            Vec3 look = p.getLookAngle();
            List<LivingEntity> cand = new ArrayList<>();
            for (LivingEntity e : Projection.targets(l, p, c, 26)) {
                Vec3 d = mid(e).subtract(c);
                if (d.length() > 0.5 && d.normalize().dot(look) > 0.75) cand.add(e);
            }
            cand.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            int n = 0;
            for (LivingEntity e : cand) {
                if (n++ >= 8) break;
                hurt(l, p, e, 16f, false);
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
                Holo.beam(l, s.tag, c.add(look.scale(0.6)), mid(e), 0.1, 0xFF9020, 1, 3, 4);
                Holo.flare(l, s.tag, mid(e), 2, 0xFFD080, 6);
                if (n == 1) { s.mark = e.getUUID(); s.markT = 300; }
            }
            if (cand.isEmpty()) for (int k = 0; k < 7; k++) {
                Vec3 end = clipEnd(l, c, c.add(rotY(look, (k - 3) * 0.06).scale(26)));
                Holo.beam(l, s.tag, c.add(look.scale(0.6)), end, 0.08, 0xC8D0E0, 1, 2, 4);
            }
            Holo.sfx(l, c, "impact", 1.2f, 1.5f);
            return true;
        }
    }

    // ═════════════════════════ 나나미 켄토
    static void ratio(ServerPlayer p, ServerLevel l, State s) {
        LivingEntity t = Projection.pick(l, p, 22);
        Vec3 from = p.position();
        Vec3 d = flat(t.position().subtract(p.position()));
        Vec3 to = t.position().subtract(d.scale(1.6));
        if (l.noCollision(p.getBoundingBox().move(to.x - p.getX(), to.y - p.getY(), to.z - p.getZ()))) p.teleportTo(to.x, to.y, to.z);
        Vec3 pt = t.position().add(0, t.getBbHeight() * 0.7, 0);   // 7 : 3 지점
        Holo.beam(l, s.tag, pt.add(d.cross(new Vec3(0, 1, 0)).scale(-1.2)), pt.add(d.cross(new Vec3(0, 1, 0)).scale(1.2)), 0.08, 0xFFE070, 1, 4, 6);
        Holo.beam(l, s.tag, from.add(0, 1, 0), p.position().add(0, 1, 0), 0.05, 0xFFFFFF, 1, 2, 4);
        Holo.flare(l, s.tag, pt, 3, 0xFFE070, 8);
        float dmg = 18f + Math.min(40f, t.getMaxHealth() * 0.10f);
        hurt(l, p, t, dmg, false);
        Holo.sfx(l, pt, "slash", 1.3f, 0.9f);
        l.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 24, 0.5, 0.3, 0.5, 0.3);
        Koming.bar(p, "§e십수할 §7— 7 : 3");
    }
    static final class Overtime implements Fx {
        int t = 0;
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (t == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 240, 2));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 240, 1));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 240, 1));
                Holo.flare(l, s.tag, p.position().add(0, 1, 0), 5, 0xFFE070, 8);
                Holo.ring(l, s.tag, p.position().add(0, 0.1, 0), 1, 4, 20, 0.4, 0.05, 0xFFE070, 3, 0, 6, 0, 4, true);
                Holo.sfx(l, p.position(), "riser", 1f, 0.9f);
            }
            if (++t % 10 == 0) dust(l, 0xFFE070, 1.2f, p.position().add(0, 1, 0), 6, 0.5);
            if (t >= 240 || !p.isAlive()) {
                if (p.isAlive()) {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    Koming.bar(p, "§7잔업 종료 §8— 피로");
                }
                return true;
            }
            return false;
        }
    }
    static void cleave(ServerPlayer p, ServerLevel l, State s) {
        Vec3 c = p.getEyePosition(), look = flat(p.getLookAngle());
        int n = 0;
        for (LivingEntity e : Projection.targets(l, p, p.position(), 7)) {
            Vec3 d = flat(e.position().subtract(p.position()));
            if (d.dot(look) < 0.2) continue;
            hurt(l, p, e, 26f, false);
            Koming.push(e, d.scale(1.0).add(0, 0.35, 0));
            Holo.flare(l, s.tag, mid(e), 2, 0xFFE070, 5);
            n++;
        }
        for (int k = -3; k <= 3; k++) {
            Vec3 end = c.add(rotY(look, k * 0.32).scale(6.5)).subtract(0, 0.4, 0);
            Holo.beam(l, s.tag, c.add(look.scale(0.5)).subtract(0, 0.4, 0), end, 0.06, 0xFFF0B0, 1, 2, 5);
        }
        Holo.shock(l, s.tag, p.position(), 6, 0xFFE070, 6);
        Holo.sfx(l, c, "slash", 1.4f, 0.7f);
        Koming.bar(p, "§e해체" + (n > 0 ? " §7× " + n : ""));
    }

    // ═════════════════════════ 오코츠 유타
    static final class Rika implements Fx {
        int t = 0; final int LIFE = 300; Vec3 pos; double yaw; List<Holo.Elem> els; int atk = 0, slam = 0;
        Rika(ServerPlayer p) { pos = p.position().subtract(flat(p.getLookAngle()).scale(2.5)); yaw = p.getYRot(); }
        static List<Object[]> parts() {
            String w = "white_concrete";
            List<Object[]> o = new ArrayList<>();
            o.add(P(w, -1, 1.4, 2.0, 0.9, 0, 3.0, 0)); o.add(P(w, -1, 1.2, 1.2, 1.0, 0, 4.6, 0.1));
            o.add(P("black_concrete", -1, 1.0, 0.18, 0.1, 0, 4.3, 0.68));
            for (int k = -1; k <= 1; k += 2) {
                o.add(P("red_concrete", 0xFF2020, 0.22, 0.22, 0.1, k * 0.3, 4.85, 0.65));
                o.add(P(w, -1, 0.5, 3.2, 0.5, k * 1.1, 2.8, 0.35));
                o.add(P("black_concrete", -1, 0.7, 0.5, 0.7, k * 1.1, 1.0, 0.45));
                o.add(P(w, -1, 0.55, 2.0, 0.55, k * 0.4, 1.0, 0));
            }
            o.add(P("gold_block", 0xFFD84A, 2.2, 0.12, 2.2, 0, 5.6, 0));
            return o;
        }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            if (!p.isAlive()) { if (els != null) killAll(els); return true; }
            t++;
            Vec3 want = p.position().subtract(flat(p.getLookAngle()).scale(2.6)).add(0, 0.6 + Math.sin(t * 0.1) * 0.25, 0);
            pos = pos.add(want.subtract(pos).scale(0.25));
            yaw = p.getYRot();
            if (t == 1) {
                els = creature(l, s.tag + "k", () -> pos, () -> yaw, 1.3, parts(), LIFE + 20);
                Holo.flare(l, s.tag, pos.add(0, 3, 0), 10, 0xFFFFFF, 12);
                Holo.shock(l, s.tag, pos, 8, 0xFFFFFF, 10);
                Holo.sfx(l, pos, "choir", 1.5f, 0.8f); Holo.sfx(l, pos, "deep", 1.5f, 0.6f);
            }
            if (--atk <= 0) {
                LivingEntity tg = null; double bd = 14 * 14;
                for (LivingEntity e : Projection.targets(l, p, p.position(), 14)) { double q = e.distanceToSqr(p); if (q < bd) { bd = q; tg = e; } }
                if (tg != null) {
                    atk = 8;
                    Vec3 hand = pos.add(0, 3.6, 0);
                    Holo.beam(l, s.tag, hand, mid(tg), 0.25, 0xFFFFFF, 1, 2, 4);
                    Holo.beam(l, s.tag, hand, mid(tg), 0.5, 0xC080FF, 1, 2, 5);
                    hurt(l, p, tg, 14f, true);
                    Holo.flare(l, s.tag, mid(tg), 2.4, 0xFFFFFF, 5);
                    Holo.sfx(l, mid(tg), "laser", 0.8f, 1.2f);
                }
            }
            if (--slam <= 0) {
                slam = 40;
                boolean any = false;
                for (LivingEntity e : Projection.targets(l, p, pos, 7)) { hurt(l, p, e, 30f, false); Koming.push(e, flat(e.position().subtract(pos)).scale(1.4).add(0, 0.5, 0)); any = true; }
                if (any) { Holo.shock(l, s.tag, pos, 7, 0xFFFFFF, 8); Holo.sfx(l, pos, "boom", 1.4f, 0.9f); }
            }
            if (t >= LIFE) { killAll(els); Holo.flare(l, s.tag, pos.add(0, 3, 0), 8, 0xFFFFFF, 10); Holo.sfx(l, pos, "shatter", 1.2f, 0.8f); return true; }
            return false;
        }
    }
    static final class Love implements Fx {
        int t = 0; Vec3 dir;
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            Vec3 c = p.getEyePosition();
            if (t <= 24) {
                dir = p.getLookAngle();
                double r = 3.5 * (1 - t / 24.0) + 0.4;
                for (int k = 0; k < 6; k++) { Vec3 q = c.add(dir.scale(2.5)).add(rotY(new Vec3(r, 0, 0), t * 0.5 + k * 1.05).add(0, Math.sin(t * 0.5 + k) * r, 0)); l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
                if (t == 1) Holo.sfx(l, c, "charge", 1.5f, 0.7f);
                if (t == 12) Holo.flare(l, s.tag, c.add(dir.scale(2.5)), 4, 0xFF80D0, 14);
                return false;
            }
            Vec3 a = c.add(dir.scale(1.5)), b = clipEnd(l, a, c.add(dir.scale(46)));
            Holo.beam(l, s.tag, a, b, 3.2, 0xFF60C0, 2, 10, 10);
            Holo.beam(l, s.tag, a, b, 1.6, 0xFFFFFF, 2, 10, 10);
            Holo.flare(l, s.tag, b, 8, 0xFFFFFF, 10);
            Vec3 ab = b.subtract(a); double len = ab.length(), L2 = len * len;
            for (LivingEntity e : Projection.targets(l, p, a.add(ab.scale(0.5)), len / 2 + 4)) {
                Vec3 q = mid(e).subtract(a);
                double u = Math.max(0, Math.min(1, q.dot(ab) / Math.max(1e-6, L2)));
                if (q.subtract(ab.scale(u)).length() <= 2.6) { hurt(l, p, e, 50f, true); Koming.push(e, dir.scale(1.2).add(0, 0.5, 0)); }
            }
            Holo.shock(l, s.tag, b, 6, 0xFF80D0, 10);
            Holo.sfx(l, c, "laser", 2f, 0.6f); Holo.sfx(l, b, "boom", 1.5f, 0.7f);
            Koming.push(p, dir.scale(-0.6));
            return true;
        }
    }

    // ═════════════════════════ 후시구로 토우지
    static final class Halberd implements Fx {
        Vec3 pos; final Vec3 dir; int t = 0; final Set<UUID> hit = new HashSet<>(); List<Holo.Elem> els; double yaw; boolean stuck;
        Halberd(ServerPlayer p) { dir = p.getLookAngle().normalize(); pos = p.getEyePosition().add(dir.scale(1.2)).subtract(0, 0.2, 0); yaw = yawOf(dir); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) {
                List<Object[]> o = new ArrayList<>();
                o.add(P("black_concrete", -1, 0.12, 0.12, 2.6, 0, 0, 0)); o.add(P("iron_block", -1, 0.1, 0.5, 0.7, 0, 0, 1.6)); o.add(P("iron_block", -1, 0.1, 0.5, 0.7, 0, 0, -1.6));
                els = creature(l, s.tag + "h", () -> pos, () -> yaw, 1.0, o, 70);
                double pitch = -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dir.y))));
                for (Holo.Elem e : els) e.pitch0 = pitch;
            }
            if (!stuck) {
                Vec3 nx = pos.add(dir.scale(3.2));
                Vec3 cl = clipEnd(l, pos, nx);
                for (LivingEntity e : Projection.targets(l, p, pos.add(dir.scale(1.6)), 3.4)) if (hit.add(e.getUUID())) {
                    hurt(l, p, e, 30f, true);
                    Koming.push(e, dir.scale(0.8).add(0, 0.25, 0));
                    Holo.flare(l, s.tag, mid(e), 2.4, 0x7CFF6A, 5);
                }
                if (cl != nx || t > 16) { stuck = true; t = 50; }
                pos = cl;
                Holo.beam(l, s.tag, pos.subtract(dir.scale(2.5)), pos, 0.05, 0xE0FFE0, 0, 1, 3);
            }
            if (t >= 62) { killAll(els); return true; }
            return false;
        }
    }
    static void dash(ServerPlayer p, ServerLevel l, State s) {
        Vec3 a = p.position(), look = flat(p.getLookAngle());
        Vec3 eye = p.getEyePosition();
        Vec3 end = clipEnd(l, eye, eye.add(look.scale(9)));
        Vec3 to = end.subtract(eye).length() < 8.9 ? end.subtract(look.scale(0.7)) : end;
        double ty = a.y;
        Vec3 dest = new Vec3(to.x, ty, to.z);
        if (!l.noCollision(p.getBoundingBox().move(dest.x - a.x, 0, dest.z - a.z))) { Koming.bar(p, "§7앞이 막혀 있다"); return; }
        Vec3 v = dest.subtract(a);
        for (LivingEntity e : Projection.targets(l, p, a.add(v.scale(0.5)).add(0, 1, 0), v.length() / 2 + 2)) {
            double u = Math.max(0, Math.min(1, e.position().subtract(a).dot(v) / Math.max(1e-6, v.lengthSqr())));
            if (e.position().subtract(a.add(v.scale(u))).length() < 2.0) { hurt(l, p, e, 9f, false); Holo.flare(l, s.tag, mid(e), 1.8, 0xFFFFFF, 4); }
        }
        Holo.beam(l, s.tag, a.add(0, 0.9, 0), dest.add(0, 0.9, 0), 0.5, 0xE0FFE0, 1, 3, 8);
        Holo.beam(l, s.tag, a.add(0, 1.3, 0), dest.add(0, 1.3, 0), 0.2, 0xFFFFFF, 1, 3, 8);
        p.teleportTo(dest.x, dest.y, dest.z);
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 2));
        Holo.sfx(l, dest, "whoosh", 1.2f, 1.7f);
        Koming.bar(p, "§a순보");
    }

    // ═════════════════════════ 틱
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            if (s == null) continue;
            if (!p.isAlive()) { s.fx.clear(); s.mark = null; continue; }
            ServerLevel l = (ServerLevel) p.level();
            for (int i = 0; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
            if (s.markT > 0 && --s.markT == 0) s.mark = null;
            s.fx.removeIf(f -> f.tick(l, p, s));
        }
    }
    public static void stopAll() { STATES.clear(); }
}
