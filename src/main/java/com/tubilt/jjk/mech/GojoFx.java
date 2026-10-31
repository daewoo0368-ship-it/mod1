package com.tubilt.jjk.mech;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 고죠 술식 연출 리메이크 — 데이터팩의 창·혁·무라사키 마커를 따라다니며 홀로그램으로 덧그린다.
 *  창(蒼)  : 푸른 코어 + 안쪽으로 빨려드는 궤도 고리 (영창판은 훨씬 크고 십자선을 따라다님)
 *  혁(赫)  : 붉은 코어 + 날아가는 궤적 빔 + 불꽃
 *  무라사키: 푸른·붉은 구가 모여드는 충전 → 보라 번개가 튀는 코어 → 굵은 궤적 빔
 * 판정·피해·쿨타임은 기존 데이터팩 그대로, 연출만 새로 얹는다.
 */
public final class GojoFx {
    private GojoFx() {}

    static final int BLUE = 0x2A6BFF, RED = 0xFF2A2A, PURPLE = 0xB050FF, WHITE = 0xFFFFFF, FOREVER = 20 * 60 * 60;

    static final class V {
        final Marker m; final ServerLevel l; final String tag; final int kind; final boolean strong;
        Vec3 last; int age = 0;
        V(Marker m, ServerLevel l, int kind, boolean strong) {
            this.m = m; this.l = l; this.kind = kind; this.strong = strong;
            this.tag = "jjk.gfx" + Integer.toHexString(m.getUUID().hashCode() & 0xFFFFFF);
            this.last = m.position();
        }
    }
    // kind: 0 창, 1 혁, 2 무라사키 중심, 3 파란 구, 4 붉은 구, 5 무라사키 빔
    static final Map<UUID, V> ACTIVE = new HashMap<>();
    static int kindOf(Marker m) {
        var t = m.getTags();
        if (t.contains("gojo.blue")) return 0;
        if (t.contains("gojo.red")) return 1;
        if (t.contains("gojo.center")) return 2;
        if (t.contains("gojo.orb_b")) return 3;
        if (t.contains("gojo.orb_r")) return 4;
        if (t.contains("gojo.purple")) return 5;
        return -1;
    }

