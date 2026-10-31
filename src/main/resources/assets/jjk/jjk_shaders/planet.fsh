#version 330 core
// 「/tp 행성 낙하」 장면 셰이더 v7 — 광선 추적 행성·지구·우주 + 깊이 합성. 출력 = 선형 HDR (post.fsh 가 블룸·톤매핑)
// 기법 참고: Seafle 'ultralaser' (MIT) — 릴리프 법선, Lommel-Seeliger 조명, 보로노이 균열.
//           rimuru.dev 'The Shooting Star' — 다단 후처리 구성(블룸 피라미드·광선·임팩트 프레임) 아이디어. 코드는 전부 새로 작성.

out vec4 fragColor;

uniform sampler2D uScene;      // 0: 월드 색 사본
uniform sampler2D uDepth;      // 1: 월드 깊이
uniform sampler2D uPlanetTex;  // 2
uniform sampler2D uRelief;     // 3
uniform sampler2D uSky;        // 4: 은하수
uniform sampler2D uEarth;      // 5
uniform sampler2D uEarthAux;   // 6: r 야경 g 구름 b 바다
uniform sampler2D uText;       // 7: 명령어 글자 아틀라스 (16줄, 알파)

uniform vec2 uRes;
uniform vec3 uRight, uUp, uFwd;
uniform float uTanHalf, uNear, uFar;
uniform float uClock;          // 초
uniform int uMode;             // 0 지상 합성, 1 우주
uniform vec3 uSun;
uniform mat3 uSpin;            // 행성 자전
uniform mat3 uESpin;           // 지구 자전
uniform vec3 uCamW;            // 카메라 월드 좌표 (절차 지형 고정용, 큰 수 잘라냄)

uniform vec3 uP;               // 행성 중심 (카메라 기준, 우주 모드는 우주 좌표)
uniform float uR;
uniform float uHeat, uCracks, uBreak, uBox, uAtmo;
uniform vec3 uLead;            // 행성 진행 방향

uniform vec3 uG;               // 충돌 지점 (카메라 기준)
uniform float uImpact;         // 충돌 후 틱 (<0 = 아직)
uniform float uSkyMix, uShadow, uBeams, uRift, uMeteors, uLava, uGround;
uniform vec3 uA[4];            // 견인 앵커
uniform vec3 uRiftC;           // 하늘 균열 중심 (카메라 기준)

uniform vec3 uCamS;            // 우주 카메라 위치
uniform vec3 uSite;            // 지구 표면 시전 지점(단위 벡터)
uniform float uChains, uPull, uReach, uWarp, uDebris, uShock, uEarthGlow;
uniform vec4 uFade;            // rgb 덮개색, a = 화면 보임(1)
uniform float uVox, uRing, uGrid, uTextAmt;
uniform float uSats, uImpactE;   // 궤도 명령 블록 위성 12기 · 우주에서 본 지구 충돌 (경과 틱, <0 없음)   // 행성 블록화 · 데이터 고리 · 처형 구역 격자 · 글자 흐름

const float PI = 3.14159265;
const float TAU = 6.28318531;
const float INF = 1e30;
const vec3 CYAN = vec3(0.33, 1.0, 1.0);

// ───────────────────────── 잡음
float hash13(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
vec3 hash33(vec3 p) { p = fract(p * vec3(0.1031, 0.1030, 0.0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx); }

float vnoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i), b = hash13(i + vec3(1, 0, 0)), c = hash13(i + vec3(0, 1, 0)), d = hash13(i + vec3(1, 1, 0));
    float e = hash13(i + vec3(0, 0, 1)), g = hash13(i + vec3(1, 0, 1)), h = hash13(i + vec3(0, 1, 1)), k = hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; }
    return s;
}
float fbm2(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 2; i++) { s += a * vnoise(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; }
    return s * 1.333;
}
vec2 voro(vec3 p) {
    vec3 c = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec3 o = vec3(float(x), float(y), float(z));
        float d = length(o + hash33(c + o) - f);
        if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
    }
    return vec2(d1, d2);
}

// 명령어 글자: u = 글자 단위가 아닌 줄 비율(0..1 반복), v = 0 아래 → 1 위, row = 0..15
float txt(float u, float v, float row) {
    if (v <= 0.0 || v >= 1.0) return 0.0;
    return texture(uText, vec2(u, (row + 1.0 - v) / 16.0)).a;
}
// 화면에서 글자가 너무 작으면(픽셀 몇 개) 잡음이 되므로 흐리게
float txtFade(float h, float dist, float px) { return smoothstep(3.0, 9.0, h / max(dist * px, 1e-6)); }

vec3 toLin(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }
vec2 eq(vec3 d) { return vec2(atan(d.x, -d.z) * (1.0 / TAU) + 0.5, acos(clamp(d.y, -1.0, 1.0)) * (1.0 / PI)); }

vec2 sph(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd), cc = dot(oc, oc) - r * r, h = b * b - cc;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// 광선 ↔ 선분 최근접: 반환 = 거리, tRay = 광선 위치, s = 선분 위치(0..1)
float segRay(vec3 ro, vec3 rd, vec3 a, vec3 b, out float tRay, out float s) {
    vec3 ab = b - a, ao = a - ro;
    float abab = dot(ab, ab), abrd = dot(ab, rd), aord = dot(ao, rd), aoab = dot(ao, ab);
    float den = abab - abrd * abrd;
    s = den > 1e-9 ? clamp((abrd * aord - aoab) / den, 0.0, 1.0) : 0.0;
    vec3 p = a + ab * s;
    tRay = max(dot(p - ro, rd), 0.0);
    return length(ro + rd * tRay - p);
}

// 견인 광선: 심 + 광륜 + 흐르는 "/tp" 패킷
float beam(vec3 ro, vec3 rd, vec3 a, vec3 b, float w, float px, float lim, float spacing, float flow) {
    float tRay, s;
    float gap = segRay(ro, rd, a, b, tRay, s);
    if (tRay > lim) return 0.0;
    float ww = max(w, tRay * px * 0.7);
    float len = distance(a, b);
    float pk = smoothstep(0.82, 1.0, fract(s * len / spacing - flow));
    float core = exp(-(gap * gap) / (ww * ww * 0.3));
    float halo = exp(-gap / (ww * 4.0)) * 0.15 + exp(-gap / (ww * 12.0)) * 0.012;
    return core * (1.0 + pk * 4.0) + halo * (1.0 + pk * 1.5);
}

// /fill 선택 상자(히트박스) 모서리 + 꼭짓점 표지
float boxWire(vec3 ro, vec3 rd, vec3 c, float h, float px, float lim) {
    float acc = 0.0;
    for (int e = 0; e < 12; e++) {
        int axis = e / 4, k = e - axis * 4;
        float s1 = (k & 1) == 0 ? -h : h, s2 = (k & 2) == 0 ? -h : h;
        vec3 a, b;
        if (axis == 0) { a = vec3(-h, s1, s2); b = vec3(h, s1, s2); }
        else if (axis == 1) { a = vec3(s1, -h, s2); b = vec3(s1, h, s2); }
        else { a = vec3(s1, s2, -h); b = vec3(s1, s2, h); }
        float tRay, s;
        float gap = segRay(ro, rd, c + a, c + b, tRay, s);
        if (tRay > lim) continue;
        float ww = max(h * 0.006, tRay * px * 1.2);
        float corner = 1.0 + 2.5 * (1.0 - smoothstep(0.0, 0.12, min(s, 1.0 - s)));
        float scan = 0.7 + 0.6 * smoothstep(0.9, 1.0, fract(s * 3.0 - uClock * 0.8));
        acc += (exp(-gap / ww) + exp(-gap / (ww * 6.0)) * 0.15) * corner * scan;
    }
    return acc;
}

