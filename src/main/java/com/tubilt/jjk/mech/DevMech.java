package com.tubilt.jjk.mech;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 개발자의 귀여운 탑승물 — 다리 8개의 거대 거미형 탈것 (GeckoLib).
 * 서버: 탑승·조종·벽 타기. 클라이언트: 다리 IK(지형에 발을 붙임) 계산 → 모델이 뼈에 적용.
 */
public class DevMech extends PathfinderMob implements GeoEntity, DevVehicle {
    public static final int LEGS = 8;
    // 블록 단위 치수 (모델과 맞춤)
    public static final float HIP_R = 2.1f, HIP_Y = 3.4f, REST_R = 8.2f;
    /** 다리 마디 길이: 기절(coxa) · 대퇴(femur) · 경절(tibia) · 부절(tarsus) — 모델과 동일 */
    public static final float LC = 1.2f, LF = 4.4f, LT = 4.0f, LS = 2.0f;
    /** 다리당 값 수: [뿌리 Y, 기절, 대퇴, 경절, 부절] */
    public static final int PER = 5;

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dev_mech.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dev_mech.walk");
    static final RawAnimation FIRE = RawAnimation.begin().thenPlay("animation.dev_mech.fire");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 다리별 [뿌리 Y회전, 허벅지 X회전, 정강이 X회전] + 몸통 [피치, 롤, 높이(px)]. 클라이언트 전용. */
    public static final int BODY = LEGS * PER;
    public final float[] legs = new float[LEGS * PER + 6], legsPrev = new float[LEGS * PER + 6];

    // ── 발 디딤 상태 (월드 좌표). 발은 땅에 고정 → 몸이 멀어지면 한 발씩 들어 옮김
    private final double[] fx = new double[LEGS], fy = new double[LEGS], fz = new double[LEGS];
    private final double[] sx = new double[LEGS], sy = new double[LEGS], sz = new double[LEGS];
    private final double[] tx = new double[LEGS], ty = new double[LEGS], tz = new double[LEGS];
    private final int[] stepT = new int[LEGS], stepDur = new int[LEGS];
    private final double[] stepLift = new double[LEGS];
    private boolean feetInit = false;
    private double vx = 0, vz = 0, pitchS = 0, rollS = 0, bobS = 0, crouchS = 0, restExS = 0;
    private int lastMode = 0;
    private double gunPitchS = 0;
    private int still = 0, clientAge = 0, airTicks = 0;
    private boolean wasAir = false;
    public int missileCd = 0, laserCd = 0, laserCharge = 0, laserTime = 0;
    public int slamCd = 0, slamState = 0, slamAir = 0, slamTimeout = 0, shockT = 0;
    public Vec3 shockCenter = Vec3.ZERO;
    public boolean gunTrigger = false;
    public int gunSpin = 0, gunHeat = 0, gunOverheat = 0;

