package com.tubilt.jjk.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 설계도 안전 필터.
 *  · 명령·구조물 블록처럼 명령 실행이나 월드 조작이 가능한 블록은 설계도에 넣지도, 배치하지도 않는다.
 *  · 블록 엔티티 데이터는 허용 목록(타입별 허용 키)만 남긴다 → 좌표·소유자·명령·루트 테이블 시드·엔티티 데이터 등은 버린다.
 *  · 컨테이너 안 아이템도 명령/엔티티/블록 엔티티 데이터를 실을 수 있는 구성 요소는 제거한다.
 */
public final class BlueprintFilter {
    private BlueprintFilter() {}

    /** 설계도에 넣지 않는 블록. */
    public static final Set<String> DENY_BLOCKS = Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:structure_void",
            "minecraft:test_block", "minecraft:test_instance_block");

    /** 컨테이너에서 빼는 아이템 (명령 블록·구조물 블록·디버그 도구·책 클릭 명령). */
    static final Set<String> DENY_ITEMS = Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:jigsaw", "minecraft:structure_void",
            "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:written_book", "minecraft:test_block", "minecraft:test_instance_block");

    /** 아이템에서 제거하는 데이터 구성 요소 (엔티티/블록 엔티티 NBT 를 실어 명령 블록 등을 우회 설치할 수 있음). */
    static final Set<String> DENY_COMPONENTS = Set.of(
            "minecraft:block_entity_data", "minecraft:entity_data", "minecraft:bucket_entity_data", "minecraft:written_book_content");

    static final Set<String> CONTAINER = Set.of("Items");

    /** 블록 엔티티 타입 → 허용 키. 목록에 없는 타입은 데이터를 통째로 버린다(블록만 배치). */
    static final Map<String, Set<String>> ALLOW = Map.ofEntries(
            Map.entry("jjk:machine", Set.of("omega", "angle", "fatigue", "wear", "throttle", "dir")),
            Map.entry("minecraft:chest", CONTAINER), Map.entry("minecraft:trapped_chest", CONTAINER),
            Map.entry("minecraft:barrel", CONTAINER), Map.entry("minecraft:shulker_box", CONTAINER),
            Map.entry("minecraft:hopper", Set.of("Items", "TransferCooldown")),
            Map.entry("minecraft:dropper", CONTAINER), Map.entry("minecraft:dispenser", CONTAINER),
            Map.entry("minecraft:furnace", CONTAINER), Map.entry("minecraft:blast_furnace", CONTAINER), Map.entry("minecraft:smoker", CONTAINER),
            Map.entry("minecraft:brewing_stand", Set.of("Items", "Fuel")),
            Map.entry("minecraft:crafter", Set.of("Items", "disabled_slots")),
            Map.entry("minecraft:chiseled_bookshelf", Set.of("Items", "last_interacted_slot")),
            Map.entry("minecraft:comparator", Set.of("OutputSignal")),
            Map.entry("minecraft:banner", Set.of("patterns")),
            Map.entry("minecraft:decorated_pot", Set.of("sherds")));

    public static boolean deniedBlock(String id) { return DENY_BLOCKS.contains(id); }

    /**
     * 저장/불러온 블록 엔티티 데이터를 정리한다. 버려야 하면 null.
     * 결과에는 타입 확인용 "id" 와 허용된 키만 남는다.
     */
    public static CompoundTag sanitize(CompoundTag raw, Collection<String> warnings) {
        if (raw == null) return null;
        String id = raw.getStringOr("id", "");
        Set<String> allow = ALLOW.get(id);
        if (allow == null) {
            if (!id.isEmpty()) warn(warnings, "블록 엔티티 데이터 제외(허용 목록 밖): " + id);
            return null;
        }
        CompoundTag out = new CompoundTag();
        out.putString("id", id);
        for (String k : allow) {
            Tag v = raw.get(k);
            if (v == null) continue;
            if (k.equals("Items")) { if (v instanceof ListTag l) out.put("Items", items(l, warnings)); }
            else out.put(k, v.copy());
        }
        if (id.equals("jjk:machine")) machine(out);
        return out;
    }

    /** 기계 상태 숫자 검증: NaN/무한대 제거, 범위 제한. */
    static void machine(CompoundTag t) {
        t.putDouble("omega", clamp(t.getDoubleOr("omega", 0), -2000, 2000, 0));
        t.putDouble("angle", clamp(t.getDoubleOr("angle", 0), -1e9, 1e9, 0));
        t.putDouble("fatigue", clamp(t.getDoubleOr("fatigue", 0), 0, 0.99, 0));
        t.putDouble("wear", clamp(t.getDoubleOr("wear", 0), 0, 0.99, 0));
        t.putDouble("throttle", clamp(t.getDoubleOr("throttle", 1), 0, 1, 1));
        t.putInt("dir", t.getIntOr("dir", 1) < 0 ? -1 : 1);
    }

    static double clamp(double v, double lo, double hi, double dflt) {
        if (!Double.isFinite(v)) return dflt;
        return Math.max(lo, Math.min(hi, v));
    }

    static ListTag items(ListTag in, Collection<String> warnings) {
        ListTag out = new ListTag();
        for (int i = 0; i < in.size(); i++) {
            CompoundTag it = in.getCompoundOrEmpty(i).copy();
            String id = it.getStringOr("id", "");
            if (id.isEmpty() || DENY_ITEMS.contains(id)) { warn(warnings, "컨테이너 아이템 제외: " + (id.isEmpty() ? "(빈 항목)" : id)); continue; }
            CompoundTag comps = it.getCompoundOrEmpty("components");
            boolean changed = false;
            for (String k : DENY_COMPONENTS) if (comps.contains(k)) { comps.remove(k); changed = true; }
            if (changed) { it.put("components", comps); warn(warnings, "아이템 데이터 구성 요소 제거: " + id); }
            out.add(it);
        }
        return out;
    }

    static void warn(Collection<String> w, String s) {
        if (w != null && w.size() < 200 && !w.contains(s)) w.add(s);
    }
}
