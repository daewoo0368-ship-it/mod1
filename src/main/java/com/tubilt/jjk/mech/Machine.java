package com.tubilt.jjk.mech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 개발자의 기계 무기 체계.
 *  Z/C 무기 선택 · R 발사(누르고 있기 무기는 계속) · X 코어 과부하(궁극) · G 보행/비행 · V 자동 포탑
 */
public final class Machine {
    private Machine() {}

    /** 이름, 재사용 대기(틱), 누르고 있기 여부 */
    public record Weapon(String name, int cd, boolean hold, int color) {}
    public static final Weapon[] WEAPONS = {
            new Weapon("개틀링 미니건", 0, true, 0xFFD040),
            new Weapon("화염방사기", 0, true, 0xFF7A20),
            new Weapon("미사일 32연장", 140, false, 0xFF5030),
            new Weapon("쌍열 주포", 0, true, 0xFFB060),
            new Weapon("레일 중포", 80, false, 0x40E8FF),
            new Weapon("박격포 폭격", 160, false, 0xE04040),
            new Weapon("쌍 아크 블래스터", 120, false, 0x7FDFFF),
            new Weapon("하이테크 흉부 레이저", 300, false, 0x40C8FF),
            new Weapon("대지 분쇄", 160, false, 0xC08040),
            new Weapon("EMP 펄스", 400, false, 0xA0A0FF),
            new Weapon("전투 드론 출격", 600, false, 0x90FF90),
            new Weapon("플라즈마 대검", 100, false, 0xFF40C0),
            new Weapon("하드라이트 방어막", 600, false, 0x60C0FF),
            new Weapon("궤도 지원 포격", 500, false, 0xFF3030)};
    public static final int W_GUN = 0, W_FLAME = 1, W_MISSILE = 2, W_TWIN = 3, W_RAIL = 4, W_MORTAR = 5, W_ARC = 6, W_LASER = 7, W_SLAM = 8, W_EMP = 9, W_DRONE = 10,
            W_BLADE = 11, W_SHIELD = 12, W_ORBITAL = 13;
    public static final int MAX_SPIN = 20, MAX_HEAT = 300, ULT_CD = 1200, ULT_CHARGE = 60, ULT_TIME = 120;
    static final int ARC_TIME = 50, LASER_CHARGE = 30, LASER_TIME = 80;