vec3 stars(vec3 rd, float px) {
    vec3 o = vec3(0.0);
    for (int L = 0; L < 3; L++) {
        float S = L == 0 ? 160.0 : L == 1 ? 420.0 : 900.0;
        vec3 q = rd * S, cell = floor(q), h = hash33(cell + float(L) * 17.0);
        if (h.x < (L == 0 ? 0.93 : L == 1 ? 0.978 : 0.992)) continue;
        vec3 sp = cell + 0.2 + 0.6 * hash33(cell + 7.1);
        float dist = length(q - sp) / S;
        float sz = max(px * 0.8, 2e-5);
        float tw = 0.75 + 0.25 * sin(uClock * (3.0 + 6.0 * h.y) + h.z * 40.0);
        float I = exp(-(dist * dist) / (sz * sz)) * (0.18 + 2.2 * pow(h.y, 10.0)) * tw;
        o += mix(vec3(0.6, 0.75, 1.0), vec3(1.0, 0.82, 0.62), h.z) * I * (L == 0 ? 1.0 : L == 1 ? 0.6 : 0.35);
    }
    return o;
}

vec3 sunGlare(vec3 rd) {
    float cs = dot(rd, uSun);
    if (cs <= 0.0) return vec3(0.0);
    float ang = sqrt(max(2.0 - 2.0 * cs, 0.0)), disc = 0.0047;
    float w = disc * 1.5;
    return vec3(1.0, 0.92, 0.78) * (40.0 * (1.0 - smoothstep(disc * 0.8, disc, ang)) + 1.6 * w * w / (ang * ang + w * w)
            + 0.08 * 0.0036 / (ang * ang + 0.0036));
}

// ───────────────────────── 워프 (우주 이동): 화면 중심에서 뻗어나가는 별빛 줄무늬 + 터널 광
vec3 warp(vec2 ndc, float aspect) {
    vec2 q = ndc * vec2(aspect, 1.0);
    float r = length(q), a = atan(q.y, q.x) / TAU + 0.5;
    vec3 o = vec3(0.0);
    for (int L = 0; L < 3; L++) {
        float N = 70.0 + 55.0 * float(L);
        float fa = a * N + float(L) * 0.37;
        float cell = floor(fa);
        float h = hash13(vec3(cell, float(L), 3.1)), h2 = hash13(vec3(cell, float(L), 9.7));
        if (h < 0.25) continue;
        float da = abs(fract(fa) - 0.5) / N * TAU * r;
        float z = fract(h2 - uClock * (0.6 + 1.1 * h) * (0.4 + 1.6 * uWarp));
        float head = 0.03 + z * z * 2.4;
        float len = 0.02 + 1.2 * uWarp * z * z;
        float along = clamp((r - (head - len)) / len, 0.0, 1.0) * step(r, head);
        float w = 0.0012 + 0.005 * z * z;
        float line = exp(-da * da / (w * w)) * along * along * smoothstep(0.0, 0.25, z);
        o += mix(vec3(0.55, 0.75, 1.0), vec3(1.0, 0.9, 0.8), h2) * line * (0.6 + 2.4 * h);
    }
    float tunnel = exp(-r * 3.0) * 0.6 + exp(-r * 18.0) * 2.0;
    float ripple = 0.5 + 0.5 * sin(log(r + 1e-3) * 14.0 - uClock * 30.0);
    vec3 tun = vec3(0.35, 0.6, 1.0) * tunnel * (0.6 + 0.4 * ripple) + vec3(0.25, 0.1, 0.6) * smoothstep(0.4, 1.4, r) * 0.25;
    return (o * 1.6 + tun) * uWarp;
}

// ───────────────────────── 행성 표면
vec3 planetShade(vec3 n, vec3 rd, vec3 sun, vec3 lead, float heat, float cracks) {
    vec3 m = uSpin * n;
    // ── /fill 로 선택된 행성 = 블록 엔티티: 주사선이 훑고 지나간 자리부터 큐브 구면 격자로 블록화
    float edge = 0.0, scan = 0.0, vx = 0.0;
    if (uVox > 0.001) {
        float sw = dot(m, normalize(vec3(0.3, 1.0, 0.2)));
        float front = uVox * 2.4 - 1.2;
        vx = step(sw, front);
        scan = exp(-abs(sw - front) * 45.0) * (1.0 - step(0.999, uVox));
        if (vx > 0.5) {
            vec3 am = abs(m);
            vec3 ax = am.x > am.y && am.x > am.z ? vec3(sign(m.x), 0.0, 0.0) : am.y > am.z ? vec3(0.0, sign(m.y), 0.0) : vec3(0.0, 0.0, sign(m.z));
            vec3 f = m / max(max(am.x, am.y), am.z);           // 큐브 면 위 좌표 (-1..1)
            const float N = 9.0;
            vec3 cellc = (floor(f * N) + 0.5) / N;
            cellc = cellc * (1.0 - abs(ax)) + ax;                // 면 축은 그대로
            vec3 fr = abs(fract(f * N) - 0.5) * (1.0 - abs(ax)) * 2.0;
            edge = smoothstep(0.9, 0.985, max(fr.x, max(fr.y, fr.z)));
            float lift = hash13(floor(f * N) + ax * 7.0);
            m = normalize(cellc);
            n = normalize(mix(transpose(uSpin) * m, transpose(uSpin) * ax, 0.35 + 0.2 * lift));   // 각진 블록 면 조명
            m = normalize(m);
        }
    }
    vec2 st = vec2(atan(m.x, m.z) * (1.0 / TAU) + 0.5, acos(clamp(m.y, -1.0, 1.0)) * (1.0 / PI));
    vec3 alb = toLin(texture(uPlanetTex, st).rgb);
    float g = dot(alb, vec3(0.3333));
    float region = fbm(m * 2.5 + 4.0);
    alb = mix(alb, g * mix(vec3(1.25, 0.78, 0.58), vec3(0.95, 0.62, 0.52), region), 0.8) * 1.15;   // 붉은 외계 행성

    vec2 tx = 1.0 / vec2(textureSize(uRelief, 0));
    float hE = texture(uRelief, st + vec2(tx.x, 0.0)).r - texture(uRelief, st - vec2(tx.x, 0.0)).r;
    float hN = texture(uRelief, st - vec2(0.0, tx.y)).r - texture(uRelief, st + vec2(0.0, tx.y)).r;
    vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), m) + vec3(1e-5, 0.0, 0.0));
    vec3 north = cross(m, east);
    vec3 bent = normalize(m - (east * hE + north * hN) * 4.0);
    vec3 sM = uSpin * sun, vM = uSpin * rd;
    float mu0 = max(dot(bent, sM), 0.0), mu = max(dot(m, -vM), 0.02);
    vec3 c = alb * (2.0 * mu0 / (mu0 + mu)) * 1.3;
    c += alb * vec3(0.10, 0.05, 0.04) * 0.3;
    // 얇은 먼지 대기: 낮 쪽 가장자리가 주황빛으로 번짐
    float limbV = pow(1.0 - mu, 4.0);
    c += vec3(1.0, 0.5, 0.28) * limbV * smoothstep(-0.2, 0.4, dot(m, sM)) * 0.35 * uAtmo;

    if (cracks > 0.001) {
        vec3 warpv = vec3(fbm(m * 4.0), fbm(m * 4.0 + 3.1), fbm(m * 4.0 + 7.7)) - 0.5;
        float spread = smoothstep(0.66 - 0.6 * cracks, 0.76 - 0.6 * cracks, fbm(m * 2.0 + 7.0));
        vec2 v1 = voro(m * 3.2 + warpv * 0.9);
        vec2 v2 = voro(m * 9.0 + 3.7 + warpv * 1.6);
        float big = (1.0 - smoothstep(0.0, 0.05, v1.y - v1.x)) * spread;
        float fine = (1.0 - smoothstep(0.0, 0.04, v2.y - v2.x)) * spread * smoothstep(0.5, 1.0, cracks);
        float glow = big * smoothstep(0.0, 0.35, cracks) + fine * 0.5;
        // 균열 주변이 은은하게 달아오름
        float bleed = exp(-(v1.y - v1.x) * 18.0) * spread * smoothstep(0.2, 0.8, cracks) * 0.15;
        vec3 lava = mix(vec3(1.0, 0.22, 0.04) * 2.4, vec3(1.0, 0.6, 0.25) * 3.2, smoothstep(0.0, 0.9, heat));
        float pulse = 0.75 + 0.25 * sin(uClock * 3.0 + m.x * 17.0 + m.z * 11.0);
        c = mix(c, lava * pulse, clamp(glow, 0.0, 1.0) * 0.85) + lava * (glow * 0.35 + bleed * 0.25);
    }
    if (heat > 0.001) {
        float ld = clamp(dot(n, lead), 0.0, 1.0);
        float limb = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
        float churn = fbm(m * 6.0 + vec3(uClock * 0.9, 0.0, uClock * 0.6));
        vec3 fire = mix(vec3(1.0, 0.16, 0.04) * 1.3, vec3(1.0, 0.55, 0.22) * 2.2, smoothstep(0.3, 1.0, churn + limb * 0.5));
        float burn = heat * smoothstep(0.05, 0.9, ld) * (0.25 + 0.75 * limb) * (0.6 + 0.7 * churn);
        c = c * (1.0 + 0.4 * heat * ld) + fire * burn * 1.15;
    }
    if (vx > 0.5) c = c * (1.0 - 0.7 * edge) * (0.85 + 0.3 * hash13(floor(m * 40.0))) + CYAN * edge * (0.15 + 0.6 * uBox);
    c += CYAN * scan * 3.0 + vec3(1.0) * scan * scan * 2.0;
    return c;
}

