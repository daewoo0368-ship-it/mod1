package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.blueprint.core.BpPlan;
import com.tubilt.jjk.machine.MachineBlock;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 설계도 한 개. 고정 설계도(모드 리소스의 SNBT)와 사용자 설계도(월드 폴더의 NBT)가 같은 형식을 쓴다.
 *
 * <pre>
 * { format: 1, id: "jjk:test_power_room", name, description, author, created_version, created_at: 0L, owner: "uuid",
 *   size: [I; x, y, z],
 *   palette: [ {Name: "minecraft:stone"}, {Name: "jjk:mech_motor", Properties: {axis: "x"}} ],
 *   blocks: [I; x, y, z, 팔레트번호, x, y, z, 팔레트번호, ...],      ← 공기는 저장하지 않음
 *   block_entities: [ {i: 블록번호, nbt: {id: "...", ...허용된 키} } ],
 *   features: ["상태|이름|설명", ...] }                              ← 상태: working / partial / planned
 * </pre>
 */
public final class Blueprint {
    public String id = "";
    public boolean fixed;
    public String name = "", description = "", author = "", createdVersion = "";
    public long createdAt;
    public UUID owner;
    public int sx, sy, sz;
    public final List<BlockState> palette = new ArrayList<>();
    public int[] xs = new int[0], ys = new int[0], zs = new int[0], ss = new int[0];
    public final Map<Integer, CompoundTag> bes = new java.util.TreeMap<>();   // 블록 번호 순 (저장 결과가 항상 같게)
    public String features = "";
    /** 불러오면서 생긴 경고(제외한 블록·데이터 등). 저장되지 않는다. */
    public final List<String> warnings = new ArrayList<>();

    public int count() { return xs.length; }
    public BlockState state(int i) { return palette.get(ss[i]); }

