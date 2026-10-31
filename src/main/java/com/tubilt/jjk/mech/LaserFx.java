package com.tubilt.jjk.mech;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * 레이저의 "실체": 디스플레이 엔티티(빛나는 블록)로 만든 굵은 빔·에너지 구체.
 * 파티클만으로는 가늘고 초라해 보여서, 실제 기하 형태를 겹겹이 렌더링.
 * 모든 엔티티는 jjk.laserfx + 탈것별 태그 → 끝나면/서버 종료 시 정리.
 */
final class LaserFx {
    private LaserFx() {}

    static String tag(net.minecraft.world.entity.Entity m) { return "jjk.lz" + m.getId(); }

    /** 레이어: [이름, 블록, 발광 테두리 색(-1 = 없음)] */
    static final String[][] BEAM = {
            {"core", "minecraft:white_concrete", "16777215"},
            {"in", "minecraft:light_blue_stained_glass", "-1"},
            {"mid", "minecraft:cyan_stained_glass", "4253951"},
            {"out", "minecraft:blue_stained_glass", "-1"},
    };
    static final String[][] ORB = {
            {"o1", "minecraft:white_concrete", "16777215"},
            {"o2", "minecraft:light_blue_stained_glass", "-1"},
            {"o3", "minecraft:purple_stained_glass", "11820287"},
    };
    static final String[][] HIT = {
            {"h1", "minecraft:white_stained_glass", "-1"},
            {"h2", "minecraft:light_blue_stained_glass", "4253951"},
    };

    /** 생성한 레이어 캐시 (tag.layer → 엔티티): 매 틱 @e 검색 + data merge 대신 직접 갱신. */
    static final java.util.Map<String, net.minecraft.world.entity.Entity> CACHE = new java.util.HashMap<>();

    static void spawn(ServerLevel level, net.minecraft.world.entity.Entity m, String[][] layers, Vec3 at) {
        for (String[] l : layers) {
            String glow = l[2].equals("-1") ? "" : ",Glowing:1b,glow_color_override:" + l[2];
            String c = String.format(Locale.ROOT,
                    "summon block_display %.3f %.3f %.3f {Tags:[\"jjk.laserfx\",\"%s\",\"%s.%s\"],block_state:{Name:\"%s\"},"
                            + "brightness:{sky:15,block:15},view_range:10f,teleport_duration:1,interpolation_duration:1,"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.001f,0.001f,0.001f]}%s}",
                    at.x, at.y, at.z, tag(m), tag(m), l[0], l[1], glow);
            net.minecraft.world.entity.Entity e = Holo.Fast.spawn(level, c);
            if (e == null) MechManager.cmd(level.getServer(), level, c);
            else { var old = CACHE.put(tag(m) + "." + l[0], e); if (old != null) old.discard(); }
        }
    }

    static void clear(ServerLevel level, net.minecraft.world.entity.Entity m) {
        String pre = tag(m) + ".";
        CACHE.entrySet().removeIf(en -> { if (en.getKey().startsWith(pre)) { en.getValue().discard(); return true; } return en.getValue().isRemoved(); });
        MechManager.cmd(level.getServer(), level, "kill @e[type=block_display,tag=" + tag(m) + "]");
    }

    static void kill(ServerLevel level, net.minecraft.world.entity.Entity m, String layer) {
        var e = CACHE.remove(tag(m) + "." + layer);
        if (e != null) { e.discard(); return; }
        MechManager.cmd(level.getServer(), level, "kill @e[type=block_display,tag=" + tag(m) + "." + layer + "]");
    }

    /** 레이어 하나를 위치 + 4x4 행렬(행 우선)로 갱신, 1틱 보간. */
    static void set(ServerLevel level, net.minecraft.world.entity.Entity m, String layer, Vec3 at, float[] mat) {
        var e = CACHE.get(tag(m) + "." + layer);
        if (e instanceof net.minecraft.world.entity.Display d && !e.isRemoved()) {
            d.snapTo(at.x, at.y, at.z, 0, 0);
            if (Holo.Fast.key(d, mat, 1)) return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) { if (i > 0) sb.append(','); sb.append(String.format(Locale.ROOT, "%.4ff", mat[i])); }
        String sel = "@e[type=block_display,tag=" + tag(m) + "." + layer + ",limit=1]";
        MechManager.cmd(level.getServer(), level, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f 0 0", sel, at.x, at.y, at.z));
        MechManager.cmd(level.getServer(), level, "data merge entity " + sel + " {transformation:[" + sb + "],start_interpolation:0,interpolation_duration:1}");
    }

    /** 원점에서 dir 방향으로 길이 len, 굵기 w 인 각기둥(빔). 축을 중심으로 roll 만큼 회전. */
    static float[] beam(Vec3 dir, double len, double w, double roll) {
        Vec3 z = dir.normalize();
        Vec3 x = new Vec3(0, 1, 0).cross(z);
        if (x.lengthSqr() < 1e-5) x = new Vec3(1, 0, 0);
        x = x.normalize();
        Vec3 y = z.cross(x).normalize();
        double c = Math.cos(roll), s = Math.sin(roll);
        Vec3 xr = x.scale(c).add(y.scale(s)), yr = y.scale(c).subtract(x.scale(s));
        Vec3 t = xr.scale(-w / 2).add(yr.scale(-w / 2));
        return new float[]{
                (float) (xr.x * w), (float) (yr.x * w), (float) (z.x * len), (float) t.x,
                (float) (xr.y * w), (float) (yr.y * w), (float) (z.y * len), (float) t.y,
                (float) (xr.z * w), (float) (yr.z * w), (float) (z.z * len), (float) t.z,
                0, 0, 0, 1};
    }

    /** 중심 기준 크기 size 인 정육면체를 (a, b) 각도로 회전. */
    static float[] cube(double size, double a, double b) {
        double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
        // R = Ry(a) * Rx(b)
        double[][] r = {
                {ca, sa * sb, sa * cb},
                {0, cb, -sb},
                {-sa, ca * sb, ca * cb}};
        float[] mtx = new float[16];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) mtx[i * 4 + j] = (float) (r[i][j] * size);
            mtx[i * 4 + 3] = (float) (-(r[i][0] + r[i][1] + r[i][2]) * size / 2);
        }
        mtx[15] = 1;
        return mtx;
    }
}
