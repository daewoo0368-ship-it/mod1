package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 개발자의 초 울트라 진짜 겁나 짱짱 센 슈퍼 울트라 데너레이터 맥스 얼티메이트 그레이트 레볼루션 스페셜 네임드 인피니티 오메가 레전드 더 엔드 매직 쉴드 핸드폰.
 * 이름에 들어간 단어마다 앱이 하나씩. Z/C 로 앱 넘기기, 우클릭 실행.
 * 패시브 「초 울트라 진짜 겁나 짱짱 센」: 들고 있으면 힘 II · 신속 I, 모든 앱 피해 ×(1 + 0.1 × 연속 실행 수, 최대 ×2).
 */
public final class DevPhone {
    private DevPhone() {}

    public static final String NAME = "개발자의 초 울트라 진짜 겁나 짱짱 센 슈퍼 울트라 데너레이터 맥스 얼티메이트 그레이트 레볼루션스페셜 네임드 인피니티 오메가 레전드 더 엔드 매직 쉴드 핸드폰";
    public static final String[] APPS = {"매직 쉴드", "인피니티", "오메가", "더 엔드", "데너레이터", "레볼루션", "네임드", "레전드", "그레이트", "스페셜", "얼티메이트", "전화"};
    public static final String[] DESC = {"켜기/끄기 · 피해 300 흡수 · 투사체 반사", "10초 · 다가오는 모든 것이 멈춤", "Ω 천벌 광선 (120)", "엔딩 크레딧 → 적 전원 종영",
            "발전기 가동: 완전 회복 · 흡수 · 정화", "폰 8대가 10초간 공전하며 갈아버림", "표적 지정 · 방어 0 · 저주", "30초 전설 오라",
            "20초 거대화 ×3", "가챠! 무작위 앱 ×2 위력", "모든 앱 동시 실행", "개발자에게 전화 → 궤도 지원 사격"};
    public static final int[] COLOR = {0x55DDFF, 0xB45CFF, 0xFF3040, 0xFFFFFF, 0x55FF55, 0xFFAA33, 0xFF55FF, 0xFFD84A, 0xC08040, 0xFF8AD8, 0xFF5050, 0x40E8FF};
    public static final String[] ICON = {"⛨", "∞", "Ω", "▣", "⚡", "↻", "✎", "♛", "▲", "★", "✸", "☎"};
    static final int[] CD = {300, 600, 160, 1200, 600, 400, 300, 1200, 900, 400, 2400, 800};

    static final Map<UUID, Integer> APP = new HashMap<>();
    static final Map<UUID, long[]> READY = new HashMap<>();
    static final Map<UUID, Integer> COMBO = new HashMap<>();
    static final Map<UUID, Shield> SHIELDS = new HashMap<>();
    static final List<Fx> FX = new ArrayList<>();
    static final ResourceLocation GREAT_ID = com.tubilt.jjk.JjkMod.id("phone_great"), GREAT_R = com.tubilt.jjk.JjkMod.id("phone_great_r"),
            GREAT_D = com.tubilt.jjk.JjkMod.id("phone_great_d"), NAMED_A = com.tubilt.jjk.JjkMod.id("phone_named");
    static int uid = 0;

