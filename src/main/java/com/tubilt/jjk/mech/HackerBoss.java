package com.tubilt.jjk.mech;

import com.tubilt.jjk.net.JjkNet;
import com.tubilt.jjk.net.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 해커의 열쇠 → 보스전 「해커」 (가상 체력 1100, 2페이지).
 *  몸: 디스플레이 인형 (검은 후드·초록 LED 마스크·노트북), 노트북을 두드리며 다가와 근접 + 해킹 패턴.
 *  PAGE 1 「침투」   : 키보드 내려치기 · 코드 비 · 방화벽 · DDoS 패킷 · 백도어 기습 · 인벤토리 해킹(핫바 섞기)
 *  PAGE 2 「루트 권한」: 가짜 연결 끊김 → 랜섬웨어 QTE(복호화 키 3개) · virus.exe · 좌표 조작 · 화면 해킹
 */
public final class HackerBoss {
    private HackerBoss() {}

    static boolean running() { for (Bosses.Boss b : Bosses.ACTIVE) if (b instanceof Hack) return true; return false; }
    public static boolean isKey(ItemStack s) { return s.is(com.tubilt.jjk.JjkItems.HACKER_KEY.get()); }

    public static void summon(ServerPlayer p, ServerLevel l) {
        if (running()) { p.displayClientMessage(Component.literal("§7해커는 이미 접속 중이다"), true); return; }
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        Hack h = new Hack(l, at);
        if (h.body != null) Bosses.ACTIVE.add(h);
    }

    static final class Hack extends Bosses.Boss {
        static final float MAX = 1100;
        static final double S = 1.15;
        static final int GREEN = 0x33FF66, RED = 0xFF3040, DARK = 0x0A140C;
        static final String WHO = "Hacker";
        final Random R = new Random();
        final Vec3 home;
        int cd = 40, atk = 0, qte = 0, qteT = 0; boolean dying = false;
        final List<Mob> keys = new ArrayList<>();

        Hack(ServerLevel l, Vec3 at) {
            super(l, "§a§lHacker §7— 접속 중…", BossEvent.BossBarColor.GREEN);
            phase = 0;
            home = new Vec3(at.x, groundY(at.x, at.y + 3, at.z), at.z);
            body = hurtbox(home, S, MAX, "§aHacker");
            if (body == null) return;
            rig = new BossRig(this, "hacker", S).prop("laptop", new Vec3(0, 0.62, 0.62), 0.75, false);
            faceTo(nearest());
            intro();
        }

        static String banner(String s, int rgb) { return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(s, rgb, true) + "]}"; }
        void say(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§a<Hacker> §f" + s)); }
        void term(String s) { for (ServerPlayer p : players(80)) p.sendSystemMessage(Component.literal("§2root@hacker:~# §a" + s)); }
        void skill(String name, int rgb) {
            Holo.banner(l, tag, body.position().add(0, 2.8 * S, 0), banner(name, rgb), 1.0, 22);
            for (ServerPlayer p : players(80)) p.displayClientMessage(Component.literal("§aHacker §7▸ §f" + name), true);
            if (!rig.busy()) rig.play("type", 16);
            sfx("data", 1.2f, 1.2f + R.nextFloat() * 0.4f);
        }
        static String hex(Random r, int n) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < n; i++) sb.append("0123456789ABCDEF".charAt(r.nextInt(16))); return sb.toString(); }
        @Override float floor() { return MAX * (phase <= 1 ? 0.5f : 0f); }

