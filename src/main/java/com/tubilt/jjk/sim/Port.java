package com.tubilt.jjk.sim;

import java.util.ArrayList;
import java.util.List;

/** 부품의 연결 단자. 도메인(기계/유체/열/전기/제어)별로 하나 이상 가지며, 링크로 다른 부품의 같은 도메인 포트와 이어진다. */
public final class Port {
    public final Part owner;
    public final Domain domain;
    public final String name;
    final List<Link> links = new ArrayList<>(2);

    public Port(Part owner, Domain domain, String name) { this.owner = owner; this.domain = domain; this.name = name; }
    public List<Link> links() { return links; }
}
