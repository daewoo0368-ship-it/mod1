package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * 개발자의 매우 귀여운 탑승물 — 높이 약 50칸 이족보행 거인봇.
 * 모델(약 20칸)을 렌더러가 SCALE 배 확대. 이 클래스의 길이 값은 모두 실제(확대 후) 칸 단위.
 * 걸음: 두 발이 번갈아 크게 내딛음(발은 땅에 고정), 사람처럼 무릎 앞으로 굽는 2마디 IK + 수평 발.
 * 비행: G 로 켜고 끄며, 탑승자가 보는 방향으로 날아감.
 */
public class DevTitan extends PathfinderMob implements GeoEntity, DevVehicle {
    /** 렌더 배율: 모델(약 20칸) × SCALE = 실제 높이. 2.5 → 약 50칸 */
    public static final float SCALE = 2.5f;
    public static final double S = SCALE / 16.0;               // 모델 px → 실제 칸
    public static final double HIP_Y = 160 * S, HIP_X = 30 * S, L1 = 80 * S, L2 = 70 * S, ANKLE = 10 * S;
    public static final double SHOULDER_Y = 250 * S, SHOULDER_X = 66 * S, ARM = (250 - 114 + 26) * S;   // 어깨→손바닥
    public static final double CHEST_Y = 228 * S, CHEST_Z = 39 * S;

