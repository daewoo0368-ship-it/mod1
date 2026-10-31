package com.tubilt.jjk.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * 시뮬레이션 세계. 부품과 링크의 토폴로지를 들고, 연결된 기계 네트워크(Net) 단위로 계산한다.
 *  · 토폴로지가 바뀔 때만 네트워크를 다시 구성(BFS 신장 트리로 기어비 전파, 모순이면 잠김)
 *  · 네트워크당 자유도 1개(루트 ω) — 선형화 토크 τ≈a+s·ω 로 정확한 지수 적분
 *  · 정지·무구동 네트워크는 잠들어 계산 생략
 *  · 부품 파손 시 링크가 끊겨 네트워크가 갈라진다 (연쇄 고장의 바탕)
 */
public final class Sim {
    public static double SLIP_W = 0.5;   // 클러치 미끄럼 정규화 속도 (rad/s)

    public final List<Part> parts = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    public final List<Net> nets = new ArrayList<>();
    public final List<String> events = new ArrayList<>();
    public double time;
    public int solves;           // 누적 네트워크 풀이 횟수 (성능 지표)
    boolean dirty = true;
    private final List<MechLink> slips = new ArrayList<>();
    private final double[] as = new double[2];

    public <P extends Part> P add(P p) { p.id = parts.size(); p.sim = this; parts.add(p); dirty = true; return p; }
    public <L extends Link> L link(L l) { links.add(l); l.a.links.add(l); l.b.links.add(l); dirty = true; return l; }
    public void unlink(Link l) { if (l.removed) return; l.removed = true; l.a.links.remove(l); l.b.links.remove(l); dirty = true; }
    public void setEngagement(MechLink l, double e) {
        if (l.setEngagement(e)) dirty = true;
        if (l.a.owner.net != null) l.a.owner.net.asleep = false;
        if (l.b.owner.net != null) l.b.owner.net.asleep = false;
    }

    public void breakPart(Part p, boolean log) {
        if (p.broken) return;
        p.broken = true;
        for (Port po : new ArrayList<>(p.ports)) for (Link l : new ArrayList<>(po.links)) unlink(l);
        if (log) events.add(String.format("t=%.2fs %s(#%d) 파손", time, p.name, p.id));
        dirty = true;
    }

    /** 부품을 세계에서 완전히 제거한다(블록이 사라지거나 청크가 언로드될 때). 링크도 모두 끊는다. */
    public void remove(Part p) {
        if (p.sim != this) return;
        for (Port po : new ArrayList<>(p.ports)) for (Link l : new ArrayList<>(po.links)) unlink(l);
        parts.remove(p); p.sim = null; p.net = null; dirty = true;
    }

    public double kineticEnergy() { double e = 0; for (Part p : parts) e += 0.5 * p.inertia() * p.omega * p.omega; return e; }

    // ───────────── 네트워크 구성 ─────────────
    private void rebuild() {
        dirty = false; nets.clear(); slips.clear();
        for (Part p : parts) { p.net = null; p.cA = 0; p.cS = 0; }
        for (Part root : parts) {
            if (root.net != null || root.broken) continue;
            Net n = new Net(); root.net = n; root.r = 1; root.parent = null; root.parentEff = 1; n.parts.add(root);
            for (int head = 0; head < n.parts.size(); head++) {
                Part p = n.parts.get(head);
                for (Port port : p.ports) for (Link l : port.links) {
                    if (!(l instanceof MechLink m) || !m.isRigid()) continue;
                    Part q = l.other(port).owner; if (q == p) continue;
                    double rq = port == l.a ? p.r * m.ratio : p.r / m.ratio;
                    if (q.net == null) { q.net = n; q.r = rq; q.parent = p; q.parentEff = m.efficiency; n.parts.add(q); }
                    else if (Math.abs(q.r - rq) > 1e-9 * Math.max(Math.abs(q.r), Math.abs(rq))) n.jammed = true;
                }
            }
            double L = 0, J = 0;
            for (Part p : n.parts) { double j = Math.max(p.inertia(), 1e-9); L += j * p.omega * p.r; J += j * p.r * p.r; }
            n.inertia = J; n.angle = root.angle; n.omega = n.jammed ? 0 : L / J;   // 합쳐질 때 각운동량 보존(비탄성 결합)
            for (Part p : n.parts) { p.omega = p.r * n.omega; p.angle = p.r * n.angle; }
            nets.add(n);
        }
        for (Link l : links) if (!l.removed && l instanceof MechLink m && m.kind == MechLink.Kind.CLUTCH && !m.isRigid()) {
            Net na = m.a.owner.net, nb = m.b.owner.net;
            if (na == null || nb == null || na == nb) continue;
            slips.add(m); na.hasClutch = nb.hasClutch = true; na.asleep = nb.asleep = false;
        }
    }

