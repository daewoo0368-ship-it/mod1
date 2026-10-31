package com.tubilt.jjk.mech;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;

/**
 * 보스 효과음: 바닐라 사운드 여러 겹을 피치 조절해 쌓은 프리셋.
 *  (예전 jjk:fx.* 합성음 대신 — 워든·드래곤·위더·삼지창·철퇴 등 묵직한 원본 소리 위주)
 *  각 줄 = {사운드 ID, 볼륨 배율, 피치 배율}
 */
public final class BossSfx {
    private BossSfx() {}

    static final Map<String, Object[][]> P = Map.ofEntries(
            Map.entry("boom", new Object[][]{{"entity.generic.explode", 1.0, 0.7}, {"entity.dragon_fireball.explode", 0.8, 0.8}, {"item.mace.smash_ground_heavy", 1.0, 0.7}, {"entity.warden.sonic_boom", 0.5, 0.6}}),
            Map.entry("charge", new Object[][]{{"block.beacon.power_select", 0.9, 0.8}, {"block.respawn_anchor.charge", 1.0, 0.7}, {"item.trident.riptide_3", 0.6, 1.2}}),
            Map.entry("choir", new Object[][]{{"block.end_portal.spawn", 0.7, 1.4}, {"item.totem.use", 0.6, 0.8}, {"ui.toast.challenge_complete", 1.0, 1.0}}),
            Map.entry("data", new Object[][]{{"block.note_block.bit", 0.8, 1.6}, {"block.sculk_sensor.clicking", 1.0, 1.4}, {"block.note_block.bit", 0.6, 2.0}}),
            Map.entry("deep", new Object[][]{{"entity.warden.emerge", 0.9, 0.7}, {"entity.ender_dragon.growl", 0.6, 0.5}, {"block.respawn_anchor.deplete", 1.0, 0.5}}),
            Map.entry("drum", new Object[][]{{"entity.warden.heartbeat", 1.0, 0.8}, {"block.note_block.basedrum", 1.0, 0.6}}),
            Map.entry("glitch", new Object[][]{{"entity.enderman.teleport", 0.8, 0.6}, {"block.sculk_shrieker.shriek", 0.4, 1.6}, {"block.note_block.bit", 0.6, 0.5}}),
            Map.entry("laser", new Object[][]{{"entity.warden.sonic_boom", 0.9, 1.3}, {"block.beacon.deactivate", 1.0, 2.0}, {"item.trident.thunder", 0.4, 1.8}}),
            Map.entry("magic", new Object[][]{{"entity.illusioner.cast_spell", 1.0, 0.8}, {"block.enchantment_table.use", 1.0, 0.7}, {"block.amethyst_block.resonate", 0.8, 1.2}}),
            Map.entry("riser", new Object[][]{{"block.beacon.activate", 1.0, 0.6}, {"block.portal.trigger", 0.4, 1.6}, {"item.trident.riptide_3", 0.7, 0.6}}),
            Map.entry("shatter", new Object[][]{{"block.glass.break", 1.0, 0.6}, {"block.amethyst_cluster.break", 1.0, 0.7}, {"entity.zombie.break_wooden_door", 0.5, 1.2}}),
            Map.entry("stinger", new Object[][]{{"entity.ender_dragon.growl", 0.8, 0.9}, {"item.trident.thunder", 0.8, 0.8}, {"entity.wither.spawn", 0.35, 1.3}}),
            Map.entry("slash", new Object[][]{{"entity.player.attack.sweep", 1.0, 0.7}, {"item.trident.throw", 0.8, 1.4}, {"entity.player.attack.crit", 0.8, 1.0}}),
            Map.entry("whoosh", new Object[][]{{"entity.breeze.wind_burst", 0.8, 0.9}, {"item.elytra.flying", 0.3, 1.6}}),
            Map.entry("thunder", new Object[][]{{"entity.lightning_bolt.thunder", 0.6, 1.0}, {"entity.lightning_bolt.impact", 1.0, 0.8}}),
            Map.entry("siren", new Object[][]{{"block.bell.resonate", 1.0, 1.4}, {"block.note_block.bit", 0.8, 0.7}}),
            Map.entry("hit", new Object[][]{{"entity.player.attack.strong", 1.0, 0.8}, {"entity.player.hurt", 0.7, 0.7}, {"block.anvil.land", 0.12, 1.8}}),
            Map.entry("impact", new Object[][]{{"entity.generic.explode", 0.7, 1.1}, {"item.mace.smash_ground", 1.0, 0.9}, {"entity.player.attack.knockback", 0.8, 0.7}}),
            Map.entry("block", new Object[][]{{"item.shield.block", 1.0, 0.8}, {"block.anvil.place", 0.3, 1.6}}));

    public static void play(ServerLevel l, Vec3 at, String name, float vol, float pitch) {
        Object[][] layers = P.get(name);
        if (layers == null) { Holo.sfx(l, at, name, vol, pitch); return; }
        int range = (int) Math.max(48, vol * 24);
        for (Object[] ly : layers) {
            float v = (float) (vol * (double) ly[1]), pt = (float) Math.max(0.5, Math.min(2.0, pitch * (double) ly[2]));
            MechManager.cmd(l.getServer(), l, String.format(Locale.ROOT, "playsound minecraft:%s hostile @a[x=%.1f,y=%.1f,z=%.1f,distance=..%d] %.2f %.2f %.2f %.2f %.2f 0.25",
                    ly[0], at.x, at.y, at.z, range, at.x, at.y, at.z, v, pt));
        }
    }
}
