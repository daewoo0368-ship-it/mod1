package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.JjkItems;
import com.tubilt.jjk.blueprint.core.BpPlan;
import com.tubilt.jjk.blueprint.core.BpTransform;
import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 건축물 소환기 (Structure Spawner) — 개발자 전용 도구.
 *  · 우클릭: 선택한 설계도를 소환 (바라보는 블록 기준) / 영역 선택 모드에서는 블록 우클릭으로 모서리 지정
 *  · 웅크리고 우클릭: 설계도 관리 화면
 * 모든 요청은 서버에서 권한·데이터를 다시 검사한다. 클라이언트가 보낸 값은 설정 후보일 뿐이다.
 *
 * 권한: 사용 = OP 레벨 2 이상 또는 태그 jjk.blueprint · 덮어쓰기/남의 설계도 관리 = OP 레벨 3 이상 또는 태그 jjk.blueprint.admin
 */
public final class StructureSpawner {
    private StructureSpawner() {}

    public static final int OP_OPEN = 0, OP_SELECT = 1, OP_DETAIL = 2, OP_SPAWN = 3, OP_PREVIEW = 4, OP_CANCEL = 5,
            OP_RENAME = 6, OP_DESC = 7, OP_COPY = 8, OP_DELETE = 9, OP_SAVE = 10, OP_SETTINGS = 11, OP_CLEAR_SEL = 12, OP_MODE = 13;
    public static final String DEFAULT = "jjk:test_power_room";
    static final int MAX_OFFSET = 64;

    static final class PState {
        String selected = DEFAULT;
        int rot, policy, anchor, ox, oy, oz;
        boolean mirror, selectMode, nextSecond;
        BlockPos p1, p2; ServerLevel selLevel;
    }
    static final Map<UUID, PState> STATES = new HashMap<>();

