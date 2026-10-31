package com.tubilt.jjk.mech;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 개발자의 가위 · 보자기 · 주먹 — 웅크리고 우클릭: 어깨 위에 거대한 손 소환/해제, 우클릭: 공격.
 *  가위: 손가락 두 개가 강철 날인 V자 손 → 바라보는 곳으로 날아가 「싹둑」 두 번 (150 ×2)
 *  보자기: 활짝 편 손바닥 → 대상 위로 올라가 내려덮음 (반경 6, 120 + 보자기에 싸여 둔화·실명)
 *  주먹: 꽉 쥔 주먹 → 뒤로 당겼다 로켓 펀치 (폭발 반경 5, 200 + 크게 날림)
 * 손 = 아이템 디스플레이 부품(손바닥·손가락·주먹)을 관절째 움직이는 인형.
 */
public final class DevHand {
    private DevHand() {}

    static final int SCISSORS = 0, PAPER = 1, FIST = 2;
    static final String[] NAME = {"개발자의 가위", "개발자의 보자기", "개발자의 주먹"};
    static final int[] COLOR = {0xC8D2E6, 0x55DDFF, 0xFF6040};
    static final Map<UUID, Hand> HANDS = new HashMap<>();

    public static int kind(ItemStack s) {
        if (s.is(com.tubilt.jjk.JjkItems.DEV_SCISSORS.get())) return SCISSORS;
        if (s.is(com.tubilt.jjk.JjkItems.DEV_CLOTH.get())) return PAPER;
        if (s.is(com.tubilt.jjk.JjkItems.DEV_FIST.get())) return FIST;
        return -1;
    }

    public static void use(ServerPlayer p, ServerLevel l, int k) {
        Hand h = HANDS.get(p.getUUID());
        if (p.isShiftKeyDown()) {
            if (h != null) { h.leave(); HANDS.remove(p.getUUID()); if (h.kind == k) { p.displayClientMessage(Component.literal("§7" + NAME[k] + " — 손을 거뒀다"), true); return; } }
            HANDS.put(p.getUUID(), new Hand(p, l, k));
            p.displayClientMessage(Component.literal("§b" + NAME[k] + " §f소환 — 우클릭: 공격 · 웅크리고 우클릭: 해제"), true);
            return;
        }
        if (h == null || h.kind != k || h.l != l) { p.displayClientMessage(Component.literal("§7웅크리고 우클릭으로 먼저 손을 꺼내세요"), true); return; }
        h.attack();
    }

