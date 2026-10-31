package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkItems;
import com.tubilt.jjk.JjkMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 커맨드 인커 — 작은 체인 커맨드 블록이 주인 곁을 떠돌며 염력과 사슬로 싸운다.
 *  0 염력 들기  : 대상을 높이 들어올렸다가 내리꽂음
 *  1 염력 투척  : 대상을 끌어와 정면으로 집어던짐
 *  2 사슬 속박  : 주변 최대 6명을 사슬로 5초간 묶음
 *  3 사슬 선풍  : 인커가 빠르게 돌며 사슬로 주변을 끌어당겨 휩쓸음
 *
 * 커맨드 수호와 한 손씩 들고 우클릭하면 「커맨드 수호.체인」으로 합체한다.
 * 체인 수호의 명령 목록 맨 끝에 이 4가지가 추가된다 (능력은 인커에 있고 수호의 명령 휠에서 고른다).
 *
 * 조작(인커 단독): 우클릭 = 소환 / 발동, 웅크리고 우클릭 = 능력 바꾸기, 웅크리고 하늘 보며 우클릭 = 해제
 */
public final class CommandInker {
    private CommandInker() {}

    public static final String NAME = "커맨드 인커";
    public static final String[] MODES = {"/inker 염력 들기", "/inker 염력 투척", "/inker 사슬 속박", "/inker 사슬 선풍"};
    static final int[] CD = {120, 100, 220, 300};
    static final int CYAN = 0x40E8FF, PURPLE = 0xB45CFF, WHITE = 0xFFFFFF;
    static final int FOREVER = 20 * 60 * 60 * 24, LINKS = 7;

    interface Fx { boolean tick(ServerLevel l, ServerPlayer p, Inker k); }

    static final class Inker {
        final ServerLevel level; final UUID owner; final String tag;
        Vec3 pos, vel = Vec3.ZERO, hold = null; double wander = 0; int age = 0, mode = 0;
        final int[] cd = new int[MODES.length];
        final List<Vec3> trail = new ArrayList<>();
        final List<Fx> fx = new ArrayList<>();
        Inker(ServerLevel l, ServerPlayer p) {
            level = l; owner = p.getUUID(); tag = "jjk.ink" + Integer.toHexString(owner.hashCode() & 0xFFFFFF);
            pos = p.position().add(0, 2.0, 0);
        }
        void spawn() {
            Holo.block(level, tag, pos, "minecraft:chain_command_block", -1, Holo.m(Holo.I(), 0.4, 0.4, 0.4, Vec3.ZERO, true), FOREVER, 1)
                    .follow(() -> pos, Vec3.ZERO).spin(14);
            for (int i = 0; i < LINKS; i++) {
                final int ix = i;
                double s = 0.15 - i * 0.012;
                Holo.block(level, tag, pos, i % 2 == 0 ? "minecraft:iron_block" : "minecraft:light_blue_concrete", i % 2 == 0 ? -1 : CYAN,
                        Holo.m(Holo.I(), s, s, s, Vec3.ZERO, true), FOREVER, 1).follow(() -> link(ix), Vec3.ZERO).spin(i % 2 == 0 ? 20 : -20);
            }
            Holo.flare(level, tag, pos, 3, CYAN, 8);
            Holo.sfx(level, pos, "magic", 0.9f, 1.8f);
        }
        Vec3 link(int i) {
            if (trail.isEmpty()) return pos;
            return trail.get(Math.min(trail.size() - 1, (i + 1) * 3));
        }
        void remove() { Holo.killOwner(tag); }
        void tether(Vec3 to, int hold) { Holo.beam(level, tag, pos, to, 0.07, CYAN, 1, hold, 3); }
    }

    static final Map<UUID, Inker> INKERS = new HashMap<>();

    // ═════════════════════════ 아이템
    public static boolean isInker(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().equals("command_inker");
    }
    /** 합체 전의 일반 커맨드 수호 */
    static boolean isPlainGuard(ItemStack s) { return CommandGuard.is(s) && !CommandGuard.isChain(s); }

