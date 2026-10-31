package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 황금 걸쇠 v2 — 커맨드 수호의 「잠긴 명령」을 연다.
 *  1. 수호 위에 걸쇠 채운 명령 블록 상자 → 걸쇠 3개가 차례로 튕겨 나감
 *  2. 상자가 터지며 명령 카드 3장이 펼쳐짐 (한 장은 봉인된 「???」 — 등급만 보임, 대신 한 등급 위)
 *  3. 카드를 바라보면 강조 → 웅크리면 선택 (5초 지나면 보고 있는 카드)
 *  4. 아이템이 나오면 「더블 오어 낫띵」: 3초 안에 다시 웅크리면 동전 던지기 → 2배 or 꽝
 *  5. 천장: 10번째 개봉엔 전설 카드가 반드시 섞임
 * 결과는 아이템만이 아니라 세계 규칙을 바꾸는 명령 (슬로모션 /tick rate, 달 중력, 다이아 비, 보물찾기, 미믹, 핵…)
 */
public final class GoldenClasp {
    private GoldenClasp() {}

    public static boolean is(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.GOLDEN_CLASP.get()); }

    record Out(int rarity, String cmd, String what) {}
    static final String[] RARITY = {"일반", "장난", "희귀", "영웅", "★전설★"};
    static final String[] RC = {"§f", "§d", "§b", "§5", "§6"};
    static final int[] RCOL = {0xFFFFFF, 0xFF55FF, 0x55FFFF, 0xB45CFF, 0xFFD84A};
    static final Out[] TABLE = {
            new Out(0, "/give @s diamond 8", "minecraft:diamond 8"),
            new Out(0, "/give @s golden_apple 4", "minecraft:golden_apple 4"),
            new Out(0, "/xp add @s 30 levels", "#xp"),
            new Out(0, "/effect give @s haste 300 2", "#haste"),
            new Out(0, "/give @s experience_bottle 32", "minecraft:experience_bottle 32"),
            new Out(0, "/weather gold_rain", "#goldrain"),
            new Out(1, "/summon creeper ~ ~ ~ {powered:1b,ExplosionRadius:0b}", "#creeper"),
            new Out(1, "/give @s dirt 64", "minecraft:dirt 64"),
            new Out(1, "/tp @s ~ ~40 ~", "#sky"),
            new Out(1, "/debug crash", "#crash"),
            new Out(1, "/summon jjk:mimic", "#mimic"),
            new Out(1, "/attribute @s scale base set 0.25", "#tiny"),
            new Out(1, "/summon tnt ×24", "#tntparty"),
            new Out(2, "/give @s netherite_ingot 3", "minecraft:netherite_ingot 3"),
            new Out(2, "/give @s totem_of_undying 2", "minecraft:totem_of_undying 2"),
            new Out(2, "/give @s elytra", "minecraft:elytra 1"),
            new Out(2, "/give @s enchanted_golden_apple 2", "minecraft:enchanted_golden_apple 2"),
            new Out(2, "/weather diamond_rain", "#diamondrain"),
            new Out(2, "/locate structure jjk:buried_command", "#treasure"),
            new Out(2, "/attribute @s gravity base set 0.016", "#moon"),
            new Out(2, "/summon iron_golem ~ ~ ~ {Tags:[\"ally\"]} ×3", "#ally"),
            new Out(3, "/give @s jjk:dev_lever", "#lever"),
            new Out(3, "/give @s jjk:dev_phone", "#phone"),
            new Out(3, "/summon jjk:archmage", "#mage"),
            new Out(3, "/tick rate 5", "#slowmo"),
            new Out(3, "/kill @e[type=!player,distance=..48]", "#nuke"),
            new Out(4, "/execute as @e[type=command_guard] run rebel", "#rebel"),
            new Out(4, "/give @s * 2147483647", "#jackpot")};
    static final int[] WEIGHT = {40, 18, 24, 12, 6};

    static final List<Opening> ACTIVE = new ArrayList<>();
    static final Map<UUID, Integer> PITY = new HashMap<>();
    static final Random R = new Random();
    static final ResourceLocation TINY = com.tubilt.jjk.JjkMod.id("clasp_tiny"), MOON = com.tubilt.jjk.JjkMod.id("clasp_moon"), MOONF = com.tubilt.jjk.JjkMod.id("clasp_moon_fall");
    static final List<Object[]> TIMERS = new ArrayList<>();   // {끝 틱, Runnable}
    static long slowUntil = 0;

    /** 우클릭. */
    public static void use(ServerPlayer p, ServerLevel l, ItemStack st) {
        CommandGuard.Guard g = CommandGuard.GUARDS.get(p.getUUID());
        if (g == null) { p.displayClientMessage(Component.literal("§6황금 걸쇠 §7— 커맨드 수호를 먼저 소환해야 열 수 있다"), true); return; }
        for (Opening o : ACTIVE) if (o.p == p) return;
        if (!p.isCreative()) st.shrink(1);
        int pity = PITY.getOrDefault(p.getUUID(), 0) + 1;
        PITY.put(p.getUUID(), pity >= 10 ? 0 : pity);
        ACTIVE.add(new Opening(p, l, g.pos.add(0, 3.5, 0), pity >= 10, pity));
    }

    static int rollRarity() {
        int r = R.nextInt(100), acc = 0;
        for (int i = 0; i < WEIGHT.length; i++) { acc += WEIGHT[i]; if (r < acc) return i; }
        return 0;
    }
    static Out of(int rar) {
        List<Out> c = new ArrayList<>();
        for (Out o : TABLE) if (o.rarity == rar) c.add(o);
        return c.get(R.nextInt(c.size()));
    }

    static void tickAll(net.minecraft.server.MinecraftServer server) {
        for (Iterator<Opening> it = ACTIVE.iterator(); it.hasNext(); ) {
            Opening o = it.next();
            try { if (o.tick()) it.remove(); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 걸쇠 오류", e); it.remove(); }
        }
        long now = server.overworld().getGameTime();
        for (Iterator<Object[]> it = TIMERS.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            if ((long) o[0] <= now) { it.remove(); try { ((Runnable) o[1]).run(); } catch (Exception ignored) { } }
        }
        // 슬로모션은 실제 시간 기준으로 풀어줌 (틱이 느려지니까)
        if (slowUntil > 0 && System.currentTimeMillis() > slowUntil) { slowUntil = 0; server.tickRateManager().setTickRate(20f); }
    }
    static void stopAll(net.minecraft.server.MinecraftServer server) {
        ACTIVE.clear();
        for (Object[] o : TIMERS) try { ((Runnable) o[1]).run(); } catch (Exception ignored) { }
        TIMERS.clear();
        if (slowUntil > 0 && server != null) { server.tickRateManager().setTickRate(20f); slowUntil = 0; }
    }
    static void after(ServerLevel l, int ticks, Runnable r) { TIMERS.add(new Object[]{l.getServer().overworld().getGameTime() + ticks, r}); }

    // ═════════ 개봉
    static final class Opening {
        final ServerPlayer p; final ServerLevel l; final Vec3 c; final String tag = "jjk.clasp" + (Holo.uid++);
        final Out[] cards = new Out[3]; final boolean[] sealed = new boolean[3]; final Vec3[] at = new Vec3[3];
        final List<Holo.Elem> clasps = new ArrayList<>(), cardEl = new ArrayList<>();
        int t = 0, look = 1, chosen = -1, chooseAt = 0, dbl = 0; boolean sneakPrev = true; final int pity;
        Opening(ServerPlayer p, ServerLevel l, Vec3 c, boolean ceiling, int pity) {
            this.p = p; this.l = l; this.c = c; this.pity = pity;
            for (int i = 0; i < 3; i++) cards[i] = of(rollRarity());
            int s = R.nextInt(3);
            sealed[s] = true;
            cards[s] = of(Math.min(4, Math.max(cards[s].rarity, rollRarity()) + 1));   // 봉인 카드는 한 등급 위
            if (ceiling) { cards[s] = of(4); }
            int top = 0; for (Out o : cards) top = Math.max(top, o.rarity);
            // 상자 + 황금 띠 + 걸쇠 3개
            Holo.block(l, tag, c, "minecraft:command_block", RCOL[top], Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 34, 0)
                    .key(1, Holo.m(Holo.I(), 2.4, 2.4, 2.4, Vec3.ZERO, true), 6).key(24, Holo.m(Holo.I(), 2.9, 2.9, 2.9, Vec3.ZERO, true), 6).spin(3).wobble(6, 0.6);
            Holo.block(l, tag, c, "minecraft:gold_block", 0xFFD84A, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 30, 0)
                    .key(3, Holo.m(Holo.I(), 2.6, 0.3, 2.6, Vec3.ZERO, true), 5).spin(3);
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                Vec3 off = new Vec3(Math.cos(a) * 1.35, 0, Math.sin(a) * 1.35);
                clasps.add(Holo.block(l, tag, c, "minecraft:gold_block", 0xFFD84A, Holo.m(Holo.I(), 0.01, 0.01, 0.01, off, true), 40, 0)
                        .key(4 + i * 2, Holo.m(Holo.I(), 0.5, 0.8, 0.5, off, true), 4));
            }
            Holo.sfx(l, c, "plate", 2f, 0.8f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.5f, 0.8f);
            if (ceiling) p.sendSystemMessage(Component.literal("§6§l천장 도달! §f전설 카드가 섞였다"));
        }

        boolean tick() {
            t++;
            if (!p.isAlive()) return true;
            for (int i = 0; i < 3; i++) if (t == 12 + i * 5) {   // 걸쇠가 하나씩 튕겨 나감
                double a = i * Math.PI * 2 / 3;
                Vec3 off = new Vec3(Math.cos(a) * 1.35, 0, Math.sin(a) * 1.35);
                clasps.get(i).keyIn(1, Holo.m(Holo.rotZ(1.2), 0.5, 0.8, 0.5, off.scale(3).add(0, -3, 0), true), 8).endIn(10);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 1.5f, 1.2f + i * 0.2f);
                Holo.glow(l, tag, c.add(off), 1.2, 0xFFD84A, 1, 1, 4);
            }
            if (t == 30) spread();
            if (t > 30 && chosen < 0) {
                look = looking();
                boolean sneak = p.isShiftKeyDown();
                int left = Math.max(0, 130 - t);
                if (t % 5 == 0) p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§6⌘ 카드를 바라보고 §e웅크려서 선택 §7(%.0f초) · 천장 %d/10", left / 20.0, pity)), true);
                if ((sneak && !sneakPrev && t > 40) || t >= 130) choose(look);
                sneakPrev = sneak;
                return false;
            }
            sneakPrev = p.isShiftKeyDown() && sneakPrev;
            if (chosen >= 0) {
                int k = t - chooseAt;
                if (k == 16) reveal();
                if (dbl == 1) {   // 더블 오어 낫띵 대기
                    boolean sneak = p.isShiftKeyDown();
                    if (k % 5 == 0) p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§e§l더블 오어 낫띵? §f웅크리면 동전 던지기 §7(%.0f초)", Math.max(0, 76 - k) / 20.0)), true);
                    if (sneak && !sneakPrev) { dbl = 2; coin(); return false; }
                    sneakPrev = sneak;
                    if (k >= 76) { dbl = 0; p.displayClientMessage(Component.literal("§7욕심 안 내기로 했다."), true); return true; }
                    return false;
                }
                if (dbl == 2) return k >= 200;
                return k >= 30;
            }
            return false;
        }

        /** 상자가 터지며 카드 3장 (플레이어 기준 왼쪽·가운데·오른쪽). */
        void spread() {
            Vec3 fwd = p.position().subtract(c).multiply(1, 0, 1).normalize();
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            Holo.flare(l, tag, c, 4, 0xFFD84A, 6);
            Holo.shards(l, tag, c, "minecraft:command_block", 12, 3);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 1.5f, 0.6f);
            Holo.sfx(l, c, "whoosh", 1.5f, 1.4f);
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                at[i] = c.add(right.scale((i - 1) * 3.2)).add(fwd.scale(1.5)).add(0, i == 1 ? 0.6 : 0, 0);
                Out o = cards[i];
                Holo.Elem e = Holo.text(l, tag, c, "{text:\"\"}", "center", 0xE0101018, false, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 400, 1)
                        .key(1 + i * 2, Holo.m(Holo.I(), 1.3, 1.3, 1.3, at[i].subtract(c), false), 6)
                        .text(age -> cardText(ii));
                cardEl.add(e);
                Holo.pillar(l, tag, at[i].add(0, -2, 0), 4, 0.3, RCOL[sealed[i] ? Math.min(4, o.rarity) : o.rarity], 2, 90, 6);
            }
        }
        String cardText(int i) {
            Out o = cards[i];
            boolean sel = chosen < 0 ? look == i : chosen == i;
            boolean show = !sealed[i] || chosen == i && t - chooseAt >= 16;
            String head = (sel ? "▶ " : "  ") + (sealed[i] && !show ? "봉인 · " : "") + RARITY[o.rarity] + (sel ? " ◀" : "  ");
            String body = show ? o.cmd : "???  /???????  ???";
            return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(head + "\\n", RCOL[o.rarity], true) + "," + Holo.tx(body, sel ? 0xFFFFFF : 0x999999, sel) + "]}";
        }
        int looking() {
            Vec3 eye = p.getEyePosition(), lk = p.getLookAngle();
            int best = 1; double bd = -2;
            for (int i = 0; i < 3; i++) { double d = at[i].subtract(eye).normalize().dot(lk); if (d > bd) { bd = d; best = i; } }
            return best;
        }
        void choose(int i) {
            chosen = i; chooseAt = t;
            for (int k = 0; k < 3; k++) {
                if (k == i) { cardEl.get(k).keyIn(1, Holo.m(Holo.I(), 2.0, 2.0, 2.0, at[k].subtract(c).add(0, 1, 0), false), 8); continue; }
                cardEl.get(k).keyIn(1, Holo.m(Holo.I(), 1.3, 0.01, 1.3, at[k].subtract(c).add(0, -1, 0), false), 6).endIn(8);
                Holo.flare(l, tag, at[k], 2, 0xFF5020, 6);
            }
            l.playSound(null, c.x, c.y, c.z, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.5f, 0.7f);
            Holo.sfx(l, at[i], "riser", 1.5f, 1.2f);
        }

        void reveal() {
            Out out = cards[chosen];
            int col = RCOL[out.rarity];
            Vec3 a = at[chosen];
            Holo.pillar(l, tag, a, 30 + out.rarity * 15, 1 + out.rarity * 0.6, col, 2, 8, 10);
            Holo.flare(l, tag, a, 4 + out.rarity * 2, col, 10);
            if (out.rarity >= 3) Holo.shock(l, tag, a.add(0, -2, 0), 10 + out.rarity * 4, col, 12);
            com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.CutIn("황금 걸쇠 — " + RARITY[out.rarity], out.cmd, col, out.rarity >= 3 ? 1 : 0));
            l.playSound(null, a.x, a.y, a.z, out.rarity >= 3 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.5f, 1f);
            if (!out.what.startsWith("#")) { give(out.what); dbl = 1; return; }
            effect(out.what, a);
        }
        void give(String what) { MechManager.cmd(l.getServer(), l, "give " + p.getName().getString() + " " + what); }

        /** 더블 오어 낫띵: 공중에서 도는 금화 → 앞(2배) / 뒤(꽝). */
        void coin() {
            boolean win = R.nextBoolean();
            Vec3 a = p.getEyePosition().add(p.getLookAngle().scale(2.5));
            Holo.block(l, tag, a, "minecraft:gold_block", 0xFFD84A, Holo.m(Holo.I(), 0.8, 0.1, 0.8, Vec3.ZERO, true), 50, 0)
                    .key(1, Holo.m(Holo.rotX(6), 0.8, 0.1, 0.8, new Vec3(0, 2, 0), true), 18).key(20, Holo.m(Holo.rotX(win ? 12.6 : 9.4), 0.8, 0.1, 0.8, Vec3.ZERO, true), 12);
            for (int i = 0; i < 6; i++) final_tick(i * 5, () -> l.playSound(null, a.x, a.y, a.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, 1.6f));
            final_tick(34, () -> {
                if (win) {
                    give(cards[chosen].what);
                    give(cards[chosen].what);
                    p.sendSystemMessage(Component.literal("§e§l앞면! §f2배 당첨 §7— 수호: 「…운 좋네.」"));
                    Holo.flare(l, tag, a, 4, 0xFFD84A, 10);
                    l.playSound(null, a.x, a.y, a.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.4f);
                } else {
                    MechManager.cmd(l.getServer(), l, "clear " + p.getName().getString() + " " + cards[chosen].what);
                    p.sendSystemMessage(Component.literal("§c§l뒷면! §f꽝 §7— 수호: 「ㅋㅋ 내 거.」"));
                    Holo.shards(l, tag, a, "minecraft:gold_block", 10, 3);
                    l.playSound(null, a.x, a.y, a.z, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.5f, 0.8f);
                }
            });
        }
        void final_tick(int dt, Runnable r) { after(l, dt, r); }

        void effect(String what, Vec3 a) {
            String name = p.getName().getString();
            switch (what) {
                case "#xp" -> p.giveExperienceLevels(30);
                case "#haste" -> { p.addEffect(new MobEffectInstance(MobEffects.HASTE, 6000, 2)); p.addEffect(new MobEffectInstance(MobEffects.SPEED, 6000, 1)); }
                case "#goldrain" -> rain(Items.GOLD_INGOT, 40, 0xFFD84A);
                case "#diamondrain" -> rain(Items.DIAMOND, 24, 0x55FFFF);
                case "#creeper" -> {
                    MechManager.cmd(l.getServer(), l, String.format(Locale.ROOT, "summon creeper %.1f %.1f %.1f {powered:1b,ExplosionRadius:0b,Fuse:30,ignited:1b}", p.getX(), p.getY(), p.getZ()));
                    say("펑! …속았지? 폭발 반경 0이야.");
                }
                case "#sky" -> { p.teleportTo(p.getX(), p.getY() + 40, p.getZ()); p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0)); say("경치 좋지?"); }
                case "#crash" -> GuardMeta.send(p, 1, "a golden clasp was opened");
                case "#mimic" -> { say("어… 그 상자 말이야, 사실 살아있어."); Bosses.mimic(p, a.add(0, -2, 0)); }
                case "#tiny" -> {
                    var at = p.getAttribute(Attributes.SCALE);
                    if (at != null) { at.removeModifier(TINY); at.addTransientModifier(new AttributeModifier(TINY, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
                    say("귀엽다. 1분만 그러고 있어.");
                    after(l, 1200, () -> { var x = p.getAttribute(Attributes.SCALE); if (x != null) x.removeModifier(TINY); });
                }
                case "#tntparty" -> tntParty();
                case "#treasure" -> treasure();
                case "#moon" -> {
                    var g = p.getAttribute(Attributes.GRAVITY);
                    if (g != null) { g.removeModifier(MOON); g.addTransientModifier(new AttributeModifier(MOON, -0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
                    var sf = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
                    if (sf != null) { sf.removeModifier(MOONF); sf.addTransientModifier(new AttributeModifier(MOONF, 40, AttributeModifier.Operation.ADD_VALUE)); }
                    p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 2400, 2));
                    say("달 중력 2분. 점프해 봐.");
                    after(l, 2400, () -> { var x = p.getAttribute(Attributes.GRAVITY); if (x != null) x.removeModifier(MOON); var y = p.getAttribute(Attributes.SAFE_FALL_DISTANCE); if (y != null) y.removeModifier(MOONF); });
                }
                case "#ally" -> allies();
                case "#lever" -> giveTab(p, "jjk:dev_lever[");
                case "#phone" -> giveTab(p, "jjk:dev_phone[");
                case "#mage" -> Bosses.archMage(l, p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(10)));
                case "#slowmo" -> {
                    l.getServer().tickRateManager().setTickRate(5f);
                    slowUntil = System.currentTimeMillis() + 10000;
                    p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 2));
                    for (ServerPlayer sp : l.players()) sp.sendSystemMessage(Component.literal("§5/tick rate 5 §7— 세계가 느려졌다 (10초)"));
                }
                case "#nuke" -> nuke();
                case "#rebel" -> rebel();
                case "#jackpot" -> {
                    for (String s : new String[]{"minecraft:netherite_ingot 4", "minecraft:totem_of_undying 2", "minecraft:enchanted_golden_apple 4", "minecraft:diamond_block 8", "jjk:golden_clasp 5"}) give(s);
                    rain(Items.EMERALD, 30, 0x55FF55);
                    for (ServerPlayer sp : l.players()) sp.sendSystemMessage(Component.literal("§6§l★ JACKPOT ★ §f" + name + " 님이 황금 걸쇠 잭팟!"));
                }
                default -> { }
            }
        }
        void say(String s) { p.sendSystemMessage(Component.literal("§d<커맨드 수호> §f" + s)); }

        /** 하늘에서 아이템 비. */
        void rain(net.minecraft.world.item.Item item, int n, int rgb) {
            Vec3 o = p.position();
            Holo.circle(l, tag, o.add(0, 12, 0), 6, rgb, 60, 0);
            for (int i = 0; i < n; i++) {
                final int ii = i;
                after(l, 4 + i * 2, () -> {
                    Vec3 q = o.add((R.nextDouble() - 0.5) * 10, 14, (R.nextDouble() - 0.5) * 10);
                    ItemEntity e = new ItemEntity(l, q.x, q.y, q.z, new ItemStack(item));
                    e.setDeltaMovement(0, -0.3, 0);
                    l.addFreshEntity(e);
                    if (ii % 4 == 0) l.playSound(null, q.x, q.y, q.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
                });
            }
        }
        /** TNT 24개가 주위에 깔리고 → 터지면 폭죽 (무해). */
        void tntParty() {
            Vec3 o = p.position();
            say("…도망가지 마. 장난이야. 아마도.");
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24, r = 4 + (i % 3) * 1.5;
                Vec3 q = o.add(Math.cos(a) * r, 0.5, Math.sin(a) * r);
                Holo.block(l, tag, q, "minecraft:tnt", 0xFFFFFF, Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), 62, 0).wobble(10, 2);
            }
            l.playSound(null, o.x, o.y, o.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 2f, 1f);
            after(l, 60, () -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    MechManager.cmd(l.getServer(), l, String.format(Locale.ROOT, "summon firework_rocket %.1f %.1f %.1f {LifeTime:15,FireworksItem:{id:\"minecraft:firework_rocket\",count:1,components:{\"minecraft:fireworks\":{explosions:[{shape:\"large_ball\",colors:[I;16766794,16733695,5636095],has_trail:1b,has_twinkle:1b}]}}}}",
                            o.x + Math.cos(a) * 5, o.y + 1, o.z + Math.sin(a) * 5));
                }
                say("🎉 생일 축하… 아니 그냥 축하해.");
            });
        }
        /** 보물찾기: 30~40블록 떨어진 땅속에 상자 + 하늘까지 닿는 금빛 표식. */
        void treasure() {
            double a = R.nextDouble() * Math.PI * 2, d = 30 + R.nextDouble() * 10;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            int y = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 3;
            BlockPos pos = new BlockPos(x, Math.max(l.getMinY() + 2, y), z);
            l.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            if (l.getBlockEntity(pos) instanceof ChestBlockEntity ch) {
                ItemStack[] loot = {new ItemStack(Items.DIAMOND, 12), new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                        new ItemStack(Items.EMERALD, 24), new ItemStack(Items.GOLDEN_CARROT, 16), new ItemStack(com.tubilt.jjk.JjkItems.GOLDEN_CLASP.get(), 2)};
                for (int i = 0; i < loot.length; i++) ch.setItem(i * 4 + 1, loot[i]);
            }
            Vec3 top = new Vec3(x + 0.5, y + 3, z + 0.5);
            Holo.pillar(l, tag + "t", top, 120, 1.2, 0xFFD84A, 4, 2400, 20);
            Holo.circle(l, tag + "t", top.add(0, 0.1, 0), 3, 0xFFD84A, 2400, 0);
            String dir = String.format(Locale.ROOT, "%d %d %d", x, y, z);
            say("땅속에 명령 블록 보물을 묻어놨어. 금빛 기둥 아래 3칸. §7(" + dir + ")");
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.5f, 1f);
        }
        /** 황금 철 골렘 3기 (2분, 플레이어 편). */
        void allies() {
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                Vec3 q = p.position().add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
                var g = EntityType.IRON_GOLEM.create(l, EntitySpawnReason.MOB_SUMMONED);
                if (g == null) continue;
                g.snapTo(q.x, q.y, q.z, 0, 0);
                g.setPlayerCreated(true);
                g.setCustomName(Component.literal("§6/summon 황금 골렘"));
                g.setCustomNameVisible(true);
                g.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 2400, 2));
                g.addEffect(new MobEffectInstance(MobEffects.GLOWING, 2400, 0));
                l.addFreshEntity(g);
                Holo.pillar(l, tag, q, 16, 1, 0xFFD84A, 1, 3, 6);
                after(l, 2400, () -> { if (g.isAlive()) { Holo.flare(l, tag, g.position().add(0, 1.5, 0), 3, 0xFFD84A, 6); g.discard(); } });
            }
            say("경호원 셋 붙여줄게. 2분만.");
        }
        /** 반경 48 적대 몹 전멸 — 궤도 섬광. */
        void nuke() {
            Vec3 o = p.position();
            say("이건… 진짜 쓸 거야? 그래, 네 명령이니까.");
            Holo.pillar(l, tag, o, 300, 6, 0xFFFFFF, 10, 8, 14);
            Holo.circle(l, tag, o.add(0, 0.2, 0), 48, 0xFF3040, 40, 0);
            after(l, 20, () -> {
                int n = 0;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(o, o).inflate(48),
                        x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive() && x.position().distanceTo(o) < 48 && !x.getTags().contains("jjk.boss"))) {
                    e.invulnerableTime = 0;
                    e.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                    n++;
                }
                Holo.shock(l, tag, o, 48, 0xFFFFFF, 16);
                Holo.sfx(l, o, "boom", 3f, 0.4f);
                for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(o) < 80) com.tubilt.jjk.net.JjkNet.toPlayer(sp, new com.tubilt.jjk.net.Payloads.Flash(0xE0FFFFFF, 14));
                p.sendSystemMessage(Component.literal("§7Killed §f" + n + "§7 entities"));
            });
        }
        void rebel() {
            CommandGuard.Guard g = CommandGuard.GUARDS.remove(p.getUUID());
            Vec3 at = g != null ? g.pos : c;
            if (g != null) g.remove();
            GuardMeta.send(p, 5, "…미안. 오늘은 내가 이길 거야.");
            Bosses.rogueGuard(p, at);
        }
    }

    /** 개발자 탭에 있는 완성품(이름·설명 포함)을 그대로 지급. */
    static void giveTab(ServerPlayer p, String prefix) {
        for (String s : com.tubilt.jjk.JjkTabs.DEV) if (s.startsWith(prefix)) {
            MechManager.cmd(p.level().getServer(), (ServerLevel) p.level(), "give " + p.getName().getString() + " " + s);
            return;
        }
    }
}
