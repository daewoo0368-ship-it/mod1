package com.tubilt.jjk.sim;

/**
 * 기계 연결. ω_b = ratio · ω_a.
 *  · 커플링(RIGID): 비율 1, 효율 1 — 같은 축
 *  · 기어 맞물림(GEAR): 비율 = −Na/Nb (외접이라 회전 방향 반전), 맞물림 효율 η
 *  · 클러치(CLUTCH): 결합도 0..1. 완전 결합(≥0.999)이면 커플링, 아니면 미끄러지며 최대 토크 × 결합도 만큼만 전달.
 */
public final class MechLink extends Link {
    public enum Kind { RIGID, GEAR, CLUTCH }

    public final Kind kind;
    public final double ratio;       // ω_b / ω_a  (클러치는 1)
    public final double efficiency;  // 맞물림 효율
    private double engagement = 1;   // 클러치 결합도
    public final double torqueMax;   // 클러치 최대 전달 토크 (N·m)
    double slipPower;                // 이번 틱의 미끄럼 손실 동력 (W) — 이후 열 시스템 연결용

    private MechLink(Port a, Port b, Kind kind, double ratio, double eff, double tmax) {
        super(a, b); this.kind = kind; this.ratio = ratio; this.efficiency = eff; this.torqueMax = tmax;
    }

    public static MechLink coupling(Port a, Port b) { return new MechLink(a, b, Kind.RIGID, 1, 1, 0); }
    public static MechLink mesh(Port a, Port b, int teethA, int teethB, double efficiency) {
        return new MechLink(a, b, Kind.GEAR, -(double) teethA / teethB, efficiency, 0);
    }
    public static MechLink clutch(Port a, Port b, double torqueMax) { return new MechLink(a, b, Kind.CLUTCH, 1, 1, torqueMax); }

    public double engagement() { return engagement; }
    /** 결합도를 바꾼다. 완전 결합 여부가 바뀌면 네트워크를 다시 구성해야 하므로 true 를 돌려준다. */
    public boolean setEngagement(double e) {
        boolean before = isRigid();
        engagement = Math.max(0, Math.min(1, e));
        return before != isRigid();
    }
    /** 구동 구조에 포함되는(축으로 묶이는) 링크인가. */
    public boolean isRigid() { return kind != Kind.CLUTCH || engagement >= 0.999; }
    public double slipPower() { return slipPower; }
}
