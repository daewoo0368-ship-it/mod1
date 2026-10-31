package com.tubilt.jjk.machine;

import com.mojang.serialization.MapCodec;
import com.tubilt.jjk.sim.Part;
import com.tubilt.jjk.sim.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 기계 블록 기반 클래스. 설치한 면의 축(AXIS)이 회전축이 된다.
 * 렌더링은 GeckoLib 블록 엔티티 렌더러가 전담(블록 자체는 투명), 물리는 {@link PhysWorld} 의 시뮬레이션 코어가 담당한다.
 */
public abstract class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    protected MachineBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    /** 모델/텍스처 이름(= 블록 id). */
    public abstract String id();
    /** 기어면 잇수, 아니면 0. 맞물림은 두 기어 잇수 합이 40일 때만 성립(중심 거리 = 1블록). */
    public int teeth() { return 0; }
    /** 이 블록이 표현하는 시뮬레이션 부품. */
    public abstract Part createPart();

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AXIS); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(AXIS, c.getClickedFace().getAxis());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new MachineBE(pos, st); }

    @Override
    public InteractionResult useWithoutItem(BlockState st, Level lvl, BlockPos pos, Player pl, BlockHitResult hit) {
        if (!pl.getMainHandItem().isEmpty()) return InteractionResult.PASS;   // 블록 들고 있으면 평소처럼 설치
        if (!lvl.isClientSide() && lvl.getBlockEntity(pos) instanceof MachineBE be) be.interact(pl);
        return InteractionResult.SUCCESS;
    }

    // ───────────── 종류별 블록 ─────────────
    public static final class Shaft extends MachineBlock {
        public static final MapCodec<Shaft> CODEC = simpleCodec(Shaft::new);
        public Shaft(BlockBehaviour.Properties p) { super(p); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public String id() { return "mech_shaft"; }
        @Override public Part createPart() { return new Parts.Shaft("회전축", 0.2, 400); }
    }
    public abstract static class Gear extends MachineBlock {
        final int n; final double J, rated; final String id;
        Gear(BlockBehaviour.Properties p, String id, int n, double J, double rated) { super(p); this.id = id; this.n = n; this.J = J; this.rated = rated; }
        @Override public String id() { return id; }
        @Override public int teeth() { return n; }
        @Override public Part createPart() { return new Parts.Gear("기어(" + n + "T)", n, J, rated); }
    }
    public static final class GearS extends Gear {
        public static final MapCodec<GearS> CODEC = simpleCodec(GearS::new);
        public GearS(BlockBehaviour.Properties p) { super(p, "mech_gear_s", 12, 0.08, 300); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
    public static final class GearM extends Gear {
        public static final MapCodec<GearM> CODEC = simpleCodec(GearM::new);
        public GearM(BlockBehaviour.Properties p) { super(p, "mech_gear_m", 20, 0.25, 600); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
    public static final class GearL extends Gear {
        public static final MapCodec<GearL> CODEC = simpleCodec(GearL::new);
        public GearL(BlockBehaviour.Properties p) { super(p, "mech_gear_l", 28, 0.6, 1000); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
    public static final class Motor extends MachineBlock {
        public static final MapCodec<Motor> CODEC = simpleCodec(Motor::new);
        public Motor(BlockBehaviour.Properties p) { super(p); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public String id() { return "mech_motor"; }
        /** 시험용 전동기: 정지토크 120 N·m, 무부하 약 1000 RPM. (전력 시스템은 5단계에서 연결 — 지금은 외부 동력 없이 도는 시험용) */
        @Override public Part createPart() { return new Parts.Motor("전동기", 1.5, 120, 104.72, 300); }
    }
    public static final class Flywheel extends MachineBlock {
        public static final MapCodec<Flywheel> CODEC = simpleCodec(Flywheel::new);
        public Flywheel(BlockBehaviour.Properties p) { super(p); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public String id() { return "mech_flywheel"; }
        @Override public Part createPart() { return new Parts.Flywheel("플라이휠", 40, 0.05, 800); }
    }
    public static final class Fan extends MachineBlock {
        public static final MapCodec<Fan> CODEC = simpleCodec(Fan::new);
        public Fan(BlockBehaviour.Properties p) { super(p); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public String id() { return "mech_fan"; }
        /** 팬 부하: 토크 = 0.02·ω² (속도의 제곱에 비례하는 공기저항). */
        @Override public Part createPart() { return new Parts.Load("팬 부하", 2, 0.02, 0, 600); }
    }
}
