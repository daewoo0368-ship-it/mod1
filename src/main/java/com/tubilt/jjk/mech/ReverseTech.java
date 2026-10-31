package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
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
 * 반전술식 — 마이너스 × 마이너스 = 플러스의 주력.
 *  heal 치유 : 체력 회복 + 재생 + 나쁜 효과 제거
 *  out  출력 : 정(正)의 에너지를 광선으로 방출 (관통)
 */
public final class ReverseTech {
    private ReverseTech() {}

    public static final String[] IDS = {"heal", "out"};
    static final String[] NAMES = {"반전술식 「치유」", "반전술식 「출력」"};
    static final int[] COST = {120, 260};
    static final int[] CD = {80, 240};
    static final int GOLD = 0xFFE9A0;
    static final Map<UUID, int[]> CDS = new HashMap<>();

    static boolean isItem(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().startsWith("reverse_");
    }
    public static boolean onUse(Player ent, ItemStack st) {
        if (!isItem(st)) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        cast(p, BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().substring("reverse_".length()));
        return true;
    }

    public static void cast(ServerPlayer p, String id) {
        int idx = Arrays.asList(IDS).indexOf(id);
        if (idx < 0) return;
        if (p.getTags().contains("jjk.hunsu")) { Koming.bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        int[] cd = CDS.computeIfAbsent(p.getUUID(), k -> new int[IDS.length]);
        if (cd[idx] > 0) { Koming.bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", NAMES[idx], cd[idx] / 20f)); return; }
        if (idx == 0 && p.getHealth() >= p.getMaxHealth() && p.getActiveEffects().isEmpty()) { Koming.bar(p, "§7회복할 곳이 없다"); return; }
        if (!Koming.spend(p, COST[idx])) { Koming.bar(p, "§c주력이 부족하다 §7(필요 " + COST[idx] + ")"); return; }
        cd[idx] = CD[idx];
        ServerLevel l = (ServerLevel) p.level();
        String tag = "jjk.rct" + Integer.toHexString(p.getUUID().hashCode() & 0xFFFFFF);
        Vec3 c = p.position().add(0, 1, 0);
        if (idx == 0) {
            p.heal(10f);
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
            p.removeEffect(MobEffects.POISON); p.removeEffect(MobEffects.WITHER); p.removeEffect(MobEffects.SLOWNESS);
            p.removeEffect(MobEffects.WEAKNESS); p.removeEffect(MobEffects.BLINDNESS); p.removeEffect(MobEffects.NAUSEA);
            p.removeEffect(MobEffects.MINING_FATIGUE); p.removeEffect(MobEffects.HUNGER);
            l.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 16, 0.4, 0.7, 0.4, 0.1);
            Holo.flare(l, tag, c, 5, GOLD, 8);
            Holo.pillar(l, tag, p.position(), 5, 1.4, GOLD, 4, 4, 8);
            Holo.sfx(l, c, "magic", 1.1f, 1.6f);
            Koming.bar(p, "§e반전술식 §f— 마이너스 × 마이너스");
        } else {
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            double range = 32;
            BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation();
            Vec3 start = eye.add(look.scale(0.8)).add(0, -0.2, 0);
            Holo.beam(l, tag, start, end, 0.8, GOLD, 1, 6, 8);
            Holo.flare(l, tag, end, 7, 0xFFFFFF, 8);
            Holo.shock(l, tag, end, 3, GOLD, 8);
            Holo.sfx(l, start, "laser", 1.4f, 1.0f);
            Set<LivingEntity> done = new HashSet<>();
            Vec3 d = end.subtract(start);
            int steps = (int) Math.ceil(d.length() / 1.5);
            for (int i = 0; i <= steps; i++) {
                Vec3 q = start.add(d.scale(i / (double) Math.max(1, steps)));
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(1.3), x -> Koming.foe(x, p)))
                    if (done.add(e)) { Projection.hurt(l, p, e, 26f); Koming.push(e, look.scale(0.9).add(0, 0.3, 0)); }
            }
            Koming.bar(p, "§e반전술식 「출력」");
        }
    }

    public static void tick(MinecraftServer server) {
        if (CDS.isEmpty()) return;
        for (var en : CDS.entrySet()) for (int i = 0; i < en.getValue().length; i++) if (en.getValue()[i] > 0) en.getValue()[i]--;
    }
    public static void stopAll() { CDS.clear(); }
}
