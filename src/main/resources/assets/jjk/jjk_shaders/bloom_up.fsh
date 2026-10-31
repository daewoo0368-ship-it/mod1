#version 330 core
// 블룸 확대 (3×3 텐트) — 위 단계에 가산 합성으로 쌓음
out vec4 fragColor;
uniform sampler2D uSrc;
uniform vec2 uOut;
uniform vec2 uTexel;
uniform float uRadius;

void main() {
    vec2 uv = gl_FragCoord.xy / uOut;
    vec2 t = uTexel * uRadius;
    vec3 s = texture(uSrc, uv).rgb * 4.0;
    s += (texture(uSrc, uv + vec2(-t.x, 0.0)).rgb + texture(uSrc, uv + vec2(t.x, 0.0)).rgb
        + texture(uSrc, uv + vec2(0.0, -t.y)).rgb + texture(uSrc, uv + vec2(0.0, t.y)).rgb) * 2.0;
    s += texture(uSrc, uv - t).rgb + texture(uSrc, uv + t).rgb
       + texture(uSrc, uv + vec2(t.x, -t.y)).rgb + texture(uSrc, uv + vec2(-t.x, t.y)).rgb;
    fragColor = vec4(s / 16.0, 1.0);
}
