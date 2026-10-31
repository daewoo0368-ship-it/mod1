package com.tubilt.jjk.machine;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 월드 엔진 기계 블록 등록: 회전축, 기어 3종, 전동기, 플라이휠, 팬 부하. */
public final class Machines {
    private Machines() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, JjkMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, JjkMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JjkMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JjkMod.MOD_ID);

    static final List<RegistryObject<Item>> ALL_ITEMS = new ArrayList<>();

    private static RegistryObject<Block> block(String name, Function<BlockBehaviour.Properties, Block> f) {
        RegistryObject<Block> b = BLOCKS.register(name, () -> f.apply(BlockBehaviour.Properties.of()
                .setId(BLOCKS.key(name)).mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));
        ALL_ITEMS.add(ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties().setId(ITEMS.key(name)).useBlockDescriptionPrefix())));
        return b;
    }

    public static final RegistryObject<Block> SHAFT = block("mech_shaft", MachineBlock.Shaft::new);
    public static final RegistryObject<Block> GEAR_S = block("mech_gear_s", MachineBlock.GearS::new);
    public static final RegistryObject<Block> GEAR_M = block("mech_gear_m", MachineBlock.GearM::new);
    public static final RegistryObject<Block> GEAR_L = block("mech_gear_l", MachineBlock.GearL::new);
    public static final RegistryObject<Block> MOTOR = block("mech_motor", MachineBlock.Motor::new);
    public static final RegistryObject<Block> FLYWHEEL = block("mech_flywheel", MachineBlock.Flywheel::new);
    public static final RegistryObject<Block> FAN = block("mech_fan", MachineBlock.Fan::new);

    public static final RegistryObject<BlockEntityType<MachineBE>> BE = TYPES.register("machine", () ->
            new BlockEntityType<>(MachineBE::new, null) {
                @Override public boolean isValid(BlockState s) { return s.getBlock() instanceof MachineBlock; }
            });

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("mech", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.literal("기계공학"))
            .icon(() -> new ItemStack(GEAR_M.get()))
            .displayItems((params, out) -> { for (RegistryObject<Item> i : ALL_ITEMS) out.accept(i.get()); })
            .build());

    public static void init(BusGroup modBus) {
        BLOCKS.register(modBus); ITEMS.register(modBus); TYPES.register(modBus); TABS.register(modBus);
        PhysWorld.init();
    }
}
