package com.tubilt.jjk.client;

import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import java.util.Random;

/**
 * 「/tp 행성 낙하」 시네마틱 v6 — 47초, 5막. 화면 셰이더(PlanetShader: HDR 장면 + 블룸 + 후처리) 기반.
 *  1막 서막   0~150  : 밤이 내려앉고 명령 터미널이 열림 · /locate · 하늘에 /fill structure_void 균열 → 균열 속으로 돌진
 *  2막 항해 150~270  : 지구 궤도에서 워프 → 붉은 행성 KEPLER-442b 근접 비행 (역광 일식 고리) · /fill 선택 상자 고정
 *  3막 견인 270~470  : 지구 시전 지점에서 /ride 사슬 12줄이 우주를 가로질러 행성을 묶음 → /tp 견인 · 조석 파편 줄기 ·
 *                      궤도에서 본 대기권 돌입 (충격파 덮개, 지구 대기가 불탐)
 *  4막 낙하 470~680  : 붉게 물든 하늘을 덮는 행성 · 유성우 · 이온화 코로나 · 돌입 충격파 · 카운트다운
 *  5막 충돌 680~940  : 임팩트 프레임 → 섬광 잔상 → 응결 돔 · 화구 · 분출물 · 충격파 굴절 · 버섯구름 · 용암 분화구
 * 시간축은 게임 틱 → 서버(CommandPlanet)와 정확히 맞음.
 */
public final class PlanetCine {
    private PlanetCine() {}

    public static final int LENGTH = 940;
    public static final int RIFT = 60, ASCEND = 120, SPACE = 150, REVEAL = 215, BOXLOCK = 240, LOCK = 270, HAUL = 320,
            CHASE = 380, ORBIT = 420, DESCENT = 470, ENTRY = 590, HIT = 680, END = 900;
    /** 에필로그: 우주에서 본 지구 충돌. */
    public static final int EPI0 = 845, EPI1 = 897;
    /** 지상 행성 반지름 · 출현 고도. */
    public static final double GR = 700, FAR = 30000, NEARALT = 2600;
    /** 예전 메시 렌더(대체 경로)용. */
    public static final double SIZE = 220;
    public static final float FOV = 70f, SPACE_FOV = 55f;

    static boolean active = false, cam = false;   // active = 연출 진행 중 (주변 모두), cam = 시전자 시네마틱
    static boolean captured = false;
    static long start;
    static Vec3 P, T, F, R;
    static long dayFrom = -1;
    static final Random RNG = new Random();

    static double cl(double x) { return Math.max(0, Math.min(1, x)); }
    static double sm(double a, double b, double x) { double t = cl((x - a) / (b - a)); return t * t * (3 - 2 * t); }
    static double pulse(double t, double at, double len) { return t >= at && t < at + len ? 1 : 0; }

    /** 지상 행성 중심 고도 (목표 지점 기준) — 서버와 같은 곡선. */
    public static double alt(double t) {
        if (t < HAUL + 10) return FAR;
        if (t < DESCENT) { double u = (t - HAUL - 10) / (DESCENT - HAUL - 10); return FAR + (NEARALT - FAR) * u * u; }
        if (t < HIT) { double u = (t - DESCENT) / (HIT - DESCENT); return NEARALT - (NEARALT - GR) * Math.pow(u, 1.8); }
        double u = cl((t - HIT) / 45.0);
        return GR * (1 - 0.75 * (1 - (1 - u) * (1 - u)));
    }

    // ───────────── 우주 장면 (단위: 지구 반지름)
    static final Vec3 SITE = new Vec3(0.35, 0.602, 0.72).normalize();   // 지구 텍스처에서 한반도 위치
    static final Vec3 SUN_S = new Vec3(0.55, 0.50, 0.65).normalize();
    static final Vec3 P0 = new Vec3(-0.70, 0.45, -0.55).normalize().scale(7.0);
    static final double RS = 0.27;
    static final Vec3 E1 = SITE.cross(new Vec3(0, 1, 0)).normalize();
    static final Vec3 E2 = SITE.cross(E1).normalize();
    // 워프 출발점·도착점, 근접 비행 궤도
    static final Vec3 WA = SITE.scale(1.18).add(E1.scale(0.1));
    static final Vec3 D0 = WA.subtract(P0).normalize();
    static final Vec3 WB = P0.add(D0.scale(RS * 3.4)).add(E2.scale(RS * 0.8));
    static final Vec3 D1 = SUN_S.scale(-1).add(D0.scale(0.35)).normalize();

    static Vec3 planetS(double t) {
        if (t < HAUL) return P0;
        if (t < ORBIT) {
            double u = (t - HAUL) / (ORBIT - HAUL), e = u * u;
            return P0.lerp(SITE.scale(1.9), e).add(E2.scale(Math.sin(Math.PI * u) * 0.9));
        }
        double u = cl((t - ORBIT) / (DESCENT - ORBIT));
        return SITE.scale(1.9).lerp(SITE.scale(1.3), u * (2 - u));
    }

    static Vec3 slerp(Vec3 a, Vec3 b, double u) {
        double d = Math.max(-1, Math.min(1, a.dot(b))), th = Math.acos(d);
        if (th < 1e-4) return a;
        double s = Math.sin(th);
        return a.scale(Math.sin((1 - u) * th) / s).add(b.scale(Math.sin(u * th) / s));
    }

    /** {위치, 바라보는 점} */
    static Vec3[] spaceCam(double t) {
        Vec3 pl = planetS(t);
        if (t < REVEAL) {                                    // 워프: 지구 궤도 → 행성 앞
            double u = sm(156, 212, t);
            u = u * u * (3 - 2 * u);
            Vec3 pos = WA.lerp(WB, u);
            Vec3 look = P0.add(E1.scale(RS * 0.4 * (1 - u)));
            return new Vec3[]{pos, look};
        }
        if (t < LOCK) {                                      // 근접 비행: 해가 행성 가장자리 뒤로 떠오름
            double u = sm(REVEAL, LOCK, t);
            Vec3 dir = slerp(D0, D1, u * 0.85);
            double dist = RS * (3.4 - 0.9 * u);
            Vec3 side = dir.cross(E2).normalize();
            return new Vec3[]{P0.add(dir.scale(dist)), P0.add(side.scale(RS * 0.55))};
        }
        if (t < 296) {                                       // 행성 뒤에서 지구를 봄: 사슬이 날아옴
            double u = sm(LOCK, 296, t);
            Vec3 back = P0.subtract(SITE).normalize();
            Vec3 pos = P0.add(back.scale(RS * (2.2 + 0.4 * u))).add(E2.scale(RS * 1.3)).add(E1.scale(RS * 0.7));
            return new Vec3[]{pos, P0.lerp(SITE, 0.35)};
        }
        if (t < HAUL) {                                      // 지구 궤도에서 행성을 봄 (사슬 팽팽)
            double u = sm(296, HAUL, t);
            Vec3 a = SITE.scale(1.16).add(E1.scale(0.10)), b = SITE.scale(1.22).subtract(E1.scale(0.12)).add(E2.scale(0.10));
            return new Vec3[]{a.lerp(b, u), pl};
        }
        if (t < CHASE) {                                     // 견인: 측면 원경
            double u = sm(HAUL, CHASE, t);
            Vec3 a = SITE.add(E1.scale(0.7)).add(E2.scale(0.3)).normalize().scale(4.2);
            Vec3 b = SITE.subtract(E1.scale(0.3)).add(E2.scale(0.6)).normalize().scale(4.8);
            return new Vec3[]{a.lerp(b, u), pl.scale(0.45 + 0.1 * u)};
        }
        if (t < ORBIT) {                                     // 행성 추적
            double u = cl((t - CHASE) / (ORBIT - CHASE));
            Vec3 back = pl.subtract(SITE).normalize();
            Vec3 pos = pl.add(back.scale(0.95 - 0.3 * u)).add(E2.scale(0.35)).add(E1.scale(0.15));
            return new Vec3[]{pos, SITE};
        }
        double u = sm(ORBIT, DESCENT, t);                    // 저궤도에서 올려다본 대기권 돌입
        Vec3 pos = SITE.add(E1.scale(0.95 - 0.08 * u)).add(E2.scale(0.1)).normalize().scale(1.14 - 0.02 * u);
        return new Vec3[]{pos, pl.add(SITE.scale(-0.12))};
    }

