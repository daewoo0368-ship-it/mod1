package com.tubilt.jjk.mech;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 보스 몸: 아이템 디스플레이 6~8개(머리·몸통·팔·다리 + 소품)로 만든 인형 + 관절 애니메이션.
 *  · 모델 = assets/jjk/models/item/boss/<skin>/<부위>.json (플레이어 스킨 배치 그대로, 바깥층 포함)
 *  · 모든 부위가 히트박스 위치·방향을 따라가고, 매 틱 관절 행렬을 보간해서 보냄 → 걷기·휘두르기·시전·피격 동작
 * 좌표: 엔티티 기준, +Z = 정면, -X = 오른쪽, 발밑 = 0, 키 2블록 × S.
 */
final class BossRig {
    static final String[] PART = {"head", "body", "arm_r", "arm_l", "leg_r", "leg_l"};
    static final double[][] JOINT = {{0, 1.5, 0}, {0, 1.5, 0}, {-0.3125, 1.375, 0}, {0.3125, 1.375, 0}, {-0.125, 0.75, 0}, {0.125, 0.75, 0}};
    static final int HEAD = 0, BODY = 1, AR = 2, AL = 3, LR = 4, LL = 5;
    static final int L = 99999999;

    final Bosses.Boss b; final String skin; final double S;
    final Holo.Elem[] parts = new Holo.Elem[6];
    Holo.Elem armR2, armL2;            // 스쿠나: 아래쪽 팔 한 쌍
    Holo.Elem prop; String propName; double propScale = 1; Vec3 propOff = Vec3.ZERO; boolean propOnBody = true;
    Holo.Elem held; String heldId;
    Holo.Elem chair; boolean sitting;
    Holo.Elem mount, lance; boolean riding; double mountSc = 1;
    double thin = 1;   // 마른 체형: 팔·다리 관절을 몸 쪽으로 (모델도 가늘게 만든 스킨용)
    boolean floating, visible = true;

    // 상태
    String pose = "idle"; int poseT = 0, poseLen = 1;
    double walkPhase = 0, walkAmt = 0, lift = 0, liftGoal = 0, tilt = 0, tiltGoal = 0;
    double headYaw = 0, headPitch = 0;
    int flinch = 0, age = 0;
    Vec3 last;
    float yaw = 0;
    Entity look;

