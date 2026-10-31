package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 홀로 연출 엔진 — 디스플레이 엔티티의 "클라이언트 보간"을 최대한 활용.
 *  · 소환 후 키프레임(변환 행렬 + 보간 시간)만 몇 번 보내면 클라이언트가 매끄럽게 애니메이션 → 끊김 없음, 명령 최소화
 *  · 회전은 엔티티 yaw/pitch 를 teleport_duration 으로 보간 (원 궤도 그대로 회전, 직선으로 가로지르지 않음)
 *  · 추적(anchor)·텍스트 갱신(타자 효과)·순간 조명(light 블록)·데미지 숫자·마법진·충격파 링·빔·조준선 제공
 * 모든 엔티티는 jjk.laserfx + jjk.holo + 세션 태그 → 서버 재시작 후 남은 잔해는 자동 정리.
 */
final class Holo {
    private Holo() {}

    static final List<Elem> ACTIVE = new ArrayList<>();
    static final List<Light> LIGHTS = new ArrayList<>();
    static final String SESSION = "jjk.s" + Integer.toHexString((int) (System.nanoTime() ^ 0x5F3759DF) & 0x7FFFFFFF);
    static int uid = 0, dmgCount = 0;

    // ═════════════════════════ 요소
    static final class Elem {
        final ServerLevel level; final String tag, owner; Entity ent; String sel;
        final Vec3 origin; int age = 0, life;
        final List<float[]> keyMat = new ArrayList<>(); final List<int[]> keyT = new ArrayList<>();   // {t, dur}
        Supplier<Vec3> anchor; Vec3 offset = Vec3.ZERO;
        double yaw0, yawSpeed, pitch0, pitchAmp, pitchFreq; int tpEvery = 0;
        IntFunction<String> text; String lastText;
        java.util.function.DoubleSupplier yawFn;
        boolean dmg, fresh = true;

        Elem(ServerLevel level, String owner, Vec3 at, int life) {
            this.level = level; this.owner = owner == null ? "" : owner; this.origin = at; this.life = life;
            this.tag = "jjk.h" + (uid++);
        }
        /** t 틱에 행렬 m 으로 dur 틱 동안 보간 (t ≥ 1). */
        Elem key(int t, float[] m, int dur) { keyMat.add(m); keyT.add(new int[]{Math.max(1, t), Math.max(0, dur)}); return this; }
        Elem spin(double degPerTick) { yawSpeed = degPerTick; if (tpEvery == 0) tpEvery = 4; return this; }
        Elem wobble(double amp, double freq) { pitchAmp = amp; pitchFreq = freq; if (tpEvery == 0) tpEvery = 4; return this; }
        Elem follow(Supplier<Vec3> s, Vec3 off) { anchor = s; offset = off; tpEvery = 1; return this; }
        /** 추적 + 방향(yaw)도 대상에 맞춤 (몸체에 붙은 부품). */
        Elem attach(Supplier<Vec3> s, java.util.function.DoubleSupplier yaw) { anchor = s; offset = Vec3.ZERO; yawFn = yaw; tpEvery = 1; return this; }
        Elem text(IntFunction<String> fn) { text = fn; return this; }
        /** 지금부터 dt 틱 뒤 키프레임 (이미 살아있는 요소에 나중에 동작 추가 — 산산조각 등). */
        Elem keyIn(int dt, float[] m, int dur) { return key(age + Math.max(1, dt), m, dur); }
        /** 지금부터 dt 틱 뒤에 사라지게. */
        Elem endIn(int dt) { life = age + dt; return this; }
        /** 직접 위치·회전 지정 (드래곤 마디 등). */
        void place(double x, double y, double z, float yaw, float pitch) {
            if (ent != null && !ent.isRemoved()) ent.snapTo(x, y, z, yaw, pitch);
            else cmd(level, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f %.1f", sel, x, y, z, yaw, pitch));
        }
        void merge(String nbt) { cmd(level, "data merge entity " + sel + " " + nbt); }
        /** 키프레임: 명령 없이 엔티티 데이터 직접 설정 (실패 시 data merge). */
        void keyframe(float[] m, int dur) {
            if (ent instanceof net.minecraft.world.entity.Display d && Fast.key(d, m, dur)) return;
            merge("{transformation:" + mat(m) + ",start_interpolation:0,interpolation_duration:" + dur + "}");
        }
        void setText(String snbt) {
            if (ent instanceof net.minecraft.world.entity.Display.TextDisplay d && Fast.text(d, snbt)) return;
            merge("{text:" + snbt + "}");
        }
        void kill() {
            if (ent != null) ent.discard(); else cmd(level, "kill " + sel);
            life = -1;
        }
        boolean dead() { return life < 0; }
    }

    static void cmd(ServerLevel level, String c) { MechManager.cmd(level.getServer(), level, c); }

    static String tags(Elem e) { return "Tags:[\"jjk.laserfx\",\"jjk.holo\",\"" + SESSION + "\",\"" + e.tag + "\"" + (e.owner.isEmpty() ? "" : ",\"" + e.owner + "\"") + "]"; }

