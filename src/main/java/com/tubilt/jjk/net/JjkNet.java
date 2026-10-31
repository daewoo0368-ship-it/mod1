package com.tubilt.jjk.net;

import com.tubilt.jjk.JjkMod;
import com.tubilt.jjk.Techniques;
import com.tubilt.jjk.mech.MechManager;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadProtocol;

import java.util.function.Consumer;

/** 포지 네트워크 채널 (패브릭판 PayloadTypeRegistry/ServerPlayNetworking 대체). */
public final class JjkNet {
    private JjkNet() {}

    public static Channel<CustomPacketPayload> CHANNEL;

    /** 클라이언트 전용 수신 처리 — JjkClient 가 채운다 (서버에선 무시). */
    public static Consumer<Payloads.Status> onStatus = p -> {};
    public static Consumer<Payloads.Shake> onShake = p -> {};
    public static Consumer<Payloads.Flash> onFlash = p -> {};
    public static Consumer<Payloads.Cine> onCine = p -> {};
    public static Consumer<Payloads.CutIn> onCutIn = p -> {};
    public static Consumer<Payloads.Cam> onCam = p -> {};
    public static Consumer<Payloads.Screen> onScreen = p -> {};
    public static Consumer<Payloads.Planet> onPlanet = p -> {};
    public static Consumer<Payloads.Meta> onMeta = p -> {};
    public static Consumer<Payloads.Unlocks> onUnlocks = p -> {};
    public static Consumer<Payloads.BpList> onBpList = p -> {};
    public static Consumer<Payloads.BpDetail> onBpDetail = p -> {};

