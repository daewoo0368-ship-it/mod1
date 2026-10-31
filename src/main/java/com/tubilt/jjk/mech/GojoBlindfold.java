package com.tubilt.jjk.mech;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 고죠의 안대 (머리에 씀).
 *  · 눈을 가림: 육안·급식안의 생명체 투시가 꺼짐 (야간 투시는 유지)
 *  · 무하한: 몸 3칸 안으로 날아오는 투사체가 멈췄다가 사라짐, 근접 피해 절반
 */
public final class GojoBlindfold {
    private GojoBlindfold() {}
    static final Map<Projectile, Integer> FROZEN = new HashMap<>();
    static boolean internal = false;

    static boolean worn(LivingEntity p) { return p.getItemBySlot(EquipmentSlot.HEAD).is(com.tubilt.jjk.JjkItems.GOJO_BLINDFOLD.get()); }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean w = worn(p);
            boolean had = p.getTags().contains("jjk.blindfold");
            if (w && !had) { p.addTag("jjk.blindfold"); p.displayClientMessage(Component.literal("§7안대 착용 — 육안·급식안 투시 봉인 §8· §b무하한 §f전개"), true); }
            if (!w && had) p.removeTag("jjk.blindfold");
            if (!w || !(p.level() instanceof ServerLevel l)) continue;
            if (now % 20 == 0) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
            for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3), x -> x.getOwner() != p && x.isAlive())) {
                if (!FROZEN.containsKey(pr)) {
                    FROZEN.put(pr, 0);
                    Holo.banner(l, "jjk.blindfold", pr.position().add(0, 0.5, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("∞", 0xBFE6FF, true) + "]}", 0.8, 16);
                }
                pr.setDeltaMovement(Vec3.ZERO);
                pr.setNoGravity(true);
            }
        }
        FROZEN.entrySet().removeIf(en -> {
            Projectile pr = en.getKey();
            if (!pr.isAlive()) return true;
            int a = en.getValue() + 1;
            en.setValue(a);
            pr.setDeltaMovement(Vec3.ZERO);
            if (a >= 20) { pr.discard(); return true; }
            return false;
        });
    }

    /** 피해 훅: 근접 피해 절반 (true = 원래 피해 취소). */
    public static boolean infinity(LivingEntity le, DamageSource src, float amt) {
        if (internal || !worn(le) || !(le.level() instanceof ServerLevel l)) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.getEntity() == null || src.getDirectEntity() instanceof Projectile) return false;
        internal = true;
        try { le.hurtServer(l, src, amt * 0.5f); } finally { internal = false; }
        return true;
    }
    static void stopAll() { FROZEN.clear(); }
}
