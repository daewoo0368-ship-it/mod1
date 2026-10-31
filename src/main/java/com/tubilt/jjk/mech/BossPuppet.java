package com.tubilt.jjk.mech;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 보스가 "원래 있는" 플레이어용 술식·아이템(코밍 술식, 개발자 핸드폰·레버·어 형이야~ 등)을 쓰게 해 주는 꼭두각시.
 *  월드에 추가되지 않는 가짜 ServerPlayer(연결 없음)를 보스 몸 위치·시선에 맞춰 두고, 그 꼭두각시를 시전자로 원래 코드를 그대로 호출한다.
 *  꼭두각시·데이터팩 보스 술식이 플레이어에게 주는 피해는 한 방 CAP 으로 제한 (원래 술식은 10만 피해 같은 즉사기라서).
 */
public final class BossPuppet {
    private BossPuppet() {}

    static final float CAP = 20f;
    static final Map<String, ServerPlayer> BY_NAME = new HashMap<>();
    static final Set<UUID> IDS = new HashSet<>();
    /** 코밍 술식 틱을 받아야 하는 꼭두각시 (Koming.tick 이 같이 돌림). */
    static final List<ServerPlayer> KOMING = new ArrayList<>();

    /** 연결(connection) 없는 꼭두각시 플레이어: 패킷을 보내는 메서드는 전부 무시. 월드엔 추가하지 않음. */
    static final class Puppet extends ServerPlayer {
        Puppet(ServerLevel l, com.mojang.authlib.GameProfile gp) { super(l.getServer(), l, gp, net.minecraft.server.level.ClientInformation.createDefault()); }
        @Override public void displayClientMessage(net.minecraft.network.chat.Component c, boolean bar) { }
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component c) { }
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component c, boolean bar) { }
        @Override public void onUpdateAbilities() { }
        @Override public void teleportTo(double x, double y, double z) { setPos(x, y, z); }
        // 연결이 없어서 패킷을 보내는 동작은 전부 막음 (효과 부여·해제·피격·팔 휘두르기 → 원래 코드에서 크래시 났던 곳)
        @Override public boolean addEffect(net.minecraft.world.effect.MobEffectInstance e, net.minecraft.world.entity.Entity src) { return false; }
        @Override public boolean removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e) { return false; }
        @Override public boolean hurtServer(ServerLevel lv, net.minecraft.world.damagesource.DamageSource src, float amt) { return false; }
        @Override public void swing(net.minecraft.world.InteractionHand h, boolean self) { }
    }

    static ServerPlayer get(ServerLevel l, String name) {
        ServerPlayer fp = BY_NAME.get(name);
        if (fp == null || fp.level() != l) {
            UUID id = UUID.nameUUIDFromBytes(("jjk.boss." + name).getBytes(StandardCharsets.UTF_8));
            fp = new Puppet(l, new com.mojang.authlib.GameProfile(id, name));
            BY_NAME.put(name, fp);
            IDS.add(fp.getUUID());
        }
        return fp;
    }

    /** 꼭두각시를 보스 몸에 겹치고 대상 쪽을 보게 함. */
    static void aim(ServerPlayer fp, Mob body, Entity tgt) {
        Vec3 at = body.position();
        fp.setPos(at.x, at.y, at.z);
        Vec3 eye = fp.getEyePosition(), to = tgt == null ? eye.add(body.getLookAngle()) : tgt.getEyePosition();
        Vec3 d = to.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        fp.setYRot(yaw); fp.setYHeadRot(yaw); fp.setYBodyRot(yaw); fp.setXRot(pitch);
        fp.yRotO = yaw; fp.xRotO = pitch;
    }

    public static boolean isPuppet(Entity e) { return e != null && IDS.contains(e.getUUID()); }

    static boolean internal = false;
    /**
     * 피해 훅. true = 원래 피해 취소.
     *  · 꼭두각시가 보스 몸/다른 꼭두각시를 때림 → 무효
     *  · 꼭두각시가 플레이어를 때림 → 최대 CAP
     *  · 데이터팩 보스(고죠·스쿠나) 근처에서 시전자 없는 마법 피해 → 최대 CAP
     */
    public static boolean capHit(LivingEntity le, DamageSource src, float amt) {
        if (internal) return false;
        Entity by = src.getEntity();
        boolean fromPuppet = isPuppet(by);
        if (fromPuppet && (le.getTags().contains("jjk.boss") || isPuppet(le))) return true;
        if (!(le instanceof ServerPlayer p) || isPuppet(le) || amt <= CAP) return false;
        boolean fromDpBoss = by == null && !src.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) && nearBoss(p);   // 무려끼 소거(공허 피해) 같은 즉사기도 포함
        if (!fromPuppet && !fromDpBoss) return false;
        if (le.level() instanceof ServerLevel sl) {
            internal = true;
            try { le.invulnerableTime = 0; le.hurtServer(sl, src, CAP); } finally { internal = false; }
        }
        return true;
    }
    static boolean nearBoss(ServerPlayer p) {
        for (Bosses.Boss b : Bosses.ACTIVE)
            if (b.vhp && b.body != null && b.body.level() == p.level() && b.body.distanceTo(p) < 80) return true;
        return false;
    }

    /** 코밍 꼭두각시 준비: 급식안 소지 + 루카력 가득 + 페널티·쿨타임 초기화. */
    static ServerPlayer koming(ServerLevel l) {
        ServerPlayer fp = get(l, "코밍");
        if (!fp.getInventory().getItem(9).is(com.tubilt.jjk.JjkItems.KOMING_EYE.get()))
            fp.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(com.tubilt.jjk.JjkItems.KOMING_EYE.get()));
        if (!KOMING.contains(fp)) KOMING.add(fp);
        Koming.setScore(fp, "gojo.ce", 999999);
        Koming.setScore(fp, "koming.tlock", 0);
        Koming.setScore(fp, "koming.dlock", 0);
        Koming.State s = Koming.st(fp);
        java.util.Arrays.fill(s.cd, 0);
        return fp;
    }
    /** 코밍 꼭두각시 술식 전부 정리. */
    static void releaseKoming(ServerLevel l) {
        ServerPlayer fp = BY_NAME.get("코밍");
        if (fp == null) return;
        Koming.State s = Koming.STATES.get(fp.getUUID());
        if (s != null) {
            for (Koming.Fx f : new ArrayList<>(s.fx)) try { f.abort(l, fp, s); } catch (Throwable ignored) { }
            s.fx.clear();
            Holo.killOwner(s.tag);
            Holo.killOwner(s.tag + "d");
        }
        Koming.STATES.remove(fp.getUUID());
        KOMING.remove(fp);
    }
    static void stopAll() { KOMING.clear(); BY_NAME.clear(); }
}
