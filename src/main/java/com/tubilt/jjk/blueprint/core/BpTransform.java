package com.tubilt.jjk.blueprint.core;

/**
 * 설계도 좌표·방향 변환 (Minecraft 비의존 → 자체 검증 가능).
 * 순서와 규약은 바닐라 StructureTemplate 과 같다: 먼저 반전(X 뒤집기, 바닐라 Mirror.FRONT_BACK), 그다음 위에서 볼 때
 * 시계 방향 90°×rot 회전. 회전 공식 (x, z) → (−z, x) 은 블록 상태 회전(북 → 동)과 같은 방향이다.
 * 결과 좌표는 변환된 경계 상자 안(0 … 크기−1)으로 정규화된다.
 */
public final class BpTransform {
    public final int rot;          // 0..3, 시계 방향 90° 단위
    public final boolean mirror;   // 좌우 반전(X 축 뒤집기)
    public final int sx, sy, sz;   // 원본 크기

    public BpTransform(int rot, boolean mirror, int sx, int sy, int sz) {
        this.rot = ((rot % 4) + 4) % 4; this.mirror = mirror;
        this.sx = sx; this.sy = sy; this.sz = sz;
    }

    public int outSx() { return (rot & 1) == 0 ? sx : sz; }
    public int outSy() { return sy; }
    public int outSz() { return (rot & 1) == 0 ? sz : sx; }

    /** 원본 지역 좌표 → 변환 후 지역 좌표. out = {x, y, z} */
    public void apply(int x, int y, int z, int[] out) {
        int a = mirror ? sx - 1 - x : x, b = z;
        int nx, nz;
        switch (rot) {
            case 1 -> { nx = sz - 1 - b; nz = a; }
            case 2 -> { nx = sx - 1 - a; nz = sz - 1 - b; }
            case 3 -> { nx = b; nz = sx - 1 - a; }
            default -> { nx = a; nz = b; }
        }
        out[0] = nx; out[1] = y; out[2] = nz;
    }

    /** 수평 벡터 (dx, dz) 변환 (정규화 없는 선형 부분). out = {dx, dz} */
    public void vec(int dx, int dz, int[] out) {
        if (mirror) dx = -dx;
        switch (rot) {
            case 1 -> { out[0] = -dz; out[1] = dx; }
            case 2 -> { out[0] = -dx; out[1] = -dz; }
            case 3 -> { out[0] = dz; out[1] = -dx; }
            default -> { out[0] = dx; out[1] = dz; }
        }
    }

    /** 축 변환: 0=X, 1=Y, 2=Z. 90°/270° 회전에서 X↔Z, 반전은 축을 바꾸지 않는다. */
    public int axis(int a) { return a == 1 || (rot & 1) == 0 ? a : 2 - a; }

    /**
     * 축 기준 회전량(각속도·누적각·전동기 방향)에 곱할 부호.
     * 각속도는 유사 벡터라 ω' = det(M)·M·ω. 블록은 언제나 자기 축의 + 방향 기준으로 값을 들고 있으므로
     * 부호 = det(M) × (변환된 +축 벡터가 새 축의 + 쪽인지 − 쪽인지).
     */
    public int spinFactor(int a) {
        int det = mirror ? -1 : 1;
        if (a == 1) return det;
        int[] v = new int[2];
        vec(a == 0 ? 1 : 0, a == 2 ? 1 : 0, v);
        int s = v[0] != 0 ? v[0] : v[1];
        return det * s;
    }

    /** 수평 방향 변환: 0=북(−z) 1=동(+x) 2=남(+z) 3=서(−x). */
    public int horizontal(int d) {
        int[] dv = {0, 1, 0, -1}, dw = {-1, 0, 1, 0};
        int[] v = new int[2];
        vec(dv[d], dw[d], v);
        for (int i = 0; i < 4; i++) if (dv[i] == v[0] && dw[i] == v[1]) return i;
        throw new IllegalStateException();
    }

    public String label() { return (rot * 90) + "°" + (mirror ? " · 좌우 반전" : ""); }
}