    static PState st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), k -> new PState()); }

    public static boolean is(ItemStack s) { return !s.isEmpty() && s.is(JjkItems.STRUCTURE_SPAWNER.get()); }

    public static boolean canUse(ServerPlayer p) { return p.createCommandSourceStack().hasPermission(2) || p.getTags().contains("jjk.blueprint"); }
    public static boolean isAdmin(ServerPlayer p) { return p.createCommandSourceStack().hasPermission(3) || p.getTags().contains("jjk.blueprint.admin"); }

    public static void init() {
        PlayerInteractEvent.RightClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.RightClickBlock>) e -> {
            if (!is(e.getItemStack())) return false;
            if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer sp) {
                Direction face = e.getFace() == null ? Direction.UP : e.getFace();
                use(sp, e.getPos(), face);
            }
            e.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        });
        PlayerInteractEvent.RightClickItem.BUS.addListener((Predicate<PlayerInteractEvent.RightClickItem>) e -> {
            if (!is(e.getItemStack())) return false;
            if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer sp) use(sp, null, null);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        });
        // 영역 선택 모드에서 소환기를 들고 있으면 선택 영역 윤곽 표시
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> {
            if (BlueprintJobs.server == null || BlueprintJobs.tick % 10 != 0) return;
            for (ServerPlayer p : BlueprintJobs.server.getPlayerList().getPlayers()) {
                PState s = STATES.get(p.getUUID());
                if (s == null || !s.selectMode || s.p1 == null || s.selLevel != p.level() || !is(p.getMainHandItem())) continue;
                BlockPos b = s.p2 == null ? s.p1 : s.p2;
                BlueprintJobs.outline(s.selLevel, min(s.p1, b), max(s.p1, b), true);
            }
        });
    }

    /** 아이템 사용. pos 가 null 이면 허공 클릭. */
    static void use(ServerPlayer sp, BlockPos pos, Direction face) {
        if (!canUse(sp)) { deny(sp); return; }
        PState s = st(sp);
        if (sp.isShiftKeyDown()) { send(sp, true, ""); return; }
        if (s.selectMode) {
            if (pos == null) { sp.displayClientMessage(Component.literal("§e영역 선택 모드 §7— 블록을 우클릭해 모서리를 지정하세요 (웅크리고 우클릭: 관리 화면)"), true); return; }
            corner(sp, s, pos);
            return;
        }
        BlockPos anchor = pos != null ? pos.relative(face) : null;
        spawn(sp, s.selected, anchor);
    }

    static void deny(ServerPlayer sp) {
        sp.displayClientMessage(Component.literal("§c건축물 소환기는 개발자 전용입니다 §7(OP 레벨 2 이상 또는 태그 jjk.blueprint 필요)"), true);
    }

    static void corner(ServerPlayer sp, PState s, BlockPos pos) {
        if (s.selLevel != sp.level()) { s.p1 = null; s.p2 = null; s.nextSecond = false; }
        s.selLevel = (ServerLevel) sp.level();
        if (!s.nextSecond || s.p1 == null) { s.p1 = pos.immutable(); s.p2 = null; s.nextSecond = true; sp.displayClientMessage(Component.literal("§a시작점 §f" + pos.toShortString() + " §7— 끝점을 우클릭하세요"), false); }
        else {
            s.p2 = pos.immutable(); s.nextSecond = false;
            String err = BpPlan.regionError(s.p1.getX(), s.p1.getY(), s.p1.getZ(), s.p2.getX(), s.p2.getY(), s.p2.getZ());
            sp.displayClientMessage(Component.literal("§a끝점 §f" + pos.toShortString() + " §7— " + selectionText(s) + (err == null ? " · 관리 화면에서 저장" : " · §c" + err)), false);
        }
    }

    static BlockPos min(BlockPos a, BlockPos b) { return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())); }
    static BlockPos max(BlockPos a, BlockPos b) { return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())); }

    static String selectionText(PState s) {
        if (s.p1 == null) return "선택 없음";
        if (s.p2 == null) return "시작점 " + s.p1.toShortString() + " (끝점 미지정)";
        BlockPos a = min(s.p1, s.p2), b = max(s.p1, s.p2);
        int dx = b.getX() - a.getX() + 1, dy = b.getY() - a.getY() + 1, dz = b.getZ() - a.getZ() + 1;
        return a.toShortString() + " ~ " + b.toShortString() + " · 크기 " + dx + "×" + dy + "×" + dz + " (" + ((long) dx * dy * dz) + "칸)";
    }

    // ───────────── 요청 처리 (서버) ─────────────
    public static void handle(ServerPlayer sp, int op, String id, String text) {
        if (!canUse(sp)) { deny(sp); return; }
        MinecraftServer server = sp.level().getServer();
        PState s = st(sp);
        String msg = "";
        switch (op) {
            case OP_OPEN -> { send(sp, true, ""); return; }
            case OP_SELECT -> {
                Blueprint b = visible(sp, id);
                if (b == null) msg = "§c설계도를 찾을 수 없음";
                else { s.selected = b.id; detail(sp, b, null); msg = "선택: " + b.name; }
            }
            case OP_DETAIL -> { Blueprint b = visible(sp, id); if (b != null) detail(sp, b, null); return; }
            case OP_SPAWN -> msg = spawn(sp, id.isEmpty() ? s.selected : id, null);
            case OP_PREVIEW -> msg = preview(sp, id.isEmpty() ? s.selected : id);
            case OP_CANCEL -> {
                PlaceJob j = BlueprintJobs.placeOf(sp.getUUID());
                CaptureJob c = BlueprintJobs.captureOf(sp.getUUID());
                if (j != null) { j.cancel(); msg = "배치 취소 요청 — 놓인 부분까지 마무리합니다"; }
                else if (c != null) { BlueprintJobs.CAPTURE.remove(c); msg = "설계도 저장 취소"; }
                else msg = "진행 중인 작업 없음";
            }
            case OP_RENAME, OP_DESC -> {
                Blueprint b = editable(sp, id);
                if (b == null) msg = "§c이 설계도는 수정할 수 없음 (고정 설계도이거나 권한 없음)";
                else {
                    String v = BpPlan.clean(text, op == OP_RENAME ? BpPlan.MAX_NAME : BpPlan.MAX_DESC);
                    if (op == OP_RENAME && v.isEmpty()) msg = "§c이름이 비어 있음";
                    else {
                        if (op == OP_RENAME) b.name = v; else b.description = v;
                        BlueprintStore.put(b, err -> { if (err != null) BlueprintJobs.runOnMain(() -> BlueprintJobs.msg(sp.getUUID(), "§c설계도 저장 실패: " + err)); });
                        msg = op == OP_RENAME ? "이름 변경: " + v : "설명 수정";
                    }
                }
            }
            case OP_COPY -> {
                Blueprint b = visible(sp, id);
                if (b == null) msg = "§c설계도를 찾을 수 없음";
                else {
                    List<String> errs = new ArrayList<>();
                    Blueprint c = Blueprint.fromTag(b.toTag(), false, errs);
                    if (c == null) msg = "§c복사 실패: " + String.join(", ", errs);
                    else {
                        c.id = BlueprintStore.newUserId(); c.owner = sp.getUUID(); c.author = sp.getName().getString();
                        c.name = BpPlan.clean(b.name + " (복사본)", BpPlan.MAX_NAME); c.createdAt = System.currentTimeMillis();
                        c.createdVersion = BlueprintStore.modVersion();
                        if (b.fixed && c.description.isEmpty()) c.description = BpPlan.clean(b.description, BpPlan.MAX_DESC);
                        if (b.fixed) c.features = b.features;
                        BlueprintStore.put(c, err -> { if (err != null) BlueprintJobs.runOnMain(() -> BlueprintJobs.msg(sp.getUUID(), "§c설계도 저장 실패: " + err)); });
                        s.selected = c.id;
                        msg = "복사: " + c.name;
                    }
                }
            }
            case OP_DELETE -> {
                Blueprint b = editable(sp, id);
                if (b == null) msg = "§c이 설계도는 삭제할 수 없음 (고정 설계도이거나 권한 없음)";
                else if (BlueprintJobs.placeOf(sp.getUUID()) != null && BlueprintJobs.placeOf(sp.getUUID()).bp == b) msg = "§c소환 중인 설계도는 삭제할 수 없음";
                else { BlueprintStore.delete(b.id); if (b.id.equals(s.selected)) s.selected = DEFAULT; msg = "삭제: " + b.name + " §7(월드 폴더 data/jjk_blueprints/deleted 에 보관)"; }
            }
            case OP_SAVE -> msg = save(sp, s, text);
            case OP_SETTINGS -> msg = settings(sp, s, text);
            case OP_CLEAR_SEL -> { s.p1 = null; s.p2 = null; s.nextSecond = false; msg = "선택 해제"; }
            case OP_MODE -> { s.selectMode = "select".equals(text); msg = s.selectMode ? "영역 선택 모드 — 블록을 우클릭해 두 모서리 지정" : "소환 모드 — 우클릭으로 소환"; }
            default -> { return; }
        }
        send(sp, false, msg);
    }

    /** 이 플레이어가 볼 수 있는 설계도: 고정 전부 + 자기 사용자 설계도 (관리자는 모두). */
    static Blueprint visible(ServerPlayer sp, String id) {
        Blueprint b = BlueprintStore.get(sp.level().getServer(), id);
        if (b == null) return null;
        if (b.fixed || isAdmin(sp) || sp.getUUID().equals(b.owner)) return b;
        return null;
    }

    static Blueprint editable(ServerPlayer sp, String id) {
        Blueprint b = visible(sp, id);
        if (b == null || b.fixed) return null;
        return isAdmin(sp) || sp.getUUID().equals(b.owner) ? b : null;
    }

    static String settings(ServerPlayer sp, PState s, String csv) {
        String[] f = csv.split(",");
        if (f.length != 7) return "§c설정 형식 오류";
        try {
            int rot = Integer.parseInt(f[0].trim()), mir = Integer.parseInt(f[1].trim()), pol = Integer.parseInt(f[2].trim()), anc = Integer.parseInt(f[3].trim());
            int ox = Integer.parseInt(f[4].trim()), oy = Integer.parseInt(f[5].trim()), oz = Integer.parseInt(f[6].trim());
            s.rot = ((rot % 4) + 4) % 4; s.mirror = mir != 0; s.anchor = anc == 1 ? 1 : 0;
            s.ox = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, ox)); s.oy = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, oy)); s.oz = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, oz));
            BpPlan.Policy p = BpPlan.Policy.of(pol);
            if (p == BpPlan.Policy.OVERWRITE && !isAdmin(sp)) { s.policy = BpPlan.Policy.SAFE.ordinal(); return "§c덮어쓰기는 개발자 권한(OP 레벨 3 / 태그 jjk.blueprint.admin)이 필요 — 안전 배치로 유지"; }
            s.policy = p.ordinal();
            return "";
        } catch (NumberFormatException e) { return "§c설정 형식 오류"; }
    }

    // ───────────── 소환 ─────────────
    /** 소환 위치 계산: 기준점(바라보는 블록 위 / 내 위치)에 변환된 건축물의 바닥 중심을 맞추고 오프셋을 더한다. */
    static BlockPos origin(ServerPlayer sp, PState s, Blueprint b, BlockPos anchor) {
        if (anchor == null) {
            if (s.anchor == 1) anchor = sp.blockPosition();
            else {
                HitResult hit = sp.pick(64, 0, false);
                if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) anchor = bh.getBlockPos().relative(bh.getDirection());
                else { Vec3 at = sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(8)); anchor = BlockPos.containing(at.x, sp.getY(), at.z); }
            }
        }
        BpTransform tf = new BpTransform(s.rot, s.mirror, b.sx, b.sy, b.sz);
        return anchor.offset(-tf.outSx() / 2 + s.ox, s.oy, -tf.outSz() / 2 + s.oz);
    }

    /** 공통 사전 검사 (권한·작업 중복·높이·청크·겹침). 문제없으면 null. */
    static String precheck(ServerPlayer sp, PState s, Blueprint b, PlaceJob j) {
        BpPlan.Policy pol = BpPlan.Policy.of(s.policy);
        if (pol == BpPlan.Policy.OVERWRITE && !isAdmin(sp)) return "덮어쓰기 권한 없음";
        if (sp.level().isOutsideBuildHeight(j.min) || sp.level().isOutsideBuildHeight(j.max)) return "월드 높이 범위를 벗어남 (y " + j.min.getY() + " ~ " + j.max.getY() + ")";
        for (int cx = j.min.getX() >> 4; cx <= j.max.getX() >> 4; cx++)
            for (int cz = j.min.getZ() >> 4; cz <= j.max.getZ() >> 4; cz++)
                if (!sp.level().isLoaded(new BlockPos(cx << 4, j.min.getY(), cz << 4))) return "로딩되지 않은 청크가 있음 (건축물 근처로 가서 다시 시도)";
        for (PlaceJob o : BlueprintJobs.PLACE) if (o.level == j.level && o.overlaps(j.min, j.max)) return "다른 소환 작업과 영역이 겹침 (" + o.ownerName + ": " + o.bp.name + ")";
        return null;
    }

    static String spawn(ServerPlayer sp, String id, BlockPos anchor) {
        if (!canUse(sp)) { deny(sp); return "§c권한 없음"; }
        PState s = st(sp);
        Blueprint b = visible(sp, id);
        if (b == null) return reply(sp, "§c설계도를 찾을 수 없음: " + id);
        if (BlueprintJobs.placeOf(sp.getUUID()) != null) return reply(sp, "§c이미 진행 중인 소환 작업이 있음 (관리 화면에서 취소 가능)");
        if (BlueprintJobs.PLACE.size() >= BlueprintJobs.MAX_PLACE_JOBS) return reply(sp, "§c서버의 동시 소환 작업이 가득 참 (" + BlueprintJobs.MAX_PLACE_JOBS + "개)");
        BlockPos o = origin(sp, s, b, anchor);
        PlaceJob j = new PlaceJob(sp.getUUID(), sp.getName().getString(), (ServerLevel) sp.level(), b, s.rot, s.mirror, o, BpPlan.Policy.of(s.policy));
        String err = precheck(sp, s, b, j);
        if (err != null) return reply(sp, "§c소환 불가: " + err);
        BlueprintJobs.PLACE.add(j);
        BlueprintJobs.dirty = true;
        if (!b.warnings.isEmpty()) j.warnings.addAll(b.warnings);
        return reply(sp, "§b소환 시작: §f" + b.name + " §7" + o.toShortString() + " · " + j.tf.label() + " · " + j.policy.label + " · 블록 " + b.count());
    }

    /** 배치 전 미리보기: 경계 상자 윤곽 + 충돌 위치 표시, 배치 가능 여부 보고. */
    static String preview(ServerPlayer sp, String id) {
        PState s = st(sp);
        Blueprint b = visible(sp, id);
        if (b == null) return "§c설계도를 찾을 수 없음";
        BlockPos o = origin(sp, s, b, null);
        PlaceJob j = new PlaceJob(sp.getUUID(), sp.getName().getString(), (ServerLevel) sp.level(), b, s.rot, s.mirror, o, BpPlan.Policy.of(s.policy));
        String err = precheck(sp, s, b, j);
        List<BlockPos> conflicts = new ArrayList<>();
        String verdict;
        if (err != null) verdict = "§c배치 불가: " + err;
        else if (b.count() <= 200_000) {
            BpPlan.Check c = BpPlan.check(b.count(), i -> j.occupancy(j.pos(i)), j.policy, 64);
            for (int i : c.conflicts) conflicts.add(j.pos(i));
            verdict = (c.ok(j.policy) ? "§a배치 가능" : "§c배치 불가: " + c.reason(j.policy))
                    + " §7(놓을 블록 " + c.willPlace + ", 건너뜀 " + c.willSkip + ", 기존 블록 충돌 " + c.solid + ")";
        } else verdict = "§e대형 건축물 — 충돌 검사는 소환할 때 여러 틱에 나눠 수행";
        BlueprintJobs.PREVIEWS.removeIf(p -> p.level() == j.level && p.min().equals(j.min) && p.max().equals(j.max));
        BlueprintJobs.PREVIEWS.add(new BlueprintJobs.Preview(j.level, j.min, j.max, conflicts, new int[]{120}));
        String m = "미리보기 " + j.min.toShortString() + " ~ " + j.max.toShortString() + " · " + j.tf.label() + " · " + j.policy.label + " — " + verdict;
        sp.displayClientMessage(Component.literal(m), false);
        return m;
    }

    static String save(ServerPlayer sp, PState s, String name) {
        name = BpPlan.clean(name, BpPlan.MAX_NAME);
        if (name.isEmpty()) return "§c설계도 이름을 입력하세요";
        if (s.p1 == null || s.p2 == null) return "§c먼저 영역의 두 모서리를 지정하세요 (영역 선택 모드에서 블록 우클릭)";
        if (s.selLevel != sp.level()) return "§c선택한 영역이 다른 차원에 있음";
        String err = BpPlan.regionError(s.p1.getX(), s.p1.getY(), s.p1.getZ(), s.p2.getX(), s.p2.getY(), s.p2.getZ());
        if (err != null) return "§c" + err;
        if (BlueprintJobs.captureOf(sp.getUUID()) != null) return "§c이미 저장 중인 설계도가 있음";
        BlockPos a = min(s.p1, s.p2), b = max(s.p1, s.p2);
        for (int cx = a.getX() >> 4; cx <= b.getX() >> 4; cx++)
            for (int cz = a.getZ() >> 4; cz <= b.getZ() >> 4; cz++)
                if (!sp.level().isLoaded(new BlockPos(cx << 4, a.getY(), cz << 4))) return "§c로딩되지 않은 청크가 있음";
        BlueprintJobs.CAPTURE.add(new CaptureJob(sp.getUUID(), sp.getName().getString(), (ServerLevel) sp.level(), s.p1, s.p2, BlueprintStore.newUserId(), name));
        return "§b설계도 저장 시작: §f" + name + " §7(" + selectionText(s) + ")";
    }

    static String reply(ServerPlayer sp, String m) {
        for (String line : m.split("\n")) sp.displayClientMessage(Component.literal(line), false);
        return m;
    }

    // ───────────── 클라이언트로 보내기 ─────────────
    static boolean real(ServerPlayer p) { return !(p instanceof FakePlayer) && p.connection != null; }

    public static void refresh(UUID id) {
        ServerPlayer p = BlueprintJobs.player(id);
        if (p != null && canUse(p)) send(p, false, "");
    }

    static void send(ServerPlayer sp, boolean open, String msg) {
        if (!real(sp)) return;
        MinecraftServer server = sp.level().getServer();
        PState s = st(sp);
        List<Payloads.BpEntry> es = new ArrayList<>();
        boolean admin = isAdmin(sp);
        for (Blueprint b : BlueprintStore.fixed(server).values()) es.add(entry(b, false));
        for (Blueprint b : BlueprintStore.users()) if (admin || sp.getUUID().equals(b.owner)) es.add(entry(b, true));
        List<Integer> set = List.of(s.rot, s.mirror ? 1 : 0, s.policy, s.anchor, s.ox, s.oy, s.oz, s.selectMode ? 1 : 0, admin ? 1 : 0);
        List<String> errs = BlueprintStore.errors();
        String m = msg == null ? "" : msg;
        if (open && !errs.isEmpty()) m = (m.isEmpty() ? "" : m + "\n") + "§e설계도 오류 " + errs.size() + "건: " + errs.get(0);
        JjkNet.toPlayer(sp, new Payloads.BpList(open, s.selected, set, selectionText(s), BlueprintJobs.jobText(sp.getUUID()), BlueprintJobs.jobFrac(sp.getUUID()), m, es));
        if (open) { Blueprint b = visible(sp, s.selected); if (b != null) detail(sp, b, null); }
    }

    static Payloads.BpEntry entry(Blueprint b, boolean editable) {
        String date = b.createdAt > 0 ? java.time.Instant.ofEpochMilli(b.createdAt).toString().substring(0, 10) : "";
        return new Payloads.BpEntry(b.id, b.fixed, editable, b.name, b.description, b.author + (date.isEmpty() ? "" : " · " + date),
                b.createdVersion, b.sx, b.sy, b.sz, b.count(), b.machineCount(), b.materialTotal(), b.warnings.size(), b.featureText());
    }

    static void detail(ServerPlayer sp, Blueprint b, String extra) {
        if (!real(sp)) return;
        StringBuilder mats = new StringBuilder();
        b.materials().forEach((k, v) -> mats.append(k).append('\t').append(v).append('\n'));
        StringBuilder info = new StringBuilder();
        info.append("포맷 v").append(BpPlan.FORMAT).append(" · 블록 엔티티 ").append(b.bes.size())
                .append(b.fixed ? " · 고정 설계도(읽기 전용)" : " · 사용자 설계도").append('\n');
        for (String w : b.warnings) info.append("⚠ ").append(w).append('\n');
        if (extra != null) info.append(extra);
        JjkNet.toPlayer(sp, new Payloads.BpDetail(b.id, b.preview(0, 48), b.preview(1, 48), mats.toString(), info.toString()));
    }

    /** 자체 검증용: 지정한 플레이어(가짜 플레이어 포함)로 소환 요청을 그대로 실행. */
    static String spawnAs(ServerPlayer sp, String id, BlockPos anchor) { return spawn(sp, id, anchor); }

    static boolean holding(Player p) { return is(p.getMainHandItem()); }
}
