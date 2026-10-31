package com.tubilt.jjk.blueprint.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 배치 계획 (Minecraft 비의존): 배치 순서, 기존 블록 처리 정책, 충돌 판정, 설계도 크기 제한. */
public final class BpPlan {
    private BpPlan() {}

    // ── 제한 ──
    public static final int FORMAT = 1;              // 현재 설계도 포맷 버전
    public static final int MAX_DIM = 256;           // 한 변 최대 길이
    public static final long MAX_VOLUME = 4_000_000; // 사용자 설계도 저장 영역 최대 부피
    public static final int MAX_BLOCKS = 1_000_000;  // 설계도 하나의 최대 블록 수
    public static final int MAX_NAME = 40, MAX_DESC = 200;

    /** 기존 블록 처리 정책. 기본값은 SAFE. */
    public enum Policy {
        SAFE("안전 배치", "기존 블록이 있으면 소환 거부"),
        AIR_ONLY("빈 공간만", "공기인 위치에만 배치, 나머지는 건너뜀"),
        OVERWRITE("덮어쓰기", "기존 블록을 교체 (개발자 권한)");
        public final String label, desc;
        Policy(String label, String desc) { this.label = label; this.desc = desc; }
        public static Policy of(int i) { return i >= 0 && i < values().length ? values()[i] : SAFE; }
    }

    // ── 배치 위치 상태 ──
    public static final int AIR = 0, REPLACEABLE = 1, SOLID = 2, UNLOADED = 3, OUT_OF_WORLD = 4;

    /** 이 위치에 놓을지. UNLOADED/OUT_OF_WORLD 는 어떤 정책에서도 놓지 않는다. */
    public static boolean shouldPlace(Policy p, int occ) {
        return switch (occ) {
            case AIR -> true;
            case REPLACEABLE -> p != Policy.AIR_ONLY;
            case SOLID -> p == Policy.OVERWRITE;
            default -> false;
        };
    }

    /** 사전 검사 결과. */
    public static final class Check {
        public int total, air, replaceable, solid, unloaded, outOfWorld, willPlace, willSkip;
        public final List<Integer> conflicts = new ArrayList<>();   // 충돌(SOLID) 블록 번호, 최대 maxList 개
        /** 소환 가능 여부: 높이 초과·미로딩은 항상 거부, 안전 배치는 충돌이 하나라도 있으면 거부. */
        public boolean ok(Policy p) { return outOfWorld == 0 && unloaded == 0 && (p != Policy.SAFE || solid == 0) && willPlace > 0; }
        public String reason(Policy p) {
            if (outOfWorld > 0) return "월드 높이 범위를 벗어나는 블록 " + outOfWorld + "개";
            if (unloaded > 0) return "로딩되지 않은 청크에 걸친 블록 " + unloaded + "개";
            if (p == Policy.SAFE && solid > 0) return "기존 블록과 충돌 " + solid + "개 (안전 배치)";
            if (willPlace == 0) return "배치할 블록이 없음";
            return "배치 가능";
        }
    }

    public interface Occupancy { int at(int i); }

    public static Check check(int n, Occupancy occ, Policy p, int maxList) {
        Check c = new Check();
        c.total = n;
        for (int i = 0; i < n; i++) add(c, i, occ.at(i), p, maxList);
        return c;
    }

    /** 검사를 여러 틱에 나눠 할 때 한 칸씩 누적. */
    public static void add(Check c, int i, int o, Policy p, int maxList) {
        switch (o) {
            case AIR -> c.air++;
            case REPLACEABLE -> c.replaceable++;
            case SOLID -> { c.solid++; if (c.conflicts.size() < maxList) c.conflicts.add(i); }
            case UNLOADED -> c.unloaded++;
            default -> c.outOfWorld++;
        }
        if (shouldPlace(p, o)) c.willPlace++; else c.willSkip++;
    }

    /**
     * 배치 순서(블록 번호의 순열): 등급(0 꽉 찬 블록 → 1 붙는·얇은 블록 → 2 기계) → 아래층부터(y) → x → z.
     * 받침 블록이 먼저 놓이고, 기계는 건축물 뼈대가 다 선 다음 놓인다.
     */
    public static int[] order(int n, int[] x, int[] y, int[] z, int[] klass) {
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> {
            if (klass[a] != klass[b]) return Integer.compare(klass[a], klass[b]);
            if (y[a] != y[b]) return Integer.compare(y[a], y[b]);
            if (x[a] != x[b]) return Integer.compare(x[a], x[b]);
            return Integer.compare(z[a], z[b]);
        });
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = idx[i];
        return out;
    }

    /** 두 모서리로 만든 영역 검사. 문제가 없으면 null, 있으면 사유. */
    public static String regionError(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = Math.abs((long) x2 - x1) + 1, dy = Math.abs((long) y2 - y1) + 1, dz = Math.abs((long) z2 - z1) + 1;
        if (dx > MAX_DIM || dy > MAX_DIM || dz > MAX_DIM) return "영역이 너무 큼 (한 변 최대 " + MAX_DIM + "칸, 현재 " + dx + "×" + dy + "×" + dz + ")";
        if (dx * dy * dz > MAX_VOLUME) return "영역 부피가 너무 큼 (최대 " + MAX_VOLUME + "칸, 현재 " + (dx * dy * dz) + "칸)";
        return null;
    }

    /** 설계도 크기 검사. */
    public static String sizeError(int sx, int sy, int sz, int blocks) {
        if (sx < 1 || sy < 1 || sz < 1) return "크기가 올바르지 않음 (" + sx + "×" + sy + "×" + sz + ")";
        if (sx > MAX_DIM || sy > MAX_DIM || sz > MAX_DIM) return "크기 제한 초과 (한 변 최대 " + MAX_DIM + ")";
        if (blocks < 0 || blocks > MAX_BLOCKS) return "블록 수 제한 초과 (최대 " + MAX_BLOCKS + ")";
        if ((long) sx * sy * sz < blocks) return "블록 수가 크기보다 많음";
        return null;
    }

    /** 이름·설명 정리: 제어 문자와 서식 코드(§) 제거, 길이 제한. */
    public static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '§') { i++; continue; }   // 서식 코드는 다음 글자까지 함께 제거
            if (Character.isISOControl(c)) continue;
            b.append(c);
        }
        return b.toString().strip();
    }
}
