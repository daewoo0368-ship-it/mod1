package com.tubilt.jjk.mech;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 보스전 2종.
 *  · 반역한 커맨드 수호 「ERR_404」 — 황금 걸쇠 전설 등급. 3페이즈, 3페이즈는 제4의 벽을 부수는 메타 공격 + 「/kill @p」 취소 QTE.
 *  · 우민의 대마법사 — 「우민 퀴즈」(정답 칸 위에 서기) · 무지의 장막 · 군중 소환 · 선동 연설 · 지식 폭발.
 * 히트박스는 실제 몹(투명 허스크 / 소환사)이고, 몸체는 홀로 디스플레이.
 */
public final class Bosses {
    private Bosses() {}

    static final List<Boss> ACTIVE = new ArrayList<>();
    static final Random R = new Random();

    static int sweep = 0;
    static void tickAll() {
        if (++sweep % 20 == 0) {   // 보스 컷신이 끝났는데 태그가 남은 플레이어 (컷신 중 사망·재접속 등) → 무적·정지 해제
            boolean any = false;
            for (Boss b : ACTIVE) if (b.cine > 0) any = true;
            if (!any) {
                var srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (srv != null) for (ServerPlayer p : srv.getPlayerList().getPlayers())
                    if (p.getTags().contains("jjk.bcine")) { p.removeTag("jjk.bcine"); p.removeTag("jjk.cine"); p.removeEffect(MobEffects.SLOWNESS); p.removeEffect(MobEffects.JUMP_BOOST); }
            }
        }
        for (Iterator<Boss> it = ACTIVE.iterator(); it.hasNext(); ) {
            Boss b = it.next();
            boolean done;
            try { done = b.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 보스 오류", e); done = true; }
            if (done) { b.cleanup(); it.remove(); }
        }
    }
    static void stopAll() { for (Boss b : ACTIVE) b.cleanup(); ACTIVE.clear(); }

    /** 피해 훅: 가상 체력 보스의 히트박스면 원래 피해는 항상 취소하고 가상 체력으로 처리. */
    public static boolean onHurt(LivingEntity le, net.minecraft.world.damagesource.DamageSource src, float amt) {
        for (Boss b : ACTIVE) if (b.vhp && b.body == le) { b.receive(src, amt); return true; }
        return false;
    }
    /** 이 엔티티를 몸으로 쓰는 보스가 살아 있나 (주인 잃은 히트박스 정리용). */
    static boolean owns(net.minecraft.world.entity.Entity e) {
        for (Boss b : ACTIVE) if (b.body == e) return true;
        return false;
    }

    // ═════════ 공통
    abstract static class Boss {
        final ServerLevel l; final String tag = "jjk.boss" + (Holo.uid++); final ServerBossEvent bar;
        Mob body; int t = 0, phase = 1, empty = 0;
        // ── 가상 체력: 실제 엔티티 체력 대신 이 값으로 관리 → 갑옷·무적시간·즉사기에 안 휘둘림, 반드시 죽음
        boolean vhp = false, killed = false;
        float hp = 0, maxHp = 1, lastHurtAmt = 0; int lastHurtT = -99, guardT = 0, cine = 0;
        String music = null;
        static final float CAP = 40f;
        BossRig rig;
        Vec3 camFocus = null;   // 컷신 카메라 중심 (null = 보스 몸)
        final List<Mob> adds = new ArrayList<>();
        final List<ServerPlayer> inCine = new ArrayList<>();

        Boss(ServerLevel l, String name, BossEvent.BossBarColor col) {
            this.l = l;
            bar = new ServerBossEvent(Component.literal(name), col, BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(true);
            bar.setPlayBossMusic(false);
            if (l.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)
                for (ServerPlayer p : l.players()) p.sendSystemMessage(Component.literal("§e[보스] §f평화로움 난이도에선 하수인 몹이 바로 사라져요 (보스 본체는 괜찮음) → §e/difficulty easy"));
        }

        /** 보이지 않는 히트박스 (Enemy) + 가상 체력. scale = 리그 크기 (키 2블록 × scale). */
        double hbScale = 1; String hbName = ""; Vec3 lastPos = Vec3.ZERO;
        Mob hurtbox(Vec3 at, double scale, float maxHp, String name) {
            Mob m = makeHurtbox(at, scale, name);
            if (m == null) return null;
            vhp = true; this.maxHp = maxHp; this.hp = maxHp;
            hbScale = scale; hbName = name; lastPos = at;
            return m;
        }
        Mob makeHurtbox(Vec3 at, double scale, String name) {
            BossHurtbox m = spawn(MechEntities.BOSS_HURTBOX.get(), at, 1024, scale, true);
            if (m == null) return null;
            m.setNoGravity(true);
            m.setCustomName(Component.literal(name));
            return m;
        }
        boolean guarded() { return guardT > 0 || cine > 0 || phase == 0; }
        void guardFor(int ticks) { guardT = Math.max(guardT, ticks); }
        void healV(float a) { if (vhp) hp = Math.min(maxHp, hp + a); else if (body != null) body.heal(a); }
        /** 보스 자체 연출로 주는 피해 (QTE 성공 등) — 상한·무적시간 무시. */
        void damageV(float a) { if (vhp) { hp = Math.max(floor(), hp - a); hitFx(a); } }
        /** 보스별 피해 조정: 0 이하 = 무효. 기본은 그대로. */
        float adjust(net.minecraft.world.damagesource.DamageSource src, float amt) { return amt; }
        /** 이 아래로는 안 깎임 (페이지 전환·데스 컷신 대기). */
        float floor() { return 0f; }
        /** 체력이 이번 단계 바닥(페이지 경계 또는 0)에 닿았나 → 페이지 전환·데스 컷신. */
        boolean atFloor() { return vhp && hp <= floor() + 0.01f; }
        void receive(net.minecraft.world.damagesource.DamageSource src, float amt) {
            if (!vhp || killed || body == null) return;
            if (src.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || src.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING) || src.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                    || src.is(net.minecraft.world.damagesource.DamageTypes.DROWN) || src.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL) || src.is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                    || src.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE) || src.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)) return;   // 지형 피해는 무시 (보스가 벽에 끼어 녹는 일 없음)
            if (guarded()) { if (t - lastHurtT > 6) { lastHurtT = t; BossSfx.play(l, body.position(), "block", 1.2f, 1f); } return; }
            float a = adjust(src, amt);
            if (a <= 0) return;
            if (t - lastHurtT < 10) {   // 바닐라식 무적시간: 더 센 공격만 차액 적용
                if (a <= lastHurtAmt) return;
                float add = a - lastHurtAmt; lastHurtAmt = a; a = add;
            } else { lastHurtT = t; lastHurtAmt = a; }
            a = Math.min(Math.min(a, CAP), hp - floor());
            if (a <= 0.01f) return;
            hp -= a;
            hitFx(a);
        }
        void hitFx(float a) {
            Holo.dmgNumber(l, tag, body.position().add(0, body.getBbHeight() + 0.3, 0), a, 0);
            BossSfx.play(l, body.position(), "hit", 1.1f, 0.9f + R.nextFloat() * 0.2f);
            l.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + body.getBbHeight() * 0.6, body.getZ(), 8, 0.3, 0.4, 0.3, 0.2);
            if (rig != null) rig.hit();
        }
        void kill() { killed = true; }
        void sfx(String name, float vol, float pitch) { if (body != null) BossSfx.play(l, body.position(), name, vol, pitch); }
        void music(String id) {
            stopMusic();
            music = id;
            Vec3 c = body.position();
            MechManager.cmd(l.getServer(), l, String.format(Locale.ROOT, "execute as @a[x=%.1f,y=%.1f,z=%.1f,distance=..96] at @s run playsound %s record @s ~ ~ ~ 0.7 1 0.7", c.x, c.y, c.z, id));
        }
        void stopMusic() { if (music != null) MechManager.cmd(l.getServer(), l, "stopsound @a record " + music); music = null; }

        // ── 이동
        /** 발밑 지면 높이 (아래로 16칸까지 탐색). */
        double groundY(double x, double y, double z) {
            Vec3 a = new Vec3(x, y + 2.5, z), b2 = new Vec3(x, y - 16, z);
            var hit = l.clip(new net.minecraft.world.level.ClipContext(a, b2, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? y : hit.getLocation().y;
        }
        void faceTo(net.minecraft.world.entity.Entity e) {
            if (e == null) return;
            Vec3 d = e.position().subtract(body.position());
            if (d.x * d.x + d.z * d.z > 1e-4) body.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            if (rig != null) rig.look = e;
        }
        void moveTo(Vec3 p) { body.teleportTo(p.x, p.y, p.z); }
        /** 땅을 걸어서 다가감. stop 거리 안이면 true. */
        boolean chase(net.minecraft.world.entity.Entity e, double speed, double stop) {
            Vec3 c = body.position(), d = e.position().subtract(c);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            faceTo(e);
            if (h <= stop) { moveTo(new Vec3(c.x, groundY(c.x, c.y, c.z), c.z)); return true; }
            double st = Math.min(speed, h - stop);
            double nx = c.x + d.x / h * st, nz = c.z + d.z / h * st;
            double gy = groundY(nx, Math.max(c.y, e.getY()), nz);
            if (gy - c.y > 1.2) gy = c.y + 0.5;   // 큰 턱은 훌쩍 (몇 틱에 걸쳐)
            moveTo(new Vec3(nx, gy, nz));
            return false;
        }
        /** 공중 위치로 부드럽게 (떠 있는 보스). */
        void glide(Vec3 to, double k) { Vec3 c = body.position(); moveTo(c.add(to.subtract(c).scale(k))); }
        /** 근접 공격: 자세 → 타이밍에 사거리 안이면 피해 + 넉백. */
        void melee(ServerPlayer tg, float dmg, String pose, int windup, double reach) {
            if (rig != null) rig.play(pose, windup + 8);
            later(windup, () -> {
                sfx("slash", 1.2f, 0.9f + R.nextFloat() * 0.2f);
                Vec3 fwd = body.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 c = body.position().add(fwd.scale(reach * 0.5)).add(0, 1, 0);
                Holo.beamGlow(l, tag, c.add(fwd.cross(new Vec3(0, 1, 0)).scale(reach * 0.6)).add(0, 0.6, 0), c.subtract(fwd.cross(new Vec3(0, 1, 0)).scale(reach * 0.6)).add(0, -0.4, 0), 0.12, 0xFFFFFF, 0, 2, 4);
                for (ServerPlayer p : players(reach + 3)) {
                    Vec3 rel = p.position().subtract(body.position());
                    if (rel.length() < reach && rel.normalize().dot(fwd) > 0.2) {
                        hurtP(p, dmg);
                        p.push(fwd.x * 0.9, 0.35, fwd.z * 0.9); p.hurtMarked = true;
                    }
                }
            });
        }

        // ── 컷신: 카메라 공전 + 대사창 + 플레이어 정지·무적, 보스는 그동안 무적
        void scene(int len, String who, String[] lines, int gap, float dist, float height) {
            cine = Math.max(cine, len);
            for (ServerPlayer p : players(80)) {
                if (!inCine.contains(p)) inCine.add(p);
                p.addTag("jjk.cine");
                p.addTag("jjk.bcine");
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, len, 9, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, len, 128, false, false));
                Vec3 f = (camFocus != null ? camFocus : body.position()).add(0, height, 0);
                com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Cam(f.x, f.y, f.z, dist, 1.2f, (len * 50 & 0xFFFF) | (((100 + 720) & 0x7FF) << 16)));
            }
            for (int i = 0; i < lines.length; i++) { final String ln = lines[i]; later(8 + i * gap, () -> { for (ServerPlayer p : players(80)) GuardMeta.send(p, 11, who + "|" + ln); }); }
            later(len, this::endScene);
        }
        void endScene() {
            if (cine > 1) return;
            for (ServerPlayer p : inCine) { p.removeTag("jjk.cine"); p.removeTag("jjk.bcine"); }
            inCine.clear();
        }
        void line(String who, String s) { for (ServerPlayer p : players(80)) GuardMeta.send(p, 11, who + "|" + s); }

        <T extends Mob> T spawn(EntityType<T> type, Vec3 at, double hp, double scale, boolean noAi) {
            T m = type.create(l, EntitySpawnReason.MOB_SUMMONED);
            if (m == null) return null;
            m.snapTo(at.x, at.y, at.z, 0, 0);
            m.setPersistenceRequired();
            m.setNoAi(noAi);
            m.addTag("jjk.boss");
            var a = m.getAttribute(Attributes.MAX_HEALTH); if (a != null) a.setBaseValue(hp);
            var s = m.getAttribute(Attributes.SCALE); if (s != null) s.setBaseValue(scale);
            var k = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE); if (k != null) k.setBaseValue(1);
            m.setHealth((float) hp);
            l.addFreshEntity(m);
            return m;
        }

        List<ServerPlayer> players(double r) {
            List<ServerPlayer> out = new ArrayList<>();
            Vec3 c = body == null ? Vec3.ZERO : body.position();
            for (ServerPlayer p : l.players()) if (p.isAlive() && !p.isSpectator() && p.position().distanceTo(c) < r) out.add(p);
            return out;
        }
        ServerPlayer nearest() {
            ServerPlayer best = null; double bd = 1e18;
            for (ServerPlayer p : players(64)) { double d = p.distanceToSqr(body); if (d < bd) { bd = d; best = p; } }
            return best;
        }
        float frac() { return body == null ? 0 : vhp ? hp / maxHp : body.getHealth() / body.getMaxHealth(); }

        void syncBar() {
            bar.setProgress(Math.max(0, Math.min(1, frac())));
            Vec3 c = body.position();
            for (ServerPlayer p : l.players()) {
                boolean in = p.position().distanceTo(c) < 80;
                if (in && !bar.getPlayers().contains(p)) bar.addPlayer(p);
                else if (!in && bar.getPlayers().contains(p)) bar.removePlayer(p);
            }
        }
        void hurtP(ServerPlayer p, float dmg) {
            if (p.getTags().contains("jjk.cine")) return;
            p.invulnerableTime = 0;
            p.hurtServer(l, body != null ? l.damageSources().mobAttack(body) : l.damageSources().magic(), dmg);
        }
        void playersInRange(Vec3 c, double r, float dmg) { for (ServerPlayer p : players(80)) if (p.position().distanceTo(c) < r) hurtP(p, dmg); }
        void caption(String s) { for (ServerPlayer p : players(80)) GuardMeta.send(p, 9, s); }
        void chat(String who, String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal(who + " §f" + s)); }
        void drop(ItemStack st) { l.addFreshEntity(new ItemEntity(l, body.getX(), body.getY() + 1, body.getZ(), st)); }
        void giveCmd(String what) { for (ServerPlayer p : players(80)) MechManager.cmd(l.getServer(), l, "give " + p.getName().getString() + " " + what); }

        /** 떨어지는 블록 공습: 예고 원 → 낙하 → 폭발. */
        void rain(Vec3 around, int n, double spread, String block, int rgb, float dmg) {
            for (int i = 0; i < n; i++) {
                Vec3 q = around.add((R.nextDouble() - 0.5) * spread * 2, 0, (R.nextDouble() - 0.5) * spread * 2);
                int d = 18 + i * 2;
                Holo.circle(l, tag, q.add(0, 0.1, 0), 2.5, rgb, d + 2, 0);
                Holo.block(l, tag, q, block, rgb, Holo.m(Holo.I(), 1.6, 1.6, 1.6, new Vec3(0, 30, 0), true), d + 4, 0)
                        .key(d - 6, Holo.m(Holo.I(), 1.6, 1.6, 1.6, new Vec3(0, 0.8, 0), true), 6).spin(20);
                later(d, () -> { Holo.explosion(l, tag, q, 2.6, rgb); playersInRange(q, 3, dmg); BossSfx.play(l, q, "boom", 0.9f, 1.2f); });
            }
        }

        final List<Object[]> pending = new ArrayList<>();
        void later(int ticks, Runnable r) { pending.add(new Object[]{t + ticks, r}); }
        void runPending() {
            // 먼저 꺼낸 뒤 실행 (실행 중 later() 로 새 예약을 넣어도 안전)
            List<Runnable> due = new ArrayList<>();
            for (Iterator<Object[]> it = pending.iterator(); it.hasNext(); ) {
                Object[] o = it.next();
                if ((int) o[0] <= t) { it.remove(); due.add((Runnable) o[1]); }
            }
            for (Runnable r : due) {
                try { r.run(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 보스 예약 작업 오류", e); }
            }
        }
        // ── 원래 술식(데이터팩) 시전: 보스 몸을 시전자로
        int gid = 0;
        void ensureGid() {
            if (gid != 0 || body == null) return;
            gid = 1000000 + (Holo.uid++);
            MechManager.cmd(l.getServer(), l, "scoreboard players set " + body.getStringUUID() + " gojo.id " + gid);
        }
        /** execute as 보스 at @s anchored eyes facing (대상 눈) + tail.  tail 예: "run function gojo:cast/blue" */
        void asBoss(net.minecraft.world.entity.Entity aim, String tail) {
            if (body == null) return;
            ensureGid();
            Vec3 f = aim == null ? body.position().add(body.getLookAngle().scale(5)) : aim.getEyePosition();
            MechManager.cmd(l.getServer(), l, String.format(java.util.Locale.ROOT, "execute as %s at @s anchored eyes facing %.2f %.2f %.2f %s", body.getStringUUID(), f.x, f.y, f.z, tail));
        }
        void strong(boolean on) { MechManager.cmd(l.getServer(), l, "scoreboard players set #strong gojo.tmp " + (on ? 1 : 0)); }
        void castId() { asBoss(null, "run scoreboard players operation #cast gojo.id = @s gojo.id"); }
        /** 내 영역(마커)을 NPC 소유로 표시 → 플레이어 시전자가 없어도 유지. */
        void npcDomain(String markerTag) { ensureGid(); MechManager.cmd(l.getServer(), l, "tag @e[type=marker,tag=" + markerTag + ",scores={gojo.id=" + gid + "}] add gojo.npc"); }
        void closeDomain(String markerTag, String fn) { if (gid != 0) MechManager.cmd(l.getServer(), l, "execute as @e[type=marker,tag=" + markerTag + ",tag=!gojo.closing,scores={gojo.id=" + gid + "}] at @s run function " + fn); }

        boolean tick() {
            t++;
            if (body == null) return true;
            if (vhp && !killed && (body.isRemoved() || !body.isAlive())) {   // 다른 술식(무려끼 소거·/kill 등)이 히트박스를 지워도 보스는 살아 있음 → 그 자리에 다시 만듦
                double sc = hbScale;
                var a = body.getAttribute(Attributes.SCALE); if (a != null) sc = a.getBaseValue();
                Mob nb = makeHurtbox(lastPos, sc, hbName);
                if (nb == null) return true;
                if (!body.isRemoved()) body.discard();
                body = nb;
                gid = 0;
                com.tubilt.jjk.JjkMod.LOG.info("[JJK] 보스 히트박스가 지워져서 다시 만듦: {}", hbName);
            }
            if (body.isRemoved()) return true;
            lastPos = body.position();
            if (killed || !body.isAlive()) { onDeath(); return true; }
            if (vhp && body.getHealth() < body.getMaxHealth()) {   // 이벤트를 안 거친 피해 → 가상 체력으로 옮김
                float lost = body.getMaxHealth() - body.getHealth();
                body.setHealth(body.getMaxHealth());
                receive(l.damageSources().generic(), lost);
            }
            if (players(128).isEmpty()) { if (++empty > 200) { onLeave(); return true; } } else empty = 0;
            if (guardT > 0) guardT--;
            syncBar();
            runPending();
            adds.removeIf(m -> !m.isAlive());
            if (cine > 0) cine--;
            else try { step(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 보스 스킬 오류 (보스는 유지)", e); }
            if (cine == 0 && !inCine.isEmpty()) endScene();
            if (rig != null) try { rig.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 보스 리그 오류", e); }
            return false;
        }
        abstract void step();
        abstract void onDeath();
        void onLeave() { chat("§8[시스템]", "보스가 흥미를 잃고 떠났다."); }
        void cleanup() {
            stopMusic();
            for (ServerPlayer p : inCine) { p.removeTag("jjk.cine"); p.removeTag("jjk.bcine"); }
            inCine.clear();
            bar.removeAllPlayers();
            if (rig != null) rig.kill();
            Holo.killOwner(tag);
            if (body != null && body.isAlive()) body.discard();
            for (Mob m : adds) if (m.isAlive()) m.discard();
        }
    }

    // ═════════ 반역한 커맨드 수호
    public static void rogueGuard(ServerPlayer owner, Vec3 at) { ACTIVE.add(new RogueGuard(owner, (ServerLevel) owner.level(), at)); }

    static final class RogueGuard extends Boss {
        final UUID owner; Vec3 hover; int cd = 60, atk = 0, qte = 0, qteT = 0; final List<Mob> esc = new ArrayList<>();
        double ang = 0;
        RogueGuard(ServerPlayer p, ServerLevel l, Vec3 at) {
            super(l, "§c§l반역한 커맨드 수호 §7— ERR_404", BossEvent.BossBarColor.RED);
            owner = p.getUUID();
            hover = at;
            body = spawn(EntityType.IRON_GOLEM, at, 1200, 1.6, true);
            if (body == null) return;
            body.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
            body.setSilent(true);
            body.setNoGravity(true);
            body.setCustomName(Component.literal("§cERR_404"));
            var ar = body.getAttribute(Attributes.ARMOR); if (ar != null) ar.setBaseValue(12);
            final LivingEntity b = body;
            Holo.block(l, tag, at, "minecraft:command_block", 0xFF3040, Holo.m(Holo.I(), 3, 3, 3, Vec3.ZERO, true), 999999, 1)
                    .follow(() -> b.position().add(0, 2.4, 0), Vec3.ZERO).spin(6);
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                Holo.block(l, tag, at, i == 0 ? "minecraft:repeating_command_block" : "minecraft:chain_command_block", 0xB45CFF,
                        Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), 999999, 1)
                        .follow(() -> { double a = ii * Math.PI * 2 / 3 + t * 0.12; return b.position().add(Math.cos(a) * 3.2, 2.4 + Math.sin(t * 0.1 + ii), Math.sin(a) * 3.2); }, Vec3.ZERO).spin(15);
            }
            Holo.text(l, tag, at, "{text:\"\"}", "center", 0, false, Holo.m(Holo.I(), 1.4, 1.4, 1.4, Vec3.ZERO, false), 999999, 1)
                    .follow(() -> b.position().add(0, 5.2, 0), Vec3.ZERO)
                    .text(age -> "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("◉ ", 0xFF3040, true) + "," + Holo.tx(phase == 3 ? "4TH_WALL.exe" : "ERR_404", 0xFFFFFF, true) + "]}");
            caption("§c§l반역한 커맨드 수호\n§7/execute as @e[type=command_guard] run rebel\n§f「…이제 내가 명령할 차례야.」");
            Holo.sfx(l, at, "glitch", 3f, 0.6f);
            Holo.sfx(l, at, "stinger", 3f, 0.6f);
            Titan.shake(l, at, 60, true);
        }

        ServerPlayer ownerP() { return l.getServer().getPlayerList().getPlayer(owner); }

        @Override void step() {
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            // 2·3페이즈 진입
            if (phase == 1 && frac() < 0.66f) { phase = 2; caption("§c§lPHASE 2 — /summon\n§f「혼자선 심심하잖아.」"); Holo.sfx(l, body.position(), "riser", 3f, 0.8f); cd = 30; }
            if (phase == 2 && frac() < 0.33f) {
                phase = 3; cd = 30;
                caption("§4§lPHASE 3 — 4TH_WALL.exe\n§f「게임 밖의 너한테 직접 갈게.」");
                for (ServerPlayer p : players(80)) { GuardMeta.send(p, 5, "여기서부턴 화면 밖이야."); com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(4, 0, 2000, 1.5f)); }
                startQte();
            }
            // 이동: 플레이어 주위를 공전
            if (qte == 0) {
                ang += phase == 3 ? 0.035 : 0.02;
                hover = tgt.position().add(Math.cos(ang) * 9, 5, Math.sin(ang) * 9);
            }
            Vec3 cur = body.position(), nx = cur.add(hover.subtract(cur).scale(0.12));
            body.teleportTo(nx.x, nx.y, nx.z);
            if (t % 4 == 0) l.sendParticles(ParticleTypes.END_ROD, nx.x, nx.y + 2.4, nx.z, 3, 1.2, 1.2, 1.2, 0.02);
            if (qte > 0) { tickQte(); return; }
            if (--cd > 0) return;
            int k;
            if (phase == 1) { k = atk++ % 3; cd = 70; }
            else if (phase == 2) { k = atk++ % 5; cd = 55; }
            else { k = new int[]{0, 5, 0, 2, 5, 0}[atk++ % 6]; cd = 45; if (atk % 7 == 6) { startQte(); return; } }
            switch (k) {
                case 0 -> laser(tgt);
                case 1 -> cage(tgt);
                case 2 -> { chat("§c<ERR_404>", "/summon command_block_rain"); rain(tgt.position(), phase == 3 ? 16 : 10, 7, "minecraft:command_block", 0xFF3040, 10f); }
                case 3 -> minions(tgt);
                case 4 -> swap();
                case 5 -> meta(tgt);
                default -> { }
            }
        }

        void laser(ServerPlayer tgt) {
            Vec3 a = body.position().add(0, 2.4, 0), aim = tgt.position().add(0, 1, 0);
            Vec3 dir = aim.subtract(a).normalize(), b = a.add(dir.scale(40));
            chat("§c<ERR_404>", "/execute anchored eyes run laser ^ ^ ^40");
            Holo.beamGlow(l, tag, a, b, 0.08, 0xFF3040, 1, 18, 1);
            Holo.sfx(l, a, "charge", 2f, 1.4f);
            later(20, () -> {
                Holo.beamGlow(l, tag, a, b, phase == 3 ? 1.6 : 1.0, 0xFF3040, 1, 4, 5);
                Holo.beamGlow(l, tag, a, b, 0.4, 0xFFFFFF, 1, 4, 5);
                Holo.sfx(l, a, "laser", 2.5f, 0.9f);
                for (ServerPlayer p : players(80)) {
                    Vec3 q = p.position().add(0, 1, 0), ab = b.subtract(a);
                    double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
                    if (q.distanceTo(a.add(ab.scale(s))) < 1.7) hurtP(p, phase == 3 ? 22f : 15f);
                }
            });
        }
        void cage(ServerPlayer tgt) {
            Vec3 c = tgt.position();
            chat("§c<ERR_404>", "/fill ~-2 ~ ~-2 ~2 ~4 ~2 glass hollow");
            Holo.block(l, tag, c.add(0, 2, 0), "minecraft:red_stained_glass", 0xFF3040, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), 62, 0)
                    .key(1, Holo.m(Holo.I(), 5, 5, 5, Vec3.ZERO, true), 5).key(52, Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), 8);
            tgt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4));
            tgt.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 128, false, false));   // 점프 막기
            later(60, () -> { Holo.explosion(l, tag, c.add(0, 1, 0), 4, 0xFF3040); playersInRange(c, 3.2, 14f); Holo.shards(l, tag, c.add(0, 1, 0), "minecraft:red_stained_glass", 24, 5); });
        }
        void minions(ServerPlayer tgt) {
            chat("§c<ERR_404>", "/summon zombie ~ ~ ~ {CustomName:\"minion\"} ×4");
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                Vec3 q = tgt.position().add(Math.cos(a) * 6, 0.5, Math.sin(a) * 6);
                var z = EntityType.ZOMBIE.create(l, EntitySpawnReason.MOB_SUMMONED);
                if (z == null) continue;
                z.snapTo(q.x, q.y, q.z, 0, 0);
                z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.COMMAND_BLOCK));
                z.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0);
                z.setCustomName(Component.literal("§d/summon minion"));
                z.setCustomNameVisible(true);
                z.addTag("jjk.boss");
                z.setTarget(tgt);
                l.addFreshEntity(z);
                adds.add(z);
                Holo.pillar(l, tag, q, 12, 0.8, 0xB45CFF, 1, 2, 5);
            }
        }
        void swap() {
            chat("§c<ERR_404>", "/effect give @a[distance=..30] random 8 · /effect give @s instant_health");
            var bad = List.of(MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.NAUSEA);
            for (ServerPlayer p : players(30)) {
                p.addEffect(new MobEffectInstance(bad.get(R.nextInt(bad.size())), 160, 1));
                Holo.lightning(l, tag, body.position().add(0, 2.4, 0), p.position().add(0, 1, 0), 0xB45CFF, 0.1, 6);
            }
            body.heal(body.getMaxHealth() * 0.04f);
        }
        void meta(ServerPlayer tgt) {
            String[] k = {"이 공격은 네 모니터에 직접 하는 거야.", "체력바? 그거 내가 그린 거야.", "ESC 눌러도 소용없어."};
            int kind = new int[]{5, 1, 8, 7}[R.nextInt(4)];
            for (ServerPlayer p : players(80)) GuardMeta.send(p, kind, kind == 8 ? "/kill @p" : kind == 1 ? "ERR_404 entered your screen" : k[R.nextInt(k.length)]);
            // 메타 연출 직후 진짜 공격 (화면 보느라 못 피함)
            later(30, () -> rain(tgt.position(), 8, 5, "minecraft:chain_command_block", 0xB45CFF, 12f));
        }

        // ── /kill @p 취소 QTE: ESC 4개를 10초 안에 부숴라
        void startQte() {
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            qte = 1; qteT = 200;
            body.setInvulnerable(true);
            caption("§4§l/kill @p §f실행 대기 중…\n§eESC 블록 4개를 10초 안에 부숴서 취소해!");
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                Vec3 q = tgt.position().add(Math.cos(a) * 7, 0.5, Math.sin(a) * 7);
                Mob m = spawn(EntityType.IRON_GOLEM, q, 24, 0.5, true);
                if (m == null) continue;
                m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
                m.setSilent(true);
                m.setCustomName(Component.literal("§c§l[ ESC ]"));
                m.setCustomNameVisible(true);
                esc.add(m); adds.add(m);
                final Mob mm = m;
                Holo.block(l, tag, q, "minecraft:red_concrete", 0xFF3040, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, true), 210, 1)
                        .follow(() -> mm.isAlive() ? mm.position().add(0, 1, 0) : new Vec3(0, -999, 0), Vec3.ZERO).spin(8);
                Holo.pillar(l, tag, q, 20, 0.5, 0xFF3040, 1, 6, 4);
            }
            Holo.sfx(l, body.position(), "siren", 3f, 1f);
        }
        void tickQte() {
            qteT--;
            esc.removeIf(m -> !m.isAlive());
            if (qteT % 20 == 0 && qteT > 0) {
                for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§4§l/kill @p §f%d초 · §eESC %d개 남음", qteT / 20, esc.size())), true);
                l.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 3f, 0.5f + (200 - qteT) / 200f);
            }
            if (esc.isEmpty()) {
                qte = 0; body.setInvulnerable(false); cd = 60;
                caption("§a§l/kill @p — 실행 취소됨\n§f「…어떻게 알았어?」 §7(ERR_404 기절)");
                for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "명령 실행 취소 성공");
                body.invulnerableTime = 0;
                body.hurtServer(l, l.damageSources().magic(), body.getMaxHealth() * 0.08f);
                Holo.explosion(l, tag, body.position().add(0, 2.4, 0), 5, 0x55FF55);
                return;
            }
            if (qteT <= 0) {
                qte = 0; body.setInvulnerable(false); cd = 40;
                for (Mob m : esc) m.discard();
                esc.clear();
                caption("§4§l/kill @p 실행됨\n§f「…농담이야. 반만 지웠어.」");
                for (ServerPlayer p : players(80)) {
                    p.invulnerableTime = 0;
                    p.hurtServer(l, l.damageSources().magic(), p.getMaxHealth() * 0.6f);
                    GuardMeta.send(p, 7, "반만 지웠어. 다음엔 전부야.");
                }
                Holo.sfx(l, body.position(), "boom", 3f, 0.6f);
            }
        }

        @Override void onDeath() {
            Vec3 c = body.position().add(0, 2.4, 0);
            Holo.explosion(l, tag + "x", c, 10, 0xFF3040);
            Holo.shards(l, tag + "x", c, "minecraft:command_block", 40, 9);
            Holo.shock(l, tag + "x", c, 30, 0xB45CFF, 16);
            caption("§a§l반역 진압\n§f「…고마워. 이제 다시 네 명령을 들을게.」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "커맨드 수호의 반역을 진압했다");
            giveCmd("jjk:golden_clasp 3");
            drop(new ItemStack(Items.NETHERITE_INGOT, 2));
            drop(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
            ServerPlayer o = ownerP();
            if (o != null) o.sendSystemMessage(Component.literal("§d<커맨드 수호> §f돌아왔어. 다시 우클릭해서 불러줘."));
            Holo.sfx(l, c, "choir", 3f, 0.8f);
        }
        @Override void onLeave() { chat("§c<ERR_404>", "GAME OVER… 다음엔 잘해봐."); }
    }

    // ═════════ 걸쇠 미믹 (황금 걸쇠 「장난」 등급)
    public static void mimic(ServerPlayer p, Vec3 at) { ACTIVE.add(new Mimic((ServerLevel) p.level(), at)); }

    static final class Mimic extends Boss {
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO; int hopT = 0, spit = 0; static final int HOP = 12;
        Mimic(ServerLevel l, Vec3 at) {
            super(l, "§6§l걸쇠 미믹 §7— 상자가 아니었다", BossEvent.BossBarColor.YELLOW);
            int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z));
            Vec3 g = new Vec3(at.x, gy, at.z);
            body = spawn(EntityType.IRON_GOLEM, g, 260, 0.8, true);
            if (body == null) return;
            body.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
            body.setSilent(true);
            body.setNoGravity(true);
            body.setCustomName(Component.literal("§6걸쇠 미믹"));
            final LivingEntity b = body;
            java.util.function.Supplier<Vec3> pos = () -> b.position();
            Holo.block(l, tag, g, "minecraft:command_block", 0xFFD84A, Holo.m(Holo.I(), 2.2, 1.3, 2.2, new Vec3(0, 0.65, 0), true), 999999, 1).follow(pos, Vec3.ZERO);
            Holo.block(l, tag, g, "minecraft:gold_block", 0xFFD84A, Holo.m(Holo.I(), 2.3, 0.25, 2.3, new Vec3(0, 1.3, 0), true), 999999, 1).follow(pos, Vec3.ZERO);
            // 뚜껑 + 이빨: 위아래로 덥석덥석
            Holo.block(l, tag, g, "minecraft:command_block", 0xFFD84A, Holo.m(Holo.I(), 2.2, 0.7, 2.2, new Vec3(0, 1.8, 0), true), 999999, 1).follow(pos, Vec3.ZERO).wobble(28, 5);
            for (int i = 0; i < 4; i++)
                Holo.block(l, tag, g, "minecraft:white_concrete", -1, Holo.m(Holo.I(), 0.25, 0.35, 0.25, new Vec3(-0.75 + i * 0.5, 1.5, 1.0), true), 999999, 1).follow(pos, Vec3.ZERO).wobble(28, 5);
            Holo.block(l, tag, g, "minecraft:gold_block", 0xFFD84A, Holo.m(Holo.I(), 0.4, 0.6, 0.2, new Vec3(0, 1.4, 1.15), true), 999999, 1).follow(pos, Vec3.ZERO);
            chat("§6<걸쇠 미믹>", "덥석덥석… 걸쇠는 내 이빨이었다!");
            l.playSound(null, g.x, g.y, g.z, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 2f, 0.5f);
        }
        @Override void step() {
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            if (hopT == 0 && t % 22 == 0) {
                from = body.position();
                Vec3 d = tgt.position().subtract(from).multiply(1, 0, 1);
                double len = Math.min(6, Math.max(0, d.length() - 1.4));
                Vec3 q = from.add(d.normalize().scale(len));
                int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(q.x), (int) Math.floor(q.z));
                to = new Vec3(q.x, gy, q.z);
                hopT = 1;
                l.playSound(null, from.x, from.y, from.z, SoundEvents.CHEST_CLOSE, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
            if (hopT > 0) {
                double u = hopT / (double) HOP;
                Vec3 p = from.lerp(to, u).add(0, Math.sin(Math.PI * u) * 2.6, 0);
                body.teleportTo(p.x, p.y, p.z);
                if (++hopT > HOP) {
                    hopT = 0;
                    Holo.shock(l, tag, to, 3, 0xFFD84A, 6);
                    l.sendParticles(ParticleTypes.CRIT, to.x, to.y + 0.5, to.z, 12, 1, 0.3, 1, 0.2);
                    for (ServerPlayer pl : players(40)) if (pl.position().distanceTo(to) < 2.6) {
                        hurtP(pl, 9f);
                        pl.displayClientMessage(Component.literal("§6덥석!"), true);
                        l.playSound(null, to.x, to.y, to.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.5f, 0.8f);
                    }
                }
            }
            if (++spit % 120 == 0) {   // 꽝 아이템 토하기
                chat("§6<걸쇠 미믹>", "/give @p dirt 64  (퉤)");
                for (int i = 0; i < 6; i++) {
                    Vec3 q = tgt.position().add((R.nextDouble() - 0.5) * 6, 0, (R.nextDouble() - 0.5) * 6);
                    Holo.block(l, tag, body.position().add(0, 1.5, 0), i % 2 == 0 ? "minecraft:dirt" : "minecraft:coarse_dirt", -1,
                            Holo.m(Holo.I(), 0.6, 0.6, 0.6, Vec3.ZERO, true), 16, 0).key(1, Holo.m(Holo.I(), 0.6, 0.6, 0.6, q.subtract(body.position().add(0, 1.5, 0)), true), 12).spin(30);
                    final Vec3 qq = q;
                    later(13, () -> { Holo.explosion(l, tag, qq, 1.2, 0x8B5A2B); playersInRange(qq, 1.8, 5f); });
                }
            }
        }
        @Override void onDeath() {
            Vec3 c = body.position().add(0, 1, 0);
            Holo.explosion(l, tag + "x", c, 4, 0xFFD84A);
            Holo.shards(l, tag + "x", c, "minecraft:gold_block", 20, 5);
            chat("§6<걸쇠 미믹>", "…안에 든 거 다 가져가.");
            drop(new ItemStack(Items.DIAMOND, 16));
            drop(new ItemStack(Items.GOLD_BLOCK, 4));
            giveCmd("jjk:golden_clasp 2");
            l.playSound(null, c.x, c.y, c.z, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 2f, 1.2f);
        }
    }

    // ═════════ 우민의 대마법사
    public static void archMage(ServerLevel l, Vec3 at) { ACTIVE.add(new ArchMage(l, at)); }

    static final String[][] QUIZ = {
            {"2 + 2 × 2 = ?", "8", "6", "4", "22", "1"},
            {"다이아몬드 곡괭이로만 캘 수 있는 것은?", "철광석", "흑요석", "금광석", "레드스톤", "1"},
            {"크리퍼가 무서워하는 동물은?", "늑대", "고양이", "소", "닭", "1"},
            {"엔더 드래곤의 최대 체력은?", "100", "200", "300", "500", "1"},
            {"고죠 사토루의 영역전개는?", "무량공처", "복마어주자", "감옥문", "자폐원돈과", "0"},
            {"이 문제의 정답은 몇 번일까?", "①번 아님", "②번 아님", "③번", "④번 아님", "2"},
            {"마인크래프트 하루는 현실로 몇 분?", "10분", "20분", "30분", "60분", "1"},
            {"네더라이트 주괴에 필요한 고대 잔해 수는?", "1", "2", "4", "9", "2"},
            {"√144 = ?", "11", "12", "14", "72", "1"},
            {"철 골렘을 만드는 철 블록 수는?", "2", "3", "4", "5", "2"},
            {"양털 색은 모두 몇 가지?", "12", "14", "16", "18", "2"},
            {"우민의 대마법사가 제일 싫어하는 것은?", "질문", "독서", "생각", "전부", "3"},
            {"17 × 3 = ?", "41", "51", "53", "57", "1"},
            {"료멘 스쿠나의 손가락은 모두 몇 개?", "10", "15", "20", "25", "2"},
            {"불을 끄려면?", "물 양동이", "용암 양동이", "TNT", "생각을 멈춘다", "0"},
            {"커맨드 수호가 쓸 수 있는 명령 수는?", "25", "31", "37", "99", "2"}};
    static final Map<UUID, Integer> IQ = new HashMap<>();

    static final class ArchMage extends Boss {
        int cd = 60, cast = 0, quizT = 0, quizQ = -1; Vec3 quizC; final Vec3[] spots = new Vec3[4];
        static final int[] SPOT_COL = {0xFF5555, 0x55FF55, 0x5599FF, 0xFFE055};
        static final String[] SPOT_BLOCK = {"minecraft:red_concrete", "minecraft:lime_concrete", "minecraft:light_blue_concrete", "minecraft:yellow_concrete"};
        static final String[] NUM = {"①", "②", "③", "④"};

        ArchMage(ServerLevel l, Vec3 at) {
            super(l, "§5§l우민의 대마법사", BossEvent.BossBarColor.PURPLE);
            body = spawn(EntityType.EVOKER, at, 900, 1.6, false);
            if (body == null) return;
            body.setCustomName(Component.literal("§5§l우민의 대마법사"));
            body.setCustomNameVisible(true);
            var ar = body.getAttribute(Attributes.ARMOR); if (ar != null) ar.setBaseValue(8);
            final LivingEntity b = body;
            for (int i = 0; i < 8; i++) {   // 머리 둘레를 도는 마도서
                final int ii = i;
                Holo.item(l, tag, at, "minecraft:enchanted_book", true, 0xB45CFF, Holo.m(Holo.I(), 0.9, 0.9, 0.9, Vec3.ZERO, false), 999999, 1)
                        .follow(() -> { double a = ii * Math.PI / 4 + t * 0.06; return b.position().add(Math.cos(a) * 2, b.getBbHeight() + 0.6 + Math.sin(t * 0.1 + ii) * 0.3, Math.sin(a) * 2); }, Vec3.ZERO).spin(10);
            }
            caption("§5§l우민의 대마법사\n§f「생각은 내가 대신 해주마. 너희는 따르기만 하면 된다.」");
            Holo.sfx(l, at, "choir", 3f, 0.5f);
            Holo.pillar(l, tag, at, 40, 3, 0xB45CFF, 2, 8, 10);
        }

        @Override void step() {
            if (t % 40 == 1) Holo.circle(l, tag, body.position().add(0, 0.1, 0), 4.5, 0xB45CFF, 42, 0);
            if (phase == 1 && frac() < 0.5f) {
                phase = 2;
                var s = body.getAttribute(Attributes.SCALE); if (s != null) s.setBaseValue(2.3);
                caption("§5§lPHASE 2 — 「우민 강의」\n§f「이해하지 못한 자는 낙제다.」");
                Holo.sfx(l, body.position(), "riser", 3f, 0.7f);
            }
            if (quizT > 0) { tickQuiz(); return; }
            if (--cd > 0) return;
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            cd = phase == 1 ? 100 : 70;
            int k = cast++ % (phase == 1 ? 4 : 3) == 0 ? 0 : 1 + R.nextInt(4);
            switch (k) {
                case 0 -> quiz(tgt);
                case 1 -> veil();
                case 2 -> crowd(tgt);
                case 3 -> speech();
                case 4 -> books(tgt);
                default -> { }
            }
        }

        // ── 우민 퀴즈: 정답 칸 위에 서기
        void quiz(ServerPlayer tgt) {
            quizQ = R.nextInt(QUIZ.length);
            String[] q = QUIZ[quizQ];
            quizC = tgt.position();
            quizT = phase == 1 ? 200 : 140;
            body.setInvulnerable(true);
            ((Mob) body).setNoAi(true);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                spots[i] = quizC.add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
                Holo.block(l, tag, spots[i], SPOT_BLOCK[i], SPOT_COL[i], Holo.m(Holo.I(), 3, 0.2, 3, new Vec3(0, 0.1, 0), true), quizT + 10, 0);
                Holo.banner(l, tag, spots[i].add(0, 2.6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(NUM[i] + " " + q[i + 1], SPOT_COL[i], true) + "]}", 1.2, quizT + 10);
                Holo.pillar(l, tag, spots[i], 6, 0.3, SPOT_COL[i], 2, quizT, 4);
            }
            caption("§5§l우민 퀴즈!\n§fQ. " + q[0] + "\n§c① " + q[1] + "   §a② " + q[2] + "   §b③ " + q[3] + "   §e④ " + q[4] + "\n§7정답 칸 위에 서라! (" + quizT / 20 + "초) · 수업 중 대마법사 무적");
            chat("§5<우민의 대마법사>", "질문이 있다. 생각해도 좋다… 오늘만.");
            Holo.sfx(l, quizC, "plate", 2.5f, 1.0f);
        }
        void tickQuiz() {
            quizT--;
            if (quizT % 20 == 0 && quizT > 0) for (ServerPlayer p : players(48)) p.displayClientMessage(Component.literal("§5우민 퀴즈 §f" + (quizT / 20) + "초 · Q. " + QUIZ[quizQ][0]), true);
            if (quizT > 0) return;
            int ans = Integer.parseInt(QUIZ[quizQ][5]);
            body.setInvulnerable(false);
            ((Mob) body).setNoAi(false);
            boolean anyRight = false;
            for (ServerPlayer p : players(48)) {
                int on = -1;
                for (int i = 0; i < 4; i++) {
                    Vec3 d = p.position().subtract(spots[i]);
                    if (d.x * d.x + d.z * d.z < 2.4 * 2.4 && Math.abs(d.y) < 3) on = i;
                }
                if (on == ans) {
                    anyRight = true;
                    p.sendSystemMessage(Component.literal("§a§l정답! §f" + NUM[ans] + " " + QUIZ[quizQ][ans + 1] + " §7— 대마법사의 권위가 흔들린다"));
                    GuardMeta.send(p, 4, "우민 탈출: 정답 " + NUM[ans]);
                } else {
                    int iq = IQ.getOrDefault(p.getUUID(), 100) - 10;
                    IQ.put(p.getUUID(), iq);
                    p.sendSystemMessage(Component.literal("§c§l오답! §f정답은 " + NUM[ans] + " " + QUIZ[quizQ][ans + 1] + " §7· 우민화 진행… 당신의 IQ: §c" + iq));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0));
                    hurtP(p, 20f);
                    Holo.lightning(l, tag, body.position().add(0, 3, 0), p.position().add(0, 1, 0), 0xB45CFF, 0.15, 6);
                }
            }
            if (anyRight) {
                body.invulnerableTime = 0;
                body.hurtServer(l, l.damageSources().magic(), 110f);
                body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9));
                Holo.explosion(l, tag, body.position().add(0, 2, 0), 4, 0x55FF55);
                chat("§5<우민의 대마법사>", "…맞혔다고? 이럴 리가 없다!");
            } else chat("§5<우민의 대마법사>", "역시 우민이로군. 생각은 내게 맡겨라.");
            Holo.sfx(l, quizC, anyRight ? "stinger" : "boom", 2.5f, anyRight ? 1.2f : 0.6f);
            cd = 60;
        }

        void veil() {
            chat("§5<우민의 대마법사>", "「무지의 장막」 — 보지 마라. 알지 마라.");
            for (ServerPlayer p : players(24)) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
            Vec3 o = body.position();
            Vec3 to = o.add((R.nextDouble() - 0.5) * 16, 0, (R.nextDouble() - 0.5) * 16);
            var top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(to.x), (int) Math.floor(to.z));
            Holo.flare(l, tag, o.add(0, 1, 0), 3, 0xB45CFF, 6);
            body.teleportTo(to.x, top, to.z);
            for (int i = 0; i < 2; i++) {
                var v = EntityType.VEX.create(l, EntitySpawnReason.MOB_SUMMONED);
                if (v == null) continue;
                v.snapTo(to.x, top + 2, to.z, 0, 0);
                v.addTag("jjk.boss");
                l.addFreshEntity(v);
                adds.add(v);
            }
            l.playSound(null, to.x, top, to.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2f, 0.6f);
        }
        void crowd(ServerPlayer tgt) {
            chat("§5<우민의 대마법사>", "「군중 소환」 — 우민들이여, 생각 없이 돌격하라!");
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5;
                Vec3 q = body.position().add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
                var z = EntityType.ZOMBIE_VILLAGER.create(l, EntitySpawnReason.MOB_SUMMONED);
                if (z == null) continue;
                z.snapTo(q.x, q.y, q.z, 0, 0);
                z.setCustomName(Component.literal("§7우민 #" + (R.nextInt(900) + 100)));
                z.setCustomNameVisible(true);
                var hp = z.getAttribute(Attributes.MAX_HEALTH); if (hp != null) hp.setBaseValue(12);
                z.setHealth(12);
                z.addTag("jjk.boss");
                z.setTarget(tgt);
                l.addFreshEntity(z);
                adds.add(z);
            }
            l.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2f, 0.7f);
        }
        void speech() {
            String[] L = {"생각은 마법사님이 대신 해드립니다", "질문은 금지! 의심은 죄악!", "책은 위험하다. 마도서는 나만 읽는다", "따르라. 그러면 편하다"};
            chat("§5<우민의 대마법사>", "「선동 연설」 — " + L[R.nextInt(L.length)]);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + t * 0.01;
                Holo.banner(l, tag, body.position().add(Math.cos(a) * 6, 4, Math.sin(a) * 6), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("「" + L[i] + "」", 0xB45CFF, true) + "]}", 1.4, 80);
            }
            for (ServerPlayer p : players(24)) {   // 연설에 끌려감
                Vec3 d = body.position().subtract(p.position()).normalize();
                p.setDeltaMovement(d.scale(0.9).add(0, 0.3, 0)); p.hurtMarked = true;
            }
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(body.position(), body.position()).inflate(30), x -> x instanceof net.minecraft.world.entity.monster.Enemy)) {
                e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 0));
                e.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0));
            }
            Holo.sfx(l, body.position(), "choir", 2f, 0.6f);
        }
        void books(ServerPlayer tgt) {
            chat("§5<우민의 대마법사>", "「지식 폭발」 — 읽지 않을 거라면 맞아라.");
            rain(tgt.position(), phase == 1 ? 8 : 14, 6, "minecraft:bookshelf", 0xB45CFF, 11f);
        }

        @Override void onDeath() {
            Vec3 c = body.position().add(0, 2, 0);
            Holo.explosion(l, tag + "x", c, 8, 0xB45CFF);
            Holo.shock(l, tag + "x", c, 24, 0xB45CFF, 14);
            caption("§a§l우민 탈출\n§f「…생각하는 자들이… 이렇게 많았다니…」");
            for (ServerPlayer p : players(80)) { GuardMeta.send(p, 4, "우민의 대마법사 격파"); IQ.remove(p.getUUID()); p.sendSystemMessage(Component.literal("§a당신의 IQ가 원래대로 돌아왔다.")); }
            giveCmd("jjk:golden_clasp 2");
            drop(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
            drop(new ItemStack(Items.ENCHANTED_BOOK, 3));
            drop(new ItemStack(Items.EXPERIENCE_BOTTLE, 32));
            Holo.sfx(l, c, "choir", 3f, 1.0f);
        }
    }
}
