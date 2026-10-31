package com.tubilt.jjk.client;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tubilt.jjk.JjkMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 「/tp 행성 낙하」 화면 셰이더 실행기 (원시 OpenGL) — 다단 후처리.
 *  1) 월드 색 → 사본  2) planet.fsh: 광선 추적 장면을 선형 HDR(RGBA16F)로
 *  3) 블룸 피라미드 (13탭 축소 ×6 → 텐트 확대 가산)  4) post.fsh: 굴절·색수차·줌블러·광선·플레어·톤매핑·임팩트 프레임 → 메인 색 버퍼
 *  깊이 버퍼를 읽어 지형과 정확히 겹치게 합성. 바닐라 GL 상태는 그리기 전 값으로 전부 되돌림.
 *  컴파일 실패 등 문제가 생기면 ok=false → PlanetRender 의 예전 메시 렌더로 대체.
 */
final class PlanetShader {
    private PlanetShader() {}

    static boolean tried = false, ok = false;
    static int prog, progDown, progUp, progPost, vao;
    static int sceneTex, sceneFbo, hdrTex, hdrFbo, holdTex, holdFbo, sceneW = -1, sceneH = -1;
    static final int LEVELS = 6;
    static final int[] bTex = new int[LEVELS], bFbo = new int[LEVELS], bW = new int[LEVELS], bH = new int[LEVELS];
    static int outFbo, outColorId = -1;
    static int texPlanet, texRelief, texSky, texEarth, texAux, texText;
    static final Map<String, Integer> U = new HashMap<>();
    static int cur;

    /** 프레임마다 채우는 값. */
    static final class Frame {
        int mode;                       // 0 지상, 1 우주
        Vector3f right = new Vector3f(), up = new Vector3f(), fwd = new Vector3f();
        float tanHalf, near = 0.05f, far = 1024f, clock;
        Vector3f sun = new Vector3f(0.45f, 0.35f, -0.8f).normalize();
        Matrix3f spin = new Matrix3f(), eSpin = new Matrix3f();
        Vector3f camW = new Vector3f();
        Vector3f p = new Vector3f(), lead = new Vector3f(0, -1, 0);
        float r, heat, cracks, brk, box, atmo = 1;
        Vector3f g = new Vector3f();
        float impact = -1, skyMix, shadow, beams, rift, meteors, lava, ground;
        Vector3f[] anchors = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        Vector3f riftC = new Vector3f(0, 180, 0);
        Vector3f camS = new Vector3f(), site = new Vector3f(0, 1, 0);
        float chains, pull, reach = 1, warp, debris, shock, earthGlow;
        float vox, ring, grid, textAmt = 1, glitch, sats, impactE = -1;
        float fadeR = 1, fadeG = 1, fadeB = 1, fadeA = 1;
        // ── 후처리
        float bloom = 0.6f, ca, zoom, exposure = 1, holdAmt, grain = 0.3f, contrast, sat = 1, film;
        float zoomX = 0.5f, zoomY = 0.5f;
        float[] flash = {1, 1, 1, 0}, vig = {0, 0, 0, 0.45f}, wave = {0.5f, 0.5f, 0, 0}, light = {0.5f, 0.5f, 0, 0}, impactF = {0, 0, 0.5f, 0.5f};
        float[] lightCol = {1, 0.8f, 0.6f};
        boolean capture;                // 이번 프레임을 잔상으로 붙잡음
    }

    static boolean ensure() {
        if (tried) return ok;
        tried = true;
        try {
            String vs = read("jjk_shaders/planet.vsh");
            prog = link(vs, read("jjk_shaders/planet.fsh"));
            progDown = link(vs, read("jjk_shaders/bloom_down.fsh"));
            progUp = link(vs, read("jjk_shaders/bloom_up.fsh"));
            progPost = link(vs, read("jjk_shaders/post.fsh"));
            vao = GL30C.glGenVertexArrays();
            texPlanet = texture("textures/fx3d/planet_albedo.png");
            texRelief = texture("textures/fx3d/planet_relief.png");
            texSky = texture("textures/fx3d/space.png");
            texEarth = texture("textures/fx3d/earth_day.png");
            texAux = texture("textures/fx3d/earth_aux.png");
            texText = texture("textures/fx3d/cmd_text.png");
            ok = true;
            JjkMod.LOG.info("[JJK] 행성 셰이더 준비 완료 (HDR + 블룸 + 후처리)");
        } catch (Throwable t) {
            ok = false;
            JjkMod.LOG.warn("[JJK] 행성 셰이더를 쓸 수 없음 → 메시 렌더로 대체", t);
        }
        return ok;
    }