    // 포즈 배열: [허벅지,정강이,발] ×2 (L,R) + [어깨X,팔꿈치X] ×2 + [루트px, 몸통롤, 몸통피치, 머리Y, 머리X]
    public static final int LEG = 0, ARMS = 6, BODY = 10, N = 15;
    public final float[] pose = new float[N], posePrev = new float[N];

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dev_titan.idle");
    static final RawAnimation RECOIL = RawAnimation.begin().thenPlay("animation.dev_titan.recoil");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 동작 모드: 0 평상, 1 아크 충전, 2 아크 발사, 3 소형 아크, 4 화염방사 */
    static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(DevTitan.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DevTitan.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Byte> HANDS = SynchedEntityData.defineId(DevTitan.class, EntityDataSerializers.BYTE);   // 소형 아크 손 (1 왼, 2 오른)

    // 발 상태 (월드)
    private final double[] fx = new double[2], fy = new double[2], fz = new double[2];
    private final double[] sx = new double[2], sy = new double[2], sz = new double[2], tx = new double[2], ty = new double[2], tz = new double[2];
    private final int[] stepT = new int[2], stepDur = new int[2];
    private final double[] lift = new double[2];
    private boolean feetInit, wasAir;
    private double vx, vz, bobS, rollS, pitchS, headS, headXS, armL = 0, armR = 0, elbL = 0, elbR = 0;
    private int still, airTicks, age2;
    public int fallT = 0;

    // 서버 무기 상태
    public boolean flameOn = false;
    public int missileCd, bombCd, smallCd, smallT, arcCd, arcCharge, arcTime, flameFuel;
    public boolean weatherSet = false;

    public DevTitan(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0)
                .add(Attributes.MOVEMENT_SPEED, 0.45)
                .add(Attributes.STEP_HEIGHT, 7.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 10000.0)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 30.0)
                .add(Attributes.ARMOR_TOUGHNESS, 20.0)
                .add(Attributes.CAMERA_DISTANCE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, (byte) 0);
        b.define(FLYING, false);
        b.define(HANDS, (byte) 0);
    }

    public int mode() { return entityData.get(MODE); }
    public boolean flying() { return entityData.get(FLYING); }
    public int hands() { return entityData.get(HANDS); }
    void setFlying(boolean f) { entityData.set(FLYING, f); setNoGravity(f); }
    void setHands(int h) { if (entityData.get(HANDS) != h) entityData.set(HANDS, (byte) h); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<DevTitan>("main", 5, test -> test.setAndContinue(IDLE)).triggerableAnim("recoil", RECOIL));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ───────────── 탑승 / 조종
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isVehicle()) { if (!level().isClientSide()) player.startRiding(this); return InteractionResult.SUCCESS; }
        return super.mobInteract(player, hand);
    }

    @Nullable @Override
    public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof Player p ? p : null; }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        float yaw = getYRot() + (float) wrap(player.getYRot() - getYRot()) * (flying() ? 0.2f : 0.08f);
        setYRot(yaw); yRotO = yaw; setXRot(0); yBodyRot = yaw; yHeadRot = yaw;
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) { return new Vec3(player.xxa * 0.4, 0, player.zza); }

    @Override
    protected float getRiddenSpeed(Player player) {
        float s = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
        return player.isSprinting() ? s * 1.6f : s;
    }

    /** 비행 중: 탑승자가 보는 방향으로 (W/S 앞뒤, A/D 옆), 입력 없으면 공중 정지. */
    @Override
    public void travel(Vec3 input) {
        if (flying() && getControllingPassenger() instanceof Player p) {
            Vec3 look = p.getLookAngle();
            double r = Math.toRadians(p.getYRot());
            Vec3 left = new Vec3(Math.cos(r), 0, Math.sin(r));
            double sp = p.isSprinting() ? 3.2 : 1.6;
            Vec3 want = look.scale(p.zza * sp).add(left.scale(p.xxa * sp * 0.6));
            if (p.zza == 0 && p.xxa == 0) want = new Vec3(0, Math.sin(tickCount * 0.05) * 0.04, 0);
            setDeltaMovement(getDeltaMovement().lerp(want, 0.1));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        super.travel(input);
    }

    @Override public boolean isAffectedByFluids() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)) return false;
        if (source.getEntity() != null && source.getEntity() == getControllingPassenger()) return false;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) Titan.cleanup(this, sl);
        super.remove(reason);
    }

    // ───────────── 틱
    @Override
    public void tick() {
        super.tick();
        updatePose();
        if (!level().isClientSide()) {
            if (isOnFire()) clearFire();
            if (getControllingPassenger() == null && flying()) setFlying(false);
            Titan.tick(this);
            byte md = (byte) (arcCharge > 0 ? 1 : arcTime > 0 ? 2 : smallT > 0 ? 3 : flameOn ? 4 : 0);
            if (md != entityData.get(MODE)) entityData.set(MODE, md);
            trample();
        }
    }

    /** 몸통에 걸리는 블록을 짓밟아 부숨 (#grief 켜짐일 때, 움직이는 중에만). */
    private void trample() {
        if (tickCount % 2 != 0 || !(level() instanceof ServerLevel sl) || !MechManager.griefOn(sl.getServer())) return;
        double sp = Math.hypot(getX() - xo, getZ() - zo) + Math.abs(getY() - yo);
        if (sp < 0.05) return;
        AABB b = getBoundingBox().inflate(1.5, 0, 1.5);
        int y0 = (int) Math.floor(b.minY + (flying() ? 0 : 3)), broke = 0;
        for (int x = (int) Math.floor(b.minX); x <= (int) Math.floor(b.maxX) && broke < 600; x++)
            for (int z = (int) Math.floor(b.minZ); z <= (int) Math.floor(b.maxZ) && broke < 600; z++)
                for (int y = y0; y <= (int) Math.floor(b.maxY); y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState st = sl.getBlockState(bp);
                    if (st.isAir() || st.getDestroySpeed(sl, bp) < 0 || !st.getFluidState().isEmpty()) continue;
                    sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
                    if (++broke % 12 == 0)
                        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, st),
                                x + 0.5, y + 0.5, z + 0.5, 6, 0.5, 0.5, 0.5, 0.2);
                }
        if (broke > 0 && tickCount % 6 == 0)
            sl.playSound(null, getX(), getY() + 10, getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 2f, 0.5f);
    }

    private void updatePose() {
        System.arraycopy(pose, 0, posePrev, 0, N);
        age2++;
        double px = getX(), py = getY(), pz = getZ();
        vx += ((px - xo) - vx) * 0.3;
        vz += ((pz - zo) - vz) * 0.3;
        double speed = Math.hypot(vx, vz);
        still = speed < 0.02 ? still + 1 : 0;
        double yaw = Math.toRadians(yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);

        airTicks = (!onGround() || flying()) ? airTicks + 1 : 0;
        boolean inAir = airTicks > 5;
        boolean snap = !feetInit || (!inAir && wasAir);
        wasAir = inAir;
        for (int i = 0; i < 2 && feetInit; i++)
            if (Math.hypot(fx[i] - px, fz[i] - pz) > 60 || Math.abs(fy[i] - py) > 60) snap = true;

        int dur = (int) Math.max(10, Math.min(18, Math.round(18 - speed * 12)));
        double stride = Math.min(11, 2.5 + speed * 14), lead = dur * 0.55;
        double bobT = 0;
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? -1 : 1;    // 0 = 왼(-x 모델) , 1 = 오른
            // 모델 +x = 오른쪽
            double restX = px + rightX * HIP_X * side, restZ = pz + rightZ * HIP_X * side;
            if (inAir) {   // 공중: 다리를 살짝 뒤로 늘어뜨림
                double dx = restX - fwdX * 3, dz = restZ - fwdZ * 3;
                fx[i] += (dx - fx[i]) * 0.2; fz[i] += (dz - fz[i]) * 0.2; fy[i] += (py + 2 - fy[i]) * 0.2;
                stepT[i] = 0;
                continue;
            }
            if (snap) { fx[i] = restX; fz[i] = restZ; fy[i] = groundAt(restX, py + 30, restZ); stepT[i] = 0; continue; }
            if (stepT[i] > 0) {
                stepT[i]++;
                double u = Math.min(1, stepT[i] / (double) stepDur[i]), e = u * u * (3 - 2 * u);
                ty[i] = groundAt(tx[i], py + 30, tz[i]);
                fx[i] = sx[i] + (tx[i] - sx[i]) * e;
                fz[i] = sz[i] + (tz[i] - sz[i]) * e;
                fy[i] = sy[i] + (ty[i] - sy[i]) * e + Math.sin(Math.PI * u) * lift[i];
                bobT += Math.sin(Math.PI * u) * 1.2;
                if (u >= 1) { stepT[i] = 0; land(i); }
                continue;
            }
            double off = Math.hypot(fx[i] - restX, fz[i] - restZ);
            boolean other = stepT[1 - i] > 0;
            if (off > stride * 1.6 || (!other && (off > stride || (still > 12 && off > 1.5)))) {
                sx[i] = fx[i]; sy[i] = fy[i]; sz[i] = fz[i];
                tx[i] = restX + vx * lead; tz[i] = restZ + vz * lead; ty[i] = groundAt(tx[i], py + 30, tz[i]);
                double dist = Math.hypot(tx[i] - sx[i], tz[i] - sz[i]);
                lift[i] = Math.min(5, 1.3 + dist * 0.3) + Math.max(0, ty[i] - sy[i]) * 0.6;
                stepDur[i] = dur;
                stepT[i] = 1;
            }
        }
        feetInit = true;

        int md = mode();
        // 몸: 걸음에 맞춰 좌우로 흔들고 위아래로 출렁, 발사 시 반동
        double rollT = 0;
        for (int i = 0; i < 2; i++) if (stepT[i] > 0) rollT += (i == 0 ? 1 : -1) * 0.05;
        double bob = -bobT * 6 - 6 + Math.sin(age2 * 0.05) * 1.2;      // px (모델 단위)
        double pitchT = flying() ? 0.25 * Math.min(1, speed * 2) : 0;
        if (md == 1) { bob -= 10; pitchT -= 0.12; rollT += (random.nextDouble() - 0.5) * 0.02; }
        if (md == 2) { bob -= 8 + random.nextDouble() * 3; pitchT -= 0.08 + random.nextDouble() * 0.03; }
        bobS += (bob - bobS) * 0.2; rollS += (rollT - rollS) * 0.1; pitchS += (pitchT - pitchS) * 0.12;

        // 다리 IK (엉덩이 높이 = HIP_Y + 몸 출렁임)
        double hipY = py + HIP_Y + bobS * S;
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? -1 : 1;
            double hx = px + rightX * HIP_X * side, hz = pz + rightZ * HIP_X * side;
            double f = (fx[i] - hx) * fwdX + (fz[i] - hz) * fwdZ;
            double h = (fy[i] + ANKLE) - hipY;
            double dist = Math.min(L1 + L2 - 0.1, Math.max(Math.abs(L1 - L2) + 0.1, Math.hypot(f, h)));
            double phi = Math.atan2(f, -h);
            double a = Math.acos(Math.max(-1, Math.min(1, (L1 * L1 + dist * dist - L2 * L2) / (2 * L1 * dist))));
            double t1 = phi + a;                                  // 무릎 앞
            double kx = L1 * Math.sin(t1), ky = -L1 * Math.cos(t1);
            double t2 = Math.atan2(f - kx, -(h - ky));
            pose[LEG + i * 3] = (float) t1;
            pose[LEG + i * 3 + 1] = (float) (t2 - t1);
            pose[LEG + i * 3 + 2] = (float) -t2;
        }

        // 팔: 걷기 흔들림 / 조준 자세 (소형 아크 = 해당 손, 화염 = 오른팔, 아크 충전 = 양팔 뒤로)
        double aimP = 0, aimYaw = 0;
        if (getControllingPassenger() != null) {
            aimP = -Math.toRadians(getControllingPassenger().getXRot());
            aimYaw = Math.toRadians(wrap(getControllingPassenger().getYRot() - yBodyRot));
        }
        double swing = Math.sin(age2 * 0.12) * Math.min(0.35, speed * 1.2);
        double aL = -swing * (flying() ? 0 : 1), aR = swing * (flying() ? 0 : 1), eL = 0.25, eR = 0.25;
        if (flying()) { aL = -0.5; aR = -0.5; eL = 0.4; eR = 0.4; }
        int hands = hands();
        if (md == 3 || hands != 0) {
            if ((hands & 1) != 0) { aL = Math.PI / 2 + aimP; eL = 0; }
            if ((hands & 2) != 0) { aR = Math.PI / 2 + aimP; eR = 0; }
        }
        if (md == 4) { aR = Math.PI / 2 + aimP; eR = 0.05; }
        if (md == 1 || md == 2) { aL = -0.7; aR = -0.7; eL = 0.6; eR = 0.6; }
        armL += (aL - armL) * 0.25; armR += (aR - armR) * 0.25; elbL += (eL - elbL) * 0.25; elbR += (eR - elbR) * 0.25;
        pose[ARMS] = (float) armL; pose[ARMS + 1] = (float) elbL; pose[ARMS + 2] = (float) armR; pose[ARMS + 3] = (float) elbR;

        headS += (Math.max(-0.7, Math.min(0.7, aimYaw)) - headS) * 0.2;
        headXS += (Math.max(-0.4, Math.min(0.4, aimP)) - headXS) * 0.2;
        pose[BODY] = (float) bobS; pose[BODY + 1] = (float) rollS; pose[BODY + 2] = (float) pitchS;
        pose[BODY + 3] = (float) -headS; pose[BODY + 4] = (float) headXS;
    }

    /** 발이 땅을 디딜 때: 서버 = 발밑 짓밟기 + 땅울림 / 클라 = 먼지·쿵 소리. */
    private void land(int i) {
        if (level() instanceof ServerLevel sl) { Titan.stomp(this, sl, fx[i], fy[i], fz[i]); return; }
        for (int k = 0; k < 24; k++)
            level().addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, fx[i] + (random.nextDouble() - 0.5) * 5, fy[i] + 0.3,
                    fz[i] + (random.nextDouble() - 0.5) * 5, (random.nextDouble() - 0.5) * 0.2, 0.03, (random.nextDouble() - 0.5) * 0.2);
        level().playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.5f, 0.4f, false);
        level().playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.3f, false);
    }

    private double groundAt(double x, double fromY, double z) {
        Vec3 a = new Vec3(x, fromY, z), b = new Vec3(x, fromY - 80, z);
        BlockHitResult hit = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? getY() : hit.getLocation().y;
    }

    // ───────────── 무기 위치 (실제 좌표)
    Vec3 fwd() { double r = Math.toRadians(yBodyRot); return new Vec3(-Math.sin(r), 0, Math.cos(r)); }
    Vec3 right() { double r = Math.toRadians(yBodyRot); return new Vec3(-Math.cos(r), 0, -Math.sin(r)); }
    Vec3 chest() { return position().add(0, CHEST_Y + bobS * S, 0).add(fwd().scale(CHEST_Z)); }
    /** 손바닥 (팔을 조준 방향으로 든 상태). side -1 왼 / +1 오른 */
    Vec3 palm(int side, Vec3 aim) {
        Vec3 shoulder = position().add(0, SHOULDER_Y + bobS * S, 0).add(right().scale(SHOULDER_X * side));
        return shoulder.add(aim.scale(ARM));
    }
    Vec3 nozzle(Vec3 aim) { return palm(1, aim).subtract(aim.scale(10 * S)).add(0, -2.0, 0); }

    static double wrap(double d) { d %= 360; if (d >= 180) d -= 360; if (d < -180) d += 360; return d; }
}
