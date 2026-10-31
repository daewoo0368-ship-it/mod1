package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.blueprint.core.BpPlan;
import com.tubilt.jjk.blueprint.core.BpTransform;
import com.tubilt.jjk.sim.GridRules;
import com.tubilt.jjk.sim.MachineSpecs;
import com.tubilt.jjk.sim.MechLink;
import com.tubilt.jjk.sim.Net;
import com.tubilt.jjk.sim.Part;
import com.tubilt.jjk.sim.Parts;
import com.tubilt.jjk.sim.Sim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 건축물 소환기 핵심 로직 자체 검증 (Minecraft 없이 실행).
 *  · 좌표/방향/축 변환 (회전 0·90·180·270°, 좌우 반전)
 *  · 기존 블록 처리 정책·충돌 판정·배치 순서·크기 제한
 *  · 모드에 포함된 고정 설계도 파일: 형식·범위·중복 검사, 기계 네트워크(잠김 없음)
 *  · 회전·반전 후에도 기계 네트워크가 같은 구조로 연결되고, 각 부품의 회전이 정확히 변환된 값으로 도는지 (실제 물리 엔진으로 계산)
 */
public final class BpSelfTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
    }

    public static void main(String[] a) throws Exception {
        System.out.println("B1 좌표 변환: 8가지 변환 모두 경계 상자 안의 일대일 대응");
        for (int[] s : new int[][]{{1, 1, 1}, {3, 2, 5}, {7, 3, 4}, {13, 5, 7}}) {
            boolean ok = true;
            for (int r = 0; r < 4; r++) for (int m = 0; m < 2; m++) {
                BpTransform t = new BpTransform(r, m == 1, s[0], s[1], s[2]);
                Set<Long> seen = new HashSet<>();
                int[] o = new int[3];
                for (int x = 0; x < s[0]; x++) for (int y = 0; y < s[1]; y++) for (int z = 0; z < s[2]; z++) {
                    t.apply(x, y, z, o);
                    if (o[0] < 0 || o[1] < 0 || o[2] < 0 || o[0] >= t.outSx() || o[1] >= t.outSy() || o[2] >= t.outSz()) ok = false;
                    seen.add(((long) o[0] << 40) | ((long) o[1] << 20) | o[2]);
                }
                if (seen.size() != s[0] * s[1] * s[2]) ok = false;
            }
            check("크기 " + s[0] + "×" + s[1] + "×" + s[2], ok, "");
        }

        System.out.println("B2 방향 규약 (바닐라 Rotation/Mirror 와 같은 방향)");
        check("90°: 북→동", new BpTransform(1, false, 3, 1, 3).horizontal(0) == 1, "");
        check("180°: 북→남", new BpTransform(2, false, 3, 1, 3).horizontal(0) == 2, "");
        check("270°: 북→서", new BpTransform(3, false, 3, 1, 3).horizontal(0) == 3, "");
        check("반전: 동↔서, 북 유지", new BpTransform(0, true, 3, 1, 3).horizontal(1) == 3 && new BpTransform(0, true, 3, 1, 3).horizontal(0) == 0, "");
        check("반전+90°: 동→남(반전 먼저)", new BpTransform(1, true, 3, 1, 3).horizontal(1) == 0, "동→서→북");
        // 좌표 변환과 방향 변환이 일치: 이웃 칸 (x+1) 은 변환 후 horizontal(동) 방향 이웃
        boolean cons = true;
        for (int r = 0; r < 4; r++) for (int m = 0; m < 2; m++) {
            BpTransform t = new BpTransform(r, m == 1, 5, 1, 4);
            int[] p = new int[3], q = new int[3];
            int[][] dv = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            for (int d = 0; d < 4; d++) {
                t.apply(2, 0, 2, p); t.apply(2 + dv[d][0], 0, 2 + dv[d][1], q);
                int h = t.horizontal(d);
                if (q[0] - p[0] != dv[h][0] || q[2] - p[2] != dv[h][1]) cons = false;
            }
        }
        check("좌표 이웃 관계 = 방향 변환", cons, "8가지 변환 × 4방향");

        System.out.println("B3 축·회전 부호 변환");
        BpTransform r90 = new BpTransform(1, false, 3, 3, 3), mir = new BpTransform(0, true, 3, 3, 3);
        check("90°: X↔Z, Y 유지", r90.axis(0) == 2 && r90.axis(2) == 0 && r90.axis(1) == 1, "");
        check("반전: 축 유지", mir.axis(0) == 0 && mir.axis(1) == 1 && mir.axis(2) == 2, "");
        check("90°: X축 부품의 +방향은 새 Z축 +방향 → 부호 +1", r90.spinFactor(0) == 1, "");
        check("90°: Z축 부품의 +방향은 새 X축 −방향 → 부호 −1", r90.spinFactor(2) == -1, "");
        check("반전: 반전면에 수직인 X축은 그대로(+1), 면 안의 Y·Z축은 뒤집힘(−1)", mir.spinFactor(0) == 1 && mir.spinFactor(1) == -1 && mir.spinFactor(2) == -1, "유사 벡터");
        check("0°: 모든 축 +1", new BpTransform(0, false, 1, 1, 1).spinFactor(0) == 1 && new BpTransform(0, false, 1, 1, 1).spinFactor(1) == 1 && new BpTransform(0, false, 1, 1, 1).spinFactor(2) == 1, "");

        System.out.println("B4 기존 블록 처리 정책·충돌 판정");
        int[] occ = {BpPlan.AIR, BpPlan.REPLACEABLE, BpPlan.SOLID, BpPlan.AIR};
        BpPlan.Check safe = BpPlan.check(4, i -> occ[i], BpPlan.Policy.SAFE, 10);
        check("안전 배치: 충돌 1개 → 거부", !safe.ok(BpPlan.Policy.SAFE) && safe.solid == 1 && safe.conflicts.get(0) == 2, safe.reason(BpPlan.Policy.SAFE));
        BpPlan.Check air = BpPlan.check(4, i -> occ[i], BpPlan.Policy.AIR_ONLY, 10);
        check("빈 공간만: 공기 2칸만 배치, 2칸 건너뜀", air.ok(BpPlan.Policy.AIR_ONLY) && air.willPlace == 2 && air.willSkip == 2, "");
        BpPlan.Check ow = BpPlan.check(4, i -> occ[i], BpPlan.Policy.OVERWRITE, 10);
        check("덮어쓰기: 4칸 모두 배치", ow.ok(BpPlan.Policy.OVERWRITE) && ow.willPlace == 4, "");
        int[] occ2 = {BpPlan.AIR, BpPlan.UNLOADED};
        check("미로딩 청크 → 어떤 정책이든 거부", !BpPlan.check(2, i -> occ2[i], BpPlan.Policy.OVERWRITE, 10).ok(BpPlan.Policy.OVERWRITE), "");
        int[] occ3 = {BpPlan.OUT_OF_WORLD};
        check("월드 높이 밖 → 거부", !BpPlan.check(1, i -> occ3[i], BpPlan.Policy.OVERWRITE, 10).ok(BpPlan.Policy.OVERWRITE), "");
        check("정책 번호 범위 밖 → 안전 배치(기본값)", BpPlan.Policy.of(7) == BpPlan.Policy.SAFE && BpPlan.Policy.of(-1) == BpPlan.Policy.SAFE, "");

        System.out.println("B5 배치 순서·크기 제한·이름 정리");
        int[] ox = {0, 0, 0, 0}, oy = {2, 0, 1, 0}, oz = {0, 0, 0, 1}, kl = {0, 2, 0, 1};
        int[] ord = BpPlan.order(4, ox, oy, oz, kl);
        check("등급(꽉 찬 블록→붙는 블록→기계) → 아래층부터", ord[0] == 2 && ord[1] == 0 && ord[2] == 3 && ord[3] == 1, java.util.Arrays.toString(ord));
        check("영역 257칸 → 거부", BpPlan.regionError(0, 0, 0, 256, 0, 0) != null, "");
        check("영역 256×64×256 (4,194,304칸) → 부피 초과", BpPlan.regionError(0, 0, 0, 255, 63, 255) != null, "");
        check("영역 200×50×200 → 허용", BpPlan.regionError(0, 0, 0, 199, 49, 199) == null, "");
        check("설계도 크기 0 → 오류", BpPlan.sizeError(0, 1, 1, 0) != null, "");
        check("블록 수 > 부피 → 오류", BpPlan.sizeError(2, 2, 2, 9) != null, "");
        check("이름 정리: 서식 코드·제어 문자 제거, 길이 제한", BpPlan.clean("§c공장\n이름" + "x".repeat(100), 10).equals("공장이름xxxxxx"), BpPlan.clean("§c공장\n이름" + "x".repeat(100), 10));

        System.out.println("B6 고정 설계도 파일 검사 + 기계 네트워크");
        Path dir = Path.of("src/main/resources/data/jjk/blueprints");
        String[] ids = {"test_power_room", "steam_power_plant", "automatic_smelting_factory", "large_manufacturing_factory", "logistics_center", "world_engine"};
        Map<String, Bp> bps = new HashMap<>();
        for (String id : ids) {
            Path f = dir.resolve(id + ".snbt");
            if (!Files.exists(f)) { check(id + " 파일 존재", false, f.toString()); continue; }
            Bp b = Bp.parse(Files.readString(f, StandardCharsets.UTF_8));
            bps.put(id, b);
            String err = b.validate();
            check(id + " 형식 (" + b.sx + "×" + b.sy + "×" + b.sz + ", 블록 " + b.n + ")", err == null && b.id.equals("jjk:" + id), err == null ? "" : err);
            Result r = simulate(b, new BpTransform(0, false, b.sx, b.sy, b.sz), 60);
            check(id + " 기계 네트워크 잠김 없음", r.jammed == 0, "기계 " + r.parts + " · 네트워크 " + r.nets + " · 링크 " + r.links);
        }

        System.out.println("B7 회전·반전 후 기계 네트워크 동일성 (실제 물리 엔진, 200스텝)");
        for (String id : new String[]{"test_power_room", "world_engine", "steam_power_plant"}) {
            Bp b = bps.get(id);
            if (b == null) continue;
            Result base = simulate(b, new BpTransform(0, false, b.sx, b.sy, b.sz), 200);
            for (int r = 0; r < 4; r++) for (int m = 0; m < 2; m++) {
                if (r == 0 && m == 0) continue;
                BpTransform t = new BpTransform(r, m == 1, b.sx, b.sy, b.sz);
                Result res = simulate(b, t, 200);
                double worst = 0;
                for (int i = 0; i < b.n; i++) {
                    if (!base.omega.containsKey(i)) continue;
                    double want = t.spinFactor(b.axis(i)) * base.omega.get(i), got = res.omega.get(i);
                    worst = Math.max(worst, Math.abs(got - want) / Math.max(1e-9, Math.abs(want)));
                }
                boolean ok = res.links == base.links && res.nets == base.nets && res.jammed == 0 && worst < 1e-9;
                check(id + " " + t.label(), ok, String.format("링크 %d/%d 네트워크 %d/%d 최대 오차 %.1e, 팬 %.1f rad/s", res.links, base.links, res.nets, base.nets, worst, res.fanOmega));
            }
        }
        Bp room = bps.get("test_power_room");
        if (room != null) {
            Result r = simulate(room, new BpTransform(0, false, room.sx, room.sy, room.sz), 200);
            check("시험용 동력실: 기어가 실제로 돈다 (팬 회전)", Math.abs(r.fanOmega) > 1, String.format("팬 %.1f rad/s", r.fanOmega));
            check("시험용 동력실: 12T↔28T 감속비 −12/28", Math.abs(r.ratio - (-12.0 / 28)) < 1e-9, String.format("ω(28T)/ω(12T) = %.6f", r.ratio));
        }

        System.out.printf("%n결과: %d 통과, %d 실패%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    // ───────────── 설계도 파일 (SNBT) 간이 해석 — 생성기가 쓰는 한 줄 한 블록 형식 ─────────────
    static final class Bp {
        String id; int sx, sy, sz, n;
        List<String> names = new ArrayList<>(); List<Integer> axes = new ArrayList<>();
        int[] x, y, z, s;
        int axis(int i) { return axes.get(s[i]); }
        String name(int i) { return names.get(s[i]); }

        static Bp parse(String text) {
            Bp b = new Bp();
            Matcher m = Pattern.compile("id: \"([^\"]+)\"").matcher(text); if (m.find()) b.id = m.group(1);
            m = Pattern.compile("size: \\[I; (\\d+), (\\d+), (\\d+)]").matcher(text);
            if (m.find()) { b.sx = Integer.parseInt(m.group(1)); b.sy = Integer.parseInt(m.group(2)); b.sz = Integer.parseInt(m.group(3)); }
            String pal = text.substring(text.indexOf("palette: ["), text.indexOf("blocks: [I;"));
            m = Pattern.compile("\\{Name: \"([^\"]+)\"(?:, Properties: \\{([^}]*)})?}").matcher(pal);
            while (m.find()) {
                b.names.add(m.group(1));
                Matcher ax = Pattern.compile("axis: \"([xyz])\"").matcher(m.group(2) == null ? "" : m.group(2));
                b.axes.add(ax.find() ? "xyz".indexOf(ax.group(1)) : 1);
            }
            String blk = text.substring(text.indexOf("blocks: [I;"), text.indexOf("block_entities"));
            m = Pattern.compile("(?m)^\\s+(\\d+), (\\d+), (\\d+), (\\d+),?$").matcher(blk);
            List<int[]> rows = new ArrayList<>();
            while (m.find()) rows.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))});
            b.n = rows.size(); b.x = new int[b.n]; b.y = new int[b.n]; b.z = new int[b.n]; b.s = new int[b.n];
            for (int i = 0; i < b.n; i++) { b.x[i] = rows.get(i)[0]; b.y[i] = rows.get(i)[1]; b.z[i] = rows.get(i)[2]; b.s[i] = rows.get(i)[3]; }
            return b;
        }

        String validate() {
            String se = BpPlan.sizeError(sx, sy, sz, n);
            if (se != null) return se;
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if (x[i] < 0 || y[i] < 0 || z[i] < 0 || x[i] >= sx || y[i] >= sy || z[i] >= sz) return "범위 밖 블록 #" + i;
                if (s[i] < 0 || s[i] >= names.size()) return "팔레트 번호 오류 #" + i;
                if (!seen.add(((long) x[i] << 40) | ((long) y[i] << 20) | z[i])) return "중복 위치 #" + i;
            }
            if (n == 0) return "블록 없음";
            return null;
        }
    }

    record Result(int parts, int links, int nets, int jammed, Map<Integer, Double> omega, double fanOmega, double ratio) {}

    /** 설계도의 기계 블록을 변환해 놓고 PhysWorld 와 같은 이웃 규칙으로 연결한 뒤 물리 엔진을 돌린다. */
    static Result simulate(Bp b, BpTransform t, int steps) {
        Sim sim = new Sim();
        Map<Long, Integer> at = new HashMap<>();
        Map<Integer, Part> parts = new HashMap<>();
        int[] o = new int[3];
        int[] tx = new int[b.n], ty = new int[b.n], tz = new int[b.n];
        for (int i = 0; i < b.n; i++) {
            String nm = b.name(i);
            if (!nm.startsWith("jjk:mech_")) continue;
            Part p = MachineSpecs.create(nm.substring(4));
            if (p == null) continue;
            if (p instanceof Parts.Motor mo) mo.setDirection(t.spinFactor(b.axis(i)));   // 설계도의 dir=1 을 변환 (PlaceJob.restore 와 같은 규칙)
            t.apply(b.x[i], b.y[i], b.z[i], o);
            tx[i] = o[0]; ty[i] = o[1]; tz[i] = o[2];
            sim.add(p); parts.put(i, p);
            at.put(key(o[0], o[1], o[2]), i);
        }
        int links = 0;
        int[][] dirs = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int i : parts.keySet()) {
            for (int d = 0; d < 3; d++) {
                Integer j = at.get(key(tx[i] + dirs[d][0], ty[i] + dirs[d][1], tz[i] + dirs[d][2]));
                if (j == null) continue;
                int ai = t.axis(b.axis(i)), aj = t.axis(b.axis(j));
                MechLink l = GridRules.link(parts.get(i).ports().get(0), parts.get(j).ports().get(0), ai, aj, d,
                        MachineSpecs.teeth(b.name(i).substring(4)), MachineSpecs.teeth(b.name(j).substring(4)));
                if (l != null) { sim.link(l); links++; }
            }
        }
        for (int k = 0; k < steps; k++) sim.step(0.05);
        int jam = 0;
        for (Net n : sim.nets) if (n.jammed) jam++;
        Map<Integer, Double> om = new HashMap<>();
        double fan = 0; Part s12 = null, l28 = null;
        for (Map.Entry<Integer, Part> e : parts.entrySet()) {
            om.put(e.getKey(), e.getValue().omega);
            if (b.name(e.getKey()).equals("jjk:mech_fan") && fan == 0) fan = e.getValue().omega;
            if (b.name(e.getKey()).equals("jjk:mech_gear_s") && s12 == null) s12 = e.getValue();
            if (b.name(e.getKey()).equals("jjk:mech_gear_l") && l28 == null) l28 = e.getValue();
        }
        double ratio = s12 != null && l28 != null && s12.omega != 0 ? l28.omega / s12.omega : Double.NaN;
        return new Result(parts.size(), links, sim.nets.size(), jam, om, fan, ratio);
    }

    static long key(int x, int y, int z) { return ((long) (x + 1000) << 42) | ((long) (y + 1000) << 21) | (z + 1000); }
}
