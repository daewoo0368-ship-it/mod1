package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.blueprint.core.BpPlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자 설계도 저장 작업: 선택 영역을 틱마다 조금씩 읽는다(월드는 바꾸지 않는다).
 * 블록 상태(방향 포함)·블록 엔티티(허용 데이터만)·기계 상태를 기록하고, 끝나면 검증한 뒤 파일로 저장한다.
 */
final class CaptureJob {
    final UUID owner; final String ownerName;
    final ServerLevel level;
    final BlockPos min;
    final int sx, sy, sz;
    final long total;
    final String id, name;
    long cursor;
    boolean done, paused;
    String result;
    Blueprint saved;

    final List<BlockState> palette = new ArrayList<>();
    final Map<BlockState, Integer> pal = new HashMap<>();
    final IntArrayList X = new IntArrayList(), Y = new IntArrayList(), Z = new IntArrayList(), S = new IntArrayList();
    final Map<Integer, CompoundTag> bes = new HashMap<>();
    final List<String> warnings = new ArrayList<>();

    CaptureJob(UUID owner, String ownerName, ServerLevel level, BlockPos a, BlockPos b, String id, String name) {
        this.owner = owner; this.ownerName = ownerName; this.level = level; this.id = id; this.name = name;
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos mx = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        sx = mx.getX() - min.getX() + 1; sy = mx.getY() - min.getY() + 1; sz = mx.getZ() - min.getZ() + 1;
        total = (long) sx * sy * sz;
    }

    double progress() { return total == 0 ? 1 : cursor / (double) total; }

    int step(int ops, long deadline) {
        paused = false;
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (n < ops && cursor < total && System.nanoTime() < deadline) {
            long layer = (long) sx * sz;
            int y = (int) (cursor / layer), rem = (int) (cursor % layer), z = rem / sx, x = rem % sx;
            p.set(min.getX() + x, min.getY() + y, min.getZ() + z);
            if (!level.isLoaded(p)) { paused = true; return n; }
            BlockState st = level.getBlockState(p);
            cursor++; n++;
            if (st.isAir()) continue;
            String key = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            if (BlueprintFilter.deniedBlock(key)) { BlueprintFilter.warn(warnings, "금지 블록은 저장하지 않음: " + key); continue; }
            if (X.size() >= BpPlan.MAX_BLOCKS) { result = "§c저장 실패: 블록이 너무 많음 (최대 " + BpPlan.MAX_BLOCKS + ")"; done = true; return n; }
            Integer k = pal.get(st);
            if (k == null) { k = palette.size(); palette.add(st); pal.put(st, k); }
            int idx = X.size();
            X.add(x); Y.add(y); Z.add(z); S.add(k);
            if (st.hasBlockEntity()) {
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    try {
                        CompoundTag raw = be.saveWithoutMetadata(level.registryAccess());
                        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                        if (type != null) raw.putString("id", type.toString());
                        CompoundTag clean = BlueprintFilter.sanitize(raw, warnings);
                        if (clean != null) bes.put(idx, clean);
                    } catch (RuntimeException e) {
                        BlueprintFilter.warn(warnings, "블록 엔티티 저장 실패: " + key);
                        JjkMod.LOG.warn("[Dan] 블록 엔티티 저장 실패 {}", p, e);
                    }
                }
            }
        }
        if (cursor >= total && !done) finish();
        return n;
    }

    void finish() {
        done = true;
        if (X.isEmpty()) { result = "§c저장 실패: 영역에 블록이 없음"; return; }
        Blueprint b = new Blueprint();
        b.id = id; b.name = name; b.author = ownerName; b.owner = owner;
        b.createdVersion = BlueprintStore.modVersion(); b.createdAt = System.currentTimeMillis();
        b.sx = sx; b.sy = sy; b.sz = sz;
        b.palette.addAll(palette);
        b.xs = X.toIntArray(); b.ys = Y.toIntArray(); b.zs = Z.toIntArray(); b.ss = S.toIntArray();
        b.bes.putAll(bes);
        // 저장 형식으로 한 번 왕복시켜 검증 (불러올 때와 똑같은 검사를 통과해야 저장)
        List<String> errs = new ArrayList<>();
        Blueprint v = Blueprint.fromTag(b.toTag(), false, errs);
        if (v == null) { result = "§c저장 실패: 검증 오류 — " + String.join(", ", errs); return; }
        v.warnings.addAll(warnings);
        saved = v;
        result = "§a설계도 저장: §f" + name + " §7(" + sx + "×" + sy + "×" + sz + ", 블록 " + v.count() + ", 기계 " + v.machineCount()
                + ", 블록 엔티티 " + v.bes.size() + ")" + (warnings.isEmpty() ? "" : "\n§e경고 " + warnings.size() + "건: " + String.join(" / ", warnings.stream().limit(3).toList()));
    }
}
