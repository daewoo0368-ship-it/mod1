package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.blueprint.core.BpPlan;
import com.tubilt.jjk.machine.MachineBE;
import com.tubilt.jjk.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 실제 Minecraft 서버 안에서 돌리는 건축물 소환기 자체 검증. 환경 변수 JJK_SELFTEST=1 / 2 일 때만 동작한다.
 *  · 1단계: 고정 설계도 로딩, 회전·반전 8가지 배치 + 기계 네트워크·기어 회전, 블록 엔티티 저장/복원, 충돌 정책, 권한(가짜 플레이어),
 *           잘못된 데이터, 대형 배치·취소, 파일 저장, 바닐라 자동화(제련·조립·물류) 실제 작동 → 대형 배치 도중에 서버를 끈다
 *  · 2단계(재시작 후): 사용자 설계도 유지, 중단된 배치가 이어서 끝까지 완료되는지, 기계 상태 유지
 * 결과는 서버 작업 폴더의 jjk-selftest-&lt;단계&gt;.txt 에 쓰고 서버를 종료한다.
 */
final class BlueprintSelfTest {
    private BlueprintSelfTest() {}

    static String phase;
    static MinecraftServer server;
    static ServerLevel lvl;
    static final List<String> out = new ArrayList<>();
    static int pass, fail, waited, total;
    static boolean registered, finished;
    static final ArrayDeque<BooleanSupplier> steps = new ArrayDeque<>();
    static final Map<String, Object> ctx = new HashMap<>();
    static final int Y = 0;

