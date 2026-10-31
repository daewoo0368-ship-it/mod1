package com.tubilt.jjk.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.Techniques;
import com.tubilt.jjk.mech.MechEntities;
import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public final class JjkClient {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(JjkMod.id("main"));
    public static KeyMapping KEY_CAST, KEY_MENU, KEY_NEXT, KEY_PREV, KEY_LASER, KEY_SLAM;
    static boolean nextHeld = false, prevHeld = false;

    // ── 서버에서 받은 상태
    public static int ce = 1000, ceMax = 1000, flags = 0, domain = 0;
    public static List<Integer> cooldowns = List.of();
    public static boolean hasServerMod = false;
    /** 직업(null = 미선택) · 해금된 능력 id. unlockInfo 가 false 면(서버 정보 없음) 전부 표시. */
    public static String job = null;
    public static java.util.Set<String> unlocked = new java.util.HashSet<>();
    public static boolean unlockInfo = false;
    public static List<Techniques.Tech> visible() {
        if (!unlockInfo) return Techniques.ALL;
        List<Techniques.Tech> out = new java.util.ArrayList<>();
        for (Techniques.Tech t : Techniques.ALL) if (unlocked.contains(t.id())) out.add(t);
        return out;
    }
    /** 해금된 능력 사이에서 dir 칸 이동. */
    static void step(int dir) {
        List<Techniques.Tech> v = visible();
        if (v.isEmpty()) return;
        Techniques.Tech cur = selectedTech();
        int idx = Math.max(0, v.indexOf(cur));
        idx = ((idx + dir) % v.size() + v.size()) % v.size();
        selected = Techniques.ALL.indexOf(v.get(idx));
        saveSelected(); JjkHud.pulse();
    }

    // ── 선택한 술식
    public static int selected = 0;

    // ── 화면 효과
    static int shakeTicks = 0, shakeTotal = 1;
    static float shakePower = 0, lastDx = 0, lastDy = 0;
    static int flashColor = 0, flashTicks = 0, flashTotal = 1;
    static final Random RNG = new Random();
    static boolean machineFire = false;
    static boolean wasOnMech = false, slamming = false, slamAirborne = false, gunHeld = false;
    static net.minecraft.client.CameraType prevCamera = null;

    public static void init(BusGroup modBus) {
        KEY_CAST = new KeyMapping("key.jjk.cast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
        KEY_MENU = new KeyMapping("key.jjk.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
        KEY_NEXT = new KeyMapping("key.jjk.next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
        KEY_PREV = new KeyMapping("key.jjk.prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
        KEY_LASER = new KeyMapping("key.jjk.laser", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
        KEY_SLAM = new KeyMapping("key.jjk.slam", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            e.register(KEY_CAST); e.register(KEY_MENU); e.register(KEY_NEXT); e.register(KEY_PREV); e.register(KEY_LASER); e.register(KEY_SLAM);
        });
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(e ->
                { e.registerEntityRenderer(MechEntities.DEV_MECH.get(), DevMechRenderer::new);
                  e.registerEntityRenderer(MechEntities.DEV_WALKER.get(), DevWalkerRenderer::new);
                  e.registerEntityRenderer(MechEntities.DEV_TITAN.get(), DevTitanRenderer::new);
                  e.registerEntityRenderer(MechEntities.DEV_MACHINE.get(), DevMachineRenderer::new);
                  e.registerEntityRenderer(MechEntities.BOSS_HURTBOX.get(), net.minecraft.client.renderer.entity.NoopRenderer::new); 
                  e.registerBlockEntityRenderer(com.tubilt.jjk.machine.Machines.BE.get(), ctx -> new MachineRenderer<>()); });
        loadSelected();

        JjkNet.onUnlocks = p -> {
            unlockInfo = true;
            job = p.job().isEmpty() ? null : p.job();
            unlocked = new java.util.HashSet<>(java.util.Arrays.asList(p.ids().split(",")));
            unlocked.remove("");
        };
        JjkNet.onStatus = p -> {
            hasServerMod = true;
            ce = p.ce(); ceMax = p.ceMax(); flags = p.flags(); domain = p.domain(); onCooldowns(p.cooldowns());
        };
        JjkNet.onShake = p -> {
            if (p.power() >= shakePower || shakeTicks <= 0) { shakeTicks = p.ticks(); shakeTotal = Math.max(1, p.ticks()); shakePower = p.power(); }
        };
        JjkNet.onCine = Cinematic::start;
        JjkNet.onCutIn = SkillCutIn::start;
        JjkNet.onCam = QuickCam::start;
        JjkNet.onScreen = ScreenFx::start;
        JjkNet.onMeta = MetaFx::start;
        JjkNet.onBpList = BlueprintScreen::receive;
        JjkNet.onBpDetail = BlueprintScreen::receiveDetail;
        JjkNet.onPlanet = PlanetCine::start;
        PlanetCine.init();
        PlanetRender.init();
        GloveRender.init();
        ScreenFx.init();
        JjkNet.onFlash = p -> { flashColor = p.argb(); flashTicks = p.ticks(); flashTotal = Math.max(1, p.ticks()); };

        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(e -> {
            hasServerMod = false;
            unlockInfo = false; job = null; unlocked = new java.util.HashSet<>();
            if (canSend()) JjkNet.toServer(new Payloads.Hello(1));
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> clientTick(Minecraft.getInstance()));
        // 개발자 탈것 탑승자는 그리지 않음 (투명화)
        net.minecraftforge.client.event.RenderLivingEvent.Pre.BUS.addListener(
                (java.util.function.Predicate<net.minecraftforge.client.event.RenderLivingEvent.Pre>) e -> hideRider(e.getState()));
        AddGuiOverlayLayersEvent.BUS.addListener(e -> {
            e.getLayeredDraw().add(JjkMod.id("hud"), JjkHud::render);
            // 개발자 탈것 탑승 중엔 바닐라 탈것 하트 숨김 (탈것 HUD 내구도 막대로 대체)
            e.getLayeredDraw().addConditionTo(net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.HOTBAR_AND_DECOS,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("vehicle_health"),
                    () -> Minecraft.getInstance().player == null || !(Minecraft.getInstance().player.getVehicle() instanceof com.tubilt.jjk.mech.DevVehicle));
        });
    }

    /** 렌더 상태 위치가 개발자 탈것에 탄 플레이어와 겹치면 숨김. */
    static boolean hideRider(net.minecraft.client.renderer.entity.state.EntityRenderState st) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || st == null) return false;
        for (var p : mc.level.players()) {
            if (!(p.getVehicle() instanceof com.tubilt.jjk.mech.DevVehicle)) continue;
            double dx = st.x - p.getX(), dy = st.y - p.getY(), dz = st.z - p.getZ();
            if (dx * dx + dy * dy + dz * dz < 9.0) return true;
        }
        return false;
    }

    /** 서버에 이 모드가 있을 때만 패킷 전송. */
    static boolean canSend() {
        var c = Minecraft.getInstance().getConnection();
        return c != null && JjkNet.present(c.getConnection());
    }

    private static int helloRetry = 0;

    private static void clientTick(Minecraft mc) {
        LocalPlayer pl = mc.player;
        if (pl == null) return;
        // 접속 직후 Hello 가 누락되면 HUD/보스바 전환이 안 되므로, 상태 패킷을 받을 때까지 2초마다 재전송
        if (!hasServerMod && ++helloRetry >= 40) { helloRetry = 0; if (canSend()) JjkNet.toServer(new Payloads.Hello(1)); }

        // ── 개발자의 기계: Z/C 무기 선택, R 누르고 있기 발사, X 궁극, G 비행, V 포탑
        if (pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMachine && canSend()) {
            while (KEY_NEXT.consumeClick()) JjkNet.toServer(new Payloads.Fire(20));
            while (KEY_PREV.consumeClick()) JjkNet.toServer(new Payloads.Fire(21));
            while (KEY_LASER.consumeClick()) JjkNet.toServer(new Payloads.Fire(1));
            while (KEY_SLAM.consumeClick()) JjkNet.toServer(new Payloads.Fire(2));
            while (KEY_MENU.consumeClick()) JjkNet.toServer(new Payloads.Fire(6));
            while (KEY_CAST.consumeClick()) { }
            boolean down = KEY_CAST.isDown() && mc.screen == null;
            if (down != machineFire) JjkNet.toServer(new Payloads.Fire(down ? 3 : 4));
            machineFire = down;
            gunHeld = false;
        } else if (machineFire) { machineFire = false; }
        boolean holdingGuard = pl.getVehicle() == null && com.tubilt.jjk.JjkItems.is(pl.getMainHandItem(), net.minecraft.world.item.Items.PAPER)
                && "커맨드 수호".equals(pl.getMainHandItem().getHoverName().getString());
        // Z/C: 한 번 누르면 정확히 한 칸 (키를 꾹 누를 때 OS 키 반복으로 두 칸씩 넘어가던 문제 방지)
        boolean nextClick = false, prevClick = false;
        while (KEY_NEXT.consumeClick()) nextClick = true;
        while (KEY_PREV.consumeClick()) prevClick = true;
        boolean nextStep = nextClick && !nextHeld, prevStep = prevClick && !prevHeld;
        nextHeld = KEY_NEXT.isDown(); prevHeld = KEY_PREV.isDown();
        if (nextStep && (flags & 128) == 0) {   // 탑승 중엔 C = 미니건 (누르고 있기)
            if (PhoneHud.holding()) PhoneHud.step(1);   // 핸드폰 앱 넘기기
            else if (holdingGuard) { if (canSend()) { JjkNet.toServer(new Payloads.Fire(20)); if (!pl.isShiftKeyDown()) JjkHud.predictGuard(1); } }   // 커맨드 수호 명령 선택
            else step(1);
        }
        boolean gunNow = (pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMech || pl.getVehicle() instanceof com.tubilt.jjk.mech.DevTitan) && KEY_NEXT.isDown() && mc.screen == null;
        if (gunNow != gunHeld && canSend()) JjkNet.toServer(new Payloads.Fire(gunNow ? 3 : 4));
        gunHeld = gunNow;
        if (prevClick && (flags & 128) != 0) { if (canSend()) JjkNet.toServer(new Payloads.Fire(8)); }   // 탑승 중 Z = 미사일 (연타 허용)
        else if (prevStep) {
            if (PhoneHud.holding()) PhoneHud.step(-1);
            else if (holdingGuard) { if (canSend()) { JjkNet.toServer(new Payloads.Fire(21)); if (!pl.isShiftKeyDown()) JjkHud.predictGuard(-1); } }
            else step(-1);
        }
        while (KEY_LASER.consumeClick()) {
            if ((flags & 128) != 0 && canSend()) JjkNet.toServer(new Payloads.Fire(1));
        }
        // 대지 분쇄: 탈것은 내 클라이언트가 움직이므로 도약 힘은 여기서 줌
        while (KEY_SLAM.consumeClick()) {
            if (pl.getVehicle() instanceof com.tubilt.jjk.mech.DevTitan titan && canSend()) {
                if ((flags & 32768) == 0 && titan.onGround()) { titan.setDeltaMovement(titan.getDeltaMovement().add(0, 2.2, 0)); titan.hasImpulse = true; }
                JjkNet.toServer(new Payloads.Fire(2));
                continue;
            }
            if (pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMech mech && (flags & 2048) != 0 && mech.onGround() && canSend()) {
                net.minecraft.world.phys.Vec3 look = pl.getLookAngle().multiply(1, 0, 1);
                look = look.lengthSqr() < 1e-4 ? net.minecraft.world.phys.Vec3.ZERO : look.normalize();
                mech.setDeltaMovement(look.x * 1.7, 2.0, look.z * 1.7);
                mech.hasImpulse = true;
                slamming = true; slamAirborne = false;
                JjkNet.toServer(new Payloads.Fire(2));
            }
        }
        if (slamming && pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMech mech) {
            if (!mech.onGround()) {
                slamAirborne = true;
                var v = mech.getDeltaMovement();
                if (v.y < 0) mech.setDeltaMovement(v.x, v.y - 0.2, v.z);   // 무겁게 내리꽂힘
            } else if (slamAirborne) slamming = false;
        } else slamming = false;
        // 탈것에 타면 자동 3인칭(뒤), 내리면 원래 시점으로
        boolean onMech = pl.getVehicle() instanceof com.tubilt.jjk.mech.DevVehicle;
        if (onMech && !wasOnMech) { prevCamera = mc.options.getCameraType(); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); }
        else if (!onMech && wasOnMech && prevCamera != null) { mc.options.setCameraType(prevCamera); prevCamera = null; }
        wasOnMech = onMech;
        boolean onTitan = pl.getVehicle() instanceof com.tubilt.jjk.mech.DevTitan;
        while (KEY_MENU.consumeClick()) {
            if (onTitan) { if (canSend()) JjkNet.toServer(new Payloads.Fire(6)); continue; }   // V = 폭탄
            mc.setScreen(new TechniqueScreen());
        }
        while (KEY_CAST.consumeClick()) {
            if (onTitan) { if (canSend()) JjkNet.toServer(new Payloads.Fire(5)); continue; }  // R = 소형 아크레이저
            Techniques.Tech st0 = selectedTech();
            if (st0 != null && canSend())
                JjkNet.toServer(new Payloads.Cast(st0.id()));
        }

        // 화면 흔들림: 지난 틱의 흔들림을 되돌리고 새로 흔든다 (시점이 밀리지 않음)
        if (lastDx != 0 || lastDy != 0) {
            pl.setYRot(pl.getYRot() - lastDx);
            pl.setXRot(pl.getXRot() - lastDy);
            lastDx = lastDy = 0;
        }
        if (shakeTicks > 0) {
            float k = shakePower * shakeTicks / (float) shakeTotal;
            lastDx = (RNG.nextFloat() - 0.5f) * 2f * k;
            lastDy = (RNG.nextFloat() - 0.5f) * 2f * k * 0.6f;
            pl.setYRot(pl.getYRot() + lastDx);
            pl.setXRot(pl.getXRot() + lastDy);
            shakeTicks--;
            if (shakeTicks <= 0) shakePower = 0;
        }
        if (flashTicks > 0) flashTicks--;
    }

    public static Techniques.Tech selectedTech() {
        Techniques.Tech cur = Techniques.ALL.get(Math.max(0, Math.min(selected, Techniques.ALL.size() - 1)));
        List<Techniques.Tech> v = visible();
        if (v.isEmpty()) return null;
        return v.contains(cur) ? cur : v.get(0);
    }

    public static float cooldownOf(Techniques.Tech t) {
        if (t == null) return 0;
        if (t.cdIndex() < 0 || t.cdIndex() >= cooldowns.size()) return 0;
        return cd(t.cdIndex());
    }

    // ── 쿨타임 부드럽게: 패킷 사이를 실제 시간으로 예측해서 매 프레임 줄어들게 (서버 렉·패킷 간격에도 끊김 없음)
    private static int[] cdPrev = new int[0], cdCur = new int[0];
    private static long[] cdAt = new long[0];
    private static float[] cdRate = new float[0];   // 틱/ms

    private static void onCooldowns(List<Integer> v) {
        long now = System.currentTimeMillis();
        int n = v.size();
        if (cdCur.length != n) { cdPrev = new int[n]; cdCur = new int[n]; cdAt = new long[n]; cdRate = new float[n]; java.util.Arrays.fill(cdRate, 0.02f); }
        for (int i = 0; i < n; i++) {
            int x = v.get(i);
            if (x != cdCur[i]) {
                if (x < cdCur[i] && x > 0 && cdAt[i] > 0) {
                    long dt = Math.max(1, now - cdAt[i]);
                    float r = (cdCur[i] - x) / (float) dt;
                    if (r < 0.2f) cdRate[i] = cdRate[i] * 0.6f + r * 0.4f;   // 큰 점프(소모·리셋)는 무시
                }
                cdPrev[i] = cdCur[i]; cdCur[i] = x; cdAt[i] = now;
            }
        }
        cooldowns = v;
    }

    /** i 번 값을 매 프레임 예측한 실수 (틱). 줄어드는 중인 값만 예측, 나머지는 그대로. */
    public static float cd(int i) {
        if (i < 0 || i >= cdCur.length) return i >= 0 && i < cooldowns.size() ? cooldowns.get(i) : 0;
        int c = cdCur[i];
        if (c <= 0 || cdPrev[i] <= c) return c;   // 감소 중이 아님 (막 시작 → 다음 패킷부터 예측)
        long el = Math.min(System.currentTimeMillis() - cdAt[i], 400);   // 서버가 멈추면 최대 0.4초만 앞서감
        return Math.max(Math.min(c, 0.05f), c - cdRate[i] * el);
    }

    private static Path cfg() { return FMLPaths.CONFIGDIR.get().resolve("jjk_selected.txt"); }

    static void saveSelected() {
        try { Files.writeString(cfg(), Techniques.ALL.get(selected).id()); } catch (Exception ignored) { }
    }

    private static void loadSelected() {
        try {
            String id = Files.readString(cfg()).trim();
            for (int i = 0; i < Techniques.ALL.size(); i++) if (Techniques.ALL.get(i).id().equals(id)) selected = i;
        } catch (Exception ignored) { }
    }
}