    static final DustParticleOptions CYAN = new DustParticleOptions(0x40E8FF, 3.0f);
    static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 3.0f);
    static final DustParticleOptions RED = new DustParticleOptions(0xFF2A2A, 3.5f);
    static final DustParticleOptions GOLD = new DustParticleOptions(0xFFC830, 3.0f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(0xB45CFF, 3.0f);
    static final DustParticleOptions EMP = new DustParticleOptions(0x9AA8FF, 4.0f);
    static final DustParticleOptions GREEN = new DustParticleOptions(0x70FF90, 1.6f);

    static final String[][] ARC_FX = {
            {"al0", "minecraft:white_concrete", "16777215"}, {"al1", "minecraft:light_blue_stained_glass", "4253951"},
            {"ar0", "minecraft:white_concrete", "16777215"}, {"ar1", "minecraft:light_blue_stained_glass", "4253951"}};
    static final String[][] LASER_FX = {
            {"L0", "minecraft:white_concrete", "16777215"}, {"L1", "minecraft:light_blue_stained_glass", "-1"},
            {"L2", "minecraft:cyan_stained_glass", "4253951"}, {"L3", "minecraft:blue_stained_glass", "-1"},
            {"Lo", "minecraft:white_concrete", "16777215"}, {"Lh", "minecraft:light_blue_stained_glass", "4253951"}};
    static final String[][] ULT_FX = {
            {"U0", "minecraft:white_concrete", "16777215"}, {"U1", "minecraft:red_stained_glass", "16722474"},
            {"U2", "minecraft:orange_stained_glass", "-1"}, {"U3", "minecraft:red_stained_glass", "-1"},
            {"U4", "minecraft:magenta_stained_glass", "16729343"},
            {"Uo1", "minecraft:white_concrete", "16777215"}, {"Uo2", "minecraft:red_stained_glass", "16722474"},
            {"Uo3", "minecraft:orange_stained_glass", "-1"},
            {"Uh1", "minecraft:white_stained_glass", "16777215"}, {"Uh2", "minecraft:red_stained_glass", "16722474"},
            {"Uh3", "minecraft:orange_stained_glass", "-1"}};
    static final String[][] ORBITAL = {
            {"O0", "minecraft:red_stained_glass", "16722474"}, {"O1", "minecraft:red_stained_glass", "16722474"},
            {"O2", "minecraft:red_stained_glass", "16722474"}, {"O3", "minecraft:red_stained_glass", "16722474"},
            {"O4", "minecraft:red_stained_glass", "16722474"}, {"O5", "minecraft:red_stained_glass", "16722474"}};

    static final List<Drone> DRONES = new ArrayList<>();

    // ───────────── 공용
    static Vec3 aim(DevMachine m) {
        return m.getControllingPassenger() instanceof Player p ? p.getLookAngle().normalize() : m.tf();
    }
    static Vec3 eye(DevMachine m) {
        return m.getControllingPassenger() instanceof Player p ? p.getEyePosition() : m.chest();
    }
    static Vec3 target(ServerLevel level, Vec3 from, Vec3 dir, double range) { return Titan.target(level, from, dir, range); }
    static Entity owner(DevMachine m) { return m.getControllingPassenger() != null ? m.getControllingPassenger() : m; }
    static DamageSource src(ServerLevel level, DevMachine m) {
        return m.getControllingPassenger() instanceof ServerPlayer p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(m);
    }
    static void msg(DevMachine m, String s) { if (m.getControllingPassenger() instanceof ServerPlayer p) p.displayClientMessage(Component.literal(s), true); }
    static boolean foe(DevMachine m, Entity e) {
        return e != m && e.isAlive() && e.getVehicle() != m && e != m.getControllingPassenger() && !(e instanceof DevVehicle)
                && !(e.getVehicle() instanceof DevVehicle);
    }
    static boolean grief(ServerLevel l) { return MechManager.griefOn(l.getServer()); }
    static Level.ExplosionInteraction ei(ServerLevel l) { return grief(l) ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE; }
    static boolean busy(DevMachine m) { return m.ultT > 0 || m.slamT > 0 || m.bladeT > 0; }
    /** Holo 연출 소유 태그 (기계가 사라지면 함께 정리). */
    static String own(DevMachine m) { return LaserFx.tag(m); }
    static void toRider(DevMachine m, net.minecraft.network.protocol.common.custom.CustomPacketPayload pl) {
        if (m.getControllingPassenger() instanceof ServerPlayer p) com.tubilt.jjk.net.JjkNet.toPlayer(p, pl);
    }
    static void cutIn(DevMachine m, String title, String sub, int col, int kind) {
        toRider(m, new com.tubilt.jjk.net.Payloads.CutIn(title, sub, col, kind));
        Holo.sfx((ServerLevel) m.level(), m.chest(), kind == 1 ? "stinger" : "plate", kind == 1 ? 1.4f : 0.9f, 1f);
    }
    static void cam(DevMachine m, Vec3 focus, double dist, double height, int span, int ms) {
        var sb = m.level().getServer().getScoreboard(); var o = sb.getObjective("gojo.cfg");
        if (o != null) { var i = sb.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("#cam"), o); if (i != null && i.value() == 0) return; }
        toRider(m, new com.tubilt.jjk.net.Payloads.Cam(focus.x, focus.y, focus.z, (float) dist, (float) height, (ms & 0xFFFF) | (((span + 720) & 0x7FF) << 16)));
    }

    // ───────────── 입력
    public static void cycle(ServerPlayer p, int dir) {
        if (!(p.getVehicle() instanceof DevMachine m)) return;
        m.trigger = false; m.flameT = 0;
        int w = (m.weapon() + dir + WEAPONS.length) % WEAPONS.length;
        m.setWeapon(w);
        ServerLevel level = (ServerLevel) m.level();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8f, 0.6f + w * 0.08f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.6f, 1.6f);
        String cd = m.cd[w] > 0 ? String.format(Locale.ROOT, " §7(%.1f초)", m.cd[w] / 20f) : " §a준비";
        p.displayClientMessage(Component.literal("§8[" + (w + 1) + "/" + WEAPONS.length + "] §f§l" + WEAPONS[w].name() + cd), true);
    }

    public static void trigger(ServerPlayer p, boolean on) {
        if (!(p.getVehicle() instanceof DevMachine m)) return;
        int w = m.weapon();
        if (WEAPONS[w].hold()) { m.trigger = on; return; }
        if (!on) return;
        if (busy(m)) return;
        if (m.cd[w] > 0) { msg(m, String.format(Locale.ROOT, "§7%s 재장전 §f%.1f초", WEAPONS[w].name(), m.cd[w] / 20f)); return; }
        ServerLevel level = (ServerLevel) m.level();
        boolean ok = switch (w) {
            case W_MISSILE -> missiles(m, level);
            case W_RAIL -> rail(m, level);
            case W_MORTAR -> mortar(m, level);
            case W_ARC -> arcStart(m, level);
            case W_LASER -> laserStart(m, level);
            case W_SLAM -> slamStart(m, level);
            case W_EMP -> emp(m, level);
            case W_DRONE -> drones(m, level);
            case W_BLADE -> bladeStart(m, level);
            case W_SHIELD -> shieldStart(m, level);
            case W_ORBITAL -> orbitalStart(m, level);
            default -> false;
        };
        if (ok) {
            m.cd[w] = WEAPONS[w].cd();
            if (w >= W_MISSILE && !WEAPONS[w].hold()) cutIn(m, WEAPONS[w].name(), "DEV-MACHINE · ARM " + String.format(Locale.ROOT, "%02d", w + 1), WEAPONS[w].color(), 0);
        }
    }

    public static void flight(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMachine m) || busy(m)) return;
        ServerLevel level = (ServerLevel) m.level();
        m.setFlying(!m.flying());
        Vec3 c = m.position();
        if (m.flying()) {
            m.setDeltaMovement(m.getDeltaMovement().add(0, 1.6, 0));
            m.hurtMarked = true;
            level.playSound(null, c.x, c.y + 20, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 6f, 0.3f);
            level.playSound(null, c.x, c.y + 20, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6f, 0.6f);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1, c.z, 120, 10, 1, 10, 0.1);
            for (int i = 0; i < 3; i++) Laser.ring(level, c.add(0, 0.5, 0), 6 + i * 6);
            Holo.shockTex(level, own(m), c.add(0, 0.3, 0), 30, 0x40E8FF, 10);
            Holo.ringT(level, own(m), m.halo(), 2, 22, 30, 0.6, 0.08, 0x40E8FF, 5, 2, 6, Holo.facing(m.tf()), 0, true);
            Holo.flare(level, own(m), m.halo(), 20, 0x40E8FF, 10);
            Holo.decal(level, own(m), c.add(0, 0.08, 0), "crack", 0x40E8FF, 2, 22, 3, 20, 10, 0, Holo.I());
            Holo.sfx(level, c, "riser", 2.5f, 1.2f); Holo.sfx(level, c, "whoosh", 3f, 0.5f);
            cutIn(m, "비행 형태", "HARDLIGHT WINGS · ONLINE", 0x40E8FF, 0);
            msg(m, "§b§l비행 형태 §7— 에너지 날개 전개 · 보는 방향으로 비행 · 달리기 가속 · G 착지");
        } else {
            level.playSound(null, c.x, c.y + 20, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 6f, 0.6f);
            msg(m, "§7보행 형태 — 낙하 착지");
        }
    }

    public static void turret(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMachine m)) return;
        m.autoTurret = !m.autoTurret;
        ((ServerLevel) m.level()).playSound(null, p.getX(), p.getY(), p.getZ(), m.autoTurret ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 1.5f, 1.4f);
        msg(m, m.autoTurret ? "§a§l자동 포탑 ON §7— 어깨 포탑이 주변 적을 알아서 쏨" : "§7자동 포탑 OFF");
    }

    /** X: 코어 과부하 — 3초 충전 → 6초간 전 무장 일제 사격 + 흉부 초대형 빔 + 궤도 폭격. */
    public static void ult(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMachine m)) return;
        if (busy(m)) return;
        if (m.ultCd > 0) { msg(m, String.format(Locale.ROOT, "§7코어 과부하 냉각 §f%.1f초", m.ultCd / 20f)); return; }
        ServerLevel level = (ServerLevel) m.level();
        m.ultCd = ULT_CD;
        m.ultT = ULT_CHARGE + ULT_TIME;
        m.trigger = false;
        killFx(level, m, LASER_FX); killFx(level, m, ARC_FX);
        LaserFx.spawn(level, m, ULT_FX, m.chest());
        if (!level.isThundering()) { MechManager.cmd(level.getServer(), level, "weather thunder 600"); m.weatherSet = true; }
        Vec3 c = m.chest();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 10f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 10f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 10f, 0.4f);
        msg(m, "§4§l⚠ 코어 과부하 ⚠ §c모든 안전장치 해제");
        cutIn(m, "코어 과부하", "OVERLOAD ─ 전 무장 해방", 0xFF3030, 1);
        cam(m, m.position().add(0, 18, 0), 70, 16, 140, 2800);
        toRider(m, new com.tubilt.jjk.net.Payloads.Screen(5, 0xFF2020, 9000, 0.5f));
        Holo.circle(level, own(m), m.position(), 36, 0xFF3030, ULT_CHARGE + ULT_TIME, 0);
        Holo.circle(level, own(m), m.position().add(0, 0.3, 0), 22, 0xFFB040, ULT_CHARGE + ULT_TIME, 0);
        for (int i = 0; i < 3; i++) Holo.ringT(level, own(m), c, 30 - i * 6, 3, 30, 0.6, 0.1, i == 1 ? 0xFFFFFF : 0xFF3030, ULT_CHARGE - 6, 0, 4, Holo.rotX(0.5 + i * 0.6), 6 - i * 4, true);
        Holo.sfx(level, c, "charge", 3f, 0.5f); Holo.sfx(level, c, "choir", 2.5f, 0.6f); Holo.sfx(level, c, "siren", 2f, 0.8f);
        MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..120] title {\"text\":\"코어 과부하\",\"color\":\"#FF3030\",\"bold\":true}", c.x, c.y, c.z));
        MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..120] subtitle {\"text\":\"개발자의 기계 — 전 무장 해방\",\"color\":\"#FFB040\"}", c.x, c.y, c.z));
    }

    static void killFx(ServerLevel level, DevMachine m, String[][] fx) { for (String[] l : fx) LaserFx.kill(level, m, l[0]); }

    // ───────────── 틱
    static void tick(DevMachine m) {
        ServerLevel level = (ServerLevel) m.level();
        for (int i = 0; i < m.cd.length; i++) if (m.cd[i] > 0) m.cd[i]--;
        if (m.ultCd > 0) m.ultCd--;
        if (!(m.getControllingPassenger() instanceof ServerPlayer)) { m.trigger = false; m.autoTurret = false; }
        int aim = 0, md = 0;
        int w = m.weapon();
        boolean hold = m.trigger && !busy(m);

        // 미니건 (왼팔)
        boolean gunFire = hold && w == W_GUN && m.overheat == 0;
        if (m.overheat > 0) { if (--m.overheat == 0) m.gunHeat = MAX_HEAT / 2; steam(m, level); }
        int prevSpin = m.spin;
        m.spin = gunFire ? Math.min(MAX_SPIN, m.spin + 2) : Math.max(0, m.spin - 1);
        if (gunFire && prevSpin == 0) level.playSound(null, m.getX(), m.getY() + 15, m.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 3f, 1.4f);
        if (m.spin > 0 && m.spin < MAX_SPIN && m.tickCount % 3 == 0)
            level.playSound(null, m.getX(), m.getY() + 15, m.getZ(), SoundEvents.MINECART_INSIDE, SoundSource.PLAYERS, 1f, 0.5f + m.spin * 0.05f);
        if (gunFire) aim |= 1;
        if (gunFire && m.spin >= 14) minigun(m, level);
        else if (m.overheat == 0 && m.gunHeat > 0) m.gunHeat = Math.max(0, m.gunHeat - 3);

        // 화염방사기 (오른팔)
        if (hold && w == W_FLAME) { aim |= 2; flame(m, level); }
        // 쌍열 주포 (어깨)
        if (hold && w == W_TWIN && m.tickCount % 5 == 0) twin(m, level, aim(m), (m.tickCount / 5) % 2 == 0 ? -1 : 1);
        // 자동 포탑
        if (m.autoTurret && !(hold && w == W_TWIN) && m.tickCount % 8 == 0) autoTurret(m, level);

        if (m.flameT > 0) m.flameT--;
        if (m.arcT > 0) { aim |= 3; arcTick(m, level); }
        if (m.laserT > 0) laserTick(m, level);
        if (m.slamT > 0) { md = slamTick(m, level); }
        if (m.bladeT > 0) { aim |= 2; bladeTick(m, level); }
        if (m.shieldT > 0) shieldTick(m, level);
        if (m.orbitalT > 0) orbitalTick(m, level);
        m.setShield(m.shieldT);
        if (m.ultT > 0) { md = ultTick(m, level); }
        if (m.flying()) jets(m, level);
        if (!m.flying() && !m.onGround()) m.fallT++;
        else { if (m.fallT > 15) landing(m, level); m.fallT = 0; }
        m.sync(md, aim);
    }

    static void tickGlobal(MinecraftServer server) {
        for (Iterator<Drone> it = DRONES.iterator(); it.hasNext(); ) {
            Drone d = it.next();
            try { d.tick(); } catch (Exception e) { d.dead = true; d.discard(); }
            if (d.dead) it.remove();
        }
    }

    static void cleanup(DevMachine m, ServerLevel level) {
        LaserFx.clear(level, m);
        Holo.killOwner(own(m));
        for (Drone d : DRONES) if (d.machine.equals(m.getUUID())) d.life = Math.max(d.life, Drone.LIFE - 1);
        if (m.weatherSet) { MechManager.cmd(level.getServer(), level, "weather clear"); m.weatherSet = false; }
    }

    // ───────────── 발 디딤 / 착지 / 분사
    static void stomp(DevMachine m, ServerLevel level, double x, double y, double z) {
        var src = src(level, m);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 3.5, y - 2, z - 3.5, x + 3.5, y + 4, z + 3.5),
                e -> foe(m, e) && !(e instanceof Player) && !e.isInvulnerable())) {
            v.invulnerableTime = 0;
            v.hurtServer(level, src, Math.max(500f, v.getMaxHealth() * 2));
        }
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 24, 3, 0.2, 3, 0.08);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Slam.ground(level, new Vec3(x, y, z))), x, y + 1, z, 40, 3, 0.8, 3, 0.3);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 8, 1.5, 0.3, 1.5, 0.2);
        level.playSound(null, x, y, z, SoundEvents.IRON_GOLEM_STEP, SoundSource.NEUTRAL, 3f, 0.4f);
        level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.8f, 0.4f);
        Titan.shake(level, new Vec3(x, y, z), 40, false);
        Holo.decal(level, own(m), new Vec3(x, y + 0.08, z), "ring", 0x40E8FF, 1, 7, 2, 0, 6, 0, Holo.I());
        Holo.decal(level, own(m), new Vec3(x, y + 0.06, z), "crack", 0x303030, 1, 5, 1, 30, 10, 0, Holo.I());
    }

    static void landing(DevMachine m, ServerLevel level) {
        Vec3 c = m.position();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 8f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 6f, 0.3f);
        for (int i = 0; i < 3; i++) Laser.ring(level, c.add(0, 0.5, 0), 8 + i * 8);
        Slam.dustRing(level, c, 16, Slam.ground(level, c));
        stomp(m, level, c.x, c.y, c.z);
        Holo.shockTex(level, own(m), c.add(0, 0.3, 0), 34, 0x40E8FF, 10);
        Holo.decal(level, own(m), c.add(0, 0.08, 0), "crack", 0x40E8FF, 3, 26, 2, 30, 14, 0, Holo.I());
        Holo.shards(level, own(m), c, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Slam.ground(level, c).getBlock()).toString(), 18, 6);
        Holo.punch(level, c, 6, 0x40E8FF);
        Holo.sfx(level, c, "impact", 3f, 0.6f);
        Titan.shake(level, c, 100, true);
    }

    static void jets(DevMachine m, ServerLevel level) {
        for (int s = -1; s <= 1; s += 2) {
            Vec3 n = m.jets(s);
            level.sendParticles(ParticleTypes.FLAME, n.x, n.y - 1, n.z, 10, 0.6, 0.6, 0.6, 0.06);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, n.x, n.y - 1.5, n.z, 4, 0.4, 0.6, 0.4, 0.04);
            level.sendParticles(CYAN, n.x, n.y - 2, n.z, 3, 0.8, 0.8, 0.8, 0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, n.x, n.y - 5, n.z, 2, 1.2, 1.2, 1.2, 0.02);
        }
        if (m.tickCount % 2 == 0) for (int s = -1; s <= 1; s += 2) {
            Vec3 n = m.jets(s).add(0, -1.5, 0);
            Holo.sprite(level, own(m), n, "flare", 0x40E8FF, "center", Holo.bb(3.5, m.tickCount * 0.3, Vec3.ZERO), 3, 0).key(1, Holo.bb(1, 0, Vec3.ZERO), 2);
            Holo.sprite(level, own(m), n.add(0, -2.5, 0), "smoke" + (m.tickCount / 2 % 2), 0x80E8FF, "center", Holo.bb(2.5, m.tickCount, Vec3.ZERO), 10, 0).key(1, Holo.bb(0.3, m.tickCount + 1, Vec3.ZERO), 9);
        }
        if (m.tickCount % 8 == 0) level.playSound(null, m.getX(), m.getY() + 15, m.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 5f, 0.4f);
        if (m.tickCount % 20 == 0) level.playSound(null, m.getX(), m.getY() + 15, m.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 4f, 0.6f);
    }

    static void steam(DevMachine m, ServerLevel level) {
        Vec3 mz = m.gatMuzzle(aim(m));
        level.sendParticles(ParticleTypes.CLOUD, mz.x, mz.y + 0.5, mz.z, 3, 0.8, 0.4, 0.8, 0.03);
        if (m.overheat % 10 == 0) level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 2f, 0.7f);
    }

    // ───────────── 0 개틀링 미니건: 틱당 4발 (초당 80발), 탄당 12
    static void minigun(DevMachine m, ServerLevel level) {
        RandomSource rnd = level.random;
        Vec3 a = aim(m), mz = m.gatMuzzle(a);
        Vec3 tgt = target(level, eye(m), a, 160);
        Vec3 dir = tgt.subtract(mz).normalize();
        for (int b = 0; b < 4; b++) hitscan(m, level, mz, dir, 0.04, 160, 12f, b < 2, Minigun.TRACER);
        level.sendParticles(Minigun.FLASH, mz.x, mz.y, mz.z, 4, 0.4, 0.4, 0.4, 0);
        if (m.tickCount % 2 == 0) {
            Holo.sprite(level, own(m), mz, "flare", 0xFFD040, "center", Holo.bb(4.5, rnd.nextDouble() * 6, Vec3.ZERO), 2, 0).key(1, Holo.bb(1.5, 0, Vec3.ZERO), 1);
            Holo.beamGlow(level, own(m), mz, tgt, 0.3, 0xFFD040, 0, 1, 1);
        }
        level.sendParticles(ParticleTypes.FLAME, mz.x, mz.y, mz.z, 6, 0.3, 0.3, 0.3, 0.06);
        level.sendParticles(ParticleTypes.SMOKE, mz.x, mz.y + 0.3, mz.z, 3, 0.4, 0.2, 0.4, 0.03);
        Vec3 ej = m.at(-58 * DevMachine.S - 1.5, (97 + 62) * DevMachine.S, 0);
        level.sendParticles(Minigun.CASING, ej.x, ej.y, ej.z, 4, 0.3, 0.1, 0.3, 0);
        level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 3f, 1.2f + rnd.nextFloat() * 0.3f);
        if (m.tickCount % 2 == 0) level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 2f, 1.5f);
        if (++m.gunHeat >= MAX_HEAT) {
            m.overheat = 60; m.trigger = false;
            level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 3f, 0.6f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mz.x, mz.y, mz.z, 30, 1, 0.6, 1, 0.05);
            msg(m, "§c§l개틀링 과열! §73초 냉각");
        }
    }

    /** 히트스캔 1발 → 맞은 대상 반환. */
    static LivingEntity hitscan(DevMachine m, ServerLevel level, Vec3 from, Vec3 aim, double spread, double range, float dmg, boolean tracer, DustParticleOptions col) {
        RandomSource rnd = level.random;
        Vec3 dir = aim.add(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread).normalize();
        Vec3 end = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        LivingEntity hitE = null;
        double best = from.distanceTo(stop);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(1.5), e -> foe(m, e))) {
            var h = le.getBoundingBox().inflate(0.5).clip(from, stop);
            if (h.isPresent() && from.distanceTo(h.get()) < best) { best = from.distanceTo(h.get()); hitE = le; }
        }
        Vec3 imp = from.add(dir.scale(best));
        if (tracer) for (double d = 1; d < best; d += 1.8) level.sendParticles(col, from.x + dir.x * d, from.y + dir.y * d, from.z + dir.z * d, 1, 0, 0, 0, 0);
        if (hitE != null) {
            hitE.invulnerableTime = 0;
            hitE.hurtServer(level, src(level, m), dmg);
            hitE.setDeltaMovement(hitE.getDeltaMovement().add(dir.scale(0.15)));
            level.sendParticles(ParticleTypes.CRIT, imp.x, imp.y, imp.z, 5, 0.2, 0.2, 0.2, 0.3);
        } else if (bh.getType() != HitResult.Type.MISS) {
            BlockPos bp = bh.getBlockPos();
            BlockState st = level.getBlockState(bp);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), imp.x, imp.y, imp.z, 6, 0.2, 0.2, 0.2, 0.15);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, imp.x, imp.y, imp.z, 2, 0.1, 0.1, 0.1, 0.2);
            float hard = st.getDestroySpeed(level, bp);
            if (hard >= 0 && hard <= 1.5f && rnd.nextInt(3) == 0 && grief(level)) level.destroyBlock(bp, false);
        }
        return hitE;
    }

    // ───────────── 1 화염방사기: 사거리 50, 원뿔
    static void flame(DevMachine m, ServerLevel level) {
        RandomSource rnd = level.random;
        Vec3 a = aim(m), n = m.flameNozzle(a);
        for (int i = 0; i < 36; i++) {
            Vec3 v = a.add(rnd.nextGaussian() * 0.09, rnd.nextGaussian() * 0.09, rnd.nextGaussian() * 0.09).scale(1.8 + rnd.nextDouble() * 1.6);
            level.sendParticles(i % 4 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, n.x, n.y, n.z, 0, v.x, v.y, v.z, 1.0);
        }
        for (int i = 0; i < 6; i++) {
            Vec3 q = n.add(a.scale(6 + rnd.nextDouble() * 40)).add(rnd.nextGaussian() * 2.5, rnd.nextGaussian() * 2.5, rnd.nextGaussian() * 2.5);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 1, 0.6, 0.6, 0.6, 0.03);
            level.sendParticles(Titan.ORANGE, q.x, q.y, q.z, 2, 1.2, 1.2, 1.2, 0);
        }
        if (m.tickCount % 2 == 0) for (int i = 0; i < 3; i++) {
            double d = 6 + i * 14 + rnd.nextDouble() * 6, sz = 2.5 + d * 0.18;
            Vec3 q = n.add(a.scale(d)).add(rnd.nextGaussian() * d * 0.05, rnd.nextGaussian() * d * 0.05, rnd.nextGaussian() * d * 0.05);
            Holo.sprite(level, own(m), q, "smoke" + (i % 2), i == 0 ? 0xFFD060 : 0xFF6A20, "center", Holo.bb(sz * 0.5, rnd.nextDouble() * 6, Vec3.ZERO), 8, 0)
                    .key(1, Holo.bb(sz, rnd.nextDouble() * 6, Vec3.ZERO), 4).key(5, Holo.bb(0.2, 0, Vec3.ZERO), 3);
        }
        if (m.tickCount % 3 == 0) level.playSound(null, n.x, n.y, n.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 4f, 0.5f);
        if (m.tickCount % 5 == 0) level.playSound(null, n.x, n.y, n.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 4f, 0.4f);
        if (m.tickCount % 2 == 0) for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(n, n.add(a.scale(50))).inflate(14), e -> foe(m, e))) {
            Vec3 to = le.position().add(0, le.getBbHeight() / 2, 0).subtract(n);
            if (to.length() > 50 || to.normalize().dot(a) < 0.965) continue;
            le.invulnerableTime = 0;
            le.hurtServer(level, src(level, m), 18f);
            le.igniteForSeconds(12);
        }
        if (grief(level) && m.tickCount % 3 == 0) for (int i = 0; i < 4; i++) {
            Vec3 dir = a.add(rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12).normalize();
            BlockHitResult h = level.clip(new ClipContext(n, n.add(dir.scale(50)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (h.getType() == HitResult.Type.MISS) continue;
            BlockPos fp = h.getBlockPos().relative(h.getDirection());
            if (level.getBlockState(fp).isAir()) level.setBlock(fp, Blocks.FIRE.defaultBlockState(), 3);
        }
    }

    // ───────────── 2 미사일 32연장
    static boolean missiles(DevMachine m, ServerLevel level) {
        Vec3 a = aim(m), tgt = target(level, eye(m), a, 300);
        List<LivingEntity> locks = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(tgt, tgt).inflate(28), e -> foe(m, e) && !(e instanceof Player)));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(70),
                e -> e instanceof Enemy && foe(m, e) && a.dot(e.position().subtract(eye(m)).normalize()) > 0.75))
            if (!locks.contains(e)) locks.add(e);
        RandomSource rnd = level.random;
        UUID shooter = owner(m).getUUID();
        for (int i = 0; i < 32; i++) {
            int side = i % 2 == 0 ? -1 : 1;
            Vec3 pod = m.pod(side).add((rnd.nextDouble() - 0.5) * 2, 0, (rnd.nextDouble() - 0.5) * 2);
            Vec3 launch = new Vec3(0, 1.2 + rnd.nextDouble() * 0.5, 0).add(m.tr().scale(side * (0.3 + rnd.nextDouble() * 0.5))).add(a.scale(0.2));
            Entity lock = locks.isEmpty() ? null : locks.get(i % locks.size());
            MechManager.MISSILES.add(new Missile(level, shooter, pod, launch, tgt.add((rnd.nextDouble() - 0.5) * 26, 0, (rnd.nextDouble() - 0.5) * 26), lock, i / 2));
        }
        m.triggerAnim("main", "recoil");
        for (int sd = -1; sd <= 1; sd += 2) { Holo.flare(level, own(m), m.pod(sd), 10, 0xFF8040, 6); Holo.smoke(level, own(m), m.pod(sd), 6, 4, -1, 6, 30); }
        for (Entity lk : locks.subList(0, Math.min(16, locks.size()))) Holo.reticle(level, own(m), lk, 0xFF5030, 40);
        Holo.sfx(level, m.chest(), "whoosh", 3f, 0.6f);
        level.playSound(null, m.getX(), m.getY() + 25, m.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 6f, 0.5f);
        level.playSound(null, m.getX(), m.getY() + 25, m.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 4f, 0.5f);
        msg(m, "§6§l미사일 32발 일제 발사!" + (locks.isEmpty() ? "" : " §c락온 " + Math.min(32, locks.size())));
        return true;
    }

    // ───────────── 3 쌍열 주포 (누르고 있기, 좌우 번갈아)
    static void twin(DevMachine m, ServerLevel level, Vec3 a, int side) {
        Vec3 mz = m.cannonMuzzle(a, side);
        Vec3 tgt = target(level, eye(m), a, 250);
        Vec3 dir = tgt.subtract(mz).normalize();
        MechManager.SHELLS.add(new Shell(level, owner(m).getUUID(), m.getUUID(), mz, dir.scale(4.0), false));
        level.sendParticles(ParticleTypes.EXPLOSION, mz.x, mz.y, mz.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, mz.x, mz.y, mz.z, 14, 0.4, 0.4, 0.4, 0.15);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mz.x, mz.y, mz.z, 4, 0.5, 0.5, 0.5, 0.03);
        level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3f, 1.3f);
        level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3f, 0.6f);
    }

    static void autoTurret(DevMachine m, ServerLevel level) {
        Vec3 c = m.pod(1);
        LivingEntity best = null; double bd = 70 * 70;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(70), e -> e instanceof Enemy && foe(m, e))) {
            double d = e.distanceToSqr(c);
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) return;
        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0);
        int side = (m.tickCount / 8) % 2 == 0 ? -1 : 1;
        Vec3 mz = m.cannonMuzzle(m.tf(), side);
        MechManager.SHELLS.add(new Shell(level, owner(m).getUUID(), m.getUUID(), mz, to.subtract(mz).normalize().scale(4.0), false));
        level.sendParticles(ParticleTypes.FLAME, mz.x, mz.y, mz.z, 8, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, mz.x, mz.y, mz.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 2f, 0.6f);
    }

    // ───────────── 4 레일 중포
    static boolean rail(DevMachine m, ServerLevel level) {
        Vec3 a = aim(m);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 mz = m.cannonMuzzle(a, s);
            Vec3 dir = target(level, eye(m), a, 300).subtract(mz).normalize();
            MechManager.SHELLS.add(new Shell(level, owner(m).getUUID(), m.getUUID(), mz, dir.scale(6.5), true));
            level.sendParticles(Shell.BLUE, mz.x, mz.y, mz.z, 30, 1, 1, 1, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mz.x, mz.y, mz.z, 30, 1.2, 1.2, 1.2, 0.3);
            level.sendParticles(ParticleTypes.SONIC_BOOM, mz.x, mz.y, mz.z, 1, 0, 0, 0, 0);
            Vec3 far = mz.add(dir.scale(Math.min(300, mz.distanceTo(target(level, eye(m), a, 300)))));
            Holo.beam(level, own(m), mz, far, 1.2, 0x40E8FF, 1, 2, 6);
            Holo.beamGlow(level, own(m), mz, far, 5, 0x40E8FF, 1, 2, 6);
            Holo.shockTex(level, own(m), mz, 8, 0x40E8FF, 6);
            Holo.ringT(level, own(m), mz, 0.5, 6, 18, 0.3, 0.05, 0xFFFFFF, 2, 0, 4, Holo.facing(dir), 0, true);
        }
        Holo.sfx(level, m.chest(), "laser", 3f, 1.2f);
        m.triggerAnim("main", "recoil");
        Vec3 c = m.chest();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 1.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 5f, 0.8f);
        Titan.shake(level, c, 40, false);
        msg(m, "§b§l레일 중포 발사!");
        return true;
    }

    // ───────────── 5 박격포 폭격 (12발)
    static boolean mortar(DevMachine m, ServerLevel level) {
        Vec3 tgt = target(level, eye(m), aim(m), 400);
        Vec3 mouth = m.mortar();
        RandomSource rnd = level.random;
        for (int i = 0; i < 12; i++) {
            double ang = i / 12.0 * Math.PI * 2, r = 6 + rnd.nextDouble() * 16;
            Titan.BOMBS.add(new Titan.Bomb(level, owner(m).getUUID(), m.getUUID(), mouth, tgt.add(Math.cos(ang) * r, 0, Math.sin(ang) * r), i * 3));
        }
        msg(m, "§c§l박격포 12발 폭격!");
        return true;
    }

    // ───────────── 6 쌍 아크 블래스터 (오른손바닥 + 왼쪽 개틀링 총구)
    static boolean arcStart(DevMachine m, ServerLevel level) {
        if (m.laserT > 0) return false;
        m.arcT = ARC_TIME + 10;
        LaserFx.spawn(level, m, ARC_FX, m.chest());
        level.playSound(null, m.getX(), m.getY() + 20, m.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5f, 1.4f);
        msg(m, "§b§l쌍 아크 블래스터");
        return true;
    }

    static void arcTick(DevMachine m, ServerLevel level) {
        m.arcT--;
        int k = ARC_TIME + 10 - m.arcT;
        Vec3 a = aim(m), tgt = target(level, eye(m), a, 250);
        RandomSource rnd = level.random;
        for (int s = 0; s < 2; s++) {
            Vec3 from = s == 0 ? m.gatMuzzle(a) : m.palm(a);
            String L = s == 0 ? "al" : "ar";
            if (k < 10) {
                level.sendParticles(CYAN, from.x, from.y, from.z, 6, 0.8, 0.8, 0.8, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 8, 1, 1, 1, 0.2);
                continue;
            }
            Vec3 dir = tgt.subtract(from).normalize();
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(250)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean blocked = hit.getType() != HitResult.Type.MISS;
            Vec3 stop = blocked ? hit.getLocation() : from.add(dir.scale(250));
            double len = stop.distanceTo(from), pulse = 1 + Math.sin(k * 0.9) * 0.15;
            LaserFx.set(level, m, L + "0", from, LaserFx.beam(dir, len, 1.2 * pulse, k * 0.2));
            LaserFx.set(level, m, L + "1", from, LaserFx.beam(dir, len, 3.0 * pulse, -k * 0.15));
            if (k % 3 == 0) Holo.beamGlow(level, own(m), from, stop, 7, 0x7FDFFF, 0, 2, 2);
            if (k % 6 == 0) Holo.sprite(level, own(m), from, "flare", 0xFFFFFF, "center", Holo.bb(6, k, Vec3.ZERO), 4, 0).key(1, Holo.bb(2, k + 1, Vec3.ZERO), 3);
            for (double d = 2; d < len; d += 3) {
                Vec3 q = from.add(dir.scale(d));
                level.sendParticles(CYAN, q.x, q.y, q.z, 1, 0.5, 0.5, 0.5, 0);
                if (rnd.nextInt(3) == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.6, 0.6, 0.6, 0.1);
            }
            if (k % 2 == 0) for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(4), e -> foe(m, e))) {
                if (le.getBoundingBox().inflate(3).clip(from, stop).isEmpty()) continue;
                le.invulnerableTime = 0;
                le.hurtServer(level, src(level, m), 120f);
                le.igniteForSeconds(8);
            }
            if (blocked) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y, stop.z, 1, 0, 0, 0, 0);
                if (k % 3 == 0) level.explode(owner(m), stop.x, stop.y, stop.z, 6f, false, ei(level));
                if (grief(level)) Titan.carve(level, stop, 3.5, rnd);
            }
        }
        if (k == 10) level.playSound(null, m.getX(), m.getY() + 20, m.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 1.3f);
        if (k > 10 && k % 6 == 0) level.playSound(null, m.getX(), m.getY() + 20, m.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 4f, 1.6f);
        if (m.arcT == 0) killFx(level, m, ARC_FX);
    }

    // ───────────── 7 하이테크 흉부 레이저 (1.5초 충전 → 4초)
    static boolean laserStart(DevMachine m, ServerLevel level) {
        if (m.arcT > 0) return false;
        m.laserT = LASER_CHARGE + LASER_TIME;
        LaserFx.spawn(level, m, LASER_FX, m.chest());
        Vec3 c = m.chest();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 6f, 0.8f);
        msg(m, "§b§l하이테크 흉부 레이저 §7충전...");
        return true;
    }

    static void laserTick(DevMachine m, ServerLevel level) {
        m.laserT--;
        int k = LASER_CHARGE + LASER_TIME - m.laserT;
        Vec3 e = m.chest();
        RandomSource rnd = level.random;
        if (k <= LASER_CHARGE) {
            double f = k / (double) LASER_CHARGE;
            LaserFx.set(level, m, "Lo", e, LaserFx.cube(1 + 5 * f, k * 0.3, k * 0.2));
            LaserFx.set(level, m, "Lh", e, LaserFx.cube(2 + 8 * f, -k * 0.2, k * 0.3));
            for (int i = 0; i < 20; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2, rr = 10 + rnd.nextDouble() * 20;
                Vec3 o = e.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
                Vec3 v = e.subtract(o).scale(0.15);
                level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
            }
            if (k == 1) { Holo.circle(level, own(m), e, 8, 0x40C8FF, LASER_CHARGE + 4, 1.57); Holo.sfx(level, e, "charge", 2.5f, 0.8f); }
            if (k == LASER_CHARGE) {
                m.triggerAnim("main", "recoil");
                Holo.flare(level, own(m), e, 30, 0xFFFFFF, 10); Holo.shockTex(level, own(m), e, 26, 0x40C8FF, 8); Holo.punch(level, e, 6, 0x40C8FF); Holo.sfx(level, e, "laser", 3f, 0.7f);
                level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8f, 0.6f);
                level.playSound(null, e.x, e.y, e.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6f, 0.5f);
                Titan.shake(level, e, 120, true);
            }
            return;
        }
        int t = k - LASER_CHARGE;
        Vec3 a = aim(m), tgt = target(level, eye(m), a, 400), dir = tgt.subtract(e).normalize();
        BlockHitResult hit = level.clip(new ClipContext(e, e.add(dir.scale(400)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 stop = blocked ? hit.getLocation() : e.add(dir.scale(400));
        double len = stop.distanceTo(e), pulse = 1 + Math.sin(t * 0.7) * 0.15;
        LaserFx.set(level, m, "L0", e, LaserFx.beam(dir, len, 3 * pulse, t * 0.25));
        LaserFx.set(level, m, "L1", e, LaserFx.beam(dir, len, 6 * pulse, -t * 0.15));
        LaserFx.set(level, m, "L2", e, LaserFx.beam(dir, len, 9 * (2 - pulse), t * 0.1));
        LaserFx.set(level, m, "L3", e, LaserFx.beam(dir, len, 12 * pulse, -t * 0.07));
        LaserFx.set(level, m, "Lo", e, LaserFx.cube(6 * pulse, t * 0.4, t * 0.3));
        LaserFx.set(level, m, "Lh", stop, LaserFx.cube(blocked ? 14 + rnd.nextDouble() * 4 : 0.001, t * 0.3, -t * 0.2));
        if (t % 3 == 0) Holo.beamGlow(level, own(m), e, stop, 26, 0x40C8FF, 0, 2, 2);
        if (t % 5 == 0) {
            double dd = (t * 9) % Math.max(10, len);
            Holo.ringT(level, own(m), e.add(dir.scale(dd)), 4, 14, 22, 0.4, 0.06, 0xFFFFFF, 2, 0, 4, Holo.facing(dir), 0, true);
        }
        if (blocked && t % 10 == 0) { Holo.decal(level, own(m), stop.add(0, 0.1, 0), "crack", 0x40C8FF, 2, 18, 2, 30, 12, 0, Holo.I()); Holo.shockTex(level, own(m), stop, 20, 0x40C8FF, 8); }
        Vec3 side = dir.cross(new Vec3(0, 1, 0)); if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0); side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (double d = 3; d < len; d += 3) {
            double ang = d * 0.1 + t * 0.5, r = 8;
            Vec3 b = e.add(dir.scale(d));
            level.sendParticles(CYAN, b.x + (side.x * Math.cos(ang) + up.x * Math.sin(ang)) * r, b.y + (side.y * Math.cos(ang) + up.y * Math.sin(ang)) * r,
                    b.z + (side.z * Math.cos(ang) + up.z * Math.sin(ang)) * r, 1, 0.3, 0.3, 0.3, 0);
        }
        if (t % 2 == 0) for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(e, stop).inflate(9), x -> foe(m, x))) {
            if (le.getBoundingBox().inflate(7).clip(e, stop).isEmpty()) continue;
            le.invulnerableTime = 0;
            le.hurtServer(level, src(level, m), 600f);
            le.igniteForSeconds(15);
            le.setDeltaMovement(dir.scale(2).add(0, 1, 0)); le.hurtMarked = true;
        }
        if (blocked) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y, stop.z, 3, 4, 3, 4, 0);
            level.sendParticles(ParticleTypes.LAVA, stop.x, stop.y, stop.z, 10, 4, 2, 4, 0);
            if (t % 4 == 0) level.explode(owner(m), stop.x, stop.y, stop.z, 12f, grief(level), ei(level));
            if (grief(level) && t % 2 == 0) Titan.carve(level, stop.add(dir.scale(2)), 6, rnd);
            if (t % 8 == 0) Laser.bolt(level, stop);
        }
        if (t % 4 == 0) level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 0.7f + rnd.nextFloat() * 0.3f);
        if (t % 10 == 0) Titan.shake(level, e, 150, false);
        if (m.laserT == 0) { killFx(level, m, LASER_FX); level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 6f, 0.6f); }
    }

    // ───────────── 8 대지 분쇄: 앞다리 들어올림(1초) → 내리찍기, 충격파 3겹
    static boolean slamStart(DevMachine m, ServerLevel level) {
        if (m.flying() || !m.onGround()) { msg(m, "§7땅 위에서만 가능"); return false; }
        m.slamT = 45;
        level.playSound(null, m.getX(), m.getY() + 10, m.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 5f, 0.4f);
        level.playSound(null, m.getX(), m.getY() + 10, m.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 5f, 0.3f);
        msg(m, "§6§l대지 분쇄!");
        return true;
    }

    static int slamTick(DevMachine m, ServerLevel level) {
        int k = 45 - --m.slamT;
        Vec3 c = m.position().add(m.tf().scale(10));
        if (k < 20) {
            if (k % 4 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 8, c.z, 20, 6, 4, 6, 0.3);
            return 3;
        }
        if (k == 20) {
            BlockState gs = Slam.ground(level, c);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 10f, 0.3f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 10f, 0.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.4f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 10, 8, 1, 8, 0);
            Slam.debris(level, c, gs, 80);
            Holo.explosion(level, own(m), c, 6, 0xC08040);
            Holo.shockTex(level, own(m), c.add(0, 0.3, 0), 60, 0xFFB060, 16);
            Holo.decal(level, own(m), c.add(0, 0.08, 0), "crack", 0x502010, 3, 40, 2, 60, 20, 0, Holo.I());
            Holo.shards(level, own(m), c, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(gs.getBlock()).toString(), 30, 9);
            Holo.punch(level, c, 9, 0xC08040);
            Holo.sfx(level, c, "boom", 3f, 0.5f); Holo.sfx(level, c, "impact", 3f, 0.6f);
            if (grief(level)) crater(level, c, 9);
            Titan.shake(level, c, 160, true);
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] add gojo.fxflash_w", c.x, c.y, c.z));
        }
        if (k >= 20 && k <= 40 && k % 2 == 0) {   // 3겹 충격파가 밖으로 퍼짐
            double r = (k - 20) * 2.6;
            BlockState gs = Slam.ground(level, c);
            for (int ring = 0; ring < 3; ring++) {
                double rr = Math.max(1, r - ring * 6);
                Slam.dustRing(level, c, rr, gs);
                Laser.ring(level, c.add(0, 0.6, 0), rr);
            }
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 2, 8, r + 2), e -> foe(m, e))) {
                double d = Math.hypot(le.getX() - c.x, le.getZ() - c.z);
                if (d > r + 2 || d < r - 6) continue;
                le.invulnerableTime = 0;
                le.hurtServer(level, src(level, m), 140f);
                Vec3 out = new Vec3(le.getX() - c.x, 0, le.getZ() - c.z).normalize();
                le.setDeltaMovement(out.scale(2.6).add(0, 1.6, 0)); le.hurtMarked = true;
            }
            if (k % 6 == 0 && grief(level)) for (int i = 0; i < 6; i++) {   // 갈라지는 땅: 고리 위 블록 튀어오름
                double ang = level.random.nextDouble() * Math.PI * 2;
                BlockPos bp = BlockPos.containing(c.x + Math.cos(ang) * r, c.y - 1, c.z + Math.sin(ang) * r);
                BlockState st = level.getBlockState(bp);
                if (!st.isAir() && st.getDestroySpeed(level, bp) >= 0 && st.getDestroySpeed(level, bp) < 50) {
                    level.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
                    MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                            "summon falling_block %d %d %d {BlockState:{Name:\"%s\"},Time:1,DropItem:0b,Motion:[%.2f,%.2f,%.2f]}",
                            bp.getX(), bp.getY() + 1, bp.getZ(), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()),
                            Math.cos(ang) * 0.4, 0.9 + level.random.nextDouble() * 0.5, Math.sin(ang) * 0.4));
                }
            }
        }
        return k < 30 ? 4 : 0;
    }

    static void crater(ServerLevel level, Vec3 c, double r) {
        BlockPos o = BlockPos.containing(c);
        RandomSource rnd = level.random;
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) for (int y = -R / 2; y <= 2; y++) {
            double d = Math.sqrt(x * x + z * z + y * y * 4);
            if (d > r) continue;
            BlockPos bp = o.offset(x, y, z);
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || st.getDestroySpeed(level, bp) < 0) continue;
            level.setBlock(bp, d > r - 1.5 && rnd.nextFloat() < 0.4f ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // ───────────── 9 EMP 펄스: 반경 48 적 마비 + 투사체 소멸
    static boolean emp(DevMachine m, ServerLevel level) {
        Vec3 c = m.chest();
        RandomSource rnd = level.random;
        for (int i = 0; i < 6; i++) Laser.ring(level, m.position().add(0, 1, 0), 8 + i * 8);
        for (int i = 0; i < 400; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, b = Math.asin(rnd.nextDouble() * 2 - 1);
            Vec3 v = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
            level.sendParticles(i % 2 == 0 ? EMP : ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 0, v.x, v.y, v.z, 2.4);
        }
        for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3; Laser.bolt(level, m.position().add(Math.cos(a) * 24, 0, Math.sin(a) * 24)); }
        int n = 0;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(48), e -> foe(m, e) && !(e instanceof Player))) {
            if (le.distanceTo(m) > 52) continue;
            le.invulnerableTime = 0;
            le.hurtServer(level, src(level, m), 40f);
            le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 9));
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 4));
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
            le.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 4));
            if (le instanceof net.minecraft.world.entity.Mob mob) mob.setTarget(null);
            Vec3 q = le.position().add(0, le.getBbHeight() / 2, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 20, 0.5, 0.6, 0.5, 0.3);
            n++;
        }
        for (Projectile pr : level.getEntitiesOfClass(Projectile.class, m.getBoundingBox().inflate(48))) {
            level.sendParticles(ParticleTypes.POOF, pr.getX(), pr.getY(), pr.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            pr.discard();
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 8f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 8f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 3f, 1.5f);
        Titan.shake(level, c, 80, false);
        Holo.shockTex(level, own(m), m.position().add(0, 1, 0), 56, 0x9AA8FF, 14);
        for (int i = 0; i < 3; i++) Holo.ringT(level, own(m), c, 2, 50 - i * 10, 40, 0.8, 0.1, i == 1 ? 0xFFFFFF : 0x9AA8FF, 6 + i * 2, 0, 6, Holo.rotX(i * 1.05), 0, true);
        Holo.flare(level, own(m), c, 40, 0x9AA8FF, 10);
        Holo.screen(level, c, 70, 4, 0, 900, 1f);
        Holo.sfx(level, c, "glitch", 3f, 0.6f); Holo.sfx(level, c, "boom", 2f, 1.2f);
        msg(m, "§9§lEMP 펄스! §7적 " + n + "체 마비");
        return true;
    }

    // ───────────── 10 전투 드론 6기 (30초)
    static boolean drones(DevMachine m, ServerLevel level) {
        int have = 0;
        for (Drone d : DRONES) if (d.machine.equals(m.getUUID())) have++;
        int add = Math.min(6, 12 - have);
        if (add <= 0) { msg(m, "§7드론이 이미 최대 (12기)"); return false; }
        Vec3 bay = m.at(0, (97 + 60) * DevMachine.S, -40 * DevMachine.S);
        for (int i = 0; i < add; i++) DRONES.add(new Drone(level, m, bay, have + i));
        level.playSound(null, bay.x, bay.y, bay.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 4f, 0.5f);
        level.playSound(null, bay.x, bay.y, bay.z, SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 4f, 1.2f);
        level.sendParticles(ParticleTypes.CLOUD, bay.x, bay.y, bay.z, 30, 1, 1, 1, 0.1);
        msg(m, "§a§l전투 드론 " + add + "기 출격!");
        return true;
    }

    static class Drone {
        static final int LIFE = 600;
        static int counter = 0;
        final ServerLevel level; final UUID machine; final String tag; final int idx;
        Vec3 pos; int life; boolean dead, spawned; LivingEntity target;
        Drone(ServerLevel level, DevMachine m, Vec3 at, int idx) {
            this.level = level; this.machine = m.getUUID(); this.pos = at; this.idx = idx; this.tag = "jjk.drone" + (counter++);
        }
        void tick() {
            if (!(level.getEntity(machine) instanceof DevMachine m) || !m.isAlive() || ++life > LIFE) { boom(); return; }
            if (!spawned) {
                spawned = true;
                MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                        "summon item_display %.2f %.2f %.2f {Tags:[\"jjk.mechpart\",\"%s\"],item:{id:\"minecraft:observer\",count:1},teleport_duration:2,view_range:6f,"
                                + "brightness:{sky:15,block:15},Glowing:1b,glow_color_override:7405456,"
                                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.6f,0.7f,1.6f]}}",
                        pos.x, pos.y, pos.z, tag));
            }
            if (target == null || !target.isAlive() || target.distanceToSqr(pos) > 80 * 80 || life % 20 == 0) {
                target = null; double bd = 64 * 64;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(m.blockPosition()).inflate(64),
                        e -> (e instanceof Enemy || (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == m.getControllingPassenger() && mob.getTarget() != null)) && foe(m, e))) {
                    double d = e.distanceToSqr(pos);
                    if (d < bd) { bd = d; target = e; }
                }
            }
            Vec3 want;
            double ang = life * 0.07 + idx * Math.PI * 2 / 6;
            if (target != null) {
                Vec3 tp = target.position();
                want = tp.add(Math.cos(ang) * 7, 6 + Math.sin(life * 0.2 + idx) * 1.5, Math.sin(ang) * 7);
            } else {
                Vec3 c = m.position();
                double r = 14 + (idx % 2) * 5;
                want = c.add(Math.cos(ang) * r, 30 + Math.sin(life * 0.1 + idx) * 3, Math.sin(ang) * r);
            }
            Vec3 step = want.subtract(pos);
            if (step.length() > 2.2) step = step.normalize().scale(2.2);
            pos = pos.add(step);
            level.sendParticles(GREEN, pos.x, pos.y - 0.4, pos.z, 1, 0.3, 0, 0.3, 0);
            if (life % 3 == 0) level.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y - 0.5, pos.z, 1, 0.1, 0, 0.1, 0.01);
            float yaw = (float) (life * 18 % 360);
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tp @e[tag=%s,limit=1] %.2f %.2f %.2f %.1f 0", tag, pos.x, pos.y, pos.z, yaw));
            if (target != null && life % 4 == idx % 4) {
                Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
                Vec3 dir = to.subtract(pos).normalize();
                hitscan(m, level, pos, dir, 0.02, 80, 10f, true, GREEN);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.02);
                if (life % 8 == 0) level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1f, 1.8f);
            }
        }
        void boom() {
            dead = true;
            discard();
            if (spawned) {
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0);
                level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 20, 0.2, 0.2, 0.2, 0.2);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5f, 1.2f);
            }
        }
        void discard() { MechManager.cmd(level.getServer(), level, "kill @e[tag=" + tag + "]"); }
    }

    // ───────────── 11 플라즈마 대검: 오른팔에 길이 36 에너지 칼날 → 140° 수평 참격
    static final int BLADE_TIME = 22;
    static boolean bladeStart(DevMachine m, ServerLevel level) {
        m.bladeT = BLADE_TIME;
        Vec3 a = aim(m), palm = m.palm(a);
        Holo.glow(level, own(m), palm, 3, 0xFF40C0, 4, 10, 4);
        Holo.beamLayer(level, own(m), palm, palm.add(a.scale(36)), 1.6, "minecraft:white_concrete", 0xFF40C0, 8, 6, 3, 0);
        Holo.beamLayer(level, own(m), palm, palm.add(a.scale(36)), 3.6, "minecraft:magenta_stained_glass", 0xFF40C0, 8, 6, 3, 0.7);
        Holo.sfx(level, palm, "charge", 2f, 1.4f);
        msg(m, "§d§l플라즈마 대검");
        return true;
    }
    static void bladeTick(DevMachine m, ServerLevel level) {
        int k = BLADE_TIME - --m.bladeT;
        if (k != 12) return;
        Vec3 f = m.tf(), c = m.position().add(0, 9, 0);
        double af = Math.atan2(f.z, f.x), R = 38;
        Holo.arc(level, own(m), c, R, af + 1.25, af - 1.25, Holo.I(), 0xFF40C0, 5, 8);
        Holo.arc(level, own(m), c.add(0, 1.5, 0), R * 0.8, af + 1.1, af - 1.1, Holo.I(), 0xFFFFFF, 3, 6);
        Holo.arc(level, own(m), c.add(0, -1.5, 0), R * 1.1, af + 1.3, af - 1.3, Holo.I(), 0xB040FF, 2, 10);
        Holo.flare(level, own(m), c.add(f.scale(R * 0.6)), 24, 0xFF40C0, 8);
        Holo.punch(level, c, 7, 0xFF40C0);
        Holo.sfx(level, c, "slash", 3f, 0.5f); Holo.sfx(level, c, "whoosh", 3f, 0.4f); Holo.sfx(level, c, "impact", 2f, 1.2f);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(R + 4, 12, R + 4), e -> foe(m, e))) {
            Vec3 d = le.position().subtract(m.position()); Vec3 dh = new Vec3(d.x, 0, d.z);
            if (dh.length() > R + 3 || dh.normalize().dot(f) < Math.cos(1.3)) continue;
            le.invulnerableTime = 0;
            le.hurtServer(level, src(level, m), 900f);
            le.setDeltaMovement(dh.normalize().scale(3).add(0, 1.2, 0)); le.hurtMarked = true;
            Holo.slash(level, own(m), le.position().add(0, le.getBbHeight() / 2, 0), 2 + le.getBbWidth(), Holo.facing(new Vec3(f.z, 0.3, -f.x)), 0xFF40C0, 6);
        }
        if (grief(level)) for (int i = -6; i <= 6; i++) {
            double an = af + i * 0.2;
            Titan.carve(level, c.add(Math.cos(an) * R * 0.85, -2, Math.sin(an) * R * 0.85), 3, level.random);
        }
    }

    // ───────────── 12 하드라이트 방어막: 8초간 육각 방어막 — 모든 피해 무효 + 투사체 반사
    static boolean shieldStart(DevMachine m, ServerLevel level) {
        m.shieldT = 160;
        Holo.bubble(level, own(m), () -> m.position().add(0, 11, 0), 30, 0x60C0FF, 160);
        Holo.flare(level, own(m), m.halo(), 26, 0x60C0FF, 10);
        Holo.shockTex(level, own(m), m.position().add(0, 0.5, 0), 34, 0x60C0FF, 10);
        Holo.sfx(level, m.chest(), "magic", 2.5f, 0.7f); Holo.sfx(level, m.chest(), "plate", 2f, 0.6f);
        msg(m, "§b§l하드라이트 방어막 §7— 8초간 모든 피해 무효 · 투사체 반사");
        return true;
    }
    static void shieldTick(DevMachine m, ServerLevel level) {
        m.shieldT--;
        Vec3 c = m.position().add(0, 11, 0);
        for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(32), pr -> pr.getOwner() != m.getControllingPassenger())) {
            if (pr.position().distanceTo(c) > 31) continue;
            pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.4)); pr.setOwner(m.getControllingPassenger());
            Holo.flare(level, own(m), pr.position(), 3, 0x60C0FF, 4);
        }
        if (m.shieldT % 20 == 0) Holo.sfx(level, c, "magic", 0.8f, 1.6f);
        if (m.shieldT == 0) {
            Holo.shards(level, own(m), c, "minecraft:light_blue_stained_glass", 30, 8);
            Holo.shockTex(level, own(m), c, 36, 0x60C0FF, 8);
            Holo.sfx(level, c, "shatter", 2.5f, 0.8f);
            msg(m, "§7방어막 해제");
        }
    }
    /** 방어막에 막힌 피해: 맞은 방향에 육각 섬광. */
    static void shieldHit(DevMachine m, ServerLevel level, DamageSource src, float amount) {
        Vec3 c = m.position().add(0, 11, 0);
        Vec3 from = src.getSourcePosition() != null ? src.getSourcePosition() : c.add(m.tf().scale(10));
        Vec3 d = from.subtract(c); if (d.lengthSqr() < 1e-3) d = new Vec3(0, 1, 0);
        Vec3 hit = c.add(d.normalize().scale(30));
        Holo.sprite(level, own(m), hit, "hex", 0x60C0FF, "fixed", Holo.m(Holo.mul(Holo.facing(d), Holo.FLAT), 2, 2, 2, Vec3.ZERO, false), 8, 0)
                .key(1, Holo.m(Holo.mul(Holo.facing(d), Holo.FLAT), 9, 9, 9, Vec3.ZERO, false), 2).key(3, Holo.m(Holo.mul(Holo.facing(d), Holo.FLAT), 0.1, 0.1, 0.1, Vec3.ZERO, false), 5);
        Holo.sfx(level, hit, "plate", 1.2f, 1.4f + level.random.nextFloat() * 0.3f);
    }

    // ───────────── 13 궤도 지원 포격: 목표에 표식 → 1.5초 뒤 하늘에서 붉은 궤도 광선 2초간
    static final int ORB_TIME = 80;
    static boolean orbitalStart(DevMachine m, ServerLevel level) {
        Vec3 t = target(level, eye(m), aim(m), 500);
        m.orbitalAt = t; m.orbitalT = ORB_TIME;
        Holo.circle(level, own(m), t, 14, 0xFF3030, ORB_TIME, 0);
        Holo.circle(level, own(m), t.add(0, 0.2, 0), 8, 0xFFFFFF, ORB_TIME, 0);
        Holo.ringT(level, own(m), t.add(0, 60, 0), 20, 2, 30, 0.6, 0.1, 0xFF3030, 30, 0, 4, Holo.I(), 8, true);
        Holo.banner(level, own(m), t.add(0, 8, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⌖ ORBITAL STRIKE", 0xFF3030, true) + "]}", 4, 32);
        Holo.sfx(level, t, "siren", 2f, 1.2f);
        msg(m, "§c§l궤도 지원 포격 §7— 좌표 전송");
        return true;
    }
    static void orbitalTick(DevMachine m, ServerLevel level) {
        int k = ORB_TIME - --m.orbitalT;
        Vec3 t = m.orbitalAt; if (t == null) return;
        if (k == 30) {
            Holo.beam(level, own(m), t.add(0, 200, 0), t, 6, 0xFF3030, 2, 40, 8);
            Holo.beamGlow(level, own(m), t.add(0, 200, 0), t, 22, 0xFF3030, 2, 40, 8);
            Holo.flare(level, own(m), t, 40, 0xFFFFFF, 10);
            Holo.punch(level, t, 8, 0xFF3030);
            Holo.sfx(level, t, "laser", 3f, 0.5f); Holo.sfx(level, t, "boom", 3f, 0.6f);
        }
        if (k > 30 && k < 72) {
            if (k % 4 == 0) {
                Holo.shockTex(level, own(m), t.add(0, 0.5, 0), 18, 0xFF3030, 6);
                level.explode(owner(m), t.x, t.y, t.z, 7f, grief(level), ei(level));
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(t, t).inflate(14, 40, 14), e -> foe(m, e))) {
                    le.invulnerableTime = 0; le.hurtServer(level, src(level, m), 260f); le.igniteForSeconds(10);
                }
            }
            if (k % 2 == 0) level.sendParticles(ParticleTypes.LAVA, t.x, t.y + 1, t.z, 6, 6, 1, 6, 0);
        }
        if (k == 72) {
            Holo.explosion(level, own(m), t, 9, 0xFF3030);
            Holo.decal(level, own(m), t.add(0, 0.1, 0), "crack", 0xFF3030, 3, 30, 2, 50, 20, 0, Holo.I());
            Titan.shake(level, t, 120, true);
            if (grief(level)) Titan.carve(level, t, 9, level.random);
        }
    }

    // ───────────── X 코어 과부하
    static int ultTick(DevMachine m, ServerLevel level) {
        int k = ULT_CHARGE + ULT_TIME - --m.ultT;
        Vec3 e = m.chest();
        RandomSource rnd = level.random;
        if (k <= ULT_CHARGE) {   // 충전: 붉은 코어 + 주변이 빨려듦 + 다리가 땅을 움켜쥠
            double f = k / (double) ULT_CHARGE, fl = 1 + Math.sin(k * 1.3) * 0.12;
            LaserFx.set(level, m, "Uo1", e, LaserFx.cube((2 + 7 * f) * fl, k * 0.35, k * 0.2));
            LaserFx.set(level, m, "Uo2", e, LaserFx.cube((4 + 12 * f) * fl, -k * 0.2, k * 0.3));
            LaserFx.set(level, m, "Uo3", e, LaserFx.cube((6 + 18 * f) / fl, k * 0.12, -k * 0.25));
            for (int i = 0; i < 30 + (int) (f * 50); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI - Math.PI / 2, rr = 20 + rnd.nextDouble() * 50;
                Vec3 o = e.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
                Vec3 v = e.subtract(o).scale(0.12);
                level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : i % 3 == 1 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
            }
            if (k % 4 == 0) Laser.ring(level, m.position().add(0, 0.5, 0), 8 + f * 40);
            if (k % 6 == 0) { double a = rnd.nextDouble() * Math.PI * 2, r = 12 + rnd.nextDouble() * 30; Laser.bolt(level, m.position().add(Math.cos(a) * r, 0, Math.sin(a) * r)); }
            if (k % 10 == 0) level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 10f, 0.5f + (float) f);
            if (k % 20 == 0) { Titan.shake(level, e, 150, false); level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 10f, 0.6f); }
            if (k == ULT_CHARGE) {
                LaserFx.spawn(level, m, ORBITAL, e);
                m.triggerAnim("main", "recoil");
                level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 12f, 0.4f);
                level.playSound(null, e.x, e.y, e.z, SoundEvents.END_GATEWAY_SPAWN, SoundSource.PLAYERS, 12f, 0.5f);
                level.playSound(null, e.x, e.y, e.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 12f, 0.4f);
                for (int i = 0; i < 5; i++) Laser.ring(level, m.position().add(0, 1, 0), 12 + i * 12);
                Titan.shake(level, e, 400, true);
                MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..300] add gojo.fxflash_w", e.x, e.y, e.z));
                missiles(m, level);
                Holo.flare(level, own(m), e, 70, 0xFFFFFF, 14);
                Holo.shockTex(level, own(m), m.position().add(0, 1, 0), 90, 0xFF3030, 14);
                Holo.punch(level, e, 10, 0xFF3030);
                Holo.sfx(level, e, "boom", 3f, 0.5f); Holo.sfx(level, e, "laser", 3f, 0.4f);
            }
            if (k % 3 == 0) Holo.glow(level, own(m), e, 3 + k * 0.15, 0xFF3030, 1, 1, 2);
            return 1;
        }
        int t = k - ULT_CHARGE;
        Vec3 a = aim(m), tgt = target(level, eye(m), a, 500), dir = tgt.subtract(e).normalize();
        BlockHitResult hit = level.clip(new ClipContext(e, e.add(dir.scale(500)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 stop = blocked ? hit.getLocation() : e.add(dir.scale(500));
        double len = stop.distanceTo(e), pulse = 1 + Math.sin(t * 0.8) * 0.15, fl = 1 + (rnd.nextDouble() - 0.5) * 0.15;
        // 붉은 5겹 빔
        LaserFx.set(level, m, "U0", e, LaserFx.beam(dir, len, 5 * pulse * fl, t * 0.3));
        LaserFx.set(level, m, "U1", e, LaserFx.beam(dir, len, 10 * pulse, -t * 0.18));
        LaserFx.set(level, m, "U2", e, LaserFx.beam(dir, len, 16 * (2 - pulse), t * 0.12));
        LaserFx.set(level, m, "U3", e, LaserFx.beam(dir, len, 22 * fl, -t * 0.08));
        LaserFx.set(level, m, "U4", e, LaserFx.beam(dir, len, 28 * (2 - pulse) * fl, t * 0.05));
        LaserFx.set(level, m, "Uo1", e, LaserFx.cube(9 * fl, t * 0.4, t * 0.3));
        LaserFx.set(level, m, "Uo2", e, LaserFx.cube(15 * pulse, -t * 0.3, t * 0.5));
        LaserFx.set(level, m, "Uo3", e, LaserFx.cube(21 / pulse, t * 0.2, -t * 0.4));
        double hs = blocked ? 1 : 0.001;
        LaserFx.set(level, m, "Uh1", stop, LaserFx.cube((26 + rnd.nextDouble() * 6) * hs, t * 0.5, t * 0.3));
        LaserFx.set(level, m, "Uh2", stop, LaserFx.cube((38 + Math.sin(t * 0.6) * 6) * hs, -t * 0.3, t * 0.2));
        LaserFx.set(level, m, "Uh3", stop, LaserFx.cube((50 + Math.sin(t * 0.4) * 8) * hs, t * 0.15, -t * 0.25));
        if (t % 3 == 0) Holo.beamGlow(level, own(m), e, stop, 60, 0xFF3030, 0, 2, 2);
        if (t % 4 == 0) Holo.ringT(level, own(m), e.add(dir.scale((t * 13) % Math.max(10, len))), 10, 30, 28, 0.6, 0.08, t % 8 == 0 ? 0xFFFFFF : 0xFFB040, 2, 0, 4, Holo.facing(dir), 0, true);
        if (blocked && t % 8 == 0) { Holo.shockTex(level, own(m), stop, 46, 0xFF3030, 10); Holo.decal(level, own(m), stop.add(0, 0.1, 0), "crack", 0xFF3030, 4, 50, 2, 40, 16, 0, Holo.I()); }
        // 궤도 폭격: 하늘에서 떨어지는 6줄기 붉은 빔이 착탄점 주위를 돎
        for (int i = 0; i < 6; i++) {
            double an = t * 0.08 + i * Math.PI / 3, r = 22 + Math.sin(t * 0.1 + i) * 8;
            Vec3 g = stop.add(Math.cos(an) * r, 0, Math.sin(an) * r);
            Vec3 top = g.add(0, 120, 0);
            LaserFx.set(level, m, "O" + i, top, LaserFx.beam(new Vec3(0, -1, 0), 120, 3.5 * fl, t * 0.2));
            if (t % 3 == i % 3) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, g.x, g.y, g.z, 1, 1, 0, 1, 0);
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(g, g).inflate(5, 60, 5), x -> foe(m, x))) {
                    le.invulnerableTime = 0; le.hurtServer(level, src(level, m), 400f); le.igniteForSeconds(10);
                }
                if (t % 6 == i % 6) level.explode(owner(m), g.x, g.y, g.z, 8f, grief(level), ei(level));
            }
        }
        // 동시에: 개틀링·화염·쌍열 주포 자동 연사
        Vec3 gm = m.gatMuzzle(a);
        for (int b = 0; b < 3; b++) hitscan(m, level, gm, tgt.subtract(gm).normalize(), 0.06, 200, 20f, b == 0, Minigun.TRACER);
        if (t % 3 == 0) flame(m, level);
        if (t % 6 == 0) twin(m, level, a, (t / 6) % 2 == 0 ? -1 : 1);
        if (t == 40 || t == 80) missiles(m, level);
        // 나선·번개
        Vec3 side = dir.cross(new Vec3(0, 1, 0)); if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0); side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (double d = 4; d < len; d += 4) {
            double ang = d * 0.08 + t * 0.5, r = 18;
            Vec3 b = e.add(dir.scale(d));
            level.sendParticles(RED, b.x + (side.x * Math.cos(ang) + up.x * Math.sin(ang)) * r, b.y + (side.y * Math.cos(ang) + up.y * Math.sin(ang)) * r,
                    b.z + (side.z * Math.cos(ang) + up.z * Math.sin(ang)) * r, 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(GOLD, b.x - (side.x * Math.cos(ang) + up.x * Math.sin(ang)) * r, b.y - (side.y * Math.cos(ang) + up.y * Math.sin(ang)) * r,
                    b.z - (side.z * Math.cos(ang) + up.z * Math.sin(ang)) * r, 2, 0.5, 0.5, 0.5, 0);
        }
        if (t % 2 == 0) for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(e, stop).inflate(16), x -> foe(m, x))) {
            if (le.getBoundingBox().inflate(14).clip(e, stop).isEmpty()) continue;
            le.invulnerableTime = 0;
            le.hurtServer(level, src(level, m), 3000f);
            le.igniteForSeconds(20);
            le.setDeltaMovement(dir.scale(3).add(0, 1.5, 0)); le.hurtMarked = true;
        }
        if (blocked) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y, stop.z, 6, 8, 5, 8, 0);
            level.sendParticles(ParticleTypes.LAVA, stop.x, stop.y, stop.z, 24, 8, 3, 8, 0);
            if (t % 4 == 0) level.explode(owner(m), stop.x, stop.y, stop.z, 18f, grief(level), ei(level));
            if (grief(level) && t % 2 == 0) Titan.carve(level, stop.add(dir.scale(3)), 9, rnd);
            if (t % 5 == 0) Laser.bolt(level, stop.add((rnd.nextDouble() - 0.5) * 30, 0, (rnd.nextDouble() - 0.5) * 30));
        }
        if (t % 4 == 0) level.playSound(null, e.x, e.y, e.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 10f, 0.4f + rnd.nextFloat() * 0.3f);
        if (t % 3 == 0) level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 10f, 0.4f);
        if (t % 10 == 0) Titan.shake(level, e, 400, true);
        if (m.ultT == 0) {   // 마무리: 착탄점 핵폭발
            killFx(level, m, ULT_FX); killFx(level, m, ORBITAL);
            level.playSound(null, e.x, e.y, e.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 10f, 0.4f);
            if (blocked) {
                var lt = ei(level);
                level.explode(owner(m), stop.x, stop.y, stop.z, 36f, grief(level), lt);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4;
                    level.explode(owner(m), stop.x + Math.cos(ang) * 22, stop.y, stop.z + Math.sin(ang) * 22, 16f, grief(level), lt);
                    Laser.bolt(level, stop.add(Math.cos(ang) * 18, 0, Math.sin(ang) * 18));
                }
                if (grief(level)) Titan.carve(level, stop, 16, rnd);
                for (int i = 0; i < 6; i++) Laser.ring(level, stop.add(0, 1, 0), 10 + i * 10);
                for (int i = 0; i < 25; i++) level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, stop.x, stop.y + i * 3, stop.z, 2, 3, 1, 3, 0);
                for (int i = 0; i < 60; i++) {
                    double ang = i / 60.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.EXPLOSION, stop.x + Math.cos(ang) * 26, stop.y + 75, stop.z + Math.sin(ang) * 26, 3, 5, 3, 5, 0);
                    level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, stop.x + Math.cos(ang) * 22, stop.y + 70, stop.z + Math.sin(ang) * 22, 2, 4, 3, 4, 0.02);
                }
                level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 16f, 0.3f);
                Titan.shake(level, stop, 500, true);
                Holo.explosion(level, own(m), stop, 14, 0xFF3030);
                Holo.shockTex(level, own(m), stop.add(0, 1, 0), 120, 0xFF3030, 20);
                Holo.pillar(level, own(m), stop, 150, 14, 0xFF6020, 3, 16, 16);
                Holo.flare(level, own(m), stop, 90, 0xFFFFFF, 18);
                Holo.punch(level, stop, 12, 0xFF3030);
                Holo.banner(level, own(m), stop.add(0, 30, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("OVERLOAD · TERMINATED", 0xFF3030, true) + "]}", 10, 50);
                Holo.sfx(level, stop, "boom", 3f, 0.35f); Holo.sfx(level, stop, "choir", 2.5f, 0.5f);
            }
            if (m.weatherSet) { MechManager.cmd(level.getServer(), level, "weather clear"); m.weatherSet = false; }
            msg(m, "§7코어 냉각 중...");
        }
        return 2;
    }

    // ───────────── HUD 상태 (Status 패킷 쿨다운 목록에 실어 보냄)
    /** [무기 선택, 열%, 과열(0/1), 궁극 쿨, 포탑(0/1), 비행(0/1), 드론 수, 각 무기 쿨...] */
    public static List<Integer> hud(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMachine m)) return null;
        List<Integer> l = new ArrayList<>();
        int drones = 0;
        for (Drone d : DRONES) if (d.machine.equals(m.getUUID())) drones++;
        l.add(m.weapon()); l.add(Math.min(100, m.gunHeat * 100 / MAX_HEAT)); l.add(m.overheat > 0 ? 1 : 0);
        l.add(m.ultT > 0 ? -1 : m.ultCd); l.add(m.autoTurret ? 1 : 0); l.add(m.flying() ? 1 : 0); l.add(drones);
        for (int c : m.cd) l.add(c);
        l.add(m.shieldT);
        return l;
    }
}
