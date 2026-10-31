package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class MechEntities {
    private MechEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, JjkMod.MOD_ID);
    public static final ResourceKey<EntityType<?>> DEV_MECH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, JjkMod.id("dev_mech"));

    public static final RegistryObject<EntityType<DevMech>> DEV_MECH = ENTITIES.register("dev_mech", () ->
            EntityType.Builder.of(DevMech::new, MobCategory.MISC)
                    .sized(4.4f, 3.6f)
                    .passengerAttachments(new Vec3(0.0, 5.05, 0.0))
                    .clientTrackingRange(12)
                    .fireImmune()                       // 불·용암에 안 탐
                    .build(DEV_MECH_KEY));

    public static final ResourceKey<EntityType<?>> DEV_WALKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, JjkMod.id("dev_walker"));

    public static final RegistryObject<EntityType<DevWalker>> DEV_WALKER = ENTITIES.register("dev_walker", () ->
            EntityType.Builder.of(DevWalker::new, MobCategory.MISC)
                    .sized(6.5f, 11.0f)
                    .passengerAttachments(new Vec3(0.0, 12.4, 3.1))
                    .clientTrackingRange(14)
                    .fireImmune()
                    .build(DEV_WALKER_KEY));

    public static final ResourceKey<EntityType<?>> DEV_TITAN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, JjkMod.id("dev_titan"));

    public static final RegistryObject<EntityType<DevTitan>> DEV_TITAN = ENTITIES.register("dev_titan", () ->
            EntityType.Builder.of(DevTitan::new, MobCategory.MISC)
                    .sized(5.0f, 16.0f)
                    .passengerAttachments(new Vec3(0.0, 36.0, 0.0))   // 가슴 조종석 (탑승자는 투명)
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .fireImmune()
                    .build(DEV_TITAN_KEY));

    public static final ResourceKey<EntityType<?>> DEV_MACHINE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, JjkMod.id("dev_machine"));

    public static final RegistryObject<EntityType<DevMachine>> DEV_MACHINE = ENTITIES.register("dev_machine", () ->
            EntityType.Builder.of(DevMachine::new, MobCategory.MISC)
                    .sized(10.0f, 18.0f)
                    .passengerAttachments(new Vec3(0.0, 24.0, 0.0))   // 몸통 조종석 (탑승자는 투명)
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .fireImmune()
                    .build(DEV_MACHINE_KEY));

    public static final ResourceKey<EntityType<?>> BOSS_HURTBOX_KEY = ResourceKey.create(Registries.ENTITY_TYPE, JjkMod.id("boss_hurtbox"));

    /** 보스 히트박스 (보이지 않음, 크기는 SCALE 속성으로 조절). */
    public static final RegistryObject<EntityType<BossHurtbox>> BOSS_HURTBOX = ENTITIES.register("boss_hurtbox", () ->
            EntityType.Builder.of(BossHurtbox::new, MobCategory.MISC)
                    .sized(0.8f, 2.0f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(BOSS_HURTBOX_KEY));

    public static void init(BusGroup modBus) {
        ENTITIES.register(modBus);
        EntityAttributeCreationEvent.BUS.addListener(e -> {
            e.put(DEV_MECH.get(), DevMech.createAttributes().build());
            e.put(DEV_WALKER.get(), DevWalker.createAttributes().build());
            e.put(DEV_TITAN.get(), DevTitan.createAttributes().build());
            e.put(DEV_MACHINE.get(), DevMachine.createAttributes().build());
            e.put(BOSS_HURTBOX.get(), BossHurtbox.createAttributes().build());
        });
    }
}
