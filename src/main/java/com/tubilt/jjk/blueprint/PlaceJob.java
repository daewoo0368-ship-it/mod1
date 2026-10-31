package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.blueprint.core.BpPlan;
import com.tubilt.jjk.blueprint.core.BpTransform;
import com.tubilt.jjk.machine.MachineBlock;
import com.tubilt.jjk.machine.PhysWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 건축물 배치 작업. 서버 틱마다 정해진 양만큼만 진행한다.
 * <ol>
 *   <li>CHECK — 모든 위치의 기존 블록·청크·높이 검사 (안전 배치는 충돌이 하나라도 있으면 아무것도 놓지 않고 거부)</li>
 *   <li>PLACE — 블록 배치(이웃 갱신 없이) + 블록 엔티티 복원(허용 데이터만, 회전 반영). 기계는 네트워크 등록 보류</li>
 *   <li>SHAPES — 다 놓인 뒤 모양 갱신(울타리·레드스톤 연결 등)과 이웃 알림</li>
 *   <li>NETWORK — 보류했던 기계를 한꺼번에 등록 → 포트 탐색·링크·기어비 계산</li>
 *   <li>VERIFY — 몇 틱 뒤 네트워크 상태(부품·네트워크·잠김) 확인 후 결과 보고</li>
 * </ol>
 * 진행 상태(단계·커서)는 {@link BlueprintJobs} 가 저장해 두므로 서버가 꺼져도 다음 시작 때 이어서 끝낸다.
 */
final class PlaceJob {
    static final int CHECK = 0, PLACE = 1, SHAPES = 2, NETWORK = 3, VERIFY = 4, DONE = 5;
    static final String[] PHASE = {"충돌 검사", "블록 배치", "모양·연결 갱신", "기계 네트워크 재구성", "작동 확인", "완료"};
    /** 이웃 갱신·드롭 없이 클라이언트에만 알림 (붙는 블록이 배치 중에 떨어지지 않게). */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    final UUID owner; final String ownerName;
    final ServerLevel level;
    final Blueprint bp;
    final BpTransform tf;
    final BlockPos origin, min, max;
    final BpPlan.Policy policy;
    final int[] order;
    final BlockState[] tstates;
    final boolean[] machine;
    final List<BlockPos> machines = new ArrayList<>();
    final BpPlan.Check check = new BpPlan.Check();
    final Set<String> warnings = new LinkedHashSet<>();
    final List<String> failures = new ArrayList<>();

    int phase = CHECK, cursor, limit;
    int placed, skipped, failed, beRestored, beFailed, waitTicks, ticks;
    boolean cancelled, paused;
    String result;
    PhysWorld.Stats stats;
    private final int[] tmp = new int[3];

    PlaceJob(UUID owner, String ownerName, ServerLevel level, Blueprint bp, int rot, boolean mirror, BlockPos origin, BpPlan.Policy policy) {
        this.owner = owner; this.ownerName = ownerName; this.level = level; this.bp = bp; this.policy = policy; this.origin = origin;
        this.tf = new BpTransform(rot, mirror, bp.sx, bp.sy, bp.sz);
        this.min = origin;
        this.max = origin.offset(tf.outSx() - 1, tf.outSy() - 1, tf.outSz() - 1);
        Rotation r = switch (tf.rot) { case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180; case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE; };
        Mirror m = mirror ? Mirror.FRONT_BACK : Mirror.NONE;
        tstates = new BlockState[bp.palette.size()];
        for (int i = 0; i < tstates.length; i++) tstates[i] = bp.palette.get(i).mirror(m).rotate(r);
        int n = bp.count();
        int[] klass = new int[n];
        machine = new boolean[n];
        for (int i = 0; i < n; i++) {
            BlockState s = bp.state(i);
            machine[i] = s.getBlock() instanceof MachineBlock;
            klass[i] = machine[i] ? 2 : s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 0 : 1;
        }
        order = BpPlan.order(n, bp.xs, bp.ys, bp.zs, klass);
        limit = n;
        for (int i = 0; i < n; i++) if (machine[i]) machines.add(pos(i));
    }

