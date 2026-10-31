package com.tubilt.jjk.sim;

/** 3단계(회전축·기어·베어링)에 필요한 기본 기계 부품. */
public final class Parts {
    private Parts() {}

    /** 회전축. 강체로 취급하고, 비틀림각은 통과 토크/비틀림 강성으로 표시용 계산만 한다. */
    public static final class Shaft extends Part {
        public final Port m = port(Domain.MECHANICAL, "m");
        final double J; public double torsionStiffness = 5e4; // N·m/rad
        public Shaft(String name, double J, double rated) { super(name); this.J = J; this.ratedTorque = rated; }
        public double inertia() { return J; }
        public double twist() { return linkTorque / torsionStiffness; }
    }

    /** 플라이휠. 큰 관성 + 풍손(속도 비례 항력). */
    public static final class Flywheel extends Part {
        public final Port m = port(Domain.MECHANICAL, "m");
        final double J, windage;
        public Flywheel(String name, double J, double windage, double rated) { super(name); this.J = J; this.windage = windage; this.ratedTorque = rated; }
        public double inertia() { return J; }
        @Override public void torque(double w, double[] o) { o[0] = 0; o[1] = -windage; }
    }

    /** 기어. 잇수와 마모를 가지며 마모될수록 쿨롱 마찰이 늘고 결국 이가 부러진다. */
    public static final class Gear extends Part {
        public final Port m = port(Domain.MECHANICAL, "m");
        public final int teeth; final double J; final double c0;
        public static double WEAR_K = 2e-8;
        public Gear(String name, int teeth, double J, double rated) { super(name); this.teeth = teeth; this.J = J; this.c0 = 0.02; this.ratedTorque = rated; this.unbalance = 1e-4; }
        public double inertia() { return J; }
        @Override public void torque(double w, double[] o) { o[0] = 0; o[1] = coulombS(c0 * (1 + 5 * wear), w); }
        @Override public void damage(double dt, Net n) {
            super.damage(dt, n);
            double s = ratedTorque == Double.POSITIVE_INFINITY ? 0 : linkTorque / ratedTorque;
            wear += Math.abs(omega) / (2 * Math.PI) * dt * (0.05 + s * s) * WEAR_K * 1e1;
        }
    }

    /** 전동기(토크-속도 직선 특성). τ = 스로틀 × T_stall × (1 − ω/ω_무부하). */
    public static final class Motor extends Part {
        public final Port m = port(Domain.MECHANICAL, "m");
        final double J, tStall, wFree; private double throttle = 1; private int dir = 1;
        public Motor(String name, double J, double tStall, double wFree, double rated) { super(name); this.J = J; this.tStall = tStall; this.wFree = wFree; this.ratedTorque = rated; }
        public double inertia() { return J; }
        public double throttle() { return throttle; }
        public int direction() { return dir; }
        public void setThrottle(double t) { throttle = Math.max(0, Math.min(1, t)); changed(); }
        public void setDirection(int d) { dir = d < 0 ? -1 : 1; changed(); }
        @Override public void torque(double w, double[] o) { o[0] = dir * throttle * tStall; o[1] = -throttle * tStall / wFree; }
        @Override protected double[] extra() { return new double[]{throttle, dir}; }
        @Override protected void loadExtra(double[] e) { throttle = e[0]; if (e.length > 1) dir = e[1] < 0 ? -1 : 1; }
    }

    /** 부하. τ = −k·|ω|·ω (팬/펌프형 2차 저항) − c·tanh(ω/ω0) (제동형). */
    public static final class Load extends Part {
        public final Port m = port(Domain.MECHANICAL, "m");
        final double J, kQuad, brake; private double scale = 1;
        public Load(String name, double J, double kQuad, double brake, double rated) { super(name); this.J = J; this.kQuad = kQuad; this.brake = brake; this.ratedTorque = rated; }
        public double inertia() { return J; }
        public void setScale(double s) { scale = Math.max(0, s); changed(); }
        @Override public void torque(double w, double[] o) { o[0] = 0; o[1] = scale * (-kQuad * Math.abs(w) + (brake > 0 ? coulombS(brake, w) : 0)); }
        @Override protected double[] extra() { return new double[]{scale}; }
        @Override protected void loadExtra(double[] e) { scale = e[0]; }
    }

    /**
     * 베어링. 점성 b + 쿨롱 c 마찰(윤활유와 마모에 따라 증가), 마찰열로 온도 상승, 윤활유 소모, 진동에 의한 피로.
     * 마찰로 소모된 에너지는 그대로 열 에너지(heatJ)가 된다.
     */
    public static final class Bearing extends Part {
        public final Port m = port(Domain.MECHANICAL, "m");
        final double b, c, hA, C;
        public double lube = 1, heatJ, pPrev;
        public double lubeUse = 1e-5, wearK = 1e-8, tLimit = 150, ambient = 20;
        public Bearing(String name, double b, double c, double hA, double heatCapacity) { super(name); this.b = b; this.c = c; this.hA = hA; this.C = heatCapacity; }
        public double inertia() { return 1e-4; }
        public void refill() { lube = 1; changed(); }
        @Override public void torque(double w, double[] o) {
            double ce = c * (1 + 4 * wear) * (1 + 3 * (1 - lube));
            o[0] = 0; o[1] = -b + coulombS(ce, w);
        }
        @Override public void damage(double dt, Net n) {
            double[] as = new double[2]; torque(omega, as);
            double p = -as[1] * omega * omega;
            heatJ += 0.5 * (p + pPrev) * dt; pPrev = p;
            double tInf = ambient + p / hA;
            temp = tInf + (temp - tInf) * Math.exp(-hA * dt / C);
            double rev = Math.abs(omega) / (2 * Math.PI) * dt;
            lube = Math.max(0, lube - rev * lubeUse);
            wear += rev * wearK * (1 + 20 * (1 - lube));
            if (temp > tLimit) fatigue += dt * (temp - tLimit) / 1000;
            fatigue += dt * 1e-7 * n.vibration;
        }
        @Override protected double[] extra() { return new double[]{lube, heatJ, pPrev}; }
        @Override protected void loadExtra(double[] e) { lube = e[0]; heatJ = e[1]; pPrev = e[2]; }
    }
}
