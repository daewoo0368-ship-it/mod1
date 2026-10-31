package com.tubilt.jjk.machine;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.sim.Sim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 서버 쪽 물리 월드. 차원마다 시뮬레이션 코어({@link Sim}) 하나를 두고,
 * 로드된 기계 블록 엔티티를 부품으로 등록해 이웃과 링크를 만든다 (서버 틱마다 dt=0.05s 로 한 스텝).
 * 청크가 언로드된 기계는 시뮬레이션에서 빠진다(= 정지·저장). 오프라인 시뮬레이션은 후속 단계.
 */
public final class PhysWorld {
    private PhysWorld() {}

    static final double DT = 0.05;

    static final class W { final Sim sim = new Sim(); final Map<Long, MachineBE> bes = new HashMap<>(); int tick; }
    static final Map<ServerLevel, W> WORLDS = new WeakHashMap<>();
    /** 설계도 배치 중인 기계 위치: 건축물이 다 놓이고 블록 엔티티 데이터가 복원될 때까지 시뮬레이션 등록을 미룬다. */
    static final Map<ServerLevel, Set<Long>> DEFERRED = new WeakHashMap<>();

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tickAll());
    }

    // ───────────── 등록 / 해제 ─────────────
    static void register(MachineBE be) {
        ServerLevel lvl = (ServerLevel) be.getLevel();
        Set<Long> held = DEFERRED.get(lvl);
        if (held != null && held.contains(be.getBlockPos().asLong())) return;
        W w = WORLDS.computeIfAbsent(lvl, k -> new W());
        long key = be.getBlockPos().asLong();
        MachineBE old = w.bes.put(key, be);
        if (old != null && old != be) detach(w, old);
        if (be.part != null) return;
        be.part = be.block().createPart();
        be.push();
        w.sim.add(be.part);
        for (Direction d : Direction.values()) {
            MachineBE nb = w.bes.get(be.getBlockPos().relative(d).asLong());
            if (nb != null && nb != be && nb.part != null) tryLink(w, be, nb, d);
        }
        be.lastSentTick = -999;
    }

    static void unregister(MachineBE be) {
        if (!(be.getLevel() instanceof ServerLevel lvl)) return;
        W w = WORLDS.get(lvl); if (w == null) return;
        if (w.bes.get(be.getBlockPos().asLong()) == be) w.bes.remove(be.getBlockPos().asLong());
        detach(w, be);
    }

    private static void detach(W w, MachineBE be) {
        if (be.part == null) return;
        be.pull();
        w.sim.remove(be.part);
        be.part = null;
    }

    /** 이웃 연결 규칙: 같은 축끼리만. 축 방향으로 이웃하면 커플링, 축에 수직으로 이웃한 두 기어는 잇수 합이 40 이면 맞물림. */
    private static void tryLink(W w, MachineBE a, MachineBE b, Direction d) {
        BlockState sa = a.getBlockState(), sb = b.getBlockState();
        Direction.Axis xa = sa.getValue(MachineBlock.AXIS), xb = sb.getValue(MachineBlock.AXIS);
        if (xa != xb) return;
        MachineBlock ba = a.block(), bb = b.block();
        var l = com.tubilt.jjk.sim.GridRules.link(a.part.ports().get(0), b.part.ports().get(0),
                xa.ordinal(), xb.ordinal(), d.getAxis().ordinal(), ba.teeth(), bb.teeth());
        if (l != null) w.sim.link(l);
    }

    // ───────────── 설계도 배치 연동 ─────────────
    /** 이 위치의 기계는 {@link #release} 전까지 네트워크에 넣지 않는다. */
    public static void defer(ServerLevel lvl, BlockPos pos) {
        DEFERRED.computeIfAbsent(lvl, k -> new HashSet<>()).add(pos.asLong());
    }

    /**
     * 보류를 풀고 기계를 등록한다 → 이웃 탐색·링크(커플링/기어 맞물림) → 다음 틱 솔버가 네트워크·기어비를 다시 계산.
     * 이미 등록된 기계는 건드리지 않는다(재시작 후 재개 등에서 여러 번 불러도 안전).
     */
    public static int release(ServerLevel lvl, Collection<BlockPos> positions) {
        Set<Long> held = DEFERRED.get(lvl);
        if (held != null) {
            for (BlockPos p : positions) held.remove(p.asLong());
            if (held.isEmpty()) DEFERRED.remove(lvl);
        }
        int n = 0;
        for (BlockPos p : positions) {
            if (lvl.isLoaded(p) && lvl.getBlockEntity(p) instanceof MachineBE be && !be.isRemoved()) { register(be); n++; }
        }
        return n;
    }

    /** 위치 목록에 있는 기계들의 네트워크 상태. */
    public record Stats(int machines, int registered, int nets, int jammed, double maxRpm) {}

    public static Stats stats(ServerLevel lvl, Collection<BlockPos> positions) {
        int machines = 0, reg = 0, jam = 0; double max = 0;
        Set<com.tubilt.jjk.sim.Net> nets = new HashSet<>();
        for (BlockPos p : positions) {
            if (!(lvl.getBlockEntity(p) instanceof MachineBE be)) continue;
            machines++;
            if (be.part == null) continue;
            reg++;
            com.tubilt.jjk.sim.Net n = be.part.net();
            if (n != null && nets.add(n) && n.jammed) jam++;
            max = Math.max(max, Math.abs(be.part.omega) * 60 / (2 * Math.PI));
        }
        return new Stats(machines, reg, nets.size(), jam, max);
    }

    // ───────────── 틱 ─────────────
    static void tickAll() {
        for (Map.Entry<ServerLevel, W> en : new ArrayList<>(WORLDS.entrySet())) {
            ServerLevel lvl = en.getKey(); W w = en.getValue();
            if (w.bes.isEmpty()) continue;
            w.tick++;
            try { w.sim.step(DT); } catch (RuntimeException ex) { JjkMod.LOG.warn("[Dan] 물리 스텝 오류", ex); continue; }
            if (!w.sim.events.isEmpty()) {
                for (String s : w.sim.events) {
                    JjkMod.LOG.info("[Dan] {}", s);
                    for (var p : lvl.players()) p.displayClientMessage(Component.literal("§c⚠ " + s), false);
                }
                w.sim.events.clear();
            }
            List<MachineBE> broken = null;
            for (MachineBE be : w.bes.values()) {
                if (be.part == null) continue;
                if (be.part.broken) { if (broken == null) broken = new ArrayList<>(); broken.add(be); continue; }
                double om = be.part.omega;
                boolean changed = Double.isNaN(be.lastSentOmega) || Math.abs(om - be.lastSentOmega) > 0.002 * Math.max(1.0, Math.abs(om));
                int since = w.tick - be.lastSentTick;
                if ((changed && since >= 2) || (om != 0 && since >= 40) || since < 0) {
                    be.pull(); be.setChanged();
                    BlockState st = be.getBlockState();
                    lvl.sendBlockUpdated(be.getBlockPos(), st, st, 3);
                    be.lastSentOmega = om; be.lastSentTick = w.tick;
                }
            }
            if (broken != null) for (MachineBE be : broken) {
                BlockPos pos = be.getBlockPos();
                lvl.destroyBlock(pos, true);    // 파손 → 블록이 부서지고 아이템이 떨어진다 (setRemoved → 부품 제거 → 네트워크 분리)
            }
        }
    }
}
