package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 레전드 소드 = 레전드 + 「어 형이야~」 검.
 *  · 선택받지 못한 자가 손에 넣으면(제작·줍기·크리에이티브 탭 모두) 즉시 그 자리 땅에 박힘.
 *  · 박힌 검 옆에서 2초간 웅크리면 뽑기 시도 → 선택받은 자(태그 jjk.chosen)만 뽑을 수 있음.
 *  · 「선택의 빛」(크랄루 격파 드랍) 우클릭 → 하늘에서 빛이 내려와 선택받음.
 *  우클릭 「레전드 슬래시」(4초): 앞으로 거대한 빛의 참격.  웅크리고 우클릭 「전설의 심판」(30초): 주변에 빛기둥 낙하.
 *  게임 접속 시 인트로 「Dan's game」.
 */
public final class LegendSword {
    private LegendSword() {}

    static final String CHOSEN = "jjk.chosen", PLANTED = "jjk.legend_planted", UPRIGHT = "jjk.legend_up";
    /** 칼끝이 아래로 (아이템 디스플레이는 Y축 180° 뒤집혀 칼끝이 왼쪽 위 → Z축 +135°). */
    static final String XF = "{left_rotation:[0f,0f,0.9239f,0.3827f],right_rotation:[0f,0f,0f,1f],translation:[0f,0.62f,0f],scale:[1.7f,1.7f,1.7f]}";
    static final int GOLD = 0xFFE27A, WHITE = 0xFFFFF0;
    static final Map<UUID, long[]> CD = new HashMap<>();
    static final Map<UUID, Integer> PULL = new HashMap<>();
    static final Map<UUID, Long> JOINED = new HashMap<>();
    static final Set<UUID> ONLINE = new HashSet<>();

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.LEGEND_SWORD.get()); }
    public static boolean isLight(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.CHOSEN_LIGHT.get()); }
    static boolean chosen(ServerPlayer p) { return p.getTags().contains(CHOSEN); }
    static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }

    // ═════════ 선택의 빛
    public static void useLight(ServerPlayer p, ServerLevel l, ItemStack st) {
        if (chosen(p)) { p.displayClientMessage(Component.literal("§e이미 선택받은 자다"), true); return; }
        if (!p.getAbilities().instabuild) st.shrink(1);
        Vec3 c = p.position();
        Holo.pillar(l, "jjk.legend", c, 40, 1.6, GOLD, 20, 30, 20);
        Holo.pillar(l, "jjk.legend", c, 40, 0.5, WHITE, 20, 30, 20);
        Holo.ring(l, "jjk.legend", c.add(0, 0.1, 0), 0.5, 4, 32, 0.4, 0.05, GOLD, 10, 40, 20, Math.PI / 2, 2, true);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.8f);
        BossSfx.play(l, c, "choir", 3f, 1.0f);
        JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFF6D0, 3000, 0.6f));
        p.addTag(CHOSEN);
        for (ServerPlayer o : l.players()) o.sendSystemMessage(Component.literal("§6§l✦ " + p.getName().getString() + "§e 님이 전설의 검에게 선택받았다"));
        JjkNet.toPlayer(p, new Payloads.CutIn("선택받은 자", "이제 레전드 소드를 뽑을 수 있다", GOLD, 1));
        l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 80, 1.2, 2, 1.2, 0.08);
    }

    // ═════════ 땅에 박기 / 뽑기
    static void plant(ServerPlayer p, ServerLevel l, String why) {
        Vec3 f = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
        int x = (int) Math.floor(f.x), z = (int) Math.floor(f.z);
        int y = (int) Math.floor(p.getY());
        for (int k = 0; k < 6 && l.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).isAir(); k++) y--;
        double px = x + 0.5, py = y, pz = z + 0.5;
        // 칼끝이 아래 (Z축 -135°), 아랫부분은 땅속
        MechManager.cmd(l.getServer(), l, String.format(Locale.ROOT,
                "summon item_display %.2f %.2f %.2f {Tags:[\"%s\",\"" + UPRIGHT + "\"],item:{id:\"jjk:legend_sword\",count:1},item_display:\"none\",brightness:{sky:15,block:15},view_range:3f,"
                        + "transformation:" + XF + "}",
                px, py, pz, PLANTED));
        Vec3 c = new Vec3(px, py, pz);
        l.playSound(null, px, py, pz, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.4f, 0.6f);
        l.playSound(null, px, py, pz, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 1.4f);
        Holo.dustRing(l, "jjk.legend", c, 2, 16, 18);
        Holo.flare(l, "jjk.legend", c.add(0, 1, 0), 2.5, GOLD, 10);
        p.displayClientMessage(Component.literal(why), true);
        p.sendSystemMessage(Component.literal("§6레전드 소드§7가 땅에 박혔다. §8(선택받은 자만 뽑을 수 있다 — 옆에서 웅크려 뽑기)"));
    }
    static void pull(ServerPlayer p, ServerLevel l, Display.ItemDisplay d) {
        Vec3 c = d.position();
        d.discard();
        ItemStack st = new ItemStack(com.tubilt.jjk.JjkItems.LEGEND_SWORD.get());
        if (!p.getInventory().add(st)) p.drop(st, false);
        Holo.pillar(l, "jjk.legend", c, 30, 1.2, GOLD, 4, 14, 14);
        Holo.banner(l, "jjk.legend", c.add(0, 3, 0), banner("LEGEND", GOLD), 1.6, 40);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 1f);
        BossSfx.play(l, c, "choir", 2.5f, 1.1f);
        JjkNet.toPlayer(p, new Payloads.CutIn("LEGEND SWORD", "전설의 검을 뽑았다", GOLD, 1));
        for (ServerPlayer o : l.players()) o.sendSystemMessage(Component.literal("§6§l⚔ " + p.getName().getString() + "§e 님이 레전드 소드를 뽑았다!"));
    }
    static void reject(ServerPlayer p, ServerLevel l, Vec3 c) {
        Vec3 d = p.position().subtract(c).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
        d = d.normalize().scale(1.4);
        p.setDeltaMovement(d.x, 0.5, d.z); p.hurtMarked = true;
        Holo.flare(l, "jjk.legend", c.add(0, 1, 0), 2, WHITE, 6);
        BossSfx.play(l, c, "block", 1.5f, 0.6f);
        p.displayClientMessage(Component.literal("§c검이 꿈쩍도 하지 않는다… §7선택받지 못한 자다"), true);
    }

    // ═════════ 능력
    public static void use(ServerPlayer p, ServerLevel l) {
        if (!chosen(p)) return;
        long now = l.getGameTime();
        long[] cd = CD.computeIfAbsent(p.getUUID(), k -> new long[2]);
        if (p.isShiftKeyDown()) {
            if (now < cd[1]) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7전설의 심판 %.1f초", (cd[1] - now) / 20.0)), true); return; }
            cd[1] = now + 600;
            judgement(p, l);
        } else {
            if (now < cd[0]) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7레전드 슬래시 %.1f초", (cd[0] - now) / 20.0)), true); return; }
            cd[0] = now + 80;
            slash(p, l);
        }
    }
    static void hurt(ServerLevel l, ServerPlayer p, LivingEntity e, float dmg) {
        if (e == null || e == p || !e.isAlive()) return;
        e.invulnerableTime = 0;
        e.hurtServer(l, l.damageSources().playerAttack(p), dmg);
    }
    /** 레전드 슬래시: 앞으로 24칸 빛의 참격 파동. */
    static void slash(ServerPlayer p, ServerLevel l) {
        Vec3 eye = p.getEyePosition(), dir = p.getLookAngle().normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        BossSfx.play(l, eye, "slash", 2.5f, 0.8f);
        l.playSound(null, eye.x, eye.y, eye.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.6f, 1.2f);
        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 1; i <= 12; i++) {
            Vec3 c = eye.add(dir.scale(i * 2));
            double w = 2.5 + i * 0.18;
            Holo.beamGlow(l, "jjk.legend", c.add(side.scale(-w)), c.add(side.scale(w)), 0.5, GOLD, 0, 2 + i / 3, 4);
            if (i % 2 == 0) Holo.beamGlow(l, "jjk.legend", c.add(side.scale(-w * 0.8)), c.add(side.scale(w * 0.8)), 0.18, WHITE, 0, 2 + i / 3, 3);
            for (LivingEntity e : Koming.foes(l, p, c, w)) if (hit.add(e)) {
                hurt(l, p, e, 1_000_000f);
                l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
            }
        }
    }
    /** 전설의 심판: 반경 16 적들 위로 빛기둥이 꽂힘. */
    static void judgement(ServerPlayer p, ServerLevel l) {
        Vec3 c = p.position();
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        Holo.banner(l, "jjk.legend", c.add(0, 3, 0), banner("전설의 심판", GOLD), 1.4, 30);
        Holo.ring(l, "jjk.legend", c.add(0, 0.1, 0), 1, 16, 48, 0.6, 0.05, GOLD, 12, 10, 10, Math.PI / 2, 1, true);
        BossSfx.play(l, c, "choir", 3f, 1.2f);
        int n = 0;
        for (LivingEntity e : Koming.foes(l, p, c, 16)) {
            if (n++ >= 24) break;
            Vec3 q = e.position();
            Holo.pillar(l, "jjk.legend", q, 30, 1.0, GOLD, 2, 6, 10);
            Holo.flare(l, "jjk.legend", q.add(0, 1, 0), 2, WHITE, 8);
            hurt(l, p, e, 2_000_000f);
        }
        l.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2f, 1.4f);
    }

    // ═════════ 틱
    static void tick(MinecraftServer server) {
        intro(server);
        long gt = server.overworld().getGameTime();
        if (gt % 4 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel l) || p.isSpectator()) continue;
            // 선택받지 못한 자 → 검을 가지고 있으면 땅에 박힘
            if (!chosen(p)) {
                boolean found = false;
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) if (is(inv.getItem(i))) { inv.setItem(i, ItemStack.EMPTY); found = true; }
                ItemStack car = p.containerMenu.getCarried();
                if (is(car)) { p.containerMenu.setCarried(ItemStack.EMPTY); found = true; }
                if (found) { p.containerMenu.broadcastChanges(); plant(p, l, "§c레전드 소드가 손을 거부하고 땅에 박혔다"); }
            }
            // 박힌 검 근처 웅크리기 → 뽑기 시도
            Display.ItemDisplay near = null;
            for (Display.ItemDisplay d : l.getEntitiesOfClass(Display.ItemDisplay.class, p.getBoundingBox().inflate(2.5), d -> d.getTags().contains(PLANTED))) { near = d; break; }
            if (near != null && !near.getTags().contains(UPRIGHT)) {   // 예전 버전에서 가로로 박힌 검 바로 세우기
                near.addTag(UPRIGHT);
                MechManager.cmd(server, l, "data merge entity " + near.getStringUUID() + " {transformation:" + XF + "}");
            }
            if (near != null && gt % 20 == 0) l.sendParticles(ParticleTypes.END_ROD, near.getX(), near.getY() + 1.4, near.getZ(), 3, 0.2, 0.6, 0.2, 0.02);
            if (near != null && p.isShiftKeyDown()) {
                int k = PULL.merge(p.getUUID(), 4, Integer::sum);
                p.displayClientMessage(Component.literal("§6검을 뽑는 중 " + "▮".repeat(Math.min(10, k / 4)) + "§8" + "▮".repeat(Math.max(0, 10 - k / 4))), true);
                if (k % 12 == 0) l.playSound(null, near.getX(), near.getY(), near.getZ(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1f, 0.6f);
                if (k >= 40) {
                    PULL.remove(p.getUUID());
                    if (chosen(p)) pull(p, l, near); else reject(p, l, near.position());
                }
            } else PULL.remove(p.getUUID());
        }
        // 주변에 아무도 없어도 박힌 검은 반짝임 (플레이어 근처만 처리하면 충분)
    }

    /** 접속 2초 뒤 인트로 「Dan's game」. */
    static void intro(MinecraftServer server) {
        long gt = server.overworld().getGameTime();
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            now.add(p.getUUID());
            if (ONLINE.add(p.getUUID())) JOINED.put(p.getUUID(), gt + 40);
        }
        ONLINE.retainAll(now);
        for (Iterator<Map.Entry<UUID, Long>> it = JOINED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            if (en.getValue() > gt) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !(p.level() instanceof ServerLevel l)) continue;
            String n = p.getName().getString();
            JjkNet.toPlayer(p, new Payloads.Screen(5, 0x000000, 4200, 0.92f));
            MechManager.cmd(server, l, "title " + n + " times 20 70 30");
            MechManager.cmd(server, l, "title " + n + " subtitle {text:\"— welcome —\",color:\"gray\",italic:true}");
            MechManager.cmd(server, l, "title " + n + " title {text:\"Dan's game\",color:\"gold\",bold:true}");
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1f, 1.2f);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.6f, 0.8f);
        }
    }
}
