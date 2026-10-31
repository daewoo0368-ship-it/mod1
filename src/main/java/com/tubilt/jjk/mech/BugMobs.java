package com.tubilt.jjk.mech;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 버그걸린 몹 — 세계 곳곳의 일부 몹이 「버그」에 걸려 나타남. 잡으면 커맨데이션 재료.
 *  · 버그걸린 주민   (주민 5%)   → 일반 오류   : 순간이동처럼 끊겨서 움직임
 *  · 버그걸린 피글린 (피글린 8%) → 복사 오류   : 맞으면 잔상 복제를 남기고 미끄러짐
 *  · 버그걸린 엔더맨 (엔더맨 8%) → 체인 오류   : 주위에 사슬 번개를 늘어뜨림
 *  · 정화 우민       (삼림 대저택 안에서 최대 5명까지 자연 스폰 · 개발자 탭 「정화 우민 소환 알」) → 정화의 물체 : 나를 적대하는 하얀 교단 우민
 *      정화의 빛(광선 + 내 버프 하나 지움) · 교화 설교(멀미·구속) · 무리 결속(근처 우민 회복) · 순교(죽을 때 빛 폭발로 동료 회복)
 * 테스트: /tag @e[type=villager,limit=1,sort=nearest] add jjk.bug.v  (v·p·e, 정화 우민 u 는 zombie_villager)
 */
public final class BugMobs {
    private BugMobs() {}

    static final Random R = new Random();
    static final List<LivingEntity> TRACK = new ArrayList<>();
    static final String CHK = "jjk.bugchk";

    /** 새로 생긴 몹 → 확률로 버그/정화 변이. */
    public static void onJoin(Entity e, boolean fromDisk) {
        if (!(e instanceof LivingEntity le) || !(e.level() instanceof ServerLevel)) return;
        if (kind(le) != null) { if (!TRACK.contains(le)) TRACK.add(le); return; }
        if (fromDisk || le.getTags().contains(CHK)) return;
        le.addTag(CHK);
        int r = R.nextInt(100);
        if (le instanceof Villager) { if (r < 5) make(le, "v"); }
        else if (le instanceof Piglin && r < 8) make(le, "p");
        else if (le instanceof EnderMan && r < 8) make(le, "e");
    }