    static void onStart(MinecraftServer s) {
        String v = System.getenv("JJK_SELFTEST");
        if (v == null || v.isBlank()) return;
        phase = v.trim(); server = s; lvl = s.overworld();
        out.clear(); steps.clear(); ctx.clear(); pass = fail = waited = total = 0; finished = false;
        log("=== 건축물 소환기 자체 검증 — 단계 " + phase + " (모드 " + BlueprintStore.modVersion() + ") ===");
        if (phase.equals("1")) plan1(); else plan2();
        if (!registered) { registered = true; TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tick()); }
    }

    static void tick() {
        if (finished || server == null || phase == null) return;
        total++;
        if (total > 14000) { check("전체 시간 제한", false, "14000틱 초과"); finish(); return; }
        BooleanSupplier st = steps.peek();
        if (st == null) { finish(); return; }
        try { if (st.getAsBoolean()) { steps.poll(); waited = 0; } else waited++; }
        catch (Throwable t) { check("단계 실행 중 예외 없음", false, t.toString()); JjkMod.LOG.error("[selftest] 예외", t); steps.poll(); waited = 0; }
    }

    // ───────────── 도우미 ─────────────
    static void log(String s) { out.add(s); JjkMod.LOG.info("[selftest] {}", s); }
    static void check(String name, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log("  [" + (ok ? "PASS" : "FAIL") + "] " + name + (detail == null || detail.isEmpty() ? "" : "  (" + detail + ")"));
    }
    static void step(Runnable r) { steps.add(() -> { r.run(); return true; }); }
    static void waitTicks(int n) { steps.add(() -> waited >= n); }
    static void until(String what, int timeout, BooleanSupplier cond) {
        steps.add(() -> {
            if (cond.getAsBoolean()) return true;
            if (waited >= timeout) { check(what + " (제한 " + timeout + "틱)", false, "시간 초과"); return true; }
            return false;
        });
    }
    static boolean done(PlaceJob j) { return j.phase == PlaceJob.DONE; }

    static void force(int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) { lvl.setChunkForced(cx, cz, true); lvl.getChunk(cx, cz); }
    }

    static Blueprint fixed(String id) { return BlueprintStore.fixed(server).get(id); }

    static PlaceJob place(Blueprint b, int rot, boolean mirror, BlockPos origin, BpPlan.Policy pol) {
        PlaceJob j = new PlaceJob(new java.util.UUID(0, 1), "selftest", lvl, b, rot, mirror, origin, pol);
        BlueprintJobs.PLACE.add(j);
        return j;
    }

    static int indexOf(Blueprint b, String blockId) {
        for (int i = 0; i < b.count(); i++) if (key(b.state(i)).equals(blockId)) return i;
        return -1;
    }
    static String key(BlockState s) { return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(); }

    static List<BlockPos> find(PlaceJob j, java.util.function.Predicate<BlockState> p) {
        List<BlockPos> l = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(j.min, j.max)) if (p.test(lvl.getBlockState(q))) l.add(q.immutable());
        return l;
    }
    static void put(BlockPos p, ItemStack s) {
        if (!(lvl.getBlockEntity(p) instanceof Container c)) return;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) { c.setItem(i, s); return; }
    }
    static int count(PlaceJob j, Item item) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(j.min, j.max))
            if (lvl.getBlockEntity(q) instanceof Container c)   // 호퍼 안에서 이동 중인 것도 센다
                for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /** 속이 빈 대형 시험 건축물: 6칸마다 바닥, 바깥벽. */
    static Blueprint shell(String id, int sx, int sy, int sz) {
        Blueprint b = new Blueprint();
        b.id = id; b.name = "대형 시험 " + sx + "×" + sy + "×" + sz; b.author = "selftest"; b.sx = sx; b.sy = sy; b.sz = sz;
        b.palette.add(Blocks.STONE_BRICKS.defaultBlockState()); b.palette.add(Blocks.GLASS.defaultBlockState());
        it.unimi.dsi.fastutil.ints.IntArrayList X = new it.unimi.dsi.fastutil.ints.IntArrayList(), Yl = new it.unimi.dsi.fastutil.ints.IntArrayList(), Z = new it.unimi.dsi.fastutil.ints.IntArrayList(), S = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (int y = 0; y < sy; y++) for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            boolean floor = y % 6 == 0, wall = x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
            if (!floor && !wall) continue;
            X.add(x); Yl.add(y); Z.add(z); S.add(floor ? 0 : 1);
        }
        b.xs = X.toIntArray(); b.ys = Yl.toIntArray(); b.zs = Z.toIntArray(); b.ss = S.toIntArray();
        return b;
    }

    static Path file(String name) { return Path.of(name); }

    static void finish() {
        if (finished) return;
        finished = true;
        log("RESULT: " + (fail == 0 ? "PASS" : "FAIL") + " (" + pass + " 통과, " + fail + " 실패, " + total + "틱)");
        try { Files.write(file("jjk-selftest-" + phase + ".txt"), out, StandardCharsets.UTF_8); } catch (Exception e) { JjkMod.LOG.error("[selftest] 결과 저장 실패", e); }
        server.halt(false);
    }

    // ───────────── 1단계 ─────────────
    static void plan1() {
        step(() -> force(-16, -64, 600, 140));

        step(() -> {
            log("T1 고정 설계도 불러오기");
            Map<String, Blueprint> f = BlueprintStore.fixed(server);
            check("고정 설계도 6개", f.size() == 6, f.keySet().toString());
            check("고정 설계도 오류 없음", BlueprintStore.errors().isEmpty(), String.join(" / ", BlueprintStore.errors()));
            for (String id : new String[]{"jjk:test_power_room", "jjk:steam_power_plant", "jjk:automatic_smelting_factory", "jjk:large_manufacturing_factory", "jjk:logistics_center", "jjk:world_engine"}) {
                Blueprint b = f.get(id);
                check(id, b != null && b.warnings.isEmpty() && b.count() > 0, b == null ? "없음" : "블록 " + b.count() + ", 경고 " + b.warnings);
            }
        });

        // T2 회전·반전 8가지
        step(() -> {
            log("T2 회전(0/90/180/270°)·좌우 반전 배치 + 기계 네트워크 재구성");
            Blueprint b = fixed("jjk:test_power_room");
            List<PlaceJob> js = new ArrayList<>();
            for (int k = 0; k < 8; k++) js.add(place(b, k % 4, k >= 4, new BlockPos(k * 20, Y, 0), BpPlan.Policy.SAFE));
            ctx.put("rooms", js);
        });
        until("동력실 8개 배치 완료", 1200, () -> rooms().stream().allMatch(BlueprintSelfTest::done));
        waitTicks(60);
        step(() -> {
            Blueprint b = fixed("jjk:test_power_room");
            List<PlaceJob> js = rooms();
            int motor = indexOf(b, "jjk:mech_motor");
            double[] base = null;
            for (PlaceJob j : js) {
                String tag = j.tf.label();
                check(tag + ": 블록 전부 배치", !j.cancelled && j.placed == b.count() && j.failed == 0, "놓음 " + j.placed + "/" + b.count() + " 실패 " + j.failed);
                check(tag + ": 네트워크 재구성 (부품 12, 네트워크 1, 잠김 0)", j.stats != null && j.stats.registered() == 12 && j.stats.machines() == 12 && j.stats.nets() == 1 && j.stats.jammed() == 0, String.valueOf(j.stats));
                double[] w = new double[b.count()];
                boolean axisOk = true;
                for (int i = 0; i < b.count(); i++) {
                    if (!(b.state(i).getBlock() instanceof MachineBlock)) continue;
                    BlockPos p = j.pos(i);
                    if (lvl.getBlockEntity(p) instanceof MachineBE be) w[i] = be.simOmega();
                    int want = j.tf.axis(b.state(i).getValue(MachineBlock.AXIS).ordinal());
                    if (lvl.getBlockState(p).getValue(MachineBlock.AXIS).ordinal() != want) axisOk = false;
                }
                check(tag + ": 회전축 방향 변환", axisOk, "");
                double wm = w[motor];
                check(tag + ": 기어가 실제로 돈다", Math.abs(wm) > 0.5, String.format("전동기 %.2f rad/s", wm));
                int fm = j.tf.spinFactor(b.state(motor).getValue(MachineBlock.AXIS).ordinal());
                if (base == null) { base = w; continue; }
                double worst = 0;
                for (int i = 0; i < b.count(); i++) {
                    if (!(b.state(i).getBlock() instanceof MachineBlock) || base[motor] == 0 || wm == 0) continue;
                    int fi = j.tf.spinFactor(b.state(i).getValue(MachineBlock.AXIS).ordinal());
                    double want = base[i] / base[motor] * fi / fm, got = w[i] / wm;
                    worst = Math.max(worst, Math.abs(got - want) / Math.max(1e-6, Math.abs(want)));
                }
                check(tag + ": 기어비·회전 방향이 회전 전과 동일", worst < 1e-6 && Math.signum(wm) == fm * Math.signum(base[motor]), String.format("최대 오차 %.1e", worst));
            }
        });

        // T3 블록 엔티티 저장·복원
        step(() -> {
            log("T3 사용자 설계도 저장 (블록 엔티티·기계 상태·금지 블록)");
            PlaceJob j0 = rooms().get(0);
            Blueprint b = fixed("jjk:test_power_room");
            BlockPos motor = j0.pos(indexOf(b, "jjk:mech_motor"));
            if (lvl.getBlockEntity(motor) instanceof MachineBE be) be.setMotor(0.5, -1);
            BlockPos barrel = j0.origin.offset(1, 1, 1), cmd = j0.origin.offset(1, 1, 2);
            lvl.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 3);
            put(barrel, new ItemStack(Items.IRON_INGOT, 5));
            put(barrel, new ItemStack(Items.WRITTEN_BOOK));
            put(barrel, new ItemStack(Items.COMMAND_BLOCK));
            lvl.setBlock(cmd, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
            CaptureJob c = new CaptureJob(new java.util.UUID(0, 1), "selftest", lvl, j0.min, j0.max, BlueprintStore.newUserId(), "selftest 저장본");
            BlueprintJobs.CAPTURE.add(c);
            ctx.put("cap", c);
        });
        until("설계도 저장 완료", 400, () -> ((CaptureJob) ctx.get("cap")).done);
        step(() -> {
            CaptureJob c = (CaptureJob) ctx.get("cap");
            Blueprint s = c.saved;
            check("저장 성공", s != null, c.result);
            if (s == null) return;
            check("블록 수 = 동력실 128 + 통 1 (명령 블록 제외)", s.count() == 129, "블록 " + s.count());
            check("명령 블록 저장 안 됨", indexOf(s, "minecraft:command_block") < 0, "");
            int mi = indexOf(s, "jjk:mech_motor"), bi = indexOf(s, "minecraft:barrel");
            CompoundTag mt = s.bes.get(mi), bt = s.bes.get(bi);
            check("전동기 상태 저장 (스로틀 0.5, 방향 −1)", mt != null && Math.abs(mt.getDoubleOr("throttle", 0) - 0.5) < 1e-9 && mt.getIntOr("dir", 0) == -1, String.valueOf(mt));
            ListTag items = bt == null ? new ListTag() : bt.getListOrEmpty("Items");
            check("통 내용물 저장 — 철 주괴만 (책·명령 블록 제외)", items.size() == 1 && items.getCompoundOrEmpty(0).getStringOr("id", "").equals("minecraft:iron_ingot"), String.valueOf(bt));
            check("저장 시 월드 불변 (명령 블록 그대로)", lvl.getBlockState(rooms().get(0).origin.offset(1, 1, 2)).is(Blocks.COMMAND_BLOCK), "");
            check("경고에 제외 항목 기록", s.warnings.stream().anyMatch(w -> w.contains("command_block")), String.join(" / ", s.warnings));
            ctx.put("saved", s);
            ctx.put("placeSaved", place(s, 2, false, new BlockPos(180, Y, 0), BpPlan.Policy.SAFE));
        });
        until("저장본 180° 배치 완료", 400, () -> done((PlaceJob) ctx.get("placeSaved")));
        step(() -> {
            log("T4 사용자 설계도 복원 (180° 회전)");
            PlaceJob j = (PlaceJob) ctx.get("placeSaved");
            Blueprint s = (Blueprint) ctx.get("saved");
            check("복원: 블록 전부 배치", j.placed == s.count() && j.failed == 0, "놓음 " + j.placed + "/" + s.count());
            check("복원: 블록 엔티티 복원 실패 없음", j.beFailed == 0 && j.beRestored >= 2, "복원 " + j.beRestored + " 실패 " + j.beFailed + " " + j.warnings);
            BlockPos barrel = j.pos(indexOf(s, "minecraft:barrel"));
            int iron = 0, other = 0;
            if (lvl.getBlockEntity(barrel) instanceof Container c)
                for (int i = 0; i < c.getContainerSize(); i++) { ItemStack it = c.getItem(i); if (it.is(Items.IRON_INGOT)) iron += it.getCount(); else if (!it.isEmpty()) other++; }
            check("복원: 통 내용물 (철 주괴 5, 다른 것 없음)", iron == 5 && other == 0, "철 " + iron + " 기타 " + other);
            int mi = indexOf(s, "jjk:mech_motor");
            int f = j.tf.spinFactor(s.state(mi).getValue(MachineBlock.AXIS).ordinal());
            if (lvl.getBlockEntity(j.pos(mi)) instanceof MachineBE be)
                check("복원: 전동기 스로틀 0.5 · 방향 = −1 × 회전 부호(" + f + ")", Math.abs(be.motorThrottle() - 0.5) < 1e-9 && be.motorDir() == -f, "스로틀 " + be.motorThrottle() + " 방향 " + be.motorDir());
            else check("복원: 전동기 블록 엔티티", false, "없음");
            check("복원: 네트워크 (부품 12, 잠김 0)", j.stats != null && j.stats.registered() == 12 && j.stats.jammed() == 0, String.valueOf(j.stats));
        });

        // T5 충돌·권한
        step(() -> {
            log("T5 기존 블록 충돌 검사 · 덮어쓰기 권한 · 멀티플레이 권한");
            Blueprint b = fixed("jjk:test_power_room");
            ctx.put("safe", place(b, 0, false, rooms().get(0).origin, BpPlan.Policy.SAFE));
            ctx.put("air", place(b, 0, false, rooms().get(0).origin.offset(3, 0, 0), BpPlan.Policy.AIR_ONLY));
        });
        until("충돌 시험 배치 완료", 400, () -> done((PlaceJob) ctx.get("safe")) && done((PlaceJob) ctx.get("air")));
        step(() -> {
            PlaceJob s = (PlaceJob) ctx.get("safe"), a = (PlaceJob) ctx.get("air");
            check("안전 배치: 기존 건축물 위 → 거부, 아무것도 놓지 않음", s.cancelled && s.placed == 0 && s.result.contains("거부"), s.result.replaceAll("§.", ""));
            check("빈 공간만: 일부만 배치하고 나머지 건너뜀", a.placed > 0 && a.skipped > 0, "놓음 " + a.placed + " 건너뜀 " + a.skipped);

            FakePlayer fp = FakePlayerFactory.getMinecraft(lvl);
            fp.removeTag("jjk.blueprint"); fp.removeTag("jjk.blueprint.admin");
            fp.setPos(300.5, Y, 100.5);
            StructureSpawner.STATES.remove(fp.getUUID());
            StructureSpawner.handle(fp, StructureSpawner.OP_SPAWN, "jjk:test_power_room", "");
            check("권한 없는 플레이어: 서버가 소환 거부", BlueprintJobs.placeOf(fp.getUUID()) == null && !StructureSpawner.STATES.containsKey(fp.getUUID()), "");
            fp.addTag("jjk.blueprint");
            StructureSpawner.handle(fp, StructureSpawner.OP_SETTINGS, "", "0,0,2,1,0,0,0");
            check("사용 권한만 있는 플레이어: 덮어쓰기 설정 거부 → 안전 배치 유지", StructureSpawner.st(fp).policy == BpPlan.Policy.SAFE.ordinal() && StructureSpawner.st(fp).anchor == 1, "정책 " + StructureSpawner.st(fp).policy);
            StructureSpawner.handle(fp, StructureSpawner.OP_SPAWN, "jjk:test_power_room", "");
            PlaceJob fj = BlueprintJobs.placeOf(fp.getUUID());
            check("사용 권한 있는 플레이어: 서버 요청 경로로 소환 작업 생성", fj != null, "");
            ctx.put("fake", fj);
            StructureSpawner.handle(fp, StructureSpawner.OP_SPAWN, "jjk:test_power_room", "");
            check("소환 작업 중복 방지 (한 사람당 1개)", BlueprintJobs.PLACE.stream().filter(x -> x.owner.equals(fp.getUUID())).count() == 1, "");
            fp.addTag("jjk.blueprint.admin");
            StructureSpawner.handle(fp, StructureSpawner.OP_SETTINGS, "", "0,0,2,1,0,0,0");
            check("개발자 권한: 덮어쓰기 설정 허용", StructureSpawner.st(fp).policy == BpPlan.Policy.OVERWRITE.ordinal(), "");
            StructureSpawner.handle(fp, StructureSpawner.OP_RENAME, "jjk:test_power_room", "해킹");
            check("고정 설계도는 이름 변경 불가", fixed("jjk:test_power_room").name.equals("시험용 동력실"), "");
            fp.removeTag("jjk.blueprint"); fp.removeTag("jjk.blueprint.admin");
            ctx.put("over", place(b, 0, false, rooms().get(0).origin, BpPlan.Policy.OVERWRITE));
        });
        until("가짜 플레이어 소환·덮어쓰기 완료", 600, () -> (ctx.get("fake") == null || done((PlaceJob) ctx.get("fake"))) && done((PlaceJob) ctx.get("over")));
        step(() -> {
            PlaceJob f = (PlaceJob) ctx.get("fake"), o = (PlaceJob) ctx.get("over");
            if (f != null) check("가짜 플레이어 소환 완료", f.placed == f.bp.count() && !f.cancelled, "놓음 " + f.placed);
            check("덮어쓰기: 기존 건축물 위에 그대로 배치", !o.cancelled && o.placed == o.bp.count(), "놓음 " + o.placed + " " + o.result.replaceAll("§.", ""));
        });

        // T6 잘못된 데이터
        step(() -> {
            log("T6 잘못된 설계도 데이터 처리");
            List<String> e = new ArrayList<>();
            CompoundTag t = new CompoundTag(); t.putInt("format", 99);
            check("포맷 버전 99 → 거부", Blueprint.fromTag(t, false, e) == null && !e.isEmpty(), String.join(",", e));
            e.clear(); t = base(3, 1, 1); t.putIntArray("blocks", new int[]{0, 0, 0, 0, 1});
            check("블록 배열 손상 → 거부", Blueprint.fromTag(t, false, e) == null, String.join(",", e));
            e.clear(); t = base(0, 1, 1);
            check("크기 0 → 거부", Blueprint.fromTag(t, false, e) == null, String.join(",", e));
            e.clear(); t = base(300, 1, 1);
            check("크기 제한 초과 → 거부", Blueprint.fromTag(t, false, e) == null, String.join(",", e));
            e.clear(); t = base(3, 1, 1);
            ListTag pal = new ListTag();
            pal.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
            CompoundTag unk = new CompoundTag(); unk.putString("Name", "minecraft:not_a_block"); pal.add(unk);
            pal.add(NbtUtils.writeBlockState(Blocks.COMMAND_BLOCK.defaultBlockState()));
            pal.add(NbtUtils.writeBlockState(Blocks.CHEST.defaultBlockState()));
            t.put("palette", pal);
            t.putIntArray("blocks", new int[]{0, 0, 0, 0, 1, 0, 0, 1, 2, 0, 0, 2, 0, 0, 0, 0, 5, 0, 0, 0, 1, 0, 0, 9, 2, 0, 0, 3});
            ListTag bes = new ListTag();
            CompoundTag cb = new CompoundTag(); cb.putInt("i", 2); CompoundTag cbn = new CompoundTag(); cbn.putString("id", "minecraft:command_block"); cbn.putString("Command", "op @a"); cb.put("nbt", cbn); bes.add(cb);
            CompoundTag ch = new CompoundTag(); ch.putInt("i", 6); CompoundTag chn = new CompoundTag(); chn.putString("id", "minecraft:chest"); chn.putString("LootTable", "minecraft:chests/end_city_treasure"); chn.putLong("LootTableSeed", 1); ch.put("nbt", chn); bes.add(ch);
            t.put("block_entities", bes);
            Blueprint b = Blueprint.fromTag(t, false, e);
            check("모르는 블록·금지 블록·범위 밖·중복·팔레트 오류는 해당 항목만 제외", b != null && b.count() == 2, b == null ? String.join(",", e) : "블록 " + b.count() + " 경고 " + b.warnings);
            check("상자의 루트 테이블·시드 제외 (허용 키만)", b != null && b.bes.values().stream().noneMatch(n -> n.contains("LootTable") || n.contains("LootTableSeed") || n.contains("Command")), b == null ? "" : String.valueOf(b.bes));
            CompoundTag mt = new CompoundTag(); mt.putString("id", "jjk:machine"); mt.put("throttle", DoubleTag.valueOf(Double.NaN)); mt.putDouble("omega", 1e30); mt.putInt("dir", -5);
            CompoundTag cl = BlueprintFilter.sanitize(mt, new ArrayList<>());
            check("기계 상태 숫자 검증 (NaN → 기본값, 범위 제한)", cl != null && cl.getDoubleOr("throttle", -1) == 1.0 && cl.getDoubleOr("omega", 0) == 2000 && cl.getIntOr("dir", 0) == -1, String.valueOf(cl));
            try {
                Path d = BlueprintStore.dir().resolve("user");
                Files.createDirectories(d);
                Path bad = d.resolve("deadbeef0000.nbt");
                Files.write(bad, new byte[]{1, 2, 3, 4, 5, 6, 7});
                int before = BlueprintStore.userErrors.size();
                Blueprint rb = BlueprintStore.readUser(bad);
                check("손상된 설계도 파일 → 안전하게 건너뜀", rb == null && BlueprintStore.userErrors.size() == before + 1, "");
                Files.deleteIfExists(bad);
                BlueprintStore.userErrors.clear();
            } catch (Exception ex) { check("손상된 설계도 파일 처리", false, ex.toString()); }
        });

        // T7 대형 배치 · 취소
        step(() -> {
            log("T7 대형 건축물 배치 (여러 틱 분할) · 배치 중 취소");
            BlueprintJobs.maxTickNanos = 0;
            Blueprint big = shell("user:selftestbig1", 96, 24, 96);
            ctx.put("big", place(big, 0, false, new BlockPos(200, Y, 0), BpPlan.Policy.SAFE));
            ctx.put("cancel", place(big, 0, false, new BlockPos(320, Y, 0), BpPlan.Policy.SAFE));
            log("  대형 시험 건축물 블록 " + big.count());
        });
        until("취소 시험 작업 배치 시작", 400, () -> ((PlaceJob) ctx.get("cancel")).phase == PlaceJob.PLACE && ((PlaceJob) ctx.get("cancel")).placed > 500);
        step(() -> ((PlaceJob) ctx.get("cancel")).cancel());
        until("대형 배치 완료", 4000, () -> done((PlaceJob) ctx.get("big")) && done((PlaceJob) ctx.get("cancel")));
        step(() -> {
            PlaceJob b = (PlaceJob) ctx.get("big"), c = (PlaceJob) ctx.get("cancel");
            check("대형: 블록 전부 배치", b.placed == b.bp.count() && b.failed == 0, "놓음 " + b.placed + "/" + b.bp.count());
            check("대형: 여러 틱에 나눠 배치", b.ticks > 10, b.ticks + "틱");
            check("대형: 틱당 작업 시간 제한 (< 40ms)", BlueprintJobs.maxTickNanos < 40_000_000L, String.format("최대 %.1fms", BlueprintJobs.maxTickNanos / 1e6));
            int miss = 0;
            for (int i = 0; i < b.bp.count(); i += 37) if (lvl.getBlockState(b.pos(i)) != b.tstates[b.bp.ss[i]]) miss++;
            check("대형: 월드 표본 검사 (누락 없음)", miss == 0, "누락 " + miss);
            check("취소: 일부만 놓고 중단", c.cancelled && c.placed > 0 && c.placed < c.bp.count() && c.result.contains("취소"), "놓음 " + c.placed + "/" + c.bp.count());
        });

        // T8 파일 저장 → 디스크에서 다시 읽기
        step(() -> {
            log("T8 사용자 설계도 파일 저장 · 다시 읽기");
            Blueprint s = (Blueprint) ctx.get("saved");
            if (s == null) { check("저장본 있음", false, ""); return; }
            CompoundTag before = s.toTag();
            Blueprint r = BlueprintStore.reloadFromDisk(s.id);
            check("디스크에서 다시 읽은 설계도가 같음", r != null && r.toTag().equals(before), r == null ? "없음" : "블록 " + r.count());
        });

        // T9 바닐라 자동화 실제 작동 + 기타 고정 설계도
        step(() -> {
            log("T9 고정 설계도 실제 작동 (제련·조립·물류·발전소·월드 엔진)");
            ctx.put("smelt", place(fixed("jjk:automatic_smelting_factory"), 0, false, new BlockPos(0, Y, -40), BpPlan.Policy.SAFE));
            ctx.put("factory", place(fixed("jjk:large_manufacturing_factory"), 0, false, new BlockPos(20, Y, -40), BpPlan.Policy.SAFE));
            ctx.put("logi", place(fixed("jjk:logistics_center"), 0, false, new BlockPos(40, Y, -40), BpPlan.Policy.SAFE));
            ctx.put("steam", place(fixed("jjk:steam_power_plant"), 1, false, new BlockPos(60, Y, -40), BpPlan.Policy.SAFE));
            ctx.put("engine", place(fixed("jjk:world_engine"), 0, false, new BlockPos(100, Y, -64), BpPlan.Policy.SAFE));
        });
        until("고정 설계도 5개 배치 완료", 800, () -> List.of("smelt", "factory", "logi", "steam", "engine").stream().allMatch(k -> done((PlaceJob) ctx.get(k))));
        step(() -> {
            for (String k : List.of("smelt", "factory", "logi", "steam", "engine")) {
                PlaceJob j = (PlaceJob) ctx.get(k);
                check(j.bp.name + ": 블록 전부 배치", !j.cancelled && j.placed == j.bp.count() && j.failed == 0 && j.beFailed == 0, "놓음 " + j.placed + "/" + j.bp.count() + " BE 실패 " + j.beFailed);
            }
            PlaceJob steam = (PlaceJob) ctx.get("steam");
            List<BlockPos> bf = find(steam, s -> s.is(Blocks.BLAST_FURNACE));
            check("증기 발전소 90°: 보일러 방향 동→남", !bf.isEmpty() && bf.stream().allMatch(p -> lvl.getBlockState(p).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH), bf.size() + "기");
            PlaceJob eng = (PlaceJob) ctx.get("engine");
            check("월드 엔진: 동력 네트워크 (부품 24, 네트워크 2, 잠김 0)", eng.stats != null && eng.stats.registered() == 24 && eng.stats.nets() == 2 && eng.stats.jammed() == 0, String.valueOf(eng.stats));
            check("증기 발전소: 회전계 네트워크 (부품 5, 잠김 0)", steam.stats != null && steam.stats.registered() == 5 && steam.stats.jammed() == 0, String.valueOf(steam.stats));
            // 원료·연료 투입
            for (String k : List.of("smelt", "factory")) {
                PlaceJob j = (PlaceJob) ctx.get(k);
                Item raw = k.equals("smelt") ? Items.RAW_IRON : Items.SAND;
                int in = 0, fuel = 0;
                for (BlockPos p : find(j, s -> s.is(Blocks.BARREL))) {
                    BlockState below = lvl.getBlockState(p.below());
                    if (!below.is(Blocks.HOPPER)) continue;
                    Direction f = below.getValue(HopperBlock.FACING);
                    BlockState target = lvl.getBlockState(p.below().relative(f));
                    if (!target.is(Blocks.FURNACE)) continue;
                    if (f == Direction.DOWN) { put(p, new ItemStack(raw, 3)); in++; }
                    else { put(p, new ItemStack(Items.COAL, 4)); fuel++; }
                }
                log("  " + j.bp.name + ": 원료 통 " + in + " · 연료 통 " + fuel + "에 투입");
                ctx.put(k + "_in", in);
            }
            PlaceJob fac = (PlaceJob) ctx.get("factory");
            BlockPos crafter = find(fac, s -> s.is(Blocks.CRAFTER)).stream().findFirst().orElse(null);
            int fed = 0;
            if (crafter != null) for (Direction d : Direction.values()) {
                BlockPos h = crafter.relative(d);
                BlockState hs = lvl.getBlockState(h);
                if (hs.is(Blocks.HOPPER) && hs.getValue(HopperBlock.FACING) == d.getOpposite()) { put(h, new ItemStack(Items.GLASS, 6)); fed++; }
            }
            check("대형 제조 공장: 크래프터 공급 호퍼 찾음", fed == 1, "");
            PlaceJob logi = (PlaceJob) ctx.get("logi");
            BlockPos top = find(logi, s -> s.is(Blocks.BARREL)).stream().max(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(null);
            if (top != null) for (int i = 0; i < 4; i++) put(top, new ItemStack(Items.COBBLESTONE, 64));
            ctx.put("logiTop", top);
        });
        waitTicks(300);
        step(() -> {
            PlaceJob fac = (PlaceJob) ctx.get("factory");
            check("대형 제조 공장: 크래프터 자동 조립 (유리 6 → 유리판)", count(fac, Items.GLASS_PANE) >= 16, "유리판 " + count(fac, Items.GLASS_PANE));
        });
        waitTicks(700);
        step(() -> {
            PlaceJob sm = (PlaceJob) ctx.get("smelt"), fac = (PlaceJob) ctx.get("factory"), logi = (PlaceJob) ctx.get("logi");
            check("자동 제련 공장: 원료 → 제련 → 출력 통", count(sm, Items.IRON_INGOT) >= 6, "철 주괴 " + count(sm, Items.IRON_INGOT) + " (투입 원광 " + 3 * (int) ctx.getOrDefault("smelt_in", 0) + ")");
            check("대형 제조 공장: 모래 → 유리 → 유리판 → 창고 (전체 라인)", count(fac, Items.GLASS_PANE) >= 32, "유리판 " + count(fac, Items.GLASS_PANE) + ", 남은 유리 " + count(fac, Items.GLASS));
            BlockPos top = (BlockPos) ctx.get("logiTop");
            int east = 0, south = 0;
            for (BlockPos p : find(logi, s -> s.is(Blocks.BARREL))) {
                if (p.equals(top) || !(lvl.getBlockEntity(p) instanceof Container c)) continue;
                int n = 0; for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
                if (n == 0) continue;
                if (p.getX() == top.getX()) south++; else east++;
            }
            check("물류 센터: 분배기가 두 줄 창고로 나눔", east > 0 && south > 0, "동쪽 줄 " + east + "통 · 남쪽 줄 " + south + "통");
        });

        // T10 재시작 시험 준비: 대형 사용자 설계도 저장 → 배치 시작 → 도중에 서버 종료
        step(() -> {
            log("T10 재시작 시험 준비 (대형 사용자 설계도 저장 → 배치 도중 서버 종료)");
            Blueprint big = shell(BlueprintStore.newUserId(), 128, 24, 128);
            big.owner = new java.util.UUID(0, 1); big.createdVersion = BlueprintStore.modVersion();
            BlueprintStore.put(big, null);
            BlueprintStore.flush();
            ctx.put("resumeBp", big);
            ctx.put("resume", place(big, 1, false, new BlockPos(440, Y, 0), BpPlan.Policy.SAFE));
        });
        until("재시작 시험 배치 진행", 600, () -> ((PlaceJob) ctx.get("resume")).phase == PlaceJob.PLACE && ((PlaceJob) ctx.get("resume")).cursor > 5000);
        step(() -> {
            PlaceJob r = (PlaceJob) ctx.get("resume");
            Blueprint s = (Blueprint) ctx.get("saved");
            PlaceJob ps = (PlaceJob) ctx.get("placeSaved");
            BlockPos motor = ps.pos(indexOf(ps.bp, "jjk:mech_motor"));   // 스로틀 0.5 로 복원된 전동기
            List<String> state = List.of("saved=" + (s == null ? "" : s.id), "savedCount=" + (s == null ? 0 : s.count()),
                    "resume=" + r.bp.id, "resumeCount=" + r.bp.count(), "cursor=" + r.cursor,
                    "motor=" + motor.getX() + "," + motor.getY() + "," + motor.getZ());
            try { Files.write(file("jjk-selftest-state.txt"), state, StandardCharsets.UTF_8); } catch (Exception e) { check("상태 파일 저장", false, e.toString()); }
            check("배치 도중 상태 저장 후 서버 종료 (배치 " + r.cursor + "/" + r.bp.count() + ")", r.phase == PlaceJob.PLACE && r.cursor < r.bp.count(), "");
        });
    }

    @SuppressWarnings("unchecked")
    static List<PlaceJob> rooms() { return (List<PlaceJob>) ctx.get("rooms"); }

    static CompoundTag base(int sx, int sy, int sz) {
        CompoundTag t = new CompoundTag();
        t.putInt("format", 1); t.putString("id", "user:test"); t.putString("name", "t");
        t.putIntArray("size", new int[]{sx, sy, sz});
        ListTag pal = new ListTag(); pal.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState())); t.put("palette", pal);
        t.putIntArray("blocks", new int[0]);
        return t;
    }

    // ───────────── 2단계 (재시작 후) ─────────────
    static void plan2() {
        step(() -> force(-16, -64, 600, 140));
        Map<String, String> st = new HashMap<>();
        step(() -> {
            log("T11 서버 재시작 후 데이터 유지");
            try {
                for (String line : Files.readAllLines(file("jjk-selftest-state.txt"), StandardCharsets.UTF_8)) {
                    int k = line.indexOf('='); if (k > 0) st.put(line.substring(0, k), line.substring(k + 1));
                }
            } catch (Exception e) { check("1단계 상태 파일 읽기", false, e.toString()); return; }
            Blueprint s = BlueprintStore.get(server, st.get("saved"));
            check("사용자 설계도 유지 (재시작 후 디스크에서 불러옴)", s != null && String.valueOf(s.count()).equals(st.get("savedCount")) && s.bes.size() >= 2, s == null ? "없음" : "블록 " + s.count());
            Blueprint r = BlueprintStore.get(server, st.get("resume"));
            check("대형 사용자 설계도 유지", r != null && String.valueOf(r.count()).equals(st.get("resumeCount")), r == null ? "없음" : "블록 " + r.count());
            PlaceJob j = BlueprintJobs.PLACE.stream().filter(x -> x.bp.id.equals(st.get("resume"))).findFirst().orElse(null);
            check("중단된 배치 작업이 재시작 후 이어서 진행", j != null && j.cursor >= Integer.parseInt(st.getOrDefault("cursor", "0")) - 1, j == null ? "작업 없음" : "커서 " + j.cursor);
            ctx.put("resumed", j);
        });
        until("재개된 배치 완료", 4000, () -> ctx.get("resumed") == null || done((PlaceJob) ctx.get("resumed")));
        waitTicks(40);
        step(() -> {
            PlaceJob j = (PlaceJob) ctx.get("resumed");
            if (j != null) {
                int miss = 0;
                for (int i = 0; i < j.bp.count(); i++) if (lvl.getBlockState(j.pos(i)) != j.tstates[j.bp.ss[i]]) miss++;
                check("재개된 배치: 모든 블록이 정확히 한 번 배치됨 (누락 0)", miss == 0 && !j.cancelled, "누락 " + miss + " / " + j.bp.count());
            }
            String[] m = st.getOrDefault("motor", "0,0,0").split(",");
            BlockPos mp = new BlockPos(Integer.parseInt(m[0]), Integer.parseInt(m[1]), Integer.parseInt(m[2]));
            if (lvl.getBlockEntity(mp) instanceof MachineBE be)
                check("재시작 후 기계 상태 유지 (전동기 스로틀 0.5 · 네트워크 등록 · 회전)", be.simRegistered() && Math.abs(be.motorThrottle() - 0.5) < 1e-9 && Math.abs(be.simOmega()) > 0.1,
                        "스로틀 " + be.motorThrottle() + " ω " + be.simOmega());
            else check("재시작 후 기계 블록 엔티티", false, "없음");
        });
    }
}
