package com.tubilt.jjk.client;

import com.tubilt.jjk.JjkMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 개발자의 장갑 보조: 들고 있는지 판정 + 발사 열기(0..1). 기계팔 본체는 {@link GloveRender}(3D). */
final class GloveHud {
    private GloveHud() {}

    static float fire = 0;   // 발사 열기 0..1 (부드럽게 올라가고 식음)

    static boolean isGlove(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().equals("dev_glove");
    }

    static boolean isGesture(ItemStack s) {
        if (s.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(JjkMod.MOD_ID) && key.getPath().equals("dev_gesture");
    }

    static boolean held(Player p) { return isGlove(p.getMainHandItem()) || isGlove(p.getOffhandItem()); }

    /** HUD 호출 자리 — 이제 2D 로 그리지 않는다. */
    static void render(net.minecraft.client.gui.GuiGraphics g, int w, int h) {}
}
