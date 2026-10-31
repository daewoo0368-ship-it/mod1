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
 * 개발자의 기계 — 지금까지의 모든 기계를 응축한 합체 기계 (높이 약 30칸, 다리 폭 약 50칸).
 * 하체: 거미 다리 8개(4마디, 디딤 걸음) / 상체: 탑승자 시선을 따라 도는 몸통(왼팔 개틀링·오른팔 화염+손바닥·어깨 주포·미사일 포드·등 제트팩·박격포).
 * 비행 형태(G): 다리를 접고 보는 방향으로 날아감.
 */
public class DevMachine extends PathfinderMob implements GeoEntity, DevVehicle {
    public static final float SCALE = 2.5f;
    public static final double S = SCALE / 16.0;
    public static final int LEGS = 8, PER = 5, BODY = LEGS * PER, N = BODY + 16;
    public static final double HIP_R = 40 * S, HIP_Y = 70 * S, LC = 19.2 * S, LF = 70.4 * S, LT = 64 * S, LS = 32 * S, REST_R = 24;
    public static final double TORSO_Y = 97 * S;

    public final float[] pose = new float[N], posePrev = new float[N];

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dev_machine.idle");
    static final RawAnimation RECOIL = RawAnimation.begin().thenPlay("animation.dev_machine.recoil");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 0 평상, 1 궁극 충전, 2 궁극 발사, 3 강타 준비, 4 강타 착지 */
    static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Byte> SPIN = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Byte> AIM = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.BYTE);   // 1 왼팔, 2 오른팔
    static final EntityDataAccessor<Byte> WEAPON = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Integer> HEAT = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.INT);
    /** 방어막 남은 틱 (클라이언트 연출용) */
    static final EntityDataAccessor<Integer> SHIELD = SynchedEntityData.defineId(DevMachine.class, EntityDataSerializers.INT);

    private final double[] fx = new double[LEGS], fy = new double[LEGS], fz = new double[LEGS];
    private final double[] sx = new double[LEGS], sy = new double[LEGS], sz = new double[LEGS], tx = new double[LEGS], ty = new double[LEGS], tz = new double[LEGS];
    private final int[] stepT = new int[LEGS], stepDur = new int[LEGS];
    private final double[] lift = new double[LEGS];
    private boolean feetInit, wasAir;
    private double vx, vz, pitchS, rollS, bobS, crouchS, torsoS, armLS, foreLS, armRS, foreRS, cannonS, headS, wingS, haloV, coreS;
    private int still, airTicks, age2, lastMode;

    // 서버 무기 상태 (Machine 이 관리)
    public final int[] cd = new int[Machine.WEAPONS.length];
    public boolean trigger = false, autoTurret = false;
    public int ultCd = 0, ultT = 0, slamT = 0, gunHeat = 0, overheat = 0, spin = 0, flameT = 0, arcT = 0, laserT = 0, fallT = 0, bladeT = 0, shieldT = 0, orbitalT = 0;
    public net.minecraft.world.phys.Vec3 orbitalAt;
    public boolean weatherSet = false;

    public DevMachine(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (int i = 0; i < LEGS; i++) pose[i * PER] = posePrev[i * PER] = (float) -Math.toRadians(22.5 + 45 * i);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0).add(Attributes.MOVEMENT_SPEED, 0.42).add(Attributes.STEP_HEIGHT, 6.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 10000.0).add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 30.0).add(Attributes.ARMOR_TOUGHNESS, 20.0).add(Attributes.CAMERA_DISTANCE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, (byte) 0); b.define(FLYING, false); b.define(SPIN, (byte) 0); b.define(AIM, (byte) 0);
        b.define(WEAPON, (byte) 0); b.define(HEAT, 0); b.define(SHIELD, 0);
    }

    public int mode() { return entityData.get(MODE); }
    public boolean flying() { return entityData.get(FLYING); }
    public int weapon() { return entityData.get(WEAPON); }
    public int shieldTicks() { return entityData.get(SHIELD); }
    void setShield(int t) { if (entityData.get(SHIELD) != t) entityData.set(SHIELD, t); }
    public int heatPct() { return entityData.get(HEAT) & 127; }
    public boolean overheated() { return (entityData.get(HEAT) & 128) != 0; }
    void setFlying(boolean f) { entityData.set(FLYING, f); setNoGravity(f); }
    void setWeapon(int w) { entityData.set(WEAPON, (byte) w); }
    void sync(int md, int aim) {
        if (entityData.get(MODE) != md) entityData.set(MODE, (byte) md);
        if (entityData.get(AIM) != aim) entityData.set(AIM, (byte) aim);
        if (entityData.get(SPIN) != spin) entityData.set(SPIN, (byte) spin);
        int h = Math.min(100, gunHeat * 100 / Machine.MAX_HEAT) | (overheat > 0 ? 128 : 0);
        if (entityData.get(HEAT) != h) entityData.set(HEAT, h);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<DevMachine>("main", 5, test -> test.setAndContinue(IDLE)).triggerableAnim("recoil", RECOIL));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

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
        float yaw = getYRot() + (float) wrap(player.getYRot() - getYRot()) * (flying() ? 0.25f : 0.12f);
        setYRot(yaw); yRotO = yaw; setXRot(0); yBodyRot = yaw; yHeadRot = yaw;
    }

    @Override protected Vec3 getRiddenInput(Player player, Vec3 travelVector) { return new Vec3(player.xxa * 0.6, 0, player.zza); }

    @Override
    protected float getRiddenSpeed(Player player) {
        float s = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
        return player.isSprinting() ? s * 1.7f : s;
    }

    @Override
    public void travel(Vec3 input) {
        if (flying() && getControllingPassenger() instanceof Player p) {
            Vec3 look = p.getLookAngle();
            double r = Math.toRadians(p.getYRot());
            Vec3 left = new Vec3(Math.cos(r), 0, Math.sin(r));
            double sp = p.isSprinting() ? 3.0 : 1.4;
            Vec3 want = look.scale(p.zza * sp).add(left.scale(p.xxa * sp * 0.7));
            if (p.zza == 0 && p.xxa == 0) want = new Vec3(0, Math.sin(tickCount * 0.06) * 0.05, 0);
            setDeltaMovement(getDeltaMovement().lerp(want, 0.12));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        super.travel(input);
    }

    @Override public boolean isAffectedByFluids() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean onClimbable() { return horizontalCollision && !flying(); }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)) return false;
        if (source.getEntity() != null && source.getEntity() == getControllingPassenger()) return false;
        if (shieldT > 0) { Machine.shieldHit(this, level, source, amount); return false; }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) Machine.cleanup(this, sl);
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        updatePose();
        if (!level().isClientSide()) {
            if (isOnFire()) clearFire();
            if (getControllingPassenger() == null && flying()) setFlying(false);
            Machine.tick(this);
            trample();
        }
    }

    /** 몸에 걸리는 지형 짓밟기 (#grief). */
    private void trample() {
        if (tickCount % 2 != 0 || !(level() instanceof ServerLevel sl) || !MechManager.griefOn(sl.getServer())) return;
        if (Math.hypot(getX() - xo, getZ() - zo) + Math.abs(getY() - yo) < 0.05) return;
        AABB b = getBoundingBox().inflate(1, 0, 1);
        int y0 = (int) Math.floor(b.minY + (flying() ? 0 : 3)), broke = 0;
        for (int x = (int) Math.floor(b.minX); x <= (int) Math.floor(b.maxX) && broke < 300; x++)
            for (int z = (int) Math.floor(b.minZ); z <= (int) Math.floor(b.maxZ) && broke < 300; z++)
                for (int y = y0; y <= (int) Math.floor(b.maxY); y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState st = sl.getBlockState(bp);
                    if (st.isAir() || st.getDestroySpeed(sl, bp) < 0 || !st.getFluidState().isEmpty()) continue;
                    sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
                    broke++;
                }
    }

    private void updatePose() {
        System.arraycopy(pose, 0, posePrev, 0, N);
        age2++;
        double px = getX(), py = getY(), pz = getZ();
        vx += ((px - xo) - vx) * 0.3; vz += ((pz - zo) - vz) * 0.3;
        double speed = Math.hypot(vx, vz);
        still = speed < 0.02 ? still + 1 : 0;
        double yaw = Math.toRadians(yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        int md = mode();
        double crouchT = md == 1 ? 3.5 : md == 3 ? -2.5 : 0;
        if (md == 4 && lastMode != 4) crouchS = 6;
        lastMode = md;
        crouchS += (crouchT - crouchS) * (md == 4 ? 0.12 : 0.15);

        int dur = (int) Math.max(8, Math.min(15, Math.round(15 - speed * 10)));
        double stride = Math.min(12, 4 + speed * 16), lead = dur * 0.8;
        airTicks = (!onGround() || flying()) && !onClimbable() ? airTicks + 1 : 0;
        boolean inAir = airTicks > 4;
        boolean snap = !feetInit || (!inAir && wasAir);
        wasAir = inAir;
        for (int i = 0; i < LEGS && feetInit; i++) if (Math.hypot(fx[i] - px, fz[i] - pz) > 60 || Math.abs(fy[i] - py) > 40) snap = true;
        int[] air = new int[2];
        for (int i = 0; i < LEGS; i++) if (stepT[i] > 0) air[group(i)]++;
        for (int i = 0; i < LEGS; i++) {
            double ang = yaw + Math.toRadians(22.5 + 45 * i);
            double restX = px - Math.sin(ang) * REST_R, restZ = pz + Math.cos(ang) * REST_R;
            if (inAir) {   // 비행·공중: 다리를 몸 쪽으로 접어 올림
                double r = flying() ? REST_R * 0.55 : REST_R - 4, ty0 = py + (flying() ? 4 : -2);
                fx[i] += (px - Math.sin(ang) * r - fx[i]) * 0.2; fz[i] += (pz + Math.cos(ang) * r - fz[i]) * 0.2; fy[i] += (ty0 - fy[i]) * 0.2;
                stepT[i] = 0; continue;
            }
            if (snap) { fx[i] = restX; fz[i] = restZ; fy[i] = groundAt(restX, py + 16, restZ); stepT[i] = 0; continue; }
            if (stepT[i] > 0) {
                stepT[i]++;
                double u = Math.min(1, stepT[i] / (double) stepDur[i]), e = u * u * (3 - 2 * u);
                ty[i] = groundAt(tx[i], py + 16, tz[i]);
                fx[i] = sx[i] + (tx[i] - sx[i]) * e; fz[i] = sz[i] + (tz[i] - sz[i]) * e;
                fy[i] = sy[i] + (ty[i] - sy[i]) * e + Math.sin(Math.PI * u) * lift[i];
                if (u >= 1) { stepT[i] = 0; land(i); }
                continue;
            }
            double off = Math.hypot(fx[i] - restX, fz[i] - restZ);
            boolean overdue = off > stride * 1.7 || Math.abs(fy[i] - py) > 18;
            boolean wants = off > stride || (still > 10 && off > 1.4);
            int g = group(i);
            if (overdue || (wants && air[1 - g] == 0)) {
                sx[i] = fx[i]; sy[i] = fy[i]; sz[i] = fz[i];
                tx[i] = restX + vx * lead; tz[i] = restZ + vz * lead; ty[i] = groundAt(tx[i], py + 16, tz[i]);
                double dist = Math.hypot(tx[i] - sx[i], tz[i] - sz[i]);
                lift[i] = Math.min(7, 2 + dist * 0.35) + Math.max(0, ty[i] - sy[i]) * 0.5;
                stepDur[i] = dur + (i % 2); stepT[i] = 1; air[g]++;
            }
        }
        feetInit = true;

        double sff = 0, sfh = 0, srr = 0, srh = 0, hSum = 0;
        for (int i = 0; i < LEGS; i++) {
            double rx = fx[i] - px, rz = fz[i] - pz, h = (stepT[i] > 0 ? ty[i] : fy[i]) - py;
            double f = rx * fwdX + rz * fwdZ, r = rx * rightX + rz * rightZ;
            sff += f * f; sfh += f * h; srr += r * r; srh += r * h; hSum += h;
        }
        double lim = Math.toRadians(14);
        double pitch = Math.max(-lim, Math.min(lim, Math.atan(sfh / Math.max(1e-3, sff))));
        double roll = Math.max(-lim, Math.min(lim, Math.atan(srh / Math.max(1e-3, srr))));
        if (md == 3) pitch -= 0.25;                     // 강타 준비: 앞을 들어올림
        if (flying()) pitch += 0.2 * Math.min(1, speed * 2);
        double bob = (Math.max(-4, Math.min(4, hSum / LEGS * 0.5)) + Math.sin(age2 * 0.06) * 0.2) / S
                + ((air[0] + air[1]) > 0 ? 1.5 : -0.5) * Math.min(1, speed * 4);
        if (md == 2) { pitch += (random.nextDouble() - 0.5) * 0.05; bob += (random.nextDouble() - 0.5) * 6; }
        pitchS += (pitch - pitchS) * 0.15; rollS += (roll - rollS) * 0.15; bobS += (bob - bobS) * 0.2;

        // 상체: 탑승자 시선(좌우)으로 회전, 팔은 무기에 따라 조준
        double aimP = 0, aimYaw = 0;
        if (getControllingPassenger() != null) {
            aimP = Math.max(-0.6, Math.min(0.6, -Math.toRadians(getControllingPassenger().getXRot())));
            aimYaw = Math.toRadians(wrap(getControllingPassenger().getYRot() - yBodyRot));
        }
        torsoS += wrap(Math.toDegrees(aimYaw - torsoS)) * Math.PI / 180 * 0.35;
        int aim = entityData.get(AIM);
        double swing = Math.sin(age2 * 0.1) * Math.min(0.25, speed);
        double aL = (aim & 1) != 0 ? Math.PI / 2 + aimP : -swing - (flying() ? 0.5 : 0);
        double fL = (aim & 1) != 0 ? 0 : 0.3;
        double aR = (aim & 2) != 0 ? Math.PI / 2 + aimP : swing - (flying() ? 0.5 : 0);
        double fR = (aim & 2) != 0 ? 0.05 : 0.3;
        if (md == 1 || md == 2) { aL = -0.8; aR = -0.8; fL = 0.7; fR = 0.7; }
        armLS += (aL - armLS) * 0.3; foreLS += (fL - foreLS) * 0.3; armRS += (aR - armRS) * 0.3; foreRS += (fR - foreRS) * 0.3;
        cannonS += (aimP - cannonS) * 0.3;
        headS += (aimP * 0.5 - headS) * 0.2;

        int B = BODY;
        pose[B] = (float) pitchS; pose[B + 1] = (float) rollS; pose[B + 2] = (float) bobS; pose[B + 3] = (float) (-crouchS / S);
        pose[B + 4] = (float) -torsoS; pose[B + 5] = (float) armLS; pose[B + 6] = (float) foreLS; pose[B + 7] = (float) armRS; pose[B + 8] = (float) foreRS;
        pose[B + 9] += entityData.get(SPIN) / (float) Machine.MAX_SPIN * 0.9f;
        float seg = (float) (Math.PI / 3);
        if (pose[B + 9] > seg * 30) { pose[B + 9] -= seg * 30; posePrev[B + 9] -= seg * 30; }
        pose[B + 10] = (float) cannonS; pose[B + 11] = (float) headS;
        // v2: 등 반응로 고리 회전 · 에너지 날개 전개 · 코어 맥동 · 배 터빈
        boolean wingsOut = flying() || md == 1 || md == 2 || entityData.get(SHIELD) > 0;
        wingS += ((wingsOut ? 1 : 0) - wingS) * 0.12;
        double haloT = 0.06 + (md == 1 ? 0.5 : md == 2 ? 0.9 : 0) + (flying() ? 0.18 : 0) + speed * 0.4 + entityData.get(SPIN) * 0.01;
        haloV += (haloT - haloV) * 0.1;
        pose[B + 12] = posePrev[B + 12] + (float) haloV;
        if (pose[B + 12] > 6283.18f) { pose[B + 12] -= 6283.18f; posePrev[B + 12] -= 6283.18f; }
        pose[B + 13] = (float) wingS;
        double coreT = 1 + Math.sin(age2 * (md == 1 ? 0.9 : 0.15)) * (md == 1 ? 0.35 : 0.06) + (md == 2 ? 0.5 : 0);
        coreS += (coreT - coreS) * 0.4;
        pose[B + 14] = (float) coreS;
        pose[B + 15] = posePrev[B + 15] + (float) (0.15 + speed * 1.2 + (flying() ? 0.6 : 0));
        if (pose[B + 15] > 6283.18f) { pose[B + 15] -= 6283.18f; posePrev[B + 15] -= 6283.18f; }

        // 다리 IK (4마디, DevMech 와 같은 방식)
        for (int i = 0; i < LEGS; i++) {
            double base = 22.5 + 45 * i;
            double rx = fx[i] - px, rz = fz[i] - pz;
            double local = base + wrap(Math.toDegrees(Math.atan2(-rx, rz)) - yBodyRot - base);
            double reach = Math.hypot(rx, rz);
            double u = stepT[i] > 0 ? Math.min(1, stepT[i] / (double) stepDur[i]) : 0, lf = Math.sin(Math.PI * u);
            double c0 = Math.toRadians(18 + 12 * lf), tau = Math.toRadians(-74 + 32 * lf);
            double k0x = HIP_R + LC * Math.cos(c0), k0y = HIP_Y - crouchS + bobS * S + LC * Math.sin(c0);
            double bx = reach - LS * Math.cos(tau), by = (fy[i] - py) - LS * Math.sin(tau);
            double d = Math.max(0.4, bx - k0x), h = by - k0y;
            double dist = Math.max(Math.abs(LF - LT) + 0.05, Math.min(LF + LT - 0.05, Math.hypot(d, h)));
            double c = (LF * LF + dist * dist - LT * LT) / (2 * LF * dist);
            double alpha = Math.atan2(h, d) + Math.acos(Math.max(-1, Math.min(1, c)));
            double kx = LF * Math.cos(alpha), ky = LF * Math.sin(alpha);
            double gamma = Math.atan2(h - ky, d - kx);
            int o = i * PER;
            pose[o] = (float) -Math.toRadians(local); pose[o + 1] = (float) c0; pose[o + 2] = (float) (alpha - c0);
            pose[o + 3] = (float) (gamma - alpha); pose[o + 4] = (float) (tau - gamma);
        }
    }

    private static int group(int i) { return (i % 2) ^ (i >= 4 ? 1 : 0); }

    private void land(int i) {
        if (level() instanceof ServerLevel sl) { Machine.stomp(this, sl, fx[i], fy[i], fz[i]); return; }
        for (int k = 0; k < 10; k++)
            level().addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, fx[i] + (random.nextDouble() - 0.5) * 3, fy[i] + 0.2,
                    fz[i] + (random.nextDouble() - 0.5) * 3, 0, 0.03, 0);
        level().playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP, net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 0.4f, false);
        level().playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL, 0.25f, 0.4f, false);
    }

    private double groundAt(double x, double fromY, double z) {
        Vec3 a = new Vec3(x, fromY, z), b = new Vec3(x, fromY - 50, z);
        BlockHitResult hit = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? getY() : hit.getLocation().y;
    }

    // ───────────── 무기 위치 (실제 좌표, 상체 회전 = 탑승자 시선)
    double torsoYaw() { return getControllingPassenger() != null ? getControllingPassenger().getYRot() : yBodyRot; }
    Vec3 tf() { double r = Math.toRadians(torsoYaw()); return new Vec3(-Math.sin(r), 0, Math.cos(r)); }
    Vec3 tr() { double r = Math.toRadians(torsoYaw()); return new Vec3(-Math.cos(r), 0, -Math.sin(r)); }
    Vec3 at(double side, double up, double fwd) { return position().add(0, up - crouchS + bobS * S, 0).add(tr().scale(side)).add(tf().scale(fwd)); }
    Vec3 chest() { return at(0, (97 + 44) * S, 36 * S); }
    Vec3 gatMuzzle(Vec3 aim) { return at(-58 * S, (97 + 62) * S, 0).add(aim.scale((62 + 48 + 14) * S)); }
    Vec3 flameNozzle(Vec3 aim) { return at(58 * S, (97 + 62) * S, 0).add(aim.scale((62 + 50) * S)).add(0, -1.2, 0); }
    Vec3 palm(Vec3 aim) { return at(58 * S, (97 + 62) * S, 0).add(aim.scale((62 + 58 + 14) * S)); }
    Vec3 pod(int side) { return at(44 * S * side, (97 + 92) * S, 0); }
    Vec3 cannonMuzzle(Vec3 aim, int side) { return at((30 + 4 * side) * S, (97 + 98) * S, -6 * S).add(aim.scale(76 * S)); }
    Vec3 mortar() { return at(0, (97 + 106) * S, -22 * S); }
    Vec3 core() { return at(0, (97 + 44) * S, -36 * S); }
    Vec3 halo() { return at(0, (97 + 62) * S, 52 * S); }
    Vec3 jets(int side) { return at(22 * S * side, (97 + 11) * S, -36 * S); }

    static double wrap(double d) { d %= 360; if (d >= 180) d -= 360; if (d < -180) d += 360; return d; }
}