    static String mat(float[] m) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 16; i++) { if (i > 0) sb.append(','); sb.append(String.format(Locale.ROOT, "%.4ff", m[i])); }
        return sb.append(']').toString();
    }

    private static Elem finish(Elem e, String summon) {
        Entity direct = Fast.spawn(e.level, summon);
        if (direct != null) { e.ent = direct; e.sel = direct.getStringUUID(); ACTIVE.add(e); return e; }
        cmd(e.level, summon);
        List<Entity> found = e.level.getEntities((Entity) null, new AABB(e.origin, e.origin).inflate(0.05), x -> x.getTags().contains(e.tag));
        e.ent = found.isEmpty() ? null : found.get(0);
        e.sel = e.ent != null ? e.ent.getStringUUID() : "@e[tag=" + e.tag + ",limit=1]";
        ACTIVE.add(e);
        return e;
    }

    private static String common(Elem e, float[] m0, float view, int tpd, float yaw, float pitch) {
        return String.format(Locale.ROOT, "%s,Rotation:[%.1ff,%.1ff],brightness:{sky:15,block:15},view_range:%.1ff,teleport_duration:%d,interpolation_duration:0,transformation:%s",
                tags(e), yaw, pitch, view, tpd, mat(m0));
    }

    static Elem block(ServerLevel l, String owner, Vec3 at, String block, int glow, float[] m0, int life, int tpd) {
        Elem e = new Elem(l, owner, at, life);
        String g = glow >= 0 ? ",Glowing:1b,glow_color_override:" + glow : "";
        return finish(e, String.format(Locale.ROOT, "summon block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},%s%s}",
                at.x, at.y, at.z, block, common(e, m0, 8f, tpd, 0, 0), g));
    }

    static Elem item(ServerLevel l, String owner, Vec3 at, String item, boolean glint, int glow, float[] m0, int life, int tpd) {
        Elem e = new Elem(l, owner, at, life);
        String g = glow >= 0 ? ",Glowing:1b,glow_color_override:" + glow : "";
        return finish(e, String.format(Locale.ROOT, "summon item_display %.3f %.3f %.3f {item:{id:\"%s\",count:1%s},%s%s}",
                at.x, at.y, at.z, item, glint ? ",components:{\"minecraft:enchantment_glint_override\":true}" : "", common(e, m0, 8f, tpd, 0, 0), g));
    }

    /** 커스텀 아이템 모델 디스플레이 (보스 리그 부위·소품). 월드 조명을 그대로 받음 (밝기 고정 없음) → 입체 음영. */
    static Elem model(ServerLevel l, String owner, Vec3 at, String itemModel, float[] m0, int life, int tpd, float shadow) {
        Elem e = new Elem(l, owner, at, life);
        return finish(e, String.format(Locale.ROOT, "summon item_display %.3f %.3f %.3f {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"%s\"}},item_display:\"none\",%s,Rotation:[0f,0f],brightness:{sky:15,block:12},view_range:2f,shadow_radius:%.2ff,shadow_strength:0.7f,teleport_duration:%d,interpolation_duration:0,transformation:%s}",
                at.x, at.y, at.z, itemModel, tags(e), shadow, tpd, mat(m0)));
    }
    /** 손에 든 아이템 (아이템 ID 그대로, 컴포넌트 문자열 선택). */
    static Elem held(ServerLevel l, String owner, Vec3 at, String itemId, float[] m0, int life, int tpd) {
        Elem e = new Elem(l, owner, at, life);
        return finish(e, String.format(Locale.ROOT, "summon item_display %.3f %.3f %.3f {item:{id:\"%s\",count:1},item_display:\"none\",%s,Rotation:[0f,0f],brightness:{sky:15,block:12},view_range:2f,teleport_duration:%d,interpolation_duration:0,transformation:%s}",
                at.x, at.y, at.z, itemId, tags(e), tpd, mat(m0)));
    }

    /** text: SNBT 텍스트 컴포넌트, bg: ARGB 배경 (0 = 없음). */
    static Elem text(ServerLevel l, String owner, Vec3 at, String text, String billboard, int bg, boolean seeThrough, float[] m0, int life, int tpd) {
        Elem e = new Elem(l, owner, at, life);
        return finish(e, String.format(Locale.ROOT, "summon text_display %.3f %.3f %.3f {text:%s,billboard:\"%s\",background:%d,shadow:1b,see_through:%s,line_width:400,alignment:\"left\",%s}",
                at.x, at.y, at.z, text, billboard, bg, seeThrough ? "1b" : "0b", common(e, m0, 6f, tpd, 0, 0)));
    }

    /** SNBT 텍스트 한 조각. */
    static String tx(String s, int rgb, boolean bold) {
        return String.format(Locale.ROOT, "{text:\"%s\",color:\"#%06X\"%s}", esc(s), rgb & 0xFFFFFF, bold ? ",bold:1b" : "");
    }
    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // ═════════════════════════ 틱
    static void tick(MinecraftServer server) {
        slowTick();
        for (Iterator<Elem> it = ACTIVE.iterator(); it.hasNext(); ) {
            Elem e = it.next();
            if (e.dead()) { it.remove(); continue; }
            if (e.fresh) { e.fresh = false; continue; }   // 소환 직후 틱엔 키프레임 보류 (클라이언트가 먼저 받도록)
            try {
                e.age++;
                for (int i = 0; i < e.keyT.size(); i++) {
                    int[] k = e.keyT.get(i);
                    if (k[0] == e.age) e.keyframe(e.keyMat.get(i), k[1]);
                }
                if (e.tpEvery > 0 && (e.age % e.tpEvery == 0 || e.anchor != null)) {
                    Vec3 p = e.anchor != null ? e.anchor.get().add(e.offset) : e.origin;
                    float yaw = e.yawFn != null ? (float) e.yawFn.getAsDouble() : (float) (e.yaw0 + e.yawSpeed * e.age);
                    float pitch = (float) (e.pitch0 + e.pitchAmp * Math.sin(e.age * e.pitchFreq));
                    if (e.anchor != null || e.age % e.tpEvery == 0) e.place(p.x, p.y, p.z, yaw, pitch);
                }
                if (e.text != null) {
                    String t = e.text.apply(e.age);
                    if (t != null && !t.equals(e.lastText)) { e.lastText = t; e.setText(t); }
                }
                if (e.age >= e.life) { e.kill(); if (e.dmg) dmgCount--; it.remove(); }
            } catch (Exception ex) { e.kill(); it.remove(); }
        }
        for (Iterator<Light> it = LIGHTS.iterator(); it.hasNext(); ) {
            Light l = it.next();
            if (--l.t <= 0) { l.off(); it.remove(); }
        }
        // 이전 세션(서버 재시작·튕김)에서 남은 홀로 잔해 정리
        if (server.getTickCount() % 600 == 0)
            for (ServerLevel lv : server.getAllLevels()) cmd(lv, "kill @e[tag=jjk.holo,tag=!" + SESSION + "]");
    }

    // ═════════════════════════ 히트스톱 / 슬로모션 (/tick rate)
    static int slowT = 0;
    static ServerLevel slowLevel;
    /** rate(틱/초)로 ticks 틱 동안 세계 전체 감속 → 큰 타격의 무게감. */
    static void slowmo(ServerLevel l, int rate, int ticks) {
        if (!MechManager.cfgOn(l.getServer(), "#slowmo", 0)) return;   // 서버 전체가 느려져 끊겨 보임 → 기본 꺼짐
        if (slowT <= 0) cmd(l, "tick rate " + rate);
        slowT = Math.max(slowT, ticks);
        slowLevel = l;
    }
    static void slowTick() {
        if (slowT > 0 && --slowT == 0 && slowLevel != null) cmd(slowLevel, "tick rate 20");
    }

    static void killOwner(String owner) {
        for (Elem e : ACTIVE) if (e.owner.equals(owner) && !e.dead()) e.kill();
    }

    static void stopAll(MinecraftServer server) {
        for (Elem e : ACTIVE) if (!e.dead()) e.kill();
        ACTIVE.clear();
        for (Light l : LIGHTS) l.off();
        LIGHTS.clear();
        dmgCount = 0;
        if (slowT > 0 && slowLevel != null) { slowT = 0; cmd(slowLevel, "tick rate 20"); }
    }

    // ═════════════════════════ 순간 조명 (빈 공간에 light 블록 → 주변이 실제로 밝아짐)
    static final class Light {
        final ServerLevel level; final BlockPos pos; int t;
        Light(ServerLevel level, BlockPos pos, int t) { this.level = level; this.pos = pos; this.t = t; }
        void off() { if (level.getBlockState(pos).is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); }
    }

    static void light(ServerLevel level, Vec3 at, int ticks) {
        BlockPos bp = BlockPos.containing(at);
        for (int dy = 0; dy < 3; dy++, bp = bp.above()) {
            if (level.getBlockState(bp).is(Blocks.LIGHT)) { for (Light l : LIGHTS) if (l.pos.equals(bp)) l.t = Math.max(l.t, ticks); return; }
            if (!level.getBlockState(bp).isAir()) continue;
            level.setBlock(bp, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
            LIGHTS.add(new Light(level, bp, ticks));
            return;
        }
    }

    // ═════════════════════════ 행렬
    static double[][] I() { return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}; }
    static double[][] rotY(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}}; }
    static double[][] rotX(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}}; }
    static double[][] rotZ(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}}; }
    static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }
    static Vec3 apply(double[][] r, Vec3 v) {
        return new Vec3(r[0][0] * v.x + r[0][1] * v.y + r[0][2] * v.z, r[1][0] * v.x + r[1][1] * v.y + r[1][2] * v.z, r[2][0] * v.x + r[2][1] * v.y + r[2][2] * v.z);
    }
    /** R·S 에 이동 t. corner=true 면 블록 디스플레이(모서리 원점)를 중심 기준으로 맞춤. */
    static float[] m(double[][] r, double sx, double sy, double sz, Vec3 t, boolean corner) {
        double[] s = {sx, sy, sz};
        float[] o = new float[16];
        Vec3 c = corner ? t.subtract(apply(r, new Vec3(sx / 2, sy / 2, sz / 2))) : t;
        double[] cc = {c.x, c.y, c.z};
        for (int i = 0; i < 3; i++) { for (int j = 0; j < 3; j++) o[i * 4 + j] = (float) (r[i][j] * s[j]); o[i * 4 + 3] = (float) cc[i]; }
        o[15] = 1;
        return o;
    }
    static float[] zero(boolean corner) { return m(I(), 0.001, 0.001, 0.001, Vec3.ZERO, corner); }

    /** a→b 를 잇는 막대 (중심 기준, 굵기 w×h). */
    static float[] bar(Vec3 a, Vec3 b, double w, double h, double roll) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return zero(true);
        Vec3 x = d.normalize();
        Vec3 up = Math.abs(x.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 z = x.cross(up).normalize(), y = z.cross(x).normalize();
        double c = Math.cos(roll), s = Math.sin(roll);
        Vec3 y2 = y.scale(c).add(z.scale(s)), z2 = z.scale(c).subtract(y.scale(s));
        double[][] r = {{x.x, y2.x, z2.x}, {x.y, y2.y, z2.y}, {x.z, y2.z, z2.z}};
        return m(r, len, h, w, a.add(b).scale(0.5), true);
    }

    // ═════════════════════════ 색 → 유리
    static final int[] GLASS_RGB = {0xF9FFFE, 0x3AB3DA, 0x169C9C, 0x3C44AA, 0x8932B8, 0xC74EBD, 0xF38BAA, 0xB02E26, 0xF9801D, 0xFED83D, 0x80C71F, 0x5E7C16, 0x1D1D21};
    static final String[] GLASS = {"white", "light_blue", "cyan", "blue", "purple", "magenta", "pink", "red", "orange", "yellow", "lime", "green", "black"};
    static String glass(int rgb) { return "minecraft:" + GLASS[nearest(rgb)] + "_stained_glass"; }
    static String concrete(int rgb) { return "minecraft:" + GLASS[nearest(rgb)] + "_concrete"; }
    static int nearest(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255, best = 0; long bd = Long.MAX_VALUE;
        for (int i = 0; i < GLASS_RGB.length - 1; i++) {
            int c = GLASS_RGB[i];
            long d = (long) (r - (c >> 16 & 255)) * (r - (c >> 16 & 255)) * 3 + (long) (g - (c >> 8 & 255)) * (g - (c >> 8 & 255)) * 4 + (long) (b - (c & 255)) * (b - (c & 255)) * 2;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    // ═════════════════════════ 발광 스프라이트 (리소스: assets/jjk/items/fx_*.json, 흰색 텍스처 + 실시간 색)
    /** 텍스처 평면 하나. fx: circle, runering, ring, orb, flare, smoke0, smoke1, slash, beam, crack, spark, hex */
    static Elem sprite(ServerLevel l, String owner, Vec3 at, String fx, int rgb, String billboard, float[] m0, int life, int tpd) {
        Elem e = new Elem(l, owner, at, life);
        return finish(e, String.format(Locale.ROOT,
                "summon item_display %.3f %.3f %.3f {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"jjk:fx_%s\",\"minecraft:custom_model_data\":{colors:[%d]}}},billboard:\"%s\",%s}",
                at.x, at.y, at.z, fx, rgb & 0xFFFFFF, billboard, common(e, m0, 10f, tpd, 0, 0)));
    }
    static final double[][] FLAT = rotX(-Math.PI / 2);
    /** 카메라를 보는 스프라이트 행렬 (Z 회전 r). */
    static float[] bb(double s, double r, Vec3 off) { return m(rotZ(r), s, s, s, off, false); }

    /** 빛 구체: 색 + 흰 심, 커졌다 줄어듦. */
    static void glow(ServerLevel l, String owner, Vec3 at, double size, int rgb, int grow, int hold, int fade) {
        int life = grow + hold + fade + 2;
        sprite(l, owner, at, "orb", rgb, "center", bb(0.01, 0, Vec3.ZERO), life, 0).key(1, bb(size * 2.4, 0, Vec3.ZERO), grow).key(1 + grow + hold, bb(0.01, 0, Vec3.ZERO), fade);
        sprite(l, owner, at, "orb", 0xFFFFFF, "center", bb(0.01, 0, Vec3.ZERO), life, 0).key(1, bb(size * 1.1, 0, Vec3.ZERO), grow).key(1 + grow + hold, bb(0.01, 0, Vec3.ZERO), fade);
    }
    /** 렌즈 섬광: 확 퍼졌다가 돌며 줄어듦. */
    static void flare(ServerLevel l, String owner, Vec3 at, double size, int rgb, int life) {
        sprite(l, owner, at, "flare", rgb, "center", bb(size * 0.2, 0, Vec3.ZERO), life + 1, 0)
                .key(1, bb(size, 0.4, Vec3.ZERO), 2).key(3, bb(size * 0.05, 1.4, Vec3.ZERO), Math.max(1, life - 3));
        sprite(l, owner, at, "flare", 0xFFFFFF, "center", bb(size * 0.1, 0.8, Vec3.ZERO), life + 1, 0)
                .key(1, bb(size * 0.6, 0.8, Vec3.ZERO), 2).key(3, bb(size * 0.03, -0.4, Vec3.ZERO), Math.max(1, life - 3));
    }
    /** 바닥 문양 (평면): from → to 크기, 회전 spin. */
    static Elem decal(ServerLevel l, String owner, Vec3 at, String fx, int rgb, double s0, double s1, int grow, int hold, int fade, double spin, double[][] T) {
        double[][] R = mul(T, FLAT);
        Elem e = sprite(l, owner, at, fx, rgb, "fixed", m(R, s0, s0, s0, Vec3.ZERO, false), grow + hold + fade + 2, 4);
        e.key(1, m(R, s1, s1, s1, Vec3.ZERO, false), grow).key(1 + grow + hold, m(R, 0.01, 0.01, 0.01, Vec3.ZERO, false), fade);
        if (spin != 0) e.spin(spin);
        return e;
    }
    /** 연기 뭉치: n 개가 퍼지며 피어오름. */
    static void smoke(ServerLevel l, String owner, Vec3 at, int n, double size, int rgb, double rise, int life) {
        java.util.Random r = new java.util.Random(uid * 31L + n);
        for (int i = 0; i < n; i++) {
            Vec3 o = new Vec3(r.nextGaussian() * size * 0.35, r.nextDouble() * size * 0.3, r.nextGaussian() * size * 0.35);
            Vec3 end = o.scale(1.8).add(0, rise * (0.6 + r.nextDouble() * 0.6), 0);
            double s = size * (0.6 + r.nextDouble() * 0.6), rot = r.nextDouble() * 6;
            int lf = life + r.nextInt(life / 2 + 1);
            int g = 70 + r.nextInt(50);
            int col = rgb >= 0 ? rgb : (g << 16 | g << 8 | g);
            sprite(l, owner, at, r.nextBoolean() ? "smoke0" : "smoke1", col, "center", bb(s * 0.3, rot, o), lf + 1, 0)
                    .key(1, bb(s, rot + 0.4, o.add(end.subtract(o).scale(0.3))), lf / 3)
                    .key(1 + lf / 3, bb(s * 1.5, rot + 1.0, end), lf - lf / 3 - 4)
                    .key(lf - 3, bb(0.01, rot + 1.2, end.add(0, 1, 0)), 3);
        }
    }
    /** 불꽃 줄기: 사방으로 튀는 빛 선. */
    static void sparks(ServerLevel l, String owner, Vec3 at, int n, double dist, int rgb, int life) {
        java.util.Random r = new java.util.Random(uid * 17L + n);
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * 0.8 + 0.2, r.nextGaussian()).normalize();
            Vec3 x = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(d).normalize();
            Vec3 z = x.cross(d).normalize();
            double[][] R = {{x.x, d.x, z.x}, {x.y, d.y, z.y}, {x.z, d.z, z.z}};
            double len = 0.6 + r.nextDouble() * 1.4;
            sprite(l, owner, at, "spark", r.nextInt(3) == 0 ? 0xFFFFFF : rgb, "fixed", m(R, 0.2, len * 0.3, 0.2, Vec3.ZERO, false), life + 1, 0)
                    .key(1, m(R, 0.35, len, 0.35, d.scale(dist * 0.6), false), life / 2)
                    .key(1 + life / 2, m(R, 0.05, len * 0.3, 0.05, d.scale(dist).add(0, -0.5, 0), false), life - life / 2 - 1);
        }
    }
    /** 칼날 초승달: 평면 T 위에서 휘두르며 나타났다 사라짐. */
    static void slash(ServerLevel l, String owner, Vec3 c, double r, double[][] T, int rgb, int life) {
        for (int k = 0; k < 2; k++) {
            double s = r * 2.35 * (k == 0 ? 1 : 0.92);
            double[][] R0 = mul(T, mul(FLAT, rotZ(-0.9))), R1 = mul(T, mul(FLAT, rotZ(0.15))), R2 = mul(T, mul(FLAT, rotZ(0.5)));
            sprite(l, owner, c, "slash", k == 0 ? rgb : 0xFFFFFF, "fixed", m(R0, s * 0.6, s * 0.6, s * 0.6, Vec3.ZERO, false), life + 2, 0)
                    .key(1, m(R1, s, s, s, Vec3.ZERO, false), 2).key(3, m(R2, s * 1.08, s * 1.08, s * 0.2, Vec3.ZERO, false), Math.max(1, life - 2));
        }
    }

    /** 화면 반응을 반경 안 플레이어에게 (kind: 1 FOV펀치, 2 롤흔들림, 3 임팩트프레임, 4 색수차, 5 하늘색, 6 집중선, 7 줌인). */
    static void screen(ServerLevel l, Vec3 at, double radius, int kind, int color, int ms, float power) {
        for (net.minecraft.server.level.ServerPlayer p : l.players()) {
            double d = p.position().distanceTo(at);
            if (d > radius) continue;
            float k = (float) Math.max(0.35, 1 - d / radius);
            com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(kind, color, ms, kind == 3 || kind == 5 || kind == 6 ? power : power * k));
        }
    }
    /** 큰 타격의 화면 반응 묶음. */
    static void punch(ServerLevel l, Vec3 at, double size, int rgb) {
        double rad = 30 + size * 12;
        screen(l, at, rad, 1, 0, (int) (380 + size * 40), (float) Math.min(0.32, 0.05 + size * 0.025));
        screen(l, at, rad, 2, 0, (int) (500 + size * 60), (float) Math.min(9, 1.5 + size * 0.7));
        if (size >= 4) screen(l, at, rad, 4, 0, (int) (300 + size * 30), (float) Math.min(1, size / 10));
        if (size >= 7) screen(l, at, rad, 3, 0, 170, 1);
        if (size >= 5) screen(l, at, rad, 5, rgb, (int) (900 + size * 80), (float) Math.min(0.85, size / 12));
    }

    /** 위로 떠오르는 불씨. */
    static void embers(ServerLevel l, String owner, Vec3 at, int n, double spread, int rgb, int life) {
        java.util.Random r = new java.util.Random(uid * 13L + n);
        for (int i = 0; i < n; i++) {
            Vec3 o = new Vec3(r.nextGaussian() * spread, r.nextDouble() * spread * 0.5, r.nextGaussian() * spread);
            Vec3 e = o.add(r.nextGaussian() * spread * 0.4, 2 + r.nextDouble() * spread * 1.5, r.nextGaussian() * spread * 0.4);
            double s = 0.25 + r.nextDouble() * 0.35;
            int lf = life / 2 + r.nextInt(life);
            sprite(l, owner, at, "orb", r.nextInt(4) == 0 ? 0xFFF0C0 : rgb, "center", bb(0.01, 0, o), lf + 1, 0)
                    .key(1, bb(s, 0, o.add(0, 0.3, 0)), 3).key(4, bb(s * 0.5, 0, e), lf - 6).key(lf - 2, bb(0.01, 0, e.add(0, 0.4, 0)), 2);
        }
    }
    /** 땅을 쓸며 바깥으로 퍼지는 먼지 고리. */
    static void dustRing(ServerLevel l, String owner, Vec3 at, double r, int n, int life) {
        java.util.Random rr = new java.util.Random(uid * 7L + n);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + rr.nextDouble() * 0.3;
            Vec3 o = new Vec3(Math.cos(a) * r * 0.15, 0.4, Math.sin(a) * r * 0.15), e = new Vec3(Math.cos(a) * r, 0.9 + rr.nextDouble(), Math.sin(a) * r);
            double s = r * 0.18 + 0.8;
            int g = 90 + rr.nextInt(40);
            sprite(l, owner, at, rr.nextBoolean() ? "smoke0" : "smoke1", g << 16 | g << 8 | (g - 8), "center", bb(s * 0.4, a, o), life + 1, 0)
                    .key(1, bb(s, a + 0.5, o.lerp(e, 0.55)), life / 3).key(1 + life / 3, bb(s * 1.6, a + 1, e), life - life / 3 - 4).key(life - 3, bb(0.01, a + 1.2, e.add(0, 0.5, 0)), 3);
        }
    }

    /** 대폭발 세트: 섬광 + 빛 구체 2겹 + 충격파 2차 + 연기 + 먼지 고리 + 불꽃 + 불씨 + 지면 균열·파편 + 조명 고리 + 효과음 + 화면 반응. */
    static void explosion(ServerLevel l, String owner, Vec3 at, double size, int rgb) {
        flare(l, owner, at.add(0, size * 0.4, 0), size * 3.6, rgb, 11);
        flare(l, owner, at.add(0, size * 0.4, 0), size * 2.2, 0xFFFFFF, 6);
        glow(l, owner, at.add(0, size * 0.35, 0), size * 1.1, rgb, 2, 3, 9);
        glow(l, owner, at.add(0, size * 0.35, 0), size * 0.65, 0xFFE8A0, 1, 2, 6);
        shockTex(l, owner, at, size * 2.8, rgb, 9);
        shockTex(l, owner, at, size * 4.2, 0xFFFFFF, 16);
        smoke(l, owner, at.add(0, size * 0.2, 0), Math.min(26, 8 + (int) (size * 2.4)), size * 1.4, -1, size * 1.8, 40);
        dustRing(l, owner, at, size * 3.2, Math.min(24, 10 + (int) (size * 1.5)), 26);
        sparks(l, owner, at.add(0, 0.5, 0), Math.min(26, 8 + (int) (size * 2.4)), size * 2.4, rgb, 13);
        embers(l, owner, at, Math.min(20, 6 + (int) (size * 1.6)), size * 0.9, rgb, 40);
        decal(l, owner, at.add(0, 0.06, 0), "crack", rgb, size * 0.5, size * 2.8, 3, 70, 20, 0, I());
        decal(l, owner, at.add(0, 0.09, 0), "orb", rgb, size * 0.5, size * 3.2, 3, 8, 12, 0, I());
        var gs = l.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        String gname = gs.isAir() || !gs.getFluidState().isEmpty() ? "minecraft:deepslate" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(gs.getBlock()).toString();
        shards(l, owner, at, gname, Math.min(18, 4 + (int) (size * 1.5)), size * 1.2 + 1);
        light(l, at.add(0, 1, 0), 18);
        if (size >= 3) for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2; light(l, at.add(Math.cos(a) * size, 1, Math.sin(a) * size), 12); }
        if (owner != null && owner.startsWith("jjk.boss")) BossSfx.play(l, at, size >= 3 ? "boom" : "impact", (float) Math.min(4, 1.0 + size * 0.35), (float) Math.max(0.6, 1.25 - size * 0.06));   // 보스: 바닐라 겹소리
        else {
            sfx(l, at, size >= 3 ? "boom" : "impact", (float) Math.min(6, 1.5 + size * 0.5), (float) Math.max(0.55, 1.2 - size * 0.06));
            if (size >= 2.5) sfx(l, at, "impact", (float) Math.min(4, 1 + size * 0.3), 0.7f);
        }
        punch(l, at, size, rgb);
    }

    /** 커스텀 효과음 (assets/jjk/sounds/fx). */
    static void sfx(ServerLevel l, Vec3 at, String name, float vol, float pitch) {
        cmd(l, String.format(Locale.ROOT, "playsound jjk:fx.%s master @a[x=%.1f,y=%.1f,z=%.1f,distance=..%d] %.2f %.2f %.2f %.2f %.2f 0.3",
                name, at.x, at.y, at.z, (int) Math.max(48, vol * 24), at.x, at.y, at.z, vol, pitch));
    }

    /** 텍스처 충격파 링 (날카로운 테 + 번지는 빛) 2겹. */
    static void shockTex(ServerLevel l, String owner, Vec3 c, double r, int rgb, int time) {
        double s = r / 0.43;
        decal(l, owner, c.add(0, 0.1, 0), "ring", rgb, s * 0.05, s, time, 0, 5, 0, I());
        decal(l, owner, c.add(0, 0.13, 0), "ring", 0xFFFFFF, s * 0.04, s * 0.8, time + 1, 0, 4, 0, I());
    }

    /** 빔: a→b, 굵기 w. 블록 심(선명) + 교차한 발광 텍스처 2장(부드러운 빛). */
    static void beam(ServerLevel l, String owner, Vec3 a, Vec3 b, double w, int rgb, int grow, int hold, int fade) {
        beamLayer(l, owner, a, b, w * 0.42, glass(rgb), rgb, grow, hold, fade, 0);
        beamLayer(l, owner, a, b, w * 0.16, "minecraft:white_concrete", -1, grow, hold, fade + 1, 0.6);
        beamGlow(l, owner, a, b, w * 2.6, rgb, grow, hold, fade);
    }
    /** 발광 텍스처 빔 (X자로 교차한 평면 2장). */
    static void beamGlow(ServerLevel l, String owner, Vec3 a, Vec3 b, double w, int rgb, int grow, int hold, int fade) {
        Vec3 d = b.subtract(a); double len = d.length();
        if (len < 0.05) return;
        Vec3 y = d.normalize();
        Vec3 x = Math.abs(y.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(y).normalize();
        Vec3 z = x.cross(y).normalize();
        Vec3 mid = d.scale(0.5);
        for (int k = 0; k < 2; k++) {
            Vec3 xx = k == 0 ? x : z, zz = k == 0 ? z : x.scale(-1);
            double[][] R = {{xx.x, y.x, zz.x}, {xx.y, y.y, zz.y}, {xx.z, y.z, zz.z}};
            sprite(l, owner, a, "beam", rgb, "fixed", m(R, w * 0.05, len, 1, mid, false), grow + hold + fade + 2, 0)
                    .key(1, m(R, w, len * 1.04, 1, mid, false), grow).key(1 + grow + hold, m(R, 0.01, len, 1, mid, false), fade);
        }
    }

    /** 빔 한 겹: a 위치에 소환, 행렬은 a 기준 상대 좌표. */
    static Elem beamLayer(ServerLevel l, String owner, Vec3 a, Vec3 b, double w, String block, int glow, int grow, int hold, int fade, double roll) {
        Vec3 d = b.subtract(a);
        float[] thin = bar(Vec3.ZERO, d, w * 0.05, w * 0.05, roll), full = bar(Vec3.ZERO, d, w, w, roll + 0.3), gone = bar(Vec3.ZERO, d, 0.001, 0.001, roll + 0.6);
        Elem e = block(l, owner, a, block, glow, thin, grow + hold + fade + 2, 0);
        return e.key(1, full, grow).key(1 + grow + hold, gone, fade);
    }

    /** 수직 빛기둥: 블록 심 + 카메라를 향하는 부드러운 빛기둥 텍스처. */
    static void pillar(ServerLevel l, String owner, Vec3 at, double h, double w, int rgb, int grow, int hold, int fade) {
        beamLayer(l, owner, at, at.add(0, h, 0), w * 0.35, glass(rgb), rgb, grow, hold, fade, 0);
        beamLayer(l, owner, at, at.add(0, h, 0), w * 0.12, "minecraft:white_concrete", -1, grow, hold, fade + 1, 0.6);
        Vec3 mid = new Vec3(0, h / 2, 0);
        sprite(l, owner, at, "beam", rgb, "vertical", m(I(), w * 0.1, h, 1, mid, false), grow + hold + fade + 2, 0)
                .key(1, m(I(), w * 2.8, h * 1.05, 1, mid, false), grow).key(1 + grow + hold, m(I(), 0.01, h * 1.05, 1, mid, false), fade);
        sprite(l, owner, at, "beam", 0xFFFFFF, "vertical", m(I(), w * 0.05, h, 1, mid, false), grow + hold + fade + 2, 0)
                .key(1, m(I(), w * 0.9, h, 1, mid, false), grow).key(1 + grow + hold, m(I(), 0.01, h, 1, mid, false), fade);
        decal(l, owner, at.add(0, 0.08, 0), "orb", rgb, w * 0.5, w * 4, grow, hold, fade, 0, I());
    }

    /**
     * 링(원형 띠): n 조각 판. r0 → r1 로 grow 틱 동안 퍼지고, hold 후 fade 틱 동안 얇아지며 사라짐.
     * tilt: X축 기울기, spin: 초당이 아닌 틱당 회전(도). 원점 = 중심 → 회전이 진짜 원 궤도.
     */
    static void ring(ServerLevel l, String owner, Vec3 c, double r0, double r1, int n, double band, double thick, int rgb,
                     int grow, int hold, int fade, double tilt, double spin, boolean glowEdge) {
        ringT(l, owner, c, r0, r1, n, band, thick, rgb, grow, hold, fade, rotX(tilt), spin, glowEdge);
    }

    /** 축을 자유롭게 정하는 고리 (T: 고리 평면의 회전). */
    static void ringT(ServerLevel l, String owner, Vec3 c, double r0, double r1, int n, double band, double thick, int rgb,
                      int grow, int hold, int fade, double[][] T, double spin, boolean glowEdge) {
        String block = glass(rgb);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float[] m0 = seg(T, a, r0, n, band * 0.4, thick), m1 = seg(T, a, r1, n, band, thick), m2 = seg(T, a, r1 * 1.06, n, band * 0.02, thick * 0.2);
            Elem e = block(l, owner, c, block, glowEdge ? rgb : -1, m0, grow + hold + fade + 2, 4);
            e.key(1, m1, grow).key(1 + grow + hold, m2, fade);
            if (spin != 0) e.spin(spin);
        }
    }
    static float[] seg(double[][] T, double a, double r, int n, double band, double thick) {
        double len = 2 * Math.PI * r / n * 1.06 + 0.02;
        double[][] R = mul(T, rotY(-(a + Math.PI / 2)));
        Vec3 ctr = apply(T, new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r));
        return m(R, len, thick, band, ctr, true);
    }

    /** 바닥 충격파: 텍스처 링 2겹 + 얇은 블록 테(입체감). */
    static void shock(ServerLevel l, String owner, Vec3 c, double r, int rgb, int time) {
        shockTex(l, owner, c, r, rgb, time);
        int n = Math.max(16, Math.min(40, (int) (r * 1.6)));
        ring(l, owner, c.add(0, 0.15, 0), 0.3, r * 0.98, n, Math.max(0.12, r * 0.02), 0.05, rgb, time, 0, 4, 0, 0, true);
    }

    /** 에너지 구체: 발광 스프라이트 + 회전하는 큐브 2겹(입체). */
    static void orb(ServerLevel l, String owner, Vec3 c, double size, int rgb, int grow, int hold, int fade) {
        glow(l, owner, c, size * 0.75, rgb, grow, hold, fade);
        String[] bl = {"minecraft:white_concrete", glass(rgb)};
        double[] k = {0.32, 0.62};
        for (int i = 0; i < bl.length; i++) {
            double[][] R = mul(rotX(0.6 * i + 0.3), rotZ(0.45 * i));
            Elem e = block(l, owner, c, bl[i], i == 1 ? rgb : -1, m(R, 0.01, 0.01, 0.01, Vec3.ZERO, true), grow + hold + fade + 2, 4);
            double s = size * k[i];
            e.key(1, m(R, s, s, s, Vec3.ZERO, true), grow).key(1 + grow + hold, m(R, 0.01, 0.01, 0.01, Vec3.ZERO, true), fade);
            e.spin(i % 2 == 0 ? 9 + i * 2 : -8 - i * 2).wobble(25, 0.15 + i * 0.05);
        }
    }

    static final String[] RUNES = {"/", "@", "~", "^", "{", "}", "[", "]", "#", ";", "$", "Σ", "Ω", "Δ", "λ", "∞", "⌘", "§", "∴", "⊕", "⟁", "☍"};

    /**
     * 마법진 (텍스처): 정밀한 마법진 + 반대로 도는 룬 고리 + 중심 빛, 바깥 블록 테(입체 발광).
     * tilt 0 = 바닥, π/2 = 세로(차원문).
     */
    static void circle(ServerLevel l, String owner, Vec3 c, double r, int rgb, int life, double tilt) {
        int grow = 6, fade = 6, hold = Math.max(1, life - grow - fade);
        Vec3 o = c.add(0, 0.06, 0);
        double[][] T = rotX(tilt);
        boolean flat = Math.abs(tilt) < 1e-3;
        decal(l, owner, o, "circle", rgb, r * 0.2, r * 2.0, grow, hold, fade, flat ? 1.4 : 0, T);
        decal(l, owner, o.add(0, 0.03, 0), "runering", flat ? 0xFFFFFF : rgb, r * 0.3, r * 2.25, grow + 2, hold - 2, fade, flat ? -2.2 : 0, T);
        decal(l, owner, o.add(0, 0.05, 0), "orb", rgb, r * 0.1, r * 1.1, grow, hold, fade, 0, T);
        int n = Math.max(16, Math.min(36, (int) (r * 3)));
        ringT(l, owner, o, r * 0.2, r * 1.02, n, Math.max(0.06, r * 0.012), 0.03, rgb, grow, hold, fade, T, flat ? 1.4 : 0, true);
    }

    /** 데미지 숫자: 튀어나왔다가 떠오르며 사라짐 (스팀 게임식). */
    static void dmgNumber(ServerLevel l, String owner, Vec3 at, float dmg, int combo) {
        if (dmgCount > 80) return;
        boolean big = dmg >= 50, huge = dmg >= 200;
        int rgb = huge ? 0xFF3040 : big ? 0xFFB020 : 0xFFFFFF;
        String s = String.valueOf(Math.round(dmg));
        String t = "{text:\"\",extra:[" + tx(s, rgb, big) + (huge ? "," + tx("!", 0xFFE040, true) : "") + "]}";
        double sc = huge ? 2.6 : big ? 1.9 : 1.3;
        Vec3 j = at.add((uid % 7 - 3) * 0.12, 0.2 + (uid % 5) * 0.08, (uid % 3 - 1) * 0.12);
        Elem e = text(l, owner, j, t, "center", 0, true, m(I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 26, 0);
        e.dmg = true; dmgCount++;
        e.key(1, m(I(), sc * 1.35, sc * 1.35, sc, new Vec3(0, 0.15, 0), false), 2)
                .key(4, m(I(), sc, sc, sc, new Vec3(0, 0.35, 0), false), 4)
                .key(9, m(I(), sc * 0.9, sc * 0.9, sc, new Vec3(0, 1.3, 0), false), 12)
                .key(21, m(I(), 0.01, 0.01, 0.01, new Vec3(0, 1.6, 0), false), 4);
    }

    /** 떠오르는 큰 글자 (COMBO, OVERDRIVE 등). */
    static void banner(ServerLevel l, String owner, Vec3 at, String snbt, double sc, int life) {
        Elem e = text(l, owner, at, snbt, "center", 0, false, m(I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), life, 0);
        e.key(1, m(I(), sc * 1.5, sc * 1.5, sc, Vec3.ZERO, false), 2)
                .key(4, m(I(), sc, sc, sc, Vec3.ZERO, false), 4)
                .key(Math.max(5, life - 8), m(I(), sc * 1.1, sc * 1.1, sc, new Vec3(0, 0.6, 0), false), 4)
                .key(Math.max(6, life - 4), m(I(), sc * 1.6, 0.01, sc, new Vec3(0, 0.7, 0), false), 3);
    }

    /** 조준선: 대상을 따라다니며 바깥에서 조여드는 브래킷 + LOCK 표시. */
    static void reticle(ServerLevel l, String owner, Entity target, int rgb, int life) {
        Supplier<Vec3> s = () -> target.isAlive() ? target.position().add(0, target.getBbHeight() * 0.5, 0) : target.position();
        double h = Math.max(1.0, target.getBbHeight());
        String br = "{text:\"\",extra:[" + tx("◤", rgb, true) + "," + tx("        ", rgb, false) + "," + tx("◥", rgb, true) + ","
                + tx("\\n\\n\\n", rgb, false) + "," + tx("◣", rgb, true) + "," + tx("        ", rgb, false) + "," + tx("◢", rgb, true) + "]}";
        br = br.replace("\\\\n", "\\n");
        Vec3 at = s.get();
        Elem e = text(l, owner, at, br, "center", 0, true, ctr(rotZ(1.2), h * 3.2), life, 1);
        e.follow(s, Vec3.ZERO);
        e.key(1, ctr(rotZ(0), h * 1.15), 6).key(life - 4, ctr(rotZ(-0.8), h * 0.2), 4);
        Elem lab = text(l, owner, at, "{text:\"\",extra:[" + tx("▶ LOCK", rgb, true) + "]}", "center", 0x90000000, true,
                m(I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), life, 1);
        lab.follow(s, new Vec3(0, h * 0.75 + 0.4, 0));
        lab.key(3, m(I(), 0.7, 0.7, 0.7, Vec3.ZERO, false), 3).key(life - 3, m(I(), 0.7, 0.01, 0.7, Vec3.ZERO, false), 3);
    }

    /** 글자 블록(높이 약 1칸·4줄)을 자기 중심 기준으로 회전·확대. */
    static float[] ctr(double[][] r, double s) { return m(r, s, s, 1, apply(r, new Vec3(0, -0.5 * s, 0)), false); }

    /** 축 d 에 수직인 평면으로 고리를 눕히는 회전 (고리의 법선 = d). */
    static double[][] facing(Vec3 d) {
        Vec3 y = d.normalize();
        Vec3 x = Math.abs(y.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(y).normalize();
        Vec3 z = x.cross(y).normalize();
        return new double[][]{{x.x, y.x, z.x}, {x.y, y.y, z.y}, {x.z, y.z, z.z}};
    }

    /**
     * 직접 그린 번개: from→to 를 지그재그 마디로 잇고 곁가지 2~3개. 흰 심 + 색 유리 외피, 번쩍 나타났다 빠르게 사라짐.
     */
    static void lightning(ServerLevel l, String owner, Vec3 from, Vec3 to, int rgb, double w, int life) {
        java.util.Random rnd = new java.util.Random(uid * 7919L);
        List<Vec3> pts = new ArrayList<>();
        int n = Math.max(5, (int) (from.distanceTo(to) / 3.5));
        Vec3 d = to.subtract(from);
        double jit = Math.min(3.5, d.length() * 0.09);
        pts.add(from);
        for (int i = 1; i < n; i++) pts.add(from.add(d.scale(i / (double) n)).add((rnd.nextDouble() - 0.5) * jit * 2, (rnd.nextDouble() - 0.5) * jit, (rnd.nextDouble() - 0.5) * jit * 2));
        pts.add(to);
        boltPath(l, owner, pts, rgb, w, life, 0);
        for (int b = 0; b < 3; b++) {      // 곁가지
            int k = 1 + rnd.nextInt(Math.max(1, n - 2));
            Vec3 o = pts.get(k);
            List<Vec3> br = new ArrayList<>();
            br.add(o);
            Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, -0.3 - rnd.nextDouble() * 0.5, rnd.nextDouble() - 0.5).normalize();
            for (int i = 1; i <= 3; i++) br.add(o.add(dir.scale(i * 2.6)).add((rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.5) * 1.4));
            boltPath(l, owner, br, rgb, w * 0.5, life - 1, 1);
        }
        light(l, to.add(0, 1, 0), life + 4);
        flare(l, owner, to.add(0, 0.5, 0), 3 + w * 2, rgb, 6);
        glow(l, owner, to.add(0, 0.5, 0), 1 + w, rgb, 1, 1, 5);
        if (w >= 1.0) { if (owner != null && owner.startsWith("jjk.boss")) BossSfx.play(l, to, "thunder", (float) Math.min(3, 0.8 + w), 1f); else sfx(l, to, "thunder", (float) Math.min(4, 1.2 + w), 0.9f + (float) (uid % 5) * 0.06f); }
    }
    private static void boltPath(ServerLevel l, String owner, List<Vec3> pts, int rgb, double w, int life, int delay) {
        Vec3 base = pts.get(0);
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i).subtract(base), b = pts.get(i + 1).subtract(base);
            Elem core = block(l, owner, base, "minecraft:white_concrete", rgb, bar(a, b, 0.001, 0.001, 0), life + 3, 0);
            core.key(1 + delay, bar(a, b, w * 0.35, w * 0.35, 0), 0).key(2 + delay + life / 2, bar(a, b, 0.001, 0.001, 0), Math.max(1, life / 2));
            Elem glow = block(l, owner, base, glass(rgb), -1, bar(a, b, 0.001, 0.001, 0), life + 3, 0);
            glow.key(1 + delay, bar(a, b, w, w, 0.7), 0).key(2 + delay, bar(a, b, w * 0.6, w * 0.6, 0.7), life);
            if (w >= 0.6) beamGlow(l, owner, pts.get(i), pts.get(i + 1), w * 4, rgb, 1, 1, life);
        }
    }

    /** 잔상: 크기 s 의 블록이 빛나며 사라짐 (궤적·불꼬리용). */
    static void ghost(ServerLevel l, String owner, Vec3 at, String block, int glow, double s, double[][] r, int life) {
        Elem e = block(l, owner, at, block, glow, m(r, s, s, s, Vec3.ZERO, true), life + 1, 0);
        e.key(1, m(r, s * 0.05, s * 0.05, s * 0.05, Vec3.ZERO, true), life);
    }

    /**
     * 칼날 궤적(스워시): 평면 T 위 반지름 r 의 호를 a0→a1 로 순식간에 그리고 사라짐.
     */
    static void arc(ServerLevel l, String owner, Vec3 c, double r, double a0, double a1, double[][] T, int rgb, double band, int life) {
        int n = Math.max(6, (int) (Math.abs(a1 - a0) * r / 0.8));
        for (int i = 0; i < n; i++) {
            double a = a0 + (a1 - a0) * (i + 0.5) / n;
            double taper = Math.sin(Math.PI * (i + 0.5) / n);
            float[] full = segArc(T, a, r, Math.abs(a1 - a0) * r / n * 1.1, band * (0.3 + taper), 0.05);
            float[] thin = segArc(T, a, r * 1.08, Math.abs(a1 - a0) * r / n, 0.01, 0.01);
            int d = i * 3 / n;
            Elem e = block(l, owner, c, i % 3 == 0 ? "minecraft:white_concrete" : glass(rgb), rgb, segArc(T, a, r, 0.01, 0.01, 0.01), life + 4, 0);
            e.key(1 + d, full, 1).key(3 + d, thin, life);
        }
    }
    static float[] segArc(double[][] T, double a, double r, double len, double band, double thick) {
        double[][] R = mul(T, rotY(-(a + Math.PI / 2)));
        Vec3 ctr = apply(T, new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r));
        return m(R, Math.max(0.01, len), thick, band, ctr, true);
    }

    /** 육각 방어막 구체: 판으로 된 공이 대상을 감싸고 따라다님. */
    static void bubble(ServerLevel l, String owner, Supplier<Vec3> at, double r, int rgb, int life) {
        Vec3 c = at.get();
        for (int i = 0; i < 6; i++) {
            double phi = Math.toRadians(-75 + 30 * i), dphi = Math.toRadians(30);
            int n = Math.max(4, (int) Math.round(10 * Math.cos(phi)));
            for (int j = 0; j < n; j++) {
                double lam = j * Math.PI * 2 / n + i * 0.3;
                double cp = Math.cos(phi), sp = Math.sin(phi), cl = Math.cos(lam), sl = Math.sin(lam);
                Vec3 radial = new Vec3(cp * cl, sp, cp * sl), east = new Vec3(-sl, 0, cl), north = new Vec3(-sp * cl, cp, -sp * sl);
                double[][] rm = {{east.x, north.x, radial.x}, {east.y, north.y, radial.y}, {east.z, north.z, radial.z}};
                double w = 2 * Math.PI * r * cp / n * 0.92, h = r * dphi * 0.92;
                Elem e = block(l, owner, c, (i + j) % 2 == 0 ? glass(rgb) : "minecraft:white_stained_glass", rgb,
                        m(rm, 0.01, 0.01, 0.01, radial.scale(r * 0.3), true), life, 1);
                e.follow(at, new Vec3(0, 1, 0)).key(1 + (i + j) % 4, m(rm, w, h, 0.06, radial.scale(r), true), 4)
                        .key(life - 5, m(rm, w * 1.3, h * 1.3, 0.01, radial.scale(r * 1.4), true), 5).spin(3);
            }
        }
    }

    /** 대상 히트박스 와이어프레임 (디버그 화면처럼, 대상을 따라다님). */
    static void hitbox(ServerLevel l, String owner, Entity target, int rgb, int life) {
        double w = target.getBbWidth() / 2 + 0.1, h = target.getBbHeight() + 0.1;
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) v[i] = new Vec3((i & 1) == 0 ? -w : w, (i & 2) == 0 ? 0 : h, (i & 4) == 0 ? -w : w);
        int[][] ed = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        Supplier<Vec3> s = target::position;
        for (int[] e2 : ed) {
            Elem e = block(l, owner, target.position(), "minecraft:white_concrete", rgb, bar(v[e2[0]].scale(1.4), v[e2[1]].scale(1.4), 0.05, 0.05, 0), life, 1);
            e.follow(s, Vec3.ZERO).key(1, bar(v[e2[0]], v[e2[1]], 0.05, 0.05, 0), 3).key(life - 3, bar(v[e2[0]], v[e2[1]], 0.001, 0.001, 0), 3);
        }
    }

    /** 떨어지는 코드 줄기 (매트릭스 비). */
    static void codeDrop(ServerLevel l, String owner, Vec3 top, double fall, int rgb, java.util.Random rnd) {
        StringBuilder sb = new StringBuilder();
        String chars = "01/@~$#{}[]λΣΩ∞⌘";
        int n = 4 + rnd.nextInt(5);
        for (int i = 0; i < n; i++) { if (i > 0) sb.append("\\n"); sb.append(chars.charAt(rnd.nextInt(chars.length()))); }
        String t = "{text:\"" + sb.toString().replace("\"", "") + "\",color:\"#" + String.format("%06X", rgb & 0xFFFFFF) + "\",font:\"minecraft:uniform\"}";
        double sc = 0.9 + rnd.nextDouble() * 0.8;
        int life = 18 + rnd.nextInt(10);
        Elem e = text(l, owner, top, t, "vertical", 0, false, m(I(), sc, sc, sc, Vec3.ZERO, false), life, 0);
        e.key(1, m(I(), sc, sc, sc, new Vec3(0, -fall, 0), false), life - 3).key(life - 3, m(I(), sc, 0.01, sc, new Vec3(0, -fall, 0), false), 3);
    }

    /** 파편: 바닥 블록 조각이 튀어 오르며 회전하다 사라짐 (디스플레이). */
    static void shards(ServerLevel l, String owner, Vec3 c, String block, int n, double power) {
        java.util.Random rnd = new java.util.Random(uid * 31L);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 0.5 + rnd.nextDouble() * power, up = 1 + rnd.nextDouble() * power * 0.8;
            double s = 0.25 + rnd.nextDouble() * 0.5;
            double[][] R0 = mul(rotX(rnd.nextDouble() * 6), rotY(rnd.nextDouble() * 6)), R1 = mul(rotX(rnd.nextDouble() * 6), rotZ(rnd.nextDouble() * 6));
            Vec3 mid = new Vec3(Math.cos(a) * d * 0.6, up, Math.sin(a) * d * 0.6), end = new Vec3(Math.cos(a) * d, -0.4, Math.sin(a) * d);
            Elem e = block(l, owner, c, block, -1, m(R0, s, s, s, Vec3.ZERO, true), 22, 0);
            e.key(1, m(R1, s, s, s, mid, true), 7).key(8, m(R0, s * 0.8, s * 0.8, s * 0.8, end, true), 9).key(17, m(R1, 0.01, 0.01, 0.01, end, true), 4);
        }
    }

    // ═════════════════════════ 고속 경로: 명령 파싱·NBT 왕복 없이 직접 처리
    static final class Fast {
        static boolean broken = false;
        static java.lang.reflect.Method mT, mDur, mDelay, mText;
        static {
            try {
                Class<?> c = net.minecraft.world.entity.Display.class;
                mT = c.getDeclaredMethod("setTransformation", com.mojang.math.Transformation.class);
                mDur = c.getDeclaredMethod("setTransformationInterpolationDuration", int.class);
                mDelay = c.getDeclaredMethod("setTransformationInterpolationDelay", int.class);
                mT.setAccessible(true); mDur.setAccessible(true); mDelay.setAccessible(true);
            } catch (Throwable t) { mT = null; com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 디스플레이 고속 키프레임 불가 → 명령 사용", t); }
            try {
                mText = net.minecraft.world.entity.Display.TextDisplay.class.getDeclaredMethod("setText", net.minecraft.network.chat.Component.class);
                mText.setAccessible(true);
            } catch (Throwable t) { mText = null; }
        }

        static boolean key(net.minecraft.world.entity.Display d, float[] m, int dur) {
            if (mT == null) return false;
            try {
                org.joml.Matrix4f mx = new org.joml.Matrix4f().set(m).transpose();   // 행 우선 → JOML 열 우선
                mT.invoke(d, new com.mojang.math.Transformation(mx));
                mDur.invoke(d, dur);
                mDelay.invoke(d, 0);
                return true;
            } catch (Throwable t) { mT = null; return false; }
        }

        static boolean text(net.minecraft.world.entity.Display.TextDisplay d, String snbt) {
            if (mText == null) return false;
            try {
                net.minecraft.nbt.Tag tag = net.minecraft.nbt.TagParser.parseCompoundFully("{t:" + snbt + "}").get("t");
                var ops = d.level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                var c = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(ops, tag).result();
                if (c.isEmpty()) return false;
                mText.invoke(d, c.get());
                return true;
            } catch (Throwable t) { return false; }
        }

        /** "summon <type> x y z {nbt}" → 직접 생성. 실패하면 null (명령으로 대체). */
        static Entity spawn(ServerLevel level, String summon) {
            if (broken) return null;
            try {
                String[] h = summon.split(" ", 6);
                if (h.length < 6 || !h[0].equals("summon")) return null;
                var type = switch (h[1]) {
                    case "block_display" -> net.minecraft.world.entity.EntityType.BLOCK_DISPLAY;
                    case "item_display" -> net.minecraft.world.entity.EntityType.ITEM_DISPLAY;
                    case "text_display" -> net.minecraft.world.entity.EntityType.TEXT_DISPLAY;
                    default -> null;
                };
                if (type == null) return null;
                double x = Double.parseDouble(h[2]), y = Double.parseDouble(h[3]), z = Double.parseDouble(h[4]);
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(h[5]);
                net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(x)); pos.add(net.minecraft.nbt.DoubleTag.valueOf(y)); pos.add(net.minecraft.nbt.DoubleTag.valueOf(z));
                tag.put("Pos", pos);
                Entity en = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (en == null) return null;
                en.deserializeNBT(level.registryAccess(), tag);
                float yaw = en.getYRot(), pitch = en.getXRot();
                en.snapTo(x, y, z, yaw, pitch);
                if (!level.addFreshEntity(en)) return null;
                return en;
            } catch (Throwable t) {
                broken = true;
                com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 디스플레이 직접 생성 실패 → 명령 사용", t);
                return null;
            }
        }
    }
}
