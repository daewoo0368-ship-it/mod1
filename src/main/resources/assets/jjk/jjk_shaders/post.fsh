#version 330 core
// 「/tp 행성 낙하」 최종 합성: 충격파 굴절 · 색수차 · 방사 줌 블러 · 블룸 · 광선(god rays) · 렌즈 플레어 ·
// 톤매핑/그레이딩 · 색 비네트 · 섬광 잔상 · 애니 임팩트 프레임 · 필름 그레인. 선형 HDR 입력 → sRGB 출력.
out vec4 fragColor;

uniform sampler2D uHdr;     // 0 장면 (선형 HDR)
uniform sampler2D uBloom;   // 1 블룸 (½ 해상도)
uniform sampler2D uHold;    // 2 잔상용으로 붙잡아 둔 프레임

uniform vec2 uRes;
uniform float uClock;
uniform float uBloomAmt, uCA, uZoom, uExposure, uHoldAmt, uGrain, uContrast, uSat, uFilm;
uniform vec2 uZoomC;
uniform vec4 uFlash;        // rgb, 세기 (1 넘으면 하얗게 날아감)
uniform vec4 uVig;          // rgb, 세기
uniform vec4 uWave;         // 화면 중심 uv, 반지름(화면 높이 비율), 세기
uniform vec4 uLight;        // 광원 uv, 세기, 플레어 세기
uniform vec3 uLightCol;
uniform vec4 uImpactF;      // 세기, 모드(0 먹 / 1 반전 / 2 진홍 / 3 청록), 중심 uv
uniform float uGlitch;      // 명령 실행 순간의 디지털 글리치

float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }

vec3 knee(vec3 c) {   // 0.8 까지는 그대로 (바닐라 월드 색 보존), 넘치면 부드럽게 눌러줌
    vec3 k = vec3(0.8);
    return mix(c, k + (1.0 - k) * (1.0 - exp(-(c - k) / (1.0 - k))), step(k, c));
}
vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
vec3 tone(vec3 c) { return mix(knee(c), aces(c * 1.25), uFilm); }

vec3 hdr(vec2 uv) { return texture(uHdr, clamp(uv, vec2(0.001), vec2(0.999))).rgb; }
vec3 bloomAt(vec2 uv) { return texture(uBloom, clamp(uv, vec2(0.001), vec2(0.999))).rgb; }
vec3 fetch(vec2 uv, vec2 dir, float ca) {
    if (ca < 1e-5) return hdr(uv);
    return vec3(hdr(uv + dir * ca).r, hdr(uv).g, hdr(uv - dir * ca).b);
}

