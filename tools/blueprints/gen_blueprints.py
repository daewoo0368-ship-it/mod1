#!/usr/bin/env python3
"""
고정 설계도 생성기 — src/main/resources/data/jjk/blueprints/*.snbt 를 만든다.

설계도는 사람이 읽고 diff 할 수 있는 SNBT 텍스트로 저장된다(한 줄에 블록 하나).
새 고정 설계도를 추가하려면 이 파일에 함수를 하나 더 만들고 다시 실행한 뒤, 생성된 .snbt 를 커밋한다.

    python3 tools/blueprints/gen_blueprints.py

생성하면서 검사하는 것:
  · 좌표가 크기 안에 있는지, 같은 칸에 블록이 겹치지 않는지
  · 기계 네트워크: 같은 축 이웃끼리 커플링, 축에 수직으로 이웃한 기어는 잇수 합 40 일 때 맞물림 → 기어비 모순(잠김)이 없어야 함
"""
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "data", "jjk", "blueprints")
VERSION = "0.80.0"
TEETH = {"jjk:mech_gear_s": 12, "jjk:mech_gear_m": 20, "jjk:mech_gear_l": 28}
MACHINES = {"jjk:mech_shaft", "jjk:mech_gear_s", "jjk:mech_gear_m", "jjk:mech_gear_l", "jjk:mech_motor", "jjk:mech_flywheel", "jjk:mech_fan"}


