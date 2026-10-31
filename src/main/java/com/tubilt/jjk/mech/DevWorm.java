package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 개발자의 귀엽지만은 않은 탑승물 — 몸길이 86블록 중장갑 지렁이 로봇 「굴착기 MK-0」.
 *  우클릭: 소환 + 머리에 탑승 / 탑승 중 우클릭: 드릴 돌진(바라보는 쪽 30칸, 경로 150) / 웅크리고 우클릭: 내리기·돌려보내기
 *  조종: 바라보는 방향으로 머리가 향함 · 앞(W) 가속 · 뒤(S) 감속 · 달리기 = 부스트 · 점프 = 위로 · 웅크리기 = 아래로 (땅속 OK)
 *  자동 무장: 몸통 회전 톱날(닿는 적 10/0.5초) · 등 포탑 6기(반경 24 적에게 레이저 25)
 *  땅 파기(griefing 켜짐일 때만): 머리가 지나가는 블록을 갈아버림
 */
public final class DevWorm {
    private DevWorm() {}

    static final int N = 36;
    static final double SPACING = 2.4;
    static final Map<UUID, Worm> WORMS = new HashMap<>();

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DEV_WORM.get()); }

    public static void use(ServerPlayer p, ServerLevel l) {
        Worm w = WORMS.get(p.getUUID());
        if (p.isShiftKeyDown()) {
            if (w == null) return;
            if (w.ride) { w.ride = false; p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0)); p.displayClientMessage(Component.literal("§7굴착기에서 내렸다 · 다시 웅크리고 우클릭하면 돌려보냄"), true); }
            else { w.leave(); WORMS.remove(p.getUUID()); }
            return;
        }
        if (w == null || w.l != l) {
            if (w != null) w.leave();
            w = new Worm(p, l);
            WORMS.put(p.getUUID(), w);
            return;
        }
        if (!w.ride) { w.ride = true; p.displayClientMessage(Component.literal("§c굴착기 MK-0 §f탑승 — W 전진 · 달리기 부스트 · 점프 위 · 웅크리기 아래 · 우클릭 드릴 돌진"), true); return; }
        w.drill();
    }

    static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Worm>> it = WORMS.entrySet().iterator(); it.hasNext(); ) {
            Worm w = it.next().getValue();
            boolean done;
            try { done = w.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 굴착기 오류", e); done = true; }
            if (done) { w.leave(); it.remove(); }
        }
    }
    static void stopAll() { for (Worm w : WORMS.values()) w.leave(); WORMS.clear(); }

    static final class Worm {
        final ServerPlayer owner; final ServerLevel l; final String tag = "jjk.devworm" + (Holo.uid++);
        final Vec3[] seg = new Vec3[N]; final float[] yaw = new float[N], pitch = new float[N];
        final List<Holo.Elem> body = new ArrayList<>();
        Vec3 vel = Vec3.ZERO; boolean ride = true; int t = 0, drillT = 0, drillCd = 0, laserCd = 0, kills = 0;
        Vec3 drillDir = Vec3.ZERO;

        Worm(ServerPlayer p, ServerLevel l) {
            owner = p; this.l = l;
            Vec3 start = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(4)).add(0, -6, 0);
            for (int i = 0; i < N; i++) seg[i] = start.add(0, -i * 0.6, 0);
            vel = new Vec3(0, 1.4, 0);
            for (int i = 0; i < N; i++) {
                final int ii = i;
                double s = i == 0 ? 4.4 : 3.8 - 2.0 * i / (N - 1.0);
                String block = i == 0 ? "minecraft:netherite_block" : i == N - 1 ? "minecraft:magma_block" : i % 6 == 3 ? "minecraft:observer" : i % 2 == 0 ? "minecraft:polished_blackstone" : "minecraft:iron_block";
                int glow = i % 6 == 3 ? 0xFF3030 : -1;
                body.add(Holo.block(l, tag, start, block, glow, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 99999999, 2)
                        .key(1 + i / 2, Holo.m(Holo.I(), s, s, s, Vec3.ZERO, true), 6).attach(() -> seg[ii], () -> yaw[ii]));
                if (i > 0 && i % 3 == 0 && i < N - 2) {   // 회전 톱날 (몸통 양옆)
                    final double ss = s;
                    for (int k = -1; k <= 1; k += 2) {
                        final int kk = k;
                        Holo.block(l, tag, start, "minecraft:iron_trapdoor", -1, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 99999999, 2)
                                .key(2 + i / 2, Holo.m(Holo.rotZ(Math.PI / 2), ss * 0.9, ss * 0.12, ss * 0.9, new Vec3(kk * ss * 0.55, 0, 0), true), 6)
                                .follow(() -> seg[ii], Vec3.ZERO).spin(35 * kk);
                    }
                }
                if (i % 6 == 3) {   // 등 포탑
                    final double ss = s;
                    Holo.block(l, tag, start, "minecraft:dispenser", 0xFF3030, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 99999999, 2)
                            .key(2 + i / 2, Holo.m(Holo.I(), ss * 0.45, ss * 0.45, ss * 0.45, new Vec3(0, ss * 0.65, 0), true), 6).attach(() -> seg[ii], () -> yaw[ii]);
                }
            }
            // 머리: 회전 드릴 (원뿔 3단) + 붉은 눈 + 턱
            double[] ds = {2.6, 1.8, 1.0};
            for (int k = 0; k < 3; k++) {
                final int kk = k;
                Holo.block(l, tag, start, k == 2 ? "minecraft:diamond_block" : "minecraft:iron_block", k == 2 ? 0x55FFFF : -1, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 99999999, 2)
                        .key(4, Holo.m(Holo.I(), ds[kk], ds[kk], 1.0, new Vec3(0, 0, 2.6 + kk * 0.95), true), 6).attach(() -> seg[0], () -> yaw[0] + t * (kk % 2 == 0 ? 25 : -25));
            }
            for (int k = -1; k <= 1; k += 2) {
                final int kk = k;
                Holo.block(l, tag, start, "minecraft:redstone_block", 0xFF2020, Holo.m(Holo.I(), 0.8, 0.5, 0.3, new Vec3(kk * 1.4, 1.2, 2.25), true), 99999999, 2).attach(() -> seg[0], () -> yaw[0]);
                Holo.block(l, tag, start, "minecraft:polished_blackstone", -1, Holo.m(Holo.I(), 0.6, 1.6, 2.8, new Vec3(kk * 2.3, -0.8, 2.0), true), 99999999, 2).attach(() -> seg[0], () -> yaw[0]).wobble(18, 0.6);
            }
            Holo.text(l, tag, start, "{text:\"\"}", "center", 0, false, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, false), 99999999, 2)
                    .follow(() -> seg[0].add(0, 4.2, 0), Vec3.ZERO)
                    .text(age -> "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("굴착기 MK-0 ", 0xFF3030, true) + "," + Holo.tx(drillT > 0 ? "[DRILL]" : ride ? "[탑승 중]" : "[대기]", 0xFFFFFF, false) + "]}");
            BossSfx.play(l, start, "deep", 3f, 0.6f);
            BossSfx.play(l, start, "boom", 2f, 0.6f);
            l.playSound(null, start.x, start.y, start.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3f, 0.4f);
            com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.CutIn("굴착기 MK-0", "개발자의 귀엽지만은 않은 탑승물", 0xFF3030, 1));
            Holo.shock(l, tag + "x", p.position(), 16, 0xFF3030, 12);
            Titan.shake(l, p.position(), 50, true);
        }

        void drill() {
            if (drillT > 0) return;
            if (drillCd > 0) { owner.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT, "§7드릴 충전 중 %.1f초", drillCd / 20.0)), true); return; }
            drillT = 22; drillCd = 80;
            drillDir = owner.getLookAngle();
            owner.displayClientMessage(Component.literal("§c§l드릴 돌진!!"), true);
            BossSfx.play(l, seg[0], "charge", 2f, 0.6f);
            l.playSound(null, seg[0].x, seg[0].y, seg[0].z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.5f, 0.7f);
        }

        boolean tick() {
            t++;
            if (!owner.isAlive() || owner.isRemoved() || owner.level() != l) return true;
            Vec3 head = seg[0];
            if (drillCd > 0) drillCd--;
            if (ride && t % 2 == 0) GuardMeta.send(owner, 12, String.format(java.util.Locale.ROOT, "%.3f|%.2f|%.1f|%s|%.2f|%.2f|%d",
                    vel.length(), drillT > 0 ? 1.9 : 1.8, seg[0].y, drillT > 0 ? "§c드릴 돌진!" : l.getBlockState(BlockPos.containing(seg[0])).isAir() ? "§b비행" : "§6굴착 중",
                    drillCd <= 0 ? 1f : 1f - drillCd / 80f, 1f - Math.max(0, laserCd) / 30f, kills));
            if (drillT > 0) {   // 드릴 돌진: 직선 가속, 경로의 적 갈아버림
                drillT--;
                vel = drillDir.scale(1.9);
                for (LivingEntity e : Koming.foes(l, owner, head, 5)) hit(e, 150f);
                if (t % 2 == 0) { BossSfx.play(l, head, "slash", 1.2f, 0.6f); l.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 20, 1.5, 1.5, 1.5, 0.3); }
            } else if (ride) {
                var in = owner.getLastClientInput();
                Vec3 dir = owner.getLookAngle();
                double acc = in.forward() ? (in.sprint() ? 0.34 : 0.2) : 0;
                vel = vel.scale(in.backward() ? 0.7 : 0.9).add(dir.scale(acc));
                if (in.jump()) vel = vel.add(0, 0.12, 0);
                if (in.shift()) vel = vel.add(0, -0.12, 0);
                double max = in.sprint() ? 1.8 : 1.1;
                if (vel.length() > max) vel = vel.normalize().scale(max);
            } else {   // 주인 곁을 맴돎
                double a = t * 0.025;
                Vec3 goal = owner.position().add(Math.cos(a) * 16, 4 + Math.sin(t * 0.05) * 6, Math.sin(a) * 16);
                Vec3 want = goal.subtract(head);
                double len = want.length();
                if (len > 1e-3) vel = vel.scale(0.93).add(want.scale(1 / len).scale(0.14));
                if (vel.length() > 0.7) vel = vel.normalize().scale(0.7);
            }
            seg[0] = head.add(vel);
            if (ride) {   // 머리가 움직인 '뒤'의 자리로 태움 (한 틱 늦게 따라가며 위아래로 떨리던 문제)
                Vec3 seat = seg[0].add(0, 3.0, 0);
                owner.teleportTo(seat.x, seat.y, seat.z);
                owner.setDeltaMovement(vel);
                owner.hurtMarked = true;
                owner.fallDistance = 0;
                if (t % 10 == 0) owner.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 3, false, false));
            }
            for (int i = 1; i < N; i++) {
                Vec3 d = seg[i - 1].subtract(seg[i]);
                double dl = d.length();
                if (dl > SPACING) seg[i] = seg[i - 1].subtract(d.scale(SPACING / dl));
            }
            for (int i = 0; i < N; i++) {
                Vec3 d = i == 0 ? (vel.lengthSqr() > 1e-4 ? vel : owner.getLookAngle()) : seg[i - 1].subtract(seg[i]);
                if (d.lengthSqr() > 1e-6) {
                    float ty = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), tp = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                    float dy = ty - yaw[i]; dy = ((dy + 540f) % 360f) - 180f;
                    yaw[i] += dy * 0.35f; pitch[i] += (tp - pitch[i]) * 0.35f;
                }
            }
            for (int i = 0; i < N && i < body.size(); i++) body.get(i).pitch0 = pitch[i];
            // 톱날: 몸통에 닿는 적
            if (t % 10 == 0) for (int i = 2; i < N; i += 3) for (LivingEntity e : Koming.foes(l, owner, seg[i], 2.8)) hit(e, 10f);
            // 포탑 레이저
            if (--laserCd <= 0) {
                laserCd = 30;
                for (int i = 3; i < N; i += 6) {
                    LivingEntity best = null; double bd = 24 * 24;
                    for (LivingEntity e : Koming.foes(l, owner, seg[i], 24)) { double d = e.distanceToSqr(seg[i]); if (d < bd) { bd = d; best = e; } }
                    if (best == null) continue;
                    Vec3 a = seg[i].add(0, 2.2, 0), b = best.position().add(0, best.getBbHeight() / 2, 0);
                    Holo.beamGlow(l, tag + "x", a, b, 0.2, 0xFF3030, 0, 2, 4);
                    BossSfx.play(l, a, "laser", 0.7f, 1.7f);
                    hit(best, 25f);
                }
            }
            // 땅 파기 (griefing) + 흙먼지
            if (MechManager.griefOn(l.getServer()) && (ride || drillT > 0)) {
                BlockPos c = BlockPos.containing(seg[0]);
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                    BlockState st = l.getBlockState(q);
                    if (!st.isAir() && st.getDestroySpeed(l, q) >= 0 && !st.is(Blocks.BEDROCK)) l.destroyBlock(q, false);
                }
            }
            if (t % 2 == 0) for (int i = 0; i < N; i += 5) {
                BlockPos bp = BlockPos.containing(seg[i]);
                BlockState st = l.getBlockState(bp);
                if (!st.isAir() && st.getFluidState().isEmpty()) {
                    Vec3 s = seg[i];
                    l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), s.x, s.y + 1, s.z, 14, 1.6, 0.8, 1.6, 0.2);
                    if (i == 0 && t % 6 == 0) l.playSound(null, s.x, s.y, s.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 2f, 0.4f);
                }
            }
            if (t % 24 == 0) l.playSound(null, seg[0].x, seg[0].y, seg[0].z, SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 1.6f, 0.4f);
            if (t % 6 == 0 && vel.length() > 0.3) l.playSound(null, seg[0].x, seg[0].y, seg[0].z, SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.6f, 0.5f);
            return false;
        }

        void hit(LivingEntity e, float dmg) {
            if (!e.isAlive() || e == owner) return;
            e.invulnerableTime = 0;
            e.hurtServer(l, l.damageSources().playerAttack(owner), dmg);
            if (!e.isAlive()) kills++;
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            Holo.flare(l, tag + "x", c, 1.6, 0xFF3030, 4);
        }

        void leave() {
            Holo.flare(l, tag + "x", seg[0], 5, 0xFF3030, 8);
            Holo.banner(l, tag + "x", seg[0].add(0, 5, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("굴착기 MK-0 귀환", 0xFF3030, true) + "]}", 1.4, 40);
            Holo.killOwner(tag);
            if (ride && owner.isAlive()) owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        }
    }
}
