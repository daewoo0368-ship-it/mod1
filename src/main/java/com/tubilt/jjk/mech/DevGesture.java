package com.tubilt.jjk.mech;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 개발자의 손동작 — 우클릭하면 화면에 손이 나타나 손가락이 돋아나며 「복마어주자」의 인(印)을 맺고(클라이언트 3D, GloveRender),
 * 잠시 뒤 앞쪽에 사당이 솟아오르며 약 6초간 사방에 참격(해·捌)이 쏟아진다. 주력은 쓰지 않고 쿨타임 45초.
 */
public final class DevGesture {
    private DevGesture() {}

    static final int PREP = 26, LENGTH = 150, CD = 900;
    static final Map<UUID, Run> RUN = new HashMap<>();
    static final Map<UUID, Integer> COOL = new HashMap<>();

    public static boolean is(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(com.tubilt.jjk.JjkMod.MOD_ID) && key.getPath().equals("dev_gesture");
    }

    public static boolean onUse(Player ent, ItemStack st) {
        if (!is(st)) return false;
        if (!(ent instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel l)) return true;
        if (RUN.containsKey(p.getUUID())) return true;
        int cd = COOL.getOrDefault(p.getUUID(), 0);
        if (cd > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7손동작 대기 %.1f초", cd / 20f)); return true; }
        COOL.put(p.getUUID(), CD);
        RUN.put(p.getUUID(), new Run(p, l));
        return true;
    }

    static final class Run {
        final ServerPlayer p; final ServerLevel l; final String tag = "jjk.gest" + (Holo.uid++);
        int t = 0; Vec3 c; double yaw; final List<Holo.Elem> els = new ArrayList<>(); final Map<UUID, Integer> last = new HashMap<>();
        Run(ServerPlayer p, ServerLevel l) { this.p = p; this.l = l; }

        /** 사당 부품: {블록, 발광(-1 없음), sx,sy,sz, ox,oy,oz} (사당 기준, 앞=+z, 바닥=0) */
        void shrine() {
            Vec3 f = p.getLookAngle().multiply(1, 0, 1);
            f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
            c = p.position().add(f.scale(10));
            Vec3 toP = p.position().subtract(c);
            yaw = Math.atan2(toP.x, toP.z);
            double[][] R = Holo.rotY(yaw);
            Object[][] P = {
                {"polished_blackstone", -1, 13, .8, 13, 0, .4, 0},
                {"stone_bricks", -1, 5, .4, 2.2, 0, .2, 7.4}, {"stone_bricks", -1, 4, .4, 1.4, 0, .6, 6.9},
                {"black_concrete", -1, 7.4, 4.4, 7.4, 0, 3.0, 0},
                {"red_concrete", -1, 8.6, .35, 8.6, 0, .98, 0}, {"red_concrete", -1, 8.6, .35, 8.6, 0, 5.1, 0},
                {"nether_bricks", -1, 12.5, .6, 12.5, 0, 5.7, 0}, {"nether_bricks", -1, 10.2, .6, 10.2, 0, 6.3, 0}, {"nether_bricks", -1, 7, .6, 7, 0, 6.9, 0},
                {"red_nether_bricks", -1, 4, .6, 4, 0, 7.5, 0}, {"gold_block", 0xFFD84A, .5, 2.6, .5, 0, 9.0, 0},
                {"bone_block", -1, 3.6, 2.8, .6, 0, 3.4, 3.8},
                {"black_concrete", -1, .9, .9, .3, -.9, 3.8, 4.15}, {"black_concrete", -1, .9, .9, .3, .9, 3.8, 4.15},
                {"red_concrete", 0xFF2020, .4, .4, .2, -.9, 3.75, 4.3}, {"red_concrete", 0xFF2020, .4, .4, .2, .9, 3.75, 4.3},
                {"black_concrete", -1, .5, .7, .3, 0, 3.0, 4.15},
            };
            List<Object[]> all = new ArrayList<>(Arrays.asList(P));
            for (int i = -3; i <= 3; i++) all.add(new Object[]{"bone_block", -1, .34, .55, .3, i * .46, 2.35, 4.15});
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                all.add(new Object[]{"red_concrete", -1, .9, 4.4, .9, sx * 4.9, 3.0, sz * 4.9});
                all.add(new Object[]{"shroomlight", 0xFFB050, .9, .9, .9, sx * 4.9, 5.6, sz * 4.9});
            }
            for (Object[] q : all) {
                double sx = ((Number) q[2]).doubleValue(), sy = ((Number) q[3]).doubleValue(), sz = ((Number) q[4]).doubleValue();
                Vec3 off = Holo.apply(R, new Vec3(((Number) q[5]).doubleValue(), ((Number) q[6]).doubleValue(), ((Number) q[7]).doubleValue()));
                float[] full = Holo.m(R, sx, sy, sz, off, true);
                float[] tiny = Holo.m(R, sx * .02, .02, sz * .02, new Vec3(off.x, -1.5, off.z), true);
                els.add(Holo.block(l, tag, c, "minecraft:" + q[0], (Integer) q[1], tiny, LENGTH + 40, 2).key(1, full, 16));
            }
            Holo.shock(l, tag, c, 16, 0xC01010, 14);
            Holo.ring(l, tag + "x", c.add(0, .1, 0), 2, 15, 36, 0.6, 0.06, 0xFF2020, 6, 40, 12, 0, 3, true);
            Holo.flare(l, tag + "x", c.add(0, 4, 0), 14, 0x801010, 14);
            Holo.sfx(l, c, "deep", 2f, 0.5f); Holo.sfx(l, c, "boom", 2f, 0.6f);
            Titan.shake(l, c, 60, true);
        }

        void slashes() {
            Random r = new Random(p.getRandom().nextLong());
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 16;
                Vec3 pt = c.add(Math.cos(a) * d, r.nextDouble() * 4.5, Math.sin(a) * d);
                double ang = r.nextDouble() * Math.PI;
                Vec3 dir = new Vec3(Math.cos(ang), (r.nextDouble() - 0.5) * 1.2, Math.sin(ang)).normalize().scale(2.6);
                Holo.beam(l, tag + "s", pt.subtract(dir), pt.add(dir), 0.05, i == 0 ? 0xFF4040 : 0xFFFFFF, 0, 1, 3);
                for (LivingEntity e : Projection.targets(l, p, pt, 3.0)) {
                    int lh = last.getOrDefault(e.getUUID(), -99);
                    if (t - lh < 5) continue;
                    last.put(e.getUUID(), t);
                    e.invulnerableTime = 0;
                    e.hurtServer(l, l.damageSources().indirectMagic(p, p), 8f);
                    Holo.flare(l, tag + "s", e.position().add(0, e.getBbHeight() / 2, 0), 1.6, 0xFF4040, 3);
                }
                if (r.nextInt(3) == 0 && Koming.griefOn(l)) Koming.erase(l, pt, 1.3);
            }
            if (t % 3 == 0) Holo.sfx(l, c, "slash", 0.9f, 0.9f + (t % 7) * 0.05f);
            if (t % 25 == 0) {   // 捌: 가장 가까운 대상에게 큰 일격
                LivingEntity best = null; double bd = 1e9;
                for (LivingEntity e : Projection.targets(l, p, c, 20)) { double d = e.distanceToSqr(c); if (d < bd) { bd = d; best = e; } }
                if (best != null) {
                    Vec3 m = best.position().add(0, best.getBbHeight() / 2, 0);
                    Holo.beam(l, tag + "s", m.add(0, 6, 0), m.subtract(0, 1, 0), 0.5, 0xFF2020, 0, 2, 5);
                    Holo.beam(l, tag + "s", m.add(0, 6, 0), m.subtract(0, 1, 0), 0.15, 0xFFFFFF, 0, 2, 5);
                    best.invulnerableTime = 0;
                    best.hurtServer(l, l.damageSources().indirectMagic(p, p), 20f);
                    Holo.shock(l, tag + "s", best.position(), 3, 0xFF4040, 6);
                    Holo.sfx(l, m, "impact", 1.3f, 0.8f);
                }
            }
        }

