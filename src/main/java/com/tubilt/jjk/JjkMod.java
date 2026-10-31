package com.tubilt.jjk;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mod(JjkMod.MOD_ID)
public class JjkMod {
    public static final String MOD_ID = "jjk";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public JjkMod(FMLJavaModLoadingContext ctx) {
        BusGroup modBus = ctx.getModBusGroup();
        JjkNet.init();
        com.tubilt.jjk.mech.MechEntities.init(modBus);
        JjkItems.init(modBus);
        JjkTabs.init(modBus);
        com.tubilt.jjk.machine.Machines.init(modBus);
        com.tubilt.jjk.mech.MechManager.init();
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) tick(server);
        });
        if (FMLEnvironment.dist.isClient()) com.tubilt.jjk.client.JjkClient.init(modBus);
        LOG.info("[JJK] 주술회전 술식 모드(포지) 로드 완료");
    }

    /** 플레이어 자신으로서(as/at @s) 데이터팩 함수를 실행. */
    public static void runFunction(MinecraftServer server, ServerPlayer p, String function) {
        try {
            CommandSourceStack src = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, "function " + function);
        } catch (Exception e) {
            LOG.warn("[JJK] 함수 실행 실패: {}", function, e);
        }
    }

    private static int tickCount = 0;
    private static final java.util.Map<java.util.UUID, String> UNL_SIG = new java.util.HashMap<>();

    private static void tick(MinecraftServer server) {
        tickCount++;
        Scoreboard sb = server.getScoreboard();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Set<String> tags = p.getTags();

            // ── 데이터팩이 붙인 연출 태그 → 클라이언트 효과
            if (tags.contains("gojo.fxshake")) { JjkNet.toPlayer(p, new Payloads.Shake(18, 4.0f)); p.removeTag("gojo.fxshake"); }
            if (tags.contains("gojo.fxshake_s")) { JjkNet.toPlayer(p, new Payloads.Shake(8, 1.6f)); p.removeTag("gojo.fxshake_s"); }
            if (tags.contains("gojo.fxflash_w")) { JjkNet.toPlayer(p, new Payloads.Flash(0xD0FFFFFF, 10)); p.removeTag("gojo.fxflash_w"); }
            if (tags.contains("gojo.fxflash_p")) { JjkNet.toPlayer(p, new Payloads.Flash(0xC0A040FF, 14)); p.removeTag("gojo.fxflash_p"); }
            if (tags.contains("gojo.fxflash_r")) { JjkNet.toPlayer(p, new Payloads.Flash(0xB0FF2020, 10)); p.removeTag("gojo.fxflash_r"); }
            if (tags.contains("gojo.fxflash_o")) { JjkNet.toPlayer(p, new Payloads.Flash(0xB0FF8A20, 12)); p.removeTag("gojo.fxflash_o"); }
            if (tags.contains("gojo.fxflash_k")) { JjkNet.toPlayer(p, new Payloads.Flash(0xE0000000, 8)); p.removeTag("gojo.fxflash_k"); }

            if (tickCount % 5 == 0) {   // 직업·해금 목록: 바뀔 때만 전송 (modhud 태그와 무관)
                String uj = com.tubilt.jjk.mech.Abilities.job(p), ui = com.tubilt.jjk.mech.Abilities.unlockedIds(p);
                String sig = (uj == null ? "" : uj) + "|" + ui;
                if (!sig.equals(UNL_SIG.get(p.getUUID()))) { UNL_SIG.put(p.getUUID(), sig); JjkNet.toPlayer(p, new Payloads.Unlocks(uj == null ? "" : uj, ui)); }
            }

            if (tickCount % 2 != 0 || !tags.contains("gojo.modhud")) continue;

            // ── 상태 패킷
            int ce = score(sb, p, "gojo.ce"), max = score(sb, p, "gojo.cemax");
            int flags = 0;
            if (tags.contains("gojo.six")) flags |= 1;
            if (tags.contains("gojo.inf")) flags |= 2;
            if (tags.contains("gojo.hr")) flags |= 4;
            if (tags.contains("gojo.jackpot")) flags |= 8;
            if (tags.contains("gojo.sealed")) flags |= 16;
            if (tags.contains("gojo.confisc")) flags |= 32;
            if (tags.contains("gojo.cs2v") || tags.contains("gojo.csv")) flags |= 64;
            if (com.tubilt.jjk.mech.MechManager.riding(p)) {
                flags |= 128;
                if (com.tubilt.jjk.mech.MechManager.missileCd(p) <= 0) flags |= 256;
                if (com.tubilt.jjk.mech.MechManager.laserReady(p)) flags |= 512;
                if (com.tubilt.jjk.mech.MechManager.laserActive(p)) flags |= 1024;
                if (com.tubilt.jjk.mech.MechManager.slamReady(p)) flags |= 2048;
                if (com.tubilt.jjk.mech.MechManager.ridingWalker(p)) flags |= 4096;
                int tf = com.tubilt.jjk.mech.MechManager.titanFlags(p);
                if (tf != 0) flags = (flags & ~(256 | 512 | 1024 | 2048)) | tf;
            }
            List<Integer> cds = new ArrayList<>(Techniques.STATUS_CD.size());
            for (String o : Techniques.STATUS_CD) cds.add(score(sb, p, o));
            List<Integer> mh = com.tubilt.jjk.mech.Machine.hud(p);
            if (mh != null) { flags |= 131072; cds = mh; }
            else {
                List<Integer> gh = com.tubilt.jjk.mech.CommandGuard.hud(p);
                if (gh != null) { flags |= 262144; cds = gh; }
            }
            JjkNet.toPlayer(p, new Payloads.Status(ce, max <= 0 ? 1000 : max, flags, domainAround(p), cds));
        }
    }

    private static int score(Scoreboard sb, ServerPlayer p, String objective) {
        Objective o = sb.getObjective(objective);
        if (o == null) return 0;
        ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(p, o);
        return info == null ? 0 : info.value();
    }

    /** 플레이어가 서 있는 영역 종류. */
    private static int domainAround(ServerPlayer p) {
        List<Marker> markers = p.level().getEntitiesOfClass(Marker.class, p.getBoundingBox().inflate(26.0));
        int found = 0;
        for (Marker m : markers) {
            Set<String> t = m.getTags();
            if (t.contains("gojo.closing")) continue;
            double d = m.distanceTo(p);
            if (t.contains("gojo.dev") && d <= 26) return 8;
            if (t.contains("gojo.domain") && d <= 14) return 1;
            if (t.contains("gojo.jp") && d <= 14) return 3;
            if (t.contains("gojo.jg") && d <= 14) return 4;
            if (t.contains("gojo.hg") && d <= 14) return 5;
            if (t.contains("gojo.tk") && d <= 14) return 6;
            if (t.contains("gojo.hk") && d <= 14) return 7;
            if (t.contains("gojo.ms") && d <= 24) found = 2;
        }
        return found;
    }
}
