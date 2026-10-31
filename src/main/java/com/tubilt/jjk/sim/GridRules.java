package com.tubilt.jjk.sim;

/**
 * 블록 격자에서 이웃한 두 기계 블록이 어떻게 연결되는지 정하는 규칙 (Minecraft 비의존 → 단위 테스트 가능).
 *  · 회전축 방향이 같은 블록끼리만 연결
 *  · 축 방향으로 이웃 → 커플링(같은 축)
 *  · 축에 수직으로 이웃한 두 기어 → 잇수 합이 40(= 중심 거리 1블록, 피치 반지름 = 잇수/40 블록)일 때만 맞물림
 */
public final class GridRules {
    private GridRules() {}
    public static final int MESH_TEETH_SUM = 40;
    public static final double MESH_EFFICIENCY = 0.97;

    /** 축 번호: X=0, Y=1, Z=2 (Direction.Axis 의 ordinal 과 동일). 연결이 안 되면 null. */
    public static MechLink link(Port pa, Port pb, int axisA, int axisB, int dirAxis, int teethA, int teethB) {
        if (axisA != axisB) return null;
        if (dirAxis == axisA) return MechLink.coupling(pa, pb);
        if (teethA > 0 && teethB > 0 && teethA + teethB == MESH_TEETH_SUM) return MechLink.mesh(pa, pb, teethA, teethB, MESH_EFFICIENCY);
        return null;
    }
}
