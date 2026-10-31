#version 330 core
// 블룸 축소 (13탭, Jimenez 2014). 첫 단계는 밝은 빛만 거르고(부드러운 무릎) 반딧불 억제(Karis 평균).
out vec4 fragColor;
uniform sampler2D uSrc;
uniform vec2 uOut;        // 출력 해상도
uniform vec2 uTexel;      // 입력 텍셀 크기
uniform float uPrefilter; // 1 = 첫 단계
uniform float uThreshold;

vec3 pre(vec3 c) {
    float br = max(c.r, max(c.g, c.b));
    float knee = uThreshold * 0.5;
    float soft = clamp(br - uThreshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float w = max(soft, br - uThreshold) / max(br, 1e-4);
    return c * w;
}
vec3 tap(vec2 uv) { return min(texture(uSrc, clamp(uv, vec2(0.0), vec2(1.0))).rgb, vec3(60.0)); }
float karis(vec3 c) { return 1.0 / (1.0 + dot(c, vec3(0.2126, 0.7152, 0.0722))); }

void main() {
    vec2 uv = gl_FragCoord.xy / uOut;
    vec2 t = uTexel;
    vec3 a = tap(uv + t * vec2(-2, 2)), b = tap(uv + t * vec2(0, 2)), c = tap(uv + t * vec2(2, 2));
    vec3 d = tap(uv + t * vec2(-2, 0)), e = tap(uv), f = tap(uv + t * vec2(2, 0));
    vec3 g = tap(uv + t * vec2(-2, -2)), h = tap(uv + t * vec2(0, -2)), i = tap(uv + t * vec2(2, -2));
    vec3 j = tap(uv + t * vec2(-1, 1)), k = tap(uv + t * vec2(1, 1));
    vec3 l = tap(uv + t * vec2(-1, -1)), m = tap(uv + t * vec2(1, -1));
    vec3 col;
    if (uPrefilter > 0.5) {
        vec3 g0 = (a + b + d + e) * 0.25, g1 = (b + c + e + f) * 0.25, g2 = (d + e + g + h) * 0.25, g3 = (e + f + h + i) * 0.25, g4 = (j + k + l + m) * 0.25;
        g0 = pre(g0); g1 = pre(g1); g2 = pre(g2); g3 = pre(g3); g4 = pre(g4);
        float w0 = karis(g0), w1 = karis(g1), w2 = karis(g2), w3 = karis(g3), w4 = karis(g4);
        col = (g0 * w0 * 0.125 + g1 * w1 * 0.125 + g2 * w2 * 0.125 + g3 * w3 * 0.125 + g4 * w4 * 0.5)
            / (w0 * 0.125 + w1 * 0.125 + w2 * 0.125 + w3 * 0.125 + w4 * 0.5);
    } else {
        col = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    }
    fragColor = vec4(col, 1.0);
}
