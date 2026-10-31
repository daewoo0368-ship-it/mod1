package com.tubilt.jjk;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 모드 전용 아이템. 이름·설명·태그 등 세부 컴포넌트는 지급 명령(give)과 같은 문자열로 붙음 → 기존 데이터팩 로직 그대로 동작. */
public final class JjkItems {
    private JjkItems() {}
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, JjkMod.MOD_ID);

    public static final RegistryObject<Item> BLUE = ITEMS.register("blue", () -> new Item(new Item.Properties().setId(ITEMS.key("blue")).stacksTo(1)));
    public static final RegistryObject<Item> RED = ITEMS.register("red", () -> new Item(new Item.Properties().setId(ITEMS.key("red")).stacksTo(1)));
    public static final RegistryObject<Item> PURPLE = ITEMS.register("purple", () -> new Item(new Item.Properties().setId(ITEMS.key("purple")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> BLACK_FLASH = ITEMS.register("black_flash", () -> new Item(new Item.Properties().setId(ITEMS.key("black_flash")).stacksTo(1)));
    public static final RegistryObject<Item> INFINITY = ITEMS.register("infinity", () -> new Item(new Item.Properties().setId(ITEMS.key("infinity")).stacksTo(1)));
    public static final RegistryObject<Item> UNLIMITED_VOID = ITEMS.register("unlimited_void", () -> new Item(new Item.Properties().setId(ITEMS.key("unlimited_void")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DISMANTLE = ITEMS.register("dismantle", () -> new Item(new Item.Properties().setId(ITEMS.key("dismantle")).sword(ToolMaterial.IRON, 3.0F, -2.4F)));
    public static final RegistryObject<Item> FUGA = ITEMS.register("fuga", () -> new Item(new Item.Properties().setId(ITEMS.key("fuga")).stacksTo(1)));
    public static final RegistryObject<Item> MALEVOLENT_SHRINE = ITEMS.register("malevolent_shrine", () -> new Item(new Item.Properties().setId(ITEMS.key("malevolent_shrine")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MAHORAGA = ITEMS.register("mahoraga", () -> new Item(new Item.Properties().setId(ITEMS.key("mahoraga")).stacksTo(1)));
    public static final RegistryObject<Item> IDLE_TRANSFIGURATION = ITEMS.register("idle_transfiguration", () -> new Item(new Item.Properties().setId(ITEMS.key("idle_transfiguration")).stacksTo(1)));
    public static final RegistryObject<Item> SELF_EMBODIMENT = ITEMS.register("self_embodiment", () -> new Item(new Item.Properties().setId(ITEMS.key("self_embodiment")).stacksTo(1)));
    public static final RegistryObject<Item> MAXIMUM_METEOR = ITEMS.register("maximum_meteor", () -> new Item(new Item.Properties().setId(ITEMS.key("maximum_meteor")).stacksTo(1)));
    public static final RegistryObject<Item> COFFIN_IRON_MOUNTAIN = ITEMS.register("coffin_iron_mountain", () -> new Item(new Item.Properties().setId(ITEMS.key("coffin_iron_mountain")).stacksTo(1)));
    public static final RegistryObject<Item> HEAVENLY_RESTRICTION = ITEMS.register("heavenly_restriction", () -> new Item(new Item.Properties().setId(ITEMS.key("heavenly_restriction")).stacksTo(1)));
    public static final RegistryObject<Item> PRISON_REALM = ITEMS.register("prison_realm", () -> new Item(new Item.Properties().setId(ITEMS.key("prison_realm")).stacksTo(1)));
    public static final RegistryObject<Item> DEADLY_SENTENCING = ITEMS.register("deadly_sentencing", () -> new Item(new Item.Properties().setId(ITEMS.key("deadly_sentencing")).stacksTo(1)));
    public static final RegistryObject<Item> MOKUGU = ITEMS.register("mokugu", () -> new Item(new Item.Properties().setId(ITEMS.key("mokugu")).stacksTo(1)));
    public static final RegistryObject<Item> KUKA = ITEMS.register("kuka", () -> new Item(new Item.Properties().setId(ITEMS.key("kuka")).stacksTo(1)));
    public static final RegistryObject<Item> TAIKOKAI = ITEMS.register("taikokai", () -> new Item(new Item.Properties().setId(ITEMS.key("taikokai")).stacksTo(1)));
    public static final RegistryObject<Item> ZASATSU = ITEMS.register("zasatsu", () -> new Item(new Item.Properties().setId(ITEMS.key("zasatsu")).stacksTo(1)));
    public static final RegistryObject<Item> SIX_EYES = ITEMS.register("six_eyes", () -> new Item(new Item.Properties().setId(ITEMS.key("six_eyes")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SUKUNA_FINGER = ITEMS.register("sukuna_finger", () -> new Item(new Item.Properties().setId(ITEMS.key("sukuna_finger"))));
    public static final RegistryObject<Item> EXEC_SWORD = ITEMS.register("exec_sword", () -> new Item(new Item.Properties().setId(ITEMS.key("exec_sword")).sword(ToolMaterial.NETHERITE, 3.0F, -2.4F).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> INVERTED_SPEAR = ITEMS.register("inverted_spear", () -> new TridentItem(new Item.Properties().setId(ITEMS.key("inverted_spear")).durability(250).attributes(TridentItem.createAttributes()).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> PURIFY_ORB = ITEMS.register("purify_orb", () -> new Item(new Item.Properties().setId(ITEMS.key("purify_orb")).stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KOMING_EYE = ITEMS.register("koming_eye", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_eye")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KOMING_SIK = ITEMS.register("koming_sik", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_sik")).stacksTo(1)));
    public static final RegistryObject<Item> KOMING_GYO = ITEMS.register("koming_gyo", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_gyo")).stacksTo(1)));
    public static final RegistryObject<Item> KOMING_MURYEO = ITEMS.register("koming_muryeo", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_muryeo")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KOMING_LEGO = ITEMS.register("koming_lego", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_lego")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KOMING_SHIELD = ITEMS.register("koming_shield", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_shield")).stacksTo(1)));
    public static final RegistryObject<Item> KOMING_HUNSU = ITEMS.register("koming_hunsu", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_hunsu")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KOMING_LUNCH = ITEMS.register("koming_lunch", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_lunch")).stacksTo(1)));
    public static final RegistryObject<Item> KOMING_YU = ITEMS.register("koming_yu", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_yu")).stacksTo(1)));
    public static final RegistryObject<Item> KOMING_BRAIN = ITEMS.register("koming_brain", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_brain")).stacksTo(1)));
    public static final RegistryObject<Item> DEV_TOKEN = ITEMS.register("dev_token", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_token"))));
    public static final RegistryObject<Item> DEV_TOKEN2 = ITEMS.register("dev_token2", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_token2"))));
    public static final RegistryObject<Item> DEV_TOKEN3 = ITEMS.register("dev_token3", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_token3"))));
    public static final RegistryObject<Item> DEV_TOKEN4 = ITEMS.register("dev_token4", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_token4"))));
    public static final RegistryObject<Item> DEV_TOKEN_FINAL = ITEMS.register("dev_token_final", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_token_final")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_MACHINE_TOKEN = ITEMS.register("dev_machine_token", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_machine_token")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> COMMAND_GUARD = ITEMS.register("command_guard", () -> new Item(new Item.Properties().setId(ITEMS.key("command_guard")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ERROR_COMMON = ITEMS.register("error_common", () -> new Item(new Item.Properties().setId(ITEMS.key("error_common")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ERROR_COPY = ITEMS.register("error_copy", () -> new Item(new Item.Properties().setId(ITEMS.key("error_copy")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ERROR_CHAIN = ITEMS.register("error_chain", () -> new Item(new Item.Properties().setId(ITEMS.key("error_chain")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PURIFY_OBJECT = ITEMS.register("purify_object", () -> new Item(new Item.Properties().setId(ITEMS.key("purify_object")).stacksTo(64).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> COMMANDATION = ITEMS.register("commandation", () -> new Item(new Item.Properties().setId(ITEMS.key("commandation")).stacksTo(16).rarity(Rarity.EPIC)));
    // ── 압축 흙 → 개발자 검 (흙 9개씩 16단계)
    public static final RegistryObject<Item> DIRT1 = ITEMS.register("dirt1", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt1")).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> DIRT2 = ITEMS.register("dirt2", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt2")).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> DIRT3 = ITEMS.register("dirt3", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt3")).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> DIRT4 = ITEMS.register("dirt4", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt4")).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> DIRT5 = ITEMS.register("dirt5", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt5")).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> DIRT6 = ITEMS.register("dirt6", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt6")).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> DIRT7 = ITEMS.register("dirt7", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt7")).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DIRT8 = ITEMS.register("dirt8", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt8")).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DIRT9 = ITEMS.register("dirt9", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt9")).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DIRT10 = ITEMS.register("dirt10", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt10")).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DIRT11 = ITEMS.register("dirt11", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt11")).rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> DIRT12 = ITEMS.register("dirt12", () -> new Item(new Item.Properties().setId(ITEMS.key("dirt12")).rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> INFINITY_CORE = ITEMS.register("infinity_core", () -> new Item(new Item.Properties().setId(ITEMS.key("infinity_core")).rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> INFINITY_BLOCK = ITEMS.register("infinity_block", () -> new Item(new Item.Properties().setId(ITEMS.key("infinity_block")).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> LEGEND = ITEMS.register("legend", () -> new Item(new Item.Properties().setId(ITEMS.key("legend")).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> DEV_INGOT = ITEMS.register("dev_ingot", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_ingot")).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> DEV_SWORD = ITEMS.register("dev_sword", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_sword"))
            .sword(ToolMaterial.NETHERITE, 999995.0F, -2.0F).fireResistant().rarity(Rarity.EPIC)
            .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    // ── 개발자 검 9개부터 다시 12단계 → 「어 형이야~」 검
    public static final RegistryObject<Item> SWORD_APEX = ITEMS.register("sword_apex", () -> new Item(new Item.Properties().setId(ITEMS.key("sword_apex")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> MEGA_SHARD = ITEMS.register("mega_shard", () -> new Item(new Item.Properties().setId(ITEMS.key("mega_shard")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> MEGA = ITEMS.register("mega", () -> new Item(new Item.Properties().setId(ITEMS.key("mega")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> MEGA_BLOCK = ITEMS.register("mega_block", () -> new Item(new Item.Properties().setId(ITEMS.key("mega_block")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> WISDOM_SHARD = ITEMS.register("wisdom_shard", () -> new Item(new Item.Properties().setId(ITEMS.key("wisdom_shard")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> WISDOM = ITEMS.register("wisdom", () -> new Item(new Item.Properties().setId(ITEMS.key("wisdom")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> SUPER = ITEMS.register("super", () -> new Item(new Item.Properties().setId(ITEMS.key("super")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> QUANTUM = ITEMS.register("quantum", () -> new Item(new Item.Properties().setId(ITEMS.key("quantum")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> WISDOM_BLOCK = ITEMS.register("wisdom_block", () -> new Item(new Item.Properties().setId(ITEMS.key("wisdom_block")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> SUPER_BLOCK = ITEMS.register("super_block", () -> new Item(new Item.Properties().setId(ITEMS.key("super_block")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> QUANTUM_BLOCK = ITEMS.register("quantum_block", () -> new Item(new Item.Properties().setId(ITEMS.key("quantum_block")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> CODE = ITEMS.register("code", () -> new Item(new Item.Properties().setId(ITEMS.key("code")).fireResistant().rarity(Rarity.EPIC).stacksTo(64)));
    public static final RegistryObject<Item> LEGEND_SWORD = ITEMS.register("legend_sword", () -> new Item(new Item.Properties().setId(ITEMS.key("legend_sword"))
            .sword(ToolMaterial.NETHERITE, 5.0E9F, -1.4F).fireResistant().rarity(Rarity.EPIC).stacksTo(1)
            .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final RegistryObject<Item> CHOSEN_LIGHT = ITEMS.register("chosen_light", () -> new Item(new Item.Properties().setId(ITEMS.key("chosen_light")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final RegistryObject<Item> HYUNG_SWORD = ITEMS.register("hyung_sword", () -> new Item(new Item.Properties().setId(ITEMS.key("hyung_sword"))
            .sword(ToolMaterial.NETHERITE, 2.0E9F, -1.6F).fireResistant().rarity(Rarity.EPIC)
            .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    // ── 코드 갑옷 (코드로 제작, 풀세트 효과는 mech/CodeArmor)
    public static final net.minecraft.tags.TagKey<Item> CODE_REPAIR = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, JjkMod.id("repairs_code_armor"));
    public static final net.minecraft.world.item.equipment.ArmorMaterial CODE_ARMOR = new net.minecraft.world.item.equipment.ArmorMaterial(9999,
            java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.HELMET, 10, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 16,
                    net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 14, net.minecraft.world.item.equipment.ArmorType.BOOTS, 10,
                    net.minecraft.world.item.equipment.ArmorType.BODY, 20),
            30, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 20.0F, 0.25F, CODE_REPAIR,
            net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, JjkMod.id("code")));
    // ── 고죠의 안대 (머리 장비, 효과는 mech/GojoBlindfold)
    public static final net.minecraft.world.item.equipment.ArmorMaterial BLINDFOLD = new net.minecraft.world.item.equipment.ArmorMaterial(9999,
            java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.HELMET, 4, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 1,
                    net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 1, net.minecraft.world.item.equipment.ArmorType.BOOTS, 1,
                    net.minecraft.world.item.equipment.ArmorType.BODY, 1),
            25, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, CODE_REPAIR,
            net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, JjkMod.id("gojo_blindfold")));
    public static final RegistryObject<Item> GOJO_BLINDFOLD = ITEMS.register("gojo_blindfold", () -> new Item(new Item.Properties().setId(ITEMS.key("gojo_blindfold")).humanoidArmor(BLINDFOLD, net.minecraft.world.item.equipment.ArmorType.HELMET).fireResistant().rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)));
    public static final RegistryObject<Item> CODE_HELMET = ITEMS.register("code_helmet", () -> new Item(new Item.Properties().setId(ITEMS.key("code_helmet")).humanoidArmor(CODE_ARMOR, net.minecraft.world.item.equipment.ArmorType.HELMET).fireResistant().rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)));
    public static final RegistryObject<Item> CODE_CHESTPLATE = ITEMS.register("code_chestplate", () -> new Item(new Item.Properties().setId(ITEMS.key("code_chestplate")).humanoidArmor(CODE_ARMOR, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE).fireResistant().rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)));
    public static final RegistryObject<Item> CODE_LEGGINGS = ITEMS.register("code_leggings", () -> new Item(new Item.Properties().setId(ITEMS.key("code_leggings")).humanoidArmor(CODE_ARMOR, net.minecraft.world.item.equipment.ArmorType.LEGGINGS).fireResistant().rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)));
    public static final RegistryObject<Item> CODE_BOOTS = ITEMS.register("code_boots", () -> new Item(new Item.Properties().setId(ITEMS.key("code_boots")).humanoidArmor(CODE_ARMOR, net.minecraft.world.item.equipment.ArmorType.BOOTS).fireResistant().rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)));
    public static final String[] DIRT_IDS = {"dirt1", "dirt2", "dirt3", "dirt4", "dirt5", "dirt6", "dirt7", "dirt8", "dirt9", "dirt10", "dirt11", "dirt12", "infinity_core", "infinity_block", "legend", "dev_ingot", "dev_sword", "sword_apex", "mega_shard", "mega", "mega_block", "wisdom_shard", "wisdom", "super", "quantum", "wisdom_block", "super_block", "quantum_block", "code", "hyung_sword", "code_helmet", "code_chestplate", "code_leggings", "code_boots"};
    public static final RegistryObject<Item> BUG_EGG_V = ITEMS.register("bug_egg_v", () -> new Item(new Item.Properties().setId(ITEMS.key("bug_egg_v")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BUG_EGG_P = ITEMS.register("bug_egg_p", () -> new Item(new Item.Properties().setId(ITEMS.key("bug_egg_p")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BUG_EGG_E = ITEMS.register("bug_egg_e", () -> new Item(new Item.Properties().setId(ITEMS.key("bug_egg_e")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CTRL_Z = ITEMS.register("ctrl_z", () -> new Item(new Item.Properties().setId(ITEMS.key("ctrl_z")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> PURIFIED_EGG = ITEMS.register("purified_egg", () -> new Item(new Item.Properties().setId(ITEMS.key("purified_egg")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DEV_CODE = ITEMS.register("dev_code", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> GOJO_CODE = ITEMS.register("gojo_code", () -> new Item(new Item.Properties().setId(ITEMS.key("gojo_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> RED_LEGO = ITEMS.register("red_lego", () -> new Item(new Item.Properties().setId(ITEMS.key("red_lego")).stacksTo(64)));
    public static final RegistryObject<Item> YELLOW_LEGO = ITEMS.register("yellow_lego", () -> new Item(new Item.Properties().setId(ITEMS.key("yellow_lego")).stacksTo(64)));
    public static final RegistryObject<Item> BLUE_LEGO = ITEMS.register("blue_lego", () -> new Item(new Item.Properties().setId(ITEMS.key("blue_lego")).stacksTo(64)));
    public static final RegistryObject<Item> HELL_KEY = ITEMS.register("hell_key", () -> new Item(new Item.Properties().setId(ITEMS.key("hell_key")).stacksTo(16).fireResistant().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> PROJECTION_FRAME = ITEMS.register("projection_frame", () -> new Item(new Item.Properties().setId(ITEMS.key("projection_frame")).stacksTo(1)));
    public static final RegistryObject<Item> PROJECTION_SLICE = ITEMS.register("projection_slice", () -> new Item(new Item.Properties().setId(ITEMS.key("projection_slice")).stacksTo(1)));
    public static final RegistryObject<Item> PROJECTION_FREEZE = ITEMS.register("projection_freeze", () -> new Item(new Item.Properties().setId(ITEMS.key("projection_freeze")).stacksTo(1)));
    public static final RegistryObject<Item> PROJECTION_RUSH = ITEMS.register("projection_rush", () -> new Item(new Item.Properties().setId(ITEMS.key("projection_rush")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> BLOOD_BIND = ITEMS.register("blood_bind", () -> new Item(new Item.Properties().setId(ITEMS.key("blood_bind")).stacksTo(1)));
    public static final RegistryObject<Item> BLOOD_PIERCE = ITEMS.register("blood_pierce", () -> new Item(new Item.Properties().setId(ITEMS.key("blood_pierce")).stacksTo(1)));
    public static final RegistryObject<Item> BLOOD_NOVA = ITEMS.register("blood_nova", () -> new Item(new Item.Properties().setId(ITEMS.key("blood_nova")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> BLOOD_SCALE = ITEMS.register("blood_scale", () -> new Item(new Item.Properties().setId(ITEMS.key("blood_scale")).stacksTo(1)));
    public static final RegistryObject<Item> SPIRIT_SCROLL = ITEMS.register("spirit_scroll", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_scroll")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SPIRIT_EGG_FLY = ITEMS.register("spirit_egg_fly", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_fly")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPIRIT_EGG_HAND = ITEMS.register("spirit_egg_hand", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_hand")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPIRIT_EGG_EYE = ITEMS.register("spirit_egg_eye", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_eye")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPIRIT_EGG_DOLL = ITEMS.register("spirit_egg_doll", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_doll")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPIRIT_EGG_CENTI = ITEMS.register("spirit_egg_centi", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_centi")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPIRIT_EGG_MAW = ITEMS.register("spirit_egg_maw", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_maw")).stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPIRIT_EGG_SPIDER = ITEMS.register("spirit_egg_spider", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_spider")).stacksTo(64).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPIRIT_EGG_HUNDRED = ITEMS.register("spirit_egg_hundred", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_hundred")).stacksTo(64).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPIRIT_EGG_REAPER = ITEMS.register("spirit_egg_reaper", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_reaper")).stacksTo(64).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPIRIT_EGG_GIANT = ITEMS.register("spirit_egg_giant", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_giant")).stacksTo(64).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SPIRIT_EGG_DRAGON = ITEMS.register("spirit_egg_dragon", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_dragon")).stacksTo(64).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SPIRIT_EGG_KING = ITEMS.register("spirit_egg_king", () -> new Item(new Item.Properties().setId(ITEMS.key("spirit_egg_king")).stacksTo(64).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MEG_DOGS = ITEMS.register("meg_dogs", () -> new Item(new Item.Properties().setId(ITEMS.key("meg_dogs")).stacksTo(1)));
    public static final RegistryObject<Item> MEG_RABBITS = ITEMS.register("meg_rabbits", () -> new Item(new Item.Properties().setId(ITEMS.key("meg_rabbits")).stacksTo(1)));
    public static final RegistryObject<Item> MEG_SERPENT = ITEMS.register("meg_serpent", () -> new Item(new Item.Properties().setId(ITEMS.key("meg_serpent")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> NOB_NAIL = ITEMS.register("nob_nail", () -> new Item(new Item.Properties().setId(ITEMS.key("nob_nail")).stacksTo(1)));
    public static final RegistryObject<Item> NOB_RESONANCE = ITEMS.register("nob_resonance", () -> new Item(new Item.Properties().setId(ITEMS.key("nob_resonance")).stacksTo(1)));
    public static final RegistryObject<Item> NOB_HAIRPIN = ITEMS.register("nob_hairpin", () -> new Item(new Item.Properties().setId(ITEMS.key("nob_hairpin")).stacksTo(1)));
    public static final RegistryObject<Item> NAN_RATIO = ITEMS.register("nan_ratio", () -> new Item(new Item.Properties().setId(ITEMS.key("nan_ratio")).stacksTo(1)));
    public static final RegistryObject<Item> NAN_OVERTIME = ITEMS.register("nan_overtime", () -> new Item(new Item.Properties().setId(ITEMS.key("nan_overtime")).stacksTo(1)));
    public static final RegistryObject<Item> NAN_CLEAVE = ITEMS.register("nan_cleave", () -> new Item(new Item.Properties().setId(ITEMS.key("nan_cleave")).stacksTo(1)));
    public static final RegistryObject<Item> YUT_RIKA = ITEMS.register("yut_rika", () -> new Item(new Item.Properties().setId(ITEMS.key("yut_rika")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> YUT_COPY = ITEMS.register("yut_copy", () -> new Item(new Item.Properties().setId(ITEMS.key("yut_copy")).stacksTo(1)));
    public static final RegistryObject<Item> YUT_LOVE = ITEMS.register("yut_love", () -> new Item(new Item.Properties().setId(ITEMS.key("yut_love")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TOJ_HALBERD = ITEMS.register("toj_halberd", () -> new Item(new Item.Properties().setId(ITEMS.key("toj_halberd")).stacksTo(1)));
    public static final RegistryObject<Item> TOJ_DASH = ITEMS.register("toj_dash", () -> new Item(new Item.Properties().setId(ITEMS.key("toj_dash")).stacksTo(1)));
    public static final RegistryObject<Item> TOJ_HEAVEN = ITEMS.register("toj_heaven", () -> new Item(new Item.Properties().setId(ITEMS.key("toj_heaven")).stacksTo(1)));
    public static final RegistryObject<Item> REVERSE_HEAL = ITEMS.register("reverse_heal", () -> new Item(new Item.Properties().setId(ITEMS.key("reverse_heal")).stacksTo(1)));
    public static final RegistryObject<Item> REVERSE_OUT = ITEMS.register("reverse_out", () -> new Item(new Item.Properties().setId(ITEMS.key("reverse_out")).stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ALL_ORB = ITEMS.register("all_orb", () -> new Item(new Item.Properties().setId(ITEMS.key("all_orb")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> JOB_SCROLL = ITEMS.register("job_scroll", () -> new Item(new Item.Properties().setId(ITEMS.key("job_scroll")).stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> JACOB_LADDER = ITEMS.register("jacob_ladder", () -> new Item(new Item.Properties().setId(ITEMS.key("jacob_ladder")).stacksTo(1)));
    public static final RegistryObject<Item> JACOB_CLIMB = ITEMS.register("jacob_climb", () -> new Item(new Item.Properties().setId(ITEMS.key("jacob_climb")).stacksTo(1)));
    public static final RegistryObject<Item> JACOB_JUDGE = ITEMS.register("jacob_judge", () -> new Item(new Item.Properties().setId(ITEMS.key("jacob_judge")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_GLOVE = ITEMS.register("dev_glove", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_glove")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_GESTURE = ITEMS.register("dev_gesture", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_gesture")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> COMMAND_INKER = ITEMS.register("command_inker", () -> new Item(new Item.Properties().setId(ITEMS.key("command_inker")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> JUJUTSU_GUIDE = ITEMS.register("jujutsu_guide", () -> new Item(new Item.Properties().setId(ITEMS.key("jujutsu_guide")).stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DEV_WEAPON_GUIDE = ITEMS.register("dev_weapon_guide", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_weapon_guide")).stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> KOMING_KEY = ITEMS.register("koming_key", () -> new Item(new Item.Properties().setId(ITEMS.key("koming_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> JAMMIN_CODE = ITEMS.register("jammin_code", () -> new Item(new Item.Properties().setId(ITEMS.key("jammin_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MYEONG_KEY = ITEMS.register("myeong_key", () -> new Item(new Item.Properties().setId(ITEMS.key("myeong_key")).stacksTo(16).fireResistant().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> BINDING = ITEMS.register("binding", () -> new Item(new Item.Properties().setId(ITEMS.key("binding")).stacksTo(64)));
    public static final RegistryObject<Item> SUKUNA_KEY = ITEMS.register("sukuna_key", () -> new Item(new Item.Properties().setId(ITEMS.key("sukuna_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SUKUNA_CODE = ITEMS.register("sukuna_code", () -> new Item(new Item.Properties().setId(ITEMS.key("sukuna_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MACHINE_PART = ITEMS.register("machine_part", () -> new Item(new Item.Properties().setId(ITEMS.key("machine_part")).stacksTo(64)));
    public static final RegistryObject<Item> ILLAGER_KEY = ITEMS.register("illager_key", () -> new Item(new Item.Properties().setId(ITEMS.key("illager_key")).stacksTo(16)));
    public static final RegistryObject<Item> DOCTOR_KEY = ITEMS.register("doctor_key", () -> new Item(new Item.Properties().setId(ITEMS.key("doctor_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ROBOT_CODE = ITEMS.register("robot_code", () -> new Item(new Item.Properties().setId(ITEMS.key("robot_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HACKER_KEY = ITEMS.register("hacker_key", () -> new Item(new Item.Properties().setId(ITEMS.key("hacker_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HACKER_CODE = ITEMS.register("hacker_code", () -> new Item(new Item.Properties().setId(ITEMS.key("hacker_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DONQUIXOTE_KEY = ITEMS.register("donquixote_key", () -> new Item(new Item.Properties().setId(ITEMS.key("donquixote_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> WORLDVIEW_CODE = ITEMS.register("worldview_code", () -> new Item(new Item.Properties().setId(ITEMS.key("worldview_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> CURSE = ITEMS.register("curse", () -> new Item(new Item.Properties().setId(ITEMS.key("curse")).stacksTo(64).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SUN_STONE = ITEMS.register("sun_stone", () -> new Item(new Item.Properties().setId(ITEMS.key("sun_stone")).stacksTo(64).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KRALU_KEY = ITEMS.register("kralu_key", () -> new Item(new Item.Properties().setId(ITEMS.key("kralu_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> UGH_CODE = ITEMS.register("ugh_code", () -> new Item(new Item.Properties().setId(ITEMS.key("ugh_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ALL_ALL = ITEMS.register("all_all", () -> new Item(new Item.Properties().setId(ITEMS.key("all_all")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> CODE_MASTER_KEY = ITEMS.register("code_master_key", () -> new Item(new Item.Properties().setId(ITEMS.key("code_master_key")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_SCISSORS = ITEMS.register("dev_scissors", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_scissors")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_CLOTH = ITEMS.register("dev_cloth", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_cloth")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_FIST = ITEMS.register("dev_fist", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_fist")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_WORM = ITEMS.register("dev_worm", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_worm")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DIMENSION_BEACON = ITEMS.register("dimension_beacon", () -> new Item(new Item.Properties().setId(ITEMS.key("dimension_beacon")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DIMENSION_CODE = ITEMS.register("dimension_code", () -> new Item(new Item.Properties().setId(ITEMS.key("dimension_code")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> GOJO_KEY = ITEMS.register("gojo_key", () -> new Item(new Item.Properties().setId(ITEMS.key("gojo_key")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_EYE = ITEMS.register("dev_eye", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_eye")).stacksTo(16).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_SIGNATURE = ITEMS.register("dev_signature", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_signature")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> INFINITY_KEY = ITEMS.register("infinity_key", () -> new Item(new Item.Properties().setId(ITEMS.key("infinity_key")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> GOLDEN_CLASP = ITEMS.register("golden_clasp", () -> new Item(new Item.Properties().setId(ITEMS.key("golden_clasp")).stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ARCHMAGE_BOOK = ITEMS.register("archmage_book", () -> new Item(new Item.Properties().setId(ITEMS.key("archmage_book")).stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_PHONE = ITEMS.register("dev_phone", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_phone")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_LEVER = ITEMS.register("dev_lever", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_lever")).stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DEV_BUTTON = ITEMS.register("dev_button", () -> new Item(new Item.Properties().setId(ITEMS.key("dev_button")).stacksTo(1).rarity(Rarity.EPIC)));
    /** 건축물 소환기 — 개발자 전용 설계도 도구 (com.tubilt.jjk.blueprint.StructureSpawner). */
    public static final RegistryObject<Item> STRUCTURE_SPAWNER = ITEMS.register("structure_spawner", () -> new Item(new Item.Properties().setId(ITEMS.key("structure_spawner")).stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    /** 바닐라 기반 옛 아이템 → 전용 아이템 (옛 아이템도 계속 인식). */
    public static Item alt(Item vanilla) {
        if (vanilla == net.minecraft.world.item.Items.COPPER_NUGGET) return DEV_TOKEN.get();
        if (vanilla == net.minecraft.world.item.Items.IRON_NUGGET) return DEV_TOKEN2.get();
        if (vanilla == net.minecraft.world.item.Items.GOLD_NUGGET) return DEV_TOKEN3.get();
        if (vanilla == net.minecraft.world.item.Items.NETHERITE_SCRAP) return DEV_TOKEN4.get();
        if (vanilla == net.minecraft.world.item.Items.NETHER_STAR) return DEV_TOKEN_FINAL.get();
        if (vanilla == net.minecraft.world.item.Items.RECOVERY_COMPASS) return DEV_MACHINE_TOKEN.get();
        if (vanilla == net.minecraft.world.item.Items.PAPER) return COMMAND_GUARD.get();
        if (vanilla == net.minecraft.world.item.Items.HEART_OF_THE_SEA) return DEV_BUTTON.get();
        return vanilla;
    }
    public static boolean is(net.minecraft.world.item.ItemStack s, Item vanilla) { return s.is(vanilla) || s.is(alt(vanilla)); }

    public static void init(BusGroup modBus) { ITEMS.register(modBus); }
}
