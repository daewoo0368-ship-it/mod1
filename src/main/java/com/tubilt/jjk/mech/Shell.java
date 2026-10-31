package com.tubilt.jjk.mech;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Locale;
import java.util.UUID;

/** 탑승물2 포탄: 빠르게 직진(약간 낙하), 닿으면 폭발. heavy = 중포탄. */
public class Shell {
    static final DustParticleOptions HOT = new DustParticleOptions(0xFF6A20, 2.4f);
    static final DustParticleOptions WHITE = new DustParticleOptions(0xFFF4D0, 1.8f);
    static final DustParticleOptions BLUE = new DustParticleOptions(0x40E8FF, 3.6f);

    final ServerLevel level;
    final UUID owner, vehicle;
    Vec3 pos, vel;
    final boolean heavy;
    int life = 0;
    boolean dead = false;

    Shell(ServerLevel level, UUID owner, UUID vehicle, Vec3 pos, Vec3 vel, boolean heavy) {
        this.level = level; this.owner = owner; this.vehicle = vehicle; this.pos = pos; this.vel = vel; this.heavy = heavy;
    }

    void tick(MinecraftServer server) {
        life++;
        vel = vel.add(0, heavy ? -0.015 : -0.03, 0);
        Vec3 next = pos.add(vel);
        BlockHitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 stop = hit.getType() == HitResult.Type.MISS ? next : hit.getLocation();
        // 생물 명중
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, stop).inflate(heavy ? 2.0 : 1.0),
                e -> e.isAlive() && !e.getUUID().equals(owner) && !e.getUUID().equals(vehicle)
                        && (e.getVehicle() == null || !e.getVehicle().getUUID().equals(vehicle)))) {
            var h = le.getBoundingBox().inflate(heavy ? 1.2 : 0.4).clip(pos, stop);
            if (h.isPresent()) { explode(h.get()); return; }
        }
        // 궤적
        for (int i = 0; i < 4; i++) {
            Vec3 q = pos.add(vel.scale(i / 4.0));
            if (heavy) {
                level.sendParticles(BLUE, q.x, q.y, q.z, 2, 0.25, 0.25, 0.25, 0);
                level.sendParticles(WHITE, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.3, 0.3, 0.3, 0.1);
            } else {
                level.sendParticles(HOT, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
                level.sendParticles(WHITE, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
            }
        }
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
        if (hit.getType() != HitResult.Type.MISS || life > 100) { explode(stop); return; }
        pos = next;
    }

    void explode(Vec3 at) {
        dead = true;
        boolean grief = MechManager.griefOn(level.getServer());
        Entity src = level.getEntity(owner);
        float power = heavy ? 10f : 4f;
        level.explode(src, at.x, at.y, at.z, power, false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, heavy ? 4 : 1, heavy ? 2 : 0, heavy ? 1 : 0, heavy ? 2 : 0, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, heavy ? 80 : 30, heavy ? 2 : 0.8, heavy ? 1.5 : 0.6, heavy ? 2 : 0.8, 0.15);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, heavy ? 16 : 5, 1, 0.5, 1, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 1, at.z, heavy ? 30 : 8, heavy ? 2.5 : 1, 1, heavy ? 2.5 : 1, 0.04);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3f, heavy ? 0.4f : 0.7f);
        if (heavy) {
            level.sendParticles(BLUE, at.x, at.y, at.z, 60, 3, 2, 3, 0);
            for (int i = 0; i < 3; i++) Laser.ring(level, at, 4 + i * 4);
            Laser.bolt(level, at);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5f, 0.6f);
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                    "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..48] add gojo.fxshake", at.x, at.y, at.z));
        } else {
            MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT,
                    "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..18] add gojo.fxshake_s", at.x, at.y, at.z));
        }
    }
}