    /** k: v 주민, p 피글린, e 엔더맨, u 정화 우민. */
    public static void make(LivingEntity le, String k) {
        le.addTag("jjk.bug." + k);
        String name = switch (k) {
            case "v" -> "§c§kXX§r §c버그걸린 주민 §c§kXX";
            case "p" -> "§b§kXX§r §b버그걸린 피글린 §b§kXX";
            case "e" -> "§5§kXX§r §5버그걸린 엔더맨 §5§kXX";
            default -> "§f✧ 정화 우민 ✧";
        };
        le.setCustomName(Component.literal(name));
        le.setCustomNameVisible(true);
        le.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
        if (le instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
        if (k.equals("u")) {   // 적대 정화 우민: 체력 40 · 햇빛에 안 탐 · 빠름 · 하얀 갑옷
            var hp = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH); if (hp != null) hp.setBaseValue(40);
            le.setHealth(40);
            var ar = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR); if (ar != null) ar.setBaseValue(8);
            le.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
            le.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 0, false, false));
            le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));   // 햇빛 막이 겸 두건
            if (le instanceof net.minecraft.world.entity.Mob m) m.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0);
        }
        if (!TRACK.contains(le)) TRACK.add(le);
    }

    static String kind(LivingEntity le) {
        for (String k : new String[]{"v", "p", "e", "u"}) if (le.getTags().contains("jjk.bug." + k)) return k;
        return null;
    }

    static void tick() {
        for (Iterator<LivingEntity> it = TRACK.iterator(); it.hasNext(); ) {
            LivingEntity e = it.next();
            if (e.isRemoved() || !e.isAlive()) { it.remove(); continue; }
            if (!(e.level() instanceof ServerLevel l)) continue;
            String k = kind(e);
            if (k == null) { it.remove(); continue; }
            long age = e.tickCount;
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            switch (k) {
                case "v" -> {   // 프레임 끊김: 가끔 몇 칸씩 툭툭 순간이동
                    if (age % 50 == 0) {
                        Vec3 j = e.position().add((R.nextDouble() - 0.5) * 4, 0, (R.nextDouble() - 0.5) * 4);
                        if (l.noCollision(e, e.getBoundingBox().move(j.subtract(e.position())))) e.teleportTo(j.x, j.y, j.z);
                        l.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0.5);
                    }
                    if (age % 10 == 0) l.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, c.x, c.y, c.z, 3, 0.4, 0.6, 0.4, 0);
                }
                case "p" -> { if (age % 8 == 0) l.sendParticles(ParticleTypes.GLOW, c.x, c.y, c.z, 2, 0.3, 0.6, 0.3, 0); }
                case "e" -> {
                    if (age % 60 == 0) {   // 주위로 늘어지는 사슬 번개
                        Vec3 q = c.add((R.nextDouble() - 0.5) * 6, -1, (R.nextDouble() - 0.5) * 6);
                        Holo.lightning(l, "jjk.bugfx", c, q, 0xB45CFF, 0.06, 6);
                        for (ServerPlayer p : l.players()) if (p.position().distanceTo(q) < 1.6) p.hurtServer(l, l.damageSources().magic(), 3f);
                    }
                    if (age % 6 == 0) l.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 6, 0.3, 0.8, 0.3, 0.3);
                }
                default -> purified(e, l, c, age);
            }
        }
    }

    /** 정화 우민의 능력: 가장 가까운 플레이어를 노림. */
    static void purified(LivingEntity e, ServerLevel l, Vec3 c, long age) {
        if (age % 12 == 0) l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 2, 0.3, 0.3, 0.3, 0.01);
        ServerPlayer tgt = null; double bd = 24 * 24;
        for (ServerPlayer p : l.players()) if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(e) < bd) { bd = p.distanceToSqr(e); tgt = p; }
        if (tgt == null) return;
        if (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == null) m.setTarget(tgt);
        long ph = (age + e.getId() * 7L) % 100;
        if (ph == 0 && bd < 18 * 18) {   // 정화의 빛: 하얀 광선 + 내 좋은 효과 하나를 지움
            Vec3 to = tgt.position().add(0, 1, 0);
            Holo.beamGlow(l, "jjk.bugfx", c.add(0, 0.6, 0), to, 0.25, 0xFFFFFF, 1, 2, 4);
            tgt.hurtServer(l, l.damageSources().indirectMagic(e, e), 6f);
            for (var inst : new ArrayList<>(tgt.getActiveEffects()))
                if (inst.getEffect().value().isBeneficial()) { tgt.removeEffect(inst.getEffect()); tgt.displayClientMessage(Component.literal("§f✧ 정화 — 버프가 지워졌다"), true); break; }
            l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5f, 0.6f);
        }
        if (ph == 50 && bd < 8 * 8) {   // 교화 설교: 가까이 있으면 멀미·구속 + 설교 문구
            String[] L = {"생각을 멈추세요…", "의심은 죄입니다", "정화를 받아들이세요", "질문하지 마세요"};
            Holo.banner(l, "jjk.bugfx", c.add(0, 1.6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("「" + L[R.nextInt(L.length)] + "」", 0xFFFFFF, true) + "]}", 0.8, 30);
            tgt.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
            tgt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
        if (ph == 75) {   // 무리 결속: 근처 정화 우민끼리 회복 + 이속
            for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(8), x -> x != e && x.getTags().contains("jjk.bug.u"))) {
                o.heal(6);
                o.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
                Holo.lightning(l, "jjk.bugfx", c, o.position().add(0, 1, 0), 0xFFFFFF, 0.05, 4);
            }
        }
    }

    /** 피격: 버그걸린 피글린은 잔상 복제를 남기고 옆으로 미끄러짐. */
    public static void onHurt(LivingEntity le) {
        if (!(le.level() instanceof ServerLevel l) || !le.getTags().contains("jjk.bug.p")) return;
        Vec3 o = le.position();
        Holo.item(l, "jjk.bugfx", o.add(0, 1, 0), "minecraft:piglin_head", false, 0x55FFFF, Holo.m(Holo.I(), 1.2, 1.2, 1.2, Vec3.ZERO, false), 20, 0)
                .key(1, Holo.m(Holo.I(), 1.2, 1.2, 1.2, new Vec3(0, 1.5, 0), false), 18);
        Vec3 side = new Vec3(R.nextDouble() - 0.5, 0, R.nextDouble() - 0.5).normalize().scale(0.9);
        le.setDeltaMovement(side.add(0, 0.3, 0)); le.hurtMarked = true;
        l.playSound(null, o.x, o.y, o.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1f, 1.6f);
    }

    /** 죽음 → 재료 드롭. */
    public static void onDeath(LivingEntity le) {
        if (!(le.level() instanceof ServerLevel l)) return;
        String k = kind(le);
        if (k == null) return;
        var item = switch (k) {
            case "v" -> com.tubilt.jjk.JjkItems.ERROR_COMMON.get();
            case "p" -> com.tubilt.jjk.JjkItems.ERROR_COPY.get();
            case "e" -> com.tubilt.jjk.JjkItems.ERROR_CHAIN.get();
            default -> com.tubilt.jjk.JjkItems.PURIFY_OBJECT.get();
        };
        Vec3 c = le.position().add(0, le.getBbHeight() / 2, 0);
        if (k.equals("u")) {   // 순교: 하얀 빛 폭발
            Holo.explosion(l, "jjk.bugfx", c, 3, 0xFFFFFF);
            for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(6), x -> x != le && x.isAlive())) {
                if (o.getTags().contains("jjk.bug.u")) { o.heal(12); o.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 1)); }
                else if (o instanceof ServerPlayer sp && !sp.isCreative()) sp.hurtServer(l, l.damageSources().magic(), 5f);
            }
        }
        ItemEntity it = new ItemEntity(l, c.x, c.y, c.z, new ItemStack(item));
        it.setGlowingTag(true);
        l.addFreshEntity(it);
        int col = k.equals("v") ? 0xFF3040 : k.equals("p") ? 0x55FFFF : k.equals("e") ? 0xB45CFF : 0xFFFFFF;
        Holo.flare(l, "jjk.bugfx", c, 2, col, 8);
        Holo.banner(l, "jjk.bugfx", c.add(0, 1.4, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(k.equals("u") ? "✧ 정화됨" : "ERROR CAUGHT", col, true) + "]}", 0.9, 30);
        l.playSound(null, c.x, c.y, c.z, k.equals("u") ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2f, 1.2f);
    }

    /** 버그걸린 몹 소환 (v 주민 · p 피글린 · e 엔더맨). 피글린은 오버월드에서 좀비화되지 않게 면역. */
    public static LivingEntity spawnBug(ServerLevel l, Vec3 at, String k) {
        var type = k.equals("v") ? net.minecraft.world.entity.EntityType.VILLAGER : k.equals("p") ? net.minecraft.world.entity.EntityType.PIGLIN : net.minecraft.world.entity.EntityType.ENDERMAN;
        var m = type.create(l, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (m == null) return null;
        m.snapTo(at.x, at.y, at.z, R.nextFloat() * 360, 0);
        m.addTag(CHK);
        if (m instanceof net.minecraft.world.entity.monster.piglin.Piglin pg) pg.setImmuneToZombification(true);
        l.addFreshEntity(m);
        make((LivingEntity) m, k);
        int col = k.equals("v") ? 0xFF3040 : k.equals("p") ? 0x55FFFF : 0xB45CFF;
        Holo.flare(l, "jjk.bugfx", at.add(0, 1, 0), 2, col, 8);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.6f);
        return (LivingEntity) m;
    }

    /** 정화 우민 한 명 소환 (좀비 주민 기반). */
    public static LivingEntity spawnPurified(ServerLevel l, Vec3 at) {
        var v = net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER.create(l, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (v == null) return null;
        v.snapTo(at.x, at.y, at.z, R.nextFloat() * 360, 0);
        v.addTag(CHK);
        l.addFreshEntity(v);
        make(v, "u");
        Holo.flare(l, "jjk.bugfx", at.add(0, 1, 0), 2, 0xFFFFFF, 8);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.2f, 0.8f);
        return v;
    }

    /** 삼림 대저택 안: 플레이어 근처에 정화 우민 자연 스폰 (반경 64 안에 최대 5명). */
    static void mansionSpawn(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !(p.level() instanceof ServerLevel l)) continue;
            if (l.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) continue;
            if (!inMansion(l, p.blockPosition())) continue;
            int n = l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64), x -> x.isAlive() && x.getTags().contains("jjk.bug.u")).size();
            if (n >= 5 || R.nextInt(3) != 0) continue;
            for (int tries = 0; tries < 12; tries++) {   // 같은 층, 8~20칸 떨어진 빈 자리
                double a = R.nextDouble() * Math.PI * 2, d = 8 + R.nextDouble() * 12;
                net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
                if (!inMansion(l, bp)) continue;
                if (!l.getBlockState(bp).isAir() || !l.getBlockState(bp.above()).isAir() || l.getBlockState(bp.below()).isAir()) continue;
                spawnPurified(l, Vec3.atBottomCenterOf(bp));
                break;
            }
        }
    }
    static boolean inMansion(ServerLevel l, net.minecraft.core.BlockPos bp) {
        return l.structureManager().getStructureWithPieceAt(bp, h -> h.is(net.minecraft.world.level.levelgen.structure.BuiltinStructures.WOODLAND_MANSION)).isValid();
    }

    /** (예전) 우민의 대마법사가 쓰러지면: 정화 우민 3명 — 지금은 안 씀. */
    static void purifiedFromMage(ServerLevel l, Vec3 at) {
        for (int i = 0; i < 3; i++) {
            var v = net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER.create(l, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (v == null) continue;
            double a = i * Math.PI * 2 / 3;
            v.snapTo(at.x + Math.cos(a) * 2, at.y, at.z + Math.sin(a) * 2, 0, 0);
            v.addTag(CHK);
            l.addFreshEntity(v);
            make(v, "u");
        }
    }
}
