package com.tubilt.jjk.sim;

/** 두 포트 사이의 연결. 도메인이 같아야 한다. 기계 연결은 {@link MechLink}. */
public abstract class Link {
    public final Port a, b;
    boolean removed;

    protected Link(Port a, Port b) {
        if (a.domain != b.domain) throw new IllegalArgumentException("포트 도메인이 다름: " + a.domain + " ≠ " + b.domain);
        this.a = a; this.b = b;
    }
    public Domain domain() { return a.domain; }
    public Port other(Port p) { return p == a ? b : a; }
}