    /** 탭·지급용 인커 아이템 */
    public static ItemStack item() {
        ItemStack st = new ItemStack(JjkItems.COMMAND_INKER.get());
        st.set(DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(s -> s.withItalic(false).withColor(CYAN).withBold(true)));
        st.set(DataComponents.ITEM_MODEL, net.minecraft.resources.ResourceLocation.withDefaultNamespace("chain_command_block"));
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        st.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("작은 체인 커맨드가 곁을 떠돌며 염력과 사슬로 싸운다").withStyle(s -> s.withItalic(true).withColor(0xAAAAAA)),
                Component.literal("우클릭: 소환 / 발동 · 웅크리고 우클릭: 능력 바꾸기").withStyle(s -> s.withItalic(false).withColor(0x55FFFF)),
                Component.literal("커맨드 수호를 반대 손에 들고 우클릭: 「커맨드 수호.체인」 합체").withStyle(s -> s.withItalic(false).withColor(0xB45CFF)))));
        return st;
    }

    /** 합체: 한 손에 인커, 다른 손에 커맨드 수호 → 우클릭. 처리했으면 true. */
    public static boolean tryFuse(ServerPlayer p) {
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (!((isInker(main) && isPlainGuard(off)) || (isPlainGuard(main) && isInker(off)))) return false;
        main.shrink(1); off.shrink(1);
        ItemStack chain = CommandGuard.chainItem();
        if (!p.getInventory().add(chain)) p.drop(chain, false);
        if (p.level() instanceof ServerLevel l) {
            Vec3 c = p.position().add(0, 1.2, 0);
            Holo.flare(l, "jjk.inkfuse", c, 9, CYAN, 10);
            Holo.shock(l, "jjk.inkfuse", p.position(), 6, PURPLE, 10);
            Holo.sfx(l, c, "stinger", 1.3f, 1.3f);
        }
        p.displayClientMessage(Component.literal("§d커맨드 수호.체인 §7— 인커의 사슬이 수호에 이어졌다. 명령 휠 맨 끝에서 고른다"), false);
        return true;
    }

    // ═════════════════════════ 사용
    /** 인커 아이템 우클릭 */
    public static boolean onUse(ServerPlayer p, ItemStack st) {
        if (!isInker(st)) return false;
        ServerLevel l = (ServerLevel) p.level();
        Inker k = INKERS.get(p.getUUID());
        if (k == null || k.level != l) { ensure(p); Koming.bar(p, "§b커맨드 인커 §7소환"); return true; }
        if (p.isShiftKeyDown()) {
            if (p.getXRot() < -50) { dismiss(p); Koming.bar(p, "§7커맨드 인커가 잠들었다"); return true; }
            k.mode = (k.mode + 1) % MODES.length;
            Koming.bar(p, "§b▶ " + MODES[k.mode]);
            return true;
        }
        cast(p, k.mode);
        return true;
    }

    static Inker ensure(ServerPlayer p) {
        ServerLevel l = (ServerLevel) p.level();
        Inker k = INKERS.get(p.getUUID());
        if (k != null && k.level == l) return k;
        if (k != null) k.remove();
        k = new Inker(l, p);
        INKERS.put(p.getUUID(), k);
        k.spawn();
        return k;
    }
    static void dismiss(ServerPlayer p) {
        Inker k = INKERS.remove(p.getUUID());
        if (k != null) k.remove();
    }

    // ═════════════════════════ 능력
    static LivingEntity pick(ServerLevel l, ServerPlayer p, double range) { return Projection.pick(l, p, range); }

    public static void cast(ServerPlayer p, int idx) {
        if (idx < 0 || idx >= MODES.length) return;
        if (p.getTags().contains("jjk.hunsu")) { Koming.bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        ServerLevel l = (ServerLevel) p.level();
        Inker k = ensure(p);
        if (k.cd[idx] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", MODES[idx], k.cd[idx] / 20f)); return; }
        if (!k.fx.isEmpty()) { Koming.bar(p, "§7인커 작동 중"); return; }
        LivingEntity t = null;
        if (idx <= 1) {
            t = pick(l, p, idx == 0 ? 26 : 22);
            if (t == null) { Koming.bar(p, "§7시선 앞에 대상이 없다"); return; }
        } else if (Projection.targets(l, p, p.position(), idx == 2 ? 14 : 7).isEmpty()) { Koming.bar(p, "§7주변에 적이 없다"); return; }
        k.cd[idx] = CD[idx];
        Holo.sfx(l, k.pos, "data", 1.0f, 1.5f);
        switch (idx) {
            case 0 -> k.fx.add(new Lift(t));
            case 1 -> k.fx.add(new Throw(t));
            case 2 -> k.fx.add(new Bind());
            case 3 -> k.fx.add(new Whirl());
            default -> { }
        }
        Koming.bar(p, "§b" + MODES[idx]);
    }

    /** 염력 들기 */
    static final class Lift implements Fx {
        final LivingEntity t; int n = 0; double y0;
        Lift(LivingEntity t) { this.t = t; this.y0 = t.getY(); }
        public boolean tick(ServerLevel l, ServerPlayer p, Inker k) {
            if (!t.isAlive() || t.level() != l) return true;
            Vec3 tc = t.position().add(0, t.getBbHeight() / 2, 0);
            k.hold = tc.add(0, 3.2, 0);
            if (n % 3 == 0) k.tether(tc, 4);
            if (n < 40) {
                double vy = t.getY() < y0 + 4.2 ? 0.28 : 0.0;
                t.setDeltaMovement(0, vy, 0); t.hurtMarked = true;
                if (n % 6 == 0) Holo.ring(l, k.tag, t.position().add(0, 0.2, 0), 1.6, 0.3, 16, 0.12, 0.03, CYAN, 6, 0, 2, 0, 5, true);
            } else if (n == 40) {
                t.setDeltaMovement(0, -2.4, 0); t.hurtMarked = true;
                Holo.sfx(l, tc, "whoosh", 1.2f, 0.7f);
            } else if (n > 42 && (t.onGround() || n >= 60)) {
                Vec3 at = t.position();
                Projection.hurt(l, p, t, 16f);
                for (LivingEntity e : Projection.targets(l, p, at, 3.5)) { if (e == t) continue; Projection.hurt(l, p, e, 9f); Koming.push(e, e.position().subtract(at).multiply(1, 0, 1).normalize().scale(0.7).add(0, 0.4, 0)); }
                Holo.flare(l, k.tag, at.add(0, 0.5, 0), 7, WHITE, 8);
                Holo.shock(l, k.tag, at, 4, CYAN, 8);
                Holo.shards(l, k.tag, at, "minecraft:stone", 10, 4);
                Holo.sfx(l, at, "impact", 1.3f, 0.8f);
                k.hold = null;
                return true;
            }
            return ++n > 80;
        }
    }

    /** 염력 투척 */
    static final class Throw implements Fx {
        final LivingEntity t; int n = 0;
        Throw(LivingEntity t) { this.t = t; }
        public boolean tick(ServerLevel l, ServerPlayer p, Inker k) {
            if (!t.isAlive() || t.level() != l) return true;
            Vec3 look = p.getLookAngle();
            Vec3 tc = t.position().add(0, t.getBbHeight() / 2, 0);
            k.hold = p.getEyePosition().add(look.scale(2.6));
            if (n < 8) {
                Vec3 want = p.getEyePosition().add(look.scale(3.0)).subtract(0, t.getBbHeight() / 2, 0);
                Koming.push(t, want.subtract(t.position()).scale(0.35));
                k.tether(tc, 3);
            } else if (n == 8) {
                Koming.push(t, look.scale(2.6).add(0, 0.25, 0));
                Holo.flare(l, k.tag, tc, 5, CYAN, 6);
                Holo.sfx(l, tc, "whoosh", 1.3f, 1.1f);
            } else {
                if (n % 2 == 0) l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, tc.x, tc.y, tc.z, 3, 0.2, 0.2, 0.2, 0.02);
                boolean hitFoe = false;
                for (LivingEntity e : Projection.targets(l, p, tc, 1.6)) if (e != t) { Projection.hurt(l, p, e, 8f); hitFoe = true; }
                if (hitFoe || t.horizontalCollision || t.onGround() || n >= 30) {
                    Projection.hurt(l, p, t, 14f);
                    Holo.flare(l, k.tag, tc, 7, WHITE, 8);
                    Holo.shock(l, k.tag, t.position(), 3.5, CYAN, 8);
                    Holo.sfx(l, tc, "impact", 1.3f, 1.0f);
                    k.hold = null;
                    return true;
                }
            }
            return ++n > 40;
        }
    }

    /** 사슬 속박 */
    static final class Bind implements Fx {
        int n = 0; List<LivingEntity> ts;
        public boolean tick(ServerLevel l, ServerPlayer p, Inker k) {
            if (n == 0) {
                ts = Projection.targets(l, p, p.position(), 14);
                if (ts.size() > 6) ts = new ArrayList<>(ts.subList(0, 6));
                for (LivingEntity e : ts) {
                    Projection.Frozen f = Projection.FROZEN.get(e.getUUID());
                    if (f != null) f.left = 100;
                    else { Projection.FROZEN.put(e.getUUID(), new Projection.Frozen(e, 100)); if (e instanceof Mob m) m.setNoAi(true); }
                    Holo.flare(l, k.tag, e.position().add(0, e.getBbHeight() / 2, 0), 4, CYAN, 6);
                }
                Holo.sfx(l, k.pos, "plate", 1.3f, 0.9f);
            }
            k.hold = ts.isEmpty() ? null : ts.get(0).position().add(0, 3, 0);
            if (n % 4 == 0) for (LivingEntity e : ts) if (e.isAlive()) k.tether(e.position().add(0, e.getBbHeight() / 2, 0), 5);
            if (++n > 24) { k.hold = null; return true; }
            return false;
        }
    }

    /** 사슬 선풍 */
    static final class Whirl implements Fx {
        int n = 0; static final int LEN = 60;
        public boolean tick(ServerLevel l, ServerPlayer p, Inker k) {
            double a = n * 0.55;
            k.hold = p.position().add(Math.cos(a) * 3.2, 1.3, Math.sin(a) * 3.2);
            if (n == 0) Holo.sfx(l, k.pos, "riser", 1.2f, 1.3f);
            if (n % 5 == 0) {
                for (LivingEntity e : Projection.targets(l, p, p.position(), 7)) {
                    Projection.hurt(l, p, e, 4f);
                    Vec3 pull = p.position().subtract(e.position()).multiply(1, 0, 1).scale(0.18);
                    Koming.push(e, pull.add(0, 0.15, 0));
                    k.tether(e.position().add(0, e.getBbHeight() / 2, 0), 4);
                }
                Holo.ring(l, k.tag, p.position().add(0, 1.2, 0), 3.4, 0.5, 20, 0.12, 0.03, PURPLE, 5, 0, 2, 0, 6, true);
            }
            if (++n >= LEN) {
                for (LivingEntity e : Projection.targets(l, p, p.position(), 7)) { Projection.hurt(l, p, e, 10f); Koming.push(e, e.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(1.4).add(0, 0.5, 0)); }
                Holo.flare(l, k.tag, p.position().add(0, 1.2, 0), 9, WHITE, 8);
                Holo.shock(l, k.tag, p.position(), 7, CYAN, 10);
                Holo.sfx(l, p.position(), "boom", 1.3f, 1.2f);
                k.hold = null;
                return true;
            }
            return false;
        }
    }

    // ═════════════════════════ 매 틱
    static boolean carrying(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isInker(s) || CommandGuard.isChain(s)) return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (INKERS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Inker>> it = INKERS.entrySet().iterator();
        while (it.hasNext()) {
            Inker k = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(k.owner);
            if (p == null || !p.isAlive() || p.level() != k.level || (k.age % 20 == 0 && !carrying(p))) { k.remove(); it.remove(); continue; }
            k.age++;
            for (int i = 0; i < k.cd.length; i++) if (k.cd[i] > 0) k.cd[i]--;
            // 떠돌기: 주인 주위를 불규칙하게 맴돎 (사슬은 꼬리처럼 따라옴)
            k.wander += 0.045 + 0.02 * Math.sin(k.age * 0.07);
            Vec3 base = p.position().add(0, 1.9, 0);
            Vec3 goal = k.hold != null ? k.hold : base.add(Math.cos(k.wander * 1.3) * 2.3 + Math.cos(k.wander * 0.41) * 0.9,
                    Math.sin(k.wander * 0.9) * 0.45, Math.sin(k.wander * 1.1) * 2.3 + Math.sin(k.wander * 0.53) * 0.9);
            double gain = k.hold != null ? 0.22 : 0.07;
            k.vel = k.vel.scale(k.hold != null ? 0.6 : 0.82).add(goal.subtract(k.pos).scale(gain));
            if (k.vel.length() > 1.6) k.vel = k.vel.normalize().scale(1.6);
            k.pos = k.pos.add(k.vel);
            if (k.pos.distanceToSqr(p.position()) > 40 * 40) k.pos = base;
            k.trail.add(0, k.pos);
            if (k.trail.size() > LINKS * 3 + 4) k.trail.remove(k.trail.size() - 1);
            if (k.age % 3 == 0) k.level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, k.pos.x, k.pos.y, k.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
            k.fx.removeIf(f -> f.tick(k.level, p, k));
            if (k.fx.isEmpty()) k.hold = null;
        }
    }

    public static void stopAll() {
        for (Inker k : INKERS.values()) k.remove();
        INKERS.clear();
    }
}
