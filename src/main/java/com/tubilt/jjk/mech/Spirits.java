package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;
import java.util.function.Supplier;

/**
 * 주령(呪霊) — 디스플레이 엔티티로 그린 적 12종. 몸(히트박스)은 투명한 실제 몹, 겉모습은 블록 디스플레이 리그가 따라다니며
 * 걷기·날갯짓·입 벌리기·촉수·궤도 검 같은 부위 애니메이션(회전축 기준)을 매 2틱 키프레임으로 보간한다.
 *  4급: 파리머리 · 손가락 · 눈알   3급: 왜곡 인형 · 지네 · 입   2급: 거미 · 백귀 · 사신   1급: 거인 · 저주룡   특급: 저주의 왕
 * 얻는 법: 밤/어두운 곳에서 플레이어 근처에 자연 스폰 · 개발자 탭 「주령 소환서」(웅크리고 우클릭 = 종류 변경, 우클릭 = 소환)
 */
public final class Spirits {
    private Spirits() {}

    static final Random R = new Random();
    static final String TAGP = "jjk.spirit.";

    // ═════════════════════════ 부위 정의
    /** 블록 디스플레이 한 조각: 크기·중심(몸 기준, 앞=+z) · 회전 중심 · 정적 회전 · 애니메이션(축·모드·진폭·속도·위상). */
    static final class Pt {
        final String block; final int glow; final double sx, sy, sz, cx, cy, cz;
        double px, py, pz; boolean hasPiv; double rx, ry, rz; char axis = 0; int mode; double amp, freq, phase;
        Holo.Elem el;
        Pt(String b, int glow, double sx, double sy, double sz, double cx, double cy, double cz) { block = b; this.glow = glow; this.sx = sx; this.sy = sy; this.sz = sz; this.cx = cx; this.cy = cy; this.cz = cz; }
        Pt piv(double x, double y, double z) { px = x; py = y; pz = z; hasPiv = true; return this; }
        Pt rot(double x, double y, double z) { rx = x; ry = y; rz = z; return this; }
        /** mode 0 사인 · 1 회전(freq×t) · 2 걷기(이동 속도 비례) · 3 입 열고닫기(0~amp) */
        Pt anim(char axis, int mode, double amp, double freq, double phase) { this.axis = axis; this.mode = mode; this.amp = amp; this.freq = freq; this.phase = phase; return this; }
    }
    static Pt pt(String b, int glow, double sx, double sy, double sz, double cx, double cy, double cz) { return new Pt(b, glow, sx, sy, sz, cx, cy, cz); }

    static final class Kind {
        final String id, name; final int grade; final EntityType<?> type; final double hp, dmg, speed, scale, kb, weight;
        final Supplier<List<Pt>> parts; final int chainN; final String ability;
        Kind(String id, String name, int grade, EntityType<?> type, double hp, double dmg, double speed, double scale, double kb, double weight, Supplier<List<Pt>> parts, int chainN, String ability) {
            this.id = id; this.name = name; this.grade = grade; this.type = type; this.hp = hp; this.dmg = dmg; this.speed = speed; this.scale = scale; this.kb = kb; this.weight = weight;
            this.parts = parts; this.chainN = chainN; this.ability = ability;
        }
        String label() { return (grade == 0 ? "특급" : grade + "급") + " " + name; }
    }

    static final String BLK = "black_concrete", DK = "gray_concrete", LG = "light_gray_concrete", PUR = "purple_concrete", BONE = "white_concrete", FLESH = "pink_terracotta",
            BLD = "red_terracotta", MAG = "magenta_terracotta", GOLD = "gold_block", GLASS = "light_gray_stained_glass", GREEN = "green_terracotta";

    static List<Pt> humanoid(String skin, String cloth, double w, double armLen, double headS) {
        List<Pt> o = new ArrayList<>();
        o.add(pt(cloth, -1, .5 * w, .8, .3 * w, 0, 1.1, 0).anim('z', 0, .02, .09, 0));
        o.add(pt(skin, -1, headS, headS, headS, 0, 1.55 + headS / 2, 0).anim('x', 0, .05, .07, 1));
        for (int s = -1; s <= 1; s += 2) {
            o.add(pt(cloth, -1, .18 * w, .7, .2 * w, s * .13 * w, .35, 0).piv(s * .13 * w, .7, 0).anim('x', 2, .75, .5, s > 0 ? 0 : Math.PI));
            o.add(pt(skin, -1, .14 * w, armLen, .14 * w, s * .34 * w, 1.5 - armLen / 2, 0).piv(s * .34 * w, 1.5, 0).anim('x', 2, .7, .5, s > 0 ? Math.PI : 0));
        }
        return o;
    }
    static void eyes(List<Pt> o, double headY, double z, double gap, double sz) {
        for (int s = -1; s <= 1; s += 2) o.add(pt("red_concrete", 0xFF2020, sz, sz * .7, .05, s * gap, headY, z));
    }

