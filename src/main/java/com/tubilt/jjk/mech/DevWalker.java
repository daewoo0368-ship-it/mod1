package com.tubilt.jjk.mech;

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
 * 개발자의 귀여운 탑승물2 — '강철 짐승' 4족 보행 요새.
 * 큰 몸통(어깨 혹·등선) + 머리, 어깨 위 회전 포탑(쌍열 주포).
 * 다리: 몸 아래 네 귀퉁이, 앞다리 무릎 뒤·뒷다리 무릎 앞(동물 관절), 대각선 두 쌍이 번갈아 디딤, 발판 수평.
 */
public class DevWalker extends PathfinderMob implements GeoEntity, DevVehicle {
    public static final int LEGS = 4, PER = 4;          // [뿌리 Y, 대퇴, 경골, 발]
    public static final float HIP_Y = 7.5f, L1 = 4.0f, L2 = 4.4f;
    /** 엉덩이 위치 (앞 +, 오른쪽 +) 칸: 앞왼, 앞오, 뒤오, 뒤왼 — 모델 HIPS 와 일치 */
    public static final double[][] HIP = {{4.0, -2.5}, {4.0, 2.5}, {-4.0, 2.5}, {-4.0, -2.5}};
    /** 앞다리는 무릎이 뒤로(다리 틀이 뒤를 봄), 뒷다리는 무릎이 앞으로 */
    static boolean front(int i) { return i < 2; }
    public static final int BODY = LEGS * PER;          // + [피치, 롤, 높이px, 포탑Y, 포신X, 루트px, 머리Y, 꼬리Y]

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dev_walker.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dev_walker.walk");
    static final RawAnimation FIRE = RawAnimation.begin().thenPlay("animation.dev_walker.fire");
    static final RawAnimation HEAVY = RawAnimation.begin().thenPlay("animation.dev_walker.heavy");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 0 평상, 1 중포 충전, 2 중포 반동 */
    static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(DevWalker.class, EntityDataSerializers.BYTE);

    public final float[] legs = new float[BODY + 8], legsPrev = new float[BODY + 8];
    private final double[] fx = new double[LEGS], fy = new double[LEGS], fz = new double[LEGS];
    private final double[] sx = new double[LEGS], sy = new double[LEGS], sz = new double[LEGS];
    private final double[] tx = new double[LEGS], ty = new double[LEGS], tz = new double[LEGS];
    private final int[] stepT = new int[LEGS], stepDur = new int[LEGS];
    private final double[] stepLift = new double[LEGS];
    private boolean feetInit = false, wasAir = false;
    private double vx, vz, pitchS, rollS, bobS, crouchS, turretS, cannonS, headS;
    private int still, age2, airTicks, lastMode;

    // 서버 스킬 상태
    public int cannonCd = 0, volley = 0, volleyT = 0, heavyCd = 0, heavyCharge = 0, recoilT = 0;

