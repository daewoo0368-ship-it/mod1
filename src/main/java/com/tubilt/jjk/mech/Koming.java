package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkItems;
import com.tubilt.jjk.JjkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.*;

/**
 * 코밍 (Koming) — 급식 술식 일체.
 *  급식안(패시브: 영적 시야·능력자 감지·루카력 분석/조절·전투 예측) / 식(吸) / 교(膠) / 무려(n+2)끼 / 레고사주자 / 급식한 /
 *  무료훈수 / 점심시간 유아독존 / 술력 유(有) / 뇌 파괴와 재생 / 무량공처 대응 / 영역전개 페널티(술식 5분·영역 90분)
 * 루카력 = 주력(gojo.ce 점수). 급식안을 지니면 소모량이 거의 0 (5%).
 * 페널티 남은 시간은 점수판(koming.tlock / koming.dlock, 틱)에 저장 → 재접속·재시작해도 유지.
 */
public final class Koming {
    private Koming() {}

    public static final String[] IDS = {"sik", "gyo", "muryeo", "lego", "shield", "hunsu", "lunch", "yu", "brain"};
    static final String[] NAMES = {"술식순전 「식(吸)」", "술식반전 「교(膠)」", "급식 「무려3끼」", "술식전개 「레고사주자」", "급식한",
            "술식전개 「무료훈수」", "점심시간 유아독존", "술력 「유(有)」", "뇌 파괴와 재생"};
    static final int SIK = 0, GYO = 1, MURYEO = 2, LEGO = 3, SHIELD = 4, HUNSU = 5, LUNCH = 6, YU = 7, BRAIN = 8;
    static final int[] COST = {80, 100, 250, 400, 50, 400, 0, 60, 0};
    static final int[] CD = {30, 40, 80, 0, 10, 0, 3600, 20, 100};
    static final int TECH_LOCK = 20 * 60 * 5, DOMAIN_LOCK = 20 * 60 * 90;
    static final int DOMAIN_TIME = 240, DOMAIN_R = 200;
    static final int BLUE = 0x3A7BFF, RED = 0xFF2A2A, PURPLE = 0xB050FF, GOLD = 0xFFD84A, CYAN = 0x55FFFF;

    static final class State {
        final UUID id; final String tag;
        final int[] cd = new int[IDS.length];
        int combo = 0; long lastMuryeo = -9999;
        boolean shield, yu;
        int lastBlockFx = 0, uv = 0;
        final List<Fx> fx = new ArrayList<>();
        final List<Object[]> later = new ArrayList<>();   // {int ticks, Runnable}
        final Map<Entity, Integer> frozen = new HashMap<>();
        State(UUID id) { this.id = id; this.tag = "jjk.kom" + Integer.toHexString(id.hashCode() & 0xFFFFFF); }
        void later(int t, Runnable r) { later.add(new Object[]{t, r}); }
    }
    interface Fx {
        boolean tick(ServerLevel l, ServerPlayer p, State s);
        /** 시전자 사망·이탈로 강제 종료될 때 정리. */
        default void abort(ServerLevel l, ServerPlayer p, State s) {}
    }
    /** 영역 연출 수명 (해제할 때까지 유지 → 해제 시 killOwner 로 정리). */
    static final int LIFE_INF = 20 * 60 * 60 * 24;
    static final String[] CHANT_SIK = {"점심", "범람", "4교시의 긴장"};
    static final String[] CHANT_GYO = {"급식", "망각괴", "점심의 기둥"};
    static final String[] CHANT_MURYEO = {"급식", "점심", "수학과 국어", "6교시의 틈새"};

    static final Map<UUID, State> STATES = new HashMap<>();
    /** 무료훈수 후유증: 대상 → 풀리는 게임 시각 */
    static final Map<UUID, Long> PARALYZED = new HashMap<>();
    static long now = 0;