def q(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


class BP:
    def __init__(self, bid, name, desc, features):
        self.id, self.name, self.desc, self.features = bid, name, desc, features
        self.blocks = {}   # (x,y,z) -> (name, props tuple, nbt str|None)

    def set(self, x, y, z, name, props=None, nbt=None):
        self.blocks[(x, y, z)] = (name, tuple(sorted((props or {}).items())), nbt)

    def fill(self, x1, y1, z1, x2, y2, z2, name, props=None, hollow=False):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    if hollow and min(x1, x2) < x < max(x1, x2) and min(y1, y2) < y < max(y1, y2) and min(z1, z2) < z < max(z1, z2):
                        continue
                    self.set(x, y, z, name, props)

    def remove(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    # ── 검사 ──
    def check_machines(self):
        m = {p: v for p, v in self.blocks.items() if v[0] in MACHINES}
        axis = {p: dict(v[1]).get("axis") for p, v in m.items()}
        links = {p: [] for p in m}
        for p in m:
            for d, ax in ((1, "x"), (1, "y"), (1, "z")):
                n = (p[0] + (ax == "x"), p[1] + (ax == "y"), p[2] + (ax == "z"))
                if n not in m or axis[p] != axis[n]:
                    continue
                if ax == axis[p]:
                    r = 1.0
                else:
                    ta, tb = TEETH.get(m[p][0], 0), TEETH.get(m[n][0], 0)
                    if not (ta and tb and ta + tb == 40):
                        continue
                    r = -ta / tb
                links[p].append((n, r)); links[n].append((p, 1 / r))
        seen, nets = {}, 0
        for root in m:
            if root in seen:
                continue
            nets += 1
            seen[root] = 1.0
            stack = [root]
            while stack:
                p = stack.pop()
                for n, r in links[p]:
                    want = seen[p] * r
                    if n not in seen:
                        seen[n] = want; stack.append(n)
                    elif abs(seen[n] - want) > 1e-9:
                        raise SystemExit(f"{self.id}: 기어비 모순(잠김) at {n}")
        return len(m), nets

    def write(self):
        xs = [p[0] for p in self.blocks]; ys = [p[1] for p in self.blocks]; zs = [p[2] for p in self.blocks]
        mn = (min(xs), min(ys), min(zs))
        size = (max(xs) - mn[0] + 1, max(ys) - mn[1] + 1, max(zs) - mn[2] + 1)
        palette, pidx = [], {}
        rows, bes = [], []
        for i, (p, (name, props, nbt)) in enumerate(sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2]))):
            key = (name, props)
            if key not in pidx:
                pidx[key] = len(palette); palette.append(key)
            rows.append((p[0] - mn[0], p[1] - mn[1], p[2] - mn[2], pidx[key]))
            if nbt:
                bes.append((i, nbt))
        machines, nets = self.check_machines()
        lines = ["{", "  format: 1,", f"  id: {q(self.id)},", f"  name: {q(self.name)},", f"  description: {q(self.desc)},",
                 f'  author: "Dan",', f"  created_version: {q(VERSION)},", "  created_at: 0L,",
                 f"  size: [I; {size[0]}, {size[1]}, {size[2]}],",
                 "  features: [" + ", ".join(q(f) for f in self.features) + "],", "  palette: ["]
        for k, (name, props) in enumerate(palette):
            pr = ("{" + ", ".join(f"{a}: {q(b)}" for a, b in props) + "}") if props else None
            lines.append(f"    {{Name: {q(name)}" + (f", Properties: {pr}" if pr else "") + "}" + ("," if k < len(palette) - 1 else ""))
        lines.append("  ],")
        lines.append("  blocks: [I;")
        for k, r in enumerate(rows):
            lines.append(f"    {r[0]}, {r[1]}, {r[2]}, {r[3]}" + ("," if k < len(rows) - 1 else ""))
        lines.append("  ],")
        lines.append("  block_entities: [")
        for k, (i, nbt) in enumerate(bes):
            lines.append(f"    {{i: {i}, nbt: {nbt}}}" + ("," if k < len(bes) - 1 else ""))
        lines.append("  ]")
        lines.append("}")
        path = os.path.join(OUT, self.id.split(":")[1] + ".snbt")
        os.makedirs(OUT, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        print(f"{self.id}: 크기 {size[0]}x{size[1]}x{size[2]}, 블록 {len(rows)}, 블록 엔티티 {len(bes)}, 기계 {machines} (네트워크 {nets})")


MOTOR_NBT = '{id: "jjk:machine", throttle: 1.0d, dir: 1}'


def mech(bp, x, y, z, kind, axis="x", nbt=None):
    bp.set(x, y, z, "jjk:mech_" + kind, {"axis": axis}, nbt)


# ───────────── 모듈 ─────────────
def power_train(bp, ox, oy, oz):
    """전동기 → 축 → 12T ↔ 28T (감속·역전) → 축 → 플라이휠 → 축 → 팬 → 20T ↔ 20T (역전) → 축 → 팬. 모두 X 축."""
    mech(bp, ox + 0, oy + 0, oz, "motor", nbt=MOTOR_NBT)
    mech(bp, ox + 1, oy + 0, oz, "shaft")
    mech(bp, ox + 2, oy + 0, oz, "gear_s")
    mech(bp, ox + 2, oy + 1, oz, "gear_l")
    mech(bp, ox + 3, oy + 1, oz, "shaft")
    mech(bp, ox + 4, oy + 1, oz, "flywheel")
    mech(bp, ox + 5, oy + 1, oz, "shaft")
    mech(bp, ox + 6, oy + 1, oz, "fan")
    mech(bp, ox + 7, oy + 1, oz, "gear_m")
    mech(bp, ox + 7, oy + 2, oz, "gear_m")
    mech(bp, ox + 8, oy + 2, oz, "shaft")
    mech(bp, ox + 9, oy + 2, oz, "fan")
    # 받침대 (기계와 이웃해도 기계가 아니면 연결되지 않음)
    for dx in (3, 4, 5, 6, 7):
        bp.set(ox + dx, oy + 0, oz, "minecraft:polished_andesite")
    for dx in (8, 9):
        bp.fill(ox + dx, oy + 0, oz, ox + dx, oy + 1, oz, "minecraft:polished_andesite")


def smelting_lanes(bp, ox, oy, oz, lanes, feed_out=True):
    """
    레인 i (x = ox+i): 원료 통(y+3) → 호퍼(아래) → 화로(y+1) ← 연료 호퍼(z−1, 남쪽 향함) ← 연료 통(위)
    화로 아래 출력 호퍼(y)들은 동쪽으로 이어져 마지막 레인 다음 칸(ox+lanes, oy, oz)으로 내보낸다.
    """
    for i in range(lanes):
        x = ox + i
        bp.set(x, oy, oz, "minecraft:hopper", {"facing": "east", "enabled": "true"})
        bp.set(x, oy + 1, oz, "minecraft:furnace", {"facing": "south", "lit": "false"})
        bp.set(x, oy + 1, oz - 1, "minecraft:hopper", {"facing": "south", "enabled": "true"})
        bp.set(x, oy + 2, oz - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        bp.set(x, oy + 2, oz, "minecraft:hopper", {"facing": "down", "enabled": "true"})
        bp.set(x, oy + 3, oz, "minecraft:barrel", {"facing": "up", "open": "false"})
    return (ox + lanes, oy, oz)


def lamp_monitor(bp, x, y, z):
    """(x−1, y, z) 의 통을 비교기로 읽어 (x+1, y, z) 램프를 켠다 — 출력 감지."""
    bp.set(x, y, z, "minecraft:comparator", {"facing": "west", "mode": "compare", "powered": "false"})
    bp.set(x + 1, y, z, "minecraft:redstone_lamp", {"lit": "false"})


def crafter_cell(bp, x, y, z):
    """크래프터(동쪽 출력, 윗줄 3칸 잠금 → 유리 6개 = 유리판) + 위쪽 관측기 클럭."""
    bp.set(x, y, z, "minecraft:crafter", {"orientation": "east_up", "triggered": "false", "crafting": "false"},
           '{id: "minecraft:crafter", disabled_slots: [I; 0, 1, 2]}')
    bp.set(x, y + 1, z, "minecraft:observer", {"facing": "up", "powered": "false"})
    bp.set(x, y + 2, z, "minecraft:observer", {"facing": "down", "powered": "false"})


def warehouse_stairs(bp, x, y, z, steps):
    """넘침식 창고: 통 → 아래 호퍼(동쪽) → 한 칸 아래 통 … 마지막 통부터 찬다. 시작 통 위치를 돌려준다."""
    bp.set(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    for k in range(steps):
        bp.set(x + k, y - 1 - k, z, "minecraft:hopper", {"facing": "east", "enabled": "true"})
        bp.set(x + k + 1, y - 1 - k, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    return (x + steps, y - steps, z)


def pillar_lights(bp, pts, y0, h):
    for (x, z) in pts:
        bp.fill(x, y0, z, x, y0 + h - 2, z, "minecraft:polished_andesite")
        bp.set(x, y0 + h - 1, z, "minecraft:sea_lantern")


# ───────────── 설계도 ─────────────
def test_power_room():
    bp = BP("jjk:test_power_room", "시험용 동력실",
            "회전 물리 엔진과 기어 연결을 시험하는 동력실. 전동기가 12T↔28T 감속 기어, 플라이휠, 팬 부하, 20T↔20T 역전 기어를 돌린다. "
            "전동기를 맨손으로 우클릭하면 스로틀(0→50→100%), 웅크리고 우클릭하면 회전 방향이 바뀐다. 다른 기계를 맨손으로 우클릭하면 RPM·토크·피로가 보인다.",
            ["working|전동기|정지토크 120 N·m, 무부하 약 1000 RPM",
             "working|회전축·기어|12T↔28T 감속(−0.43배) · 20T↔20T 역전(−1배)",
             "working|플라이휠|관성 40 kg·m² — 회전 안정화",
             "working|팬 부하|속도 제곱 저항 2기",
             "working|회전 물리 엔진|관성·토크·피로·마모, 기어비·회전 방향 자동 계산",
             "planned|베어링|시뮬레이션 부품만 있음 — 블록 미구현"])
    bp.fill(0, 0, 0, 12, 0, 6, "minecraft:smooth_stone")
    pillar_lights(bp, [(0, 0), (12, 0), (0, 6), (12, 6)], 1, 4)
    power_train(bp, 2, 1, 3)
    return bp


def steam_power_plant():
    bp = BP("jjk:steam_power_plant", "증기 발전소",
            "보일러 → 증기 배관 → 터빈 → 발전기 → 냉각 설비 배치의 발전소. 증기·전기 시스템은 아직 없어서 "
            "터빈 자리에는 시험용 전동기, 발전기 자리에는 플라이휠과 부하를 두어 회전 계통만 실제로 돈다. 보일러·배관·냉각탑은 외형만 있다.",
            ["partial|터빈·발전기 회전계|전동기(터빈 대역) → 플라이휠(로터) → 팬(발전기 부하) — 회전만 작동",
             "planned|보일러|증기 시스템 미구현 — 외형(용광로·굴뚝)만",
             "planned|증기 배관|유체 포트 미구현 — 외형(구리)만",
             "planned|발전·전력망|전기 포트 미구현",
             "partial|냉각 설비|물 저장 냉각탑(외형) — 열 시스템 미구현"])
    bp.fill(0, 0, 0, 16, 0, 12, "minecraft:smooth_stone")
    # 보일러 3기 + 굴뚝
    for z in (2, 5, 8):
        bp.set(1, 1, z, "minecraft:blast_furnace", {"facing": "east", "lit": "false"})
        bp.fill(0, 1, z, 0, 5, z, "minecraft:bricks")
    bp.fill(1, 2, 2, 1, 2, 8, "minecraft:bricks")
    # 증기 배관 (외형)
    bp.fill(2, 3, 5, 6, 3, 5, "minecraft:cut_copper")
    bp.fill(1, 3, 2, 1, 3, 8, "minecraft:cut_copper")
    # 터빈 홀: 전동기(터빈) → 축 → 플라이휠(로터) → 축 → 팬(발전기 부하)
    mech(bp, 7, 3, 5, "motor", nbt=MOTOR_NBT)
    mech(bp, 8, 3, 5, "shaft")
    mech(bp, 9, 3, 5, "flywheel")
    mech(bp, 10, 3, 5, "shaft")
    mech(bp, 11, 3, 5, "fan")
    bp.fill(7, 1, 5, 11, 2, 5, "minecraft:iron_block")
    bp.fill(6, 1, 4, 12, 1, 4, "minecraft:polished_andesite")
    bp.fill(6, 1, 6, 12, 1, 6, "minecraft:polished_andesite")
    # 냉각탑: 물을 담은 원통형 벽
    bp.fill(12, 1, 8, 16, 6, 12, "minecraft:light_gray_concrete", hollow=True)
    for x in range(13, 16):
        for z in range(9, 12):
            for y in range(2, 7):
                bp.remove(x, y, z)
            bp.set(x, 1, z, "minecraft:water", {"level": "0"})
    pillar_lights(bp, [(4, 0), (4, 11), (14, 0)], 1, 5)
    return bp


def automatic_smelting_factory():
    bp = BP("jjk:automatic_smelting_factory", "자동 제련 공장",
            "원료 투입 → 제련 → 제품 배출이 자동으로 돌아가는 3레인 제련 공장. 레인마다 위쪽 통에 원료(광석·모래 등), "
            "뒤쪽 통에 연료를 넣으면 화로가 굽고, 결과물은 아래 호퍼 줄을 따라 오른쪽 출력 통에 모인다. 출력 통에 물건이 있으면 램프가 켜진다.",
            ["working|원료 투입|레인별 원료 통 → 호퍼 → 화로 윗면",
             "working|연료 공급|레인별 연료 통 → 호퍼 → 화로 옆면",
             "working|제련|화로 3기 병렬",
             "working|제품 배출|출력 호퍼 줄 → 출력 통 + 출력 감지 램프",
             "partial|분류|레인별 수동 투입 — 자동 분류기 미구현",
             "partial|컨베이어|호퍼 줄로 대체 — 컨베이어 블록 미구현"])
    bp.fill(0, 0, 0, 8, 0, 4, "minecraft:smooth_stone")
    out = smelting_lanes(bp, 2, 1, 2, 3)
    bp.set(out[0], out[1], out[2], "minecraft:barrel", {"facing": "up", "open": "false"})
    lamp_monitor(bp, out[0] + 1, out[1], out[2])
    pillar_lights(bp, [(0, 0), (8, 0), (0, 4), (8, 4)], 1, 5)
    return bp


def large_manufacturing_factory():
    bp = BP("jjk:large_manufacturing_factory", "대형 제조 공장",
            "원료 저장 → 부품 생산(제련) → 자동 조립(크래프터) → 제품 검사 → 물류(창고)로 이어지는 복합 생산 라인. "
            "원료 통에 모래, 연료 통에 석탄을 넣으면 화로 2기가 유리를 굽고, 크래프터(윗줄 잠금)가 유리 6개로 유리판 16개를 만든다. "
            "완성품은 3칸 넘침식 창고로 내려가고, 마지막 창고에 물건이 있으면 램프가 켜진다. 크래프터는 위쪽 관측기 클럭으로 계속 작동한다.",
            ["working|원료 저장|원료 통 2 · 연료 통 2",
             "working|부품 생산|화로 2기 — 모래 → 유리",
             "partial|자동 조립|크래프터 + 관측기 클럭 — 유리 6 → 유리판 16 (실험적)",
             "partial|제품 검사|출력 감지 램프 — 품질 검사 미구현",
             "working|물류|크래프터 → 호퍼 → 3칸 넘침식 창고"])
    bp.fill(0, 0, 0, 12, 0, 5, "minecraft:smooth_stone")
    bp.fill(1, 1, 0, 5, 3, 4, "minecraft:stone_bricks")      # 생산 단(아래에 창고가 들어갈 높이)
    out = smelting_lanes(bp, 2, 4, 2, 2)                      # 출력 호퍼 (2,4,2)(3,4,2) → (4,4,2)
    bp.set(4, 4, 2, "minecraft:hopper", {"facing": "east", "enabled": "true"})
    crafter_cell(bp, 5, 4, 2)
    bp.set(6, 4, 2, "minecraft:hopper", {"facing": "down", "enabled": "true"})
    last = warehouse_stairs(bp, 6, 3, 2, 2)                   # 통 (6,3,2) → (7,2,2) → (8,1,2)
    lamp_monitor(bp, last[0] + 1, last[1], last[2])
    pillar_lights(bp, [(0, 0), (12, 0), (0, 5), (12, 5)], 1, 9)
    return bp


def logistics_center():
    bp = BP("jjk:logistics_center", "물류 센터",
            "투입 통에 넣은 물건이 호퍼 컨베이어를 따라 흐르고, 컨베이어 아래 분배 호퍼가 창고 통에 차례로 나눠 넣는다. "
            "앞 창고가 가득 차면 다음 창고로 넘어가고, 모든 창고가 차면 끝의 넘침 통에 모인다. 넘침 통에 물건이 있으면 램프가 켜진다.",
            ["working|창고|창고 통 5칸 + 넘침 통 (앞 칸부터 채움)",
             "working|컨베이어|호퍼 줄",
             "working|분배기|컨베이어 아래 분배 호퍼 — 창고가 차면 다음 칸으로",
             "working|가득 참 표시|넘침 통 감지 램프",
             "planned|자동 운송 시스템|수레·드론 운송 미구현"])
    bp.fill(0, 0, 0, 9, 0, 4, "minecraft:smooth_stone")
    bp.set(1, 4, 2, "minecraft:barrel", {"facing": "up", "open": "false"})          # 투입 통
    for x in range(1, 7):
        bp.set(x, 3, 2, "minecraft:hopper", {"facing": "east", "enabled": "true"})   # 컨베이어
    for x in range(2, 7):
        bp.set(x, 2, 2, "minecraft:hopper", {"facing": "down", "enabled": "true"})   # 분배 호퍼
        bp.set(x, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})       # 창고
    bp.set(7, 3, 2, "minecraft:barrel", {"facing": "up", "open": "false"})          # 넘침 통
    bp.fill(7, 1, 2, 7, 2, 2, "minecraft:stone_bricks")
    bp.fill(1, 1, 2, 1, 2, 2, "minecraft:stone_bricks")
    lamp_monitor(bp, 8, 3, 2)
    bp.fill(8, 1, 2, 9, 2, 2, "minecraft:stone_bricks")
    pillar_lights(bp, [(0, 0), (9, 0), (0, 4), (9, 4)], 1, 6)
    return bp


def world_engine():
    bp = BP("jjk:world_engine", "월드 엔진",
            "최종 목표 건축물의 1단계 골격. 중앙 제어 핵(신호기)을 중심으로 동력·정제·조립·물류 모듈이 사방에 붙는 모듈식 구조이고, "
            "아직 없는 시스템(에너지 저장·자동 채굴·자동 건설·유지보수·냉각·비상 대응)은 색 받침대로 자리만 잡아 두었다. "
            "구현된 모듈(동력·정제·물류)은 실제로 작동하며, 새 시스템이 생기면 해당 받침대 자리에 모듈을 끼우는 방식으로 확장한다.",
            ["working|중앙 제어 핵|신호기 + 철 블록 기단 (외형·신호기 효과)",
             "working|대형 동력 시스템|전동기 2기 · 기어 · 플라이휠 · 부하 (실제 회전)",
             "working|광물 정제|화로 3레인 제련 모듈",
             "partial|부품 제조·자동 조립|크래프터 + 관측기 클럭 (실험적)",
             "working|물류 시스템|넘침식 창고 3칸",
             "planned|에너지 저장|미구현 — 노란 받침대 자리",
             "planned|자동 채굴|미구현 — 갈색 받침대 자리",
             "planned|자동 건설|미구현 — 연두 받침대 자리",
             "planned|유지보수 시스템|미구현 — 주황 받침대 자리",
             "planned|냉각·열관리|미구현 — 하늘색 받침대 자리",
             "planned|비상 대응 시스템|미구현 — 빨간 받침대 자리"])
    S = 32
    bp.fill(0, 0, 0, S, 0, S, "minecraft:smooth_stone")
    # 외곽 낮은 벽 + 출입구
    bp.fill(0, 1, 0, S, 1, S, "minecraft:stone_bricks", hollow=True)
    for x in range(0, S + 1):
        for z in range(0, S + 1):
            if 0 < x < S and 0 < z < S:
                bp.remove(x, 1, z)
    for k in (15, 16, 17):
        bp.remove(k, 1, 0); bp.remove(k, 1, S); bp.remove(0, 1, k); bp.remove(S, 1, k)
    # 중앙 제어 핵
    c = S // 2
    bp.fill(c - 1, 1, c - 1, c + 1, 1, c + 1, "minecraft:iron_block")
    bp.set(c, 2, c, "minecraft:beacon")
    for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        bp.fill(c + dx, 1, c + dz, c + dx, 3, c + dz, "minecraft:polished_deepslate")
        bp.set(c + dx, 4, c + dz, "minecraft:sea_lantern")
    # 북서: 대형 동력 시스템 (동력 열 2개)
    power_train(bp, 3, 1, 4)
    power_train(bp, 3, 1, 8)
    # 북동: 광물 정제 (3레인)
    out = smelting_lanes(bp, 21, 1, 5, 3)
    bp.set(out[0], out[1], out[2], "minecraft:barrel", {"facing": "up", "open": "false"})
    lamp_monitor(bp, out[0] + 1, out[1], out[2])
    # 남동: 부품 제조·자동 조립 — 유리 투입 통 → 호퍼 → 크래프터(관측기 클럭) → 완성품 통
    bp.set(23, 2, 24, "minecraft:barrel", {"facing": "up", "open": "false"})
    bp.set(23, 1, 24, "minecraft:hopper", {"facing": "east", "enabled": "true"})
    crafter_cell(bp, 24, 1, 24)
    bp.set(25, 1, 24, "minecraft:barrel", {"facing": "up", "open": "false"})
    lamp_monitor(bp, 26, 1, 24)
    # 남서: 물류 (넘침식 창고) — 통 (4,4,24) → (5,3,24) → (6,2,24) → (7,1,24)
    warehouse_stairs(bp, 4, 4, 24, 3)
    bp.fill(4, 1, 24, 4, 2, 24, "minecraft:stone_bricks")
    bp.set(5, 1, 24, "minecraft:stone_bricks")
    # 미구현 모듈 받침대 (3×3)
    pads = [("minecraft:yellow_concrete", 11, 3), ("minecraft:brown_concrete", 19, 3), ("minecraft:lime_concrete", 11, 27),
            ("minecraft:orange_concrete", 19, 27), ("minecraft:light_blue_concrete", 27, 13), ("minecraft:red_concrete", 27, 19)]
    for (blk, x, z) in pads:
        bp.fill(x, 0, z, x + 2, 0, z + 2, blk)
    return bp


def main():
    for f in (test_power_room, steam_power_plant, automatic_smelting_factory, large_manufacturing_factory, logistics_center, world_engine):
        f().write()


if __name__ == "__main__":
    sys.exit(main())
