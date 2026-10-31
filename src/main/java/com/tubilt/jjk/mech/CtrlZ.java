package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 개발자의 키캡 「Ctrl+Z」 — 세계에 실행 취소를 건다.
 *  가지고만 있어도 지난 10초를 기록: 내 위치·체력·배고픔, 주변 몹 위치, 반경 10 블록 상태, 내가 준 피해.
 *  우클릭 「Ctrl+Z 되돌리기」(30초): 화면이 VHS 처럼 되감기며 내가 지나온 길을 거꾸로 달려 10초 전 자리로.
 *      체력·배고픔은 그때로(더 높으면), 불·투사체 지움, 주변 몹은 10초 전 자리로 끌려가 시간 멀미,
 *      그 사이 부서진 블록(크리퍼 구멍 등)이 날아와 원래 자리에 다시 박힘.  ※ 내가 준 피해는 안 되돌림 (개발자 특권)
 *  웅크리고 우클릭 「Ctrl+Y 다시 실행」(30초): 지난 10초 동안 내가 넣은 공격을 잔상과 함께 그대로 한 번 더 (×1.5).
 */
public final class CtrlZ {
    private CtrlZ() {}

    record Snap(long t, Vec3 pos, float yaw, float pitch, float hp, int food) {}
    record Hit(long t, UUID target, float dmg) {}
    record Blocks10(long t, BlockPos center, Map<Long, BlockState> states) {}