    public static void init() {
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> proto =
                ChannelBuilder.named(JjkMod.id("main")).networkProtocolVersion(2).optional().payloadChannel().play();

        proto.serverbound()
                .add(Payloads.BpReq.TYPE, Payloads.BpReq.CODEC, (p, ctx) -> server(ctx, sp -> com.tubilt.jjk.blueprint.StructureSpawner.handle(sp, p.op(), p.id(), p.text())))
                .add(Payloads.Hello.TYPE, Payloads.Hello.CODEC, (p, ctx) -> server(ctx, sp -> sp.addTag("gojo.modhud")))
                .add(Payloads.Cast.TYPE, Payloads.Cast.CODEC, (p, ctx) -> server(ctx, sp -> {
                    Techniques.Tech t = Techniques.byId(p.tech());
                    if (t == null) return;
                    if (!com.tubilt.jjk.mech.Abilities.unlocked(sp, t)) { sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c잠긴 능력 §7— 「" + t.name() + "」 아이템을 우클릭해 해금"), true); return; }
                    if (t.function().startsWith("java:jacob/")) { com.tubilt.jjk.mech.JacobLadder.cast(sp, t.function().substring("java:jacob/".length())); return; }
                    if (t.function().startsWith("java:rct/")) { com.tubilt.jjk.mech.ReverseTech.cast(sp, t.function().substring("java:rct/".length())); return; }
                    if (t.function().startsWith("java:koming/")) { com.tubilt.jjk.mech.Koming.cast(sp, t.function().substring("java:koming/".length())); return; }
                    if (t.function().startsWith("java:chars/")) { com.tubilt.jjk.mech.Chars.cast(sp, t.function().substring("java:chars/".length())); return; }
                    if (t.function().startsWith("java:blood/")) { com.tubilt.jjk.mech.BloodManip.cast(sp, t.function().substring("java:blood/".length())); return; }
                    if (t.function().startsWith("java:proj/")) { com.tubilt.jjk.mech.Projection.cast(sp, t.function().substring("java:proj/".length())); return; }
                    if (sp.getTags().contains("jjk.hunsu")) { sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"), true); return; }
                    JjkMod.runFunction(sp.level().getServer(), sp, t.function());
                }))
                .add(Payloads.Fire.TYPE, Payloads.Fire.CODEC, (p, ctx) -> server(ctx, sp -> { if (p.n() >= 100 && p.n() < 100 + com.tubilt.jjk.mech.DevPhone.APPS.length) { com.tubilt.jjk.mech.DevPhone.select(sp, p.n() - 100); return; } if (sp.getVehicle() instanceof com.tubilt.jjk.mech.DevMachine) { switch (p.n()) { case 20 -> com.tubilt.jjk.mech.Machine.cycle(sp, 1); case 21 -> com.tubilt.jjk.mech.Machine.cycle(sp, -1); case 3 -> com.tubilt.jjk.mech.Machine.trigger(sp, true); case 4 -> com.tubilt.jjk.mech.Machine.trigger(sp, false); case 1 -> com.tubilt.jjk.mech.Machine.ult(sp); case 2 -> com.tubilt.jjk.mech.Machine.flight(sp); case 6 -> com.tubilt.jjk.mech.Machine.turret(sp); default -> {} } return; } if (p.n() == 20 || p.n() == 21) { com.tubilt.jjk.mech.CommandGuard.cycle(sp, p.n() == 20 ? 1 : -1); return; } if (sp.getVehicle() instanceof com.tubilt.jjk.mech.DevTitan) { switch (p.n()) { case 1 -> com.tubilt.jjk.mech.Titan.arc(sp); case 2 -> com.tubilt.jjk.mech.Titan.flight(sp); case 3 -> com.tubilt.jjk.mech.Titan.flame(sp, true); case 4 -> com.tubilt.jjk.mech.Titan.flame(sp, false); case 5 -> com.tubilt.jjk.mech.Titan.smallArc(sp); case 6 -> com.tubilt.jjk.mech.Titan.bombs(sp); case 8 -> com.tubilt.jjk.mech.Titan.missiles(sp); default -> {} } return; } if (sp.getVehicle() instanceof com.tubilt.jjk.mech.DevWalker) { if (p.n() == 1) com.tubilt.jjk.mech.Walker.heavy(sp); else if (p.n() == 8) com.tubilt.jjk.mech.Walker.cannon(sp); return; } if (p.n() == 1) com.tubilt.jjk.mech.Laser.start(sp); else if (p.n() == 2) com.tubilt.jjk.mech.Slam.start(sp); else if (p.n() == 3 || p.n() == 4) com.tubilt.jjk.mech.Minigun.trigger(sp, p.n() == 3); else MechManager.fire(sp); }));

        proto.clientbound()
                .add(Payloads.Status.TYPE, Payloads.Status.CODEC, (p, ctx) -> client(ctx, () -> onStatus.accept(p)))
                .add(Payloads.Shake.TYPE, Payloads.Shake.CODEC, (p, ctx) -> client(ctx, () -> onShake.accept(p)))
                .add(Payloads.Flash.TYPE, Payloads.Flash.CODEC, (p, ctx) -> client(ctx, () -> onFlash.accept(p)))
                .add(Payloads.Cine.TYPE, Payloads.Cine.CODEC, (p, ctx) -> client(ctx, () -> onCine.accept(p)))
                .add(Payloads.CutIn.TYPE, Payloads.CutIn.CODEC, (p, ctx) -> client(ctx, () -> onCutIn.accept(p)))
                .add(Payloads.Cam.TYPE, Payloads.Cam.CODEC, (p, ctx) -> client(ctx, () -> onCam.accept(p)))
                .add(Payloads.Screen.TYPE, Payloads.Screen.CODEC, (p, ctx) -> client(ctx, () -> onScreen.accept(p)))
                .add(Payloads.Planet.TYPE, Payloads.Planet.CODEC, (p, ctx) -> client(ctx, () -> onPlanet.accept(p)))
                .add(Payloads.Meta.TYPE, Payloads.Meta.CODEC, (p, ctx) -> client(ctx, () -> onMeta.accept(p)))
                .add(Payloads.Unlocks.TYPE, Payloads.Unlocks.CODEC, (p, ctx) -> client(ctx, () -> onUnlocks.accept(p)))
                .add(Payloads.BpList.TYPE, Payloads.BpList.CODEC, (p, ctx) -> client(ctx, () -> onBpList.accept(p)))
                .add(Payloads.BpDetail.TYPE, Payloads.BpDetail.CODEC, (p, ctx) -> client(ctx, () -> onBpDetail.accept(p)));

        CHANNEL = proto.bidirectional().build();
    }

    private static void server(CustomPayloadEvent.Context ctx, Consumer<ServerPlayer> body) {
        ServerPlayer sp = ctx.getSender();
        if (sp != null) ctx.enqueueWork(() -> body.accept(sp));
        ctx.setPacketHandled(true);
    }

    private static void client(CustomPayloadEvent.Context ctx, Runnable body) {
        ctx.enqueueWork(body);
        ctx.setPacketHandled(true);
    }

    public static void toPlayer(ServerPlayer p, CustomPacketPayload payload) {
        if (p == null || p.connection == null) return;   // 보스 꼭두각시(연결 없음)
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(p));
    }

    public static void toServer(CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }

    /** 상대(서버)에 이 모드가 있는지. */
    public static boolean present(Connection c) {
        try { return c != null && CHANNEL.isRemotePresent(c); } catch (Throwable t) { return false; }
    }
}
