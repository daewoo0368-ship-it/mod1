package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.blueprint.core.BpPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 설계도 작업 관리자.
 *  · 틱당 처리량(블록 수 + 시간 예산)을 모든 작업이 나눠 쓴다 → 큰 건축물도 서버가 멈추지 않는다.
 *  · 진행률은 작업한 플레이어의 액션바에 표시.
 *  · 배치 작업의 단계·커서를 월드 폴더(jobs.nbt)에 저장 → 서버가 꺼져도 다음 시작 때 이어서 끝낸다.
 */
public final class BlueprintJobs {
    private BlueprintJobs() {}

    public static int BLOCKS_PER_TICK = 2048;
    public static long NANOS_PER_TICK = 8_000_000L;   // 8ms
    public static final int MAX_PLACE_JOBS = 4;

    static final List<PlaceJob> PLACE = new ArrayList<>();
    static final List<CaptureJob> CAPTURE = new ArrayList<>();
    static final List<Preview> PREVIEWS = new ArrayList<>();
    static final List<Runnable> MAIN = new ArrayList<>();    // IO 스레드 → 서버 스레드
    static final List<Consumer<PlaceJob>> ON_PLACE_DONE = new ArrayList<>();
    static int tick;
    static boolean dirty;
    /** 작업 처리에 쓴 시간 (나노초) — 직전 틱 / 최대값 (서버 부하 확인용). */
    static long lastTickNanos, maxTickNanos;
    static MinecraftServer server;

    record Preview(ServerLevel level, BlockPos min, BlockPos max, List<BlockPos> conflicts, int[] left) {}