    BlockPos pos(int i) {
        tf.apply(bp.xs[i], bp.ys[i], bp.zs[i], tmp);
        return origin.offset(tmp[0], tmp[1], tmp[2]);
    }

    int occupancy(BlockPos p) {
        if (level.isOutsideBuildHeight(p)) return BpPlan.OUT_OF_WORLD;
        if (!level.isLoaded(p)) return BpPlan.UNLOADED;
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return BpPlan.AIR;
        return s.canBeReplaced() ? BpPlan.REPLACEABLE : BpPlan.SOLID;
    }

    boolean overlaps(BlockPos a, BlockPos b) {
        return a.getX() <= max.getX() && b.getX() >= min.getX() && a.getY() <= max.getY() && b.getY() >= min.getY() && a.getZ() <= max.getZ() && b.getZ() >= min.getZ();
    }

    /** 사용자가 취소: 놓은 데까지만 마무리(모양 갱신·네트워크 등록)하고 끝낸다. */
    void cancel() {
        if (phase >= NETWORK) return;
        cancelled = true;
        if (phase == CHECK) { phase = NETWORK; return; }
        if (phase == PLACE) { limit = cursor; cursor = 0; phase = SHAPES; }
    }

    double progress() {
        int n = Math.max(1, bp.count());
        return switch (phase) {
            case CHECK -> 0.15 * cursor / n;
            case PLACE -> 0.15 + 0.75 * cursor / n;
            case SHAPES -> 0.90 + 0.08 * cursor / Math.max(1, limit);
            case NETWORK, VERIFY -> 0.99;
            default -> 1.0;
        };
    }

    String status() {
        return String.format("%s · %s %d%% (%d/%d) · 놓음 %d 건너뜀 %d%s", bp.name, PHASE[phase], Math.round(progress() * 100),
                Math.min(cursor, bp.count()), bp.count(), placed, skipped, paused ? " · §e청크 로딩 대기" : "");
    }