    // ───────────── 지상 카메라 키
    private record Key(double t, Vec3 pos, Vec3 look, double fov) {}
    static Key[] keys;

    static Vec3 at(double r, double f, double up) { return T.add(R.scale(r)).add(F.scale(f)).add(0, up, 0); }
    static Vec3 planetG(double t) { return T.add(0, alt(t), 0); }

    public static void start(Payloads.Planet p) {
        T = new Vec3(p.tx(), p.ty(), p.tz());
        cam = !Double.isNaN(p.px());
        var me = Minecraft.getInstance().player;
        P = cam ? new Vec3(p.px(), p.py(), p.pz()) : me != null ? me.getEyePosition() : T;
        Vec3 f = new Vec3(T.x - P.x, 0, T.z - P.z);
        F = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        R = new Vec3(-F.z, 0, F.x);
        Vec3 rift = T.add(0, 180, 0);
        keys = new Key[]{
                // 1막 서막
                new Key(0, P.subtract(F.scale(4)).add(R.scale(1.5)).add(0, 0.8, 0), P.add(F.scale(40)).add(0, 12, 0), 60),
                new Key(45, P.subtract(F.scale(3)).subtract(R.scale(1.5)).add(0, 1.5, 0), P.add(F.scale(40)).add(0, 34, 0), 55),
                new Key(46, at(-40, -120, 6), rift, 75),
                new Key(RIFT, at(-46, -132, 8), rift, 75),
                new Key(100, at(-30, -100, 26), rift, 70),
                new Key(101, at(0, -30, 18), rift, 85),
                new Key(ASCEND, at(0, -22, 50), rift, 85),
                new Key(146, at(0, -6, 172), rift, 100),            // 균열 속으로 돌진
                new Key(SPACE, at(0, 0, 250), rift.add(0, 400, 0), 70),
                new Key(DESCENT - 1, at(0, 0, 250), rift.add(0, 400, 0), 70),
                // 4막 낙하
                new Key(DESCENT, at(-900, -1200, 40), null, 60),      // 초원경: 하늘을 덮는 행성
                new Key(510, at(-840, -1120, 110), null, 62),
                new Key(511, at(40, -60, 4), null, 90),              // 땅에서 올려다봄
                new Key(545, at(30, -50, 3), null, 95),
                new Key(546, P.subtract(F.scale(3)).add(R.scale(1.2)).add(0, 1.0, 0), null, 65),   // 시전자 어깨 너머
                new Key(ENTRY - 1, P.subtract(F.scale(4.5)).subtract(R.scale(1.6)).add(0, 1.6, 0), null, 60),
                new Key(ENTRY, at(0, -150, 170), T, 70),             // /kill 처형 구역 내려다봄
                new Key(604, at(30, -120, 130), T, 72),
                new Key(605, at(280, -40, 140), null, 70),           // 측면 추적
                new Key(640, at(320, -10, 80), null, 75),
                new Key(641, at(-120, -200, 2), null, 95),           // 마지막 순간: 하늘이 행성으로 막힘
                new Key(664, at(-112, -190, 2), null, 100),
                // 5막 충돌
                new Key(665, at(-760, -860, 60), T.add(0, 120, 0), 65),
                new Key(745, at(-820, -920, 100), T.add(0, 190, 0), 62),
                new Key(746, at(-1300, -1450, 260), T.add(0, 230, 0), 55),   // 버섯구름 초원경
                new Key(810, at(-1350, -1500, 300), T.add(0, 300, 0), 55),
                new Key(811, at(0, -650, 700), T, 70),               // 분화구 항공
                new Key(EPI0 - 1, at(80, -600, 640), T, 70),
                new Key(EPI1, P.add(0, 2, 0).subtract(F.scale(4)), T, 70),
                new Key(LENGTH, P, T, 70)};
        var lv = Minecraft.getInstance().level;
        start = lv == null ? 0 : lv.getGameTime();
        dayFrom = lv == null ? -1 : lv.getDayTime();
        captured = false;
        active = true;
    }

