package com.tubilt.jjk.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * 시뮬레이션 부품의 기반 클래스. 한 부품은 하나의 강체로 회전하며(모든 기계 포트가 같은 ω),
 * 관성 J, 속도에 따른 토크 τ(ω) ≈ a + s·ω (s ≤ 0), 피로·마모 상태를 가진다.
 */
public abstract class Part {
    // ── 재료 튜닝 상수 (전역) ──
    public static double ENDURANCE = 0.5;     // 이 응력비 이하에서는 피로가 쌓이지 않음
    public static double N_REF = 2e6;         // 응력비 1.0 에서 파손까지의 회전수
    public static double EXPONENT = 4;        // Basquin 지수
    public static double STATIC_LIMIT = 1.5;  // 이 응력비를 넘으면 정적 과부하로 즉시 손상
    public static final double W0 = 0.05;     // 쿨롱 마찰 정규화 속도 (rad/s)

    int id = -1;
    Sim sim;
    public final String name;
    final List<Port> ports = new ArrayList<>(2);

    public double omega;          // 이 부품 기준 각속도 (rad/s)
    public double angle;          // 이 부품 기준 누적 회전각 (rad) — 서버에서 네트워크 각도 × 기어비로 갱신해 맞물림 위상을 유지
    public double linkTorque;     // 직전 스텝에서 이 부품을 통과한 토크 크기 (N·m)
    public double fatigue;        // 0..1 (1 = 피로 파괴)
    public double wear;           // 0..1 (1 = 마모 한계)
    public double temp = 20;      // °C
    public double ratedTorque = Double.POSITIVE_INFINITY;
    public double unbalance;      // 불평형량 (kg·m)
    public boolean broken;

    // 솔버 임시값
    Net net; double r = 1; Part parent; double parentEff = 1;
    double cA, cS, accA, accS, accJ, subA, subS, subJ;

    protected Part(String name) { this.name = name; }

    protected final Port port(Domain d, String portName) { Port p = new Port(this, d, portName); ports.add(p); return p; }
    public List<Port> ports() { return ports; }
    public int id() { return id; }
    public Net net() { return net; }

    /** 관성 모멘트 (kg·m²) */
    public abstract double inertia();
    /** 각속도 w(이 부품 기준)에서의 선형화 토크: out[0]=a, out[1]=s (τ ≈ a + s·w, s ≤ 0) */
    public void torque(double w, double[] out) { out[0] = 0; out[1] = 0; }
    /** 상태가 바뀌었음을 알려 잠든 네트워크를 깨운다. */
    protected final void changed() { if (net != null) net.asleep = false; }

    public double imbalance() { return unbalance * (1 + 4 * wear + 4 * fatigue); }

    /** 스텝 뒤 손상 계산. 기본 구현은 토크 기반 피로. */
    public void damage(double dt, Net n) {
        if (ratedTorque == Double.POSITIVE_INFINITY) return;
        double s = linkTorque / ratedTorque;
        if (s > ENDURANCE) fatigue += Math.abs(omega) / (2 * Math.PI) * dt * Math.pow(s, EXPONENT) / N_REF;
        if (s > STATIC_LIMIT) fatigue += dt * (s - STATIC_LIMIT) * 0.5;
    }

    /** 쿨롱 마찰 c(N·m) 의 시컨트 기울기(≤0): τ = −c·tanh(w/W0) = s·w */
    protected static double coulombS(double c, double w) {
        double x = w / W0;
        if (Math.abs(x) < 1e-6) return -c / W0;
        return -c * Math.tanh(x) / w;
    }

    // ── 저장/불러오기 ──
    protected double[] extra() { return new double[0]; }
    protected void loadExtra(double[] e) {}
    public final double[] save() {
        double[] x = extra(), s = new double[5 + x.length];
        s[0] = omega; s[1] = fatigue; s[2] = wear; s[3] = temp; s[4] = broken ? 1 : 0;
        System.arraycopy(x, 0, s, 5, x.length);
        return s;
    }
    final void load(double[] s) {
        omega = s[0]; fatigue = s[1]; wear = s[2]; temp = s[3];
        double[] x = new double[s.length - 5]; System.arraycopy(s, 5, x, 0, x.length); loadExtra(x);
    }
}