    static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Hand>> it = HANDS.entrySet().iterator(); it.hasNext(); ) {
            Hand h = it.next().getValue();
            boolean done;
            try { done = h.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 개발자의 손 오류", e); done = true; }
            if (done) { h.leave(); it.remove(); }
        }
    }
    static void stopAll() { for (Hand h : HANDS.values()) h.leave(); HANDS.clear(); }

    static double lerp(double a, double b, double t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }
    static double ease(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    static final class Hand {
        static final double HS = 3.4;   // 손 크기 (모델 1px = 1/16블록 × HS)
        static final int L = 99999999;
        final ServerPlayer owner; final ServerLevel l; final int kind; final String tag = "jjk.devhand" + (Holo.uid++);
        Holo.Elem palm, fist; final Holo.Elem[] fingers = new Holo.Elem[4];
        Vec3 pos; double yaw, pitch = 0;
        int t = 0, at = -1, cd = 0;
        Vec3 from, target, dir;

        Hand(ServerPlayer p, ServerLevel l, int kind) {
            this.owner = p; this.l = l; this.kind = kind;
            pos = idle();
            yaw = p.getYRot();
            float[] z = Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false);
            if (kind == FIST) fist = part("fist", z);
            else {
                palm = part("palm", z);
                for (int i = 0; i < 4; i++) {
                    String m = kind == PAPER ? "finger" : i < 2 ? "blade" : "finger_curl";
                    fingers[i] = part(m, z);
                }
            }
            Holo.flare(l, tag + "x", pos, 3, COLOR[kind], 8);
            BossSfx.play(l, pos, kind == SCISSORS ? "slash" : kind == PAPER ? "whoosh" : "impact", 1.5f, 1.1f);
        }
        Holo.Elem part(String m, float[] z) { return Holo.model(l, tag, pos, "jjk:dev_hand/" + m, z, L, 2, 0f).attach(() -> pos, () -> yaw); }

        Vec3 idle() {
            double yr = Math.toRadians(owner.getYRot());
            Vec3 f = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
            return owner.getEyePosition().add(right.scale(2.2)).add(0, 0.9 + Math.sin(t * 0.08) * 0.15, 0).subtract(f.scale(0.4));
        }
        /** 바라보는 곳: 맞은 생명체 > 블록 > 24칸 앞. */
        Vec3 aim() {
            Vec3 eye = owner.getEyePosition(), look = owner.getLookAngle(), end = eye.add(look.scale(24));
            var bh = l.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            LivingEntity best = null; double bd = 1e9;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(2), x -> Koming.foe(x, owner))) {
                Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0), ab = stop.subtract(eye);
                double s = Math.max(0, Math.min(1, c.subtract(eye).dot(ab) / ab.lengthSqr()));
                double d = c.distanceTo(eye.add(ab.scale(s)));
                if (d < 2.2 && s < bd) { bd = s; best = e; }
            }
            return best != null ? best.position().add(0, best.getBbHeight() / 2, 0) : stop;
        }

        void attack() {
            if (at >= 0 || cd > 0) return;
            target = aim();
            from = pos;
            dir = target.subtract(owner.getEyePosition()).normalize();
            at = 0;
            BossSfx.play(l, pos, "whoosh", 1.4f, kind == FIST ? 0.7f : 1.1f);
            if (kind == PAPER) Holo.circle(l, tag + "x", new Vec3(target.x, target.y - 0.4, target.z), 6, COLOR[kind], 18, 0);
        }

        boolean tick() {
            t++;
            if (!owner.isAlive() || owner.isRemoved() || owner.level() != l) return true;
            if (cd > 0) cd--;
            double spread = 0, curl = 0, rollFan = 0;
            Vec3 want = idle();
            double wantYaw = owner.getYRot(), wantPitch = 0;
            if (at >= 0) {
                at++;
                double ty = Math.toDegrees(Math.atan2(-dir.x, dir.z));
                wantYaw = ty;
                switch (kind) {
                    case SCISSORS -> {
                        Vec3 hit = target.subtract(dir.scale(2.2));
                        if (at <= 8) { want = from.lerp(hit, ease(at / 8.0)); spread = 0.65; }
                        else if (at <= 20) { want = hit; int k = (at - 9) % 6; spread = k < 3 ? lerp(0.65, -0.06, k / 2.0) : lerp(-0.06, 0.65, (k - 3) / 2.0); if (at == 11 || at == 17) snip(); }
                        else { want = hit.lerp(idle(), ease((at - 20) / 8.0)); spread = 0.2; }
                        wantPitch = Math.PI / 2;   // 날이 앞을 향함
                        if (at >= 28) { at = -1; cd = 6; }
                    }
                    case PAPER -> {
                        Vec3 up = target.add(0, 8, 0);
                        if (at <= 12) { want = from.lerp(up, ease(at / 12.0)); wantPitch = lerp(0, Math.PI / 2, at / 12.0); }
                        else if (at <= 17) { want = up.lerp(target.add(0, 1.2, 0), ease((at - 12) / 5.0)); wantPitch = Math.PI / 2; if (at == 17) slap(); }
                        else if (at <= 26) { want = target.add(0, 1.2, 0); wantPitch = Math.PI / 2; }
                        else { want = target.add(0, 1.2, 0).lerp(idle(), ease((at - 26) / 10.0)); wantPitch = lerp(Math.PI / 2, 0, (at - 26) / 10.0); }
                        rollFan = 0.22;
                        if (at >= 36) { at = -1; cd = 8; }
                    }
                    default -> {   // 주먹
                        Vec3 back = from.subtract(dir.scale(2.5));
                        Vec3 hit = target.subtract(dir.scale(1.2));
                        if (at <= 8) { want = from.lerp(back, ease(at / 8.0)); }
                        else if (at <= 13) { want = back.lerp(hit, (at - 8) / 5.0); if (at == 13) punch(); }
                        else if (at <= 18) { want = hit; }
                        else { want = hit.lerp(idle(), ease((at - 18) / 12.0)); }
                        wantPitch = Math.PI / 2;   // 주먹이 앞으로
                        if (at >= 30) { at = -1; cd = 10; }
                    }
                }
                if (at > 0 && at % 2 == 0) l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.01);
            } else if (kind == PAPER) rollFan = 0.12;
            pos = at >= 0 ? want : pos.lerp(want, 0.35);
            yaw = yaw + net.minecraft.util.Mth.wrapDegrees((float) (wantYaw - yaw)) * 0.5;
            pitch = lerp(pitch, wantPitch, 0.5);
            pose(spread, rollFan);
            return false;
        }

        /** 손 인형 관절 → 행렬. 로컬: +Z 앞, +Y 위. */
        void pose(double spread, double fan) {
            double[][] R = Holo.rotX(pitch);
            double s = HS;
            if (kind == FIST) { fist.keyframe(Holo.m(R, s, s, s, Vec3.ZERO, false), 0); return; }
            palm.keyframe(Holo.m(R, s, s, s, Vec3.ZERO, false), 0);
            double[] xs = {-3.75, -1.25, 1.25, 3.75};
            for (int i = 0; i < 4; i++) {
                double[][] rf;
                if (kind == SCISSORS) rf = i < 2 ? Holo.rotZ(i == 0 ? spread * 0.5 : -spread * 0.5) : Holo.I();
                else rf = Holo.rotZ((i - 1.5) * -fan);
                Vec3 off = Holo.apply(R, new Vec3((kind == SCISSORS && i < 2 ? (i == 0 ? -1.4 : 1.4) : xs[i]) / 16.0 * s, 5.0 / 16.0 * s, 0));
                fingers[i].keyframe(Holo.m(Holo.mul(R, rf), s, s, s, off, false), 0);
            }
        }

        void snip() {
            Vec3 c = target;
            Holo.beamGlow(l, tag + "x", c.add(-2.5, 1.5, -2.5), c.add(2.5, -1.5, 2.5), 0.25, 0xFFFFFF, 0, 2, 5);
            Holo.beamGlow(l, tag + "x", c.add(-2.5, 1.5, 2.5), c.add(2.5, -1.5, -2.5), 0.25, 0xFFFFFF, 0, 2, 5);
            Holo.banner(l, tag + "x", c.add(0, 2.5, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("싹둑!", 0xFFFFFF, true) + "]}", 1.6, 16);
            BossSfx.play(l, c, "slash", 1.8f, 1.3f);
            l.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.PLAYERS, 2f, 0.6f);
            for (LivingEntity e : Koming.foes(l, owner, c, 3.5)) hurt(e, 150f);
        }
        void slap() {
            Vec3 c = new Vec3(target.x, target.y - 0.5, target.z);
            Holo.shock(l, tag + "x", c, 12, COLOR[PAPER], 12);
            Holo.flare(l, tag + "x", c.add(0, 1, 0), 6, 0xFFFFFF, 8);
            BossSfx.play(l, c, "boom", 2f, 1.1f);
            Titan.shake(l, c, 30, false);
            for (LivingEntity e : Koming.foes(l, owner, c, 6)) {
                hurt(e, 120f);
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                final LivingEntity ee = e;
                Holo.block(l, tag + "x", e.position(), "minecraft:white_wool", -1, Holo.m(Holo.I(), 0.1, 0.1, 0.1, new Vec3(0, ee.getBbHeight() / 2, 0), true), 60, 1)
                        .key(1, Holo.m(Holo.I(), ee.getBbWidth() + 0.6, ee.getBbHeight() + 0.4, ee.getBbWidth() + 0.6, new Vec3(0, ee.getBbHeight() / 2, 0), true), 4)
                        .follow(() -> ee.position(), Vec3.ZERO);
            }
            Holo.banner(l, tag + "x", c.add(0, 3, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("보자기에 싸였다!", COLOR[PAPER], true) + "]}", 1.6, 30);
        }
        void punch() {
            Vec3 c = target;
            Holo.explosion(l, tag + "x", c, 5, COLOR[FIST]);
            BossSfx.play(l, c, "boom", 2.5f, 0.9f);
            Titan.shake(l, c, 40, true);
            for (LivingEntity e : Koming.foes(l, owner, c, 5)) {
                hurt(e, 200f);
                Vec3 d = e.position().subtract(owner.position()).normalize();
                e.setDeltaMovement(d.scale(2.4).add(0, 0.8, 0)); e.hurtMarked = true;
            }
            Holo.banner(l, tag + "x", c.add(0, 3, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("주먹!!", COLOR[FIST], true) + "]}", 1.8, 24);
        }
        void hurt(Entity e, float dmg) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le == owner) return;
            le.invulnerableTime = 0;
            le.hurtServer(l, l.damageSources().playerAttack(owner), dmg);
        }
        void leave() {
            Holo.flare(l, tag + "x", pos, 2.5, COLOR[kind], 6);
            Holo.killOwner(tag);
        }
    }
}