// ── 행성 둘레를 도는 데이터 고리 2겹 (엔티티 NBT·명령어가 흘러감). lim = 가림 거리
vec3 dataRings(vec3 ro, vec3 rd, float lim, float tPl, float px) {
    vec3 acc = vec3(0.0);
    float Rr = uR * 1.38;
    vec2 h = sph(ro, rd, uP, Rr);
    if (h.y <= 0.0) return acc;
    for (int k = 0; k < 2; k++) {
        vec3 ax = k == 0 ? normalize(vec3(0.25, 1.0, 0.1)) : normalize(vec3(-0.6, 0.7, 0.5));
        vec3 e1 = normalize(cross(ax, vec3(0.0, 0.0, 1.0)));
        vec3 e2 = cross(ax, e1);
        float row = k == 0 ? 14.0 : 4.0;
        float spd = k == 0 ? 0.035 : -0.025;
        for (int i = 0; i < 2; i++) {
            float th = i == 0 ? h.x : h.y;
            if (th <= 0.0 || th > lim || (i == 1 && th > tPl)) continue;
            vec3 q = normalize(ro + rd * th - uP);
            float lat = asin(clamp(dot(q, ax), -1.0, 1.0));
            float hw = 0.05 * (1.0 + 0.3 * float(k));
            if (abs(lat) > hw * 1.6) continue;
            float lon = atan(dot(q, e2), dot(q, e1)) / TAU + 0.5;
            float v = lat / (2.0 * hw) + 0.5;
            float a = txt(-lon * 2.0 + uClock * spd, v, row);
            float rule = exp(-abs(abs(lat) - hw * 1.25) / (hw * 0.04));
            float fade = txtFade(Rr * hw * 2.0, th, px) * (i == 0 ? 1.0 : 0.35);
            acc += (CYAN * a * 1.6 * fade + CYAN * rule * 0.5 * (i == 0 ? 1.0 : 0.4));
        }
    }
    return acc * uRing;
}

// 행성 대기 광륜 (역광이면 일식처럼 고리가 타오름)
vec3 atmoHalo(vec3 ro, vec3 rd, vec3 c, float r, float lim) {
    vec3 oc = c - ro;
    float along = dot(oc, rd);
    if (along <= 0.0 || along - r > lim) return vec3(0.0);
    vec3 cp = rd * along - oc;
    float miss = length(cp);
    if (miss < r * 0.995 || miss > r * 1.25) return vec3(0.0);
    vec3 cn = cp / miss;
    float h = (miss - r) / r;
    float lit = smoothstep(-0.35, 0.35, dot(cn, uSun));
    float back = pow(max(dot(rd, uSun), 0.0), 6.0);
    return (vec3(1.0, 0.5, 0.3) * exp(-h / 0.02) * (0.4 * lit + 3.0 * back) + vec3(1.0, 0.75, 0.5) * exp(-h / 0.006) * back * 4.0) * uAtmo;
}

// 행성 가장자리 플라스마 외피 (진행 방향 쪽이 가장 밝음)
vec3 sheath(vec3 ro, vec3 rd, float lim) {
    vec3 oc = uP - ro;
    float along = dot(oc, rd);
    vec3 side = rd * along - oc;
    float miss = length(side);
    if (along <= 0.0 || miss < uR * 0.985 || along - uR > lim) return vec3(0.0);
    float lead = dot(side / miss, uLead);
    float shell = exp(-(miss - uR) / (uR * (0.025 + 0.06 * clamp(lead + 0.3, 0.0, 1.0))));
    float plume = (1.0 - smoothstep(-0.7, 0.1, lead)) * exp(-(miss - uR) / (uR * 0.35));
    float fl = 0.75 + 0.5 * vnoise(vec3(side / uR * 3.0 + uLead * uClock * 4.0));
    return (mix(vec3(1.0, 0.4, 0.12), vec3(1.0, 0.8, 0.6) * 1.6, smoothstep(0.3, 1.0, lead)) * shell * 1.6
            + vec3(1.0, 0.2, 0.06) * plume * 1.5) * uHeat * fl;
}

// 충격파 덮개(bow shock): 진행 방향 앞쪽의 포물면 플라스마 껍질 — 부피 적분 12걸음
vec3 bowShock(vec3 ro, vec3 rd, float lim) {
    float Rb = uR * 2.6;
    vec2 hb = sph(ro, rd, uP + uLead * uR * 0.6, Rb);
    if (hb.y <= 0.0) return vec3(0.0);
    float t0 = max(hb.x, 0.0), t1 = min(hb.y, lim);
    if (t1 <= t0) return vec3(0.0);
    vec3 acc = vec3(0.0);
    float dt = (t1 - t0) / 12.0;
    float j = hash13(vec3(gl_FragCoord.xy, uClock));
    for (int i = 0; i < 12; i++) {
        vec3 q = ro + rd * (t0 + dt * (float(i) + j)) - uP;
        float x = dot(q, uLead);
        float rho = length(q - uLead * x);
        if (length(q) < uR) continue;
        float surf = x - (uR * 1.18 - rho * rho / (uR * 2.4));
        float shell = exp(-abs(surf) / (uR * 0.03));
        float inner = 0.0;
        float flick = 0.6 + 0.8 * fbm2(q / uR * 4.0 + uLead * uClock * 6.0);
        float edge = 1.0 - smoothstep(uR * 1.4, uR * 2.4, rho);
        float hot = smoothstep(-uR * 0.6, uR * 1.1, x);
        vec3 col = mix(vec3(1.0, 0.22, 0.05), mix(vec3(1.0, 0.62, 0.32), vec3(0.8, 0.85, 1.0), smoothstep(0.8, 1.0, hot)) * 1.6, hot);
        acc += col * (shell + inner) * flick * edge * step(-uR * 1.8, x);
    }
    return acc * dt / uR * 0.6 * uShock;
}