    interface Fx { boolean tick(); }

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.DEV_PHONE.get()); }
    public static boolean holding(ServerPlayer p) { return is(p.getMainHandItem()); }

    /** 클라이언트가 고른 앱 번호. */
    public static void select(ServerPlayer p, int app) {
        if (app < 0 || app >= APPS.length) return;
        APP.put(p.getUUID(), app);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.6f + app * 0.03f);
    }

    static String tag(ServerPlayer p) { return "jjk.phone" + (uid++); }

    /** 우클릭. */
    public static void use(ServerPlayer p, ServerLevel l) {
        int app = APP.getOrDefault(p.getUUID(), 0);
        long now = l.getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[APPS.length]);
        if (app == 0 && SHIELDS.containsKey(p.getUUID())) { SHIELDS.remove(p.getUUID()).off(true); return; }   // 쉴드 끄기는 쿨타임 무시
        if (now < ready[app]) {
            p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7%s %s §8— 충전 중 %.1f초", ICON[app], APPS[app], (ready[app] - now) / 20.0)), true);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.6f);
            return;
        }
        ready[app] = now + CD[app];
        if (countApp(p, app)) { overload(p, l); return; }
        int combo = Math.min(10, COMBO.getOrDefault(p.getUUID(), 0) + 1);
        COMBO.put(p.getUUID(), combo);
        appToast(p, app, "앱 실행");
        run(p, l, app, 1.0 + combo * 0.1);
    }

    /** 앱 사용 기록 (플레이어 태그라 저장됨). 12개 전부 쓰면 true. */
    static boolean countApp(ServerPlayer p, int app) {
        p.addTag("jjk.app" + app);
        for (int i = 0; i < APPS.length; i++) if (!p.getTags().contains("jjk.app" + i)) return false;
        for (int i = 0; i < APPS.length; i++) p.removeTag("jjk.app" + i);
        return true;
    }

    /** 모든 앱을 다 써버림 → 폰 과부하 → 가짜 크래시 화면 (게임은 안 꺼짐). */
    static void overload(ServerPlayer p, ServerLevel l) {
        p.sendSystemMessage(Component.literal("§c§l[경고] §f12/12 앱 실행됨 — 핸드폰 과열… 과열… 과열…"));
        GuardMeta.send(p, 5, "핸드폰이… 버티지 못해.");
        Holo.sfx(l, p.position(), "siren", 2f, 1.4f);
        FX.add(new Fx() {
            int t = 0;
            public boolean tick() {
                t++;
                if (t % 5 == 0) l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.5f, 0.5f + t * 0.03f);
                if (t == 40) JjkNet.toPlayer(p, new Payloads.Meta(10, String.join(", ", APPS)));
                return t >= 40;
            }
        });
    }

    static void appToast(ServerPlayer p, int app, String sub) {
        JjkNet.toPlayer(p, new Payloads.CutIn(ICON[app] + " " + APPS[app], sub + " · " + DESC[app], COLOR[app], app == 3 || app == 10 || app == 11 ? 1 : 0));
        ServerLevel l = (ServerLevel) p.level();
        // 손 앞에 떠오르는 폰 화면 (앱 아이콘)
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(1.6)).add(0, -0.3, 0);
        Holo.text(l, tag(p), at, "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(ICON[app] + "\\n", COLOR[app], true) + "," + Holo.tx(APPS[app], 0xFFFFFF, true) + "]}",
                "center", 0xE0101018, false, Holo.m(Holo.I(), 0.01, 1.0, 1.0, Vec3.ZERO, false), 24, 1)
                .key(1, Holo.m(Holo.I(), 1.1, 1.1, 1.1, Vec3.ZERO, false), 3).key(18, Holo.m(Holo.I(), 1.0, 0.01, 1.0, new Vec3(0, 0.4, 0), false), 5);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.6f);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.4f);
    }

    static void run(ServerPlayer p, ServerLevel l, int app, double k) {
        switch (app) {
            case 0 -> SHIELDS.put(p.getUUID(), new Shield(p, l, k));
            case 1 -> FX.add(new Infinity(p, l, k));
            case 2 -> omega(p, l, target(p, 80), k);
            case 3 -> FX.add(new TheEnd(p, l, target(p, 60), k));
            case 4 -> generator(p, l, k);
            case 5 -> FX.add(new Revolution(p, l, k));
            case 6 -> named(p, l, k);
            case 7 -> FX.add(new Legend(p, l, k));
            case 8 -> FX.add(new Great(p, l, k));
            case 9 -> FX.add(new Gacha(p, l));
            case 10 -> FX.add(new Ultimate(p, l, target(p, 70), k));
            case 11 -> FX.add(new Call(p, l, target(p, 90), k));
            default -> { }
        }
    }

    // ═════════ 공통
    static Vec3 target(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        var hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation();
    }
    static LivingEntity aimed(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        EntityHitResult r = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p.level(), p, eye, end,
                p.getBoundingBox().expandTowards(p.getLookAngle().scale(range)).inflate(2), e -> e instanceof LivingEntity && e != p && e.isAlive(), 0.3f);
        return r != null && r.getEntity() instanceof LivingEntity le ? le : null;
    }
    static List<LivingEntity> foes(ServerLevel l, ServerPlayer p, Vec3 c, double r) { return Koming.foes(l, p, c, r); }
    static void hurt(ServerLevel l, ServerPlayer p, LivingEntity e, float dmg) {
        if (e == null || !e.isAlive() || e == p) return;
        e.invulnerableTime = 0;
        e.hurtServer(l, l.damageSources().playerAttack(p), dmg);
    }
    static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }

    static void tickAll(net.minecraft.server.MinecraftServer server) {
        // 인덱스 순회: 틱 도중 새 효과가 추가돼도 안전 (가챠·얼티메이트가 다른 앱을 실행함)
        for (int i = 0; i < FX.size(); i++) {
            Fx f = FX.get(i);
            boolean done;
            try { done = f.tick(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 핸드폰 오류", e); done = true; }
            if (done) FX.remove(i--);
        }
        for (UUID id : new ArrayList<>(SHIELDS.keySet())) { Shield s = SHIELDS.get(id); if (s != null && s.tick()) SHIELDS.remove(id); }
        // 패시브: 들고 있으면 힘·신속, 3초 안 쓰면 연속 실행 수 초기화
        if (server.getTickCount() % 20 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (holding(p)) {
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30, 1, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, false, false));
            }
        }
        if (server.getTickCount() % 100 == 0) COMBO.clear();
    }

    static void stopAll() {
        for (Shield s : SHIELDS.values()) s.off(false);
        SHIELDS.clear();
        for (Fx f : FX) if (f instanceof Great g) g.end();
        FX.clear();
    }

    /** 피해 훅: 매직 쉴드가 흡수. true = 취소. */
    public static boolean absorb(LivingEntity target, DamageSource src, float amount) {
        Shield s = SHIELDS.get(target.getUUID());
        return s != null && s.absorb(amount, src);
    }

    // ═════════ 0 매직 쉴드
    static final class Shield {
        final ServerPlayer p; final ServerLevel l; final String tag; float hp, max; int t = 0, quiet = 0, shell = 0;
        Shield(ServerPlayer p, ServerLevel l, double k) {
            this.p = p; this.l = l; this.tag = tag(p); max = hp = (float) (300 * k);
            Holo.bubble(l, tag, () -> p.position(), 2.6, 0x55DDFF, 200);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.6f);
            Holo.sfx(l, p.position(), "magic", 1.5f, 1.2f);
        }
        boolean absorb(float dmg, DamageSource src) {
            hp -= dmg; quiet = 0;
            Vec3 c = p.position().add(0, 1, 0);
            Holo.glow(l, tag, c, 3.2, 0x55DDFF, 1, 1, 4);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 1.4f);
            p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§b⛨ 매직 쉴드 §f%.0f §7/ %.0f", Math.max(0, hp), max)), true);
            if (src.getEntity() instanceof LivingEntity atk && atk != p) {   // 반격 전류
                Holo.lightning(l, tag, c, atk.position().add(0, atk.getBbHeight() / 2, 0), 0x55DDFF, 0.12, 4);
                hurt(l, p, atk, dmg * 0.5f);
            }
            if (hp <= 0) { off(true); SHIELDS.remove(p.getUUID()); }
            return true;
        }
        boolean tick() {
            t++; quiet++;
            if (!p.isAlive() || p.isRemoved()) { off(false); return true; }
            if (t % 190 == 0) Holo.bubble(l, tag, () -> p.position(), 2.6, 0x55DDFF, 200);
            if (quiet > 60 && hp < max) hp = Math.min(max, hp + 1.5f);
            Vec3 c = p.position().add(0, 1, 0);
            for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(4), x -> x.getOwner() != p)) {
                Vec3 v = pr.getDeltaMovement();
                if (v.dot(c.subtract(pr.position())) <= 0) continue;   // 다가오는 것만
                pr.setDeltaMovement(v.scale(-1.2));
                pr.setOwner(p);
                pr.hurtMarked = true;
                Holo.glow(l, tag, pr.position(), 1.2, 0x55DDFF, 1, 1, 3);
                l.playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 1.8f);
            }
            if (t % 40 == 0) l.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 20, 1.5, 1.5, 1.5, 0.5);
            return false;
        }
        void off(boolean sound) {
            Holo.killOwner(tag);
            if (sound) {
                Vec3 c = p.position().add(0, 1, 0);
                Holo.shards(l, tag + "x", c, "minecraft:light_blue_stained_glass", 24, 4);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 1.2f);
            }
        }
    }

    // ═════════ 1 인피니티
    static final class Infinity implements Fx {
        final ServerPlayer p; final ServerLevel l; final String tag; int t = 0; final int len;
        Infinity(ServerPlayer p, ServerLevel l, double k) {
            this.p = p; this.l = l; tag = tag(p); len = (int) (200 * k);
            Holo.ring(l, tag, p.position().add(0, 0.1, 0), 6, 6.2, 48, 0.4, 0.05, 0xB45CFF, len, 4, 6, 0, 2, true);
            Holo.sfx(l, p.position(), "deep", 2f, 1.2f);
        }
        public boolean tick() {
            t++;
            if (!p.isAlive()) return true;
            Vec3 c = p.position().add(0, 1, 0);
            for (Entity e : l.getEntities(p, new AABB(c, c).inflate(7), x -> x.position().distanceTo(c) < 7)) {
                if (e instanceof LivingEntity le && !Koming.foe(le, p) && !(e instanceof Projectile)) continue;
                Vec3 v = e.getDeltaMovement(), to = c.subtract(e.position()).normalize();
                double in = v.dot(to);
                if (in > 0) e.setDeltaMovement(v.subtract(to.scale(in)).scale(0.2));   // 다가오는 속도 제거
                double d = e.position().distanceTo(c);
                if (d < 3.5) e.setDeltaMovement(e.getDeltaMovement().add(to.scale(-0.15)));   // 무한에 막혀 밀려남
                e.hurtMarked = true;
                if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 5, false, false));
            }
            if (t % 5 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 12, 3, 1.5, 3, 0.02);
            return t >= len;
        }
    }

    // ═════════ 2 오메가
    static void omega(ServerPlayer p, ServerLevel l, Vec3 at, double k) {
        String tag = tag(p);
        Holo.banner(l, tag, at.add(0, 22, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("Ω", 0xFF3040, true) + "]}", 14, 30);
        Holo.circle(l, tag, at.add(0, 0.1, 0), 7, 0xFF3040, 28, 0);
        Holo.sfx(l, at, "charge", 2.5f, 0.7f);
        Fx f = new Fx() {
            int t = 0;
            public boolean tick() {
                t++;
                if (t == 14) {
                    Holo.pillar(l, tag, at, 120, 6 * k, 0xFF3040, 2, 6, 10);
                    Holo.pillar(l, tag, at, 120, 2.4 * k, 0xFFFFFF, 2, 6, 10);
                    Holo.shock(l, tag, at, 16, 0xFF3040, 10);
                    Holo.explosion(l, tag, at, 6, 0xFF3040);
                    for (LivingEntity e : foes(l, p, at, 7 * k)) hurt(l, p, e, (float) (120 * k));
                    Holo.sfx(l, at, "laser", 3f, 0.5f);
                    Holo.sfx(l, at, "boom", 3f, 0.7f);
                    Titan.shake(l, at, 50, true);
                }
                return t >= 16;
            }
        };
        FX.add(f);
    }

    // ═════════ 3 더 엔드 (엔딩 크레딧)
    static final class TheEnd implements Fx {
        final ServerPlayer p; final ServerLevel l; final Vec3 at; final String tag; final double k; int t = 0;
        final List<LivingEntity> cast = new ArrayList<>();
        static final String[] CREDITS = {"THE END", "", "감독 · 개발자", "각본 · 개발자", "주연 · %s", "", "조연 · 몹 %d마리", "특별 출연 · 핸드폰", "", "제작 · jjk-mod", "", "Thanks for playing"};
        TheEnd(ServerPlayer p, ServerLevel l, Vec3 at, double k) {
            this.p = p; this.l = l; this.at = at; this.k = k; tag = tag(p);
            cast.addAll(foes(l, p, at, 22 * k));
            for (LivingEntity e : cast) { e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 9)); Holo.hitbox(l, tag, e, 0xFFFFFF, 110); }
            for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(at) < 40) {
                JjkNet.toPlayer(sp, new Payloads.Screen(5, 0x000000, 5600, 0.75f));
                JjkNet.toPlayer(sp, new Payloads.Screen(6, 0x000000, 5600, 1));
            }
            Holo.sfx(l, at, "choir", 2.5f, 0.6f);
        }
        public boolean tick() {
            t++;
            if (t % 8 == 1 && (t - 1) / 8 < CREDITS.length) {
                String line = CREDITS[(t - 1) / 8];
                if (!line.isEmpty()) {
                    String txt = String.format(Locale.ROOT, line, p.getName().getString(), cast.size());
                    Holo.text(l, tag, at.add(0, 6, 0), banner(txt, (t - 1) / 8 == 0 ? 0xFFFFFF : 0xCCCCCC), "center", 0, false,
                            Holo.m(Holo.I(), (t - 1) / 8 == 0 ? 6 : 3, (t - 1) / 8 == 0 ? 6 : 3, 1, Vec3.ZERO, false), 90, 0)
                            .key(1, Holo.m(Holo.I(), (t - 1) / 8 == 0 ? 6 : 3, (t - 1) / 8 == 0 ? 6 : 3, 1, new Vec3(0, 24, 0), false), 88);
                }
            }
            if (t == 100) {
                for (LivingEntity e : cast) if (e.isAlive()) {
                    Vec3 o = e.position();
                    Holo.text(l, tag, o.add(0, e.getBbHeight() + 1.2, 0), banner("— 종영 —", 0xFFFFFF), "center", 0, false, Holo.m(Holo.I(), 1.5, 1.5, 1, Vec3.ZERO, false), 30, 0);
                    Holo.flare(l, tag, o.add(0, 1, 0), 4, 0xFFFFFF, 6);
                    if (e.getMaxHealth() <= 200) { e.invulnerableTime = 0; e.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
                    else hurt(l, p, e, (float) (300 * k));
                }
                Holo.sfx(l, at, "stinger", 3f, 0.6f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 0.8f);
            }
            return t >= 110;
        }
    }

    // ═════════ 4 데너레이터 (발전기)
    static void generator(ServerPlayer p, ServerLevel l, double k) {
        String tag = tag(p);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20);
        for (var inst : new ArrayList<>(p.getActiveEffects())) if (!inst.getEffect().value().isBeneficial()) p.removeEffect(inst.getEffect());
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, (int) Math.round(4 * k)));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 2));
        Vec3 c = p.position();
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            Vec3 q = c.add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
            Holo.block(l, tag, q, "minecraft:copper_bulb", 0x55FF55, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 1, 0), true), 40, 0)
                    .key(1 + i, Holo.m(Holo.I(), 1.2, 1.2, 1.2, new Vec3(0, 1, 0), true), 4).spin(20);
            Holo.lightning(l, tag, q.add(0, 1.6, 0), c.add(0, 1, 0), 0x55FF55, 0.1, 30);
        }
        Holo.pillar(l, tag, c, 30, 1.6, 0x55FF55, 2, 6, 8);
        Holo.sfx(l, c, "charge", 2f, 1.4f);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 1.2f);
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 80, 1.5, 1.5, 1.5, 0.4);
    }

    // ═════════ 5 레볼루션 (공전)
    static final class Revolution implements Fx {
        final ServerPlayer p; final ServerLevel l; final String tag; final double k; int t = 0; final int len = 200;
        Revolution(ServerPlayer p, ServerLevel l, double k) {
            this.p = p; this.l = l; this.k = k; tag = tag(p);
            for (int i = 0; i < 8; i++) {
                final int ii = i;
                Holo.item(l, tag, p.position(), "jjk:dev_phone", true, 0xFFAA33, Holo.m(Holo.I(), 1.4, 1.4, 1.4, Vec3.ZERO, false), len, 1)
                        .follow(() -> orbit(ii), Vec3.ZERO).spin(30);
            }
            Holo.sfx(l, p.position(), "whoosh", 2f, 1.3f);
        }
        Vec3 orbit(int i) {
            double a = i * Math.PI / 4 + t * 0.35;
            return p.position().add(Math.cos(a) * 3.6, 1.2 + Math.sin(t * 0.2 + i) * 0.6, Math.sin(a) * 3.6);
        }
        public boolean tick() {
            t++;
            if (!p.isAlive()) return true;
            if (t % 6 == 0) {
                for (LivingEntity e : foes(l, p, p.position(), 5.5)) {
                    hurt(l, p, e, (float) (9 * k));
                    Vec3 out = e.position().subtract(p.position()).normalize();
                    e.setDeltaMovement(out.scale(0.7).add(0, 0.25, 0)); e.hurtMarked = true;
                }
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.5f);
            }
            if (t % 3 == 0) for (int i = 0; i < 8; i++) { Vec3 q = orbit(i); l.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0.01); }
            return t >= len;
        }
    }

    // ═════════ 6 네임드
    static void named(ServerPlayer p, ServerLevel l, double k) {
        LivingEntity e = aimed(p, 50);
        if (e == null) { p.displayClientMessage(Component.literal("§7✎ 네임드 — 표적이 화면에 없다"), true); return; }
        String tag = tag(p);
        e.setCustomName(Component.literal("§c§l[NAMED] §f개발자의 표적"));
        e.setCustomNameVisible(true);
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0));
        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 1));
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 2));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 2));
        var armor = e.getAttribute(Attributes.ARMOR);
        if (armor != null && !armor.hasModifier(NAMED_A))
            armor.addTransientModifier(new AttributeModifier(NAMED_A, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        Holo.hitbox(l, tag, e, 0xFF55FF, 600);
        Holo.banner(l, tag, e.position().add(0, e.getBbHeight() + 2, 0), banner("✎ /data modify entity @s CustomName set \"표적\"", 0xFF55FF), 1.0, 40);
        hurt(l, p, e, (float) (25 * k));
        Holo.sfx(l, e.position(), "data", 2f, 1.2f);
        l.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1f, 1.6f);
    }

    // ═════════ 7 레전드
    static final class Legend implements Fx {
        final ServerPlayer p; final ServerLevel l; final String tag; int t = 0; final int len;
        Legend(ServerPlayer p, ServerLevel l, double k) {
            this.p = p; this.l = l; tag = tag(p); len = (int) (600 * k);
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, len, 3));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, len, 1));
            p.addEffect(new MobEffectInstance(MobEffects.HASTE, len, 2));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, len, 1));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, len, 1));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, len, 0));
            // 머리 위 황금 왕관
            for (int i = 0; i < 5; i++) {
                final int ii = i;
                Holo.block(l, tag, p.position(), "minecraft:gold_block", 0xFFD84A, Holo.m(Holo.I(), 0.22, 0.32, 0.22, Vec3.ZERO, true), len, 1)
                        .follow(() -> { double a = ii * Math.PI * 2 / 5 + t * 0.05; return p.position().add(Math.cos(a) * 0.32, p.getBbHeight() + 0.35, Math.sin(a) * 0.32); }, Vec3.ZERO);
            }
            Holo.pillar(l, tag, p.position(), 40, 2, 0xFFD84A, 2, 6, 10);
            Holo.banner(l, tag, p.position().add(0, 4, 0), banner("♛ LEGEND", 0xFFD84A), 2.2, 40);
            Holo.sfx(l, p.position(), "choir", 2.5f, 1.0f);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.2f);
        }
        public boolean tick() {
            t++;
            if (!p.isAlive()) { Holo.killOwner(tag); return true; }
            if (t % 4 == 0) l.sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1, p.getZ(), 4, 0.5, 0.8, 0.5, 0.2);
            return t >= len;
        }
    }

    // ═════════ 8 그레이트 (거대화)
    static final class Great implements Fx {
        final ServerPlayer p; final ServerLevel l; final String tag; int t = 0; final int len = 400;
        Great(ServerPlayer p, ServerLevel l, double k) {
            this.p = p; this.l = l; tag = tag(p);
            mod(Attributes.SCALE, GREAT_ID, 2.0);
            mod(Attributes.ENTITY_INTERACTION_RANGE, GREAT_R, 6);
            mod(Attributes.BLOCK_INTERACTION_RANGE, GREAT_R, 6);
            mod(Attributes.ATTACK_DAMAGE, GREAT_D, 12 * k);
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, len, 1));
            Holo.shock(l, tag, p.position(), 10, 0xC08040, 10);
            Holo.banner(l, tag, p.position().add(0, 9, 0), banner("▲ GREAT", 0xC08040), 3, 30);
            Holo.sfx(l, p.position(), "boom", 2f, 0.8f);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2f, 0.6f);
        }
        void mod(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> at, ResourceLocation id, double v) {
            var a = p.getAttribute(at);
            if (a == null) return;
            a.removeModifier(id);
            a.addTransientModifier(new AttributeModifier(id, v, at == Attributes.SCALE ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE));
        }
        void end() {
            for (var at : List.of(Attributes.SCALE, Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ATTACK_DAMAGE)) {
                var a = p.getAttribute(at);
                if (a != null) { a.removeModifier(GREAT_ID); a.removeModifier(GREAT_R); a.removeModifier(GREAT_D); }
            }
        }
        boolean ground = true;
        public boolean tick() {
            t++;
            if (!p.isAlive()) { end(); return true; }
            boolean on = p.onGround();
            if (on && !ground) {   // 착지 = 지진
                Holo.shock(l, tag, p.position(), 8, 0xC08040, 8);
                for (LivingEntity e : foes(l, p, p.position(), 6)) { hurt(l, p, e, 20f); e.setDeltaMovement(0, 0.8, 0); e.hurtMarked = true; }
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.5f, 0.6f);
            }
            ground = on;
            if (t % 20 == 0) l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_STEP, SoundSource.PLAYERS, 1f, 0.6f);
            if (t >= len) { end(); return true; }
            return false;
        }
    }

    // ═════════ 9 스페셜 (가챠)
    static final class Gacha implements Fx {
        final ServerPlayer p; final ServerLevel l; int t = 0; final int pick;
        static final int[] POOL = {1, 2, 3, 4, 5, 6, 7, 8, 11};
        Gacha(ServerPlayer p, ServerLevel l) {
            this.p = p; this.l = l;
            pick = POOL[l.random.nextInt(POOL.length)];
        }
        public boolean tick() {
            t++;
            if (t < 30 && t % 3 == 0) {
                int a = POOL[(t / 3) % POOL.length];
                p.displayClientMessage(Component.literal("§d★ 가챠 §f[ " + ICON[a] + " " + APPS[a] + " ]"), true);
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, 1.0f + t * 0.03f);
            }
            if (t == 32) {
                p.displayClientMessage(Component.literal("§d§l★ SSR ★ §f" + ICON[pick] + " " + APPS[pick] + " §6×2"), true);
                appToast(p, pick, "★ 가챠 당첨 ×2");
                Holo.flare(l, tag(p), p.position().add(0, 2.5, 0), 4, 0xFF8AD8, 10);
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.5f, 1.2f);
                run(p, l, pick, 2.0);
                return true;
            }
            return false;
        }
    }

    // ═════════ 10 얼티메이트: 모든 앱 동시 실행
    static final class Ultimate implements Fx {
        final ServerPlayer p; final ServerLevel l; final Vec3 at; final double k; int t = 0; final String tag;
        Ultimate(ServerPlayer p, ServerLevel l, Vec3 at, double k) {
            this.p = p; this.l = l; this.at = at; this.k = k; tag = tag(p);
            // 하늘에 거대한 폰 화면
            Holo.text(l, tag, at.add(0, 30, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("ULTIMATE\\n", 0xFF5050, true) + ","
                    + Holo.tx(String.join(" ", ICON), 0xFFFFFF, true) + "]}", "center", 0xE0101018, false, Holo.m(Holo.I(), 0.01, 8, 8, Vec3.ZERO, false), 90, 0)
                    .key(1, Holo.m(Holo.I(), 8, 8, 8, Vec3.ZERO, false), 6).key(80, Holo.m(Holo.I(), 8, 0.01, 8, Vec3.ZERO, false), 8);
            for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(at) < 60) JjkNet.toPlayer(sp, new Payloads.Screen(4, 0, 2000, 1.2f));
            Holo.sfx(l, at, "riser", 3f, 0.8f);
            if (!SHIELDS.containsKey(p.getUUID())) SHIELDS.put(p.getUUID(), new Shield(p, l, k));
            generator(p, l, k);
        }
        public boolean tick() {
            t++;
            if (t == 10) FX.add(new Revolution(p, l, k));
            if (t == 14) FX.add(new Infinity(p, l, k));
            if (t >= 20 && t <= 60 && t % 8 == 4) {
                double a = (t - 20) / 8.0 * Math.PI * 2 / 5;
                omega(p, l, at.add(Math.cos(a) * 10, 0, Math.sin(a) * 10), k);
            }
            if (t == 70) omega(p, l, at, k * 1.6);
            if (t == 90) FX.add(new Call(p, l, at, k));
            return t >= 90;
        }
    }

    // ═════════ 11 전화: 개발자에게 지원 요청
    static final class Call implements Fx {
        final ServerPlayer p; final ServerLevel l; final Vec3 at; final double k; final String tag; int t = 0;
        static final String[] TALK = {"☎ 뚜르르르…", "☎ 뚜르르르…", "📞 개발자: 여보세요?", "📞 나: 좌표 보낼게. 지금 쏴줘", "📞 개발자: …진짜? 알았어", "📞 개발자: /execute positioned 좌표 run summon orbital_laser"};
        Call(ServerPlayer p, ServerLevel l, Vec3 at, double k) {
            this.p = p; this.l = l; this.at = at; this.k = k; tag = tag(p);
            Holo.circle(l, tag, at.add(0, 0.1, 0), 10 * k, 0x40E8FF, 110, 0);
            Holo.banner(l, tag, at.add(0, 3, 0), banner("◎ 좌표 전송: " + String.format(Locale.ROOT, "%.0f %.0f %.0f", at.x, at.y, at.z), 0x40E8FF), 1.2, 100);
        }
        public boolean tick() {
            t++;
            if (t % 14 == 1 && (t - 1) / 14 < TALK.length) {
                int i = (t - 1) / 14;
                p.displayClientMessage(Component.literal("§b" + TALK[i]), true);
                Holo.banner(l, tag, p.position().add(0, 2.8, 0), banner(TALK[i], 0x40E8FF), 0.8, 14);
                if (i < 2) { l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.2f, 1.4f);
                    l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.2f, 1.2f); }
                else l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.PLAYERS, 1f, 1.5f);
            }
            if (t == 86) {
                for (int i = 0; i < 3; i++) Holo.pillar(l, tag, at.add((i - 1) * 4, 0, 0), 200, 3.5 * k, i == 1 ? 0xFFFFFF : 0x40E8FF, 4, 14, 10);
                Holo.sfx(l, at, "laser", 3f, 0.4f);
                Holo.sfx(l, at, "charge", 3f, 0.6f);
            }
            if (t >= 90 && t <= 100 && t % 2 == 0) {
                for (LivingEntity e : foes(l, p, at, 10 * k)) hurt(l, p, e, (float) (40 * k));
                Holo.explosion(l, tag, at.add((l.random.nextDouble() - 0.5) * 12, 0, (l.random.nextDouble() - 0.5) * 12), 5, 0x40E8FF);
            }
            if (t == 100) {
                Holo.shock(l, tag, at, 26 * k, 0x40E8FF, 14);
                Holo.sfx(l, at, "boom", 3f, 0.5f);
                Titan.shake(l, at, 70, true);
                p.displayClientMessage(Component.literal("§b📞 개발자: 끊는다~"), true);
            }
            return t >= 104;
        }
    }
}
