package com.tubilt.jjk.machine;

import com.tubilt.jjk.sim.Part;
import com.tubilt.jjk.sim.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 기계 블록의 블록 엔티티.
 *  · 서버: 시뮬레이션 코어의 {@link Part} 를 소유(로드 시 등록, 언로드/파괴 시 제거). 회전 상태를 NBT 로 저장.
 *  · 클라이언트: 서버가 보낸 (각도, 각속도)를 받아 렌더 프레임마다 각도를 외삽한다 → 회전은 전적으로 물리 엔진 값에서 나온다.
 */
public class MachineBE extends BlockEntity implements GeoBlockEntity {
    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.machine.idle");
    static final RawAnimation SHAKE = RawAnimation.begin().thenLoop("animation.machine.shake");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 서버 전용
    Part part;
    double lastSentOmega = Double.NaN; int lastSentTick = -999;

    // 저장/동기화되는 상태 (서버에서는 part 가 없을 때의 보관값, 클라이언트에서는 수신값)
    double sOmega, sAngle, sFatigue, sWear, sThrottle = 1; int sDir = 1;

    // 클라이언트 보간
    private long stamp; private double jump; private boolean gotFirst;

    public MachineBE(BlockPos pos, BlockState st) { super(Machines.BE.get(), pos, st); }

    public MachineBlock block() { return (MachineBlock) getBlockState().getBlock(); }
    public String kind() { return block().id(); }
    public boolean damaged() { return sFatigue >= 0.5 || sWear >= 0.5; }

    // ───────────── 서버: 생명주기 ─────────────
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) PhysWorld.register(this);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel) PhysWorld.unregister(this);
        super.setRemoved();
    }

    /** 시뮬레이션 상태 → 보관 필드 */
    void pull() {
        if (part == null) return;
        sOmega = part.omega; sAngle = part.angle; sFatigue = part.fatigue; sWear = part.wear;
        if (part instanceof Parts.Motor m) { sThrottle = m.throttle(); sDir = m.direction(); }
    }
    /** 보관 필드 → 시뮬레이션 부품 */
    void push() {
        part.omega = sOmega; part.angle = sAngle; part.fatigue = sFatigue; part.wear = sWear;
        if (part instanceof Parts.Motor m) { m.setThrottle(sThrottle); m.setDirection(sDir); }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        pull();
        out.putDouble("omega", sOmega); out.putDouble("angle", sAngle);
        out.putDouble("fatigue", sFatigue); out.putDouble("wear", sWear);
        out.putDouble("throttle", sThrottle); out.putInt("dir", sDir);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        double om = in.getDoubleOr("omega", 0), an = in.getDoubleOr("angle", 0);
        sFatigue = in.getDoubleOr("fatigue", 0); sWear = in.getDoubleOr("wear", 0);
        sThrottle = in.getDoubleOr("throttle", 1); sDir = in.getIntOr("dir", 1);
        // 이전 외삽값과 새 서버값의 차이를 짧게 감쇠시켜 각도가 튀지 않게 한다 (기어 사이 비율은 선형이라 유지됨)
        double pred = gotFirst ? angleNow() : an;
        sOmega = om; sAngle = an; stamp = System.nanoTime();
        jump = gotFirst ? pred - an : 0; gotFirst = true;
    }

    // ───────────── 동기화 ─────────────
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveWithoutMetadata(r); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(1.0); }

    // ───────────── 클라이언트: 렌더 각도 ─────────────
    /** 지금 렌더할 회전각(rad). 마지막 수신값에서 각속도로 외삽. */
    public double angleNow() {
        double dt = (System.nanoTime() - stamp) / 1e9;
        if (stamp == 0) return sAngle;
        dt = Math.min(dt, 3.0);
        return sAngle + sOmega * dt + jump * Math.exp(-dt / 0.15);
    }
    public double omegaNow() { return sOmega; }

    // ───────────── 상호작용 ─────────────
    void interact(Player p) {
        if (part == null) return;
        if (part instanceof Parts.Motor m) {
            if (p.isShiftKeyDown()) m.setDirection(-m.direction());
            else m.setThrottle(m.throttle() < 0.01 ? 0.5 : m.throttle() < 0.99 ? 1.0 : 0.0);
            p.displayClientMessage(Component.literal(String.format("§e전동기 §f스로틀 %d%% §7| %s", Math.round(m.throttle() * 100), m.direction() > 0 ? "정방향" : "역방향")), true);
            return;
        }
        double rpm = part.omega * 60 / (2 * Math.PI);
        p.displayClientMessage(Component.literal(String.format("§e%s §f%.0f RPM §7| 통과 토크 %.1f N·m §7| 피로 %d%% 마모 %d%%",
                part.name, rpm, part.linkTorque, Math.round(part.fatigue * 100), Math.round(part.wear * 100))), true);
    }

    // ───────────── GeckoLib ─────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<MachineBE>("main", 5, t -> t.setAndContinue(t.animatable().damaged() ? SHAKE : IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