void main() {
    vec2 uv = gl_FragCoord.xy / uRes;
    float aspect = uRes.x / uRes.y;

    // ── 충격파 굴절: 화면을 지나가는 고리가 뒤 풍경을 휘게 함
    float ringGlow = 0.0;
    if (uWave.w > 0.001) {
        vec2 d = (uv - uWave.xy) * vec2(aspect, 1.0);
        float r = length(d);
        float wdt = 0.02 + 0.06 * uWave.z;
        float x = (r - uWave.z) / wdt;
        float band = exp(-x * x);
        uv -= (d / max(r, 1e-4)) / vec2(aspect, 1.0) * band * x * uWave.w * 0.035;
        ringGlow = band * uWave.w;
    }

    // ── 글리치: 가로 띠 밀림 + 블록 픽셀화
    float gT = floor(uClock * 30.0);
    if (uGlitch > 0.001) {
        float band = floor(uv.y * 28.0);
        if (hash12(vec2(band, gT)) < uGlitch * 0.55) uv.x += (hash12(vec2(band, gT + 3.1)) - 0.5) * 0.09 * uGlitch;
        vec2 cell = floor(uv * vec2(16.0, 9.0));
        if (hash12(cell + gT * 1.7) < uGlitch * 0.22) uv = (floor(uv * vec2(80.0, 45.0)) + 0.5) / vec2(80.0, 45.0);
    }
    vec2 c0 = uv - 0.5;
    float ca = uGlitch * 0.006 + uCA * 0.014 * dot(c0 * vec2(aspect, 1.0), c0 * vec2(aspect, 1.0)) * 2.0 + uCA * 0.0015;
    vec3 col = fetch(uv, c0, ca);
    vec3 bl = bloomAt(uv);

    // ── 방사 줌 블러 (돌진·충돌)
    if (uZoom > 0.001) {
        vec3 acc = col, bacc = bl;
        float total = 1.0;
        vec2 dz = uv - uZoomC;
        float j = hash12(gl_FragCoord.xy);
        for (int i = 1; i < 12; i++) {
            float k = (float(i) + j) / 12.0;
            vec2 suv = uZoomC + dz * (1.0 - uZoom * 0.14 * k);
            float w = 1.0 - k * 0.6;
            acc += fetch(suv, c0, ca) * w;
            bacc += bloomAt(suv) * w;
            total += w;
        }
        col = acc / total;
        bl = bacc / total;
    }

    col += bl * uBloomAmt;

    // ── 광선: 광원 쪽으로 걸어가며 블룸(밝은 것)만 모음 → 행성 테두리·화구 사이로 빛줄기
    if (uLight.z > 0.001) {
        vec2 delta = (uLight.xy - uv) / 36.0;
        vec2 p = uv + delta * hash12(gl_FragCoord.xy + 17.0);
        vec3 sum = vec3(0.0);
        float w = 1.0;
        for (int i = 0; i < 36; i++) {
            p += delta;
            if (p.x < 0.0 || p.y < 0.0 || p.x > 1.0 || p.y > 1.0) break;
            sum += bloomAt(p) * w;
            w *= 0.965;
        }
        col += sum / 36.0 * uLightCol * uLight.z * 1.2;
        // 렌즈 플레어: 아나모픽 줄 + 중심 반대편 고스트
        if (uLight.w > 0.001) {
            vec2 dl = (uv - uLight.xy) * vec2(aspect, 1.0);
            col += vec3(0.45, 0.65, 1.0) * exp(-abs(dl.y) * 260.0) * exp(-abs(dl.x) * 1.6) * uLight.w * 1.2;
            col += uLightCol * exp(-length(dl) * 9.0) * uLight.w * 0.6;
            for (int g = 0; g < 4; g++) {
                float k = float(g) * 0.4 + 0.3;
                vec2 gp = mix(uLight.xy, vec2(1.0) - uLight.xy, k);
                float rad = 0.02 + 0.035 * float(g);
                float dg = length((uv - gp) * vec2(aspect, 1.0));
                vec3 tint = g == 0 ? vec3(1.0, 0.5, 0.3) : g == 1 ? vec3(0.3, 1.0, 0.6) : g == 2 ? vec3(0.4, 0.6, 1.0) : vec3(1.0, 0.4, 0.9);
                col += tint * (smoothstep(rad, rad * 0.75, dg) * 0.06 + exp(-abs(dg - rad) * 300.0) * 0.05) * uLight.w;
            }
        }
    }
    col += vec3(1.0, 0.85, 0.65) * ringGlow * 0.6;

    // ── 톤매핑 + 그레이딩
    col *= uExposure;
    col = tone(col);
    float grey = lum(col);
    col = mix(vec3(grey), col, uSat);
    if (abs(uContrast) > 0.001) col = clamp((col - 0.45) * (1.0 + uContrast) + 0.45, 0.0, 1.5);

    vec2 cv = gl_FragCoord.xy / uRes - 0.5;
    float edge = smoothstep(0.25, 0.95, length(cv * vec2(aspect * 0.8, 1.0)) * 1.3);
    col = mix(col, uVig.rgb * (0.35 + 0.65 * grey), clamp(uVig.a * edge, 0.0, 1.0));

    // ── 섬광 잔상: 충돌 직전의 장면이 눈에 타 들어간 채 천천히 사라짐
    if (uHoldAmt > 0.001) {
        vec3 held = tone(texture(uHold, gl_FragCoord.xy / uRes).rgb);
        float ghost = smoothstep(0.55, 1.0, lum(held)) * clamp(uHoldAmt, 0.0, 1.0) * 0.8;
        col = 1.0 - (1.0 - col) * (1.0 - ghost * vec3(1.0, 0.85, 0.7));
    }

    col += uFlash.rgb * uFlash.a;
    col = mix(col, vec3(1.0), clamp(uFlash.a - 1.0, 0.0, 1.0));

    // ── 임팩트 프레임: 두 색 먹선 + 집중선
    if (uImpactF.x > 0.001) {
        vec2 st = 1.5 / uRes;
        vec2 iu = gl_FragCoord.xy / uRes;
        float gx = lum(tone(hdr(iu + vec2(st.x, 0.0)))) - lum(tone(hdr(iu - vec2(st.x, 0.0))));
        float gy = lum(tone(hdr(iu + vec2(0.0, st.y)))) - lum(tone(hdr(iu - vec2(0.0, st.y))));
        float ink = smoothstep(0.05, 0.16, length(vec2(gx, gy)));
        float l = lum(tone(hdr(iu)) + bloomAt(iu) * 0.5);
        float m = smoothstep(0.30, 0.36, l);
        vec2 dc = (iu - uImpactF.zw) * vec2(aspect, 1.0);
        float ang = atan(dc.y, dc.x);
        float rr = length(dc);
        float lines = smoothstep(0.55, 1.0, hash12(vec2(floor(ang * 90.0), floor(uClock * 20.0))))
                    * smoothstep(0.18, 0.6, rr);
        float shape = max(m, ink);
        int mode = int(uImpactF.y + 0.5);
        vec3 fr;
        if (mode == 0) fr = mix(vec3(0.02), vec3(1.0), shape) + vec3(0.85) * lines * (1.0 - shape);
        else if (mode == 1) fr = mix(vec3(1.0, 0.98, 0.95), vec3(0.02), shape) * (1.0 - lines * (1.0 - shape) * 0.85);
        else if (mode == 2) fr = mix(vec3(0.02, 0.0, 0.0), mix(vec3(0.85, 0.04, 0.02), vec3(1.0, 0.95, 0.9), smoothstep(0.6, 0.9, l)), shape) + vec3(0.6, 0.0, 0.0) * lines * (1.0 - shape);
        else fr = mix(vec3(0.0, 0.02, 0.04), mix(vec3(0.2, 1.0, 1.0), vec3(1.0), smoothstep(0.6, 0.9, l)), shape) + vec3(0.1, 0.5, 0.5) * lines * (1.0 - shape);
        col = mix(col, fr, clamp(uImpactF.x, 0.0, 1.0));
    }

    if (uGlitch > 0.001) {   // 주사선 + 청록 데이터 줄
        col *= 1.0 - uGlitch * 0.22 * (0.5 + 0.5 * sin(gl_FragCoord.y * 3.14159));
        float bandL = floor(gl_FragCoord.y / uRes.y * 60.0);
        col += vec3(0.2, 1.0, 1.0) * step(1.0 - uGlitch * 0.08, hash12(vec2(bandL, gT * 0.5))) * 0.35;
    }
    // ── 필름 그레인 + 디더
    col += (hash12(gl_FragCoord.xy + fract(uClock * 7.3) * 431.0) - 0.5) * uGrain * 0.12;
    col = pow(max(col, vec3(0.0)), vec3(1.0 / 2.2));
    col += (hash12(gl_FragCoord.xy * 1.37 + uClock) - 0.5) / 255.0;
    fragColor = vec4(col, 1.0);
}