    /** 이번 틱 몫만큼 진행. ops 는 남은 작업량 한도, deadline 은 System.nanoTime() 마감. 처리한 양을 돌려준다. */
    int step(int ops, long deadline) {
        ticks++;
        paused = false;
        int done = 0;
        int n = bp.count();
        while (done < ops && System.nanoTime() < deadline && phase < DONE) {
            switch (phase) {
                case CHECK -> {
                    if (cursor >= n) {
                        if (!check.ok(policy)) { result = "§c소환 거부: " + check.reason(policy); failures.add(check.reason(policy)); phase = NETWORK; cancelled = true; }
                        else { phase = PLACE; cursor = 0; }
                        continue;
                    }
                    int i = order[cursor];
                    BpPlan.add(check, i, occupancy(pos(i)), policy, 64);
                    cursor++; done++;
                }
                case PLACE -> {
                    if (cursor >= n) { phase = SHAPES; limit = n; cursor = 0; continue; }
                    int i = order[cursor];
                    BlockPos p = pos(i);
                    int occ = occupancy(p);
                    if (occ == BpPlan.UNLOADED) { paused = true; return done; }
                    if (!BpPlan.shouldPlace(policy, occ)) {
                        skipped++;
                        if (occ == BpPlan.OUT_OF_WORLD) fail(p, "월드 높이 밖");
                        cursor++; done++; continue;
                    }
                    placeOne(i, p);
                    cursor++; done++;
                }
                case SHAPES -> {
                    if (cursor >= limit) { phase = NETWORK; continue; }
                    int i = order[cursor];
                    BlockPos p = pos(i);
                    if (!level.isLoaded(p)) { paused = true; return done; }
                    BlockState cur = level.getBlockState(p);
                    if (cur.getBlock() == tstates[bp.ss[i]].getBlock()) {
                        BlockState upd = Block.updateFromNeighbourShapes(cur, level, p);
                        if (upd != cur) level.setBlock(p, upd, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        level.updateNeighborsAt(p, upd.getBlock());
                    }
                    cursor++; done++;
                }
                case NETWORK -> {
                    PhysWorld.release(level, machines);
                    phase = VERIFY; waitTicks = 3;
                    return done + machines.size();
                }
                case VERIFY -> {
                    if (--waitTicks > 0) return done;
                    stats = PhysWorld.stats(level, machines);
                    phase = DONE;
                    if (result == null) result = summary();
                    return done;
                }
                default -> { return done; }
            }
        }
        return done;
    }

    void placeOne(int i, BlockPos p) {
        BlockState st = tstates[bp.ss[i]];
        if (machine[i]) PhysWorld.defer(level, p);
        try {
            boolean changed = level.setBlock(p, st, FLAGS);
            if (!changed && !level.getBlockState(p).equals(st)) { fail(p, "배치 실패"); return; }
            placed++;
            CompoundTag nbt = bp.bes.get(i);
            if (nbt != null) restore(i, p, st, nbt);
        } catch (RuntimeException e) {
            fail(p, "배치 오류: " + e.getMessage());
            JjkMod.LOG.warn("[Dan] 설계도 블록 배치 오류 {} {}", p, st, e);
        }
    }

    /** 블록 엔티티 복원: 종류 확인 → 기계는 회전량 부호 변환 → 허용된 데이터만 적용. */
    void restore(int i, BlockPos p, BlockState st, CompoundTag nbt) {
        BlockEntity be = level.getBlockEntity(p);
        String want = nbt.getStringOr("id", "");
        if (be == null) { beFailed++; warnings.add("블록 엔티티가 생기지 않음: " + want); return; }
        ResourceLocation have = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (have == null || !have.toString().equals(want)) { beFailed++; warnings.add("블록 엔티티 종류 불일치: " + want + " ≠ " + have); return; }
        CompoundTag t = nbt.copy();
        if (want.equals("jjk:machine")) {
            BlockState src = bp.state(i);
            int axis = src.hasProperty(MachineBlock.AXIS) ? src.getValue(MachineBlock.AXIS).ordinal() : 1;
            int f = tf.spinFactor(axis);
            t.putInt("dir", t.getIntOr("dir", 1) * f);
            t.putDouble("omega", t.getDoubleOr("omega", 0) * f);
            t.putDouble("angle", t.getDoubleOr("angle", 0) * f);
        }
        try {
            be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), t));
            be.setChanged();
            level.sendBlockUpdated(p, st, st, Block.UPDATE_CLIENTS);
            beRestored++;
        } catch (RuntimeException e) {
            beFailed++;
            warnings.add("블록 엔티티 데이터 복원 실패: " + want);
            JjkMod.LOG.warn("[Dan] 블록 엔티티 복원 실패 {} {}", p, want, e);
        }
    }

    void fail(BlockPos p, String why) {
        failed++;
        if (failures.size() < 50) failures.add(p.toShortString() + " " + why);
    }

    String summary() {
        StringBuilder s = new StringBuilder();
        s.append(cancelled ? "§e소환 취소됨" : "§a소환 완료").append(" §f").append(bp.name).append(" §7(").append(tf.label()).append(", ").append(policy.label).append(")")
                .append("\n§7블록 놓음 ").append(placed).append(" · 건너뜀 ").append(skipped).append(" · 실패 ").append(failed)
                .append(" · 블록 엔티티 복원 ").append(beRestored).append(beFailed > 0 ? " (실패 " + beFailed + ")" : "")
                .append(" · ").append(ticks).append("틱");
        if (stats != null && stats.machines() > 0)
            s.append("\n§b기계 네트워크 §f부품 ").append(stats.registered()).append("/").append(stats.machines())
                    .append(" · 네트워크 ").append(stats.nets()).append(stats.jammed() > 0 ? " · §c잠김 " + stats.jammed() : " · 잠김 없음");
        if (!warnings.isEmpty()) s.append("\n§e경고 ").append(warnings.size()).append("건: ").append(String.join(" / ", warnings.stream().limit(3).toList()));
        return s.toString();
    }
}