    static State st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), State::new); }

    // ═════════════════════════ 아이템 · 판정
    static boolean isKomingItem(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().startsWith("koming_") && !key.getPath().equals("koming_key");
    }
    static boolean has(Player p, net.minecraft.world.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }
    /** 코밍인가: 급식안 또는 코밍 술식 아이템을 지님. */
    public static boolean isKoming(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isKomingItem(inv.getItem(i))) return true;
        return false;
    }
    static boolean eye(Player p) { return has(p, JjkItems.KOMING_EYE.get()); }

    /** 우클릭 아이템 → true 면 처리함. */
    public static boolean onUse(Player ent, ItemStack st) {
        if (!isKomingItem(st)) return false;
        if (!(ent instanceof ServerPlayer p)) return true;
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().substring("koming_".length());
        if (path.equals("eye")) { analyze(p); return true; }
        cast(p, path);
        return true;
    }

    // ═════════════════════════ 루카력(주력)·점수판
    static Objective obj(MinecraftServer s, String n) { return s.getScoreboard().getObjective(n); }
    static int score(ServerPlayer p, String n, int def) {
        Objective o = obj(p.level().getServer(), n);
        if (o == null) return def;
        var i = p.level().getServer().getScoreboard().getPlayerScoreInfo(p, o);
        return i == null ? def : i.value();
    }
    static void setScore(ServerPlayer p, String n, int v) {
        Scoreboard sb = p.level().getServer().getScoreboard();
        Objective o = sb.getObjective(n);
        if (o == null) {
            if (!n.startsWith("koming.")) return;
            o = sb.addObjective(n, ObjectiveCriteria.DUMMY, Component.literal(n), ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        sb.getOrCreatePlayerScore(p, o).set(v);
    }
    static int cost(ServerPlayer p, int idx) { return eye(p) ? (int) Math.ceil(COST[idx] * 0.05) : COST[idx]; }
    static boolean spend(ServerPlayer p, int amount) {
        if (amount <= 0 || obj(p.level().getServer(), "gojo.ce") == null) return true;
        int ce = score(p, "gojo.ce", 0);
        if (ce < amount) return false;
        setScore(p, "gojo.ce", ce - amount);
        return true;
    }
    static int tlock(ServerPlayer p) { return score(p, "koming.tlock", 0); }
    static int dlock(ServerPlayer p) { return score(p, "koming.dlock", 0); }
    static String mmss(int ticks) { int s = (ticks + 19) / 20; return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60); }
    static void bar(ServerPlayer p, String msg) { p.displayClientMessage(Component.literal(msg), true); }

    // ═════════════════════════ 시전
    public static void cast(ServerPlayer p, String id) {
        int idx = Arrays.asList(IDS).indexOf(id);
        if (idx < 0) return;
        if (!isKoming(p)) { bar(p, "§7코밍만 쓸 수 있다 §8(급식안 또는 코밍 술식 아이템 필요)"); return; }
        if (p.getTags().contains("jjk.hunsu")) { bar(p, "§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"); return; }
        State s = st(p);
        ServerLevel l = (ServerLevel) p.level();
        // 술식전개는 같은 술식을 다시 쓰면 해제 (그 전까지 계속 유지)
        if (idx == LEGO || idx == HUNSU) for (Fx f : s.fx) {
            if (idx == LEGO && f instanceof Lego lg && !lg.stop) { lg.stop = true; return; }
            if (idx == HUNSU && f instanceof Hunsu h && !h.stop) { h.stop = true; return; }
        }
        if (tlock(p) > 0 && idx != BRAIN) { bar(p, "§c술식 담당 뇌 과부하 §f" + mmss(tlock(p)) + " §7— 「뇌 파괴와 재생」으로 해소"); return; }
        if ((idx == LEGO || idx == HUNSU) && dlock(p) > 0) { bar(p, "§c영역전개 불가 §f" + mmss(dlock(p))); return; }
        if ((idx == LEGO || idx == HUNSU) && s.fx.stream().anyMatch(f -> f instanceof Lego || f instanceof Hunsu)) { bar(p, "§7이미 다른 영역이 펼쳐져 있다 — 먼저 해제"); return; }
        if (s.fx.stream().anyMatch(f -> f instanceof Chant)) { bar(p, "§7영창 중"); return; }
        if (s.cd[idx] > 0) { bar(p, String.format(Locale.ROOT, "§7%s 대기 %.1f초", NAMES[idx], s.cd[idx] / 20f)); return; }
        boolean toggleOff = (idx == SHIELD && s.shield) || (idx == YU && s.yu);
        if (!toggleOff && !spend(p, cost(p, idx))) { bar(p, "§c루카력이 부족하다 §7(필요 " + cost(p, idx) + ")"); return; }
        s.cd[idx] = CD[idx];
        // 웅크리고 식·교·무려 → 영창 후 강화판 (루카력 한 번 더 소모)
        if ((idx == SIK || idx == GYO || idx == MURYEO) && p.isShiftKeyDown()) {
            if (spend(p, cost(p, idx))) {
                String[] lines = idx == SIK ? CHANT_SIK : idx == GYO ? CHANT_GYO : CHANT_MURYEO;
                int col = idx == SIK ? BLUE : idx == GYO ? RED : PURPLE;
                s.fx.add(new Chant(idx, lines, col));
                return;
            }
            bar(p, "§7루카력이 모자라 영창 없이 시전");
        }
        switch (idx) {
            case SIK -> { s.fx.add(new Sik(aim(p, 18, 1.5), false)); bar(p, "§b식(吸) §7— 열량 제거 → 보충 작용 → 공간 흡수"); }
            case GYO -> { s.fx.add(new Gyo(p, false)); bar(p, "§c교(膠) §7— 열량 증폭 → 공간 밀어내기 → 과부하"); }
            case MURYEO -> {
                s.combo = now - s.lastMuryeo <= 200 ? s.combo + 1 : 1;
                s.lastMuryeo = now;
                s.fx.add(new Muryeo(p, s.combo, false));
            }
            case LEGO -> s.fx.add(new Lego(p));
            case SHIELD -> {
                s.shield = !s.shield;
                bar(p, s.shield ? "§b급식한 §f켜짐 §7— 존재와 부재를 0.000001초마다 반복" : "§7급식한 꺼짐");
                Holo.sfx(l, p.position(), "magic", 1.2f, s.shield ? 1.4f : 0.8f);
                if (s.shield) Holo.bubble(l, s.tag, () -> p.position(), 1.6, 0x9FD8FF, 30);
            }
            case HUNSU -> s.fx.add(new Hunsu(p));
            case LUNCH -> lunch(l, p, s);
            case YU -> {
                s.yu = !s.yu;
                bar(p, s.yu ? "§e술력 「유(有)」 §f— 술식에 압도적인 존재 부여 · 방어 관통 · 회복" + (eye(p) ? " · §b전투 예측" : "") : "§7술력 유 해제");
                Holo.pillar(l, s.tag, p.position(), 6, 2.4, GOLD, 4, 4, 8);
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, s.yu ? 1.4f : 0.7f);
            }
            case BRAIN -> brain(l, p, s, true);
            default -> { }
        }
    }

    static Vec3 aim(ServerPlayer p, double range, double back) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return eye.add(look.scale(range));
        return hit.getLocation().subtract(look.scale(back));
    }

    // ═════════════════════════ 대상 · 피해
    static boolean foe(Entity e, ServerPlayer p) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player pl) return !pl.isCreative() && !pl.isSpectator();
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p);
    }
    static List<LivingEntity> foes(ServerLevel l, ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), x -> foe(x, p)))
            if (e.position().distanceToSqr(c) <= r * r) out.add(e);
        return out;
    }
    static void hurt(ServerLevel l, ServerPlayer p, State s, LivingEntity e, float dmg) {
        if (e == null || e == p || !e.isAlive()) return;
        DamageSource src = s.yu ? l.damageSources().indirectMagic(p, p) : l.damageSources().playerAttack(p);   // 유: 방어 관통
        float before = e.getHealth() + e.getAbsorptionAmount();
        e.invulnerableTime = 0;
        e.hurtServer(l, src, dmg * (s.yu ? 1.5f : 1f));
        float dealt = before - (e.isAlive() ? e.getHealth() + e.getAbsorptionAmount() : 0);
        if (dealt > 0.05f) Holo.dmgNumber(l, s.tag, e.position().add(0, e.getBbHeight() + 0.2, 0), dealt, 0);
    }
    static void push(Entity e, Vec3 v) { e.setDeltaMovement(v); e.hurtMarked = true; }
    static boolean griefOn(ServerLevel l) { return MechManager.griefOn(l.getServer()); }
    /** 구 안의 블록을 드롭 없이 지움 (부술 수 없는 블록 제외). */
    static void erase(ServerLevel l, Vec3 c, double r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
            if (x * x + y * y + z * z > r * r) continue;
            m.set(c.x + x, c.y + y, c.z + z);
            if (m.getY() < l.getMinY() || m.getY() > l.getMaxY()) continue;
            var bs = l.getBlockState(m);
            if (bs.isAir() || bs.getDestroySpeed(l, m) < 0) continue;
            l.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    // ═════════════════════════ 영창: 한 구절씩 → 마지막 구절에서 강화판 발동
    static final class Chant implements Fx {
        final int idx, col; final String[] lines; int t = 0;
        static final int STEP = 12;
        Chant(int idx, String[] lines, int col) { this.idx = idx; this.lines = lines; this.col = col; }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) {
                Holo.circle(l, s.tag, p.position(), 2.6, col, lines.length * STEP + 14, 0);
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, lines.length * STEP + 6, 2, false, false, false));
            }
            if ((t - 1) % STEP == 0 && (t - 1) / STEP < lines.length) {
                int i = (t - 1) / STEP;
                boolean last = i == lines.length - 1;
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k <= i; k++) sb.append(k == 0 ? "" : " — ").append(lines[k]);
                bar(p, (idx == SIK ? "§b" : idx == GYO ? "§c" : "§d") + (last ? "§l" : "") + sb);
                Vec3 at = p.getEyePosition().add(0, 0.9 + i * 0.05, 0).add(flatLook(p).scale(1.6))
                        .add(new Vec3(-flatLook(p).z, 0, flatLook(p).x).scale((i - (lines.length - 1) / 2.0) * 0.9));
                Holo.banner(l, s.tag, at, "{text:\"\",extra:[" + Holo.tx(lines[i], last ? 0xFFFFFF : col, true) + "]}", last ? 1.0 : 0.75, lines.length * STEP - i * STEP + 14);
                Holo.sfx(l, p.position(), last ? "deep" : "drum", 2f, last ? 0.6f : 0.8f + i * 0.1f);
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2f, 0.6f + i * 0.15f);
                l.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.8, 0.8, 0.8, 0.6);
            }
            if (t == lines.length * STEP + 2) {
                switch (idx) {
                    case SIK -> { s.fx.add(new Sik(aim(p, 24, 2), true)); bar(p, "§b§l점심-범람-4교시의 긴장 §f— 식(吸)"); }
                    case GYO -> { s.fx.add(new Gyo(p, true)); bar(p, "§c§l급식-망각괴-점심의 기둥 §f— 교(膠)"); }
                    default -> {
                        s.combo = now - s.lastMuryeo <= 200 ? s.combo + 1 : 1;
                        s.lastMuryeo = now;
                        s.fx.add(new Muryeo(p, s.combo, true));
                    }
                }
                return true;
            }
            return false;
        }
    }

    // ═════════════════════════ 2. 술식순전 — 식(吸)
    static final class Sik implements Fx {
        final Vec3 c; final boolean boost; int t = 0, life = 100; boolean done; String vt = null;
        Sik(Vec3 c, boolean boost) { this.c = c; this.boost = boost; }
        /** 자폭에 쓰이면 식의 구·고리 연출을 즉시 지운다. */
        void vanish() { done = true; if (vt != null) Holo.killOwner(vt); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1 && !done) {
                if (s.yu) life = 140;
                if (boost) life += 60;
                vt = s.tag + "s" + Integer.toHexString(System.identityHashCode(this));
                Holo.orb(l, vt, c, boost ? 3.8 : 2.4, BLUE, 6, life - 12, 6);
                Holo.sprite(l, vt, c, "ring", BLUE, "center", Holo.bb(0.1, 0, Vec3.ZERO), life, 0).key(1, Holo.bb(boost ? 10 : 6, 0, Vec3.ZERO), 6).spin(-6);
                if (boost) Holo.sprite(l, vt, c, "runering", 0xBFE0FF, "center", Holo.bb(0.1, 0, Vec3.ZERO), life, 0).key(1, Holo.bb(14, 0, Vec3.ZERO), 8).spin(4);
                Holo.sfx(l, c, "magic", 2f, 0.55f); Holo.sfx(l, c, "deep", 2f, 0.8f);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.6f);
            }
            if (done) return true;
            if (t % 10 == 2) Holo.ring(l, s.tag, c, boost ? 17 : 11, 0.4, 20, 0.15, 0.04, BLUE, 10, 0, 2, (t * 0.37) % 3, 6, true);
            l.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 30, 5, 5, 5, 1.2);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.02);
            double R = boost ? 26 : 16, pull = (s.yu ? 0.6 : 0.42) * (boost ? 1.35 : 1);
            for (Entity e : l.getEntities((Entity) null, new AABB(c, c).inflate(R), x -> x != p && !(x instanceof Display) && !(x instanceof Marker) && !(x instanceof ArmorStand) && x.isAlive())) {
                if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                Vec3 d = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                double dist = d.length();
                if (dist > R || dist < 1e-3) continue;
                push(e, e.getDeltaMovement().scale(0.55).add(d.normalize().scale(pull * (1 - dist / R) + 0.06)));
                if (dist < (boost ? 4.5 : 3.0) && t % 5 == 0 && e instanceof LivingEntity le) hurt(l, p, s, le, boost ? 12f : 7f);
            }
            if (griefOn(l) && t % 2 == 0) erase(l, c, Math.min(boost ? 7.0 : 4.5, 1.5 + t * (boost ? 0.05 : 0.03)));
            if (t >= life) {
                Holo.flare(l, s.tag, c, 9, BLUE, 10);
                Holo.sfx(l, c, "shatter", 2f, 0.6f);
                return true;
            }
            return false;
        }
    }

    // ═════════════════════════ 3. 술식반전 — 교(膠)
    static final class Gyo implements Fx {
        Vec3 pos, dir; final boolean boost; final double K; int t = 0; double traveled = 0;
        Gyo(ServerPlayer p, boolean boost) { this.boost = boost; K = boost ? 1.7 : 1; dir = p.getLookAngle(); pos = p.getEyePosition().add(dir.scale(1.4)); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) {
                Holo.glow(l, s.tag, pos, 1.6, RED, 2, 2, 4);
                Holo.sfx(l, pos, "laser", 2f, 1.5f);
                l.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2f, 0.6f);
            }
            double speed = (s.yu ? 6.5 : 4.8) * (boost ? 1.3 : 1);
            Vec3 prev = pos;
            for (int k = 0; k < 4; k++) {
                Vec3 next = pos.add(dir.scale(speed / 4));
                BlockHitResult hit = l.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (hit.getType() != HitResult.Type.MISS) { boom(l, p, s, hit.getLocation(), K); return true; }
                for (LivingEntity e : foes(l, p, next, boost ? 2.6 : 1.8)) { boom(l, p, s, e.position().add(0, e.getBbHeight() / 2, 0), K); return true; }
                // 순전 식에 반전 교를 꽂음 → 자폭 무려(n+2)끼
                for (Fx f : s.fx) if (f instanceof Sik sk && !sk.done && next.distanceTo(sk.c) < (sk.boost ? 6.5 : 4.5)) {
                    sk.vanish();
                    s.combo = now - s.lastMuryeo <= 200 ? s.combo + 1 : 1;
                    s.lastMuryeo = now;
                    s.fx.add(new SelfMuryeo(sk.c, s.combo, boost || sk.boost));
                    return true;
                }
                pos = next;
            }
            traveled += speed;
            Holo.sprite(l, s.tag, pos, "orb", RED, "center", Holo.bb(2.6 * K, 0, Vec3.ZERO), 3, 0);
            Holo.sprite(l, s.tag, pos, "orb", 0xFFFFFF, "center", Holo.bb(1.0 * K, 0, Vec3.ZERO), 3, 0);
            Holo.beamGlow(l, s.tag, prev, pos, 1.4 * K, RED, 0, 1, 4);
            l.sendParticles(new DustParticleOptions(0xFF3030, 2.0f), pos.x, pos.y, pos.z, 6, 0.3, 0.3, 0.3, 0);
            // 공간 밀어내기: 궤도 옆 대상을 옆으로 밀침
            for (LivingEntity e : foes(l, p, pos, 4)) {
                Vec3 side = e.position().subtract(pos);
                side = side.subtract(dir.scale(side.dot(dir)));
                if (side.lengthSqr() > 1e-4) push(e, e.getDeltaMovement().add(side.normalize().scale(1.1)).add(0, 0.3, 0));
            }
            if (traveled > 90 * K) { boom(l, p, s, pos, K); return true; }
            return false;
        }
        static void boom(ServerLevel l, ServerPlayer p, State s, Vec3 c, double k) {
            double r = 6 * k;
            Holo.explosion(l, s.tag, c, 5.5 * k, RED);
            Holo.shockTex(l, s.tag, c, 14 * k, RED, 10);
            Holo.flare(l, s.tag, c, 10 * k, 0xFF6040, 8);
            for (LivingEntity e : foes(l, p, c, r)) {
                hurt(l, p, s, e, (float) (34 * k));
                Vec3 d = e.position().subtract(c);
                double f = 2.4 * k * (1 - Math.min(1, d.length() / r)) + 0.4;
                push(e, (d.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : d.normalize()).scale(f).add(0, 0.6, 0));
            }
            if (griefOn(l) && c.distanceTo(p.position()) > 7) l.explode(p, c.x, c.y, c.z, (float) (4.5 * k), false, Level.ExplosionInteraction.TNT);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3f, 0.7f);
            Titan.shake(l, c, 60, k > 1.2);
        }
    }

    // ═════════════════════════ 4. 급식 — 무려(n+2)끼
    static final class Muryeo implements Fx {
        final int n; final double R, range; int t = 0; Vec3 cur, dir, M, bp, rp; double traveled = 0;
        static final int CHARGE = 26;
        final boolean boost;
        Muryeo(ServerPlayer p, int n, boolean boost) {
            this.n = n; this.boost = boost;
            double k = Math.min(3.0, 1 + 0.12 * (n - 1));   // 연속 사용 배율 (설정 미정 → 자체 수치: 회당 +12%, 최대 3배)
            R = 3.2 * k * (boost ? 1.45 : 1); range = 70 * Math.min(2.0, k) * (boost ? 1.5 : 1);
        }
        String name() { return "무려" + (n + 2) + "끼" + (boost ? " (영창)" : ""); }

        /** 조준을 따라가는 합류점 + 나선을 그리며 좁혀 오는 청·적 구체 위치. */
        void aimUpdate(ServerPlayer p) {
            dir = p.getLookAngle();
            M = p.getEyePosition().add(dir.scale(2.6));
            cur = M;
            Vec3 right = dir.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(dir).normalize();
            double u = Math.min(1, (t - 1) / (double) (CHARGE - 2)), e = u * u * (3 - 2 * u);
            double rad = 2.0 * Math.pow(1 - e, 1.3) + 0.02;
            double ang = 0.35 * t + 7.0 * u * u;            // 점점 빨리 돌며 다가감
            Vec3 off = right.scale(Math.cos(ang) * rad).add(up.scale(Math.sin(ang) * rad * 0.7)).add(dir.scale(-0.4 * (1 - e)));
            bp = M.add(off);
            rp = M.subtract(off);
        }

        void orbFollow(ServerLevel l, State s, java.util.function.Supplier<Vec3> at, int rgb, String glass) {
            Holo.sprite(l, s.tag, at.get(), "orb", rgb, "center", Holo.bb(0.01, 0, Vec3.ZERO), CHARGE + 1, 1).follow(at, Vec3.ZERO)
                    .key(1, Holo.bb(2.4, 0, Vec3.ZERO), 5).key(14, Holo.bb(1.7, 0, Vec3.ZERO), 8).key(CHARGE - 3, Holo.bb(0.01, 0, Vec3.ZERO), 3);
            Holo.sprite(l, s.tag, at.get(), "orb", 0xFFFFFF, "center", Holo.bb(0.01, 0, Vec3.ZERO), CHARGE + 1, 1).follow(at, Vec3.ZERO)
                    .key(1, Holo.bb(0.9, 0, Vec3.ZERO), 5).key(CHARGE - 3, Holo.bb(0.01, 0, Vec3.ZERO), 3);
            double[][] Rm = Holo.mul(Holo.rotX(0.5), Holo.rotZ(0.4));
            Holo.block(l, s.tag, at.get(), glass, rgb, Holo.m(Rm, 0.01, 0.01, 0.01, Vec3.ZERO, true), CHARGE + 1, 1).follow(at, Vec3.ZERO)
                    .key(1, Holo.m(Rm, 0.55, 0.55, 0.55, Vec3.ZERO, true), 5).key(CHARGE - 3, Holo.m(Rm, 0.01, 0.01, 0.01, Vec3.ZERO, true), 3).spin(18).wobble(30, 0.3);
        }

        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t <= CHARGE) aimUpdate(p);
            if (t == 1) {
                orbFollow(l, s, () -> bp, BLUE, "minecraft:blue_stained_glass");
                orbFollow(l, s, () -> rp, RED, "minecraft:red_stained_glass");
                // 합류점의 보라빛: 두 구체가 가까워질수록 서서히 차오르다가, 합쳐지는 순간 본체로 커짐 (발사 후에도 그대로 이어짐)
                int life = CHARGE + (int) (range / 1.6) + 8;
                Holo.sprite(l, s.tag, cur, "orb", PURPLE, "center", Holo.bb(0.01, 0, Vec3.ZERO), life, 1).follow(() -> cur, Vec3.ZERO)
                        .key(8, Holo.bb(R * 0.7, 0, Vec3.ZERO), CHARGE - 10).key(CHARGE, Holo.bb(R * 3.0, 0, Vec3.ZERO), 4);
                Holo.sprite(l, s.tag, cur, "orb", 0xFFFFFF, "center", Holo.bb(0.01, 0, Vec3.ZERO), life, 1).follow(() -> cur, Vec3.ZERO)
                        .key(14, Holo.bb(R * 0.3, 0, Vec3.ZERO), CHARGE - 15).key(CHARGE, Holo.bb(R * 1.3, 0, Vec3.ZERO), 4);
                Holo.sprite(l, s.tag, cur, "runering", 0xD090FF, "center", Holo.bb(0.01, 0, Vec3.ZERO), life, 1).follow(() -> cur, Vec3.ZERO)
                        .key(16, Holo.bb(R * 1.2, 0, Vec3.ZERO), CHARGE - 17).key(CHARGE, Holo.bb(R * 3.6, 0, Vec3.ZERO), 6).spin(9);
                for (int i = 0; i < 2; i++) {
                    double[][] Rm = Holo.mul(Holo.rotX(0.6 * i + 0.3), Holo.rotZ(0.5 * i));
                    double sz = R * (i == 0 ? 0.9 : 1.4);
                    Holo.block(l, s.tag, cur, i == 0 ? "minecraft:white_concrete" : "minecraft:purple_stained_glass", PURPLE, Holo.m(Rm, 0.01, 0.01, 0.01, Vec3.ZERO, true), life, 1)
                            .follow(() -> cur, Vec3.ZERO).key(CHARGE - 2, Holo.m(Rm, sz, sz, sz, Vec3.ZERO, true), 6).spin(i == 0 ? 14 : -11).wobble(30, 0.2);
                }
                Holo.banner(l, s.tag, p.getEyePosition().add(0, 1.4, 0).add(dir.scale(2)), "{text:\"\",extra:[" + Holo.tx(name(), PURPLE, true) + "]}", 1.2 + n * 0.08, 34);
                bar(p, "§d" + name() + " §7— 압도적인 부재 × 압도적인 존재 → 가상의 열량");
                Holo.sfx(l, M, "charge", 2.5f, 0.7f);
            }
            if (t < CHARGE) {
                double u = t / (double) CHARGE;
                // 나선 궤적 잔광 + 합류점으로 빨려드는 입자
                l.sendParticles(new DustParticleOptions(0x4A8BFF, 1.3f), bp.x, bp.y, bp.z, 3, 0.08, 0.08, 0.08, 0);
                l.sendParticles(new DustParticleOptions(0xFF3A3A, 1.3f), rp.x, rp.y, rp.z, 3, 0.08, 0.08, 0.08, 0);
                if (u > 0.35) l.sendParticles(ParticleTypes.PORTAL, M.x, M.y, M.z, (int) (6 + 20 * u), 1.6, 1.6, 1.6, 0.6);
                if (u > 0.55) l.sendParticles(ParticleTypes.WITCH, M.x, M.y, M.z, 4, 0.3, 0.3, 0.3, 0.02);
                if (t % 3 == 0) l.playSound(null, M.x, M.y, M.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, (float) (0.5 + 1.3 * u));
                if (t == 14 || t == 19 || t == 23) Holo.lightning(l, s.tag, bp, rp, 0xD090FF, 0.06, 3);
                if (t == 18) Holo.sfx(l, M, "choir", 2.5f, 0.7f);
                return false;
            }
            if (t == CHARGE) {   // 합쳐지는 순간: 빛이 안으로 빨려들었다가 한 박자 뒤 터지듯 퍼짐
                Holo.flare(l, s.tag, M, R * 2.2, PURPLE, 10);
                double[][] T = Holo.mul(Holo.facing(dir), Holo.rotX(Math.PI / 2));
                Holo.ringT(l, s.tag, M, 0.4, R * 2.6, 24, 0.35, 0.05, PURPLE, 6, 0, 5, T, 0, true);
                Holo.ringT(l, s.tag, M, R * 3.5, 0.3, 20, 0.2, 0.04, 0xFFFFFF, 6, 0, 2, T, 0, true);
                l.sendParticles(ParticleTypes.END_ROD, M.x, M.y, M.z, 40, 0.2, 0.2, 0.2, 0.35);
                Holo.slowmo(l, 6, 10);
                Holo.sfx(l, M, "deep", 3f, 0.5f); Holo.sfx(l, M, "boom", 3f, 0.4f);
                return false;
            }
            Vec3 prev = cur;
            cur = cur.add(dir.scale(1.6));
            traveled += 1.6;
            boolean clear = cur.distanceTo(p.getEyePosition()) > R + 1.5;   // 시전자 바로 앞에서는 지우지 않음
            // 소거: 존재 자체를 지움 (드롭·잔해 없음)
            if (clear) for (Entity e : l.getEntities((Entity) null, new AABB(cur, cur).inflate(R), x -> x != p && x.isAlive() && !(x instanceof Display) && !(x instanceof Marker))) {
                if (e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(cur) > R + e.getBbWidth()) continue;
                if (e instanceof Player pl) {
                    if (pl.isCreative() || pl.isSpectator()) continue;
                    pl.invulnerableTime = 0;
                    pl.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                } else {
                    if (e instanceof ArmorStand && !e.getTags().isEmpty()) continue;   // 컷신 배우 보호
                    Holo.glow(l, s.tag, e.position().add(0, e.getBbHeight() / 2, 0), Math.max(0.6, e.getBbHeight() * 0.6), PURPLE, 1, 0, 5);
                    e.discard();
                }
            }
            if (clear && griefOn(l)) erase(l, cur, R);
            if (t % 2 == 0) Holo.beamGlow(l, s.tag, prev.subtract(dir.scale(1.6)), cur, R * 1.6, 0x7020C0, 0, 2, 8);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, cur.x, cur.y, cur.z, 20, R * 0.6, R * 0.6, R * 0.6, 0.05);
            l.sendParticles(ParticleTypes.END_ROD, cur.x, cur.y, cur.z, 6, R * 0.5, R * 0.5, R * 0.5, 0.02);
            if (t % 6 == 0) Holo.sfx(l, cur, "whoosh", 2f, 0.5f);
            if (traveled >= range || cur.y < l.getMinY() - 10 || cur.y > l.getMaxY() + 30) {
                Holo.flare(l, s.tag, cur, R * 4, PURPLE, 12);
                Holo.sfx(l, cur, "shatter", 2.5f, 0.5f);
                return true;
            }
            return false;
        }
    }

    // ═════════════════════════ 자폭 무려(n+2)끼: 순전 식 한가운데에 반전 교를 꽂아 그 자리에서 터뜨림 (일반보다 강함)
    static final class SelfMuryeo implements Fx {
        final Vec3 c; final int n; final boolean boost; final double RMAX; int t = 0;
        static final int FUSE = 14, GROW = 18;
        SelfMuryeo(Vec3 c, int n, boolean boost) {
            this.c = c; this.n = n; this.boost = boost;
            double k = Math.min(3.0, 1 + 0.12 * (n - 1));
            RMAX = 13 * k * (boost ? 1.4 : 1);
        }
        String name() { return "자폭 무려" + (n + 2) + "끼" + (boost ? " (영창)" : ""); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            if (t == 1) {
                bar(p, "§d§l" + name() + " §7— 순전 식 × 반전 교, 그 자리에서 가상의 열량 폭주");
                Holo.banner(l, s.tag, c.add(0, 4, 0), "{text:\"\",extra:[" + Holo.tx(name(), PURPLE, true) + "]}", 2.0 + n * 0.1, 40);
                // 청·적이 서로를 삼키며 소용돌이 → 보라 핵
                Holo.sprite(l, s.tag, c, "orb", BLUE, "center", Holo.bb(5, 0, Vec3.ZERO), FUSE + 2, 0).key(1, Holo.bb(2, 3, Vec3.ZERO), FUSE).spin(20);
                Holo.sprite(l, s.tag, c, "orb", RED, "center", Holo.bb(5, 1, Vec3.ZERO), FUSE + 2, 0).key(1, Holo.bb(2, -3, Vec3.ZERO), FUSE).spin(-20);
                Holo.sprite(l, s.tag, c, "orb", PURPLE, "center", Holo.bb(0.1, 0, Vec3.ZERO), FUSE + GROW + 30, 0)
                        .key(1, Holo.bb(4, 0, Vec3.ZERO), FUSE).key(FUSE + 1, Holo.bb(RMAX * 2.6, 0, Vec3.ZERO), GROW).key(FUSE + GROW + 8, Holo.bb(0.1, 0, Vec3.ZERO), 18);
                Holo.sprite(l, s.tag, c, "orb", 0xFFFFFF, "center", Holo.bb(0.1, 0, Vec3.ZERO), FUSE + GROW + 30, 0)
                        .key(4, Holo.bb(1.5, 0, Vec3.ZERO), FUSE - 4).key(FUSE + 1, Holo.bb(RMAX * 1.2, 0, Vec3.ZERO), GROW).key(FUSE + GROW + 8, Holo.bb(0.1, 0, Vec3.ZERO), 18);
                Holo.sfx(l, c, "charge", 3f, 0.6f); Holo.sfx(l, c, "choir", 3f, 0.5f);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.6f);
            }
            if (t < FUSE) {
                l.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 60, 8, 8, 8, 2.0);
                if (t % 3 == 0) Holo.lightning(l, s.tag, c.add((t % 2 == 0 ? 1 : -1) * 4, 2, 0), c, 0xD090FF, 0.08, 3);
                return false;
            }
            if (t == FUSE) {
                Holo.flare(l, s.tag, c, RMAX * 2.5, PURPLE, 14);
                Holo.slowmo(l, 5, 16);
                Holo.shockTex(l, s.tag, c, RMAX * 3, PURPLE, 14);
                Holo.ring(l, s.tag, c, 1, RMAX * 2.2, 36, 0.8, 0.08, 0xD090FF, 12, 2, 8, 0, 4, true);
                Holo.ring(l, s.tag, c, 1, RMAX * 1.8, 30, 0.6, 0.06, 0xFFFFFF, 10, 2, 8, Math.PI / 2, -4, true);
                Holo.pillar(l, s.tag, c.subtract(0, RMAX, 0), RMAX * 6, RMAX * 0.5, PURPLE, 4, 10, 14);
                Holo.sfx(l, c, "boom", 3f, 0.3f); Holo.sfx(l, c, "deep", 3f, 0.4f); Holo.sfx(l, c, "impact", 3f, 0.4f);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 0.5f);
                Titan.shake(l, c, 120, true);
            }
            if (t >= FUSE && t <= FUSE + GROW) {
                double u = (t - FUSE) / (double) GROW, r = RMAX * (1 - Math.pow(1 - u, 2.2));
                boolean grief = griefOn(l);
                // 커지는 보라 구 안의 모든 존재를 소거 (시전자 제외 · 시전자 주변 2칸 블록 보호)
                for (Entity e : l.getEntities((Entity) null, new AABB(c, c).inflate(r + 2), x -> x != p && x.isAlive() && !(x instanceof Display) && !(x instanceof Marker))) {
                    if (e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(c) > r + e.getBbWidth()) continue;
                    if (e instanceof Player pl) {
                        if (pl.isCreative() || pl.isSpectator()) continue;
                        pl.invulnerableTime = 0;
                        pl.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                    } else {
                        if (e instanceof ArmorStand && !e.getTags().isEmpty()) continue;
                        e.discard();
                    }
                }
                if (grief) {
                    BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                    int R = (int) Math.ceil(r);
                    double r0 = Math.max(0, RMAX * (1 - Math.pow(1 - Math.max(0, u - 1.0 / GROW), 2.2)) - 1);   // 지난 틱 반경 바깥 껍질만
                    Vec3 pp = p.position();
                    for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
                        double d2 = x * x + y * y + z * z;
                        if (d2 > r * r || d2 < r0 * r0) continue;
                        m.set(c.x + x, c.y + y, c.z + z);
                        if (m.getY() < l.getMinY() || m.getY() > l.getMaxY()) continue;
                        if (Math.abs(m.getX() + 0.5 - pp.x) < 2 && Math.abs(m.getZ() + 0.5 - pp.z) < 2 && m.getY() >= pp.y - 2 && m.getY() <= pp.y + 3) continue;
                        var bs = l.getBlockState(m);
                        if (bs.isAir() || bs.getDestroySpeed(l, m) < 0) continue;
                        l.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
                // 소거 범위 밖에도 충격: 바깥 적을 밀쳐냄
                for (LivingEntity e : foes(l, p, c, r * 1.8)) {
                    Vec3 d = e.position().subtract(c);
                    push(e, (d.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : d.normalize()).scale(1.8).add(0, 0.5, 0));
                }
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, r * 0.6, r * 0.6, r * 0.6, 0.2);
                l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, r * 0.5, r * 0.5, r * 0.5, 0.3);
            }
            if (t == FUSE + GROW + 6) { Holo.flare(l, s.tag, c, RMAX * 1.5, 0xFFFFFF, 10); Holo.sfx(l, c, "shatter", 3f, 0.4f); }
            return t >= FUSE + GROW + 10;
        }
    }

    // ═════════════════════════ 영역 공통
    static double[][] basis(Vec3 F) {
        Vec3 X = new Vec3(-F.z, 0, F.x);   // 오른쪽
        return new double[][]{{X.x, 0, F.x}, {0, 1, 0}, {X.z, 0, F.z}};
    }
    static Vec3 flatLook(ServerPlayer p) {
        Vec3 f = p.getLookAngle().multiply(1, 0, 1);
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }
    /** 로컬 상자 (중심 cx,cy,cz · 크기) → 키 t0 부터 솟아오름. */
    static void box(ServerLevel l, String owner, Vec3 B, double[][] M, double cx, double cy, double cz, double sx, double sy, double sz, String block, int glow, int t0, int life) {
        Vec3 c = Holo.apply(M, new Vec3(cx, cy, cz));
        Holo.block(l, owner, B, block, glow, Holo.m(M, sx, 0.01, sz, c.add(0, -sy / 2, 0), true), life, 0)
                .key(t0, Holo.m(M, sx, sy, sz, c, true), 6).key(life - 10, Holo.m(M, sx * 0.01, sy * 0.01, sz * 0.01, c, true), 8);
    }
    static void endDomain(ServerLevel l, ServerPlayer p, State s, String name) {
        setScore(p, "koming.tlock", TECH_LOCK);
        setScore(p, "koming.dlock", DOMAIN_LOCK);
        bar(p, "§c" + name + " 종료 §7— 술식 담당 뇌 과부하: 술식 5분 · 영역 90분 불가 (뇌 파괴와 재생으로 즉시 회복)");
        Holo.sfx(l, p.position(), "glitch", 1.5f, 0.6f);
    }

    // ═════════════════════════ 5. 술식전개 — 레고사주자
    static final String[] LEGO_BLOCKS = {"minecraft:red_concrete", "minecraft:yellow_concrete", "minecraft:blue_concrete", "minecraft:lime_concrete", "minecraft:white_concrete", "minecraft:orange_concrete"};
    static final int[] LEGO_RGB = {0xD01010, 0xFFD000, 0x1E4FD8, 0x48C020, 0xFFFFFF, 0xFF8A00};
    static final class Lego implements Fx {
        final Vec3 B, F; final double[][] M; int t = 0; List<LivingEntity> targets = List.of();
        final Random rnd = new Random();
        boolean stop; String dt;
        Lego(ServerPlayer p) { F = flatLook(p); B = p.position().subtract(F.scale(9)); M = basis(F); }
        Vec3 top() { return B.add(0, 13, 0); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            int life = LIFE_INF;
            if (t == 1) {
                dt = s.tag + "d";
                Holo.banner(l, s.tag, p.getEyePosition().add(F.scale(3)).add(0, 1, 0), "{text:\"\",extra:[" + Holo.tx("술식전개 「레고사주자」", 0xFF3030, true) + "]}", 1.5, 50);
                bar(p, "§c레고사주자 §7— 레고의 고통과 피해의 열량을 증폭 §8(다시 쓰면 해제)");
                Holo.sfx(l, B, "deep", 3f, 0.5f); Holo.sfx(l, B, "drum", 3f, 0.6f);
                Holo.circle(l, dt, p.position(), 22, 0xFF3030, life, 0);
                // 신전: 3단 기단 + 기둥 + 2단 지붕 + 네 뿔
                box(l, dt, B, M, 0, 0.5, 0, 14, 1, 10, "minecraft:gray_concrete", -1, 2, life);
                box(l, dt, B, M, 0, 1.5, 0, 11, 1, 8, "minecraft:red_concrete", -1, 5, life);
                box(l, dt, B, M, 0, 2.5, 0, 8, 1, 6, "minecraft:yellow_concrete", -1, 8, life);
                for (int i = 0; i < 4; i++) {
                    double x = (i & 1) == 0 ? -3.2 : 3.2, z = (i & 2) == 0 ? -2.2 : 2.2;
                    box(l, dt, B, M, x, 5, z, 1, 4, 1, "minecraft:blue_concrete", -1, 11 + i, life);
                }
                box(l, dt, B, M, 0, 7.5, 0, 10, 1, 8, "minecraft:red_concrete", -1, 16, life);
                box(l, dt, B, M, 0, 8.5, 0, 8, 1, 6, "minecraft:yellow_concrete", -1, 18, life);
                box(l, dt, B, M, 0, 9.5, 0, 6, 1, 4, "minecraft:red_concrete", -1, 20, life);
                for (int i = -3; i <= 3; i += 2) for (int j = -2; j <= 2; j += 2)   // 돌기(스터드)
                    box(l, dt, B, M, i * 0.9, 10.2, j * 0.8, 0.7, 0.4, 0.7, "minecraft:white_concrete", -1, 22, life);
                // 정면 눈: 붉게 빛나는 두 블록
                box(l, dt, B, M, -1.2, 5.2, 3.3, 0.8, 0.5, 0.2, "minecraft:red_stained_glass", 0xFF0000, 24, life);
                box(l, dt, B, M, 1.2, 5.2, 3.3, 0.8, 0.5, 0.2, "minecraft:red_stained_glass", 0xFF0000, 24, life);
                // 네 뿔
                for (int i = 0; i < 4; i++) {
                    double sx = (i & 1) == 0 ? -1 : 1, sz = (i & 2) == 0 ? -1 : 1;
                    Vec3 a = new Vec3(sx * 3.5, 10, sz * 2.5), b = new Vec3(sx * 5.2, 13.5, sz * 3.6), c = new Vec3(sx * 4.6, 16.5, sz * 3.0);
                    Vec3 wa = Holo.apply(M, a), wb = Holo.apply(M, b), wc = Holo.apply(M, c);
                    Holo.block(l, dt, B, LEGO_BLOCKS[i], -1, Holo.bar(wa, wa, 0.01, 0.01, 0), life, 0).key(26, Holo.bar(wa, wb, 1.0, 1.0, 0), 8).key(life - 10, Holo.bar(wa, wa, 0.01, 0.01, 0), 8);
                    Holo.block(l, dt, B, LEGO_BLOCKS[(i + 2) % 4], -1, Holo.bar(wb, wb, 0.01, 0.01, 0), life, 0).key(32, Holo.bar(wb, wc, 0.7, 0.7, 0), 8).key(life - 10, Holo.bar(wb, wb, 0.01, 0.01, 0), 8);
                }
                Holo.text(l, dt, B.add(0, 18.5, 0), "{text:\"\",extra:[" + Holo.tx("레고사주자", 0xFF4040, true) + "]}", "center", 0x80000000, false,
                        Holo.m(Holo.I(), 6, 6, 6, Vec3.ZERO, false), life, 0);
                Titan.shake(l, B, 80, true);
            }
            if (t >= 30 && !stop) {
                if (t % 20 == 0 || targets.isEmpty()) targets = foes(l, p, B, DOMAIN_R);
                if (t % 2 == 0 && !targets.isEmpty()) {
                    for (int k = 0; k < Math.min(3, targets.size()); k++) {
                        LivingEntity e = targets.get(rnd.nextInt(targets.size()));
                        if (!e.isAlive()) continue;
                        fire(l, p, s, e);
                    }
                }
                if (t % 40 == 0) Holo.sfx(l, B, "drum", 2.5f, 0.8f);
            }
            if (stop) { collapse(l, s); endDomain(l, p, s, "레고사주자"); return true; }
            return false;
        }
        void collapse(ServerLevel l, State s) {
            if (dt != null) Holo.killOwner(dt);
            Holo.flare(l, s.tag, B.add(0, 8, 0), 18, 0xFF3030, 12);
            Holo.sfx(l, B, "shatter", 3f, 0.5f);
        }
        @Override public void abort(ServerLevel l, ServerPlayer p, State s) { collapse(l, s); }
        /** 블록 6개 → 십자 투사체 → 대상에게 내리꽂힘 (고통·피해의 열량을 강제 흡수). */
        void fire(ServerLevel l, ServerPlayer p, State s, LivingEntity e) {
            Vec3 tgt = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 from = tgt.add(top().subtract(tgt).normalize().scale(16)).add(0, 3, 0);
            int col = rnd.nextInt(LEGO_BLOCKS.length);
            Holo.beamGlow(l, s.tag, top(), from, 0.6, LEGO_RGB[col], 0, 1, 4);
            Vec3 d = tgt.subtract(from);
            double[][] R = Holo.facing(d.normalize());
            int[][] cells = {{0, 0}, {0, 1}, {0, -1}, {0, 2}, {1, 1}, {-1, 1}};   // 십자 (6칸)
            for (int[] c : cells) {
                Vec3 off = Holo.apply(R, new Vec3(c[0] * 0.55, c[1] * 0.55, 0));
                Holo.block(l, s.tag, from, LEGO_BLOCKS[col], -1, Holo.m(R, 0.5, 0.5, 0.5, off, true), 9, 0)
                        .key(1, Holo.m(R, 0.5, 0.5, 0.5, off.add(d), true), 5);
            }
            final LivingEntity target = e;
            State st = s;
            s.later(5, () -> {
                if (!target.isAlive()) return;
                hurt(l, p, st, target, 13f);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
                Holo.sparks(l, st.tag, tgt, 6, 1.2, LEGO_RGB[col], 8);
                l.playSound(null, tgt.x, tgt.y, tgt.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.5f, 0.5f + rnd.nextFloat() * 0.6f);
                if (rnd.nextInt(6) == 0) Holo.banner(l, st.tag, tgt.add(0, 1.2, 0), "{text:\"\",extra:[" + Holo.tx("레고 밟음!", 0xFFD000, true) + "]}", 0.7, 18);
            });
        }
    }

    // ═════════════════════════ 7. 술식전개 — 무료훈수
    static final String[] HUNSU_LINES = {"아 거기서 그걸 왜 써", "와드 좀 박아", "그냥 내가 할게", "궁 아껴!!", "템 순서 틀렸는데?", "CS 좀 먹어",
            "물러나라고!!", "그 각 아니잖아", "스킬 다 빼고 들어가네", "미니맵 좀 봐", "그거 너프됐어", "이걸 지네", "점멸 왜 아껴",
            "한타 때 어디 있었음?", "판단이 너무 느려", "손이 안 따라주네", "하… 내가 해줄까?", "그거 아니라니까", "설정 바꿔 봐"};
    static final class Hunsu implements Fx {
        final Vec3 C, F; final double[][] M; int t = 0;
        final Map<LivingEntity, Integer> exposed = new HashMap<>();
        final Set<Mob> noAi = new HashSet<>();
        final Random rnd = new Random();
        boolean stop; String dt;
        Hunsu(ServerPlayer p) { F = flatLook(p); C = p.position(); M = basis(F); }
        public boolean tick(ServerLevel l, ServerPlayer p, State s) {
            t++;
            int life = LIFE_INF;
            if (t == 1) {
                dt = s.tag + "d";
                Holo.banner(l, s.tag, p.getEyePosition().add(F.scale(3)).add(0, 1, 0), "{text:\"\",extra:[" + Holo.tx("술식전개 「무료훈수」", 0x55FFFF, true) + "]}", 1.5, 50);
                bar(p, "§b무료훈수 §7— 미숙함의 열량 주입 → 훈수의 열량 무한 유입 §8(다시 쓰면 해제)");
                Holo.sfx(l, C, "glitch", 3f, 0.7f); Holo.sfx(l, C, "data", 3f, 0.9f);
                // PC방: 바닥 + 좌석 4줄×6 (책상·모니터·화면·의자) + 네온 간판
                box(l, dt, C, M, 0, 0.03, 0, 34, 0.06, 26, "minecraft:gray_concrete", -1, 2, life);
                int n = 0;
                for (int row = 0; row < 4; row++) for (int col = 0; col < 6; col++) {
                    double x = (col - 2.5) * 4.2, z = (row - 1.5) * 5.0 + 1.5;
                    int t0 = 4 + (n++ % 12);
                    box(l, dt, C, M, x, 1.0, z, 2.2, 0.12, 1.1, "minecraft:white_concrete", -1, t0, life);
                    box(l, dt, C, M, x, 1.65, z + 0.4, 1.6, 0.95, 0.08, "minecraft:black_concrete", -1, t0 + 2, life);
                    box(l, dt, C, M, x, 1.67, z + 0.35, 1.4, 0.75, 0.02, (row + col) % 3 == 0 ? "minecraft:light_blue_stained_glass" : (row + col) % 3 == 1 ? "minecraft:lime_stained_glass" : "minecraft:magenta_stained_glass", 0x55FFFF, t0 + 3, life);
                    box(l, dt, C, M, x, 0.6, z - 0.9, 0.8, 0.12, 0.8, "minecraft:red_concrete", -1, t0 + 1, life);
                    box(l, dt, C, M, x, 1.05, z - 1.3, 0.8, 0.8, 0.1, "minecraft:red_concrete", -1, t0 + 1, life);
                }
                Vec3 sign = C.add(Holo.apply(M, new Vec3(0, 7, 12)));
                Holo.text(l, dt, sign, "{text:\"\",extra:[" + Holo.tx("무료훈수 PC방", 0xFF55FF, true) + "]}", "center", 0xA0000000, false, Holo.m(Holo.I(), 5, 5, 5, Vec3.ZERO, false), life, 0);
                Holo.text(l, dt, sign.add(0, -1.4, 0), "{text:\"\",extra:[" + Holo.tx("1시간 무료 · 훈수 무제한", 0x55FFFF, false) + "]}", "center", 0, false, Holo.m(Holo.I(), 2.2, 2.2, 2.2, Vec3.ZERO, false), life, 0);
                for (int i = 0; i < 3; i++) {
                    Vec3 a = C.add(Holo.apply(M, new Vec3(-16, 5.5, -10 + i * 10))), b = C.add(Holo.apply(M, new Vec3(16, 5.5, -10 + i * 10)));
                    Holo.beamGlow(l, dt, a, b, 0.6, i == 1 ? 0xFF55FF : 0x55FFFF, 6, life - 16, 8);
                }
            }
            if (!stop) {
                // 즉각 구속 + 능력 봉쇄 (반경 200)
                if (t % 5 == 1) for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(C, C).inflate(DOMAIN_R),
                        x -> x != p && x.isAlive() && !(x instanceof ArmorStand) && !(x instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                    if (e.position().distanceToSqr(C) > DOMAIN_R * DOMAIN_R) continue;
                    exposed.merge(e, 5, Integer::sum);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 255, false, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 12, 255, false, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 12, 255, false, false, true));
                    if (e instanceof Mob m && !m.isNoAi()) { m.setNoAi(true); noAi.add(m); }
                    if (e instanceof Player) e.addTag("jjk.hunsu");
                }
                for (LivingEntity e : exposed.keySet()) if (e.isAlive()) push(e, new Vec3(0, Math.min(0, e.getDeltaMovement().y), 0));
                // 머리 위로 쏟아지는 훈수
                if (t % 8 == 0) {
                    List<LivingEntity> list = new ArrayList<>(exposed.keySet());
                    list.removeIf(e -> !e.isAlive() || e.distanceTo(p) > 64);
                    for (int i = 0; i < Math.min(4, list.size()); i++) {
                        LivingEntity e = list.get(rnd.nextInt(list.size()));
                        Vec3 at = e.position().add(rnd.nextGaussian() * 0.6, e.getBbHeight() + 0.5 + rnd.nextDouble() * 0.8, rnd.nextGaussian() * 0.6);
                        Holo.Elem txt = Holo.text(l, s.tag, at, "{text:\"\",extra:[" + Holo.tx(HUNSU_LINES[rnd.nextInt(HUNSU_LINES.length)], rnd.nextBoolean() ? 0xFFFFFF : 0xFFE060, false) + "]}",
                                "center", 0x90000000, false, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 30, 0);
                        txt.key(1, Holo.m(Holo.I(), 0.8, 0.8, 0.8, Vec3.ZERO, false), 3).key(20, Holo.m(Holo.I(), 0.8, 0.8, 0.8, new Vec3(0, 0.8, 0), false), 8);
                    }
                    l.playSound(null, C.x, C.y, C.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 2f, 0.6f + rnd.nextFloat());
                }
                if (t % 20 == 0) for (LivingEntity e : exposed.keySet()) if (e.isAlive() && e.distanceTo(p) < 80)
                    l.sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 1, 0.2, 0.1, 0.2, 0);
                return false;
            }
            // ── 종료: 후유증
            for (Mob m : noAi) if (m.isAlive()) m.setNoAi(false);
            for (Map.Entry<LivingEntity, Integer> en : exposed.entrySet()) {
                LivingEntity e = en.getKey();
                if (!e.isAlive()) continue;
                int ticks = en.getValue();
                if (e instanceof ServerPlayer sp) {
                    int para = Math.min(20 * 60 * 30, ticks * 10);   // 노출 1분당 10분 행동 불가
                    PARALYZED.put(sp.getUUID(), now + para);
                    sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, para, 255, false, false, true));
                    sp.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, para, 255, false, false, true));
                    sp.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§c훈수 과부하 후유증 §f%s §7동안 움직일 수 없다", mmss(para))), true);
                } else {
                    e.removeTag("jjk.hunsu");
                    if (ticks > 20) {   // 일반인: 1초 넘게 노출 → 영구 손상
                        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MobEffectInstance.INFINITE_DURATION, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MobEffectInstance.INFINITE_DURATION, 1));
                    }
                }
            }
            if (dt != null) Holo.killOwner(dt);
            Holo.sfx(l, C, "glitch", 3f, 0.5f);
            endDomain(l, p, s, "무료훈수");
            return true;
        }
        @Override public void abort(ServerLevel l, ServerPlayer p, State s) {
            for (Mob m : noAi) if (m.isAlive()) m.setNoAi(false);
            for (LivingEntity e : exposed.keySet()) e.removeTag("jjk.hunsu");
            if (dt != null) Holo.killOwner(dt);
        }
    }

    // ═════════════════════════ 8. 점심시간 유아독존
    static void lunch(ServerLevel l, ServerPlayer p, State s) {
        Objective max = obj(l.getServer(), "gojo.cemax");
        if (obj(l.getServer(), "gojo.ce") != null) setScore(p, "gojo.ce", max == null ? 1000 : Math.max(1, score(p, "gojo.cemax", 1000)));
        for (int i = 0; i < s.cd.length; i++) if (i != LUNCH) s.cd[i] = 0;
        s.combo = 0;
        for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects()))
            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(e.getEffect());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20f);
        p.setHealth(p.getMaxHealth());
        Vec3 c = p.position();
        Holo.banner(l, s.tag, p.getEyePosition().add(flatLook(p).scale(2.5)).add(0, 0.8, 0), "{text:\"\",extra:[" + Holo.tx("점심시간 유아독존", GOLD, true) + "]}", 1.4, 50);
        Holo.pillar(l, s.tag, c, 30, 3.2, GOLD, 4, 20, 14);
        Holo.circle(l, s.tag, c, 5, GOLD, 50, 0);
        String[] food = {"minecraft:bread", "minecraft:cooked_chicken", "minecraft:mushroom_stew", "minecraft:apple", "minecraft:milk_bucket", "minecraft:cookie"};
        for (int i = 0; i < food.length; i++) {
            double a = i * Math.PI * 2 / food.length;
            Vec3 off = new Vec3(Math.cos(a) * 1.8, 1.2, Math.sin(a) * 1.8);
            Holo.item(l, s.tag, c, food[i], false, -1, Holo.m(Holo.I(), 0.01, 0.01, 0.01, off, false), 50, 0)
                    .key(1, Holo.m(Holo.I(), 0.9, 0.9, 0.9, off, false), 6).key(40, Holo.m(Holo.I(), 0.01, 0.01, 0.01, off.scale(0.2), false), 8).spin(8);
        }
        l.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 1.2f);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.5f, 1f);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.5f, 0.8f);
        bar(p, "§6점심시간 유아독존 §7— 주력 100% · 피로·술식 부담 초기화");
    }

    // ═════════════════════════ 10. 뇌 파괴와 재생
    static void brain(ServerLevel l, ServerPlayer p, State s, boolean manual) {
        boolean overloaded = tlock(p) > 0 || dlock(p) > 0;
        if (tlock(p) > 0) setScore(p, "koming.tlock", 0);
        if (dlock(p) > 0) setScore(p, "koming.dlock", 0);   // 술식전개 과부하도 치료
        Vec3 head = p.getEyePosition();
        Holo.glow(l, s.tag, head, 0.9, 0xFF2020, 1, 2, 4);
        s.later(4, () -> Holo.glow(l, s.tag, p.getEyePosition(), 1.2, 0xFFFFFF, 2, 3, 6));
        l.sendParticles(new DustParticleOptions(0xB01010, 1.2f), head.x, head.y, head.z, 16, 0.25, 0.25, 0.25, 0);
        l.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 10, 0.3, 0.3, 0.3, 0.05);
        Holo.sfx(l, head, "glitch", 1.2f, 1.3f);
        l.playSound(null, head.x, head.y, head.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        if (manual) {
            p.invulnerableTime = 0;
            p.hurtServer(l, l.damageSources().magic(), 2f);
            s.later(4, () -> p.heal(6f));
            bar(p, overloaded ? "§f뇌 파괴 → 재생 §a술식·술식전개 과부하 해소 §7— 바로 다시 사용 가능" : "§f뇌 파괴 → 재생 §7(과부하 없음)");
        }
    }

    /** 급식안 분석 (급식안 우클릭). */
    static void analyze(ServerPlayer p) {
        StringBuilder sb = new StringBuilder("§b급식안 §7루카력 ");
        boolean e = eye(p);
        for (int i = 0; i < IDS.length; i++) if (COST[i] > 0) sb.append("§f").append(NAMES[i].replaceAll(".*「|」", "")).append(" §8").append(COST[i]).append("→§a").append(e ? cost(p, i) : COST[i]).append(' ');
        bar(p, sb.toString());
    }

    // ═════════════════════════ 6. 급식한 / 전투 예측 (피해 판정)
    /** true = 피해 무효. */
    public static boolean blockDamage(ServerPlayer p, DamageSource src, float amount) {
        State s = STATES.get(p.getUUID());
        if (s == null || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        Entity atk = src.getEntity(), direct = src.getDirectEntity();
        boolean external = (atk != null && atk != p) || (direct != null && direct != p) || src.is(DamageTypeTags.IS_EXPLOSION);
        if (!external || !isKoming(p)) return false;
        ServerLevel l = (ServerLevel) p.level();
        if (s.shield) {   // 첫 번째 층: 접근 차단
            if (s.lastBlockFx <= 0) {
                Entity from = direct != null ? direct : atk;
                Vec3 hit = from != null ? p.getEyePosition().add(from.position().add(0, from.getBbHeight() / 2, 0).subtract(p.getEyePosition()).normalize().scale(1.2)) : p.getEyePosition();
                Holo.sprite(l, s.tag, hit, "hex", 0x9FD8FF, "center", Holo.bb(0.3, 0, Vec3.ZERO), 8, 0).key(1, Holo.bb(1.8, 0.4, Vec3.ZERO), 6);
                l.playSound(null, hit.x, hit.y, hit.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 1.6f);
                s.lastBlockFx = 4;
            }
            return true;
        }
        if (s.yu && eye(p) && p.getRandom().nextFloat() < 0.5f && atk != null) {   // 전투 예측: 공격을 읽고 비켜섬
            Vec3 away = p.position().subtract(atk.position()).multiply(1, 0, 1);
            Vec3 side = new Vec3(-away.z, 0, away.x);
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            side = side.normalize().scale(p.getRandom().nextBoolean() ? 1.6 : -1.6);
            AABB box = p.getBoundingBox().move(side);
            if (l.noCollision(p, box)) p.teleportTo(p.getX() + side.x, p.getY(), p.getZ() + side.z);
            Holo.banner(l, s.tag, p.getEyePosition().add(0, 0.6, 0), "{text:\"\",extra:[" + Holo.tx("예측", CYAN, true) + "]}", 0.6, 14);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f);
            return true;
        }
        return false;
    }

    // ═════════════════════════ 매 틱
    public static void tick(MinecraftServer server) {
        now++;
        // 무료훈수 후유증 풀림
        if (now % 20 == 0) PARALYZED.entrySet().removeIf(en -> {
            if (now < en.getValue()) return false;
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp != null) sp.removeTag("jjk.hunsu");
            return true;
        });
        List<ServerPlayer> casters = new ArrayList<>(server.getPlayerList().getPlayers());
        casters.addAll(BossPuppet.KOMING);   // 코밍 보스의 꼭두각시도 원래 술식 틱을 받음
        for (ServerPlayer p : casters) {
            if (!PARALYZED.containsKey(p.getUUID()) && p.getTags().contains("jjk.hunsu") && !hunsuNear(p)) p.removeTag("jjk.hunsu");
            if (PARALYZED.containsKey(p.getUUID())) push(p, new Vec3(0, Math.min(0, p.getDeltaMovement().y), 0));   // 훈수 후유증: 행동 불가
            // 페널티 시간 감소 (점수판, 20틱마다)
            if (now % 20 == 0) {
                int tl = tlock(p), dl = dlock(p);
                if (tl > 0) setScore(p, "koming.tlock", Math.max(0, tl - 20));
                if (dl > 0) setScore(p, "koming.dlock", Math.max(0, dl - 20));
            }
            State s0 = STATES.get(p.getUUID());
            boolean kom = isKoming(p);
            if (s0 == null && !kom) continue;
            final State s = s0 != null ? s0 : st(p);
            ServerLevel l = (ServerLevel) p.level();
            for (int i = 0; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
            if (s.lastBlockFx > 0) s.lastBlockFx--;
            // 예약 작업
            if (!s.later.isEmpty()) {
                List<Object[]> run = new ArrayList<>();
                for (Iterator<Object[]> it = s.later.iterator(); it.hasNext(); ) {
                    Object[] o = it.next();
                    o[0] = (int) o[0] - 1;
                    if ((int) o[0] <= 0) { run.add(o); it.remove(); }
                }
                for (Object[] o : run) try { ((Runnable) o[1]).run(); } catch (Throwable ex) { JjkMod.LOG.warn("[JJK] 코밍 예약 작업 오류", ex); }
            }
            // 진행 중 술식
            if (!s.fx.isEmpty()) {
                List<Fx> cur = new ArrayList<>(s.fx);
                for (Fx f : cur) {
                    boolean done;
                    try {
                        if (!p.isAlive() || p.level() != l) { f.abort(l, p, s); done = true; }
                        else done = f.tick(l, p, s);
                    } catch (Throwable ex) { JjkMod.LOG.warn("[JJK] 코밍 술식 오류", ex); done = true; try { f.abort(l, p, s); } catch (Throwable ignored) { } }
                    if (done) s.fx.remove(f);
                }
            }
            if (!kom) { s.shield = false; s.yu = false; continue; }
            boolean e = eye(p);
            // 1. 급식안 패시브: 영적 시야(투명 존재) · 능력자 감지
            if (e && now % 10 == 0 && !p.getTags().contains("jjk.blindfold")) {   // 고죠의 안대를 쓰면 투시 봉인
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
                for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive()
                        && (x.isInvisible() || x instanceof Player || x.getTags().stream().anyMatch(tg -> tg.startsWith("gojo.") || tg.startsWith("jjk.")))))
                    x.addEffect(new MobEffectInstance(MobEffects.GLOWING, 15, 0, false, false, false));
            }
            // 6. 급식한: 투사체 정지 → 사라질 때까지 유지, 근접한 대상 정지
            if (s.shield) {
                if (now % 2 == 0 && !spend(p, e ? (now % 40 == 0 ? 1 : 0) : 1)) { s.shield = false; bar(p, "§7루카력 고갈 — 급식한 해제"); }
                for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(4.5), x -> x.getOwner() != p)) {
                    if (!s.frozen.containsKey(pr)) {
                        s.frozen.put(pr, 0);
                        l.sendParticles(ParticleTypes.END_ROD, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
                    }
                    pr.setDeltaMovement(Vec3.ZERO);
                    pr.setNoGravity(true);
                }
                for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.4), x -> x != p && x.isAlive() && !(x instanceof ArmorStand))) {
                    Vec3 d = x.position().subtract(p.position()).multiply(1, 0, 1);
                    push(x, (d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize()).scale(0.25));
                }
                if (now % 6 == 0) l.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 6, 0.8, 1, 0.8, 0.2);
            }
            if (!s.frozen.isEmpty()) s.frozen.entrySet().removeIf(en -> {
                Entity x = en.getKey();
                if (!x.isAlive()) return true;
                int age = en.getValue() + 1;
                en.setValue(age);
                x.setDeltaMovement(Vec3.ZERO);
                if (age >= 30 || !s.shield) {   // 존재·부재 반복 → 접촉한 물체 소멸
                    l.sendParticles(ParticleTypes.REVERSE_PORTAL, x.getX(), x.getY(), x.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
                    x.discard();
                    return true;
                }
                return false;
            });
            // 9. 술력 유: 존재의 열량으로 회복
            if (s.yu) {
                if (now % 2 == 0 && !spend(p, e ? (now % 20 == 0 ? 1 : 0) : 2)) { s.yu = false; bar(p, "§7루카력 고갈 — 술력 유 해제"); }
                if (now % 20 == 0) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, true, false, false));
                if (now % 4 == 0) l.sendParticles(new DustParticleOptions(0xFFE070, 1.0f), p.getX(), p.getY() + 1, p.getZ(), 3, 0.5, 0.8, 0.5, 0);
            }
            // 12. 무량공처 대응: 뇌를 파괴·재생하며 계속 움직이려 함 (정보 유입 자체는 막지 못함)
            if (inVoid(l, p)) {
                if (++s.uv % 40 == 0) {
                    brain(l, p, s, false);
                    for (MobEffectInstance x : new ArrayList<>(p.getActiveEffects()))
                        if (x.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(x.getEffect());
                    push(p, p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.7).add(0, 0.25, 0));
                    bar(p, "§f뇌 전체 파괴 → 재생 §7… 정보 유입은 계속된다");
                }
            } else s.uv = 0;
            // 상태 표시 (코밍 아이템을 손에 들었을 때)
            if (now % 10 == 0 && isKomingItem(p.getMainHandItem()) && s.fx.isEmpty()) {
                int tl = tlock(p), dl = dlock(p);
                String st = (s.shield ? "§b급식한 ON " : "") + (s.yu ? "§e유 ON " : "") + (tl > 0 ? "§c술식 " + mmss(tl) + " " : "") + (dl > 0 ? "§4영역 " + mmss(dl) : "");
                if (!st.isEmpty()) bar(p, st.trim());
            }
        }
    }

    /** 남의 무량공처 안에 있는가 (내가 펼친 무량공처는 제외: gojo.id 비교). */
    static boolean inVoid(ServerLevel l, ServerPlayer p) {
        Objective id = obj(l.getServer(), "gojo.id");
        for (Marker m : l.getEntitiesOfClass(Marker.class, p.getBoundingBox().inflate(16), x -> x.getTags().contains("gojo.domain") && !x.getTags().contains("gojo.closing"))) {
            if (m.distanceTo(p) > 14) continue;
            if (id != null) {
                var sb = l.getServer().getScoreboard();
                var mi = sb.getPlayerScoreInfo(m, id);
                var pi = sb.getPlayerScoreInfo(p, id);
                if (mi != null && pi != null && mi.value() == pi.value()) continue;
            }
            return true;
        }
        return false;
    }
    static boolean hunsuNear(ServerPlayer p) {
        for (State s : STATES.values()) for (Fx f : s.fx) if (f instanceof Hunsu h && h.C.distanceTo(p.position()) <= DOMAIN_R + 4) return true;
        return false;
    }

    public static void stopAll() {
        for (State s : STATES.values()) for (Fx f : s.fx) if (f instanceof Hunsu h) for (Mob m : h.noAi) if (m.isAlive()) m.setNoAi(false);
        STATES.clear();
        PARALYZED.clear();
    }
}