    static final List<Kind> KINDS = new ArrayList<>();
    static {
        // ── 4급
        KINDS.add(new Kind("fly", "파리머리", 4, EntityType.VEX, 14, 3, .5, 1.3, 0, 5, () -> {
            List<Pt> o = new ArrayList<>();
            o.add(pt(BLK, -1, .5, .5, .6, 0, .5, 0).anim('x', 0, .08, .35, 0));
            o.add(pt(DK, -1, .4, .4, .4, 0, .62, .38));
            for (int s = -1; s <= 1; s += 2) {
                o.add(pt("red_concrete", 0xFF2020, .26, .3, .2, s * .17, .72, .52).anim('y', 0, .1, .4, s));
                o.add(pt(GLASS, -1, .75, .02, .4, s * .5, .78, -.05).piv(s * .12, .78, -.05).anim('z', 0, 1.0, 2.6, s > 0 ? 0 : Math.PI));
                o.add(pt(GLASS, -1, .55, .02, .3, s * .42, .7, -.35).piv(s * .12, .72, -.3).anim('z', 0, .9, 2.6, s > 0 ? 1 : 1 + Math.PI));
                for (int z = -1; z <= 1; z++) o.add(pt(BLK, -1, .06, .3, .06, s * .15, .22, z * .18).piv(s * .15, .4, z * .18).anim('x', 0, .5, .3, z + s));
            }
            return o;
        }, 0, "swarm"));
        KINDS.add(new Kind("hand", "손가락 주령", 4, EntityType.SPIDER, 18, 4, .4, .75, 0, 5, () -> {
            List<Pt> o = new ArrayList<>();
            o.add(pt(LG, -1, .8, .26, .85, 0, .34, 0).anim('x', 0, .03, .2, 0));
            o.add(pt(BLD, -1, .45, .3, .25, 0, .3, -.52));
            o.add(pt("red_concrete", 0xFF2020, .22, .22, .06, 0, .5, .05));
            o.add(pt("black_concrete", -1, .1, .1, .07, 0, .5, .09));
            for (int i = 0; i < 4; i++) {
                double x = -.3 + i * .2;
                o.add(pt(LG, -1, .14, .13, .5, x, .3, .62).piv(x, .3, .44).anim('x', 2, .6, .9, i * .9));
                o.add(pt(BONE, -1, .15, .15, .1, x, .3, .9).piv(x, .3, .44).anim('x', 2, .6, .9, i * .9));
            }
            o.add(pt(LG, -1, .15, .13, .45, .5, .32, .25).rot(0, -.7, 0).piv(.42, .32, .1).anim('y', 0, .35, .3, 0));
            return o;
        }, 0, "leap"));
        KINDS.add(new Kind("eye", "눈알 주령", 4, EntityType.VEX, 16, 3, .45, 1.2, 0, 4, () -> {
            List<Pt> o = new ArrayList<>();
            double[][] r = {{0, 0, 0}, {Math.PI / 4, Math.PI / 4, 0}, {0, Math.PI / 4, Math.PI / 4}, {Math.PI / 4, 0, Math.PI / 4}};
            for (double[] q : r) o.add(pt(BONE, -1, .62, .62, .62, 0, .65, 0).rot(q[0], q[1], q[2]).anim('y', 0, .05, .2, 0));
            o.add(pt("red_concrete", -1, .34, .34, .12, 0, .65, .38).anim('y', 0, .25, .12, 0));
            o.add(pt("black_concrete", 0x000000, .16, .16, .06, 0, .65, .46).anim('y', 0, .25, .12, 0));
            for (int i = 0; i < 5; i++) {
                double a = i * 1.257;
                o.add(pt(BLD, -1, .08, .55, .08, Math.cos(a) * .2, .25, -.3 + Math.sin(a) * .18).piv(Math.cos(a) * .2, .55, -.3).anim('x', 0, .55, .18, i));
            }
            return o;
        }, 0, "laser"));
        // ── 3급
        KINDS.add(new Kind("doll", "왜곡 인형", 3, EntityType.HUSK, 46, 6, .26, 1.0, 0, 3, () -> {
            List<Pt> o = humanoid(FLESH, DK, 1.0, 1.35, .46);
            o.add(pt(BLK, -1, .34, .05, .05, 0, 1.78, .24));
            for (int i = -2; i <= 2; i++) o.add(pt(BLK, -1, .03, .1, .05, i * .07, 1.78, .24));
            eyes(o, 1.95, .24, .11, .1);
            o.add(pt(BLK, -1, .08, .5, .04, 0, 1.15, .16));
            return o;
        }, 0, "scream"));
        KINDS.add(new Kind("centi", "지네 주령", 3, EntityType.SPIDER, 56, 7, .34, 1.0, 0, 3, null, 10, "venom"));
        KINDS.add(new Kind("maw", "입 주령", 3, EntityType.HUSK, 80, 11, .24, 1.5, .3, 3, () -> {
            List<Pt> o = new ArrayList<>();
            o.add(pt(MAG, -1, 1.2, 1.3, .9, 0, .85, -.5).anim('x', 0, .03, .12, 0));
            o.add(pt(DK, -1, 1.4, .55, 1.3, 0, 1.45, .35));
            o.add(pt(BONE, -1, 1.3, .16, .06, 0, 1.12, .98));
            for (int i = 0; i < 6; i++) o.add(pt(BONE, -1, .16, .26, .12, -.55 + i * .22, 1.2, .98));
            // 아래턱 + 이빨 + 혀 (회전축 = 턱 뒤쪽)
            double[] pv = {0, .75, -.2};
            o.add(pt(BLD, -1, 1.35, .45, 1.2, 0, .55, .38).piv(pv[0], pv[1], pv[2]).anim('x', 3, .8, .16, 0));
            o.add(pt("red_concrete", -1, .55, .1, .9, 0, .78, .4).piv(pv[0], pv[1], pv[2]).anim('x', 3, .8, .16, 0));
            for (int i = 0; i < 6; i++) o.add(pt(BONE, -1, .15, .24, .12, -.55 + i * .22, .9, .95).piv(pv[0], pv[1], pv[2]).anim('x', 3, .8, .16, 0));
            for (int i = -1; i <= 1; i++) o.add(pt("red_concrete", 0xFF2020, .22, .22, .05, i * .38, 1.78, .55));
            for (int s = -1; s <= 1; s += 2) o.add(pt(DK, -1, .3, .6, .3, s * .45, .3, -.5).piv(s * .45, .6, -.5).anim('x', 2, .6, .5, s > 0 ? 0 : Math.PI));
            return o;
        }, 0, "lunge"));
        // ── 2급
        KINDS.add(new Kind("spider", "거미 주령", 2, EntityType.SPIDER, 130, 13, .38, 2.0, .4, 2, () -> {
            List<Pt> o = new ArrayList<>();
            o.add(pt(BLK, -1, 1.0, .85, 1.3, 0, .85, -.85).anim('x', 0, .04, .15, 0));
            o.add(pt(DK, -1, .8, .65, .85, 0, .8, .3));
            o.add(pt(PUR, -1, .5, .08, .6, 0, 1.3, -.8));
            for (int i = 0; i < 4; i++) { double x = -.2 + (i % 2) * .4, y = .95 + (i / 2) * .2; o.add(pt("red_concrete", 0xFF2020, .13, .13, .05, x, y, .75)); }
            o.add(pt(BONE, -1, .1, .3, .1, -.18, .5, .72).anim('y', 0, .2, .3, 0)); o.add(pt(BONE, -1, .1, .3, .1, .18, .5, .72).anim('y', 0, .2, .3, 1));
            for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 4; i++) {
                double z = .55 - i * .33, ph = (i % 2 == 0 ? 0 : Math.PI) + (s > 0 ? 0 : Math.PI);
                o.add(pt(BLK, -1, 1.1, .1, .1, s * .95, .75, z).rot(0, 0, s * -.55).piv(s * .35, .8, z).anim('y', 2, .45, .6, ph));
                o.add(pt(DK, -1, .1, .85, .1, s * 1.55, .35, z).piv(s * .35, .8, z).anim('y', 2, .45, .6, ph));
            }
            return o;
        }, 0, "web"));
        KINDS.add(new Kind("hundred", "백귀", 2, EntityType.HUSK, 115, 11, .24, 1.4, .2, 1.5, () -> {
            List<Pt> o = humanoid(BONE, PUR, 1.4, .0001, .5);
            o.removeIf(q -> q.sy < .01);   // 기본 팔 제거
            eyes(o, 2.0, .26, .13, .1);
            o.add(pt("red_concrete", 0xFF2020, .12, .12, .05, 0, 2.2, .26));
            for (int i = 0; i < 8; i++) {
                int s = i % 2 == 0 ? 1 : -1; double h = 1.1 + (i / 2) * .22;
                o.add(pt(BONE, -1, .11, .95, .11, s * .62, h - .35, .02).rot(0, 0, s * (.35 + (i / 2) * .22)).piv(s * .4, h, 0).anim('z', 0, .4, .13 + i * .01, i * 1.1));
                o.add(pt(BLK, -1, .16, .12, .16, s * (.82 + (i / 2) * .12), h - .78, .02).piv(s * .4, h, 0).anim('z', 0, .4, .13 + i * .01, i * 1.1));
            }
            return o;
        }, 0, "grab"));
        KINDS.add(new Kind("reaper", "사신", 2, EntityType.HUSK, 105, 12, .26, 1.25, .2, 1.5, () -> {
            List<Pt> o = new ArrayList<>();
            o.add(pt(BLK, -1, .75, .8, .5, 0, 1.15, 0).anim('x', 0, .04, .1, 0));
            o.add(pt(DK, -1, 1.0, .7, .6, 0, .55, 0).anim('x', 0, .05, .1, .6));
            o.add(pt(BLK, -1, 1.25, .5, .75, 0, .12, 0).anim('x', 0, .06, .1, 1.2));
            o.add(pt(BLK, -1, .62, .62, .62, 0, 1.95, .02).anim('x', 0, .04, .09, 0));
            o.add(pt("black_concrete", 0x000000, .4, .4, .05, 0, 1.92, .34));
            for (int s = -1; s <= 1; s += 2) {
                o.add(pt("white_concrete", 0xFFFFFF, .12, .06, .05, s * .11, 1.95, .36));
                o.add(pt(DK, -1, .2, .7, .22, s * .52, 1.2, .1).rot(.6, 0, s * .1));
            }
            o.add(pt(DK, -1, .09, 2.5, .09, .62, 1.3, .55).anim('z', 0, .08, .1, 0));
            o.add(pt("iron_block", -1, .06, .16, .95, .62, 2.6, .98).anim('z', 0, .08, .1, 0));
            o.add(pt("iron_block", -1, .06, .12, .5, .62, 2.5, 1.55).anim('z', 0, .08, .1, 0));
            return o;
        }, 0, "reap"));
        // ── 1급
        KINDS.add(new Kind("giant", "저주 거인", 1, EntityType.HUSK, 320, 21, .19, 2.6, 1.0, .6, () -> {
            List<Pt> o = humanoid(DK, BLK, 1.7, 1.2, .62);
            o.add(pt("orange_concrete", 0xFF7A00, .1, .6, .05, 0, 1.15, .17));
            o.add(pt("orange_concrete", 0xFF7A00, .5, .08, .05, 0, 1.3, .17));
            o.add(pt("orange_concrete", 0xFF7A00, .5, .08, .05, .05, .95, .17).rot(0, 0, .5));
            o.add(pt("red_concrete", 0xFF2020, .26, .26, .06, 0, 2.0, .32));
            o.add(pt("black_concrete", 0x000000, .1, .22, .06, 0, 2.0, .36));
            for (int s = -1; s <= 1; s += 2) {
                o.add(pt(BLK, -1, .38, .38, .38, s * .62, .5, 0).piv(s * .58, 1.5, 0).anim('x', 2, .7, .4, s > 0 ? Math.PI : 0));
                o.add(pt(DK, -1, .3, .22, .3, s * .45, 1.78, 0));
            }
            return o;
        }, 0, "slam"));
        KINDS.add(new Kind("dragon", "저주룡", 1, EntityType.VEX, 170, 13, .6, 1.6, .8, .5, null, 18, "breath"));
        // ── 특급
        KINDS.add(new Kind("king", "저주의 왕", 0, EntityType.HUSK, 420, 19, .24, 1.8, 1.0, .2, () -> {
            List<Pt> o = humanoid(BONE, BLK, 1.2, 1.1, .5);
            o.add(pt(PUR, -1, .85, 1.0, .5, 0, .35, 0).anim('x', 0, .04, .1, 0));
            o.add(pt(GOLD, -1, .8, .1, .5, 0, 1.2, 0));
            o.add(pt(GOLD, 0xFFD84A, .14, .26, .14, 0, 2.28, .1)); o.add(pt(GOLD, -1, .5, .08, .5, 0, 2.08, 0));
            for (int s = -1; s <= 1; s += 2) { o.add(pt(GOLD, -1, .1, .28, .1, s * .18, 2.28, 0)); o.add(pt(GOLD, -1, .1, .2, .1, s * .3, 2.24, 0)); }
            eyes(o, 2.0, .26, .11, .09);
            for (int i = 0; i < 4; i++) o.add(pt(GOLD, 0xFFD84A, 2.2, .06, .12, 0, 2.95, 0).rot(0, i * Math.PI / 4, 0).anim('y', 1, 0, .05, 0));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3, cx = Math.cos(a) * 1.6, cz = Math.sin(a) * 1.6;
                o.add(pt("iron_block", -1, .1, 1.0, .05, cx, 1.6, cz).piv(0, 1.6, 0).anim('y', 1, 0, .06, 0));
                o.add(pt(GOLD, 0xFFD84A, .32, .07, .12, cx, 2.1, cz).piv(0, 1.6, 0).anim('y', 1, 0, .06, 0));
                o.add(pt(BLK, -1, .07, .3, .07, cx, 2.3, cz).piv(0, 1.6, 0).anim('y', 1, 0, .06, 0));
            }
            return o;
        }, 0, "rain"));
    }
    static Kind kind(String id) { for (Kind k : KINDS) if (k.id.equals(id)) return k; return null; }

    // ═════════════════════════ 인스턴스
    static final class Spirit {
        final Mob mob; final Kind k; final List<Pt> parts; final double sc; final String tag;
        Vec3[] chain; double[] cyaw; List<Holo.Elem> chainEls = new ArrayList<>();
        int t = 0, cd = 60, a = 0; Vec3 last; double spd; boolean built;
        final List<Vec3> pend = new ArrayList<>(); final List<Integer> pendT = new ArrayList<>();
        Spirit(Mob m, Kind k) { mob = m; this.k = k; parts = k.parts == null ? new ArrayList<>() : k.parts.get(); sc = k.scale; tag = "jjk.spr" + (Holo.uid++); last = m.position(); }
    }
    static final Map<UUID, Spirit> LIVE = new HashMap<>();
    static final List<Mob> PENDING = new ArrayList<>();

    static float[] matrix(Pt p, double ang, double sc, double fl) {
        double[][] r0 = Holo.I();
        if (p.rx != 0 || p.ry != 0 || p.rz != 0) r0 = Holo.mul(Holo.rotY(p.ry), Holo.mul(Holo.rotX(p.rx), Holo.rotZ(p.rz)));
        double[][] ra = p.axis == 'x' ? Holo.rotX(ang) : p.axis == 'y' ? Holo.rotY(ang) : p.axis == 'z' ? Holo.rotZ(ang) : Holo.I();
        Vec3 c0 = new Vec3(p.cx, p.cy, p.cz).scale(sc);
        Vec3 pv = p.hasPiv ? new Vec3(p.px, p.py, p.pz).scale(sc) : c0;
        Vec3 c = pv.add(Holo.apply(ra, c0.subtract(pv)));
        return Holo.m(Holo.mul(ra, r0), p.sx * sc, p.sy * sc * fl, p.sz * sc, new Vec3(c.x, c.y * fl, c.z), true);
    }
    static double angle(Pt p, int t, double spd, double agro) {
        return switch (p.mode) {
            case 1 -> p.freq * t + p.phase;
            case 2 -> p.amp * spd * Math.sin(p.freq * t + p.phase) + p.amp * 0.1 * Math.sin(0.07 * t + p.phase);
            case 3 -> p.amp * (0.45 + 0.55 * agro) * (0.5 + 0.5 * Math.sin(p.freq * t + p.phase));
            default -> p.amp * Math.sin(p.freq * t + p.phase);
        };
    }

    // ═════════════════════════ 소환 / 입양
    public static Mob spawn(ServerLevel l, Vec3 at, Kind k) {
        Entity e = k.type.create(l, EntitySpawnReason.MOB_SUMMONED);
        if (!(e instanceof Mob m)) return null;
        m.snapTo(at.x, at.y, at.z, R.nextFloat() * 360, 0);
        m.addTag(TAGP + k.id);
        m.setCustomName(Component.literal("§5" + k.label()));
        m.setCustomNameVisible(false);
        m.setSilent(true);
        m.setPersistenceRequired();
        attr(m, Attributes.MAX_HEALTH, k.hp); attr(m, Attributes.ATTACK_DAMAGE, k.dmg); attr(m, Attributes.SCALE, k.scale);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, k.kb); attr(m, Attributes.FOLLOW_RANGE, 40);
        if (k.type != EntityType.VEX) attr(m, Attributes.MOVEMENT_SPEED, k.speed);
        m.setHealth((float) k.hp);
        m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        l.addFreshEntity(m);
        Holo.cmd(l, "data merge entity " + m.getStringUUID() + " {DeathLootTable:\"minecraft:empty\"}");
        Holo.ring(l, "jjk.sprfx", at.add(0, 0.1, 0), 0.5, 2.5 * Math.max(1, k.scale * .7), 20, 0.4, 0.05, 0x8040C0, 6, 0, 8, 0, 6, true);
        Holo.flare(l, "jjk.sprfx", at.add(0, k.scale * .8, 0), 3 + k.scale, 0x6020A0, 10);
        Holo.sfx(l, at, k.grade <= 1 ? "boom" : "deep", 1.2f, 0.6f);
        return m;
    }
    static void attr(Mob m, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v) {
        var inst = m.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    public static void onJoin(Entity e) {
        if (!(e instanceof Mob m)) return;
        for (String t : m.getTags()) if (t.startsWith(TAGP)) { if (!PENDING.contains(m) && !LIVE.containsKey(m.getUUID())) PENDING.add(m); return; }
    }

    static Kind kindOf(Mob m) {
        for (String t : m.getTags()) if (t.startsWith(TAGP)) return kind(t.substring(TAGP.length()));
        return null;
    }

    static void build(ServerLevel l, Mob m, Kind k) {
        Spirit s = new Spirit(m, k);
        LIVE.put(m.getUUID(), s);
        if (k.chainN > 0) {
            s.chain = new Vec3[k.chainN]; s.cyaw = new double[k.chainN];
            Vec3 b = m.position().add(0, k.id.equals("dragon") ? 1.0 : 0.4, 0);
            Arrays.fill(s.chain, b);
            for (int i = 0; i < k.chainN; i++) {
                final int ii = i;
                double u = i / (double) Math.max(1, k.chainN - 1);
                double sz = i == 0 ? (k.id.equals("dragon") ? 1.7 : 1.1) : (k.id.equals("dragon") ? 1.35 - u * .85 : .95 - u * .45);
                String blk = k.id.equals("dragon") ? (i == 0 ? PUR : i % 2 == 0 ? BLK : PUR) : (i == 0 ? "red_concrete" : i % 2 == 0 ? BLK : BLD);
                s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:" + blk, -1, Holo.m(Holo.I(), sz, sz * .85, sz * 1.5, Vec3.ZERO, true), 99999999, 2).attach(() -> s.chain[ii], () -> s.cyaw[ii]));
                if (k.id.equals("dragon")) {
                    if (i % 2 == 1 && i < k.chainN - 2) s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:" + GOLD, 0xFFD84A, Holo.m(Holo.I(), .12, sz * .6, sz * .7, new Vec3(0, sz * .75, 0), true), 99999999, 2).attach(() -> s.chain[ii], () -> s.cyaw[ii]));
                    if (i == 0) {
                        for (int q = -1; q <= 1; q += 2) {
                            s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:red_concrete", 0xFF2020, Holo.m(Holo.I(), .22, .22, .12, new Vec3(q * .6, .3, 1.25), true), 99999999, 2).attach(() -> s.chain[0], () -> s.cyaw[0]));
                            s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:" + BONE, -1, Holo.m(Holo.rotX(-.6), .16, .16, .9, new Vec3(q * .5, 1.0, -.2), true), 99999999, 2).attach(() -> s.chain[0], () -> s.cyaw[0]));
                            s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:" + GLASS, -1, Holo.m(Holo.rotZ(q * .3), 2.4, .03, 1.2, new Vec3(q * 1.4, .6, -.3), true), 99999999, 2).attach(() -> s.chain[3], () -> s.cyaw[3]));
                        }
                    }
                } else if (i == 0) {
                    for (int q = -1; q <= 1; q += 2)
                        s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:" + BONE, -1, Holo.m(Holo.I(), .12, .12, .7, new Vec3(q * .4, -.1, .95), true), 99999999, 2).attach(() -> s.chain[0], () -> s.cyaw[0]));
                } else if (i < k.chainN - 1) {
                    for (int q = -1; q <= 1; q += 2)
                        s.chainEls.add(Holo.block(l, s.tag, b, "minecraft:" + BLK, -1, Holo.m(Holo.I(), 1.1, .08, .12, new Vec3(q * sz * .6, -sz * .3, 0), true), 99999999, 2).attach(() -> s.chain[ii], () -> s.cyaw[ii]));
                }
            }
        }
        for (Pt p : s.parts) {
            p.el = Holo.block(l, s.tag, m.position(), "minecraft:" + p.block, p.glow, matrix(p, angle(p, 0, 0, 0), s.sc, 1), 99999999, 2).attach(() -> m.position(), () -> m.getYRot());
        }
        s.built = true;
    }

    static void killRig(Spirit s) {
        for (Pt p : s.parts) if (p.el != null) p.el.kill();
        for (Holo.Elem e : s.chainEls) e.kill();
    }

    // ═════════════════════════ 틱
    public static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            for (Mob m : new ArrayList<>(PENDING)) {
                PENDING.remove(m);
                if (m.isRemoved() || !(m.level() instanceof ServerLevel l)) continue;
                Kind k = kindOf(m);
                if (k != null && !LIVE.containsKey(m.getUUID())) build(l, m, k);
            }
        }
        if (LIVE.isEmpty()) { naturalSpawn(server); return; }
        for (Iterator<Spirit> it = LIVE.values().iterator(); it.hasNext(); ) {
            Spirit s = it.next();
            Mob m = s.mob;
            if (!(m.level() instanceof ServerLevel l) || m.isRemoved()) { killRig(s); it.remove(); continue; }
            if (!m.isAlive()) { death(l, s); killRig(s); it.remove(); continue; }
            try { step(l, s); } catch (Exception ex) { JjkMod.LOG.warn("[JJK] 주령 오류", ex); }
        }
        naturalSpawn(server);
    }

    static ServerPlayer near(ServerLevel l, Vec3 c, double r) {
        ServerPlayer best = null; double bd = r * r;
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double d = p.distanceToSqr(c.x, c.y, c.z);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }
    static boolean los(ServerLevel l, Vec3 a, Vec3 b) {
        BlockHitResult h = l.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return h.getType() == HitResult.Type.MISS;
    }
    static void hit(ServerLevel l, Spirit s, LivingEntity e, float dmg, boolean magic) {
        e.invulnerableTime = 0;
        e.hurtServer(l, magic ? l.damageSources().indirectMagic(s.mob, s.mob) : l.damageSources().mobAttack(s.mob), dmg);
    }
    static Vec3 mid(Entity e) { return e.position().add(0, e.getBbHeight() / 2, 0); }

    static void step(ServerLevel l, Spirit s) {
        Mob m = s.mob; Kind k = s.k;
        s.t++;
        Vec3 pos = m.position();
        s.spd = Math.min(1, pos.subtract(s.last).multiply(1, 0, 1).length() * 5);
        s.last = pos;
        ServerPlayer tgt = near(l, pos, 34);
        if (s.t % 20 == 1 && tgt != null && (m.getTarget() == null || !m.getTarget().isAlive())) m.setTarget(tgt);
        double dist = tgt == null ? 1e9 : tgt.position().distanceTo(pos);
        boolean agro = tgt != null && dist < 12;
        // 부위 애니메이션 (플레이어가 가까울 때만)
        if (s.t % 2 == 0 && near(l, pos, 72) != null) {
            double fl = 1 + (m.hurtTime > 0 ? 0.12 * m.hurtTime / 10.0 : 0);
            for (Pt p : s.parts) if (p.el != null && (p.axis != 0 || m.hurtTime > 0))
                p.el.keyframe(matrix(p, angle(p, s.t, s.spd, agro ? 1 : 0.2), s.sc, fl), 2);
        }
        if (m.hurtTime == 9) {
            Holo.flare(l, s.tag + "x", pos.add(0, m.getBbHeight() / 2, 0), 1.5 + s.sc * .5, 0xFF4040, 4);
            Holo.sfx(l, pos, "impact", 0.5f, 1.4f);
        }
        // 사슬형 몸통
        if (s.chain != null) {
            boolean dr = k.id.equals("dragon");
            Vec3 h = pos.add(0, dr ? 1.0 : 0.45, 0);
            if (dr) h = h.add(0, Math.sin(s.t * 0.12) * 0.25, 0);
            s.chain[0] = h;
            Vec3 face = tgt != null ? tgt.position().subtract(pos) : m.getLookAngle();
            if (face.horizontalDistanceSqr() > 1e-4) s.cyaw[0] = Math.toDegrees(Math.atan2(-face.x, face.z));
            double gap = dr ? 1.4 : 0.8;
            for (int i = 1; i < s.chain.length; i++) {
                Vec3 d = s.chain[i - 1].subtract(s.chain[i]); double dl = d.length();
                if (dl > gap) s.chain[i] = s.chain[i - 1].subtract(d.scale(gap / dl));
                if (dl > 1e-3) {
                    if (dr) s.chain[i] = s.chain[i].add(0, Math.sin(s.t * 0.15 - i * 0.6) * 0.04, 0);
                    s.cyaw[i] = Math.toDegrees(Math.atan2(-d.x, d.z));
                }
            }
        }
        // 분위기 입자
        if (s.t % 5 == 0) {
            Vec3 c = pos.add(0, m.getBbHeight() * .5, 0);
            l.sendParticles(k.grade <= 1 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE, c.x, c.y, c.z, k.grade <= 1 ? 2 : 1, m.getBbWidth() * .4, m.getBbHeight() * .3, m.getBbWidth() * .4, 0.01);
        }
        ability(l, s, tgt, dist, pos);
    }

    static void ability(ServerLevel l, Spirit s, ServerPlayer tgt, double dist, Vec3 pos) {
        Mob m = s.mob; Kind k = s.k;
        if (s.cd > 0) s.cd--;
        Vec3 eye = pos.add(0, m.getBbHeight() * .7, 0);
        Vec3 to = tgt == null ? null : mid(tgt);
        switch (k.ability) {
            case "leap" -> {
                if (tgt != null && s.cd <= 0 && dist > 3 && dist < 9 && m.onGround()) {
                    s.cd = 70;
                    Vec3 d = tgt.position().subtract(pos).multiply(1, 0, 1).normalize();
                    m.setDeltaMovement(d.x * 0.95, 0.5, d.z * 0.95); m.hurtMarked = true;
                    Holo.sfx(l, pos, "whoosh", 0.7f, 1.6f);
                }
            }
            case "laser" -> {
                if (s.a > 0) {
                    s.a--;
                    if (s.a % 3 == 0) Holo.flare(l, s.tag + "x", eye.add(m.getLookAngle().scale(0.5)), 1.0 + (18 - s.a) * 0.08, 0xFF2020, 4);
                    if (s.a == 0 && tgt != null && los(l, eye, to)) {
                        Holo.beam(l, s.tag + "x", eye, to, 0.18, 0xFF2020, 1, 3, 5);
                        Holo.beam(l, s.tag + "x", eye, to, 0.06, 0xFFFFFF, 1, 3, 5);
                        hit(l, s, tgt, 7f, true);
                        Holo.sfx(l, eye, "laser", 0.8f, 1.5f);
                    }
                } else if (tgt != null && s.cd <= 0 && dist < 18 && los(l, eye, to)) { s.cd = 90; s.a = 18; Holo.sfx(l, eye, "charge", 0.6f, 1.8f); }
            }
            case "scream" -> {
                if (tgt != null && s.cd <= 0 && dist < 10) {
                    s.cd = 140;
                    Holo.ring(l, s.tag + "x", pos.add(0, 1, 0), 1, 9, 24, 0.4, 0.05, 0xA040FF, 3, 2, 8, 0, 4, true);
                    Holo.shock(l, s.tag + "x", pos, 9, 0x8030C0, 8);
                    Holo.sfx(l, pos, "siren", 1.0f, 0.8f);
                    for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(pos) < 9) {
                        hit(l, s, p, 3f, true);
                        p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                    }
                }
            }
            case "venom" -> {
                if (s.cd <= 0) for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(pos) < 2.8) {
                    s.cd = 40; hit(l, s, p, 3f, false);
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                    l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1, p.getZ(), 8, .3, .5, .3, .05);
                }
            }
            case "lunge" -> {
                if (s.a > 0) {
                    s.a--;
                    for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(pos.add(0, .8, 0)) < 2.8 * k.scale / 1.5 + .6) {
                        hit(l, s, p, 12f, false); s.a = 0;
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                        Holo.flare(l, s.tag + "x", mid(p), 2.4, 0xFF2040, 5); Holo.sfx(l, mid(p), "slash", 1f, 0.7f);
                    }
                } else if (tgt != null && s.cd <= 0 && dist > 4 && dist < 11) {
                    s.cd = 85; s.a = 14;
                    Vec3 d = tgt.position().subtract(pos).multiply(1, 0, 1).normalize();
                    m.setDeltaMovement(d.x * 1.15, 0.38, d.z * 1.15); m.hurtMarked = true;
                    Holo.sfx(l, pos, "whoosh", 1f, 0.6f);
                }
            }
            case "web" -> {
                if (tgt != null && s.cd <= 0 && dist > 4 && dist < 16 && los(l, eye, to)) {
                    s.cd = 100;
                    Holo.beam(l, s.tag + "x", eye, to, 0.1, 0xE8E8E8, 1, 4, 8);
                    Holo.beam(l, s.tag + "x", eye, to, 0.3, 0xB0B0C0, 1, 4, 8);
                    hit(l, s, tgt, 4f, true);
                    tgt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
                    Holo.flare(l, s.tag + "x", to, 3, 0xFFFFFF, 8);
                    l.sendParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 20, .5, .7, .5, .02);
                    Holo.sfx(l, eye, "magic", 0.8f, 1.3f);
                }
            }
            case "grab" -> {
                if (tgt != null && s.cd <= 0 && dist > 4 && dist < 13 && los(l, eye, to)) {
                    s.cd = 120;
                    for (int i = 0; i < 4; i++) Holo.beam(l, s.tag + "x", eye.add(0, i * .15 - .2, 0), to.add(0, i * .1 - .15, 0), 0.07, i % 2 == 0 ? 0xC080FF : 0xFFFFFF, 1, 6, 6);
                    Vec3 d = pos.subtract(tgt.position()).normalize();
                    tgt.setDeltaMovement(d.x * 1.0, 0.3, d.z * 1.0); tgt.hurtMarked = true;
                    hit(l, s, tgt, 5f, true);
                    tgt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                    Holo.sfx(l, eye, "plate", 1f, 0.7f);
                }
            }
            case "reap" -> {
                if (s.a > 0) {
                    s.a--;
                    Holo.flare(l, s.tag + "x", pos.add(0, 1.4, 0), 1 + (14 - s.a) * 0.15, 0xA040FF, 3);
                    if (s.a == 0) {
                        Holo.ring(l, s.tag + "x", pos.add(0, 1.3, 0), 1, 5.5, 28, 0.5, 0.06, 0xC080FF, 2, 2, 6, 0, 8, true);
                        for (int i = 0; i < 8; i++) Holo.beam(l, s.tag + "x", pos.add(0, 1.3, 0), pos.add(Math.cos(i * .785) * 5.5, 1.3, Math.sin(i * .785) * 5.5), 0.1, 0xFFFFFF, 1, 2, 5);
                        for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(pos) < 5.5) {
                            hit(l, s, p, 14f, true);
                            Vec3 d = p.position().subtract(pos).normalize();
                            p.setDeltaMovement(d.x * 1.0, 0.45, d.z * 1.0); p.hurtMarked = true;
                        }
                        Holo.sfx(l, pos, "slash", 1.4f, 0.6f);
                    }
                } else if (tgt != null && s.cd <= 0) {
                    if (dist > 9) {
                        s.cd = 60;
                        Vec3 look = tgt.getLookAngle().multiply(1, 0, 1).normalize();
                        Vec3 np = tgt.position().subtract(look.scale(2.2));
                        Holo.flare(l, s.tag + "x", pos.add(0, 1, 0), 3, 0x6020A0, 6);
                        m.teleportTo(np.x, np.y, np.z);
                        Holo.flare(l, s.tag + "x", np.add(0, 1, 0), 3, 0x6020A0, 6);
                        Holo.sfx(l, np, "glitch", 1f, 0.7f);
                    } else if (dist < 5) { s.cd = 100; s.a = 14; Holo.sfx(l, pos, "charge", 1f, 0.7f); }
                }
            }
            case "slam" -> {
                if (s.a > 0) {
                    s.a--;
                    if (s.a % 4 == 0) Holo.ring(l, s.tag + "x", pos.add(0, .1, 0), 8, 8.5, 24, 0.3, 0.04, 0xFF7A00, 1, 3, 3, 0, 0, true);
                    if (s.a == 0) {
                        Holo.shock(l, s.tag + "x", pos, 9, 0xFF9020, 10);
                        Holo.explosion(l, s.tag + "x", pos.add(0, .5, 0), 4, 0xFF7A00);
                        Holo.shards(l, s.tag + "x", pos.add(0, .5, 0), "minecraft:stone", 12, 1.0);
                        Titan.shake(l, pos, 40, true);
                        Holo.sfx(l, pos, "boom", 1.6f, 0.5f);
                        for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(pos) < 9) {
                            hit(l, s, p, 22f, false);
                            Vec3 d = p.position().subtract(pos).normalize();
                            p.setDeltaMovement(d.x * 1.3, 0.8, d.z * 1.3); p.hurtMarked = true;
                        }
                    }
                } else if (tgt != null && s.cd <= 0 && dist < 11) { s.cd = 140; s.a = 24; Holo.sfx(l, pos, "riser", 1.3f, 0.5f); }
            }
            case "breath" -> {
                Vec3 h = s.chain == null ? eye : s.chain[0].add(0, .3, 0);
                if (s.a > 0) {
                    s.a--;
                    if (s.a % 3 == 0) Holo.flare(l, s.tag + "x", h, 1.2 + (22 - s.a) * 0.12, 0xA040FF, 4);
                    if (s.a == 0 && tgt != null) {
                        Vec3 d = tgt.getEyePosition().subtract(h).normalize();
                        Vec3 end = h.add(d.scale(18));
                        Holo.beam(l, s.tag + "x", h, end, 2.2, 0x8030C0, 2, 8, 8);
                        Holo.beam(l, s.tag + "x", h, end, 0.9, 0xFFFFFF, 2, 8, 8);
                        for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative()) {
                            Vec3 q = mid(p).subtract(h); double u = Math.max(0, Math.min(18, q.dot(d)));
                            if (q.subtract(d.scale(u)).length() < 2.0) { hit(l, s, p, 13f, true); p.setRemainingFireTicks(60); }
                        }
                        Holo.sfx(l, h, "laser", 1.5f, 0.5f);
                    }
                } else if (tgt != null && s.cd <= 0 && dist < 24 && dist > 5) { s.cd = 120; s.a = 22; Holo.sfx(l, h, "charge", 1.2f, 0.6f); }
            }
            case "rain" -> {
                for (int i = s.pend.size() - 1; i >= 0; i--) {
                    Vec3 c = s.pend.get(i); int t = s.pendT.get(i) - 1;
                    s.pendT.set(i, t);
                    if (t % 5 == 0) Holo.ring(l, s.tag + "x", c.add(0, .1, 0), 3.2, 3.6, 20, 0.25, 0.03, 0xFFD84A, 1, 4, 2, 0, 0, true);
                    if (t <= 0) {
                        Holo.pillar(l, s.tag + "x", c, 24, 1.6, 0xFFD84A, 2, 4, 8);
                        Holo.shock(l, s.tag + "x", c, 4, 0xFFFFFF, 6);
                        Holo.sfx(l, c, "thunder", 1.2f, 1.2f);
                        for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(c) < 3.4) { hit(l, s, p, 18f, true); p.setDeltaMovement(0, 0.6, 0); p.hurtMarked = true; }
                        s.pend.remove(i); s.pendT.remove(i);
                    }
                }
                if (tgt != null && s.cd <= 0 && dist < 28) {
                    s.cd = 150;
                    int n = 0;
                    for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.position().distanceTo(pos) < 28 && n++ < 4) {
                        s.pend.add(p.position()); s.pendT.add(28);
                        Holo.beam(l, s.tag + "x", eye.add(0, 1, 0), p.position().add(0, 6, 0), 0.08, 0xFFD84A, 1, 4, 6);
                    }
                    Holo.sfx(l, pos, "choir", 1.3f, 0.7f);
                }
            }
            default -> { }
        }
    }

    static void death(ServerLevel l, Spirit s) {
        Vec3 c = s.mob.position().add(0, s.mob.getBbHeight() / 2, 0);
        double sz = 2 + s.sc * 2;
        Holo.flare(l, s.tag + "x", c, sz, 0x8040C0, 12);
        Holo.explosion(l, s.tag + "x", c, sz * .7, 0x6020A0);
        Holo.shards(l, s.tag + "x", c, "minecraft:" + (s.parts.isEmpty() ? BLK : s.parts.get(0).block), 8 + (int) (s.sc * 4), 0.8);
        Holo.sfx(l, c, "shatter", 1.2f, 0.7f);
        l.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 30, s.mob.getBbWidth() * .5, s.mob.getBbHeight() * .4, s.mob.getBbWidth() * .5, 0.08);
        if (s.k.grade <= 1 && s.mob.getLastHurtByMob() instanceof ServerPlayer p) Koming.bar(p, "§5" + s.k.label() + " §7— 조복");
    }

    // ═════════════════════════ 자연 스폰
    static void naturalSpawn(MinecraftServer server) {
        if (server.getTickCount() % 400 != 0) return;
        if (LIVE.size() >= 14) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !(p.level() instanceof ServerLevel l)) continue;
            if (l.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) continue;
            if (R.nextInt(100) >= 35) continue;
            int nearN = 0;
            for (Spirit s : LIVE.values()) if (s.mob.level() == l && s.mob.distanceTo(p) < 64) nearN++;
            if (nearN >= 4) continue;
            double tot = 0; for (Kind k : KINDS) tot += k.weight;
            double r = R.nextDouble() * tot; Kind pick = KINDS.get(0);
            for (Kind k : KINDS) { r -= k.weight; if (r <= 0) { pick = k; break; } }
            for (int tries = 0; tries < 10; tries++) {
                double a = R.nextDouble() * Math.PI * 2, d = 20 + R.nextDouble() * 16;
                BlockPos b = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY() + 6, p.getZ() + Math.sin(a) * d);
                if (!l.hasChunkAt(b)) continue;
                BlockPos g = null;
                for (int dy = 0; dy < 16; dy++) {
                    BlockPos q = b.below(dy);
                    if (l.getBlockState(q).isAir() && l.getBlockState(q.above()).isAir() && l.getBlockState(q.above(2)).isAir() && !l.getBlockState(q.below()).isAir()) { g = q; break; }
                }
                if (g == null || l.getMaxLocalRawBrightness(g) > 9) continue;
                spawn(l, Vec3.atBottomCenterOf(g), pick);
                break;
            }
        }
    }

    // ═════════════════════════ 주령 소환서
    static final Map<UUID, Integer> SEL = new HashMap<>();
    public static boolean onUse(Player ent, ItemStack st) {
        if (st.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(st.getItem());
        if (!key.getNamespace().equals(JjkMod.MOD_ID)) return false;
        String path = key.getPath();
        if (path.startsWith("spirit_egg_")) {   // 소환 알: 바라보는 곳에 1마리, 소모
            Kind ek = kind(path.substring("spirit_egg_".length()));
            if (ek == null) return false;
            if (!(ent instanceof ServerPlayer ep) || !(ep.level() instanceof ServerLevel el)) return true;
            Vec3 e0 = ep.getEyePosition();
            BlockHitResult eh = el.clip(new ClipContext(e0, e0.add(ep.getLookAngle().scale(14)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 at0 = eh.getType() == HitResult.Type.MISS ? ep.position().add(ep.getLookAngle().multiply(1, 0, 1).normalize().scale(4)) : eh.getLocation().add(0, 0.1, 0);
            spawn(el, at0, ek);
            if (!ep.isCreative()) st.shrink(1);
            Koming.bar(ep, "§5" + ek.label() + " §7소환");
            return true;
        }
        if (!path.equals("spirit_scroll")) return false;
        if (!(ent instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel l)) return true;
        int i = SEL.getOrDefault(p.getUUID(), 0);
        if (p.isShiftKeyDown()) {
            i = (i + 1) % KINDS.size();
            SEL.put(p.getUUID(), i);
            Kind k = KINDS.get(i);
            Koming.bar(p, "§5주령 소환서 §f" + (i + 1) + "/" + KINDS.size() + " §7— " + k.label());
            return true;
        }
        Kind k = KINDS.get(i);
        Vec3 eye = p.getEyePosition();
        BlockHitResult h = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(24)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 at = h.getType() == HitResult.Type.MISS ? eye.add(p.getLookAngle().scale(10)) : h.getLocation().add(0, 0.1, 0);
        spawn(l, at, k);
        Koming.bar(p, "§5" + k.label() + " §7소환");
        return true;
    }

    public static void stopAll() {
        for (Spirit s : LIVE.values()) killRig(s);
        LIVE.clear(); PENDING.clear();
    }
}