        // ═════════ 컷신
        void intro() {
            for (ServerPlayer p : players(80)) {
                JjkNet.toPlayer(p, new Payloads.Screen(5, DARK, 7000, 0.55f));
                JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 1500, 1.5f));
                p.sendSystemMessage(Component.literal("§eHacker님이 게임에 참여했습니다"));
            }
            sfx("glitch", 3f, 0.6f);
            rig.play("type", 150);
            for (int i = 0; i < 24; i++) {   // 초록 코드 기둥들
                Vec3 q = home.add((R.nextDouble() - 0.5) * 16, 0, (R.nextDouble() - 0.5) * 16);
                Holo.pillar(l, tag, q, 10 + R.nextInt(10), 0.15, GREEN, 3 + i, 20, 10);
            }
            later(10, () -> term("ssh guest@" + hex(R, 4).toLowerCase(Locale.ROOT) + ".server  …접속 성공"));
            scene(150, WHO, new String[]{"…접속 완료.", "네 서버, 보안이 꽤 허술하네?", "방화벽 0개, 비밀번호 1234.", "좋아. 전부 털어 갈게."}, 34, 4.8f, 1.8f);
            later(146, () -> {
                phase = 1; cd = 30;
                bar.setName(Component.literal("§a§lHacker §7— PAGE 1 · 침투"));
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("Hacker", "PAGE 1 — 침투", GREEN, 1));
                sfx("stinger", 3f, 1.0f);
                music("minecraft:music_disc.11");
            });
        }
        void page2() {
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 6, "서버와의 연결이 끊어졌습니다");   // 가짜 연결 끊김
            later(70, () -> {
                for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 2000, 2f)); JjkNet.toPlayer(p, new Payloads.Screen(5, 0x200008, 9000, 0.5f)); }
                term("sudo su   …[sudo] password: ********   …root 권한 획득");
                sfx("glitch", 3f, 0.5f);
                rig.play("laugh", 60);
            });
            scene(200, WHO, new String[]{"……", "…농담이야. 연결은 내가 끊은 척한 거고.", "sudo 성공. 이제부턴 관리자다.", "PAGE 2 — 루트 권한."}, 44, 4.8f, 1.8f);
            later(196, () -> {
                phase = 2; cd = 20;
                bar.setName(Component.literal("§c§lroot@Hacker §7— PAGE 2 · 루트 권한"));
                bar.setColor(BossEvent.BossBarColor.RED);
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.CutIn("root@Hacker", "PAGE 2 — 루트 권한", RED, 1));
                sfx("stinger", 3f, 0.7f);
                music("minecraft:music_disc.otherside");
            });
        }
        void deathScene() {
            dying = true;
            stopMusic();
            for (ServerPlayer p : players(80)) { JjkNet.toPlayer(p, new Payloads.Screen(4, 0, 3000, 1.8f)); JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFFFFF, 5000, 0.3f)); }
            sfx("glitch", 3f, 0.4f);
            term("ERROR: 역추적 감지됨. 세션 강제 종료 중…");
            rig.play("guard", 9999);
            scene(140, WHO, new String[]{"뭐? 역추적?!", "말도 안 돼, 내 VPN이…", "연결이… 끊…"}, 38, 4.8f, 1.6f);
            later(100, () -> rig.play("fall", 9999));
            later(120, () -> {
                for (ServerPlayer p : l.players()) p.sendSystemMessage(Component.literal("§eHacker님이 서버에서 추방되었습니다 §7(사유: 보안 위반)"));
                Holo.flare(l, tag, body.position().add(0, 1, 0), 6, GREEN, 10);
                Holo.shards(l, tag, body.position().add(0, 1, 0), "minecraft:lime_stained_glass", 30, 6);
                sfx("shatter", 2f, 1.2f);
            });
            later(138, this::kill);
        }

        // ═════════ 전투
        @Override void step() {
            if (phase == 0 || dying) return;
            if (phase == 1 && atFloor()) { page2(); return; }
            if (phase == 2 && atFloor() && qte == 0) { deathScene(); return; }
            ServerPlayer tgt = nearest();
            if (tgt == null) return;
            if (qte > 0) { faceTo(tgt); tickQte(); return; }
            if (!rig.busy()) chase(tgt, phase == 2 ? 0.2 : 0.16, 2.4);
            else faceTo(tgt);
            if (phase == 2 && t % 60 == 30 && R.nextBoolean()) {   // 글리치 순간이동 (근접 거리 유지)
                Holo.flare(l, tag, body.position().add(0, S, 0), 1.4, GREEN, 4);
                Vec3 q = tgt.position().add((R.nextDouble() - 0.5) * 5, 0, (R.nextDouble() - 0.5) * 5);
                moveTo(new Vec3(q.x, groundY(q.x, tgt.getY() + 2, q.z), q.z));
                sfx("glitch", 1f, 1.6f);
            }
            if (--cd > 0) return;
            double dist = tgt.position().distanceTo(body.position());
            int k = phase == 1 ? (dist < 3.5 ? new int[]{9, 3, 9, 0, 9, 4}[atk++ % 6] : new int[]{0, 1, 2, 3, 0, 4, 2}[atk++ % 7])
                    : (dist < 3.5 ? new int[]{9, 3, 9, 6, 9, 8}[atk++ % 6] : new int[]{0, 5, 3, 6, 1, 7, 2, 8, 6}[atk++ % 9]);
            cd = phase == 2 ? 38 : 46;
            switch (k) {
                case 0 -> codeRain(tgt);
                case 1 -> firewall(tgt);
                case 2 -> ddos(tgt);
                case 3 -> backdoor(tgt);
                case 4 -> hotbar();
                case 5 -> ransomware(tgt);
                case 6 -> viruses(tgt);
                case 7 -> coords();
                case 8 -> screenHack(tgt);
                case 9 -> { melee(tgt, 7f, "slam", 9, 3.2); later(9, () -> sfx("data", 1.5f, 0.6f)); }
                default -> { }
            }
        }

        /** 코드 비: 초록 글자 기둥이 떨어지는 칸 → 착탄. */
        void codeRain(ServerPlayer tgt) {
            skill("코드 비", GREEN);
            term("for i in range(" + (phase == 2 ? 16 : 10) + "): drop(code)");
            int n = phase == 2 ? 16 : 10;
            for (int i = 0; i < n; i++) {
                Vec3 q = tgt.position().add((R.nextDouble() - 0.5) * 10, 0, (R.nextDouble() - 0.5) * 10);
                Holo.circle(l, tag, q.add(0, 0.1, 0), 1.3, GREEN, 18 + i, 0);
                Holo.text(l, tag, q, "{text:\"\"}", "center", 0, false, Holo.m(Holo.I(), 1.6, 1.6, 1.6, new Vec3(0, 14, 0), false), 22 + i, 1)
                        .key(2, Holo.m(Holo.I(), 1.6, 1.6, 1.6, new Vec3(0, 0.6, 0), false), 16 + i)
                        .text(age -> "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(hex(R, 1) + "\\n" + hex(R, 1) + "\\n" + hex(R, 1), GREEN, true) + "]}");
                final Vec3 qq = q;
                later(18 + i, () -> {
                    Holo.flare(l, tag, qq.add(0, 0.5, 0), 1.2, GREEN, 5);
                    l.playSound(null, qq.x, qq.y, qq.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1f, 1.8f);
                    for (ServerPlayer p : players(80)) if (p.position().distanceTo(qq) < 1.5) hurtP(p, 7f);
                });
            }
        }
        /** 방화벽: 빨간 벽이 투기장을 쓸고 지나감 (점프로 넘기). */
        void firewall(ServerPlayer tgt) {
            skill("방화벽 가동", RED);
            say("방화벽 켰어. 넘어오지 마~");
            Vec3 dir = tgt.position().subtract(body.position()).multiply(1, 0, 1).normalize(), side = new Vec3(-dir.z, 0, dir.x);
            Vec3 from = tgt.position().subtract(dir.scale(14)), to = tgt.position().add(dir.scale(14));
            for (int j = -6; j <= 6; j++) {   // 벽 조각 13개가 나란히 전진
                Vec3 st = from.add(side.scale(j * 2));
                Holo.block(l, tag, st, "minecraft:red_stained_glass", RED, Holo.m(Holo.I(), 2, 1.3, 2, new Vec3(0, 0.65, 0), true), 60, 0)
                        .key(14, Holo.m(Holo.I(), 2, 1.3, 2, to.subtract(from).add(0, 0.65, 0), true), 40);
            }
            for (int k = 0; k < 20; k++) {
                final double u = k / 19.0;
                later(14 + k * 2, () -> {
                    Vec3 c = from.lerp(to, u);
                    for (ServerPlayer p : players(80)) {
                        Vec3 rel = p.position().subtract(c);
                        if (Math.abs(rel.dot(dir)) < 1.0 && Math.abs(rel.dot(side)) < 13 && rel.y < 1.2) { hurtP(p, 5f); p.igniteForSeconds(2); }
                    }
                    if ((int) (u * 19) % 3 == 0) l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.5, c.z, 30, side.x * 6, 0.4, side.z * 6, 0.01);
                });
            }
        }
        /** DDoS: 패킷 큐브 수십 개가 날아옴. */
        void ddos(ServerPlayer tgt) {
            skill("DDoS 공격", GREEN);
            term("ping -f -s 65500 " + tgt.getName().getString());
            int n = phase == 2 ? 24 : 16;
            for (int i = 0; i < n; i++) {
                final int ii = i;
                later(i * 2, () -> {
                    ServerPlayer tt = nearest();
                    if (tt == null) return;
                    Vec3 a = body.position().add((R.nextDouble() - 0.5) * 2, 1.5 + R.nextDouble(), (R.nextDouble() - 0.5) * 2);
                    Vec3 b = tt.position().add((R.nextDouble() - 0.5) * 2.5, 1, (R.nextDouble() - 0.5) * 2.5);
                    Holo.block(l, tag, a, ii % 3 == 0 ? "minecraft:lime_concrete" : "minecraft:green_stained_glass", GREEN, Holo.m(Holo.I(), 0.4, 0.4, 0.4, Vec3.ZERO, true), 12, 0)
                            .key(10, Holo.m(Holo.I(), 0.4, 0.4, 0.4, b.subtract(a), true), 10).spin(30);
                    later(10, () -> { for (ServerPlayer p : players(80)) if (p.position().add(0, 1, 0).distanceTo(b) < 1.2) hurtP(p, 2f); });
                });
            }
        }
        /** 백도어: 뒤에서 튀어나와 찌름. */
        void backdoor(ServerPlayer tgt) {
            skill("백도어", GREEN);
            Vec3 back = tgt.position().subtract(tgt.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
            Holo.block(l, tag, back, "minecraft:black_concrete", GREEN, Holo.m(Holo.I(), 1.1, 2.0, 0.1, new Vec3(0, 1, 0), true), 26, 0);   // 문
            later(12, () -> {
                moveTo(new Vec3(back.x, tgt.getY(), back.z));
                faceTo(tgt);
                rig.play("swing", 14);
                Holo.flare(l, tag, back.add(0, 1.4, 0), 1.5, GREEN, 5);
                l.playSound(null, back.x, back.y, back.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 1.5f, 1.2f);
                if (tgt.position().distanceTo(back) < 3.5) hurtP(tgt, phase == 2 ? 14f : 10f);
            });
        }
        /** 인벤토리 해킹: 핫바 순서를 섞어 버림. */
        void hotbar() {
            skill("인벤토리 해킹", RED);
            say("핫바 정리해 줬어. 고맙지?");
            for (ServerPlayer p : players(30)) {
                var inv = p.getInventory();
                List<ItemStack> hb = new ArrayList<>();
                for (int i = 0; i < 9; i++) hb.add(inv.getItem(i).copy());
                java.util.Collections.shuffle(hb, R);
                for (int i = 0; i < 9; i++) inv.setItem(i, hb.get(i));
                p.inventoryMenu.broadcastChanges();
                GuardMeta.send(p, 5, "inventory.dat 변조됨");
            }
        }
        /** 랜섬웨어: 화면이 빨갛게 잠기고 계속 피해 → 복호화 키 3개를 부숴야 풀림. */
        void ransomware(ServerPlayer tgt) {
            skill("랜섬웨어", RED);
            term("encrypt --all --key=" + hex(R, 8) + "   …복구를 원하면 다이아 64개를 보내라");
            qte = 1; qteT = 200;
            guardFor(220);
            rig.play("type", 200);
            caption("§4§l당신의 파일은 암호화되었습니다\n§e복호화 키 3개를 10초 안에 부숴!");
            for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0x500000, 10000, 0.5f));
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                Vec3 q = tgt.position().add(Math.cos(a) * 7, 0.5, Math.sin(a) * 7);
                Mob m = spawn(EntityType.IRON_GOLEM, q, 30, 0.5, true);
                if (m == null) continue;
                m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
                m.setSilent(true);
                m.setCustomName(Component.literal("§a§l[ 🔑 " + hex(R, 4) + " ]"));
                m.setCustomNameVisible(true);
                keys.add(m); adds.add(m);
                final Mob mm = m;
                Holo.block(l, tag, q, "minecraft:gold_block", GREEN, Holo.m(Holo.I(), 0.9, 0.9, 0.9, Vec3.ZERO, true), 210, 1)
                        .follow(() -> mm.isAlive() ? mm.position().add(0, 0.7, 0) : new Vec3(0, -999, 0), Vec3.ZERO).spin(10);
            }
        }
        void tickQte() {
            qteT--;
            keys.removeIf(m -> !m.isAlive());
            if (qteT % 20 == 0) {
                for (ServerPlayer p : players(80)) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§4§l랜섬웨어 §f%d초 · §a키 %d개 남음", qteT / 20, keys.size())), true); hurtP(p, 1.5f); }
            }
            if (keys.isEmpty()) {
                qte = 0; guardT = 0; cd = 40; rig.play("guard", 20);
                say("뭐야, 키를 찾았어? …역으로 내 서버가 털리잖아!");
                for (ServerPlayer p : players(80)) JjkNet.toPlayer(p, new Payloads.Screen(5, 0xFFFFFF, 600, 0.3f));
                damageV(MAX * 0.06f);
                Holo.explosion(l, tag, body.position().add(0, 1.4, 0), 4, GREEN);
                return;
            }
            if (qteT <= 0) {
                qte = 0; guardT = 0; cd = 30; rig.play("laugh", 30);
                for (Mob m : keys) m.discard();
                keys.clear();
                say("시간 초과~ 파일은 내가 가져갈게.");
                for (ServerPlayer p : players(80)) { p.invulnerableTime = 0; p.hurtServer(l, l.damageSources().magic(), p.getMaxHealth() * 0.6f); }
                BossSfx.play(l, home, "boom", 3f, 0.5f);
            }
        }
        /** 바이러스 하수인 (virus.exe 좀벌레). */
        void viruses(ServerPlayer tgt) {
            skill("virus.exe 배포", RED);
            term("./virus.exe --spread=" + (phase == 2 ? 6 : 4));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 q = tgt.position().add(Math.cos(a) * 4, 0.3, Math.sin(a) * 4);
                var s = EntityType.SILVERFISH.create(l, EntitySpawnReason.MOB_SUMMONED);
                if (s == null) continue;
                s.snapTo(q.x, q.y, q.z, 0, 0);
                s.setCustomName(Component.literal("§cvirus.exe"));
                s.setCustomNameVisible(true);
                s.addTag("jjk.boss");
                s.setTarget(tgt);
                s.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 1, false, false));
                l.addFreshEntity(s);
                adds.add(s);
                Holo.flare(l, tag, q.add(0, 0.3, 0), 1, RED, 6);
            }
        }
        /** 좌표 조작: 플레이어를 공중으로 /tp. */
        void coords() {
            skill("좌표 조작", GREEN);
            for (ServerPlayer p : players(30)) {
                term("/tp " + p.getName().getString() + " ~ ~15 ~");
                p.teleportTo(p.getX(), p.getY() + 15, p.getZ());
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
                Holo.circle(l, tag, p.position().subtract(0, 15, 0).add(0, 0.1, 0), 2, GREEN, 40, 0);
            }
            say("y좌표 +15. 낙하 데미지는 서비스야~");
        }
        /** 화면 해킹: 화면 금 + 가짜 타이핑 직후 진짜 레이저. */
        void screenHack(ServerPlayer tgt) {
            skill("화면 해킹", RED);
            for (ServerPlayer p : players(80)) GuardMeta.send(p, R.nextBoolean() ? 5 : 8, R.nextBoolean() ? "rm -rf /" + p.getName().getString() : "모니터 너머에서 보고 있어");
            later(30, () -> {
                ServerPlayer tt = nearest();
                if (tt == null) return;
                Vec3 a = body.position().add(0, 1.4, 0), b = a.add(tt.position().add(0, 1, 0).subtract(a).normalize().scale(40));
                Holo.beamGlow(l, tag, a, b, 1.2, RED, 0, 4, 6);
                Holo.beamGlow(l, tag, a, b, 0.4, 0xFFFFFF, 0, 4, 6);
                BossSfx.play(l, a, "laser", 2.5f, 0.8f);
                for (ServerPlayer p : players(80)) {
                    Vec3 q = p.position().add(0, 1, 0), ab = b.subtract(a);
                    double s = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
                    if (q.distanceTo(a.add(ab.scale(s))) < 1.6) hurtP(p, 20f);
                }
            });
        }

        @Override void onDeath() {
            caption("§a§lHacker 격파\n§f「연결이… 끊…」");
            for (ServerPlayer p : players(80)) GuardMeta.send(p, 4, "해커를 역추적했다");
            giveCmd("jjk:hacker_code[minecraft:enchantment_glint_override=true] 1");
            giveCmd("jjk:code 8");
            giveCmd("jjk:quantum_block 2");
            giveCmd("jjk:golden_clasp 1");
            BossSfx.play(l, body.position(), "choir", 3f, 1.0f);
        }
        @Override void onLeave() { say("다 로그아웃했네. 로그는 지우고 간다~"); }
        @Override void cleanup() {
            for (Mob m : keys) if (m.isAlive()) m.discard();
            super.cleanup();
        }
    }
}