    public static void tick(MinecraftServer server) {
        // 새 마커 찾기 (2틱마다)
        if (server.getTickCount() % 2 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel l = (ServerLevel) p.level();
                for (Marker m : l.getEntitiesOfClass(Marker.class, p.getBoundingBox().inflate(72), x -> x.isAlive() && !x.getTags().isEmpty())) {
                    if (ACTIVE.containsKey(m.getUUID())) continue;
                    int k = kindOf(m);
                    if (k < 0) continue;
                    V v = new V(m, l, k, m.getTags().contains("gojo.strong"));
                    ACTIVE.put(m.getUUID(), v);
                    spawn(v);
                }
            }
        }
        if (ACTIVE.isEmpty()) return;
        Iterator<V> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            V v = it.next();
            if (v.m.isRemoved() || !v.m.isAlive()) { end(v); it.remove(); continue; }
            update(v);
            v.last = v.m.position();
            v.age++;
        }
    }

    static void spawn(V v) {
        Vec3 at = v.m.position();
        double s = v.strong ? 1.0 : 0.55;
        switch (v.kind) {
            case 0 -> {
                core(v, "flare", BLUE, 3.6 * (v.strong ? 1.9 : 1));
                core(v, "flare", WHITE, 1.4 * (v.strong ? 1.9 : 1));
                Holo.sfx(v.l, at, "charge", 1.0f, v.strong ? 0.8f : 1.3f);
            }
            case 1 -> { core(v, "flare", RED, 3.2 * (v.strong ? 1.8 : 1)); core(v, "flare", WHITE, 1.2); }
            case 2 -> { core(v, "flare", PURPLE, 5.0); core(v, "flare", WHITE, 1.8); }
            case 3 -> core(v, "flare", BLUE, 2.2);
            case 4 -> core(v, "flare", RED, 2.2);
            case 5 -> { core(v, "flare", PURPLE, 9.0 * (v.strong ? 1.6 : 1)); core(v, "flare", WHITE, 3.4); }
            default -> { }
        }
    }
    static void core(V v, String fx, int rgb, double size) {
        Holo.sprite(v.l, v.tag, v.m.position(), fx, rgb, "center", Holo.bb(size, 0, Vec3.ZERO), FOREVER, 1)
                .follow(() -> v.m.position(), Vec3.ZERO);
    }

    static void update(V v) {
        Vec3 c = v.m.position(), last = v.last;
        ServerLevel l = v.l;
        int t = v.age;
        switch (v.kind) {
            case 0 -> {
                double R = v.strong ? 9 : 5;
                if (t % 3 == 0) Holo.ring(l, v.tag, c, R, 0.6, 22, 0.14, 0.03, BLUE, 10, 0, 2, (t * 0.31) % 3.1, 6, true);
                if (t % 6 == 3) Holo.ring(l, v.tag, c, R * 0.7, 0.4, 18, 0.1, 0.03, WHITE, 8, 0, 2, 1.57 + (t * 0.23) % 3.1, -6, true);
                if (v.strong && t % 10 == 0) Holo.shock(l, v.tag, c, 7, BLUE, 8);
            }
            case 1 -> {
                if (last.distanceToSqr(c) > 0.04) Holo.beam(l, v.tag, last, c, v.strong ? 0.9 : 0.5, RED, 1, 1, 4);
                if (t % 3 == 0) Holo.sparks(l, v.tag, c, 3, v.strong ? 3.0 : 1.8, 0xFF5A3A, 5);
            }
            case 2 -> {
                if (t % 3 == 0) {
                    double a = l.random.nextDouble() * 6.28, b = l.random.nextDouble() * 3.14 - 1.57, r = 2.4 + l.random.nextDouble();
                    Vec3 to = c.add(Math.cos(a) * Math.cos(b) * r, Math.sin(b) * r, Math.sin(a) * Math.cos(b) * r);
                    Holo.lightning(l, v.tag, c, to, PURPLE, 0.12, 4);
                }
                if (t % 5 == 0) Holo.ring(l, v.tag, c, 4.5, 0.5, 20, 0.16, 0.03, PURPLE, 8, 0, 2, (t * 0.4) % 3.1, 8, true);
            }
            case 3, 4 -> {
                if (t % 4 == 0) Holo.sparks(l, v.tag, c, 2, 1.0, v.kind == 3 ? BLUE : RED, 4);
            }
            case 5 -> {
                if (last.distanceToSqr(c) > 0.04) {
                    Holo.beam(l, v.tag, last, c, v.strong ? 3.6 : 2.2, PURPLE, 1, 2, 6);
                    Holo.beam(l, v.tag, last, c, v.strong ? 1.6 : 1.0, WHITE, 1, 1, 4);
                }
                if (t % 2 == 0) Holo.shock(l, v.tag, c, v.strong ? 9 : 6, PURPLE, 6);
                if (t % 4 == 0) Holo.lightning(l, v.tag, c, c.add((l.random.nextDouble() - 0.5) * 8, (l.random.nextDouble() - 0.5) * 6, (l.random.nextDouble() - 0.5) * 8), PURPLE, 0.2, 4);
            }
            default -> { }
        }
    }

    static void end(V v) {
        Holo.killOwner(v.tag);
        Vec3 c = v.m.position();
        if (v.kind == 0 && v.m.getTags().contains("gojo.fused")) {   // 창 + 혁 = 자폭 무라사키
            String o = v.tag + "e";
            Holo.flare(v.l, o, c, 30, PURPLE, 12);
            Holo.flare(v.l, o, c, 16, WHITE, 8);
            Holo.shock(v.l, o, c, 16, PURPLE, 12);
            Holo.shock(v.l, o, c, 10, WHITE, 8);
            Holo.explosion(v.l, o, c, 8, PURPLE);
            for (int i = 0; i < 8; i++) {
                double a1 = i * Math.PI / 4;
                Holo.lightning(v.l, o, c, c.add(Math.cos(a1) * 12, (i % 3 - 1) * 3, Math.sin(a1) * 12), PURPLE, 0.3, 8);
            }
            Holo.sfx(v.l, c, "boom", 2.0f, 0.8f);
            return;
        }
        switch (v.kind) {
            case 0 -> { Holo.flare(v.l, v.tag + "e", c, v.strong ? 12 : 6, BLUE, 8); Holo.shock(v.l, v.tag + "e", c, v.strong ? 10 : 5, BLUE, 8); }
            case 1 -> { Holo.flare(v.l, v.tag + "e", c, v.strong ? 14 : 8, RED, 8); Holo.shock(v.l, v.tag + "e", c, v.strong ? 12 : 7, RED, 8); }
            case 5 -> { Holo.flare(v.l, v.tag + "e", c, 16, PURPLE, 10); Holo.shock(v.l, v.tag + "e", c, 14, PURPLE, 10); }
            default -> { }
        }
    }

    public static void stopAll() {
        for (V v : ACTIVE.values()) Holo.killOwner(v.tag);
        ACTIVE.clear();
    }
}
