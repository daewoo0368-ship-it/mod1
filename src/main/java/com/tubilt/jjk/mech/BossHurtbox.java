package com.tubilt.jjk.mech;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * 보스 히트박스: 보이지 않는 적(Enemy) 몹.
 *  · Enemy 라서 모드 무기·술식(적만 노리는 것들)이 전부 보스를 때림
 *  · 몬스터가 아니라 평화로움 난이도에서도 안 사라짐
 *  · 실제 체력은 쓰지 않음 (보스가 가상 체력으로 관리) — 주인 보스가 없으면 스스로 사라짐
 */
public class BossHurtbox extends PathfinderMob implements Enemy {
    public BossHurtbox(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoAi(true);
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvisible() { return true; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 40 && tickCount % 20 == 0 && !Bosses.owns(this)) discard();   // 서버 재시작 등으로 주인 잃은 히트박스 정리
    }
}
