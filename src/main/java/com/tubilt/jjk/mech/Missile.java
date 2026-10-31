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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 탈것 미사일: 위로 솟구친 뒤 목표를 향해 휘어 날아가 폭발. */
public class Missile {
    static int counter = 0;
    static final DustParticleOptions TRAIL = new DustParticleOptions(0xFF7A1E, 1.6f);
    static final DustParticleOptions LOCK = new DustParticleOptions(0xFF2020, 1.4f);
    final String tag;
    final ServerLevel level;
    final UUID shooter;
    Vec3 pos, vel, target;
    Entity lock;
    int life = 0, delay;
    boolean dead = false, spawned = false;

    Missile(ServerLevel level, UUID shooter, Vec3 pos, Vec3 launch, Vec3 target, Entity lock, int delay) {
        this.level = level; this.shooter = shooter; this.pos = pos; this.vel = launch; this.target = target; this.lock = lock; this.delay = delay;
        this.tag = "jjk.mis" + (counter++);
    }

    void tick(MinecraftServer server) {
        if (delay > 0) { delay--; return; }
        if (!spawned) {
            spawned = true;
            MechManager.cmd(server, level, String.format(Locale.ROOT,
                    "summon item_display %.3f %.3f %.3f {Tags:[\"jjk.mechpart\",\"%s\"],item:{id:\"minecraft:firework_rocket\",count:1},teleport_duration:1,"
                            + "transformation:{left_rotation:[0.7071f,0f,0f,0.7071f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.8f,1.8f,1.8f]}}",
                    pos.x, pos.y, pos.z, tag));
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.5f, 0.6f);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.7f);
            // 포드 발사 섬광 + 뒤로 뿜는 연기
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 14, 0.25, 0.25, 0.25, 0.08);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y - 0.4, pos.z, 6, 0.3, 0.1, 0.3, 0.03);
            level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.15);
        }
        life++;
        if (lock != null && lock.isAlive()) target = lock.position().add(0, lock.getBbHeight() * 0.5, 0);
        if (life > 8) {
            Vec3 want = target.subtract(pos).normalize().scale(1.6);
            vel = vel.lerp(want, Math.min(0.35, 0.08 + life * 0.01));
        } else {
            vel = vel.add(0, -0.02, 0);
            double w = life * 0.9 + counter;   // 상승 중 살짝 흔들리며 솟구침
            vel = vel.add(Math.cos(w) * 0.04, 0, Math.sin(w) * 0.04);
        }
        // 락온 표시: 목표 위 빨간 고리
        if (life % 3 == 0 && life < 40) {
            for (int i = 0; i < 10; i++) {
                double a = i / 10.0 * Math.PI * 2 + life * 0.2;
                level.sendParticles(LOCK, target.x + Math.cos(a) * 1.6, target.y + 0.2, target.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
            }
        }
        Vec3 next = pos.add(vel);

        BlockHitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.MISS) { explode(server, hit.getLocation()); return; }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(1.4),
                e -> !e.getUUID().equals(shooter) && !(e instanceof DevVehicle) && !(e.getVehicle() instanceof DevVehicle));
        if (!near.isEmpty() || life > 120 || next.distanceTo(target) < 1.2) { explode(server, next); return; }

        pos = next;
        for (int i = 0; i < 3; i++) {   // 끊김 없는 꼬리: 이동 구간을 나눠서 채움
            double f = i / 3.0;
            double qx = pos.x - vel.x * f, qy = pos.y - vel.y * f, qz = pos.z - vel.z * f;
            level.sendParticles(ParticleTypes.FLAME, qx, qy, qz, 2, 0.06, 0.06, 0.06, 0.01);
            level.sendParticles(TRAIL, qx, qy, qz, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x - vel.x * 0.8, pos.y - vel.y * 0.8, pos.z - vel.z * 0.8, 3, 0.12, 0.12, 0.12, 0.01);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x - vel.x, pos.y - vel.y, pos.z - vel.z, 1, 0.05, 0.05, 0.05, 0.005);
        if (life % 2 == 0) level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.02);
        float yaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z)), pitch = (float) -Math.toDegrees(Math.atan2(vel.y, vel.horizontalDistance()));
        MechManager.cmd(server, level, String.format(Locale.ROOT, "tp @e[tag=%s,limit=1] %.3f %.3f %.3f %.1f %.1f", tag, pos.x, pos.y, pos.z, yaw, pitch));
    }

    void explode(MinecraftServer server, Vec3 at) {
        dead = true;
        MechManager.cmd(server, level, "kill @e[tag=" + tag + "]");
        boolean grief = MechManager.griefOn(server);
        Entity src = level.getEntity(shooter);
        level.explode(src, at.x, at.y, at.z, 4.5f, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, 1.0, 1.0, 1.0, 0.2);
        level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 40, 0.3, 0.3, 0.3, 0.45);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.6, 0.4, 0.6, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 1, at.z, 10, 1.2, 0.8, 1.2, 0.04);
        for (int i = 0; i < 24; i++) {   // 바닥 충격파 고리
            double a = i / 24.0 * Math.PI * 2;
            level.sendParticles(ParticleTypes.CLOUD, at.x + Math.cos(a) * 3, at.y + 0.2, at.z + Math.sin(a) * 3, 1, 0, 0, 0, 0.12);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3f, 0.6f);
        MechManager.cmd(server, level, String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..18] add gojo.fxshake_s", at.x, at.y, at.z));
    }

    void discard(MinecraftServer server) { MechManager.cmd(server, level, "kill @e[tag=" + tag + "]"); }
}