        boolean tick() {
            if (!p.isAlive() || p.level() != l) { end(); return true; }
            t++;
            if (t == 1) { Koming.bar(p, "§c손동작 — 인을 맺는다"); Holo.sfx(l, p.position(), "charge", 1.2f, 0.7f); }
            if (t == PREP - 4) {
                com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.CutIn("領域展開 「伏魔御廚子」", "개발자의 손동작", 0xC01010, 1));
                Holo.sfx(l, p.position(), "stinger", 1.5f, 0.7f);
            }
            if (t == PREP) shrine();
            if (t > PREP && t < PREP + LENGTH - 30) slashes();
            if (t >= PREP + LENGTH) { end(); return true; }
            return false;
        }
        void end() {
            for (Holo.Elem e : els) e.keyframe(Holo.m(Holo.I(), .02, .02, .02, new Vec3(0, -1.5, 0), true), 12);
            if (c != null) {
                Holo.flare(l, tag + "x", c.add(0, 3, 0), 12, 0xFF4040, 10);
                Holo.shock(l, tag + "x", c, 14, 0xFFFFFF, 10);
                Holo.shards(l, tag + "x", c.add(0, 3, 0), "minecraft:red_nether_bricks", 16, 1.0);
                Holo.sfx(l, c, "shatter", 1.6f, 0.6f);
            }
            Holo.killOwner(tag + "s");
            // 접힘 연출이 끝난 뒤 정리
            final List<Holo.Elem> kill = els;
            for (Holo.Elem e : kill) e.endIn(14);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Integer>> it = COOL.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (en.getValue() <= 1) it.remove(); else en.setValue(en.getValue() - 1);
        }
        for (Iterator<Run> it = RUN.values().iterator(); it.hasNext(); ) {
            Run r = it.next();
            boolean done;
            try { done = r.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 손동작 오류", e); done = true; r.end(); }
            if (done) it.remove();
        }
    }
    public static void stopAll() { for (Run r : RUN.values()) r.end(); RUN.clear(); COOL.clear(); }
}