    static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }

    /** 연출 진행 중인지 — 주변 모든 플레이어. */
    public static boolean renderActive() {
        if (active && (now() - start > LENGTH || Minecraft.getInstance().level == null)) { active = false; restoreTime(); }
        return active;
    }
    /** 시전자 시네마틱(카메라·HUD) 진행 중인지. */
    public static boolean active() { return renderActive() && cam; }
    public static double elapsed(float pt) { return time(pt); }
    static double time(float pt) { return (now() - start) + pt; }
    /** 지금 우주 장면을 화면 전체에 그리는 중인지. */
    static boolean spaceView(double t) { return cam && (t >= SPACE && t < DESCENT || t >= EPI0 && t < EPI1); }

    static void setDay(long day) {
        try {
            Object ld = Minecraft.getInstance().level.getLevelData();
            ld.getClass().getMethod("setDayTime", long.class).invoke(ld, day);
        } catch (Throwable ignored) { }
    }
    static void restoreTime() {
        if (dayFrom >= 0 && Minecraft.getInstance().level != null) setDay(dayFrom + LENGTH);
        dayFrom = -1;
    }

    static int keyAt(double t) {
        for (int i = 0; i < keys.length - 1; i++) if (t >= keys[i].t && t <= keys[i + 1].t) return i;
        return keys.length - 2;
    }

    /** 이번 프레임 화각 (시전자만 샷마다 다름, 주변은 고정). */
    public static float fov() {
        if (!active() || keys == null) return FOV;
        double t = time(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (spaceView(t)) return FOV;
        int i = keyAt(t);
        Key a = keys[i], b = keys[i + 1];
        double u = b.t == a.t ? 1 : smooth(cl((t - a.t) / (b.t - a.t)));
        double f = a.fov + (b.fov - a.fov) * u;
        if (t >= HIT && t < HIT + 30) f *= 1 + 0.12 * Math.exp(-(t - HIT) / 8.0);   // 충돌 순간 화각 펀치
        return (float) f;
    }

    public static Cinematic.Shot shot(float pt) {
        if (!active()) return null;
        double t = time(pt);
        if (t > 20 && t < LENGTH - 20 && dayFrom >= 0) setDay((dayFrom / 24000) * 24000 + 18000);   // 한밤
        Vec3 planet = planetG(t);
        int i = keyAt(t);
        Key a = keys[i], b = keys[i + 1];
        double u = b.t == a.t ? 1 : smooth(cl((t - a.t) / (b.t - a.t)));
        Vec3 pos = a.pos.lerp(b.pos, u);
        Vec3 la = a.look == null ? planet : a.look, lb = b.look == null ? planet : b.look;
        Vec3 look = la.lerp(lb, u);
        double shake = t > DESCENT + 20 && t < HIT ? 0.2 + (t - DESCENT - 20) / (HIT - DESCENT - 20.0) * 2.8
                : t >= HIT && t < HIT + 70 ? 4.0 * Math.pow(1 - (t - HIT) / 70.0, 2)
                : t > 128 && t < SPACE ? 0.8 : t > RIFT && t < RIFT + 12 ? 0.5 : 0;
        if (shake > 0) {
            double k = t * 1.7;   // 부드러운 흔들림 (잡음 대신 엇갈린 사인)
            pos = pos.add((Math.sin(k * 3.1) + 0.5 * Math.sin(k * 7.3) + (RNG.nextDouble() - 0.5) * 0.4) * shake * 0.5,
                    (Math.sin(k * 2.7 + 1) + 0.5 * Math.sin(k * 6.1)) * shake * 0.5,
                    (Math.sin(k * 3.7 + 2) + 0.5 * Math.sin(k * 5.3)) * shake * 0.5);
        }
        pos = aboveGround(pos);
        Vec3 d = look.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new Cinematic.Shot(pos, yaw, pitch);
    }

    static double smooth(double u) { return u * u * (3 - 2 * u); }

    /** 낮은 샷이 지형 속에 묻히지 않게: 그 자리 지면 높이 + 1.6 이상. */
    static Vec3 aboveGround(Vec3 pos) {
        var lv = Minecraft.getInstance().level;
        if (lv == null) return pos;
        int x = (int) Math.floor(pos.x), z = (int) Math.floor(pos.z);
        int hy = lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        return hy > lv.getMinY() && pos.y < hy + 1.6 ? new Vec3(pos.x, hy + 1.6, pos.z) : pos;
    }

    /** 안개 밀어내기 + FOV 고정은 ScreenFx 에서 (fov()). */
    public static void init() {
        ViewportEvent.RenderFog.BUS.addListener((java.util.function.Predicate<ViewportEvent.RenderFog>) e -> {
            if (!renderActive()) return false;
            e.setNearPlaneDistance(900f);
            e.setFarPlaneDistance(2400f);
            return true;
        });
    }

    // ───────────── 셰이더 값 채우기
    static Vector3f v(Vec3 a) { return new Vector3f((float) a.x, (float) a.y, (float) a.z); }
    static void basis(PlanetShader.Frame f, Vec3 fwd) {
        Vec3 fw = fwd.normalize();
        Vec3 right = fw.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(fw).normalize();
        f.fwd.set((float) fw.x, (float) fw.y, (float) fw.z);
        f.right.set((float) right.x, (float) right.y, (float) right.z);
        f.up.set((float) up.x, (float) up.y, (float) up.z);
    }

    /** yaw/pitch(도) → 앞 방향 (마인크래프트 규약). */
    static Vec3 forward(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    /** 카메라 기준 점 → GL 화면 uv (y 위쪽). 뒤면 null. */
    static float[] uv(Vec3 rel, PlanetShader.Frame f, double aspect) {
        double z = rel.x * f.fwd.x + rel.y * f.fwd.y + rel.z * f.fwd.z;
        if (z <= 1e-4) return null;
        double x = (rel.x * f.right.x + rel.y * f.right.y + rel.z * f.right.z) / (z * f.tanHalf * aspect);
        double y = (rel.x * f.up.x + rel.y * f.up.y + rel.z * f.up.z) / (z * f.tanHalf);
        return new float[]{(float) (x * 0.5 + 0.5), (float) (y * 0.5 + 0.5)};
    }
    static void light(PlanetShader.Frame f, float[] at, double strength, double flare, float r, float g, float b) {
        if (at == null || strength <= 0.001) return;
        double off = Math.max(Math.abs(at[0] - 0.5), Math.abs(at[1] - 0.5));
        double vis = 1 - sm(0.7, 1.4, off);   // 화면 밖으로 멀어지면 약해짐
        f.light[0] = at[0]; f.light[1] = at[1];
        f.light[2] = (float) (strength * vis);
        f.light[3] = (float) (flare * vis * (1 - sm(0.45, 0.6, off)));
        f.lightCol[0] = r; f.lightCol[1] = g; f.lightCol[2] = b;
    }
    static void impactFrame(PlanetShader.Frame f, int mode, float cx, float cy) {
        f.impactF[0] = 1; f.impactF[1] = mode; f.impactF[2] = cx; f.impactF[3] = cy;
    }

    /** 후처리 기본값. */
    static void resetPost(PlanetShader.Frame f) {
        f.bloom = 0.55f; f.ca = 0; f.zoom = 0; f.exposure = 1; f.holdAmt = 0; f.grain = 0.12f; f.contrast = 0; f.sat = 1; f.film = 0;
        f.zoomX = 0.5f; f.zoomY = 0.5f;
        f.flash[0] = f.flash[1] = f.flash[2] = 1; f.flash[3] = 0;
        f.vig[0] = f.vig[1] = f.vig[2] = 0; f.vig[3] = 0.45f;
        f.wave[3] = 0; f.light[2] = 0; f.light[3] = 0; f.impactF[0] = 0;
        f.capture = false;
        f.vox = 0; f.ring = 0; f.grid = 0; f.glitch = 0; f.textAmt = 1; f.sats = 0; f.impactE = -1;
    }

    /** @return false 면 이번 프레임은 그릴 것 없음. */
    static boolean fill(PlanetShader.Frame f, Vec3 camPos, float yaw, float pitch, float pt, float fovDeg, float far) {
        if (!renderActive() || T == null) return false;
        double t = time(pt);
        var mt = Minecraft.getInstance().getMainRenderTarget();
        double aspect = mt.width / (double) Math.max(1, mt.height);
        f.clock = (float) (t / 20.0);
        f.far = far;
        resetPost(f);
        f.atmo = 1;
        if (spaceView(t) && t >= EPI0) {     // 에필로그: 궤도에서 내려다본 지구 — 충격파가 대륙을 휩씀
            f.mode = 1;
            double u = sm(EPI0, EPI1, t);
            Vec3 dir = SITE.add(E1.scale(0.9 - 0.6 * u)).add(E2.scale(-0.35 + 0.15 * u)).normalize();
            Vec3 cp = dir.scale(3.1 - 0.5 * u);
            basis(f, SITE.scale(0.92).subtract(cp));
            f.tanHalf = (float) Math.tan(Math.toRadians(SPACE_FOV) / 2);
            f.camS.set(v(cp));
            f.p.set(v(SITE.scale(1.0001)));
            f.r = 0; f.heat = 0; f.cracks = 0; f.brk = 0; f.box = 0; f.chains = 0; f.reach = 1; f.pull = 0;
            f.lead.set(v(SITE.scale(-1)));
            f.sun.set(v(SUN_S)); f.site.set(v(SITE));
            f.warp = 0; f.debris = 0; f.shock = 0; f.sats = 0;
            f.earthGlow = (float) (0.7 * (1 - 0.5 * u));
            f.impactE = (float) (t - HIT);
            f.spin.identity(); f.eSpin.identity().rotateY(1.7729f);
            f.fadeR = f.fadeG = f.fadeB = 1f;
            f.fadeA = (float) Math.min(sm(EPI0, EPI0 + 8, t), 1 - sm(EPI1 - 6, EPI1, t));
            f.impact = -1; f.meteors = 0; f.lava = 0; f.ground = 0;
            f.film = 0.6f; f.bloom = 0.5f; f.vig[3] = 0.6f;
            light(f, uv(SITE.subtract(cp), f, aspect), 0.3, 0.25, 1f, 0.6f, 0.3f);
            return true;
        }
        if (spaceView(t)) {
            f.mode = 1;
            f.impactE = -1;
            f.sats = (float) (sm(LOCK - 24, LOCK - 4, t) * (1 - sm(ORBIT, ORBIT + 20, t)));
            Vec3[] c = spaceCam(t);
            Vec3 pl = planetS(t);
            basis(f, c[1].subtract(c[0]));
            f.tanHalf = (float) Math.tan(Math.toRadians(SPACE_FOV) / 2);
            f.camS.set(v(c[0]));
            f.p.set(v(pl));
            f.r = (float) RS;
            f.lead.set(v(SITE.subtract(pl).normalize()));
            f.sun.set(v(SUN_S));
            f.site.set(v(SITE));
            f.heat = (float) (0.25 * sm(CHASE, ORBIT, t) + 0.55 * sm(ORBIT, DESCENT - 5, t));
            f.cracks = (float) (0.6 * sm(330, DESCENT, t));
            f.brk = 0;
            f.box = (float) (0.35 * sm(BOXLOCK, BOXLOCK + 6, t) * (1 - sm(LOCK - 6, LOCK, t)));
            f.chains = (float) cl((t - LOCK) / 10.0);
            f.reach = (float) sm(LOCK + 2, 292, t);
            f.pull = (float) (t < HAUL ? 0.15 : 0.3 + 0.7 * sm(HAUL, ORBIT, t));
            f.warp = (float) (sm(156, 166, t) * (1 - sm(198, 212, t)));
            f.debris = (float) sm(335, 360, t);
            f.shock = (float) sm(ORBIT + 5, ORBIT + 30, t);
            f.earthGlow = (float) sm(ORBIT + 10, DESCENT, t);
            f.spin.identity().rotateY((float) (t * 0.006)).rotateX(0.35f);
            f.eSpin.identity().rotateY(1.7729f);
            double fin = sm(SPACE, SPACE + 8, t), fout = 1 - sm(DESCENT - 8, DESCENT, t);
            f.fadeR = f.fadeG = f.fadeB = 1f;
            f.fadeA = (float) Math.min(fin, fout);
            f.impact = -1;
            f.meteors = 0; f.lava = 0; f.ground = 0;
            // ── 후처리
            f.film = 0.6f;
            f.bloom = 0.7f;
            f.zoom = f.warp * 1.1f;
            f.ca = f.warp * 1.8f + 0.15f;
            Vec3 cp = c[0];
            if (t >= REVEAL && t < LOCK) {        // 행성 뒤에서 떠오르는 해
                light(f, uv(SUN_S.scale(1000), f, aspect), 1.0, 0.9, 1f, 0.85f, 0.7f);
            } else if (t >= ORBIT) {             // 충격파 덮개
                light(f, uv(pl.add(SITE.subtract(pl).normalize().scale(RS)).subtract(cp), f, aspect), 0.7, 0.4, 1f, 0.55f, 0.25f);
            } else if (t >= LOCK && t < 296) {
                light(f, uv(SUN_S.scale(1000), f, aspect), 0.5, 0.6, 1f, 0.85f, 0.7f);
            }
            if (t >= 292 && t < 294) impactFrame(f, 3, 0.5f, 0.5f);                 // 사슬이 행성에 박힘
            if (t >= HAUL && t < HAUL + 3) {                                         // /tp 실행
                float[] q = uv(pl.subtract(cp), f, aspect);
                impactFrame(f, t < HAUL + 1.5 ? 1 : 0, q == null ? 0.5f : q[0], q == null ? 0.5f : q[1]);
            }
            double kick = t >= HAUL ? Math.exp(-(t - HAUL) / 10.0) : 0;
            f.zoom += (float) (kick * 1.2);
            f.ca += (float) (kick * 1.2);
            f.vig[3] = 0.55f;
            f.vox = (float) sm(BOXLOCK, BOXLOCK + 26, t);
            f.ring = (float) (sm(BOXLOCK + 4, BOXLOCK + 14, t) * (1 - sm(ORBIT, ORBIT + 20, t)));
            f.textAmt = 1;
            f.glitch = glitch(t);
            return true;
        }
        f.mode = 0;
        basis(f, forward(yaw, pitch));
        f.tanHalf = (float) Math.tan(Math.toRadians(fovDeg) / 2);
        Vec3 pl = planetG(t);
        f.p.set(v(pl.subtract(camPos)));
        f.camW.set(v(camPos.subtract(T)));
        double appear = sm(HAUL - 4, HAUL + 4, t);
        f.r = (float) (GR * appear);
        f.lead.set(0, -1, 0);
        f.sun.set(0.45f, 0.35f, -0.8f).normalize();
        f.heat = (float) (t < DESCENT ? 0 : t < ENTRY ? 0.2 + 0.4 * sm(DESCENT, ENTRY, t) : t < HIT ? 0.6 + 0.4 * sm(ENTRY, HIT, t) : 1 - sm(HIT, HIT + 40, t));
        f.cracks = (float) (t < 330 ? 0 : t < DESCENT ? 0.5 * sm(330, DESCENT, t) : t < HIT ? 0.5 + 0.5 * sm(DESCENT, HIT - 10, t) : 1);
        f.brk = (float) cl((t - HIT) / 45.0);
        f.box = (float) (cam ? 0.5 * sm(DESCENT, DESCENT + 20, t) * (1 - sm(ENTRY - 10, ENTRY, t)) : sm(HAUL - 2, HAUL + 4, t) * (1 - sm(400, 420, t)));
        f.spin.identity().rotateY((float) (t * 0.004)).rotateX(0.35f);
        f.g.set(v(T.subtract(camPos)));
        f.impact = t >= HIT ? (float) (t - HIT) : -1f;
        double skyIn = cam ? Math.max(0.55 * sm(10, 60, t) * (1 - sm(SPACE, SPACE + 1, t)), sm(DESCENT, DESCENT + 30, t))
                : 0.3 * sm(HAUL, HAUL + 40, t) + 0.55 * sm(DESCENT, DESCENT + 30, t);
        f.skyMix = (float) (0.85 * Math.min(1, skyIn / 0.85) * (1 - sm(HIT + 120, END, t)));
        f.shadow = (float) (sm(DESCENT, HIT, t) * 0.55 * (1 - sm(HIT, HIT + 30, t)));
        f.beams = (float) (sm(HAUL, HAUL + 10, t) * (1 - sm(ENTRY - 10, ENTRY, t)));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + 0.4;
            f.anchors[i].set(v(T.add(Math.cos(a) * 40, 3, Math.sin(a) * 40).subtract(camPos)));
        }
        f.rift = (float) (sm(RIFT, ASCEND, t) * (1 - sm(DESCENT - 10, DESCENT + 10, t)));
        f.riftC.set(v(T.add(0, 180, 0).subtract(camPos)));
        f.meteors = (float) (sm(DESCENT + 10, DESCENT + 40, t) * (1 - sm(HIT - 5, HIT + 5, t)));
        f.shock = (float) (sm(ENTRY - 20, ENTRY + 20, t) * (1 - sm(HIT - 2, HIT + 4, t)));
        f.lava = (float) (sm(HIT + 20, HIT + 80, t) * (1 - sm(END + 10, LENGTH, t)));
        f.ground = 1;
        f.vox = t >= HAUL ? 1 : 0;
        f.ring = (float) (sm(HAUL, HAUL + 10, t) * (1 - sm(ENTRY - 10, ENTRY, t)));
        f.grid = (float) (sm(ENTRY - 4, ENTRY + 10, t) * (1 - sm(HIT + 5, HIT + 30, t)));
        f.textAmt = 1;
        f.glitch = glitch(t);
        f.warp = 0; f.debris = 0; f.earthGlow = 0; f.chains = 0;
        f.fadeA = 1f;

        // ── 후처리
        Vec3 gRel = T.subtract(camPos);
        if (cam && t < SPACE) {                       // 서막: 색이 빠진 서늘한 밤
            f.sat = (float) (1 - 0.3 * sm(5, 40, t));
            f.contrast = 0.12f;
            f.vig[0] = 0.02f; f.vig[1] = 0.04f; f.vig[2] = 0.09f; f.vig[3] = 0.6f;
            f.ca = (float) (0.3 * sm(RIFT, RIFT + 10, t) + 1.4 * sm(126, 148, t));
            f.zoom = (float) (1.3 * sm(126, 148, t));
            f.flash[3] = (float) (1.8 * sm(138, SPACE, t));
            float[] r = uv(T.add(0, 180, 0).subtract(camPos), f, aspect);
            if (t > RIFT) light(f, r, 0.8 * sm(RIFT, ASCEND, t), 0.4, 0.4f, 1f, 1f);
            if (r != null) { f.zoomX = r[0]; f.zoomY = r[1]; }
            if (t >= RIFT && t < RIFT + 2) impactFrame(f, 3, r == null ? 0.5f : r[0], r == null ? 0.5f : r[1]);   // 하늘이 갈라짐
        }
        if (t >= DESCENT && t < HIT) {                // 낙하: 붉은 비네트 · 행성 아랫면 빛줄기
            double e = sm(ENTRY, HIT, t);
            f.vig[0] = 0.35f; f.vig[1] = 0.04f; f.vig[2] = 0.02f; f.vig[3] = (float) (0.3 + 0.4 * e);
            f.ca = (float) (0.2 + 0.9 * e);
            f.contrast = 0.1f;
            f.bloom = 0.45f;
            f.film = 0.6f;
            f.exposure = 0.9f;
            light(f, uv(pl.add(0, -GR * 0.95, 0).subtract(camPos), f, aspect), 0.25 + 0.35 * f.heat, 0.25, 1f, 0.5f, 0.2f);
            if (cam) f.flash[3] = (float) (1.6 * (1 - sm(DESCENT, DESCENT + 14, t)));
        }
        if (t >= HIT - 1 && !captured) { f.capture = true; captured = true; }
        if (t >= HIT) {
            double k = t - HIT;
            float[] g = uv(gRel, f, aspect);
            float gx = g == null ? 0.5f : g[0], gy = g == null ? 0.5f : g[1];
            if (k < 10) {
                int[] modes = {1, 0, 2, 1, 0};
                impactFrame(f, modes[Math.min(4, (int) (k / 2))], gx, gy);
            }
            f.flash[0] = 1f; f.flash[1] = 0.92f; f.flash[2] = 0.8f;
            f.flash[3] = k < 10 ? 0 : (float) (1.8 * Math.exp(-(k - 10) / 10.0));
            f.holdAmt = k < 10 ? 0 : (float) Math.max(0, Math.exp(-(k - 10) / 25.0) * 1.05 - 0.05);
            f.zoom = (float) (1.6 * Math.exp(-k / 14.0));
            f.zoomX = gx; f.zoomY = gy;
            f.ca = (float) (2.5 * Math.exp(-k / 20.0) + 0.2);
            f.bloom = (float) (0.55 + 1.0 * Math.exp(-k / 60.0));
            f.exposure = (float) (0.8 + 0.2 * sm(HIT, HIT + 100, t));
            f.vig[0] = 0.15f; f.vig[1] = 0.03f; f.vig[2] = 0.01f; f.vig[3] = 0.5f;
            f.film = 0.35f;
            f.contrast = 0.08f;
            if (k < 90 && g != null) {
                double dist = Math.max(1, gRel.length());
                double rs = 30 + k * 20;
                f.wave[0] = gx; f.wave[1] = gy;
                f.wave[2] = (float) Math.min(3, rs / (dist * 2 * f.tanHalf));
                f.wave[3] = (float) Math.exp(-k / 40.0);
            }
            double rf = 40 + 380 * (1 - Math.exp(-k / 18.0));
            light(f, uv(gRel.add(0, rf * 0.15 + k * 0.7, 0), f, aspect), 1.4 * Math.exp(-k / 80.0), 1.0 * Math.exp(-k / 40.0), 1f, 0.6f, 0.3f);
        }
        if (t > LENGTH - 12) f.flash[3] = Math.max(f.flash[3], 0f);
        return f.r > 0 || f.rift > 0 || f.impact >= 0 || f.skyMix > 0;
    }

    // ───────────── 명령 실행 (입력줄 타이핑 → 엔터 → 글리치)
    /** {실행 틱, 색, 명령, 실행 결과(%s = 시전자), 자동완성 후보...} */
    static final Object[][] CMDS = {
            {16.0, 0xFF8A50, "/locate planet kepler-442b", "The nearest kepler-442b is at [1.2e16, ~, ~] (1,206 light years away)", "planet", "structure", "biome", "poi"},
            {(double) RIFT, 0x55FFFF, "/fill ~ ~180 ~ ~ ~200 ~ minecraft:structure_void", "Successfully filled 40,401 block(s)", "structure_void", "structure_block", "stone"},
            {(double) ASCEND, 0x55FFFF, "/execute in jjk:orbit run tp @s ~ ~400 ~", "Teleported %s to 0.50, 400.00, 0.50", "jjk:orbit", "minecraft:the_end", "minecraft:overworld"},
            {SPACE + 8.0, 0xB45CFF, "/gamerule maxTravelSpeed 2147483647", "Gamerule maxTravelSpeed is now set to: 2147483647", "maxTravelSpeed", "maxEntityCramming", "maxCommandChainLength"},
            {(double) BOXLOCK, 0x55FFFF, "/data merge entity @e[type=planet,limit=1] {Glowing:1b}", "Modified entity data of Kepler-442b", "@e[type=planet]", "@e[type=player]", "@s"},
            {(double) LOCK, 0xB45CFF, "/ride @e[type=planet,limit=1] mount @s", "Kepler-442b started riding %s ×12", "mount", "dismount"},
            {(double) HAUL, 0x55FFFF, "/tp @e[type=planet,limit=1] @s", "Teleported Kepler-442b to %s", "@s", "@p", "~ ~ ~", "^ ^ ^"},
            {(double) ORBIT, 0xFF8A50, "/data merge entity @e[type=planet] {Motion:[0d,-31d,0d]}", "Modified entity data of Kepler-442b", "Motion", "NoGravity", "Glowing"},
            {(double) DESCENT, 0xFF3020, "/attribute @e[type=planet] minecraft:gravity base set 9.8", "Set base value of attribute Gravity for entity Kepler-442b to 9.8", "minecraft:gravity", "minecraft:scale", "minecraft:safe_fall_distance"},
            {(double) ENTRY, 0xFF3020, "/kill @e[distance=..80,type=!player]", "Killed 2,147 entities", "@e[distance=..80]", "@e[type=!player]", "@a"},
            {HIT - 6.0, 0xFFFFFF, "/function jjk:planet/impact", "Executed 1 command(s) from function 'jjk:planet/impact'", "jjk:planet/impact", "jjk:planet/reset"}};
    static final int TYPE = 22;   // 입력에 걸리는 틱
    /** 브리가디어식 인자 색 (명령어 이름 다음부터 돌아가며). */
    static final String[] ARG = {"§b", "§e", "§a", "§d", "§6"};

    static float glitch(double t) {
        double g = 0;
        for (Object[] c : CMDS) {
            double k = t - (double) c[0];
            if (k >= 0 && k < 14) g = Math.max(g, Math.exp(-k / 4.0));
        }
        return (float) g;
    }

    /** 명령어 구문 강조: 이름 흰색, 인자마다 색 순환, 선택자·NBT 괄호 안은 한 덩어리. */
    static String highlight(String cmd, int n) {
        StringBuilder out = new StringBuilder("§f");
        int arg = -1, depth = 0;
        for (int i = 0; i < Math.min(n, cmd.length()); i++) {
            char ch = cmd.charAt(i);
            if (ch == '[' || ch == '{') depth++;
            if (ch == ']' || ch == '}') depth = Math.max(0, depth - 1);
            if (ch == ' ' && depth == 0) { arg++; out.append(' ').append(ARG[arg % ARG.length]); continue; }
            out.append(ch);
        }
        return out.toString();
    }

    /** 화면 아래 채팅창 같은 명령 입력줄: 한 글자씩 입력 → 구문 강조 · 자동완성 → 엔터 → 위로 채팅 기록 + 실행 결과. */
    static void commandBar(GuiGraphics g, Font font, int w, int h, int bar, double t) {
        var me = Minecraft.getInstance().player;
        String who = me == null ? "Player" : me.getName().getString();
        int baseY = h - bar - 46;
        // ── 채팅 기록 (실행된 명령 + 결과, 최근 것이 아래)
        int line = 0;
        for (int i = CMDS.length - 1; i >= 0 && line < 5; i--) {
            Object[] c = CMDS[i];
            double age = t - (double) c[0];
            if (age < 10 || age > 170) continue;
            double a = Math.min(1, Math.min((age - 10) / 4.0, (170 - age) / 20.0));
            int al = (int) (255 * a);
            String out = String.format((String) c[3], who);
            int y = baseY - 18 - line * 20;
            g.fill(6, y - 11, 14 + Math.max(font.width((String) c[2]), font.width(out)), y + 9, ((int) (0x70 * a)) << 24);
            g.drawString(font, highlight((String) c[2], 999), 10, y - 10, (al << 24) | 0xFFFFFF, false);
            g.drawString(font, "§7§o[" + who + ": " + out + "]", 10, y, (al << 24) | 0xAAAAAA, false);
            line++;
        }
        // ── 입력줄
        for (Object[] c : CMDS) {
            double t0 = (double) c[0];
            if (t < t0 - TYPE || t > t0 + 10) continue;
            String cmd = (String) c[2];
            int col = (int) c[1];
            int x0 = 8, y = baseY, x1 = Math.min(w - 8, x0 + font.width(cmd) + 24);
            boolean done = t >= t0;
            double fade = done ? 1 - (t - t0) / 10.0 : 1;
            int a = (int) (0xB0 * fade);
            g.fill(x0, y - 3, x1, y + 11, (a << 24));
            g.fill(x0, y - 3, x0 + 2, y + 11, ((int) (255 * fade) << 24) | col);
            int n = done ? cmd.length() : (int) Math.min(cmd.length(), (t - (t0 - TYPE)) / (TYPE - 4.0) * cmd.length());
            n = Math.max(0, n);
            String typed = cmd.substring(0, n);
            String caret = !done && ((long) (t * 1.5)) % 2 == 0 ? "§f_" : "";
            g.drawString(font, highlight(cmd, n) + caret, x0 + 6, y, ((int) (255 * fade) << 24) | 0xFFFFFF, false);
            // 남은 부분 회색 미리보기 (자동완성 고스트)
            if (!done && n < cmd.length() && n > cmd.length() * 0.35)
                g.drawString(font, "§8" + cmd.substring(n, Math.min(cmd.length(), n + 18)), x0 + 6 + font.width(typed), y, ((int) (160 * fade) << 24) | 0x555555, false);
            // 자동완성 후보 (입력 중반)
            if (!done && n > cmd.length() * 0.35 && c.length > 4) {
                int bx = x0 + 6 + font.width(typed), by = y - 4 - (c.length - 4) * 10;
                int bw = 0;
                for (int i = 4; i < c.length; i++) bw = Math.max(bw, font.width((String) c[i]));
                g.fill(bx - 2, by - 2, bx + bw + 4, y - 3, 0xE0000000);
                for (int i = 4; i < c.length; i++)
                    g.drawString(font, (String) c[i], bx, by + (i - 4) * 10, i == 4 ? 0xFFFFFF55 : 0xFFAAAAAA, false);
            }
            // 명령 블록 아이콘 + 줄 번호
            g.drawString(font, "§8" + String.format("#%02d", java.util.Arrays.asList(CMDS).indexOf(c) + 1), x1 + 4, y, ((int) (255 * fade) << 24) | 0x555555, false);
            // 엔터: 입력줄이 번쩍 + 실행 표시
            if (done && t - t0 < 4) {
                int fa = (int) (200 * (1 - (t - t0) / 4.0));
                g.fill(x0, y - 3, x1, y + 11, (fa << 24) | col);
            }
            if (done) g.drawString(font, "§a✔ executed", x1 + 30, y, ((int) (255 * fade) << 24) | 0x55FF55, true);
        }
    }

    // ───────────── HUD
    static final Object[][] LOG = {
            {6.0, 0xAAAAAA, "> jjk-shell v4.2 · operator: §bcommand_guardian"},
            {16.0, 0xFF8A50, "/locate planet kepler-442b"},
            {32.0, 0xAAAAAA, "  found: kepler-442b @ 1,206 ly · r=7,000 km"},
            {RIFT + 0.0, 0x55FFFF, "/fill ~ ~180 ~ ~ ~200 ~ structure_void"},
            {ASCEND + 4.0, 0x55FFFF, "/execute in jjk:orbit run tp @s ~ ~400 ~"},
            {SPACE + 10.0, 0xB45CFF, "/gamerule maxTravelSpeed 2147483647"},
            {REVEAL + 2.0, 0xAAAAAA, "  target acquired · KEPLER-442b · 2.1e24 kg"},
            {BOXLOCK + 0.0, 0x55FFFF, "/fill hitbox @e[type=planet] → selected"},
            {LOCK + 0.0, 0xB45CFF, "/ride @e[type=planet] mount @s ×12"},
            {HAUL + 0.0, 0x55FFFF, "/tp @e[type=planet,limit=1] @s"},
            {340.0, 0xFF3020, "[WARN] tidal stress 340% · crust failure"},
            {ORBIT + 6.0, 0xFF3020, "[WARN] bow shock 48,000 K"},
            {DESCENT + 6.0, 0xFF3020, "/attribute @planet minecraft:gravity base set 9.8"},
            {ENTRY - 10.0, 0xFF3020, "[WARN] atmosphere entry · 31 km/s"},
            {ENTRY + 6.0, 0xFFFFFF, "/execute at @planet run kill @e[distance=..80]"},
            {HIT + 30.0, 0x55FF55, "[ OK ] planet impact · exit 0"}};

    /** 월드 점을 화면 좌표로 (마지막 셰이더 프레임 기준). */
    static float[] project(Vec3 rel, PlanetShader.Frame f, int w, int h) {
        double z = rel.x * f.fwd.x + rel.y * f.fwd.y + rel.z * f.fwd.z;
        if (z <= 1e-3) return null;
        double aspect = w / (double) h;
        double x = (rel.x * f.right.x + rel.y * f.right.y + rel.z * f.right.z) / (z * f.tanHalf * aspect);
        double y = (rel.x * f.up.x + rel.y * f.up.y + rel.z * f.up.z) / (z * f.tanHalf);
        if (Math.abs(x) > 1.2 || Math.abs(y) > 1.2) return null;
        return new float[]{(float) ((x + 1) * 0.5 * w), (float) ((1 - y) * 0.5 * h)};
    }

    static void frameBox(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int L = 8;
        g.fill(x0, y0, x0 + L, y0 + 1, col); g.fill(x0, y0, x0 + 1, y0 + L, col);
        g.fill(x1 - L, y0, x1, y0 + 1, col); g.fill(x1 - 1, y0, x1, y0 + L, col);
        g.fill(x0, y1 - 1, x0 + L, y1, col); g.fill(x0, y1 - L, x0 + 1, y1, col);
        g.fill(x1 - L, y1 - 1, x1, y1, col); g.fill(x1 - 1, y1 - L, x1, y1, col);
    }

    public static void render(GuiGraphics g, Font font, int w, int h) {
        double t = time(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        double slide = Math.max(0, Math.min(1, Math.min(t / 10.0, (LENGTH - t) / 10.0)));
        boolean shader = PlanetShader.ok;
        int bar = (int) (h / 7.5 * slide);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (bar > 2) { g.fill(0, bar - 1, w, bar, 0x60FF8A50); g.fill(0, h - bar, w, h - bar + 1, 0x60FF8A50); }
        // 셰이더 후처리가 없을 때만 HUD 섬광
        if (!shader) {
            if (t > 138 && t < SPACE) g.fill(0, 0, w, h, ((int) (255 * sm(138, SPACE, t)) << 24) | 0xFFFFFF);
            if (t >= DESCENT && t < DESCENT + 12) g.fill(0, 0, w, h, ((int) (255 * (1 - sm(DESCENT, DESCENT + 12, t))) << 24) | 0xFFFFFF);
            if (t >= HIT - 1 && t < HIT + 24) {
                int a = (int) (255 * (t < HIT + 1 ? (t - HIT + 1) / 2.0 : 1 - (t - HIT - 1) / 23.0));
                g.fill(0, 0, w, h, (Math.max(0, Math.min(255, a)) << 24) | 0xFFFFFF);
            }
        }
        // 대기권 돌입: 붉은 열기 맥동
        if (t > ENTRY && t < HIT) {
            int a = (int) ((10 + 40 * (t - ENTRY) / (HIT - ENTRY)) * (0.6 + 0.4 * Math.abs(Math.sin(t * 0.5))));
            g.fill(0, bar, w, h - bar, (Math.min(90, a) << 24) | 0xFF3010);
        }
        // 행성 표지 (히트박스 라벨 + 조준 모서리)
        PlanetShader.Frame f = PlanetRender.LAST;
        if (f != null && t > BOXLOCK && t < ENTRY) {
            Vec3 c = f.mode == 1 ? new Vec3(f.p.x - f.camS.x, f.p.y - f.camS.y, f.p.z - f.camS.z) : new Vec3(f.p.x, f.p.y, f.p.z);
            Vec3 rel = c.add(0, f.r * 1.25, 0);
            float[] s = project(rel, f, w, h), sc = project(c, f, w, h);
            if (sc != null && c.length() > f.r * 1.05) {
                double ang = Math.atan(f.r / Math.max(1e-6, c.length()));
                int rad = (int) Math.min(w, Math.tan(ang) / f.tanHalf * h / 2);
                double lock = sm(BOXLOCK, BOXLOCK + 8, t);
                int rr = (int) (rad * (1 + 0.6 * (1 - lock))) + 6;
                int col = (((int) (200 * lock)) << 24) | 0x55FFFF;
                if (rr < w) frameBox(g, (int) sc[0] - rr, (int) sc[1] - rr, (int) sc[0] + rr, (int) sc[1] + rr, col);
            }
            if (s != null) {
                String a = "§b@e[type=planet,name=KEPLER-442b]";
                String b2 = f.mode == 1 ? String.format("§7dist §f%,.0f km", c.length() * 6371) : String.format("§7alt §f%,.0f m", f.p.y + 0.0);
                int x = (int) s[0], y = (int) s[1] - 18;
                g.fill(x - font.width(a) / 2 - 4, y - 3, x + font.width(a) / 2 + 4, y + 21, 0x90000000);
                g.drawCenteredString(font, a, x, y, 0xFFFFFFFF);
                g.drawCenteredString(font, b2, x, y + 10, 0xFFFFFFFF);
            }
        }
        commandBar(g, font, w, h, bar, t);
        // 명령 터미널 (좌상단)
        int ly = bar + 8, shown = 0, vis = 0;
        for (Object[] e : LOG) if (t >= (double) e[0]) vis++;
        int skip = Math.max(0, vis - 7);
        int tw = 0;
        for (Object[] e : LOG) tw = Math.max(tw, font.width((String) e[2]));
        if (vis > 0 && t < END) {
            g.fill(6, ly - 4, 18 + tw, ly - 4 + Math.min(7, vis) * 11 + 6, 0x90000000);
            g.fill(6, ly - 4, 8, ly - 4 + Math.min(7, vis) * 11 + 6, 0xFF55FFFF);
            for (Object[] e : LOG) {
                double t0 = (double) e[0];
                if (t < t0 || skip-- > 0) continue;
                String s = (String) e[2];
                int n = (int) Math.min(s.length(), (t - t0) * 3 + 1);
                g.drawString(font, s.substring(0, n) + (n < s.length() && ((long) t / 3) % 2 == 0 ? "▌" : ""), 12, ly, 0xFF000000 | (int) e[1], true);
                ly += 11;
                if (++shown >= 7) break;
            }
        }
        // 고도·속도 (우상단)
        if (t > HAUL && t < HIT + 2) {
            double al = alt(t), vv = (alt(t - 1) - al) * 20;
            String[] ln = {"§6§lKEPLER-442b", String.format("§7ALT §f%,.0f m", al * 9.8e2), String.format("§7VEL §f%,.0f m/s", vv * 9.8e2 / 20 + 11.2e3),
                    "§7MASS §f2.1e24 kg", String.format("§7HULL §f%.0f%%", 100 - 100 * sm(HAUL, HIT, t)),
                    t < DESCENT ? "§b▶ /tp TRACTOR HAUL" : t < ENTRY ? "§c▶ ATMOSPHERIC ENTRY" : "§4§l▶ IMPACT IMMINENT"};
            int px = w - 160, py = bar + 8;
            g.fill(px - 6, py - 4, w - 6, py + ln.length * 10 + 2, 0x90000000);
            g.fill(px - 6, py - 4, px - 4, py + ln.length * 10 + 2, 0xFFFF8A50);
            for (int i = 0; i < ln.length; i++) g.drawString(font, ln[i], px, py + i * 10, 0xFFFFFFFF, true);
        }
        // 행성 공개 타이틀
        if (t > REVEAL + 6 && t < REVEAL + 44) {
            double a = Math.min(1, Math.min((t - REVEAL - 6) / 8.0, (REVEAL + 44 - t) / 8.0));
            Cinematic.big(g, font, w, h, "KEPLER-442b", 4.2f + (float) (t - REVEAL) * 0.01f, a, 0xFFFFD0B0);
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(w / 2f, h / 2f + 26);
            pose.scale(1.3f, 1.3f);
            g.drawCenteredString(font, "§7거리 1,206 광년 · 질량 2.1×10²⁴ kg · 표면 온도 -40℃", 0, 0, (Math.max(8, (int) (255 * a)) << 24) | 0xFFFFFF);
            pose.popMatrix();
        }
        // 카운트다운
        String[] cd = {"3", "2", "1"};
        for (int i = 0; i < 3; i++) {
            double t0 = HIT - 30 + i * 10;
            if (t >= t0 && t < t0 + 10) Cinematic.big(g, font, w, h, cd[i], 8f - (float) (t - t0) * 0.3f, 1 - (t - t0) / 10.0, 0xFFFF5030);
        }
        Cinematic.sub(g, font, w, h, bar, t, 14, 56, "§6/locate planet §f— 우주 저편의 좌표를 고정한다", 1.6f);
        Cinematic.sub(g, font, w, h, bar, t, RIFT + 4, ASCEND, "§b/fill structure_void §f— 하늘을 지운다", 1.6f);
        Cinematic.sub(g, font, w, h, bar, t, SPACE + 14, 205, "§b/execute in jjk:orbit §f— 광년을 건너뛴다", 1.6f);
        Cinematic.sub(g, font, w, h, bar, t, LOCK + 4, 315, "§b/ride §f— 사슬 열두 줄로 행성을 묶는다", 1.6f);
        Cinematic.sub(g, font, w, h, bar, t, HAUL + 4, 376, "§b/tp §f— 행성을 지구로 끌어온다", 1.6f);
        Cinematic.sub(g, font, w, h, bar, t, ORBIT + 4, DESCENT - 4, "§c대기권 돌입", 2.0f);
        Cinematic.sub(g, font, w, h, bar, t, DESCENT + 30, 560, "§c하늘이 무너진다", 2.0f);
        Cinematic.sub(g, font, w, h, bar, t, HIT + 50, HIT + 130, "§f§lPLANET IMPACT", 3.0f);
        Cinematic.sub(g, font, w, h, bar, t, 812, 842, "§7/kill @e[type=planet] §f— exit 0", 1.6f);
        Cinematic.sub(g, font, w, h, bar, t, EPI0 + 6, EPI1 - 4, "§6대륙을 휩쓰는 충격파 §7— 반경 2,300 km", 1.8f);
        if (t > EPI0 + 10 && t < EPI1 - 4) {   // 피해 보고서
            String[] ln = {"§c§lIMPACT REPORT", "§7energy §f4.2×10³² J", "§7crater §f2,300 km", "§7killed §f2,147,483,647 entities", "§a/tp exit 0"};
            int px = w - 170, py = bar + 8;
            g.fill(px - 6, py - 4, w - 6, py + ln.length * 10 + 2, 0x90000000);
            g.fill(px - 6, py - 4, px - 4, py + ln.length * 10 + 2, 0xFFFF3040);
            for (int i = 0; i < ln.length; i++) g.drawString(font, ln[i], px, py + i * 10, 0xFFFFFFFF, true);
        }
    }
}