    static final class Rec {
        final ArrayDeque<Snap> snaps = new ArrayDeque<>();
        final ArrayDeque<Hit> hits = new ArrayDeque<>();
        final ArrayDeque<Blocks10> blocks = new ArrayDeque<>();
        final Map<UUID, ArrayDeque<Vec3>> mobs = new HashMap<>();
        long cdZ = 0, cdY = 0;
    }
    static final Map<UUID, Rec> REC = new HashMap<>();
    static final List<Runnable[]> FX = new ArrayList<>();
    static final List<Object[]> LATER = new ArrayList<>();
    static boolean replaying = false;
    static final int WINDOW = 200, R = 10;

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.CTRL_Z.get()); }
    static boolean carries(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (is(p.getInventory().getItem(i))) return true;
        return false;
    }
    static void later(ServerLevel l, int dt, Runnable r) { LATER.add(new Object[]{l.getGameTime() + dt, r}); }
    static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }

    // ═════════ 기록
    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (int i = 0; i < LATER.size(); i++) {
            Object[] o = LATER.get(i);
            if ((long) o[0] <= now) { LATER.remove(i--); try { ((Runnable) o[1]).run(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] Ctrl+Z", e); } }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel l)) continue;
            boolean has = server.getTickCount() % 5 == 0 ? carries(p) : REC.containsKey(p.getUUID());
            if (!has) { if (server.getTickCount() % 5 == 0) REC.remove(p.getUUID()); continue; }
            Rec r = REC.computeIfAbsent(p.getUUID(), k -> new Rec());
            long t = l.getGameTime();
            if (server.getTickCount() % 5 == 0) {
                r.snaps.addLast(new Snap(t, p.position(), p.getYRot(), p.getXRot(), p.getHealth(), p.getFoodData().getFoodLevel()));
                while (!r.snaps.isEmpty() && r.snaps.peekFirst().t < t - WINDOW) r.snaps.pollFirst();
                while (!r.hits.isEmpty() && r.hits.peekFirst().t < t - WINDOW) r.hits.pollFirst();
                for (LivingEntity e : Koming.foes(l, p, p.position(), 32)) {
                    ArrayDeque<Vec3> q = r.mobs.computeIfAbsent(e.getUUID(), k -> new ArrayDeque<>());
                    q.addLast(e.position());
                    while (q.size() > WINDOW / 5) q.pollFirst();
                }
                if (server.getTickCount() % 100 == 0) r.mobs.entrySet().removeIf(en -> l.getEntity(en.getKey()) == null);
            }
            if (server.getTickCount() % 40 == 0) {   // 반경 10 블록 사진 (2초마다, 6장 = 12초)
                BlockPos c = p.blockPosition();
                Map<Long, BlockState> m = new HashMap<>();
                BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
                for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz > R * R) continue;
                    mp.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = l.getBlockState(mp);
                    if (!st.isAir() && !st.hasBlockEntity() && st.getFluidState().isEmpty()) m.put(mp.asLong(), st);
                }
                r.blocks.addLast(new Blocks10(t, c, m));
                while (r.blocks.size() > 6) r.blocks.pollFirst();
            }
            if (server.getTickCount() % 10 == 0 && is(p.getMainHandItem())) {
                double sec = r.snaps.isEmpty() ? 0 : (t - r.snaps.peekFirst().t) / 20.0;
                p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§b◀◀ Ctrl+Z §7기록 %.1f초 %s  §e▶▶ Ctrl+Y §7공격 %d회 %s", sec,
                        t < r.cdZ ? String.format(Locale.ROOT, "§8(%.0f)", (r.cdZ - t) / 20.0) : "§a●", r.hits.size(),
                        t < r.cdY ? String.format(Locale.ROOT, "§8(%.0f)", (r.cdY - t) / 20.0) : "§a●")), true);
            }
        }
    }

    /** 피해 훅: 내가 준 피해 기록 (다시 실행용). */
    public static void onDamage(LivingEntity target, Entity src, float amount) {
        if (replaying || !(src instanceof ServerPlayer p) || target == p) return;
        Rec r = REC.get(p.getUUID());
        if (r == null) return;
        r.hits.addLast(new Hit(p.level().getGameTime(), target.getUUID(), amount));
        while (r.hits.size() > 60) r.hits.pollFirst();
    }

    // ═════════ 사용
    public static void use(ServerPlayer p, ServerLevel l) {
        Rec r = REC.computeIfAbsent(p.getUUID(), k -> new Rec());
        long now = l.getGameTime();
        if (p.isShiftKeyDown()) {
            if (now < r.cdY) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7Ctrl+Y 대기 %.1f초", (r.cdY - now) / 20.0)), true); return; }
            if (r.hits.isEmpty()) { p.displayClientMessage(Component.literal("§7다시 실행할 공격 기록이 없다"), true); return; }
            r.cdY = now + 600;
            redo(p, l, r);
        } else {
            if (now < r.cdZ) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7Ctrl+Z 대기 %.1f초", (r.cdZ - now) / 20.0)), true); return; }
            if (r.snaps.size() < 4) { p.displayClientMessage(Component.literal("§7아직 되돌릴 기록이 없다"), true); return; }
            r.cdZ = now + 600;
            undo(p, l, r);
        }
    }

    /** Ctrl+Z: VHS 되감기. */
    static void undo(ServerPlayer p, ServerLevel l, Rec r) {
        List<Snap> path = new ArrayList<>(r.snaps);
        java.util.Collections.reverse(path);
        Snap old = path.get(path.size() - 1);
        String tag = "jjk.ctrlz" + (Holo.uid++);
        JjkNet.toPlayer(p, new Payloads.CutIn("Ctrl+Z", "되돌리기 — " + String.format(Locale.ROOT, "%.1f", (l.getGameTime() - old.t) / 20.0) + "초 전으로", 0x55DDFF, 1));
        JjkNet.toPlayer(p, new Payloads.Screen(5, 0xC8A070, 2400, 0.45f));
        JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 2400, 1.6f));
        JjkNet.toPlayer(p, new Payloads.Screen(6, 0xFFFFFF, 2000, 1));
        Holo.sfx(l, p.position(), "glitch", 2f, 0.6f);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
        // 지나온 길에 남는 잔상 (흰 유리 실루엣)
        for (int i = 0; i < path.size(); i += 2) {
            Snap s = path.get(i);
            Holo.block(l, tag, s.pos, "minecraft:white_stained_glass", 0x55DDFF, Holo.m(Holo.rotY(-Math.toRadians(s.yaw)), 0.6, 1.8, 0.4, new Vec3(0, 0.9, 0), true), 40 + i / 2, 0);
        }
        Holo.banner(l, tag, p.position().add(0, 3, 0), banner("◀◀ REWIND", 0xFFFFFF), 2, 40);
        // 40틱 동안 길을 거꾸로 달림
        int steps = 36;
        for (int k = 0; k <= steps; k++) {
            final int kk = k;
            later(l, k + 1, () -> {
                if (!p.isAlive()) return;
                double u = kk / (double) steps * (path.size() - 1);
                int i = (int) Math.floor(u);
                Snap a = path.get(Math.min(i, path.size() - 1)), b = path.get(Math.min(i + 1, path.size() - 1));
                double f = u - i;
                Vec3 q = a.pos.lerp(b.pos, f);
                p.teleportTo(l, q.x, q.y, q.z, java.util.Set.of(), a.yaw, a.pitch, false);
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0;
                if (kk % 4 == 0) l.playSound(null, q.x, q.y, q.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8f, 2f - kk * 0.03f);
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, q.y + 1, q.z, 6, 0.3, 0.6, 0.3, 0.02);
            });
        }
        // 몹도 끌려감
        for (Map.Entry<UUID, ArrayDeque<Vec3>> en : r.mobs.entrySet()) {
            Entity e = l.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || en.getValue().isEmpty() || le.position().distanceTo(p.position()) > 48) continue;
            Vec3 to = en.getValue().peekFirst();
            later(l, 20, () -> {
                if (!le.isAlive()) return;
                Holo.beamGlow(l, tag, le.position().add(0, 1, 0), to.add(0, 1, 0), 0.08, 0x55DDFF, 1, 2, 4);
                le.teleportTo(to.x, to.y, to.z);
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 3));
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 100, 0));
                Holo.banner(l, tag, to.add(0, le.getBbHeight() + 0.8, 0), banner("시간 멀미", 0x55DDFF), 0.7, 20);
            });
        }
        // 투사체 지우기
        for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(24), x -> x.getOwner() != p)) {
            Holo.flare(l, tag, pr.position(), 0.8, 0x55DDFF, 4);
            pr.discard();
        }
        // 끝: 체력·배고픔 복원 + 블록 복원
        later(l, steps + 2, () -> {
            if (!p.isAlive()) return;
            if (old.hp > p.getHealth()) p.setHealth(Math.min(p.getMaxHealth(), old.hp));
            if (old.food > p.getFoodData().getFoodLevel()) p.getFoodData().setFoodLevel(old.food);
            p.clearFire();
            Holo.flare(l, tag, p.position().add(0, 1, 0), 4, 0x55DDFF, 8);
            Holo.shock(l, tag, p.position(), 12, 0x55DDFF, 10);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2f, 0.6f);
            restoreBlocks(l, r, tag);
            p.sendSystemMessage(Component.literal("§b<Ctrl+Z> §f실행 취소 완료 §7— 체력 " + String.format(Locale.ROOT, "%.0f", p.getHealth()) + " · 블록 복원"));
        });
    }

    /** 가장 오래된 사진과 지금을 비교 → 사라진 블록이 날아와 다시 박힘. */
    static void restoreBlocks(ServerLevel l, Rec r, String tag) {
        if (r.blocks.isEmpty()) return;
        Blocks10 oldest = r.blocks.peekFirst();
        List<Map.Entry<Long, BlockState>> gone = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, BlockState> en : oldest.states.entrySet()) {
            mp.set(BlockPos.of(en.getKey()));
            BlockState now = l.getBlockState(mp);
            if ((now.isAir() || !now.getFluidState().isEmpty()) && !now.equals(en.getValue())) gone.add(en);
        }
        java.util.Collections.shuffle(gone);
        int n = Math.min(400, gone.size());
        for (int i = 0; i < n; i++) {
            Map.Entry<Long, BlockState> en = gone.get(i);
            BlockPos bp = BlockPos.of(en.getKey());
            Vec3 c = Vec3.atCenterOf(bp);
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(en.getValue().getBlock()).toString();
            Vec3 from = new Vec3((Math.random() - 0.5) * 16, 6 + Math.random() * 8, (Math.random() - 0.5) * 16);
            int d = i / 20;
            Holo.block(l, tag, c, id, -1, Holo.m(Holo.rotY(Math.random() * 6), 0.9, 0.9, 0.9, from, true), d + 12, 0)
                    .key(d + 1, Holo.m(Holo.I(), 1.0, 1.0, 1.0, Vec3.ZERO, true), 9);
            later(l, d + 10, () -> { if (l.getBlockState(bp).isAir() || !l.getBlockState(bp).getFluidState().isEmpty()) l.setBlock(bp, en.getValue(), 3); });
        }
        if (n > 0) later(l, 10, () -> l.playSound(null, oldest.center.getX(), oldest.center.getY(), oldest.center.getZ(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 2f, 0.6f));
        r.blocks.clear();
    }

    /** Ctrl+Y: 지난 공격을 잔상과 함께 그대로 한 번 더. */
    static void redo(ServerPlayer p, ServerLevel l, Rec r) {
        List<Hit> hits = new ArrayList<>(r.hits);
        String tag = "jjk.ctrly" + (Holo.uid++);
        JjkNet.toPlayer(p, new Payloads.CutIn("Ctrl+Y", "다시 실행 — 공격 " + hits.size() + "회 재생", 0xFFD84A, 1));
        Holo.banner(l, tag, p.position().add(0, 3, 0), banner("▶▶ REPLAY", 0xFFD84A), 2, 40);
        Holo.sfx(l, p.position(), "riser", 2f, 1.4f);
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            later(l, 4 + i * 2, () -> {
                Entity e = l.getEntity(h.target);
                if (!(e instanceof LivingEntity le) || !le.isAlive() || le.position().distanceTo(p.position()) > 64) return;
                Vec3 c = le.position().add(0, le.getBbHeight() / 2, 0);
                Vec3 ghost = c.add(p.position().subtract(c).normalize().scale(1.6));
                Holo.block(l, tag, ghost, "minecraft:yellow_stained_glass", 0xFFD84A, Holo.m(Holo.I(), 0.6, 1.8, 0.4, new Vec3(0, -0.4, 0), true), 10, 0);
                Holo.beamGlow(l, tag, ghost, c, 0.15, 0xFFD84A, 1, 1, 3);
                Holo.flare(l, tag, c, 1.6, 0xFFD84A, 5);
                replaying = true;
                try { le.invulnerableTime = 0; le.hurtServer(l, l.damageSources().playerAttack(p), h.dmg * 1.5f); } finally { replaying = false; }
                l.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 1.3f);
            });
        }
        r.hits.clear();
    }
}