    // ───────────── 한 스텝 ─────────────
    public void step(double dt) {
        if (dirty) rebuild();
        time += dt;
        for (Net n : nets) if (!n.hasClutch && !n.asleep) solve(n, dt);
        if (!slips.isEmpty()) {
            int sub = 1;
            for (MechLink m : slips) {
                Part pa = m.a.owner, pb = m.b.owner;
                double tp = m.torqueMax * m.engagement() / SLIP_W;
                double ja = pa.net.inertia / (pa.r * pa.r), jb = pb.net.inertia / (pb.r * pb.r);
                sub = Math.max(sub, (int) Math.ceil(dt * tp * (1 / ja + 1 / jb) / 0.4));
            }
            sub = Math.min(sub, 1000);
            double h = dt / sub;
            for (int k = 0; k < sub; k++) {
                for (MechLink m : slips) { m.a.owner.cA = m.a.owner.cS = m.b.owner.cA = m.b.owner.cS = 0; }
                for (MechLink m : slips) {
                    Part pa = m.a.owner, pb = m.b.owner;
                    double d = pa.omega - pb.omega, th = Math.tanh(d / SLIP_W), tm = m.torqueMax * m.engagement();
                    double t0 = tm * th, tp = tm / SLIP_W * (1 - th * th);
                    m.slipPower = Math.abs(t0 * d);
                    pa.cA += -t0 + tp * pa.omega; pa.cS += -tp;
                    pb.cA += t0 + tp * pb.omega;  pb.cS += -tp;
                }
                for (Net n : nets) if (n.hasClutch) solve(n, h);
            }
        }
    }

    private void solve(Net n, double dt) {
        solves++;
        List<Part> ps = n.parts; int cnt = ps.size(); double w = n.omega;
        for (int i = 0; i < cnt; i++) {
            Part p = ps.get(i); as[0] = 0; as[1] = 0;
            p.torque(p.r * w, as);
            p.accA = (as[0] + p.cA) * p.r; p.accS = (as[1] + p.cS) * p.r * p.r;
            p.accJ = Math.max(p.inertia(), 1e-9) * p.r * p.r;
        }
        for (int i = cnt - 1; i >= 1; i--) {
            Part p = ps.get(i), q = p.parent;
            p.subA = p.accA; p.subS = p.accS; p.subJ = p.accJ;
            double f = 1;
            if (p.parentEff != 1) { double pw = (p.accA + p.accS * w) * w; f = (w == 0 || pw >= 0) ? p.parentEff : 1 / p.parentEff; }
            q.accA += f * p.accA; q.accS += f * p.accS; q.accJ += p.accJ;
        }
        Part root = ps.get(0);
        double A = root.accA, S = root.accS, J = root.accJ, w2;
        if (n.jammed) w2 = 0;
        else {
            if (S < -1e-12) { double wi = -A / S; w2 = wi + (w - wi) * Math.exp(S * dt / J); }   // 선형 ODE 의 정확해
            else w2 = w + A * dt / J;
            if (Math.abs(A) < 1e-9 && Math.abs(w2) < 1e-4) w2 = 0;
        }
        double alpha = (w2 - w) / dt;
        root.linkTorque = 0;
        for (int i = 1; i < cnt; i++) {
            Part p = ps.get(i);
            double cut = p.subA + p.subS * w2 - alpha * p.subJ;   // 부모 쪽에서 이 하위 트리로 들어가는 토크 (루트 기준)
            p.linkTorque = Math.abs(cut) / Math.abs(p.r);
            if (p.parent == root) root.linkTorque = Math.max(root.linkTorque, Math.abs(cut));
        }
        n.omega = w2; n.angle += 0.5 * (w + w2) * dt;
        double vib = 0;
        for (Part p : ps) { p.omega = p.r * w2; p.angle = p.r * n.angle; vib += p.imbalance() * p.omega * p.omega; }
        n.vibration = vib;
        List<Part> dead = null;
        for (Part p : ps) {
            p.damage(dt, n);
            if (p.fatigue >= 1 || p.wear >= 1) { if (dead == null) dead = new ArrayList<>(); dead.add(p); }
        }
        if (dead != null) for (Part p : dead) breakPart(p, true);
        n.asleep = !n.hasClutch && w2 == 0 && Math.abs(A) < 1e-12;
    }

    // ───────────── 저장 / 불러오기 ─────────────
    public static final class Snapshot { public double time; public double[][] parts; public double[] engagement; }

    public Snapshot snapshot() {
        Snapshot s = new Snapshot(); s.time = time;
        s.parts = new double[parts.size()][];
        for (int i = 0; i < parts.size(); i++) s.parts[i] = parts.get(i).save();
        s.engagement = new double[links.size()];
        for (int i = 0; i < links.size(); i++) s.engagement[i] = links.get(i) instanceof MechLink m ? m.engagement() : Double.NaN;
        return s;
    }

    /** 같은 순서로 같은 토폴로지를 다시 만든 뒤 호출한다. */
    public void restore(Snapshot s) {
        if (s.parts.length != parts.size()) throw new IllegalArgumentException("부품 수 불일치");
        time = s.time;
        for (int i = 0; i < parts.size(); i++) parts.get(i).load(s.parts[i]);
        for (int i = 0; i < links.size() && i < s.engagement.length; i++) if (links.get(i) instanceof MechLink m && !Double.isNaN(s.engagement[i])) m.setEngagement(s.engagement[i]);
        for (int i = 0; i < parts.size(); i++) if (s.parts[i][4] == 1) breakPart(parts.get(i), false);
        dirty = true;
    }
}
