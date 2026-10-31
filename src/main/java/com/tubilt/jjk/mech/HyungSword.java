package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 「어 형이야~」 검 — 코드 8 + 개발자 검.
 *  패시브 「형 왔다」  : 들고 있으면 힘 V·신속 II·저항 II, 반경 10 적은 쫄아서 구속·나약함
 *  타격 「어 형이야~」: 타격마다 대사 + 번개 + 주변 적 3마리에게 연쇄 (각 5000) + 하늘로 띄움
 *  우클릭 「형 간다」 (5초): 바라보는 쪽 16칸 순간 돌진, 길 위 모든 적 10만 피해 + 참격 궤적
 *  웅크리고 우클릭 「형 불렀냐」 (40초): 하늘에 내 얼굴(머리) 거대하게 등장 → "어 형이야~" → 반경 14 내리찍기 100만
 *  처치하면 채팅에 "어 형이야~"
 */
public final class HyungSword {
    private HyungSword() {}

    static final Map<UUID, long[]> CD = new HashMap<>();   // {돌진, 형 불렀냐}
    static final List<Object[]> LATER = new ArrayList<>();
    static final String LINE = "어 형이야~";

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.HYUNG_SWORD.get()); }
    static boolean holding(ServerPlayer p) { return is(p.getMainHandItem()); }

    static void later(ServerLevel l, int dt, Runnable r) { LATER.add(new Object[]{l.getGameTime() + dt, r}); }
    static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }
    static void hurt(ServerLevel l, ServerPlayer p, LivingEntity e, float dmg) {
        if (e == null || e == p || !e.isAlive()) return;
        e.invulnerableTime = 0;
        internal = true;   // 연쇄 피해가 다시 「타격」으로 잡혀 무한 연쇄되지 않게
        try { e.hurtServer(l, l.damageSources().playerAttack(p), dmg); } finally { internal = false; }
    }
    static boolean internal = false;
    static List<LivingEntity> foes(ServerLevel l, ServerPlayer p, Vec3 c, double r) { return Koming.foes(l, p, c, r); }

    /** 우클릭. */
    public static void use(ServerPlayer p, ServerLevel l) {
        long now = l.getGameTime();
        long[] cd = CD.computeIfAbsent(p.getUUID(), k -> new long[2]);
        if (p.isShiftKeyDown()) {
            if (now < cd[1]) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7형은 바빠… %.1f초", (cd[1] - now) / 20.0)), true); return; }
            cd[1] = now + 800;
            called(p, l);
        } else {
            if (now < cd[0]) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7숨 고르는 중 %.1f초", (cd[0] - now) / 20.0)), true); return; }
            cd[0] = now + 100;
            dash(p, l);
        }
    }

    /** 「형 간다」: 순간 돌진 + 경로 위 전부 베기. */
    static void dash(ServerPlayer p, ServerLevel l) {
        Vec3 from = p.position(), dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 to = from;
        for (int i = 1; i <= 16; i++) {   // 벽 앞에서 멈춤
            Vec3 q = from.add(dir.scale(i));
            if (!l.noCollision(p, p.getBoundingBox().move(q.subtract(from)))) break;
            to = q;
        }
        Vec3 a = from.add(0, 1, 0), b = to.add(0, 1, 0);
        Vec3 seg = b.subtract(a);
        for (LivingEntity e : foes(l, p, a.add(seg.scale(0.5)), seg.length() / 2 + 3)) {
            Vec3 q = e.position().add(0, e.getBbHeight() / 2, 0);
            double s = seg.lengthSqr() < 1e-6 ? 0 : Math.max(0, Math.min(1, q.subtract(a).dot(seg) / seg.lengthSqr()));
            if (q.distanceTo(a.add(seg.scale(s))) < 2.8) {
                hurt(l, p, e, 100000f);
                Holo.flare(l, "jjk.hyung", q, 2, 0xFFD84A, 6);
            }
        }
        p.teleportTo(to.x, to.y, to.z);
        p.setDeltaMovement(dir.scale(0.6));
        p.hurtMarked = true;
        Holo.beamGlow(l, "jjk.hyung", a, b, 0.9, 0xFFD84A, 1, 3, 6);
        Holo.beamGlow(l, "jjk.hyung", a, b, 0.25, 0xFFFFFF, 1, 3, 6);
        Holo.banner(l, "jjk.hyung", b.add(0, 2, 0), banner("형 간다", 0xFFD84A), 1.6, 24);
        l.playSound(null, b.x, b.y, b.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2f, 0.6f);
        Holo.sfx(l, b, "slash", 2f, 1f);
    }

    /** 「형 불렀냐」: 하늘에 내 머리가 거대하게 → 내리찍기. */
    static void called(ServerPlayer p, ServerLevel l) {
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        String name = p.getName().getString();
        String tag = "jjk.hyunghead" + (Holo.uid++);
        MechManager.cmd(l.getServer(), l, String.format(Locale.ROOT,
                "summon item_display %.2f %.2f %.2f {Tags:[\"%s\"],item:{id:\"minecraft:player_head\",count:1,components:{\"minecraft:profile\":\"%s\"}},billboard:\"vertical\",brightness:{sky:15,block:15},"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.1f,0.1f,0.1f]}}", c.x, c.y + 18, c.z, tag, name));
        later(l, 1, () -> MechManager.cmd(l.getServer(), l, "data merge entity @e[tag=" + tag + ",limit=1] {start_interpolation:0,interpolation_duration:12,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[14f,14f,14f]}}"));
        Holo.banner(l, "jjk.hyung", c.add(0, 30, 0), banner("📞 " + name + " 불렀냐?", 0xFFFFFF), 3, 30);
        later(l, 24, () -> {
            Holo.banner(l, "jjk.hyung", c.add(0, 30, 0), banner(LINE, 0xFFD84A), 6, 40);
            for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(c) < 64) {
                JjkNet.toPlayer(sp, new Payloads.CutIn(LINE, "형 등장", 0xFFD84A, 1));
                sp.sendSystemMessage(Component.literal("§6§l<형> §f" + LINE));
            }
            l.playSound(null, c.x, c.y + 10, c.z, SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 3f, 0.5f);
            MechManager.cmd(l.getServer(), l, "data merge entity @e[tag=" + tag + ",limit=1] {start_interpolation:0,interpolation_duration:10,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,-16f,0f],scale:[14f,14f,14f]}}");
        });
        later(l, 36, () -> {
            for (LivingEntity e : foes(l, p, c, 14)) { hurt(l, p, e, 1000000f); e.setDeltaMovement(0, 1.5, 0); e.hurtMarked = true; }
            Holo.shock(l, "jjk.hyung", c, 30, 0xFFD84A, 14);
            Holo.explosion(l, "jjk.hyung", c, 10, 0xFFD84A);
            Holo.sfx(l, c, "boom", 3f, 0.6f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3f, 0.5f);
            Titan.shake(l, c, 60, true);
        });
        later(l, 60, () -> MechManager.cmd(l.getServer(), l, "kill @e[tag=" + tag + "]"));
        Holo.sfx(l, c, "riser", 2.5f, 1f);
    }

    /** 타격: 대사 + 번개 + 연쇄. */
    public static void onHit(ServerPlayer p, LivingEntity target) {
        if (internal || !(p.level() instanceof ServerLevel l)) return;
        Vec3 c = target.position().add(0, target.getBbHeight() / 2, 0);
        Holo.banner(l, "jjk.hyung", c.add(0, target.getBbHeight() / 2 + 1.2, 0), banner(LINE, 0xFFD84A), 1.3, 24);
        Holo.lightning(l, "jjk.hyung", c.add(0, 14, 0), c, 0xFFD84A, 0.3, 6);
        Holo.flare(l, "jjk.hyung", c, 3, 0xFFD84A, 6);
        target.setDeltaMovement(target.getDeltaMovement().add(0, 1.0, 0));
        target.hurtMarked = true;
        int n = 0;
        for (LivingEntity e : foes(l, p, c, 10)) {
            if (e == target || n >= 3) continue;
            Holo.lightning(l, "jjk.hyung", c, e.position().add(0, e.getBbHeight() / 2, 0), 0xFFD84A, 0.15, 5);
            final LivingEntity ee = e;
            later(l, 2 + n * 2, () -> hurt(l, p, ee, 5000f));
            n++;
        }
        l.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, 1.2f);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** 처치: 채팅에 "어 형이야~". */
    public static void onKill(ServerPlayer p, LivingEntity dead) {
        p.sendSystemMessage(Component.literal("§6§l<형> §f" + LINE + " §7(" + dead.getName().getString() + " 처리 완료)"));
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (int i = 0; i < LATER.size(); i++) {
            Object[] o = LATER.get(i);
            if ((long) o[0] <= now) { LATER.remove(i--); try { ((Runnable) o[1]).run(); } catch (Exception ignored) { } }
        }
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!holding(p) || !(p.level() instanceof ServerLevel l)) continue;
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 4, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 1, false, false));
            for (LivingEntity e : foes(l, p, p.position(), 10)) {   // 형 앞에서 쫄음
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 2));
            }
            l.sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.6, 0.4, 0.1);
        }
    }
}
