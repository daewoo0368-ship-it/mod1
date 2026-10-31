package com.tubilt.jjk.mixin;

import com.tubilt.jjk.client.Cinematic;
import com.tubilt.jjk.mech.DevTitan;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 pos);

    /** 거인봇 탑승 중 3인칭 카메라를 멀리 (바닐라 최대 32칸 제한 우회) → 몸 전체가 보이게. */
    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float jjk$farCamera(float maxZoom) {
        var pl = Minecraft.getInstance().player;
        if (pl != null && pl.getVehicle() instanceof DevTitan) return 55f;
        if (pl != null && pl.getVehicle() instanceof com.tubilt.jjk.mech.DevMachine) return 48f;
        return maxZoom;
    }

    /** 개발자의 버튼 컷신: 카메라 위치·방향을 컷신 경로로 덮어씀 (플레이어도 보이게 3인칭 취급). */
    @Inject(method = "setup", at = @At("TAIL"))
    private void jjk$cinematic(CallbackInfo ci) {
        Cinematic.Shot shot = Cinematic.shot(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (shot == null) shot = com.tubilt.jjk.client.PlanetCine.shot(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (shot == null) shot = com.tubilt.jjk.client.QuickCam.shot();
        if (shot == null) return;
        this.detached = true;
        ((Camera) (Object) this).setRotation(shot.yaw(), shot.pitch(), 0f);
        this.setPosition(shot.pos());
    }
}