    public static void init() {
        ServerStartedEvent.BUS.addListener(e -> {
            server = e.getServer();
            BlueprintStore.open(server);
            resume(server);
            BlueprintSelfTest.onStart(server);
        });
        ServerStoppingEvent.BUS.addListener(e -> {
            persist();
            BlueprintStore.close();
            PLACE.clear(); CAPTURE.clear(); PREVIEWS.clear();
            synchronized (MAIN) { MAIN.clear(); }
            server = null;
        });
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> { if (server != null) tick(); });
    }

    static void runOnMain(Runnable r) { synchronized (MAIN) { MAIN.add(r); } }

    // ───────────── 틱 ─────────────
    static void tick() {
        tick++;
        List<Runnable> todo;
        synchronized (MAIN) { todo = new ArrayList<>(MAIN); MAIN.clear(); }
        for (Runnable r : todo) { try { r.run(); } catch (RuntimeException ex) { JjkMod.LOG.warn("[Dan] 설계도 후처리 오류", ex); } }

        long t0 = System.nanoTime();
        long deadline = t0 + NANOS_PER_TICK;
        int budget = BLOCKS_PER_TICK;
        // 저장(읽기) 작업
        for (Iterator<CaptureJob> it = CAPTURE.iterator(); it.hasNext(); ) {
            CaptureJob j = it.next();
            try { budget -= j.step(Math.max(64, budget / 2), deadline); }
            catch (RuntimeException ex) { j.done = true; j.result = "§c저장 중 오류: " + ex.getMessage(); JjkMod.LOG.warn("[Dan] 설계도 저장 오류", ex); }
            if (j.done) { it.remove(); finishCapture(j); }
            else if (tick % 10 == 0) bar(j.owner, String.format("§b[설계도 저장] §f%s §7%d%% (%d/%d칸)%s", j.name, Math.round(j.progress() * 100), j.cursor, j.total, j.paused ? " · §e청크 로딩 대기" : ""));
        }
        // 배치 작업 (남은 예산을 나눠 씀)
        int active = PLACE.size();
        for (Iterator<PlaceJob> it = PLACE.iterator(); it.hasNext(); ) {
            PlaceJob j = it.next();
            int share = Math.max(32, budget / Math.max(1, active--));
            try { budget -= j.step(share, deadline); }
            catch (RuntimeException ex) {
                JjkMod.LOG.warn("[Dan] 설계도 배치 오류", ex);
                j.result = "§c배치 중 오류: " + ex.getMessage();
                j.cancelled = true;
                com.tubilt.jjk.machine.PhysWorld.release(j.level, j.machines);
                j.phase = PlaceJob.DONE;
            }
            if (j.phase == PlaceJob.DONE) {
                it.remove(); dirty = true;
                msg(j.owner, j.result);
                JjkMod.LOG.info("[Dan] 설계도 배치 끝: {} → {}", j.bp.id, j.result.replaceAll("§.", "").replace('\n', ' '));
                for (Consumer<PlaceJob> c : new ArrayList<>(ON_PLACE_DONE)) c.accept(j);
            } else if (tick % 10 == 0) bar(j.owner, "§b[건축물 소환] §f" + j.status());
        }
        if (!PLACE.isEmpty() || !CAPTURE.isEmpty()) { lastTickNanos = System.nanoTime() - t0; maxTickNanos = Math.max(maxTickNanos, lastTickNanos); }
        if (!PLACE.isEmpty() && tick % 100 == 0) dirty = true;
        if (dirty && tick % 20 == 0) persist();

        // 미리보기 윤곽
        if (tick % 5 == 0) for (Iterator<Preview> it = PREVIEWS.iterator(); it.hasNext(); ) {
            Preview p = it.next();
            p.left()[0] -= 5;
            if (p.left()[0] <= 0) { it.remove(); continue; }
            outline(p.level(), p.min(), p.max(), false);
            for (BlockPos c : p.conflicts()) p.level().sendParticles(ParticleTypes.ANGRY_VILLAGER, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    static void finishCapture(CaptureJob j) {
        if (j.saved == null) { msg(j.owner, j.result); return; }
        String res = j.result;
        BlueprintStore.put(j.saved, err -> runOnMain(() -> {
            if (err == null) msg(j.owner, res + "\n§7파일 저장 완료 (월드별 저장 공간)");
            else msg(j.owner, "§c설계도 파일 저장 실패: " + err + " §7(메모리에는 남아 있음)");
            StructureSpawner.refresh(j.owner);
        }));
        StructureSpawner.refresh(j.owner);
    }

    /** 경계 상자 모서리를 입자로 그린다. */
    static void outline(ServerLevel l, BlockPos a, BlockPos b, boolean selection) {
        double x0 = a.getX(), y0 = a.getY(), z0 = a.getZ(), x1 = b.getX() + 1, y1 = b.getY() + 1, z1 = b.getZ() + 1;
        double len = (x1 - x0 + y1 - y0 + z1 - z0) * 4;
        double step = Math.max(0.5, len / 480);
        var type = selection ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD;
        for (double[] e : new double[][]{{x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}}) {
            double dx = e[3] - e[0], dy = e[4] - e[1], dz = e[5] - e[2], L = Math.sqrt(dx * dx + dy * dy + dz * dz);
            for (double t = 0; t <= L; t += step) {
                double f = L == 0 ? 0 : t / L;
                l.sendParticles(type, e[0] + dx * f, e[1] + dy * f, e[2] + dz * f, 1, 0, 0, 0, 0);
            }
        }
    }

    // ───────────── 작업 조회 ─────────────
    static PlaceJob placeOf(UUID owner) { for (PlaceJob j : PLACE) if (j.owner.equals(owner)) return j; return null; }
    static CaptureJob captureOf(UUID owner) { for (CaptureJob j : CAPTURE) if (j.owner.equals(owner)) return j; return null; }

    static String jobText(UUID owner) {
        PlaceJob p = placeOf(owner);
        if (p != null) return "배치 중: " + p.status().replaceAll("§.", "");
        CaptureJob c = captureOf(owner);
        if (c != null) return String.format("저장 중: %s %d%%", c.name, Math.round(c.progress() * 100));
        return "";
    }

    static float jobFrac(UUID owner) {
        PlaceJob p = placeOf(owner);
        if (p != null) return (float) p.progress();
        CaptureJob c = captureOf(owner);
        return c != null ? (float) c.progress() : -1f;
    }

    // ───────────── 저장 / 재개 ─────────────
    static Path file() { Path d = BlueprintStore.dir(); return d == null ? null : d.resolve("jobs.nbt"); }

    static void persist() {
        dirty = false;
        Path f = file();
        if (f == null) return;
        ListTag list = new ListTag();
        for (PlaceJob j : PLACE) {
            if (j.phase >= PlaceJob.VERIFY) continue;
            CompoundTag t = new CompoundTag();
            t.putString("owner", j.owner.toString()); t.putString("owner_name", j.ownerName);
            t.putString("dim", j.level.dimension().location().toString());
            t.putString("bp", j.bp.id);
            t.putInt("rot", j.tf.rot); t.putBoolean("mirror", j.tf.mirror); t.putInt("policy", j.policy.ordinal());
            t.putIntArray("origin", new int[]{j.origin.getX(), j.origin.getY(), j.origin.getZ()});
            t.putInt("phase", j.phase); t.putInt("cursor", j.cursor); t.putInt("limit", j.limit);
            t.putInt("placed", j.placed); t.putInt("skipped", j.skipped); t.putInt("failed", j.failed);
            t.putBoolean("cancelled", j.cancelled);
            list.add(t);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("format", 1);
        root.put("jobs", list);
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("jobs.nbt.tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { JjkMod.LOG.warn("[Dan] 설계도 작업 상태 저장 실패", e); }
    }

    static void resume(MinecraftServer s) {
        Path f = file();
        if (f == null || !Files.exists(f)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.create(16L * 1024 * 1024));
            ListTag list = root.getListOrEmpty("jobs");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompoundOrEmpty(i);
                try {
                    ResourceLocation dim = ResourceLocation.tryParse(t.getStringOr("dim", ""));
                    ServerLevel lvl = dim == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
                    Blueprint bp = BlueprintStore.get(s, t.getStringOr("bp", ""));
                    int[] o = t.getIntArray("origin").orElse(new int[0]);
                    if (lvl == null || bp == null || o.length != 3) { JjkMod.LOG.warn("[Dan] 설계도 작업 재개 불가 (설계도/차원 없음): {}", t); continue; }
                    PlaceJob j = new PlaceJob(UUID.fromString(t.getStringOr("owner", "")), t.getStringOr("owner_name", "?"), lvl, bp,
                            t.getIntOr("rot", 0), t.getBooleanOr("mirror", false), new BlockPos(o[0], o[1], o[2]), BpPlan.Policy.of(t.getIntOr("policy", 0)));
                    j.phase = Math.max(PlaceJob.CHECK, Math.min(PlaceJob.NETWORK, t.getIntOr("phase", 0)));
                    j.cursor = Math.max(0, t.getIntOr("cursor", 0)); j.limit = Math.max(0, Math.min(bp.count(), t.getIntOr("limit", bp.count())));
                    j.placed = t.getIntOr("placed", 0); j.skipped = t.getIntOr("skipped", 0); j.failed = t.getIntOr("failed", 0);
                    j.cancelled = t.getBooleanOr("cancelled", false);
                    if (j.phase == PlaceJob.CHECK) j.cursor = 0;   // 검사는 처음부터 다시 (월드가 바뀌었을 수 있음)
                    if (j.phase == PlaceJob.PLACE) for (BlockPos m : j.machines) com.tubilt.jjk.machine.PhysWorld.defer(lvl, m);
                    j.warnings.add("서버 재시작 후 이어서 진행");
                    PLACE.add(j);
                    JjkMod.LOG.info("[Dan] 설계도 배치 작업 재개: {} 단계 {} 커서 {}", bp.id, PlaceJob.PHASE[j.phase], j.cursor);
                } catch (RuntimeException ex) { JjkMod.LOG.warn("[Dan] 설계도 작업 재개 실패: {}", t, ex); }
            }
        } catch (Exception e) { JjkMod.LOG.warn("[Dan] 설계도 작업 상태 읽기 실패", e); }
        dirty = true;
    }

    // ───────────── 메시지 ─────────────
    static ServerPlayer player(UUID id) { return server == null ? null : server.getPlayerList().getPlayer(id); }

    static void bar(UUID id, String s) { ServerPlayer p = player(id); if (p != null) p.displayClientMessage(Component.literal(s), true); }

    static void msg(UUID id, String s) {
        ServerPlayer p = player(id);
        if (p == null || s == null) return;
        for (String line : s.split("\n")) p.displayClientMessage(Component.literal(line), false);
    }

    static boolean sameLevel(ServerPlayer p, Level l) { return p.level() == l; }
}