    static String read(String path) throws IOException {
        var res = Minecraft.getInstance().getResourceManager().getResourceOrThrow(JjkMod.id(path));
        try (InputStream in = res.open()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    static int shader(int type, String src) {
        int s = GL20C.glCreateShader(type);
        GL20C.glShaderSource(s, src);
        GL20C.glCompileShader(s);
        if (GL20C.glGetShaderi(s, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(s);
            GL20C.glDeleteShader(s);
            throw new IllegalStateException("셰이더 컴파일 실패: " + log);
        }
        return s;
    }

    static int link(String vs, String fs) {
        int v = shader(GL20C.GL_VERTEX_SHADER, vs), f = shader(GL20C.GL_FRAGMENT_SHADER, fs);
        int p = GL20C.glCreateProgram();
        GL20C.glAttachShader(p, v);
        GL20C.glAttachShader(p, f);
        GL20C.glLinkProgram(p);
        GL20C.glDeleteShader(v);
        GL20C.glDeleteShader(f);
        if (GL20C.glGetProgrami(p, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) throw new IllegalStateException("셰이더 링크 실패: " + GL20C.glGetProgramInfoLog(p));
        return p;
    }

    /** PNG → GL 텍스처 (밉맵, 가로 반복). 바인딩 상태는 원래대로. */
    static int texture(String path) throws IOException {
        byte[] bytes;
        var res = Minecraft.getInstance().getResourceManager().getResourceOrThrow(JjkMod.id(path));
        try (InputStream in = res.open()) { bytes = in.readAllBytes(); }
        ByteBuffer src = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack st = MemoryStack.stackPush()) {
            src.put(bytes).flip();
            IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
            ByteBuffer img = STBImage.stbi_load_from_memory(src, w, h, c, 4);
            if (img == null) throw new IOException(path + ": " + STBImage.stbi_failure_reason());
            int prevActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int prevTex = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            int prevPbo = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
            int prevAlign = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT), prevRow = GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH);
            int prevSkipP = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS), prevSkipR = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
            int id = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, id);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, w.get(0), h.get(0), 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, img);
            GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR_MIPMAP_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL14C.GL_MIRRORED_REPEAT);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTex);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, prevPbo);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, prevAlign);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, prevRow);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, prevSkipP);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, prevSkipR);
            GL13C.glActiveTexture(prevActive);
            STBImage.stbi_image_free(img);
            return id;
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    static void use(int p) { cur = p; GL20C.glUseProgram(p); }
    static int u(String name) { return U.computeIfAbsent(cur + ":" + name, n -> GL20C.glGetUniformLocation(cur, name)); }
    static void u1(String n, float v) { GL20C.glUniform1f(u(n), v); }
    static void u2(String n, float a, float b) { GL20C.glUniform2f(u(n), a, b); }
    static void u3(String n, Vector3f v) { GL20C.glUniform3f(u(n), v.x, v.y, v.z); }
    static void u3(String n, float[] v) { GL20C.glUniform3f(u(n), v[0], v[1], v[2]); }
    static void u4(String n, float[] v) { GL20C.glUniform4f(u(n), v[0], v[1], v[2], v[3]); }
    static void tex(int unit, int id, String name) {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, id);
        GL33C.glBindSampler(unit, 0);
        GL20C.glUniform1i(u(name), unit);
    }

    static final FloatBuffer M3 = BufferUtils.createFloatBuffer(9);
    static void m3(String n, Matrix3f m) { M3.clear(); m.get(M3); GL20C.glUniformMatrix3fv(u(n), false, M3); }

    /** 색 텍스처 + FBO 하나 (선형 필터, 가장자리 고정). */
    static int[] target(int w, int h, boolean hdr) {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        int t = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, t);
        if (hdr) GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA16F, w, h, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, (ByteBuffer) null);
        else GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, w, h, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
        int f = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, f);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, t, 0);
        return new int[]{t, f};
    }
    static void free(int t, int f) { if (t != 0) GL11C.glDeleteTextures(t); if (f != 0) GL30C.glDeleteFramebuffers(f); }

    static void resize(int w, int h) {
        free(sceneTex, sceneFbo); free(hdrTex, hdrFbo); free(holdTex, holdFbo);
        for (int i = 0; i < LEVELS; i++) free(bTex[i], bFbo[i]);
        int[] a = target(w, h, false); sceneTex = a[0]; sceneFbo = a[1];
        a = target(w, h, true); hdrTex = a[0]; hdrFbo = a[1];
        a = target(w, h, true); holdTex = a[0]; holdFbo = a[1];
        int lw = w, lh = h;
        for (int i = 0; i < LEVELS; i++) {
            lw = Math.max(1, lw / 2); lh = Math.max(1, lh / 2);
            a = target(lw, lh, true); bTex[i] = a[0]; bFbo[i] = a[1]; bW[i] = lw; bH[i] = lh;
        }
        // 잔상 텍스처는 검게 비워 둠
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, holdFbo);
        GL11C.glClearColor(0, 0, 0, 1);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        sceneW = w; sceneH = h;
    }

    static void tri() { GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3); }

    /** 메인 색 버퍼를 셰이더 결과로 덮어씀. */
    static void draw(Frame f) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!(main.getColorTexture() instanceof GlTexture col) || !(main.getDepthTexture() instanceof GlTexture dep)) return;
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) return;

        // ── 현재 GL 상태 저장
        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING), prevDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevProg = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM), prevVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int[] vp = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, vp);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND), depthT = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE), scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        int bSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB), bDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
                bSrcA = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA), bDstA = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
        int bEqRgb = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB), bEqA = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA);
        boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        ByteBuffer cm = BufferUtils.createByteBuffer(16);
        GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, cm);
        float[] clear = new float[4];
        GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, clear);
        int units = 8;
        int[] prevTex = new int[units], prevSamp = new int[units];
        for (int i = 0; i < units; i++) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
            prevTex[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            prevSamp[i] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
        }
        int depId = dep.glId();
        int[] depParams = new int[3];
        try {
            if (sceneW != w || sceneH != h) resize(w, h);
            if (outColorId != col.glId()) {
                if (outFbo != 0) GL30C.glDeleteFramebuffers(outFbo);
                outFbo = GL30C.glGenFramebuffers();
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, outFbo);
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, col.glId(), 0);
                outColorId = col.glId();
            }
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDepthMask(false);
            GL11C.glColorMask(true, true, true, true);
            GL30C.glBindVertexArray(vao);

            // ── 1) 월드 색 → 사본
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, outFbo);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, sceneFbo);
            GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);

            // ── 2) 장면 → HDR
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, hdrFbo);
            GL11C.glViewport(0, 0, w, h);
            use(prog);
            int[] tx = {sceneTex, depId, texPlanet, texRelief, texSky, texEarth, texAux, texText};
            String[] names = {"uScene", "uDepth", "uPlanetTex", "uRelief", "uSky", "uEarth", "uEarthAux", "uText"};
            for (int i = 0; i < units; i++) tex(i, tx[i], names[i]);
            // 깊이 텍스처: 비교 모드 끄고 최근접 (그린 뒤 원래대로)
            GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
            depParams[0] = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE);
            depParams[1] = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER);
            depParams[2] = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);

            u2("uRes", w, h);
            u3("uRight", f.right); u3("uUp", f.up); u3("uFwd", f.fwd);
            u1("uTanHalf", f.tanHalf); u1("uNear", f.near); u1("uFar", f.far); u1("uClock", f.clock);
            GL20C.glUniform1i(u("uMode"), f.mode);
            u3("uSun", f.sun); m3("uSpin", f.spin); m3("uESpin", f.eSpin); u3("uCamW", f.camW);
            u3("uP", f.p); u1("uR", f.r); u1("uHeat", f.heat); u1("uCracks", f.cracks); u1("uBreak", f.brk); u1("uBox", f.box); u1("uAtmo", f.atmo);
            u3("uLead", f.lead);
            u3("uG", f.g); u1("uImpact", f.impact); u1("uSkyMix", f.skyMix); u1("uShadow", f.shadow); u1("uBeams", f.beams); u1("uRift", f.rift);
            u1("uMeteors", f.meteors); u1("uLava", f.lava); u1("uGround", f.ground);
            for (int i = 0; i < 4; i++) u3("uA[" + i + "]", f.anchors[i]);
            u3("uRiftC", f.riftC);
            u3("uCamS", f.camS); u3("uSite", f.site); u1("uChains", f.chains); u1("uPull", f.pull); u1("uReach", f.reach);
            u1("uWarp", f.warp); u1("uDebris", f.debris); u1("uShock", f.shock); u1("uEarthGlow", f.earthGlow);
            u1("uSats", f.sats); u1("uImpactE", f.impactE); u1("uVox", f.vox); u1("uRing", f.ring); u1("uGrid", f.grid); u1("uTextAmt", f.textAmt);
            GL20C.glUniform4f(u("uFade"), f.fadeR, f.fadeG, f.fadeB, f.fadeA);
            tri();

            GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, depParams[0]);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, depParams[1]);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, depParams[2]);

            // ── 2b) 잔상 붙잡기
            if (f.capture) {
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, hdrFbo);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, holdFbo);
                GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
            }

            // ── 3) 블룸 피라미드
            use(progDown);
            for (int i = 0; i < LEVELS; i++) {
                int src = i == 0 ? hdrTex : bTex[i - 1];
                int sw = i == 0 ? w : bW[i - 1], sh = i == 0 ? h : bH[i - 1];
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, bFbo[i]);
                GL11C.glViewport(0, 0, bW[i], bH[i]);
                tex(0, src, "uSrc");
                u2("uOut", bW[i], bH[i]);
                u2("uTexel", 1f / sw, 1f / sh);
                u1("uPrefilter", i == 0 ? 1 : 0);
                u1("uThreshold", 1.0f);
                tri();
            }
            use(progUp);
            GL11C.glEnable(GL11C.GL_BLEND);
            GL14C.glBlendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ONE, GL11C.GL_ONE, GL11C.GL_ONE);
            GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
            for (int i = LEVELS - 1; i > 0; i--) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, bFbo[i - 1]);
                GL11C.glViewport(0, 0, bW[i - 1], bH[i - 1]);
                tex(0, bTex[i], "uSrc");
                u2("uOut", bW[i - 1], bH[i - 1]);
                u2("uTexel", 1f / bW[i], 1f / bH[i]);
                u1("uRadius", 1.0f);
                tri();
            }
            GL11C.glDisable(GL11C.GL_BLEND);

            // ── 4) 최종 합성 → 메인 색 버퍼
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, outFbo);
            GL11C.glViewport(0, 0, w, h);
            use(progPost);
            tex(0, hdrTex, "uHdr");
            tex(1, bTex[0], "uBloom");
            tex(2, holdTex, "uHold");
            u2("uRes", w, h);
            u1("uClock", f.clock);
            u1("uBloomAmt", f.bloom); u1("uCA", f.ca); u1("uZoom", f.zoom); u1("uExposure", f.exposure); u1("uHoldAmt", f.holdAmt);
            u1("uGrain", f.grain); u1("uContrast", f.contrast); u1("uSat", f.sat); u1("uFilm", f.film);
            u2("uZoomC", f.zoomX, f.zoomY);
            u4("uFlash", f.flash); u4("uVig", f.vig); u4("uWave", f.wave); u4("uLight", f.light); u4("uImpactF", f.impactF);
            u3("uLightCol", f.lightCol); u1("uGlitch", f.glitch);
            tri();
        } finally {
            // ── 원래 GL 상태로 복구 (바닐라 상태 캐시와 어긋나지 않게)
            for (int i = 0; i < units; i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTex[i]);
                GL33C.glBindSampler(i, prevSamp[i]);
            }
            GL13C.glActiveTexture(prevActive);
            GL30C.glBindVertexArray(prevVao);
            GL20C.glUseProgram(prevProg);
            GL11C.glViewport(vp[0], vp[1], vp[2], vp[3]);
            GL14C.glBlendFuncSeparate(bSrcRgb, bDstRgb, bSrcA, bDstA);
            GL20C.glBlendEquationSeparate(bEqRgb, bEqA);
            if (blend) GL11C.glEnable(GL11C.GL_BLEND); else GL11C.glDisable(GL11C.GL_BLEND);
            if (depthT) GL11C.glEnable(GL11C.GL_DEPTH_TEST); else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            if (cull) GL11C.glEnable(GL11C.GL_CULL_FACE); else GL11C.glDisable(GL11C.GL_CULL_FACE);
            if (scissor) GL11C.glEnable(GL11C.GL_SCISSOR_TEST); else GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDepthMask(depthMask);
            GL11C.glColorMask(cm.get(0) != 0, cm.get(1) != 0, cm.get(2) != 0, cm.get(3) != 0);
            GL11C.glClearColor(clear[0], clear[1], clear[2], clear[3]);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDraw);
        }
    }
}