    /** 동작 모드 (모든 클라이언트에 동기화): 0 평상, 1 레이저 충전, 2 레이저 발사, 3 도약, 4 착지 충격 */
    static final net.minecraft.network.syncher.EntityDataAccessor<Byte> MODE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DevMech.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** 미니건: 총열 회전(0~20) / 열(0~100, 과열 시 +128) */
    static final net.minecraft.network.syncher.EntityDataAccessor<Byte> GUN_SPIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DevMech.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    static final net.minecraft.network.syncher.EntityDataAccessor<Integer> GUN_HEAT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DevMech.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) 0);
        builder.define(GUN_SPIN, (byte) 0);
        builder.define(GUN_HEAT, 0);
    }

    public int mode() { return entityData.get(MODE); }

    /** 클라이언트 HUD 용: 열 0~100 */
    public int gunHeatPct() { return entityData.get(GUN_HEAT) & 127; }

    public boolean gunOverheated() { return (entityData.get(GUN_HEAT) & 128) != 0; }

    public DevMech(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (int i = 0; i < LEGS; i++) { legs[i * PER] = legsPrev[i * PER] = (float) -Math.toRadians(22.5 + 45 * i); }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.STEP_HEIGHT, 2.6)
                .add(Attributes.SAFE_FALL_DISTANCE, 1000.0)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.CAMERA_DISTANCE, 18.0);   // 탑승자 3인칭 카메라를 멀리
    }

    // ───────────── GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DevMech>("main", 5,
                test -> test.setAndContinue(test.isMoving() ? WALK : IDLE))
                .triggerableAnim("fire", FIRE));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ───────────── 탑승 / 조종
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isVehicle() || getControllingPassenger() == null) {
            if (!level().isClientSide()) player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof Player p ? p : null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        float target = player.getYRot();
        float yaw = getYRot() + (float) Mth_wrap(target - getYRot()) * 0.25f;
        setYRot(yaw);
        yRotO = yaw;
        setXRot(0);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(player.xxa * 0.7, 0, player.zza);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float s = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
        return player.isSprinting() ? s * 1.7f : s;
    }

    /** 물·용암 속에서도 땅에서처럼 걷는다 (물 저항·물살 무시, 강바닥을 그대로 걸어감). */
    @Override
    public boolean isAffectedByFluids() { return false; }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean canBreatheUnderwater() { return true; }

    /** 벽에 부딪히면 거미처럼 기어오른다. */
    @Override
    public boolean onClimbable() { return horizontalCollision; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)) return false;
        if (source.getEntity() != null && source.getEntity() == getControllingPassenger()) return false;
        return super.hurtServer(level, source, amount);
    }

    // ───────────── 틱
    @Override
    public void tick() {
        super.tick();
        if (missileCd > 0) missileCd--;
        updateLegs();   // 서버도 같은 걸음 계산 → 실제 발 착지점으로 밟기 판정
        if (!level().isClientSide()) {
            if (isOnFire()) clearFire();
            if (laserCd > 0) laserCd--;
            if (slamCd > 0) slamCd--;
            Laser.tick(this);
            Slam.tick(this);
            Minigun.tick(this);
            if (entityData.get(GUN_SPIN) != gunSpin) entityData.set(GUN_SPIN, (byte) gunSpin);
            int heatSync = gunHeat * 100 / Minigun.MAX_HEAT | (gunOverheat > 0 ? 128 : 0);
            if (entityData.get(GUN_HEAT) != heatSync) entityData.set(GUN_HEAT, heatSync);
            byte md = (byte) (laserCharge > 0 ? 1 : laserTime > 0 ? 2 : slamState == 1 ? 3 : slamState == 2 ? 4 : 0);
            if (md != entityData.get(MODE)) entityData.set(MODE, md);
        }
    }

    /**
     * 클라이언트: 절차적 걸음.
     *  - 발은 월드에 박혀 있고, 이상 위치에서 보폭 이상 멀어지면 들어서 "앞으로 예측한 위치"에 내려놓음
     *  - 대각 4발씩 두 무리(테트라포드)가 번갈아 걸음, 무리끼리 동시에 들지 않음
     *  - 발 궤적: 부드러운 가감속 + 포물선 들기, 착지 때 먼지·발소리
     *  - 몸통: 발 높이로 기울기(피치·롤)와 높이를 맞추고 걸음에 따라 살짝 출렁임
     *  - 다리: 실제 발 위치로 2관절 IK
     */
    private void updateLegs() {
        System.arraycopy(legs, 0, legsPrev, 0, legs.length);
        clientAge++;
        double px = getX(), py = getY(), pz = getZ();
        vx += ((px - xo) - vx) * 0.35;
        vz += ((pz - zo) - vz) * 0.35;
        double speed = Math.sqrt(vx * vx + vz * vz);
        still = speed < 0.012 ? still + 1 : 0;

        double yaw = Math.toRadians(yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);

        // ── 스킬 자세: 레이저 = 다리를 넓게 벌려 버티고 몸을 낮춤 / 착지 = 쾅 주저앉았다 일어남
        int md = mode();
        double aimPitch = 0;
        if (getControllingPassenger() != null)
            aimPitch = Math.max(-0.55, Math.min(0.55, -Math.toRadians(getControllingPassenger().getXRot())));
        double restExT = (md == 1 || md == 2) ? 1.9 : 0, crouchT = md == 1 ? 1.0 : md == 2 ? 0.7 : 0;
        if (md == 4 && lastMode != 4) crouchS = 2.0;           // 착지 순간 크게 주저앉음
        lastMode = md;
        restExS += (restExT - restExS) * 0.12;
        crouchS += (crouchT - crouchS) * (md == 4 ? 0.12 : 0.18);
        double restR = REST_R + restExS;

        int dur = (int) Math.max(4, Math.min(9, Math.round(9 - speed * 12)));
        double stride = Math.min(4.2, 1.5 + speed * 7.0);
        double lead = dur * 0.85;

        // 공중(도약 등): 걸음 대신 다리를 몸 아래로 모음, 착지하면 다시 땅에 디딤
        airTicks = (!onGround() && !onClimbable()) ? airTicks + 1 : 0;
        boolean inAir = airTicks > 3;     // 계단 내려가기 같은 잠깐의 공중은 무시
        if (inAir) {
            for (int i = 0; i < LEGS; i++) {
                double ang = yaw + Math.toRadians(22.5 + 45 * i);
                double r = REST_R - 1.6, tuckY = py - 1.2 - Math.min(1.5, (airTicks - 3) * 0.1);
                fx[i] += (px - Math.sin(ang) * r - fx[i]) * 0.35;
                fz[i] += (pz + Math.cos(ang) * r - fz[i]) * 0.35;
                fy[i] += (tuckY - fy[i]) * 0.35;
                stepT[i] = 0;
            }
        }
        boolean snapAll = !feetInit || (!inAir && wasAir);
        wasAir = inAir;
        if (feetInit) {   // 순간이동 등으로 너무 멀어지면 전부 재배치
            for (int i = 0; i < LEGS; i++) {
                double dx = fx[i] - px, dz = fz[i] - pz;
                if (dx * dx + dz * dz > 20 * 20 || Math.abs(fy[i] - py) > 14) { snapAll = true; break; }
            }
        }

        // 무리별로 지금 발을 든 다리 수
        int[] airborne = new int[2];
        for (int i = 0; i < LEGS; i++) if (stepT[i] > 0) airborne[group(i)]++;

        for (int i = 0; i < LEGS; i++) {
            double base = Math.toRadians(22.5 + 45 * i);
            double ang = yaw + base;
            double restX = px - Math.sin(ang) * restR, restZ = pz + Math.cos(ang) * restR;
            if (inAir) continue;
            if (snapAll) {
                fx[i] = restX; fz[i] = restZ; fy[i] = groundAt(restX, py + 6, restZ);
                stepT[i] = 0;
                continue;
            }
            if (stepT[i] > 0) {    // 발 옮기는 중
                stepT[i]++;
                double u = Math.min(1.0, stepT[i] / (double) stepDur[i]);
                double e = u * u * (3 - 2 * u);                       // smoothstep
                ty[i] = groundAt(tx[i], py + 6, tz[i]);               // 착지점 지형이 바뀌면 따라감
                fx[i] = sx[i] + (tx[i] - sx[i]) * e;
                fz[i] = sz[i] + (tz[i] - sz[i]) * e;
                fy[i] = sy[i] + (ty[i] - sy[i]) * e + Math.sin(Math.PI * u) * stepLift[i];
                if (u >= 1.0) { stepT[i] = 0; land(i); }
                continue;
            }
            double ox = fx[i] - restX, oz = fz[i] - restZ;
            double off = Math.sqrt(ox * ox + oz * oz);
            boolean overdue = off > stride * 1.7 || Math.abs(fy[i] - (py)) > 7;
            boolean wants = off > stride || (still > 8 && off > 0.45);
            int g = group(i);
            boolean free = airborne[1 - g] == 0 && airborne[g] < 4;
            if (overdue || (wants && free)) {
                // 몸의 진행 방향으로 앞질러 디딤 (보폭의 절반 정도 앞)
                double ax = restX + vx * lead, az = restZ + vz * lead;
                sx[i] = fx[i]; sy[i] = fy[i]; sz[i] = fz[i];
                tx[i] = ax; tz[i] = az; ty[i] = groundAt(ax, py + 6, az);
                double dist = Math.sqrt((tx[i] - sx[i]) * (tx[i] - sx[i]) + (tz[i] - sz[i]) * (tz[i] - sz[i]));
                stepLift[i] = Math.min(2.4, 0.7 + dist * 0.35) + Math.max(0, ty[i] - sy[i]) * 0.5;
                stepDur[i] = dur + (i % 2);                            // 살짝 어긋나게 → 기계적인 느낌 줄임
                stepT[i] = 1;
                airborne[g]++;
            }
        }
        feetInit = true;

        // ── 몸통: 발 높이 평면에 맞춰 기울이고 높이 조절
        double sff = 0, sfh = 0, srr = 0, srh = 0, hSum = 0;
        for (int i = 0; i < LEGS; i++) {
            double rx = fx[i] - px, rz = fz[i] - pz, h = (stepT[i] > 0 ? ty[i] : fy[i]) - py;
            double f = rx * fwdX + rz * fwdZ, r = rx * rightX + rz * rightZ;
            sff += f * f; sfh += f * h; srr += r * r; srh += r * h; hSum += h;
        }
        double pitch = Math.atan(sfh / Math.max(1e-3, sff)), roll = Math.atan(srh / Math.max(1e-3, srr));
        double lim = Math.toRadians(16);
        pitch = Math.max(-lim, Math.min(lim, pitch));
        roll = Math.max(-lim, Math.min(lim, roll));
        int air = airborne[0] + airborne[1];
        double bob = Math.max(-1.2, Math.min(1.2, hSum / LEGS * 0.5)) * 16      // 지형 높이 반영
                + Math.sin(clientAge * 0.08) * 0.5                            // 숨쉬기
                + (air > 0 ? 0.9 : -0.5) * Math.min(1, speed * 6);            // 발 들 때 살짝 솟음
        if (md == 1) {          // 충전: 조준 방향으로 고개 + 점점 심해지는 떨림
            pitch += aimPitch * 0.6 + 0.06;
            double tr = 0.012 + 0.02 * Math.min(1, clientAge % 1000 / 30.0);
            pitch += (random.nextDouble() - 0.5) * tr * 2;
            roll += (random.nextDouble() - 0.5) * tr * 2;
            bob += (random.nextDouble() - 0.5) * 1.2;
        } else if (md == 2) {   // 발사: 조준 추적 + 반동 진동
            pitch += aimPitch * 0.85;
            pitch += (random.nextDouble() - 0.5) * 0.07;
            roll += (random.nextDouble() - 0.5) * 0.05;
            bob += (random.nextDouble() - 0.5) * 2.6 - 1.0;
        }
        pitchS += (pitch - pitchS) * (md == 1 || md == 2 ? 0.35 : 0.15);
        rollS += (roll - rollS) * (md == 1 || md == 2 ? 0.35 : 0.15);
        bobS += (bob - bobS) * 0.25;
        legs[BODY] = (float) pitchS;
        legs[BODY + 1] = (float) rollS;
        legs[BODY + 2] = (float) bobS;
        legs[BODY + 3] = (float) (-crouchS * 16);   // 루트(몸+엉덩이)를 낮춤 → 무릎이 더 솟음
        // 미니건: 조준 상하(몸통 기울기 빼고) + 총열 회전 (6중 대칭이라 60°마다 되감음)
        gunPitchS += (Math.max(-0.6, Math.min(0.45, aimPitch)) - pitchS - gunPitchS) * 0.3;
        legs[BODY + 4] = (float) gunPitchS;
        float spin = entityData.get(GUN_SPIN);
        legs[BODY + 5] += spin / Minigun.MAX_SPIN * 0.95f;
        float seg = (float) (Math.PI / 3);
        if (legs[BODY + 5] > seg * 30) { legs[BODY + 5] -= seg * 30; legsPrev[BODY + 5] -= seg * 30; }

        // ── 다리 IK (4마디): 실제 발 위치 → 뿌리 Y회전 + 기절/대퇴/경절/부절 각
        //  기절: 몸에서 비스듬히 위로(발 들면 더 들림) · 부절: 땅을 찍듯 거의 수직(들면 안으로 오므림)
        //  대퇴+경절: 기절 끝 → 부절 시작점까지 2관절 IK, 무릎은 항상 위로 솟음
        for (int i = 0; i < LEGS; i++) {
            double base = 22.5 + 45 * i;
            double rx = fx[i] - px, rz = fz[i] - pz;
            double worldAng = Math.toDegrees(Math.atan2(-rx, rz));
            double local = base + Mth_wrap(worldAng - yBodyRot - base);
            double reach = Math.sqrt(rx * rx + rz * rz);
            double u = stepT[i] > 0 ? Math.min(1.0, stepT[i] / (double) stepDur[i]) : 0;
            double lift = Math.sin(Math.PI * u);

            double c0 = Math.toRadians(18 + 12 * lift);                    // 기절 절대각
            double tau = Math.toRadians(-74 + 32 * lift);                  // 부절 절대각 (아래쪽)
            double k0x = HIP_R + LC * Math.cos(c0), k0y = HIP_Y - crouchS + LC * Math.sin(c0);
            double footD = reach, footH = fy[i] - py;
            double bx = footD - LS * Math.cos(tau), by = footH - LS * Math.sin(tau);   // 부절 시작점
            double d = bx - k0x, h = by - k0y;
            if (d < 0.4) d = 0.4;
            double dist = Math.max(Math.abs(LF - LT) + 0.05, Math.min(LF + LT - 0.05, Math.sqrt(d * d + h * h)));
            double phi = Math.atan2(h, d);
            double c = (LF * LF + dist * dist - LT * LT) / (2 * LF * dist);
            double alpha = phi + Math.acos(Math.max(-1, Math.min(1, c)));  // 대퇴 절대각 (무릎 위)
            double kx = LF * Math.cos(alpha), ky = LF * Math.sin(alpha);
            double gamma = Math.atan2(h - ky, d - kx);                     // 경절 절대각

            int o = i * PER;
            legs[o] = (float) -Math.toRadians(local);
            legs[o + 1] = (float) c0;
            legs[o + 2] = (float) (alpha - c0);
            legs[o + 3] = (float) (gamma - alpha);
            legs[o + 4] = (float) (tau - gamma);
        }
    }

    /** 대각 테트라포드 무리: 한쪽 0·2번 + 반대쪽 1·3번. */
    private static int group(int i) { return (i % 2) ^ (i >= 4 ? 1 : 0); }

    /** 착지: 클라이언트는 먼지·발소리, 서버는 발밑 몹을 밟아 죽임. */
    private void land(int i) {
        var lvl = level();
        if (lvl instanceof net.minecraft.server.level.ServerLevel sl) { stomp(sl, fx[i], fy[i], fz[i]); return; }
        for (int k = 0; k < 4; k++)
            lvl.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, fx[i] + (random.nextDouble() - 0.5) * 0.6, fy[i] + 0.1,
                    fz[i] + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        lvl.playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP, net.minecraft.sounds.SoundSource.NEUTRAL,
                0.35f, 0.55f + random.nextFloat() * 0.2f, false);
    }

    /** 발이 내려찍힌 자리의 몹(플레이어 제외)을 즉사. */
    private void stomp(net.minecraft.server.level.ServerLevel sl, double x, double y, double z) {
        AABB box = new AABB(x - 1.7, y - 1.0, z - 1.7, x + 1.7, y + 2.6, z + 1.7);
        var victims = sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != this
                && !(e instanceof Player) && e.getVehicle() != this && !e.isInvulnerable());
        if (victims.isEmpty()) return;
        Entity rider = getControllingPassenger();
        var src = rider instanceof Player p ? sl.damageSources().playerAttack(p) : sl.damageSources().mobAttack(this);
        for (LivingEntity v : victims) {
            v.invulnerableTime = 0;
            v.hurtServer(sl, src, Math.max(1000f, v.getMaxHealth() * 2));
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, v.getX(), v.getY() + 0.3, v.getZ(), 20, 0.4, 0.2, 0.4, 0.3);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, v.getX(), v.getY() + 0.2, v.getZ(), 12, 0.5, 0.1, 0.5, 0.05);
        }
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, x, y + 0.3, z, 2, 0.4, 0.1, 0.4, 0);
        sl.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 0.5f);
        sl.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.SLIME_SQUISH, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    private double groundAt(double x, double fromY, double z) {
        Vec3 a = new Vec3(x, fromY, z), b = new Vec3(x, fromY - 18, z);
        BlockHitResult hit = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? getY() : hit.getLocation().y;
    }

    /** 미사일 포드 8개 (월드 좌표). */
    public Vec3[] pods() {
        Vec3[] r = new Vec3[8];
        double ry = Math.toRadians(getYRot());
        for (int i = 0; i < 8; i++) {
            double lx = (i < 4 ? 1.75 : -1.75) + ((i % 2) - 0.5) * 0.35, lz = 0.6, ly = 4.75 + ((i % 4) / 2 - 0.5) * 0.3;
            double wx = lx * Math.cos(ry) - lz * Math.sin(ry), wz = lx * Math.sin(ry) + lz * Math.cos(ry);
            r[i] = new Vec3(getX() + wx, getY() + ly, getZ() + wz);
        }
        return r;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && (laserCharge > 0 || laserTime > 0)) Laser.stop(this);
        super.remove(reason);
    }

    private static double Mth_wrap(double d) { d %= 360; if (d >= 180) d -= 360; if (d < -180) d += 360; return d; }
}