    public DevWalker(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (int i = 0; i < LEGS; i++) legs[i * PER] = legsPrev[i * PER] = front(i) ? (float) Math.PI : 0f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 800.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.STEP_HEIGHT, 3.2)
                .add(Attributes.SAFE_FALL_DISTANCE, 1000.0)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 30.0)
                .add(Attributes.CAMERA_DISTANCE, 22.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) 0);
    }

    public int mode() { return entityData.get(MODE); }

    // ───────────── GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DevWalker>("main", 5,
                test -> test.setAndContinue(test.isMoving() ? WALK : IDLE))
                .triggerableAnim("fire", FIRE).triggerableAnim("heavy", HEAVY));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ───────────── 탑승 / 조종
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isVehicle()) {
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
        float yaw = getYRot() + (float) wrap(player.getYRot() - getYRot()) * 0.1f;   // 선체는 묵직하게 천천히 회전
        setYRot(yaw); yRotO = yaw; setXRot(0);
        yBodyRot = yaw; yHeadRot = yaw;
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) { return new Vec3(player.xxa * 0.6, 0, player.zza); }

    @Override
    protected float getRiddenSpeed(Player player) {
        float s = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
        return player.isSprinting() ? s * 1.6f : s;
    }

    @Override public boolean isAffectedByFluids() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

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
        updateLegs();
        if (!level().isClientSide()) {
            if (isOnFire()) clearFire();
            Walker.tick(this);
            byte md = (byte) (heavyCharge > 0 ? 1 : recoilT > 0 ? 2 : 0);
            if (md != entityData.get(MODE)) entityData.set(MODE, md);
        }
    }

    private void updateLegs() {
        System.arraycopy(legs, 0, legsPrev, 0, legs.length);
        age2++;
        double px = getX(), py = getY(), pz = getZ();
        vx += ((px - xo) - vx) * 0.35;
        vz += ((pz - zo) - vz) * 0.35;
        double speed = Math.sqrt(vx * vx + vz * vz);
        still = speed < 0.012 ? still + 1 : 0;
        double yaw = Math.toRadians(yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);

        int md = mode();
        if (md == 2 && lastMode != 2) crouchS = 1.4;      // 중포 반동: 쿵 주저앉음
        lastMode = md;
        double crouchT = md == 1 ? 1.2 : 0;
        crouchS += (crouchT - crouchS) * 0.15;

        int dur = (int) Math.max(7, Math.min(13, Math.round(13 - speed * 14)));
        double stride = Math.min(3.6, 1.2 + speed * 7.0), lead = dur * 0.8;

        airTicks = (!onGround()) ? airTicks + 1 : 0;
        boolean inAir = airTicks > 4;
        boolean snapAll = !feetInit || (!inAir && wasAir);
        wasAir = inAir;
        for (int i = 0; i < LEGS && feetInit; i++) {
            double dx = fx[i] - px, dz = fz[i] - pz;
            if (dx * dx + dz * dz > 24 * 24 || Math.abs(fy[i] - py) > 16) snapAll = true;
        }
        int[] airborne = new int[2];
        for (int i = 0; i < LEGS; i++) if (stepT[i] > 0) airborne[i % 2]++;

        for (int i = 0; i < LEGS; i++) {
            // 발 기본 위치: 엉덩이 바로 아래 (동물처럼 몸 밑으로 디딤)
            double restX = px + fwdX * HIP[i][0] + rightX * HIP[i][1], restZ = pz + fwdZ * HIP[i][0] + rightZ * HIP[i][1];
            if (inAir) {   // 공중: 다리를 몸 쪽으로 접어 올림
                fx[i] += (restX - fx[i]) * 0.3;
                fz[i] += (restZ - fz[i]) * 0.3;
                fy[i] += (py + 1.5 - fy[i]) * 0.3;
                stepT[i] = 0;
                continue;
            }
            if (snapAll) { fx[i] = restX; fz[i] = restZ; fy[i] = groundAt(restX, py + 8, restZ); stepT[i] = 0; continue; }
            if (stepT[i] > 0) {
                stepT[i]++;
                double u = Math.min(1.0, stepT[i] / (double) stepDur[i]), e = u * u * (3 - 2 * u);
                ty[i] = groundAt(tx[i], py + 8, tz[i]);
                fx[i] = sx[i] + (tx[i] - sx[i]) * e;
                fz[i] = sz[i] + (tz[i] - sz[i]) * e;
                fy[i] = sy[i] + (ty[i] - sy[i]) * e + Math.sin(Math.PI * u) * stepLift[i];
                if (u >= 1.0) { stepT[i] = 0; land(i); }
                continue;
            }
            double off = Math.hypot(fx[i] - restX, fz[i] - restZ);
            boolean overdue = off > stride * 1.7 || Math.abs(fy[i] - py) > 8;
            boolean wants = off > stride || (still > 10 && off > 0.5);
            int g = i % 2;   // 대각선 두 쌍
            if (overdue || (wants && airborne[1 - g] == 0)) {
                double ax = restX + vx * lead, az = restZ + vz * lead;
                sx[i] = fx[i]; sy[i] = fy[i]; sz[i] = fz[i];
                tx[i] = ax; tz[i] = az; ty[i] = groundAt(ax, py + 8, az);
                double dist = Math.hypot(tx[i] - sx[i], tz[i] - sz[i]);
                stepLift[i] = Math.min(2.6, 0.9 + dist * 0.35) + Math.max(0, ty[i] - sy[i]) * 0.5;
                stepDur[i] = dur;
                stepT[i] = 1;
                airborne[g]++;
            }
        }
        feetInit = true;

        // 선체 기울기·높이
        double sff = 0, sfh = 0, srr = 0, srh = 0, hSum = 0;
        for (int i = 0; i < LEGS; i++) {
            double rx = fx[i] - px, rz = fz[i] - pz, h = (stepT[i] > 0 ? ty[i] : fy[i]) - py;
            double f = rx * fwdX + rz * fwdZ, r = rx * rightX + rz * rightZ;
            sff += f * f; sfh += f * h; srr += r * r; srh += r * h; hSum += h;
        }
        double lim = Math.toRadians(12);
        double pitch = Math.max(-lim, Math.min(lim, Math.atan(sfh / Math.max(1e-3, sff))));
        double roll = Math.max(-lim, Math.min(lim, Math.atan(srh / Math.max(1e-3, srr))));
        int air = airborne[0] + airborne[1];
        double bob = Math.max(-1.5, Math.min(1.5, hSum / LEGS * 0.5)) * 16 + Math.sin(age2 * 0.06) * 0.4
                + (air > 0 ? 1.2 : -0.6) * Math.min(1, speed * 6);
        if (md == 1) { pitch += (random.nextDouble() - 0.5) * 0.02; bob += (random.nextDouble() - 0.5) * 0.8; }
        pitchS += (pitch - pitchS) * 0.12; rollS += (roll - rollS) * 0.12; bobS += (bob - bobS) * 0.2;

        // 포탑: 탑승자 시선(좌우) 즉시 추적 / 포신: 상하
        double turretT = 0, cannonT = 0;
        if (getControllingPassenger() != null) {
            turretT = Math.toRadians(wrap(getControllingPassenger().getYRot() - yBodyRot));
            cannonT = Math.max(-0.35, Math.min(0.6, -Math.toRadians(getControllingPassenger().getXRot()))) - pitchS;
        }
        turretS += wrap(Math.toDegrees(turretT - turretS)) * Math.PI / 180 * 0.45;
        cannonS += (cannonT - cannonS) * 0.4;

        legs[BODY] = (float) pitchS; legs[BODY + 1] = (float) rollS; legs[BODY + 2] = (float) bobS;
        legs[BODY + 3] = (float) -turretS; legs[BODY + 4] = (float) cannonS; legs[BODY + 5] = (float) (-crouchS * 16);

        // 다리 IK: 다리는 앞뒤 평면에서만 굽음. 앞다리 틀은 뒤를, 뒷다리 틀은 앞을 향함 → 무릎 방향이 동물처럼
        for (int i = 0; i < LEGS; i++) {
            double hipX = px + fwdX * HIP[i][0] + rightX * HIP[i][1], hipZ = pz + fwdZ * HIP[i][0] + rightZ * HIP[i][1];
            double s = front(i) ? -1 : 1;                                  // 다리 틀 방향 (앞 = +1)
            double d = ((fx[i] - hipX) * fwdX + (fz[i] - hipZ) * fwdZ) * s;
            double h = fy[i] - (py + HIP_Y - crouchS);
            double dist = Math.max(Math.abs(L1 - L2) + 0.05, Math.min(L1 + L2 - 0.05, Math.hypot(d, h)));
            double c = (L1 * L1 + dist * dist - L2 * L2) / (2 * L1 * dist);
            double alpha = Math.atan2(h, d) + Math.acos(Math.max(-1, Math.min(1, c)));
            double kx = L1 * Math.cos(alpha), ky = L1 * Math.sin(alpha);
            double gamma = Math.atan2(h - ky, d - kx);
            int o = i * PER;
            legs[o] = front(i) ? (float) Math.PI : 0f;
            legs[o + 1] = (float) alpha;
            legs[o + 2] = (float) (gamma - alpha);
            legs[o + 3] = (float) -gamma;
        }
        // 머리: 포탑 쪽을 살짝 따라봄 / 꼬리: 걸음에 맞춰 흔들림
        headS += (Math.max(-0.45, Math.min(0.45, turretS * 0.35)) - headS) * 0.15;
        legs[BODY + 6] = (float) -headS;
        legs[BODY + 7] = (float) (Math.sin(age2 * (0.06 + speed * 1.2)) * (0.12 + speed * 1.5));
    }

    private void land(int i) {
        var lvl = level();
        if (lvl instanceof ServerLevel sl) { stomp(sl, fx[i], fy[i], fz[i]); return; }
        for (int k = 0; k < 6; k++)
            lvl.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, fx[i] + (random.nextDouble() - 0.5) * 1.6, fy[i] + 0.1,
                    fz[i] + (random.nextDouble() - 0.5) * 1.6, 0, 0.03, 0);
        lvl.playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP, net.minecraft.sounds.SoundSource.NEUTRAL,
                0.6f, 0.4f + random.nextFloat() * 0.1f, false);
        lvl.playLocalSound(fx[i], fy[i], fz[i], net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL,
                0.15f, 0.5f, false);
    }

    /** 발판이 내려찍힌 자리 몹 즉사 (플레이어 제외). */
    private void stomp(ServerLevel sl, double x, double y, double z) {
        AABB box = new AABB(x - 2.2, y - 1, z - 2.2, x + 2.2, y + 3, z + 2.2);
        Entity rider = getControllingPassenger();
        var src = rider instanceof Player p ? sl.damageSources().playerAttack(p) : sl.damageSources().mobAttack(this);
        boolean hit = false;
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != this
                && !(e instanceof Player) && e.getVehicle() != this && !e.isInvulnerable())) {
            v.invulnerableTime = 0;
            v.hurtServer(sl, src, Math.max(1000f, v.getMaxHealth() * 2));
            hit = true;
        }
        if (hit) sl.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.SLIME_SQUISH, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.5f);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, y + 0.2, z, 6, 1.0, 0.05, 1.0, 0.02);
    }

    private double groundAt(double x, double fromY, double z) {
        Vec3 a = new Vec3(x, fromY, z), b = new Vec3(x, fromY - 22, z);
        BlockHitResult hit = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? getY() : hit.getLocation().y;
    }

    static double wrap(double d) { d %= 360; if (d >= 180) d -= 360; if (d < -180) d += 360; return d; }
}