    // ───────────── 직렬화 ─────────────
    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("format", BpPlan.FORMAT);
        t.putString("id", id);
        t.putString("name", name); t.putString("description", description);
        t.putString("author", author); t.putString("created_version", createdVersion);
        t.putLong("created_at", createdAt);
        if (owner != null) t.putString("owner", owner.toString());
        t.putIntArray("size", new int[]{sx, sy, sz});
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        t.put("palette", pal);
        int n = count();
        int[] packed = new int[n * 4];
        for (int i = 0; i < n; i++) { packed[i * 4] = xs[i]; packed[i * 4 + 1] = ys[i]; packed[i * 4 + 2] = zs[i]; packed[i * 4 + 3] = ss[i]; }
        t.putIntArray("blocks", packed);
        ListTag be = new ListTag();
        for (Map.Entry<Integer, CompoundTag> e : bes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("i", e.getKey()); c.put("nbt", e.getValue().copy());
            be.add(c);
        }
        t.put("block_entities", be);
        ListTag fl = new ListTag();
        for (String line : features.split("\n")) if (!line.isBlank()) fl.add(StringTag.valueOf(line));
        t.put("features", fl);
        return t;
    }

    /**
     * NBT → 설계도. 치명적인 문제(포맷 버전·크기·손상)는 errors 에 넣고 null 을 돌려준다.
     * 개별 문제(모르는 블록, 금지 블록, 범위 밖 좌표, 중복, 허용되지 않은 데이터)는 그 항목만 빼고 warnings 에 남긴다.
     */
    public static Blueprint fromTag(CompoundTag t, boolean fixed, List<String> errors) {
        Blueprint b = new Blueprint();
        b.fixed = fixed;
        int format = t.getIntOr("format", -1);
        if (format < 1 || format > BpPlan.FORMAT) { errors.add("지원하지 않는 설계도 포맷 버전: " + format + " (지원: 1~" + BpPlan.FORMAT + ")"); return null; }
        b.id = t.getStringOr("id", "");
        b.name = BpPlan.clean(t.getStringOr("name", "이름 없음"), BpPlan.MAX_NAME);
        b.description = BpPlan.clean(t.getStringOr("description", ""), fixed ? 2000 : BpPlan.MAX_DESC);
        b.author = BpPlan.clean(t.getStringOr("author", ""), 40);
        b.createdVersion = BpPlan.clean(t.getStringOr("created_version", ""), 20);
        b.createdAt = t.getLongOr("created_at", 0L);
        String ow = t.getStringOr("owner", "");
        if (!ow.isEmpty()) { try { b.owner = UUID.fromString(ow); } catch (IllegalArgumentException e) { b.warnings.add("제작자 UUID 손상"); } }
        ListTag fl = t.getListOrEmpty("features");
        StringBuilder fs = new StringBuilder();
        for (int i = 0; i < Math.min(fl.size(), 64); i++) fs.append(BpPlan.clean(fl.getStringOr(i, ""), 200)).append('\n');
        b.features = fs.toString();
        int[] size = t.getIntArray("size").orElse(new int[0]);
        if (size.length != 3) { errors.add("크기 정보 없음/손상"); return null; }
        b.sx = size[0]; b.sy = size[1]; b.sz = size[2];
        int[] packed = t.getIntArray("blocks").orElse(new int[0]);
        if (packed.length % 4 != 0) { errors.add("블록 데이터 손상 (길이 " + packed.length + ")"); return null; }
        String se = BpPlan.sizeError(b.sx, b.sy, b.sz, packed.length / 4);
        if (se != null) { errors.add(se); return null; }

        // 팔레트: 모르는 블록·금지 블록은 -1 로 표시하고 해당 블록들을 뺀다
        ListTag pal = t.getListOrEmpty("palette");
        if (pal.isEmpty() && packed.length > 0) { errors.add("팔레트 없음"); return null; }
        int[] remap = new int[pal.size()];
        Map<BlockState, Integer> seen = new HashMap<>();
        for (int i = 0; i < pal.size(); i++) {
            CompoundTag c = pal.getCompoundOrEmpty(i);
            String name = c.getStringOr("Name", "");
            ResourceLocation rl = ResourceLocation.tryParse(name);
            if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) { remap[i] = -1; BlueprintFilter.warn(b.warnings, "현재 게임에 없는 블록 제외: " + name); continue; }
            if (BlueprintFilter.deniedBlock(rl.toString())) { remap[i] = -1; BlueprintFilter.warn(b.warnings, "금지 블록 제외: " + rl); continue; }
            BlockState st = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, c);
            if (st.isAir()) { remap[i] = -1; continue; }
            Integer k = seen.get(st);
            if (k == null) { k = b.palette.size(); b.palette.add(st); seen.put(st, k); }
            remap[i] = k;
        }

        int n = packed.length / 4;
        IntArrayList X = new IntArrayList(n), Y = new IntArrayList(n), Z = new IntArrayList(n), S = new IntArrayList(n);
        int[] newIndex = new int[n];
        Set<Long> used = new HashSet<>();
        int bad = 0, dup = 0, dropped = 0;
        for (int i = 0; i < n; i++) {
            newIndex[i] = -1;
            int x = packed[i * 4], y = packed[i * 4 + 1], z = packed[i * 4 + 2], s = packed[i * 4 + 3];
            if (x < 0 || y < 0 || z < 0 || x >= b.sx || y >= b.sy || z >= b.sz || s < 0 || s >= remap.length) { bad++; continue; }
            if (remap[s] < 0) { dropped++; continue; }
            if (!used.add(BlockPos.asLong(x, y, z))) { dup++; continue; }
            newIndex[i] = X.size();
            X.add(x); Y.add(y); Z.add(z); S.add(remap[s]);
        }
        if (bad > 0) b.warnings.add("범위를 벗어나거나 손상된 블록 " + bad + "개 제외");
        if (dup > 0) b.warnings.add("같은 위치에 겹친 블록 " + dup + "개 제외");
        if (dropped > 0) b.warnings.add("사용할 수 없는 블록 " + dropped + "개 제외");
        b.xs = X.toIntArray(); b.ys = Y.toIntArray(); b.zs = Z.toIntArray(); b.ss = S.toIntArray();

        ListTag be = t.getListOrEmpty("block_entities");
        for (int i = 0; i < be.size(); i++) {
            CompoundTag c = be.getCompoundOrEmpty(i);
            int idx = c.getIntOr("i", -1);
            if (idx < 0 || idx >= n || newIndex[idx] < 0) continue;
            CompoundTag clean = BlueprintFilter.sanitize(c.getCompoundOrEmpty("nbt"), b.warnings);
            if (clean != null) b.bes.put(newIndex[idx], clean);
        }
        return b;
    }

    // ───────────── 요약 ─────────────
    public int machineCount() {
        int m = 0;
        for (int i = 0; i < count(); i++) if (state(i).getBlock() instanceof MachineBlock) m++;
        return m;
    }

    /** 필요한 재료 (블록 아이템의 번역 키 → 개수, 많은 순 — 이름은 클라이언트 언어로 표시). 아이템이 없는 블록(물 등)은 "#other". */
    public LinkedHashMap<String, Integer> materials() {
        Map<Item, Integer> m = new HashMap<>();
        int other = 0;
        for (int i = 0; i < count(); i++) {
            Item it = state(i).getBlock().asItem();
            if (it == Items.AIR) { other++; continue; }
            m.merge(it, 1, Integer::sum);
        }
        List<Map.Entry<Item, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, c) -> Integer.compare(c.getValue(), a.getValue()));
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : l) out.put(e.getKey().getDescriptionId(), e.getValue());
        if (other > 0) out.put("#other", other);
        return out;
    }

    public int materialTotal() {
        int n = 0;
        for (int i = 0; i < count(); i++) if (state(i).getBlock().asItem() != Items.AIR) n++;
        return n;
    }

    /** 사용자 설계도의 "예상 기능" 자동 요약. */
    public String autoFeatures() {
        int mach = machineCount(), cont = 0, red = 0;
        for (int i = 0; i < count(); i++) {
            String k = BuiltInRegistries.BLOCK.getKey(state(i).getBlock()).getPath();
            if (k.contains("chest") || k.contains("barrel") || k.contains("hopper") || k.contains("furnace") || k.contains("smoker") || k.contains("dropper") || k.contains("dispenser") || k.equals("crafter")) cont++;
            if (k.contains("redstone") || k.contains("repeater") || k.contains("comparator") || k.contains("observer") || k.contains("piston") || k.contains("lever")) red++;
        }
        StringBuilder s = new StringBuilder();
        if (mach > 0) s.append("working|기계 동력 계통|기계 부품 ").append(mach).append("개 — 배치 후 네트워크 재구성\n");
        if (cont > 0) s.append("working|저장·가공 장치|컨테이너·가공 블록 ").append(cont).append("개\n");
        if (red > 0) s.append("working|레드스톤 회로|레드스톤 부품 ").append(red).append("개\n");
        if (s.isEmpty()) s.append("working|건축물|기능 블록 없음\n");
        return s.toString();
    }

    public String featureText() { return fixed || !features.isBlank() ? features : autoFeatures(); }

    // ───────────── 미리보기 ─────────────
    /** 미리보기 격자: kind 0 = 위에서 본 모습(x,z), 1 = 앞(남쪽)에서 본 모습(x,y). 최대 48×48 로 축소. out = {w, h, 색...} */
    public int[] preview(int kind, int maxSide) {
        int W = sx, H = kind == 0 ? sz : sy;
        int step = Math.max(1, (int) Math.ceil(Math.max(W, H) / (double) maxSide));
        int w = (W + step - 1) / step, h = (H + step - 1) / step;
        int[] best = new int[w * h], col = new int[w * h];
        java.util.Arrays.fill(best, Integer.MIN_VALUE);
        for (int i = 0; i < count(); i++) {
            int u = xs[i] / step, v, depth;
            if (kind == 0) { v = zs[i] / step; depth = ys[i]; }
            else { v = (sy - 1 - ys[i]) / step; depth = zs[i]; }
            int k = v * w + u;
            if (depth >= best[k]) { best[k] = depth; col[k] = color(state(i), kind == 0 ? ys[i] / (double) Math.max(1, sy - 1) : zs[i] / (double) Math.max(1, sz - 1)); }
        }
        int[] out = new int[2 + w * h];
        out[0] = w; out[1] = h;
        System.arraycopy(col, 0, out, 2, w * h);
        return out;
    }

    static int color(BlockState s, double shade) {
        int c;
        if (s.getBlock() instanceof MachineBlock) c = 0x40C8FF;
        else {
            try { c = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col; } catch (RuntimeException e) { c = 0x808080; }
            if (c == 0) c = 0x707070;
        }
        double f = 0.55 + 0.45 * shade;
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), bl = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