// 행성 뒤로 끌리는 불기둥
vec3 trail(vec3 ro, vec3 rd, float lim, float tP) {
    vec3 a = uP, b = uP - uLead * uR * 7.0;
    float tRay, s;
    float gap = segRay(ro, rd, a, b, tRay, s);
    if (tRay > lim || tRay > tP + uR * 0.2) return vec3(0.0);
    float w = uR * (0.85 + 1.6 * s);
    if (gap > w * 8.0) return vec3(0.0);
    float n = fbm(vec3(gap / uR * 2.0, s * 7.0 + uClock * 2.6, tRay * 0.002));
    float dens = exp(-gap / w * 2.2) * (1.0 - s) * smoothstep(0.0, 0.06, s) * (0.4 + 1.2 * n);
    vec3 col = mix(vec3(1.0, 0.62, 0.25) * 2.6, vec3(0.55, 0.09, 0.03), smoothstep(0.0, 0.55, s));
    return col * dens * uHeat;
}

// ───────────────────────── 우주: 조석력으로 떨어져 나간 파편 줄기 (행성 → 지구)
vec3 debris(vec3 ro, vec3 rd, inout float near) {
    vec3 c = vec3(0.0);
    bool hit = false;
    vec3 hc = vec3(0.0);
    vec3 toE = normalize(uSite - uP);
    vec3 u = normalize(cross(toE, abs(toE.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 v = cross(toE, u);
    float span = distance(uSite, uP) * 0.8;
    for (int i = 0; i < 28; i++) {
        vec3 h = hash33(vec3(float(i), 3.7, 1.3));
        float s = fract(h.x + uClock * (0.05 + 0.08 * h.y));
        float ang = h.z * TAU + s * 3.0;
        float spread = uR * (0.4 + 0.9 * h.y) * (0.6 + s * 1.6);
        vec3 pos = uP + toE * (uR * 0.9 + span * s * s) + (u * cos(ang) + v * sin(ang)) * spread;
        float rr = uR * (0.012 + 0.04 * pow(h.z, 2.0)) * (1.0 - 0.5 * s) * smoothstep(0.0, 0.05, s) * (1.0 - smoothstep(0.85, 1.0, s));
        if (rr <= 0.0) continue;
        vec2 hs = sph(ro, rd, pos, rr);
        if (hs.x > 0.0 && hs.x < near) {
            near = hs.x;
            vec3 n = normalize(ro + rd * hs.x - pos);
            float bump = vnoise(n * 6.0 + h * 10.0);
            float ndl = max(dot(n, uSun), 0.0) * (0.6 + 0.4 * bump);
            vec3 rock = vec3(0.32, 0.2, 0.16) * ndl * 1.6;
            float hotSide = pow(max(dot(n, -toE), 0.0), 2.0) * uHeat;
            hc = rock + vec3(1.0, 0.35, 0.08) * (hotSide * 2.5 + 0.25 * smoothstep(0.5, 0.8, bump));
            hit = true;
        }
        // 파편의 빛꼬리
        float tR, sg;
        float gap = segRay(ro, rd, pos, pos - toE * rr * 14.0, tR, sg);
        if (tR < near) c += vec3(1.0, 0.45, 0.12) * exp(-gap / (rr * 0.8)) * (1.0 - sg) * 0.6 * (0.3 + uHeat);
    }
    return (hit ? hc : vec3(0.0)) + c * uDebris;
}

// ───────────────────────── 지상 모드: 어두워진 하늘
vec3 groundSky(vec3 rd, float px) {
    vec3 s = toLin(texture(uSky, eq(rd)).rgb) * 0.5 + stars(rd, px) * 0.45;
    vec3 pd = normalize(uP);
    float toward = max(dot(rd, pd), 0.0);
    // 찢긴 대기: 행성 쪽으로 빨려 올라가는 연기 구름 (아래에서 불빛을 받음)
    vec2 q = rd.xz / max(rd.y + 0.25, 0.08);
    float cl = fbm(vec3(q * 1.6, uClock * 0.15)) * 0.7 + fbm(vec3(q * 5.0 + 3.0, uClock * 0.3)) * 0.3;
    float cov = smoothstep(0.42, 0.75, cl) * smoothstep(-0.1, 0.25, rd.y);
    vec3 lit = mix(vec3(0.05, 0.02, 0.02), vec3(0.9, 0.25, 0.07), uHeat * (0.35 + 0.65 * pow(toward, 3.0)));
    s = mix(s, lit, cov * 0.85);
    s += vec3(0.6, 0.12, 0.04) * uHeat * pow(toward, 12.0) * 1.4;
    s += vec3(0.35, 0.07, 0.025) * uHeat * exp(-abs(rd.y) * 7.0);
    // 이온화 코로나: 행성 둘레 하늘에 너울거리는 자홍·청록 커튼
    if (uR > 0.0 && uHeat > 0.01) {
        float ar = asin(clamp(uR / length(uP), 0.0, 1.0));
        float ang = acos(clamp(dot(rd, pd), -1.0, 1.0));
        float x = (ang - ar) / max(ar, 0.05);
        vec3 tA = normalize(cross(pd, vec3(0.0, 1.0, 0.0) + vec3(0.001)));
        float phi = atan(dot(rd, cross(pd, tA)), dot(rd, tA));
        float curtain = fbm(vec3(phi * 3.0, x * 2.0 - uClock * 0.6, uClock * 0.2));
        float band = exp(-max(x, 0.0) * 2.2) * step(0.0, x) * smoothstep(0.35, 0.8, curtain);
        s += mix(vec3(0.9, 0.15, 0.5), vec3(0.2, 1.0, 0.75), smoothstep(0.2, 1.2, x)) * band * uHeat * 0.6;
    }
    return s;
}

// 하늘 균열 (/fill structure_void 로 하늘을 지움)
vec3 rift(vec3 rd, float px) {
    vec3 cd = normalize(uRiftC);
    float cosA = dot(rd, cd);
    if (cosA < 0.4) return vec3(0.0);
    vec3 u = normalize(cross(cd, vec3(0.0, 0.0, 1.0) + vec3(0.001)));
    vec3 v = cross(cd, u);
    vec2 q = vec2(dot(rd, u), dot(rd, v)) / max(cosA, 1e-3);
    float len = 0.6 * uRift;
    float jag = (fbm(vec3(q.x * 9.0, 0.0, uClock * 0.4)) - 0.5) * 0.08 + (fbm(vec3(q.x * 40.0, 3.0, 0.0)) - 0.5) * 0.02;
    float d = abs(q.y - jag);
    float on = 1.0 - smoothstep(len * 0.85, len, abs(q.x));
    float open = 0.002 + 0.03 * uRift * uRift * (1.0 - smoothstep(0.0, len, abs(q.x)));
    float core = (1.0 - smoothstep(open * 0.6, open, d)) * on;
    float glow = exp(-d / (0.004 + open * 0.6)) * on + exp(-d / 0.05) * on * 0.1;
    // 갈라진 틈에서 뻗는 잔금 (블록 격자처럼 각진 선)
    vec2 g = q * 22.0;
    vec2 gi = floor(g), gf = fract(g);
    float cell = hash13(vec3(gi, 2.0));
    float br = (cell > 0.72 ? exp(-min(gf.x, 1.0 - gf.x) * 40.0) + exp(-min(gf.y, 1.0 - gf.y) * 40.0) : 0.0)
            * exp(-d / (0.03 + 0.08 * uRift)) * on * uRift;
    vec3 inside = toLin(texture(uSky, eq(rd)).rgb) * 1.4 + stars(rd, px) * 2.5;
    float rowR = floor(q.y * 70.0 + 8.0);
    float mtx = txt(q.x * 0.9 + uClock * (0.05 + 0.1 * hash13(vec3(rowR, 1.0, 2.0))) + hash13(vec3(rowR, 3.0, 1.0)), fract(q.y * 70.0), mod(rowR, 16.0));
    inside += CYAN * mtx * 0.9 * uTextAmt;
    return inside * core * 3.0 + CYAN * (glow * 1.6 + br * 0.5) * uRift + vec3(1.0) * exp(-d / 0.004) * on * uRift * 1.5;
}

// 낙하 중 하늘에서 쏟아지는 불타는 파편 (깊이 가림)
vec3 meteors(vec3 rd, float tS, float px) {
    vec3 c = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        vec3 h = hash33(vec3(float(i), 1.7, 4.2));
        vec3 h2 = hash33(vec3(float(i), 8.1, 0.3));
        float period = 1.1 + 1.6 * h.x;
        float ph = fract(uClock / period + h.y);
        vec3 s0 = uP + vec3((h.x - 0.5) * 1.6, -0.85, (h.z - 0.5) * 1.6) * uR;
        vec3 g0 = uG + vec3((h2.x - 0.5) * 1100.0, 0.0, (h2.y - 0.5) * 1100.0);
        vec3 dir = g0 - s0;
        vec3 head = s0 + dir * ph;
        vec3 tail = head - normalize(dir) * (uR * (0.15 + 0.25 * h2.z));
        float tR, s;
        float gap = segRay(vec3(0.0), rd, tail, head, tR, s);
        if (tR > tS) continue;
        float w = max(1.5 + 5.0 * h.z, tR * px * 1.1);
        float I = exp(-gap * gap / (w * w)) * s * s * 2.5 + exp(-gap / (w * 6.0)) * s * 0.12;
        c += mix(vec3(1.0, 0.25, 0.06), vec3(1.0, 0.9, 0.7) * 2.5, s * s) * I * smoothstep(0.0, 0.05, ph);
    }
    return c * uMeteors;
}

// 렌더 거리 밖(허공) 아래쪽에 깔리는 절차 지형 — 원경 샷에서 땅이 비지 않게
vec3 synthGround(vec3 rd, float tp, vec3 skyCol) {
    vec3 wp = rd * tp;
    vec3 ap = wp + uCamW;
    float n = fbm(vec3(ap.xz * 0.004, 0.0)) * 0.7 + fbm(vec3(ap.xz * 0.03, 1.0)) * 0.3;
    vec3 g = mix(vec3(0.035, 0.05, 0.025), vec3(0.06, 0.055, 0.04), n);
    float D = length(wp - uP);
    g += g * uHeat * vec3(9.0, 2.6, 0.9) * clamp(uR * uR * 2.0 / (D * D), 0.0, 4.0);
    float fog = 1.0 - exp(-tp / 2600.0);
    return mix(g, skyCol * 0.8, fog);
}

// ───────────────────────── 충돌: 응결 돔 + 화구 + 버섯기둥 + 분출물 + 충격파 고리 + 벽
vec3 impact(vec3 rd, vec3 c, float tS, vec3 wp, bool solid, float px) {
    float k = uImpact;
    float age = clamp(k / 200.0, 0.0, 1.0);
    float rf = 40.0 + 380.0 * (1.0 - exp(-k / 18.0));
    vec3 fc = uG + vec3(0.0, rf * 0.15 + k * 0.7, 0.0);   // 커지면서 천천히 솟음

    // 화구 빛이 땅을 비춤
    if (solid) {
        float D = length(wp - fc);
        float Ifb = exp(-k / 60.0) * clamp(rf * rf * 3.0 / (D * D), 0.0, 6.0);
        c += c * vec3(1.0, 0.5, 0.2) * Ifb * 1.5;
        // 분화구 용암
        vec3 d = wp - uG;
        float dd = length(d.xz);
        if (uLava > 0.0 && dd < 110.0 && abs(d.y) < 70.0) {
            vec2 vo = voro((wp + uCamW) * vec3(0.06, 0.02, 0.06));
            float crack = 1.0 - smoothstep(0.0, 0.08, vo.y - vo.x);
            float core = pow(1.0 - dd / 110.0, 1.5);
            float pulse = 0.8 + 0.2 * sin(uClock * 2.0 + dd * 0.1);
            c += vec3(1.0, 0.32, 0.06) * (crack * 3.0 + core * 1.2) * core * uLava * pulse;
        }
    }

    // 버섯기둥 (화구 아래로 이어지는 연기 기둥)
    if (k > 20.0) {
        float tR, s;
        float gap = segRay(vec3(0.0), rd, uG, fc, tR, s);
        if (tR < tS) {
            float w = rf * (0.18 + 0.12 * s);
            float n = fbm(vec3(gap / w, s * 4.0 - uClock * 0.5, tR * 0.003));
            float dens = exp(-(gap * gap) / (w * w)) * (0.5 + 0.8 * n) * smoothstep(20.0, 50.0, k);
            vec3 col = mix(vec3(1.0, 0.4, 0.1) * 2.5 * exp(-k / 120.0), vec3(0.1, 0.07, 0.06), smoothstep(0.0, 0.5, s));
            c = mix(c, col, clamp(dens, 0.0, 0.92));
        }
    }

    vec2 hf = sph(vec3(0.0), rd, fc, rf);
    if (hf.y > 0.0 && hf.x < tS) {
        float t0 = max(hf.x, 0.0), t1 = min(hf.y, tS);
        float T = 1.0;
        vec3 acc = vec3(0.0);
        float dt = (t1 - t0) / 8.0;
        float j = hash13(vec3(gl_FragCoord.xy, uClock * 7.0));
        float squash = 1.0 + 0.9 * clamp(k / 140.0, 0.0, 1.0);   // 시간이 지나면 납작한 버섯갓
        for (int i = 0; i < 8; i++) {
            vec3 q = (rd * (t0 + dt * (float(i) + j)) - fc) / rf;
            q.y *= squash;
            float wv = fbm(q * 2.0 + vec3(0.0, -uClock * 0.5, 0.0));
            float r = length(q) + (wv - 0.5) * 0.6;
            float n = fbm(q * 4.5 + vec3(0.0, -uClock * 0.9, uClock * 0.2));
            float dens = clamp((0.95 - r) * 2.6 + (n - 0.5) * 1.6, 0.0, 1.0);
            float heat = clamp((1.0 - r) * 1.3 + (n - 0.5) * 1.2 - age * 2.4 + 0.25, 0.0, 1.0);
            // 식은 부분은 안쪽 불빛을 받는 짙은 연기 (아래쪽이 더 밝음)
            vec3 smoke = mix(vec3(0.045, 0.038, 0.034), vec3(0.55, 0.2, 0.06), clamp(-q.y * 1.2 + 0.25, 0.0, 1.0) * exp(-k / 110.0));
            vec3 em = mix(smoke, vec3(1.0, 0.36, 0.07) * 2.4, smoothstep(0.15, 0.55, heat));
            em = mix(em, vec3(1.0, 0.82, 0.5) * 5.0, smoothstep(0.65, 1.0, heat));
            float a = 1.0 - exp(-dens * dt / rf * 7.0);
            acc += em * a * T;
            T *= 1.0 - a;
        }
        float vis = 1.0 - smoothstep(0.8, 1.0, age);
        c = c * mix(1.0, T, vis) + acc * vis;
    }

    // 응결 돔 (윌슨 구름): 충격파를 따라 퍼지는 하얀 반구 껍질
    if (k < 48.0) {
        float Rd = 50.0 + k * 28.0;
        float fade = smoothstep(0.0, 4.0, k) * (1.0 - smoothstep(25.0, 48.0, k));
        vec2 hd = sph(vec3(0.0), rd, uG, Rd);
        for (int i = 0; i < 2; i++) {
            float th = i == 0 ? hd.x : hd.y;
            if (th <= 0.0 || th >= tS) continue;
            vec3 p = rd * th;
            if (p.y < uG.y - 2.0) continue;
            vec3 n = normalize(p - uG);
            float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
            float tex = 0.6 + 0.4 * fbm2((p + uCamW) * 0.01 + uClock * 0.3);
            c = mix(c, vec3(0.95, 0.95, 1.0) * 1.4, clamp(fres * 0.75 * tex * fade, 0.0, 1.0));
        }
    }

    // 분출물: 포물선을 그리며 날아가는 불덩이
    if (k < 90.0) {
        for (int i = 0; i < 22; i++) {
            vec3 h = hash33(vec3(float(i), 5.5, 2.2));
            float a = h.x * TAU, el = 0.35 + 0.8 * h.y, v = 7.0 + 12.0 * h.z;
            vec3 dv = vec3(cos(a) * cos(el), sin(el), sin(a) * cos(el)) * v;
            vec3 p1 = uG + dv * k - vec3(0.0, 0.18 * k * k, 0.0);
            float k0 = max(k - 4.0, 0.0);
            vec3 p0 = uG + dv * k0 - vec3(0.0, 0.18 * k0 * k0, 0.0);
            if (p1.y < uG.y - 6.0) continue;
            float tR, s;
            float gap = segRay(vec3(0.0), rd, p0, p1, tR, s);
            if (tR > tS) continue;
            float w = max(3.0, tR * px * 1.2);
            float I = (exp(-gap * gap / (w * w)) * 3.0 + exp(-gap / (w * 4.0)) * 0.2) * s;
            c += mix(vec3(1.0, 0.3, 0.06), vec3(1.0, 0.85, 0.6) * 2.0, s) * I * (1.0 - smoothstep(40.0, 90.0, k));
        }
    }

    float rs = 30.0 + k * 20.0, fade = exp(-k / 35.0) * (1.0 - smoothstep(60.0, 90.0, k));
    if (abs(rd.y) > 1e-4) {
        float tp = uG.y / rd.y;
        if (tp > 0.0 && tp < tS + 4.0) {
            vec3 q = rd * tp - uG;
            float r = length(q.xz);
            float wob = fbm2(vec3(q.xz * 0.02, k * 0.1));
            c += vec3(1.0, 0.7, 0.4) * exp(-abs(r - rs) / (6.0 + 6.0 * wob)) * fade * 3.0
               + vec3(1.0, 0.4, 0.1) * exp(-max(rs - r, 0.0) / 30.0) * step(r, rs) * fade * 0.35;
        }
    }
    vec3 rel = -uG;
    float A = rd.x * rd.x + rd.z * rd.z, B = 2.0 * (rel.x * rd.x + rel.z * rd.z), C = rel.x * rel.x + rel.z * rel.z - rs * rs;
    float disc = B * B - 4.0 * A * C;
    if (A > 1e-6 && disc > 0.0) {
        float sq = sqrt(disc);
        for (int i = 0; i < 2; i++) {
            float tw = (-B + (i == 0 ? -sq : sq)) / (2.0 * A);
            if (tw <= 0.0 || tw >= tS) continue;
            vec3 p = rd * tw;
            float y = p.y - uG.y, H = (80.0 + 40.0 * fbm2((p + uCamW) * 0.01)) * fade + 5.0;
            vec3 wn = normalize(vec3(p.x - uG.x, 0.0, p.z - uG.z));
            float graze = pow(1.0 - abs(dot(rd, wn)), 4.0);
            if (y > -2.0 && y < H) {
                float dust = 0.5 + 0.5 * fbm2(vec3(atan(wn.z, wn.x) * 20.0, y * 0.05 - k * 0.1, 0.0));
                c = mix(c, vec3(0.25, 0.16, 0.1), (1.0 - y / H) * fade * 0.5 * dust);
                c += vec3(1.0, 0.72, 0.45) * (1.0 - y / H) * fade * graze * 1.4 * dust;
            }
        }
    }
    return c;
}

// ───────────────────────── 지구
// 축 정렬 상자: x = 진입 t (없으면 -1), 법선은 out
float boxHit(vec3 ro, vec3 rd, vec3 c, float h, out vec3 nrm) {
    vec3 m = 1.0 / rd, o = ro - c;
    vec3 t1 = (-o - h) * m, t2 = (-o + h) * m;
    vec3 tn = min(t1, t2), tf = max(t1, t2);
    float a = max(max(tn.x, tn.y), tn.z), b = min(min(tf.x, tf.y), tf.z);
    if (a > b || b < 0.0 || a < 0.0) { nrm = vec3(0.0); return -1.0; }
    nrm = -sign(rd) * step(tn.yzx, tn.xyz) * step(tn.zxy, tn.xyz);
    return a;
}
vec3 satPos(int k) {
    vec3 u = normalize(cross(uSite, vec3(0.0, 1.0, 0.0)));
    vec3 v = cross(uSite, u);
    float a = float(k) * TAU / 12.0 + uClock * 0.05;
    return normalize(uSite + (u * cos(a) + v * sin(a)) * 0.42) * 1.28;
}
// 명령 블록 위성: 주황 본체 + 청록 모서리 + 앞면 화살표 무늬
vec3 satShade(vec3 p, vec3 c, vec3 nrm, float h, int k) {
    vec3 q = (p - c) / h;
    vec3 a = abs(q);
    float edge = smoothstep(0.82, 0.95, max(max(min(a.x, a.y), min(a.y, a.z)), min(a.x, a.z)));
    vec2 f = abs(nrm.x) > 0.5 ? q.yz : abs(nrm.y) > 0.5 ? q.xz : q.xy;
    float grid = step(0.85, max(abs(fract(f.x * 2.5) - 0.5), abs(fract(f.y * 2.5) - 0.5)) * 2.0);
    float arrow = (1.0 - smoothstep(0.0, 0.08, abs(f.x) - (0.5 - f.y) * 0.5)) * step(-0.3, f.y) * step(f.y, 0.5);
    vec3 base = vec3(0.62, 0.36, 0.2) * (0.35 + 0.65 * max(dot(nrm, uSun), 0.0));
    float pulse = 0.6 + 0.4 * sin(uClock * 6.0 + float(k));
    return base * (1.0 - 0.4 * grid) + CYAN * (edge * 1.6 + arrow * 0.8 * pulse);
}

vec3 earthShade(vec3 n, vec3 rd) {
    vec3 m = uESpin * n;
    vec2 st = vec2(atan(m.x, m.z) * (1.0 / TAU) + 0.5, acos(clamp(m.y, -1.0, 1.0)) * (1.0 / PI));
    vec3 day = toLin(texture(uEarth, st).rgb);
    vec3 aux = texture(uEarthAux, st).rgb;
    float ndl = dot(n, uSun);
    vec3 c = day * max(ndl, 0.0) * 1.05;
    c = mix(c, vec3(1.0) * max(ndl, 0.0) * 0.9, aux.g * 0.6);
    c += vec3(1.0, 0.72, 0.4) * pow(aux.r, 2.0) * 2.0 * smoothstep(0.05, -0.2, ndl) * (1.0 - aux.g * 0.7);
    vec3 hv = normalize(uSun - rd);
    c += vec3(1.0, 0.95, 0.85) * pow(max(dot(n, hv), 0.0), 80.0) * aux.b * (1.0 - aux.g) * 1.5 * step(0.0, ndl);
    float rim = pow(1.0 - max(dot(n, -rd), 0.0), 2.5);
    c = mix(c, vec3(0.3, 0.55, 1.0) * smoothstep(-0.2, 0.5, ndl) * 1.4, rim * 0.6);
    // 행성의 붉은 빛이 지구 표면을 물들임 + 돌입 지점의 불타는 대기
    vec3 toP = uP - n;
    float dP = length(toP);
    c += day * vec3(1.0, 0.35, 0.12) * max(dot(n, toP / dP), 0.0) * uHeat * 0.25 * uR * uR / (dP * dP) * 8.0;
    float eg = length(n - normalize(uP));
    c += vec3(1.0, 0.4, 0.1) * (exp(-eg / 0.05) * 1.6 + exp(-eg / 0.2) * 0.35) * uEarthGlow;
    if (uImpactE >= 0.0) {   // 지표를 휩쓰는 충격파 고리 · 불타는 내부 · 분화구
        float a = acos(clamp(dot(n, uSite), -1.0, 1.0));
        float Rr = 0.05 + uImpactE * 0.0016;
        float jag = (fbm(n * 14.0) - 0.5) * 0.03;
        float ring = exp(-abs(a - Rr - jag) / 0.01);
        float inside = 1.0 - smoothstep(Rr * 0.85, Rr + 0.01, a + jag);
        vec2 vo = voro(n * 40.0);
        float crk = 1.0 - smoothstep(0.0, 0.07, vo.y - vo.x);
        vec3 burnt = mix(vec3(0.05, 0.03, 0.025), vec3(1.0, 0.32, 0.06) * 2.0, crk * (0.4 + 0.6 * exp(-a / (Rr * 0.6))));
        c = mix(c, burnt, inside * 0.92);
        c += vec3(1.0, 0.6, 0.3) * ring * 3.0 + vec3(1.0, 0.75, 0.45) * exp(-a / 0.02) * 3.0;
    }
    float sd = length(n - uSite);
    c += CYAN * (exp(-sd / 0.006) * 3.0 + exp(-sd / 0.035) * 0.5) * uChains;
    return c;
}

vec3 space(vec3 rd, float px, vec2 ndc, float aspect) {
    vec3 ro = uCamS;
    vec3 c = toLin(texture(uSky, eq(rd)).rgb) * 0.85 + stars(rd, px) + sunGlare(rd);
    float near = INF;
    vec2 he = sph(ro, rd, vec3(0.0), 1.0);
    if (he.x > 0.0) { near = he.x; c = earthShade(normalize(ro + rd * he.x), rd); }
    {   // 대기 광륜 (지구 앞이든 옆이든)
        float b = dot(-ro, rd);
        vec3 cp = ro + rd * max(b, 0.0);
        float dd = length(cp);
        if (b > 0.0 && dd < 1.09 && dd > 0.995) {
            float h = (dd - 1.0) / 0.09;
            vec3 cn = cp / dd;
            float lit = smoothstep(-0.25, 0.3, dot(cn, uSun));
            float burn = exp(-length(cn - normalize(uP)) / 0.3) * uEarthGlow;
            c += vec3(0.3, 0.55, 1.0) * exp(-h * 4.0) * lit * 1.6 + vec3(1.0, 0.45, 0.2) * exp(-h * 6.0) * (1.0 - smoothstep(0.0, 0.3, abs(dot(cn, uSun)))) * 0.6
               + vec3(1.0, 0.35, 0.08) * exp(-h * 3.0) * burn * 1.6;
        }
    }
    if (uSats > 0.001) {   // 궤도 명령 블록 위성 12기 + 지표로 내리는 데이터 링크
        for (int k = 0; k < 12; k++) {
            float on = clamp(uSats * 12.0 - float(k), 0.0, 1.0);
            if (on <= 0.0) continue;
            vec3 sp = satPos(k);
            float h = 0.028 * on;
            vec3 nb;
            float tb = boxHit(ro, rd, sp, h, nb);
            if (tb > 0.0 && tb < near) { near = tb; c = satShade(ro + rd * tb, sp, nb, h, k); }
            float tR, sR;
            float g = segRay(ro, rd, sp, uSite, tR, sR);
            if (tR < near) c += CYAN * exp(-g / max(0.002, tR * px * 1.5)) * 0.35 * on * (0.5 + 0.5 * step(0.9, fract(sR * 6.0 - uClock * 2.0)));
            c += CYAN * exp(-length(cross(rd, sp - ro)) / max(0.01, length(sp - ro) * px * 6.0)) * 0.25 * on;   // 광륜
        }
    }
    if (uImpactE >= 0.0) {   // 우주로 치솟는 분출 기둥
        float tR, sR;
        float top = 1.0 + 0.35 * clamp(uImpactE / 120.0, 0.0, 1.0) + 0.1;
        float g = segRay(ro, rd, uSite, uSite * top, tR, sR);
        if (tR < near + 0.05) {
            float w = 0.03 + 0.08 * sR;
            float n2 = fbm(vec3(g / w * 2.0, sR * 6.0 - uClock * 0.8, 0.0));
            float dens = exp(-(g * g) / (w * w)) * (1.0 - smoothstep(0.6, 1.0, sR)) * (0.5 + n2);
            c = mix(c, mix(vec3(1.0, 0.5, 0.15) * 3.0, vec3(0.12, 0.08, 0.07), smoothstep(0.0, 0.5, sR)), clamp(dens, 0.0, 0.9));
        }
    }
    vec2 hm = sph(ro, rd, uP, uR);
    float tP = INF;
    if (hm.x > 0.0 && hm.x < near) { near = hm.x; tP = hm.x; c = planetShade(normalize(ro + rd * hm.x - uP), rd, uSun, uLead, uHeat, uCracks); }
    c += atmoHalo(ro, rd, uP, uR, near);
    if (uDebris > 0.001) {
        float nd = near;
        vec3 d = debris(ro, rd, nd);
        if (nd < near) { c = d; near = nd; } else c += d;
    }
    if (uHeat > 0.001) c += sheath(ro, rd, near + uR) + trail(ro, rd, near, tP);
    if (uShock > 0.001) c += bowShock(ro, rd, near);
    if (uBox > 0.001) c += CYAN * boxWire(ro, rd, uP, uR * 1.12, px, near + uR * 3.0) * uBox * 0.9;
    if (uRing > 0.001) c += dataRings(ro, rd, near + uR * 3.0, tP, px);
    if (uChains > 0.001) {
        vec3 axis = normalize(uSite - uP);
        vec3 u = normalize(cross(axis, abs(axis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
        vec3 v = cross(axis, u);
        for (int k = 0; k < 12; k++) {
            float on = clamp(uChains * 12.0 - float(k), 0.0, 1.0);
            if (on <= 0.0) continue;
            float a = float(k) * TAU / 12.0;
            vec3 ring = u * cos(a) + v * sin(a);
            vec3 port = uP + ring * uR * 0.9 + axis * uR * 0.35;
            vec3 anchor = uSats > 0.5 ? satPos(k) : uSite + ring * 0.035;   // 위성이 있으면 위성에서 사슬 발사
            float reach = clamp(uReach * 1.3 - float(k) * 0.025, 0.0, 1.0);
            vec3 tip = mix(anchor, port, reach);
            float l = beam(ro, rd, anchor, tip, 0.0022, px, near + 1e-3, 0.06, uClock * (1.5 + 6.0 * uPull));
            if (uTextAmt > 0.001) {   // 사슬을 따라 흐르는 /tp · /ride 명령어
                float tRc, sc2;
                float gp = segRay(ro, rd, anchor, tip, tRc, sc2);
                float th = 0.016;
                if (tRc < near) {
                    float L = distance(anchor, tip);
                    float a = txt((sc2 * L) / (64.0 * th) - uClock * (0.15 + 0.6 * uPull) + float(k) * 0.37, (gp - 0.004) / th, mod(float(k), 2.0) < 0.5 ? 0.0 : 1.0);
                    c += CYAN * a * 1.4 * txtFade(th, tRc, px) * on * uTextAmt;
                }
            }
            // 뻗어가는 사슬 끝의 섬광
            float tR, sT;
            float g = segRay(ro, rd, tip, tip, tR, sT);
            float tipGlow = reach < 0.999 ? exp(-g / max(0.006, tR * px * 3.0)) * 3.0 : 0.0;
            c += (mix(CYAN, vec3(0.7, 1.0, 1.0) * 1.2, uPull) * l * (0.45 + 0.3 * uPull) + vec3(0.8, 1.0, 1.0) * tipGlow) * on;
        }
    }
    if (uWarp > 0.001) c = c * (1.0 - 0.6 * uWarp) + warp(ndc, aspect);
    return c;
}

// ───────────────────────── 메인
void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    vec2 ndc = uv * 2.0 - 1.0;
    float aspect = uRes.x / uRes.y;
    vec3 rd = normalize(uFwd + uRight * (ndc.x * uTanHalf * aspect) + uUp * (ndc.y * uTanHalf));
    float px = 2.0 * uTanHalf / uRes.y;

    if (uMode == 1) {
        vec3 c = space(rd, px, ndc, aspect);
        fragColor = vec4(mix(uFade.rgb, c, uFade.a), 1.0);
        return;
    }

    float d = texelFetch(uDepth, ivec2(gl_FragCoord.xy), 0).r;
    bool sky = d >= 0.999999;
    float tS = INF;
    if (!sky) {
        float z = d * 2.0 - 1.0;
        float ze = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
        tS = ze / max(dot(rd, uFwd), 1e-4);
    }

    // 열 아지랑이: 행성 아래·불기둥 근처 화면 일렁임
    vec2 suv = uv;
    if (uHeat > 0.01 && uR > 0.0) {
        float tR, sR;
        float gap = segRay(vec3(0.0), rd, uP + uLead * uR, uP - uLead * uR * 5.0, tR, sR);
        float mask = exp(-gap / (uR * 1.6)) * uHeat;
        suv += (vec2(vnoise(vec3(uv * 40.0, uClock * 3.0)), vnoise(vec3(uv * 40.0 + 7.0, uClock * 3.0))) - 0.5) * 0.012 * mask;
    }
    if (uImpact >= 0.0 && uImpact < 120.0) {   // 화구 위 열 아지랑이
        suv += (vec2(vnoise(vec3(uv * 25.0, uClock * 4.0)), vnoise(vec3(uv * 25.0 + 3.0, uClock * 4.0))) - 0.5) * 0.008 * exp(-uImpact / 60.0);
    }
    vec3 scene = toLin(texture(uScene, suv).rgb);
    vec3 c = scene;
    bool solid = !sky;

    if (sky) {
        vec3 skyC = groundSky(rd, px);
        if (uSkyMix > 0.0) c = mix(c, skyC, uSkyMix);
        if (uRift > 0.0) c += rift(rd, px);
        // 허공 아래쪽: 절차 지형으로 채움 (원경 샷)
        if (uGround > 0.0 && rd.y < -1e-4) {
            float tp = uG.y / rd.y;
            if (tp > 0.0) {
                c = mix(c, synthGround(rd, tp, skyC), uGround);
                tS = tp;
                solid = true;
                scene = c;
            }
        }
    } else {
        vec3 wp = rd * tS;
        float D = length(wp - uP);
        c *= 1.0 - uShadow * 0.65;
        c += scene * uHeat * vec3(1.6, 0.5, 0.18) * clamp(uR * uR * 3.0 / (D * D), 0.0, 3.0);
    }

    float tP = INF;
    if (uR > 0.0) {
        vec2 hp = sph(vec3(0.0), rd, uP, uR);
        if (hp.x > 0.0 && hp.x < tS) {
            vec3 n = normalize(rd * hp.x - uP);
            vec3 m = uSpin * n;
            float cut = uBreak * 1.15 - 0.08;
            float nz = uBreak > 0.0 ? fbm(m * 3.5) : 1.0;
            if (nz >= cut) {
                tP = hp.x;
                vec3 pc = planetShade(n, rd, uSun, uLead, uHeat, uCracks);
                if (uBreak > 0.0) pc += vec3(1.0, 0.5, 0.15) * 6.0 * (1.0 - smoothstep(0.0, 0.08, nz - cut));
                c = pc;
            } else {   // 부서져 나간 자리: 뒤가 비쳐 보이고 가장자리만 녹아 빛남
                c += vec3(1.0, 0.35, 0.08) * 1.5 * exp(-(cut - nz) * 18.0) * (1.0 - uBreak);
            }
        }
        c += atmoHalo(vec3(0.0), rd, uP, uR, min(tS, INF)) * (1.0 - uBreak);
        if (uHeat > 0.001) c += (sheath(vec3(0.0), rd, tS) + trail(vec3(0.0), rd, tS, tP)) * (1.0 - uBreak);
        if (uShock > 0.001) c += bowShock(vec3(0.0), rd, min(tS, tP + uR * 0.5)) * (1.0 - uBreak);
        if (uBox > 0.001) c += CYAN * boxWire(vec3(0.0), rd, uP, uR * 1.12, px, min(tS, INF)) * uBox * 0.9;
        if (uRing > 0.001) c += dataRings(vec3(0.0), rd, tS, tP, px) * (1.0 - uBreak);
        if (uBeams > 0.001) {
            vec3 bottom = uP - vec3(0.0, uR * 0.92, 0.0);
            for (int i = 0; i < 4; i++) {
                c += CYAN * beam(vec3(0.0), rd, uA[i], bottom, 1.2, px, min(tS, tP), 40.0, uClock * 3.0) * uBeams;
                float tRc, sc2;
                float gp = segRay(vec3(0.0), rd, uA[i], bottom, tRc, sc2);
                if (tRc < min(tS, tP)) {
                    float th = 6.0, L = distance(uA[i], bottom);
                    float a = txt(sc2 * L / (64.0 * th) - uClock * 0.4 + float(i) * 0.25, (gp - 2.5) / th, float(i == 0 ? 0 : i == 1 ? 3 : i == 2 ? 4 : 13));
                    c += CYAN * a * 1.3 * txtFade(th, tRc, px) * uBeams * uTextAmt;
                }
            }
        }
    }
    if (uGrid > 0.001 && solid && tS < 4000.0) {   // /kill @e[distance=..80] 선택 영역: 블록 격자 + 경계 + 글자
        vec3 w = rd * tS + uCamW;
        float r = length(w.xz);
        if (r < 120.0 && abs(w.y) < 60.0) {
            float lw = 0.04 + px * tS * 1.2;
            vec2 f1 = abs(fract(w.xz + 0.5) - 0.5), f4 = abs(fract(w.xz / 8.0 + 0.5) - 0.5) * 8.0;
            float g1 = (1.0 - smoothstep(0.0, lw, min(f1.x, f1.y))) * (1.0 - smoothstep(4.0, 18.0, px * tS * 40.0));
            float g8 = 1.0 - smoothstep(0.0, lw * 1.5, min(f4.x, f4.y));
            float inside = 1.0 - smoothstep(78.0, 80.0, r);
            float border = exp(-abs(r - 80.0) / (0.6 + px * tS));
            float sweep = exp(-abs(fract(r / 40.0 - uClock * 0.8) - 0.5) * 30.0);
            vec3 red = vec3(1.0, 0.15, 0.2);
            c += mix(CYAN, red, 0.6) * (g1 * 0.25 + g8 * 0.6) * inside * (0.6 + 0.8 * sweep) * uGrid;
            c += red * border * 3.0 * uGrid;
            float ang = atan(w.z, w.x) / TAU + 0.5;
            float a = txt(ang * 2.0 + uClock * 0.02, (r - 83.0) / 9.0, 6.0);
            c += red * a * 2.5 * txtFade(9.0, tS, px) * uGrid;
        }
    }
    if (uMeteors > 0.001) c += meteors(rd, min(tS, tP), px);
    if (uImpact >= 0.0) c = impact(rd, c, tS, rd * tS, solid, px);

    fragColor = vec4(mix(uFade.rgb, c, uFade.a), 1.0);
}
