package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 개발자의 레버 — 세계의 디버그 모드를 켜고 끈다.
 *  레버 내림(ON)  : 반경 48 「DEBUG MODE」 — 모든 몹·투사체가 멈추고 히트박스·NBT 가 드러남. 시전자는 비행.
 *                  이 동안 멈춘 대상에게 준 피해는 바로 들어가지 않고 「변경 로그」에 쌓임.
 *  레버 올림(OFF) : 「COMMIT」 — 쌓인 로그 ×2 (+15) 를 한꺼번에 적용 (안쪽에서 바깥으로 파도처럼),
 *                  멈춰 있던 투사체는 쏜 사람에게 되돌아감. 최대 30초 뒤 자동 커밋.
 */
public final class DevLever {
    private DevLever() {}

    public static final String NAME = "개발자의 레버";
    static final int RADIUS = 48, MAX = 600, COOLDOWN = 100;
    static final int GREEN = 0x55FF55, RED = 0xFF3040, CYAN = 0x55FFFF;
    static final Map<UUID, Session> ACTIVE = new HashMap<>();
    static final Map<UUID, Long> CD = new HashMap<>();

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DEV_LEVER.get()); }

    /** 우클릭: 켜기/끄기. */
    public static void flip(ServerPlayer p, ServerLevel l) {
        Session s = ACTIVE.get(p.getUUID());
        if (s != null) { s.commit(); return; }
        long now = l.getGameTime();
        Long until = CD.get(p.getUUID());
        if (until != null && now < until) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(String.format(Locale.ROOT, "§7레버가 아직 뜨겁다 (%.1f초)", (until - now) / 20.0)), true);
            return;
        }
        ACTIVE.put(p.getUUID(), new Session(p, l));
    }

    static void tickAll() {
        for (Iterator<Session> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Session s = it.next();
            try { if (s.tick()) it.remove(); }
            catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 레버 오류", e); s.release(); it.remove(); }
        }
    }

    static void stopAll() { for (Session s : ACTIVE.values()) s.release(); ACTIVE.clear(); }

    /** 피해 훅: 디버그 모드에서 멈춘 대상이 맞으면 피해를 로그로 돌리고 취소. */
    public static boolean intercept(LivingEntity target, DamageSource src, float amount) {
        for (Session s : ACTIVE.values()) {
            if (s.committing) continue;
            Frozen f = s.frozen.get(target.getUUID());
            if (f == null) continue;
            f.logged += amount;
            f.hits++;
            Entity atk = src.getEntity();
            if (atk instanceof Player) s.say(target, String.format(Locale.ROOT, "+%.1f  (logged · %d)", amount, f.hits), GREEN, 0.8);
            s.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 4, 0.3, 0.3, 0.3, 0);
            s.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
            return true;
        }
        return false;
    }

    static final class Frozen {
        final LivingEntity e; final boolean noAi, noGrav; float logged; int hits; Holo.Elem label;
        Frozen(LivingEntity e) { this.e = e; this.noAi = e instanceof Mob m && m.isNoAi(); this.noGrav = e.isNoGravity(); }
    }

    static final class Session {
        final ServerLevel level; final UUID owner; final Vec3 c; final String tag;
        final Map<UUID, Frozen> frozen = new HashMap<>();
        final Map<Projectile, Vec3> shots = new HashMap<>();
        final List<Holo.Elem> lever = new ArrayList<>();
        int t = 0, ct = 0; boolean committing = false, flew;
        final List<Frozen> order = new ArrayList<>();

        Session(ServerPlayer p, ServerLevel l) {
            level = l; owner = p.getUUID(); c = p.position(); tag = "jjk.lever" + (Holo.uid++);
            flew = p.getAbilities().mayfly;
            if (!flew) { p.getAbilities().mayfly = true; p.onUpdateAbilities(); }
            // 거대 레버 (하늘): 받침 + 손잡이 + 붉은 손잡이 끝 — 내려가는 동작
            Vec3 at = c.add(0, 14, 0);
            lever.add(Holo.block(l, tag, at, "minecraft:cobblestone", -1, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), MAX + 60, 0)
                    .key(1, Holo.m(Holo.I(), 4, 1.2, 2.6, Vec3.ZERO, true), 6));
            lever.add(handle(at, -0.7, 0.01).key(3, handleM(-0.7, 1), 4).key(9, handleM(0.75, 1), 4));
            lever.add(knob(at, -0.7, 0.01).key(3, knobM(-0.7, 1), 4).key(9, knobM(0.75, 1), 4));
            for (Holo.Elem e : lever) e.spin(0.6);
            Holo.circle(l, tag, c.add(0, 0.1, 0), RADIUS, GREEN, MAX + 40, 0);
            Holo.circle(l, tag, c.add(0, 0.2, 0), RADIUS * 0.5, GREEN, MAX + 40, 0);
            Holo.pillar(l, tag, at, 40, 1.2, GREEN, 2, 4, 8);
            // F3 디버그 패널 (시전자 앞 공중, 1초마다 갱신)
            Holo.text(l, tag, c.add(0, 4.2, 0), "{text:\"\"}", "center", 0xC0000000, false, Holo.m(Holo.I(), 1.1, 1.1, 1.1, Vec3.ZERO, false), MAX + 40, 1)
                    .follow(() -> { ServerPlayer o = player(); return o == null ? c : o.position(); }, new Vec3(0, 3.4, 0))
                    .text(age -> f3());
            freezeAround();
            Holo.sfx(l, c, "glitch", 2.5f, 0.6f);
            Holo.sfx(l, c, "data", 2.5f, 0.8f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 3f, 0.5f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5f, 0.6f);
            for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(c) < RADIUS + 16) {
                JjkNet.toPlayer(sp, new Payloads.Screen(5, GREEN, MAX * 50, 0.18f));
                JjkNet.toPlayer(sp, new Payloads.Screen(4, 0, 600, 1f));
                JjkNet.toPlayer(sp, new Payloads.Screen(6, GREEN, 900, 1));
            }
            JjkNet.toPlayer(p, new Payloads.CutIn("개발자의 레버", "DEBUG MODE ─ ON", GREEN, 1));
            sayAt(c.add(0, 8, 0), "/debug start  ·  /tick freeze  ·  F3+B", GREEN, 1.6);
        }

        ServerPlayer player() { return level.getServer().getPlayerList().getPlayer(owner); }

        Holo.Elem handle(Vec3 at, double ang, double s) { return Holo.block(level, tag, at, "minecraft:stripped_dark_oak_log", -1, handleM(ang, s), MAX + 60, 0); }
        Holo.Elem knob(Vec3 at, double ang, double s) { return Holo.block(level, tag, at, "minecraft:redstone_block", RED, knobM(ang, s), MAX + 60, 0); }
        static float[] handleM(double ang, double s) {
            double[][] r = Holo.rotX(ang);
            return Holo.m(r, 0.6 * s, 5 * s, 0.6 * s, new Vec3(0, 0.6, 0).add(Holo.apply(r, new Vec3(0, 2.5 * s, 0))), true);
        }
        static float[] knobM(double ang, double s) {
            double[][] r = Holo.rotX(ang);
            return Holo.m(r, 1.2 * s, 1.2 * s, 1.2 * s, new Vec3(0, 0.6, 0).add(Holo.apply(r, new Vec3(0, 5.2 * s, 0))), true);
        }

        String f3() {
            ServerPlayer o = player();
            Vec3 q = o == null ? c : o.position();
            int logs = 0; float total = 0;
            for (Frozen f : frozen.values()) { logs += f.hits; total += f.logged; }
            String l1 = String.format(Locale.ROOT, "Minecraft 1.21.10 (jjk-debug)  %s", committing ? "COMMITTING…" : "DEBUG MODE");
            String l2 = String.format(Locale.ROOT, "XYZ: %.3f / %.3f / %.3f", q.x, q.y, q.z);
            String l3 = String.format(Locale.ROOT, "E: %d frozen · P: %d projectiles", frozen.size(), shots.size());
            String l4 = String.format(Locale.ROOT, "changelog: %d entries · Δ %.1f dmg", logs, total);
            String l5 = String.format(Locale.ROOT, "auto-commit in %.1fs  ·  [레버 올리기 = COMMIT]", Math.max(0, MAX - t) / 20.0);
            return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(l1, GREEN, true) + ",{text:\"\\n\"}," + Holo.tx(l2, 0xFFFFFF, false) + ",{text:\"\\n\"},"
                    + Holo.tx(l3, 0xFFFFFF, false) + ",{text:\"\\n\"}," + Holo.tx(l4, 0xFFE070, false) + ",{text:\"\\n\"}," + Holo.tx(l5, 0xAAAAAA, false) + "]}";
        }

        void sayAt(Vec3 at, String s, int rgb, double sc) {
            Holo.banner(level, tag, at, "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}", sc, 50);
        }
        void say(LivingEntity e, String s, int rgb, double sc) {
            Holo.banner(level, tag, e.position().add(0, e.getBbHeight() + 1.4 + level.random.nextDouble() * 0.6, 0),
                    "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}", sc, 26);
        }

        void freezeAround() {
            AABB box = new AABB(c, c).inflate(RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof Player) && x.position().distanceTo(c) <= RADIUS)) {
                if (frozen.containsKey(e.getUUID()) || e instanceof DevVehicle || e.getTags().contains("jjk.guard")) continue;
                Frozen f = new Frozen(e);
                frozen.put(e.getUUID(), f);
                if (e instanceof Mob m) m.setNoAi(true);
                e.setNoGravity(true);
                e.setDeltaMovement(Vec3.ZERO);
                Holo.hitbox(level, tag, e, GREEN, MAX + 20);
                String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                f.label = Holo.text(level, tag, e.position().add(0, e.getBbHeight() + 0.5, 0), "{text:\"\"}", "center", 0x90000000, false,
                        Holo.m(Holo.I(), 0.6, 0.6, 0.6, Vec3.ZERO, false), MAX + 20, 1)
                        .follow(e::position, new Vec3(0, e.getBbHeight() + 0.5, 0))
                        .text(age -> "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("[" + id + "] ", GREEN, true) + ","
                                + Holo.tx(String.format(Locale.ROOT, "HP %.1f  NoAI:1b", e.getHealth()), 0xFFFFFF, false)
                                + (f.logged > 0 ? "," + Holo.tx(String.format(Locale.ROOT, "  Δ-%.1f", f.logged * 2 + 15), RED, true) : "") + "]}");
                order.add(f);
            }
            for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, x -> x.position().distanceTo(c) <= RADIUS)) {
                if (shots.containsKey(pr)) continue;
                shots.put(pr, pr.getDeltaMovement());
                pr.setNoGravity(true);
                pr.setDeltaMovement(Vec3.ZERO);
                pr.hurtMarked = true;
            }
        }

        /** true = 끝남. */
        boolean tick() {
            t++;
            ServerPlayer p = player();
            if (p == null || !p.isAlive() || p.level() != level) { release(); return true; }
            if (!committing) {
                if (t % 10 == 0) freezeAround();           // 새로 들어온 몹·투사체도 멈춤
                for (Frozen f : frozen.values()) if (f.e.isAlive()) { f.e.setDeltaMovement(Vec3.ZERO); f.e.hurtMarked = true; }
                for (Map.Entry<Projectile, Vec3> en : shots.entrySet()) if (en.getKey().isAlive()) { en.getKey().setDeltaMovement(Vec3.ZERO); en.getKey().hurtMarked = true; }
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 10, 3, false, false));
                if (t % 20 == 0) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1, c.z, 40, RADIUS * 0.6, 3, RADIUS * 0.6, 0);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, 1.6f);
                }
                if (t >= MAX) commit();
                return false;
            }
            // ── COMMIT: 안쪽부터 바깥으로 로그 적용
            ct++;
            int per = Math.max(1, order.size() / 18 + 1);
            for (int i = 0; i < per && !order.isEmpty() && ct > 10; i++) apply(order.remove(0), p);
            if (ct == 12) {
                for (Map.Entry<Projectile, Vec3> en : shots.entrySet()) {
                    Projectile pr = en.getKey();
                    if (!pr.isAlive()) continue;
                    pr.setNoGravity(false);
                    Entity sh = pr.getOwner();
                    Vec3 back = sh != null ? sh.position().add(0, sh.getBbHeight() / 2, 0).subtract(pr.position()).normalize().scale(Math.max(1.5, en.getValue().length() * 1.5))
                            : en.getValue().scale(-1.5);
                    pr.setOwner(p);
                    pr.setDeltaMovement(back);
                    pr.hurtMarked = true;
                }
                shots.clear();
            }
            if (ct == 10) {
                Holo.shock(level, tag, c, RADIUS, GREEN, 18);
                Holo.flare(level, tag, c.add(0, 14, 0), 10, 0xFFFFFF, 8);
                Holo.sfx(level, c, "boom", 3f, 0.7f);
                Titan.shake(level, c, RADIUS + 20, true);
            }
            if (order.isEmpty() && ct > 30) { release(); return true; }
            return false;
        }

        void apply(Frozen f, ServerPlayer p) {
            LivingEntity e = f.e;
            if (e instanceof Mob m) m.setNoAi(f.noAi);
            e.setNoGravity(f.noGrav);
            if (f.label != null) f.label.endIn(1);
            if (!e.isAlive()) return;
            float dmg = f.logged * 2 + 15;
            Vec3 o = e.position().add(0, e.getBbHeight() / 2, 0);
            say(e, String.format(Locale.ROOT, "COMMIT  -%.1f", dmg), f.logged > 0 ? RED : 0xFFFFFF, 1.2 + Math.min(2, f.logged / 40));
            Holo.explosion(level, tag, o, 1.6 + Math.min(4, f.logged / 25), f.logged > 0 ? RED : GREEN);
            Holo.pillar(level, tag, e.position(), 20, 0.8, f.logged > 0 ? RED : GREEN, 1, 2, 5);
            e.invulnerableTime = 0;
            e.hurtServer(level, level.damageSources().playerAttack(p), dmg);
            e.setDeltaMovement(o.subtract(c).normalize().scale(0.8).add(0, 0.5, 0));
            e.hurtMarked = true;
            level.playSound(null, o.x, o.y, o.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.6f);
        }

        void commit() {
            if (committing) return;
            committing = true;
            ServerPlayer p = player();
            order.sort((a, b) -> Double.compare(a.e.position().distanceToSqr(c), b.e.position().distanceToSqr(c)));
            // 레버 올라감
            if (lever.size() == 3) {
                lever.get(1).keyIn(1, handleM(-0.75, 1), 4);
                lever.get(2).keyIn(1, knobM(-0.75, 1), 4);
            }
            for (Holo.Elem e : lever) e.endIn(40);
            int logs = 0; for (Frozen f : frozen.values()) logs += f.hits;
            sayAt(c.add(0, 9, 0), String.format(Locale.ROOT, "/reload  ·  committing %d changes", logs + frozen.size()), RED, 1.8);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 3f, 0.4f);
            Holo.sfx(level, c, "stinger", 3f, 0.8f);
            Holo.sfx(level, c, "riser", 2.5f, 1.4f);
            for (ServerPlayer sp : level.players()) if (sp.position().distanceTo(c) < RADIUS + 16) {
                JjkNet.toPlayer(sp, new Payloads.Screen(5, RED, 1400, 0.4f));
                JjkNet.toPlayer(sp, new Payloads.Screen(3, 0, 300, 1));
                JjkNet.toPlayer(sp, new Payloads.Screen(4, 0, 1200, 1.4f));
                JjkNet.toPlayer(sp, new Payloads.Screen(6, RED, 1200, 1));
            }
            if (p != null) JjkNet.toPlayer(p, new Payloads.CutIn("개발자의 레버", "COMMIT ─ 변경 사항을 세계에 적용", RED, 1));
        }

        /** 모든 상태 되돌림. */
        void release() {
            for (Frozen f : frozen.values()) {
                if (f.e instanceof Mob m) m.setNoAi(f.noAi);
                f.e.setNoGravity(f.noGrav);
                if (f.label != null) f.label.endIn(1);
            }
            for (Map.Entry<Projectile, Vec3> en : shots.entrySet()) if (en.getKey().isAlive()) { en.getKey().setNoGravity(false); en.getKey().setDeltaMovement(en.getValue()); }
            frozen.clear(); shots.clear(); order.clear();
            for (Holo.Elem e : lever) e.endIn(1);
            ServerPlayer p = player();
            if (p != null) {
                if (!flew && !p.isCreative() && !p.isSpectator()) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 80, 0, false, false));
            }
            CD.put(owner, level.getGameTime() + COOLDOWN);
        }
    }
}
