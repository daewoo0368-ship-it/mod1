package com.tubilt.jjk.mech;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 코드 갑옷 — 방어 50 · 방어 강도 80 · 밀치기 저항 100% · 부서지지 않음.
 * 풀세트 「컴파일 완료」: 재생 II · 화염 저항 · 야간 투시 · 신속 I · 낙하 피해 없음 ·
 *   25% 확률로 피해를 「예외 처리」해서 회피 (머리 위에 catch (Exception e) {} 가 뜸)
 */
public final class CodeArmor {
    private CodeArmor() {}
    static final Random R = new Random();
    static final String[] MISS = {"catch (Exception e) {}", "NullPointerException: damage", "return;", "// TODO: 피해 받기", "if (false) hurt();"};

    static boolean full(LivingEntity p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(com.tubilt.jjk.JjkItems.CODE_HELMET.get())
                && p.getItemBySlot(EquipmentSlot.CHEST).is(com.tubilt.jjk.JjkItems.CODE_CHESTPLATE.get())
                && p.getItemBySlot(EquipmentSlot.LEGS).is(com.tubilt.jjk.JjkItems.CODE_LEGGINGS.get())
                && p.getItemBySlot(EquipmentSlot.FEET).is(com.tubilt.jjk.JjkItems.CODE_BOOTS.get());
    }

    /** 피해 훅: true = 취소. */
    public static boolean dodge(LivingEntity le, DamageSource src) {
        if (!(le instanceof Player) || !full(le) || !(le.level() instanceof ServerLevel l)) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (src.is(DamageTypeTags.IS_FALL)) return true;
        if (R.nextInt(100) >= 25) return false;
        Vec3 c = le.position().add(0, le.getBbHeight() + 0.6, 0);
        Holo.banner(l, "jjk.codearmor", c, "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(MISS[R.nextInt(MISS.length)], 0x55FF55, true) + "]}", 0.8, 20);
        Holo.glow(l, "jjk.codearmor", le.position().add(0, 1, 0), 2.2, 0x55FF55, 1, 1, 4);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 1.8f);
        return true;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean f = full(p);
            boolean had = p.getTags().contains("jjk.codearmor");
            if (f && !had) { p.addTag("jjk.codearmor"); p.displayClientMessage(Component.literal("§a> javac CodeArmor.java  §f— 컴파일 완료 (풀세트 효과)"), true); }
            if (!f && had) p.removeTag("jjk.codearmor");
            if (!f) continue;
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false));
            if (p.level() instanceof ServerLevel l)
                l.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.8, 0.4, 0);
        }
    }
}
