package com.tubilt.jjk.sim;

import java.util.ArrayList;
import java.util.List;

/** 강체로 묶인 기계 부품 묶음(축·기어로 연결된 하나의 회전 네트워크). 자유도는 루트 기준 각속도 ω 하나뿐이다. */
public final class Net {
    public final List<Part> parts = new ArrayList<>();
    public double omega;        // 루트 기준 각속도 (rad/s)
    public double angle;        // 루트 기준 누적 회전각 (rad, 비정규화) — 각 부품 각도 = r × angle
    public double inertia;      // 루트 기준 환산 관성 (kg·m²)
    public double vibration;    // Σ 불평형량 × ω²  (진동 세기 지표)
    public boolean jammed;      // 기어비 모순으로 잠김
    public boolean asleep;      // 정지 + 구동 토크 0 → 계산 건너뜀
    public boolean hasClutch;   // 미끄러지는 클러치로 다른 네트워크와 결합
    public int size() { return parts.size(); }
}