    BossRig(Bosses.Boss b, String skin, double scale) {
        this.b = b; this.skin = skin; this.S = scale;
        Vec3 at = b.body.position();
        last = at;
        yaw = b.body.getYRot();
        for (int i = 0; i < 6; i++) {
            parts[i] = Holo.model(b.l, b.tag, at, "jjk:boss/" + skin + "/" + PART[i], Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, i == BODY ? (float) (0.6 * S) : 0f)
                    .attach(() -> b.body.position(), () -> yaw);
        }
    }
    BossRig fourArms() {
        Vec3 at = b.body.position();
        armR2 = Holo.model(b.l, b.tag, at, "jjk:boss/" + skin + "/arm_r", Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, 0f).attach(() -> b.body.position(), () -> yaw);
        armL2 = Holo.model(b.l, b.tag, at, "jjk:boss/" + skin + "/arm_l", Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, 0f).attach(() -> b.body.position(), () -> yaw);
        return this;
    }
    /** 등에 메는/몸에 붙는 소품 (bag·jetpack) 또는 몸 앞에 드는 소품 (laptop: onBody=false). */
    BossRig prop(String name, Vec3 off, double sc, boolean onBody) {
        propName = name; propOff = off; propScale = sc; propOnBody = onBody;
        prop = Holo.model(b.l, b.tag, b.body.position(), "jjk:boss/prop/" + name, Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, 0f)
                .attach(() -> b.body.position(), () -> yaw);
        return this;
    }
    void dropProp() { if (prop != null) { prop.kill(); prop = null; } }
    /** 게이밍 의자 (개발자 1페이지). */
    void sit(boolean on) {
        sitting = on;
        if (on && chair == null)
            chair = Holo.model(b.l, b.tag, b.body.position(), "jjk:boss/prop/chair", Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, (float) (0.8 * S))
                    .attach(() -> b.body.position(), () -> yaw);
        if (!on && chair != null) { chair.kill(); chair = null; }
    }
    /** 오른손에 아이템 (null = 비우기). */
    void hold(String itemId) {
        if (itemId != null && itemId.equals(heldId)) return;
        if (held != null) { held.kill(); held = null; }
        heldId = itemId;
        if (itemId != null)
            held = Holo.held(b.l, b.tag, b.body.position(), itemId, Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2)
                    .attach(() -> b.body.position(), () -> yaw);
    }
    /** 탈것 (말 등): 모델 원점 = 다리 끝에서 14/16 위, 안장 = 원점 위 15.5/16. 탑승 시 리그를 안장 높이로 올림. */
    void ride(String model, double sc) {
        if (mount != null) { mount.kill(); mount = null; }
        riding = model != null; mountSc = sc;
        if (model != null)
            mount = Holo.model(b.l, b.tag, b.body.position(), "jjk:boss/prop/" + model, Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, (float) (0.9 * S))
                    .attach(() -> b.body.position(), () -> yaw);
        liftGoal = riding ? (0.875 + 0.97) * sc - 0.78 : 0;
    }
    /** 오른손 긴 무기 모델 (창): 손잡이 = 모델 z 20, 날끝 = 모델 z -16. */
    void lance(String model) {
        if (lance != null) { lance.kill(); lance = null; }
        if (model != null)
            lance = Holo.model(b.l, b.tag, b.body.position(), "jjk:boss/prop/" + model, Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, false), L, 2, 0f)
                    .attach(() -> b.body.position(), () -> yaw);
    }
    void play(String p, int len) { pose = p; poseT = 0; poseLen = Math.max(1, len); }
    boolean busy() { return !pose.equals("idle") && poseT < poseLen; }
    void hit() { flinch = 6; }
    void hide(boolean h) { visible = !h; }

    static double lerp(double a, double b, double t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }
    static double ease(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    void tick() {
        if (b.body == null) return;
        age++;
        // 방향: 몸 yaw 를 부드럽게
        float target = b.body.getYRot();
        float d = net.minecraft.util.Mth.wrapDegrees(target - yaw);
        yaw += d * 0.35f;
        // 걷기
        Vec3 p = b.body.position();
        double sp = Math.sqrt((p.x - last.x) * (p.x - last.x) + (p.z - last.z) * (p.z - last.z));
        last = p;
        walkAmt = lerp(walkAmt, Math.min(1, sp * 6), 0.3);
        walkPhase += Math.min(0.6, sp * 2.6 / S) + 0.0;
        // 시선
        double hy = 0, hp = 0;
        if (look != null && look.isAlive()) {
            Vec3 e = look.getEyePosition().subtract(p.add(0, 1.62 * S, 0));
            double ty = Math.toDegrees(Math.atan2(-e.x, e.z));
            hy = Math.toRadians(net.minecraft.util.Mth.wrapDegrees(ty - yaw));
            hp = -Math.atan2(e.y, Math.sqrt(e.x * e.x + e.z * e.z));
            hy = Math.max(-1.0, Math.min(1.0, hy)); hp = Math.max(-0.7, Math.min(0.7, hp));
        }
        headYaw = lerp(headYaw, -hy, 0.4);   // 로컬 X 반전 (오른쪽 = -X) 보정
        headPitch = lerp(headPitch, hp, 0.4);
        if (poseT < poseLen) poseT++;
        if (flinch > 0) flinch--;
        lift = lerp(lift, liftGoal, 0.25);
        tilt = lerp(tilt, tiltGoal, 0.2);
        apply();
    }

    /** 자세 → 관절 각도 → 행렬. */
    void apply() {
        double t = age;
        double u = poseT / (double) poseLen;
        boolean on = poseT < poseLen;
        // 기본(서 있기·걷기)
        double sw = Math.sin(walkPhase) * 0.7 * walkAmt;
        double idle = Math.sin(t * 0.08) * 0.05;
        double aRp = -sw * 0.8 + idle, aRr = -0.06 - idle * 0.3, aLp = sw * 0.8 - idle, aLr = 0.06 + idle * 0.3;
        double lRp = sw, lLp = -sw;
        double lean = walkAmt * 0.08, rootLift = Math.abs(Math.sin(walkPhase)) * 0.04 * walkAmt;
        double hYaw = headYaw, hPitch = headPitch;
        if (floating) { lRp = -0.25 + Math.sin(t * 0.07) * 0.08; lLp = -0.1 + Math.sin(t * 0.07 + 1) * 0.08; rootLift += Math.sin(t * 0.06) * 0.06; }
        if (riding) { lRp = 0.55; lLp = 0.55; lean = 0.05 + walkAmt * 0.06; rootLift = Math.abs(Math.sin(walkPhase * 1.6)) * 0.07 * walkAmt; }
        if (sitting) { lRp = 1.45; lLp = 1.45; aRp = 0.55 + idle; aLp = 0.55 - idle; lean = -0.12; }
        tiltGoal = 0;
        if (on) switch (pose) {
            case "swing" -> {   // 오른팔 머리 위로 → 내려찍기
                if (u < 0.45) { double k = ease(u / 0.45); aRp = lerp(aRp, 2.9, k); aRr = lerp(aRr, -0.3, k); lean = lerp(lean, -0.12, k); }
                else { double k = ease((u - 0.45) / 0.2); aRp = lerp(2.9, 0.5, k); aRr = lerp(-0.3, 0.25, k); lean = lerp(-0.12, 0.3, k); }
                hPitch += 0.1;
            }
            case "sweep" -> {   // 양팔 옆으로 크게 휘두르기
                double k = ease(u);
                aRp = 1.4; aLp = 1.4; aRr = lerp(-1.2, 0.6, k); aLr = lerp(1.2, -0.6, k); lean = 0.15;
            }
            case "cast" -> {    // 양팔 앞으로 뻗기
                double k = ease(Math.min(1, u / 0.3)) * (u > 0.85 ? (1 - (u - 0.85) / 0.15) : 1);
                aRp = lerp(aRp, 1.5, k); aLp = lerp(aLp, 1.5, k); aRr = lerp(aRr, 0.18, k); aLr = lerp(aLr, -0.18, k); lean = lerp(lean, 0.1, k);
            }
            case "point" -> {   // 오른팔로 가리킴
                double k = ease(Math.min(1, u / 0.25)) * (u > 0.85 ? (1 - (u - 0.85) / 0.15) : 1);
                aRp = lerp(aRp, 1.6, k); aRr = lerp(aRr, 0.05, k);
            }
            case "raise" -> {   // 양팔 하늘로 (영창·소환)
                double k = ease(Math.min(1, u / 0.3)) * (u > 0.9 ? (1 - (u - 0.9) / 0.1) : 1);
                aRp = lerp(aRp, 2.8, k); aLp = lerp(aLp, 2.8, k); aRr = lerp(aRr, -0.45, k); aLr = lerp(aLr, 0.45, k); hPitch = lerp(hPitch, -0.5, k);
                aRp += Math.sin(t * 0.5) * 0.05 * k; aLp += Math.cos(t * 0.5) * 0.05 * k;
            }
            case "slam" -> {    // 양팔 들었다 내리찍기 + 웅크림
                if (u < 0.55) { double k = ease(u / 0.55); aRp = lerp(aRp, 3.0, k); aLp = lerp(aLp, 3.0, k); lean = lerp(0, -0.2, k); }
                else { double k = ease((u - 0.55) / 0.15); aRp = lerp(3.0, 0.2, k); aLp = lerp(3.0, 0.2, k); lean = lerp(-0.2, 0.45, k); rootLift -= 0.18 * k; }
            }
            case "charge" -> {  // 돌진 자세
                aRp = -0.7; aLp = -0.7; aRr = -0.25; aLr = 0.25; lean = 0.4; lRp = 0.5; lLp = -0.6;
            }
            case "phone" -> {   // 폰 보기
                double k = ease(Math.min(1, u / 0.2));
                aRp = lerp(aRp, 1.2, k); aRr = lerp(aRr, 0.45, k); hPitch = lerp(hPitch, 0.45, k);
            }
            case "type" -> {    // 노트북 타자
                aRp = 1.0 + Math.sin(t * 1.3) * 0.07; aLp = 1.0 + Math.cos(t * 1.5) * 0.07; aRr = 0.2; aLr = -0.2; hPitch = 0.35;
            }
            case "laugh" -> {
                hPitch = -0.35 + Math.sin(t * 0.9) * 0.08; lean = -0.1 + Math.sin(t * 0.9) * 0.04; aRp = 0.3; aLp = 0.3;
            }
            case "guard" -> {   // 팔로 막기
                aRp = 1.7; aLp = 1.7; aRr = 0.7; aLr = -0.7; lean = 0.05;
            }
            case "kneel" -> {
                double k = ease(Math.min(1, u / 0.3));
                lRp = lerp(lRp, 1.4, k); lLp = lerp(lLp, -0.2, k); rootLift -= 0.45 * k; lean = lerp(lean, 0.35, k); aRp = 0.2; aLp = 0.2; hPitch = lerp(hPitch, 0.5, k);
            }
            case "fall" -> {    // 쓰러짐 (뒤로 눕기)
                double k = ease(Math.min(1, u / 0.5));
                tiltGoal = 1.5 * k; aRp = 0.2; aLp = 0.2; aRr = -0.7 * k; aLr = 0.7 * k; hPitch = 0;
            }
            default -> { }
        }
        if (flinch > 0) { lean -= 0.18 * flinch / 6.0; hPitch -= 0.25 * flinch / 6.0; }

        // 행렬 계산
        double[][] rootR = Holo.rotX(-tilt);           // 눕기: 발을 축으로
        double[][] bodyR = Holo.rotX(lean);            // 상체 숙이기: 엉덩이 축
        Vec3 hip = new Vec3(0, 0.75, 0);
        double[][][] R = new double[6][][];
        R[HEAD] = Holo.mul(bodyR, Holo.mul(Holo.rotY(hYaw), Holo.rotX(hPitch)));
        R[BODY] = bodyR;
        R[AR] = Holo.mul(bodyR, Holo.mul(Holo.rotX(-aRp), Holo.rotZ(aRr)));
        R[AL] = Holo.mul(bodyR, Holo.mul(Holo.rotX(-aLp), Holo.rotZ(aLr)));
        R[LR] = Holo.rotX(-lRp);
        R[LL] = Holo.rotX(-lLp);
        Vec3[] P = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            Vec3 j = new Vec3(JOINT[i][0] * thin, JOINT[i][1], JOINT[i][2]);
            if (i <= AL) j = hip.add(Holo.apply(bodyR, j.subtract(hip)));
            P[i] = j;
        }
        double yOff = lift + rootLift + (sitting ? 0.0 : 0);
        double vis = visible ? S : 0.001;
        for (int i = 0; i < 6; i++) push(parts[i], Holo.mul(rootR, R[i]), P[i], yOff, vis);
        if (armR2 != null) {   // 아래 팔: 위 팔을 살짝 늦게 따라 + 옆으로 벌림
            Vec3 j2 = hip.add(Holo.apply(bodyR, new Vec3(-0.34, 1.15, 0.02).subtract(hip)));
            Vec3 k2 = hip.add(Holo.apply(bodyR, new Vec3(0.34, 1.15, 0.02).subtract(hip)));
            push(armR2, Holo.mul(rootR, Holo.mul(bodyR, Holo.mul(Holo.rotX(-(aRp * 0.7 + 0.25 + Math.sin(t * 0.11) * 0.08)), Holo.rotZ(-0.35)))), j2, yOff, vis * 0.92);
            push(armL2, Holo.mul(rootR, Holo.mul(bodyR, Holo.mul(Holo.rotX(-(aLp * 0.7 + 0.25 + Math.cos(t * 0.11) * 0.08)), Holo.rotZ(0.35)))), k2, yOff, vis * 0.92);
        }
        if (prop != null) {
            if (propOnBody) {
                Vec3 pj = hip.add(Holo.apply(bodyR, new Vec3(0, 1.5, 0).add(propOff).subtract(hip)));
                push(prop, Holo.mul(rootR, bodyR), pj, yOff, vis * propScale);
            } else push(prop, Holo.mul(rootR, Holo.rotX(0.25)), propOff, yOff, vis * propScale);
        }
        if (chair != null) push(chair, rootR, new Vec3(0, 0.8, -0.05), 0, S * 1.15);
        if (mount != null) {
            double bob = Math.abs(Math.sin(walkPhase * 1.6)) * 0.07 * walkAmt;
            push(mount, Holo.mul(rootR, Holo.rotX(Math.sin(walkPhase * 1.6) * 0.04 * walkAmt)), new Vec3(0, 0.875 * mountSc + bob, 0), 0, S * mountSc);
        }
        if (lance != null) {   // 창: 주먹을 손잡이로, 팔과 수직 (팔 내리면 앞으로 수평)
            double[][] ra = R[AR];
            Vec3 hand = P[AR].add(Holo.apply(ra, new Vec3(0, -0.6, 0)));
            Vec3 c = hand.add(Holo.apply(ra, new Vec3(0, 0, 0.75 * 0.95)));
            push(lance, Holo.mul(rootR, ra), c, yOff, vis * 0.95);
        }
        if (held != null) {   // 손잡이를 주먹에, 날은 팔과 수직 앞쪽
            double[][] ra = R[AR];
            double[][] ri = Holo.mul(ra, Holo.mul(Holo.rotY(Math.PI / 2), Holo.rotZ(Math.PI / 4)));
            Vec3 hand = P[AR].add(Holo.apply(ra, new Vec3(0, -0.6, 0)));
            Vec3 c = hand.add(Holo.apply(ra, new Vec3(0, 0, 0.53 * 0.9)));
            push(held, Holo.mul(rootR, ri), c, yOff, vis * 0.9);
        }
    }
    void push(Holo.Elem e, double[][] r, Vec3 local, double yOff, double sc) {
        if (e == null || e.dead()) return;
        double[][] rootR = Holo.rotX(-tilt);
        Vec3 pos = Holo.apply(rootR, local).scale(S).add(0, yOff * S, 0);
        e.keyframe(Holo.m(r, sc, sc, sc, pos, false), 0);   // 매 틱 즉시 적용 (클라 보간 끔 → 떨림·커졌다 작아짐·깜빡임 없음)
    }
    void kill() {
        for (Holo.Elem e : parts) if (e != null) e.kill();
        for (Holo.Elem e : new Holo.Elem[]{armR2, armL2, prop, held, chair, mount, lance}) if (e != null) e.kill();
    }
}
