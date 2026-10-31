package com.tubilt.jjk.sim;

import com.tubilt.jjk.sim.Parts.*;
import java.util.function.Supplier;

/** Minecraft/JUnit 없이 도는 시뮬레이션 코어 검증. 실행: ./run_simtest.sh */
public final class SimSelfTest {
    static int pass, fail;
    static void check(String name, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        System.out.printf("  [%s] %s  (%s)%n", ok ? "PASS" : "FAIL", name, detail);
    }
    static void near(String name, double got, double want, double relTol) {
        double err = Math.abs(got - want) / Math.max(Math.abs(want), 1e-12);
        check(name, err <= relTol, String.format("got=%.9g want=%.9g relErr=%.2e", got, want, err));
    }
    static double mod(double x, double m) { double r = x % m; return r < 0 ? r + m : r; }
    static void run(Sim s, double dt, int n) { for (int i = 0; i < n; i++) s.step(dt); }

    public static void main(String[] a) {
        System.out.println("T1 무마찰 전동기+플라이휠 기동 — 해석해와 비교");
        { Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 100, 300, 1e9)); var f = s.add(new Flywheel("fly", 4.5, 0, 1e9));
          s.link(MechLink.coupling(m.ports().get(0), f.ports().get(0)));
          run(s, 0.05, 60);
          near("ω(3s) = ω0(1−e^{−T t/(Jω0)})", f.omega, 300 * (1 - Math.exp(-100 * 3.0 / (5 * 300))), 1e-9); }

        System.out.println("T2 기어비 −0.5, 정상상태 속도, 효율 η");
        { double eta = .95, b = 0.4, T0 = 100, w0 = 300;
          Sim s = new Sim(); var m = s.add(new Motor("motor", .5, T0, w0, 1e9)); var ga = s.add(new Flywheel("A", .02, 0, 1e9)); var gb = s.add(new Flywheel("B", .05, 0, 1e9));
          var ld = s.add(new Load("load", .5, b, 0, 1e9));
          s.link(MechLink.coupling(m.ports().get(0), ga.ports().get(0))); s.link(MechLink.mesh(ga.ports().get(0), gb.ports().get(0), 10, 20, eta));
          s.link(MechLink.coupling(gb.ports().get(0), ld.ports().get(0)));
          run(s, .05, 6000);
          // 부하는 2차 저항이라 해석식 대신 정상상태 조건으로 검증: 입력동력 × η = 출력동력
          double wA = ga.omega, wB = gb.omega;
          near("ω_B/ω_A = −Na/Nb", wB / wA, -0.5, 1e-9);
          double tm = T0 * (1 - wA / w0), pin = tm * wA, pout = b * Math.abs(wB) * wB * wB;
          near("P_out/P_in = η", pout / pin, eta, 1e-4); }

        System.out.println("T3 일-에너지 정리 (무마찰)");
        { Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 100, 300, 1e9)); var f = s.add(new Flywheel("fly", 4.5, 0, 1e9));
          s.link(MechLink.coupling(m.ports().get(0), f.ports().get(0)));
          double W = 0, dt = .002; double[] o = new double[2];
          for (int i = 0; i < 5000; i++) { m.torque(m.omega, o); double t0 = o[0] + o[1] * m.omega, w0 = m.omega; s.step(dt); m.torque(m.omega, o); double t1 = o[0] + o[1] * m.omega; W += .5 * (t0 * w0 + t1 * m.omega) * dt; }
          near("ΔKE = ∫τω dt", s.kineticEnergy(), W, 1e-4); }

        System.out.println("T4 결합 시 각운동량 보존(비탄성), 에너지는 줄어든다");
        { Sim s = new Sim(); var A = s.add(new Flywheel("A", 2, 0, 1e9)); var B = s.add(new Flywheel("B", 6, 0, 1e9));
          A.omega = 100; s.step(.01); double ke0 = s.kineticEnergy(), l0 = 2 * 100.0;
          s.link(MechLink.mesh(A.ports().get(0), B.ports().get(0), 20, 20, 1)); s.step(1e-6);
          near("ω_A = J_A·100/(J_A+J_B)", A.omega, 25, 1e-9); near("ω_B = −ω_A", B.omega, -25, 1e-9);
          check("운동에너지 감소", s.kineticEnergy() < ke0, String.format("%.1f → %.1f J", ke0, s.kineticEnergy())); }

        System.out.println("T5 기어 3개 맞물린 고리는 잠기고, 구동하면 부품이 부서진다 / T6 4개 고리는 정상");
        { Sim s = new Sim(); Gear[] g = new Gear[3]; for (int i = 0; i < 3; i++) g[i] = s.add(new Gear("g" + i, 20, .05, 120));
          var m = s.add(new Motor("motor", .5, 400, 300, 1e9));
          for (int i = 0; i < 3; i++) s.link(MechLink.mesh(g[i].ports().get(0), g[(i + 1) % 3].ports().get(0), 20, 20, 1));
          s.link(MechLink.coupling(m.ports().get(0), g[0].ports().get(0)));
          s.step(.05);
          check("잠김 감지", g[0].net().jammed && g[0].omega == 0, "ω=" + g[0].omega);
          run(s, .05, 200);
          check("정적 과부하로 파손", s.parts.stream().anyMatch(p -> p.broken), s.events.toString()); }
        { Sim s = new Sim(); Gear[] g = new Gear[4]; for (int i = 0; i < 4; i++) g[i] = s.add(new Gear("g" + i, 20, .05, 1e9));
          var m = s.add(new Motor("motor", .5, 40, 300, 1e9));
          for (int i = 0; i < 4; i++) s.link(MechLink.mesh(g[i].ports().get(0), g[(i + 1) % 4].ports().get(0), 20, 20, 1));
          s.link(MechLink.coupling(m.ports().get(0), g[0].ports().get(0))); run(s, .05, 100);
          check("짝수 고리는 잠기지 않음", !g[0].net().jammed && g[0].omega > 1, "ω=" + g[0].omega); }

        System.out.println("T7 피로: 예측 수명과 실측 비교 / 내구한도 이하는 파손 없음 / 파손 후 네트워크 분리");
        { double life = fatigueRun(60, 1);   // 전동기 토크 대비 샤프트 정격 → 응력비 ≈ 1.2~
          check("피로 파손 발생", life > 0, "t=" + life + "s"); }
        { Sim s = plant(1000); run(s, 1, 100000);
          check("응력비 < 0.5 이면 10만 초 동안 파손 없음", s.parts.stream().noneMatch(p -> p.broken), "fatigue=" + s.parts.get(1).fatigue); }
        { Sim s = plant(45); run(s, 1, 400000);
          Part sh = s.parts.get(1);
          check("샤프트 파손", sh.broken, s.events.toString());
          Part mo = s.parts.get(0);
          check("축이 끊긴 뒤 전동기는 무부하 속도로 폭주", mo.omega > 0.99 * 300, "ω=" + mo.omega);
          check("네트워크가 둘로 갈라짐", mo.net() != s.parts.get(2).net(), "nets=" + s.nets.size()); }

        System.out.println("T8 베어링: 마찰열 = 잃은 운동에너지, 윤활 고갈 시 마찰·온도 증가");
        { Sim s = new Sim(); var f = s.add(new Flywheel("fly", 10, 0, 1e9)); var br = s.add(new Bearing("brg", .01, .2, 5, 500));
          s.link(MechLink.coupling(f.ports().get(0), br.ports().get(0))); f.omega = 100; br.omega = 100;
          s.step(1e-9); double ke0 = s.kineticEnergy(); br.heatJ = 0; br.pPrev = 0;
          run(s, .002, 3000);
          near("열량 = 운동에너지 감소", br.heatJ, ke0 - s.kineticEnergy(), 2e-3);
          check("온도 상승", br.temp > 20, "T=" + br.temp); }
        { Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 20, 300, 1e9)); var f = s.add(new Flywheel("fly", 4, 0, 1e9)); var br = s.add(new Bearing("brg", .02, .5, 2, 400));
          s.link(MechLink.coupling(m.ports().get(0), f.ports().get(0))); s.link(MechLink.coupling(f.ports().get(0), br.ports().get(0)));
          br.lubeUse = 2e-5; br.tLimit = 1e9; run(s, .5, 400); double t1 = br.temp, lube1 = br.lube;
          run(s, .5, 8000);
          check("윤활유 소모", br.lube < lube1 && br.lube == 0 || br.lube < .01, "lube=" + br.lube);
          check("윤활 부족 → 발열 증가", br.temp > t1, String.format("%.1f→%.1f °C", t1, br.temp));
          br.refill(); check("재급유", br.lube == 1, ""); }

        System.out.println("T9 진동 ∝ ω², 마모되면 증가");
        { Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 40, 300, 1e9)); var g = s.add(new Gear("g", 20, .05, 1e9)); g.unbalance = .01;
          s.link(MechLink.coupling(m.ports().get(0), g.ports().get(0)));
          m.setThrottle(.3); run(s, .05, 2000); double v1 = g.net().vibration, w1 = g.omega;
          m.setThrottle(1); run(s, .05, 2000); double v2 = g.net().vibration, w2 = g.omega;
          near("vib ∝ ω²", v2 / v1, (w2 * w2) / (w1 * w1), 1e-3);
          g.wear = .5; s.step(.05); check("마모 시 진동 증가", g.net().vibration > v2, v2 + "→" + g.net().vibration); }

        System.out.println("T10 클러치: 전달토크 상한, 완전 결합 시 동속");
        { Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 100, 300, 1e9)); var f = s.add(new Flywheel("fly", 4, 0, 1e9));
          var cl = s.link(MechLink.clutch(m.ports().get(0), f.ports().get(0), 80)); s.setEngagement(cl, .2);
          double maxAlpha = 0, prev = 0; for (int i = 0; i < 400; i++) { s.step(.05); double al = (f.omega - prev) / .05; maxAlpha = Math.max(maxAlpha, al); prev = f.omega; }
          check("플라이휠 가속도 ≤ T_max·e/J", maxAlpha <= 80 * .2 / 4 * 1.02, String.format("α_max=%.3f limit=%.3f", maxAlpha, 80 * .2 / 4));
          run(s, .05, 20000); check("충분히 지나면 동속(미끄럼 소멸)", Math.abs(f.omega - m.omega) < 0.5, String.format("%.2f vs %.2f", f.omega, m.omega));
          s.setEngagement(cl, 1); s.step(.05); check("완전 결합 시 같은 네트워크", m.net() == f.net() && Math.abs(m.omega - f.omega) < 1e-9, "") ; }

        System.out.println("T11 잠자기: 정지한 네트워크는 풀이 생략");
        { Sim s = new Sim(); var f = s.add(new Flywheel("fly", 10, 0, 1e9)); var br = s.add(new Bearing("brg", .01, .2, 5, 500));
          s.link(MechLink.coupling(f.ports().get(0), br.ports().get(0))); f.omega = 50; br.omega = 50;
          run(s, .5, 20000); int before = s.solves; run(s, .5, 100);
          check("정지 후 풀이 0회", f.omega == 0 && s.solves == before, "ω=" + f.omega + " solves+=" + (s.solves - before)); }

        System.out.println("T12 저장/불러오기: 재현성");
        { Supplier<Sim> mk = () -> plant(100); Sim s1 = mk.get(); run(s1, .05, 500); var snap = s1.snapshot(); run(s1, .05, 500);
          Sim s2 = mk.get(); s2.restore(snap); run(s2, .05, 500);
          double err = 0; for (int i = 0; i < s1.parts.size(); i++) err = Math.max(err, Math.abs(s1.parts.get(i).omega - s2.parts.get(i).omega) + Math.abs(s1.parts.get(i).fatigue - s2.parts.get(i).fatigue));
          check("복원 후 진행 결과 동일", err < 1e-9, "maxErr=" + err); }

        System.out.println("T13 규모: 5000 부품");
        { Sim s = new Sim(); for (int c = 0; c < 100; c++) chain(s, 16, 50);   // 100 네트워크 × 50부품
          run(s, .05, 20); long t = System.nanoTime(); run(s, .05, 200); double ms = (System.nanoTime() - t) / 1e6 / 200;
          check("100×50 부품: tick 당 < 20ms", ms < 20, String.format("%d parts, %d nets, %.3f ms/tick", s.parts.size(), s.nets.size(), ms)); }
        { Sim s = new Sim(); chain(s, 1666, 0);   // 5000 부품 단일 네트워크
          run(s, .05, 20); long t = System.nanoTime(); run(s, .05, 200); double ms = (System.nanoTime() - t) / 1e6 / 200;
          check("단일 5000부품 네트워크: tick 당 < 20ms", ms < 20, String.format("%d parts, %d nets, %.3f ms/tick", s.parts.size(), s.nets.size(), ms)); }

        System.out.println("T14 기어 위상/각도: 맞물린 두 기어의 누적각이 정확히 기어비를 따르고, 제거/역회전도 동작");
        { Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 50, 300, 1e9)); var ga = s.add(new Gear("a", 12, .05, 1e9)); var gb = s.add(new Gear("b", 28, .1, 1e9));
          s.link(MechLink.coupling(m.ports().get(0), ga.ports().get(0))); s.link(MechLink.mesh(ga.ports().get(0), gb.ports().get(0), 12, 28, .97));
          run(s, .05, 400);
          near("θ_b = −(12/28)·θ_a", gb.angle, -12.0 / 28.0 * ga.angle, 1e-12);
          check("각도 누적", ga.angle > 100, "θ_a=" + ga.angle);
          m.setDirection(-1); run(s, .05, 4000);
          check("역방향 전동기 → 음의 회전", ga.omega < -1, "ω_a=" + ga.omega);
          s.remove(gb); s.step(.05);
          check("부품 제거 후 네트워크 재구성", ga.net() != gb.net() && s.parts.size() == 2, "parts=" + s.parts.size()); }

        System.out.println("T15 블록 배치 시나리오(게임과 같은 연결 규칙): 전동기-축-12T기어⇄28T기어-축-팬");
        { Sim s = new Sim(); java.util.Map<String, Part> cell = new java.util.LinkedHashMap<>(); java.util.Map<String, int[]> info = new java.util.HashMap<>();
          Motor mo = s.add(new Motor("motor", 1.5, 120, 104.72, 300)); Shaft sh1 = s.add(new Shaft("s1", .2, 400)); Gear g12 = s.add(new Gear("g12", 12, .08, 300));
          Gear g28 = s.add(new Gear("g28", 28, .6, 1000)); Shaft sh2 = s.add(new Shaft("s2", .2, 400)); Load fan = s.add(new Load("fan", 2, .02, 0, 600));
          Object[][] grid = { {mo, 0,0,0, 0}, {sh1, 0,1,0, 0}, {g12, 0,2,0, 12}, {g28, 1,2,0, 28}, {sh2, 1,3,0, 0}, {fan, 1,4,0, 0} };   // 모두 Y축, {부품, x,y,z, 잇수}
          for (int i = 0; i < grid.length; i++) for (int j = i + 1; j < grid.length; j++) {
              int[] a1 = {(int) grid[i][1], (int) grid[i][2], (int) grid[i][3]}, b1 = {(int) grid[j][1], (int) grid[j][2], (int) grid[j][3]};
              int d = Math.abs(a1[0] - b1[0]) + Math.abs(a1[1] - b1[1]) + Math.abs(a1[2] - b1[2]); if (d != 1) continue;
              int dirAxis = a1[0] != b1[0] ? 0 : a1[1] != b1[1] ? 1 : 2;
              var l = GridRules.link(((Part) grid[i][0]).ports().get(0), ((Part) grid[j][0]).ports().get(0), 1, 1, dirAxis, (int) grid[i][4], (int) grid[j][4]);
              if (l != null) s.link(l); }
          run(s, .05, 3000);
          near("팬/전동기 속도비 = −12/28", fan.omega / mo.omega, -12.0 / 28.0, 1e-9);
          double rpm = mo.omega * 60 / (2 * Math.PI);
          check("전동기는 무부하(1000RPM)보다 느리게 정상상태", rpm > 100 && rpm < 999, String.format("%.1f RPM", rpm));
          double pin = (120 * (1 - mo.omega / 104.72)) * mo.omega, pout = .02 * fan.omega * fan.omega * Math.abs(fan.omega);
          near("전달 효율: P_out/P_in ≈ η·(마찰 손실 반영)", pout / pin, .97, 0.02);
          check("정상 부하에서 파손 없음", s.parts.stream().noneMatch(p -> p.broken), "fatigue=" + sh1.fatigue);
          check("기어 12T+20T 는 맞물리지 않음(잇수 합 ≠ 40)", GridRules.link(g12.ports().get(0), g28.ports().get(0), 1, 1, 0, 12, 20) == null, "");
          check("축이 다른 블록은 연결 안 됨", GridRules.link(g12.ports().get(0), g28.ports().get(0), 1, 0, 0, 12, 28) == null, ""); }

        System.out.println("T16 기어 이빨 위상: 홀짝 반피치 오프셋 + 기어비 각도 → 어느 각도에서도 이빨-홈이 맞물림");
        { int[][] pairs = {{12, 28}, {20, 20}, {28, 12}}; boolean ok = true; double worst = 0;
          for (int[] pr : pairs) for (double[] contact : new double[][]{{0, Math.PI}, {Math.PI / 2, 3 * Math.PI / 2}}) for (int step = 0; step < 400; step++) {
              int na = pr[0], nb = pr[1]; double thA = step * 0.0377, thB = -(double) na / nb * thA, offB = Math.PI / nb, pa = 2 * Math.PI / na, pb = 2 * Math.PI / nb;
              double fa = mod(contact[0] - thA, pa) / pa, fb = mod(contact[1] - (thB + offB), pb) / pb, sum = mod(fa + fb, 1.0);
              worst = Math.max(worst, Math.abs(sum - 0.5)); if (Math.abs(sum - 0.5) > 1e-9) ok = false; }
          check("이빨↔홈 위상 합 ≡ 0.5 (mod 1)", ok, "worst=" + worst); }

        System.out.printf("%n결과: %d 통과, %d 실패%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** 전동기(0) — 샤프트(1) — 부하(2). 샤프트 정격을 바꿔 응력비를 조절한다. */
    static Sim plant(double shaftRated) {
        Sim s = new Sim(); var m = s.add(new Motor("motor", .5, 100, 300, 1e9)); var sh = s.add(new Shaft("shaft", .1, shaftRated)); var ld = s.add(new Load("load", 1, 0.0008, 0, 1e9));
        s.link(MechLink.coupling(m.ports().get(0), sh.ports().get(0))); s.link(MechLink.coupling(sh.ports().get(0), ld.ports().get(0)));
        return s;
    }
    /** 정상상태에서 응력비로 예측한 수명과 실측 수명을 비교한다. 반환: 실측 파손 시각(s). */
    static double fatigueRun(double rated, double dt) {
        Sim s = plant(rated); run(s, .05, 4000);
        Part sh = s.parts.get(1); double st = sh.linkTorque / rated, w = sh.omega;
        double pred = Part.N_REF / (Math.pow(st, Part.EXPONENT) * w / (2 * Math.PI));
        if (st > Part.STATIC_LIMIT) { System.out.println("  (응력비 " + st + " > 정적한계 — 예측 생략)"); return -1; }
        double t = 0; while (!sh.broken && t < pred * 3) { s.step(dt); t += dt; }
        near("피로 수명 (응력비 " + String.format("%.3f", st) + ")", t, pred, 0.03);
        return t;
    }
    /** 전동기 — [기어 a — 기어 b — 샤프트] × stages — 부하 . 모든 기어 1:1, 단수가 짝수여야 방향이 맞다. */
    static void chain(Sim s, int stages, int unused) {
        Part prev = s.add(new Motor("motor", .5, 50, 300, 1e9));
        for (int k = 0; k < stages; k++) {
            var ga = s.add(new Gear("a", 20, .02, 1e9)); var gb = s.add(new Gear("b", 20, .02, 1e9)); var sh = s.add(new Shaft("s", .05, 1e9));
            s.link(MechLink.coupling(prev.ports().get(0), ga.ports().get(0))); s.link(MechLink.mesh(ga.ports().get(0), gb.ports().get(0), 20, 20, .99)); s.link(MechLink.coupling(gb.ports().get(0), sh.ports().get(0)));
            prev = sh;
        }
        var ld = s.add(new Load("load", .5, 0.001, 0, 1e9)); s.link(MechLink.coupling(prev.ports().get(0), ld.ports().get(0)));
    }
}
