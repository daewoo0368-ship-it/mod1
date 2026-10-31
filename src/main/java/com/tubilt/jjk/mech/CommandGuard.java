package com.tubilt.jjk.mech;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;

/**
 * 커맨드 수호 — 플레이어 뒤 허공에 떠서 천천히 도는 거대한(5칸) 마법부여된 커맨드 블록.
 *  우클릭: 소환 / (소환 중) 선택한 명령 실행 — Z/C 로 11가지 명령 선택
 *  (폭탄·화살 비·레이저·감옥·번개 폭풍·공중 낙하·시간 감속·커맨드 메테오·특이점·결계·분신)
 *  웅크리고 우클릭: 궁극기 「/kill」 / 아래 보며: 「/gamemode creative」 15초 비행 / 하늘 보며: 수호 해제
 *  자동: 적이 가까우면 작은 커맨드 블록들이 날아가 모여 「커맨드 골렘」 이 되어 싸움
 *        큰 피해 → 방어막 / 체력 30% → 긴급 회피 / 체력 50% → 황금사과 / 투사체 삭제 / 불 → 화염 저항
 *        추락 → 느린 낙하 / 어둠 → 야간 투시 / 주인이 때리면 후속타 / 가끔 명령어 중얼거림
 */
public final class CommandGuard {
    private CommandGuard() {}

    public static final String NAME = "커맨드 수호";
    static final Map<UUID, Guard> GUARDS = new HashMap<>();
    static final DustParticleOptions PURPLE = new DustParticleOptions(0xB45CFF, 1.6f);
    static final DustParticleOptions GREEN = new DustParticleOptions(0x40FF70, 1.2f);
    static final String[] FLAVOR = {"/time query daytime", "/gamerule keepInventory true", "/effect give @s luck", "/say 안녕?",
            "/execute if entity @e[type=zombie]", "/scoreboard players add @s 수호 1", "/data get entity @s", "/weather clear", "/locate structure ?", "/seed"};

    static DustParticleOptions dust(int rgb, float size) { return new DustParticleOptions(rgb, size); }

    public static final String CHAIN_NAME = "커맨드 수호.체인";
    public static boolean is(ItemStack s) {
        if (!com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.PAPER)) return false;
        String n = s.getHoverName().getString();
        return NAME.equals(n) || CHAIN_NAME.equals(n);
    }
    /** 커맨드 인커와 합체한 「커맨드 수호.체인」 */
    public static boolean isChain(ItemStack s) {
        return com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.PAPER) && CHAIN_NAME.equals(s.getHoverName().getString());
    }
    /** 인벤토리에 체인 수호가 있나 → 명령 휠에 인커 능력 4개가 붙는다. */
    static boolean chain(ServerPlayer p) {
        if (p == null) return false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isChain(inv.getItem(i))) return true;
        return false;
    }
    public static ItemStack chainItem() {
        ItemStack st = item();
        st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(CHAIN_NAME).withStyle(s -> s.withItalic(false).withColor(0x40E8FF).withBold(true)));
        st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, net.minecraft.resources.ResourceLocation.withDefaultNamespace("chain_command_block"));
        st.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("커맨드 수호 + 커맨드 인커").withStyle(s -> s.withItalic(true).withColor(0xAAAAAA)),
                Component.literal("명령 휠 맨 끝: 인커의 염력·사슬 능력 4가지").withStyle(s -> s.withItalic(false).withColor(0x40E8FF)),
                Component.literal("우클릭 소환·공격 / 웅크리고 우클릭 「/kill」 / 하늘 보며 웅크리고 우클릭 해제").withStyle(s -> s.withItalic(false).withColor(0x55FFFF)))));
        return st;
    }

    public static ItemStack item() {
        ItemStack st = new ItemStack(com.tubilt.jjk.JjkItems.COMMAND_GUARD.get());
        st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(s -> s.withItalic(false).withColor(0xFFB45CFF & 0xFFFFFF).withBold(true)));
        st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, net.minecraft.resources.ResourceLocation.withDefaultNamespace("command_block"));
        st.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        st.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        st.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("개발자의 토큰을 모두 합친 것").withStyle(s -> s.withItalic(true).withColor(0xAAAAAA)),
                Component.literal("우클릭 소환·공격 / 웅크리고 우클릭 「/kill」 / 하늘 보며 웅크리고 우클릭 해제").withStyle(s -> s.withItalic(false).withColor(0x55FFFF)))));
        return st;
    }

    // ───────────── 합체: 웅크리고 「개발자의 토큰」 우클릭 + 인벤토리에 토큰2 · (최종토큰 또는 토큰3+4)
    static boolean named(ItemStack s, net.minecraft.world.item.Item it, String n) { return com.tubilt.jjk.JjkItems.is(s, it) && n.equals(s.getHoverName().getString()); }

    public static boolean tryFuse(ServerPlayer p, ServerLevel level, ItemStack hand) {
        if (!p.isShiftKeyDown() || !named(hand, Items.COPPER_NUGGET, "개발자의 토큰")) return false;
        var inv = p.getInventory();
        int t2 = -1, fin = -1, t3 = -1, t4 = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (t2 < 0 && named(s, Items.IRON_NUGGET, "개발자의 토큰2")) t2 = i;
            if (fin < 0 && named(s, Items.NETHER_STAR, MechManager.FINAL_NAME)) fin = i;
            if (t3 < 0 && MechManager.isToken3(s)) t3 = i;
            if (t4 < 0 && MechManager.isToken4(s)) t4 = i;
        }
        if (t2 < 0 || (fin < 0 && (t3 < 0 || t4 < 0))) {
            p.displayClientMessage(Component.literal("§7합체하려면 개발자의 토큰2 와 최종토큰(또는 토큰3·토큰4)이 필요하다"), true);
            return true;
        }
        hand.shrink(1);
        inv.getItem(t2).shrink(1);
        if (fin >= 0) inv.getItem(fin).shrink(1); else { inv.getItem(t3).shrink(1); inv.getItem(t4).shrink(1); }
        ItemStack out = item();
        if (!inv.add(out)) p.drop(out, false);
        Vec3 c = p.position().add(0, 1, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 1, c.z, 300, 1.5, 1.5, 1.5, 1.0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 100, 0.6, 0.8, 0.6, 0.6);
        p.displayClientMessage(Component.literal("§d§l모든 개발자의 토큰이 하나로 — §f커맨드 수호"), false);
        return true;
    }

    // ───────────── 입력
    public static void use(ServerPlayer p, ServerLevel level) {
        Guard g = GUARDS.get(p.getUUID());
        if (g == null || g.level != level) {
            if (g != null) g.remove();
            g = new Guard(level, p);
            GUARDS.put(p.getUUID(), g);
            g.summon();
            return;
        }
        if (p.isShiftKeyDown()) {
            if (p.getXRot() < -50) { g.remove(); GUARDS.remove(p.getUUID()); p.displayClientMessage(Component.literal("§7커맨드 수호가 잠들었다"), true); }
            else if (p.getXRot() > 50) g.creative();
            else if (g.odT > 0 && g.domainCd == 0 && !g.domainOn) g.domain();   // 각성: 오버드라이브 중이면 영역 전개
            else g.ultimate();
            return;
        }
        g.attack();
    }

    /** 플레이어가 맞을 때: 방어막이 막으면 true. */
    /** 주인을 공격한 적에게 반격 번개. */
    public static void onHurtBy(Player p, LivingEntity attacker) {
        Guard g = GUARDS.get(p.getUUID());
        if (g != null && p instanceof ServerPlayer sp) g.retaliate(sp, attacker);
    }

    public static boolean onHurt(Player p, float amount) {
        Guard g = GUARDS.get(p.getUUID());
        return g != null && (g.absolute(amount) || g.shield(amount));
    }

    /** 주인이 죽기 직전: 부활 가능하면 true (죽음 취소). */
    public static boolean onDeath(Player p) {
        Guard g = GUARDS.get(p.getUUID());
        return g != null && p instanceof ServerPlayer sp && g.revive(sp);
    }

    /** Z/C 로 명령 선택 (수호를 들고 있을 때). */
    public static void cycle(ServerPlayer p, int dir) {
        if (p.isShiftKeyDown()) { toggleGolem(p); return; }   // 웅크리고 Z/C = 골렘 켜기/끄기
        Guard g = GUARDS.get(p.getUUID());
        if (g == null) { p.displayClientMessage(Component.literal("§7먼저 우클릭으로 커맨드 수호를 소환하세요"), true); return; }
        g.cycle(dir);
    }

    /** 골렘 끈 플레이어 (수호를 다시 소환해도 유지). */
    static final Set<UUID> GOLEM_OFF = new HashSet<>();

    static void toggleGolem(ServerPlayer p) {
        boolean off = !GOLEM_OFF.remove(p.getUUID());
        if (off) GOLEM_OFF.add(p.getUUID());
        Guard g = GUARDS.get(p.getUUID());
        if (off && g != null) { for (Golem gl : g.golems) gl.discard(g); g.golems.clear(); }
        p.displayClientMessage(Component.literal(off ? "§7커맨드 골렘 §c끔 §8(웅크리고 Z/C 로 다시 켜기)" : "§7커맨드 골렘 §a켬"), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), off ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    /** 골렘 허용: 개인 설정 + 월드 설정(#golem gojo.cfg, 0 이면 모두 끔). */
    static boolean golemAllowed(ServerPlayer p) {
        if (GOLEM_OFF.contains(p.getUUID())) return false;
        var sb = p.level().getServer().getScoreboard();
        var o = sb.getObjective("gojo.cfg");
        if (o == null) return true;
        var i = sb.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("#golem"), o);
        return i == null || i.value() != 0;
    }

    /** 수호 주인이 무언가를 때렸을 때 → 후속 사격. */
    public static void onOwnerHit(Player p, LivingEntity target) {
        Guard g = GUARDS.get(p.getUUID());
        if (g != null && target != p) g.followUp(target);
    }

    static void tickAll(MinecraftServer server) {
        sweepKb(server);
        for (Iterator<Map.Entry<UUID, Guard>> it = GUARDS.entrySet().iterator(); it.hasNext(); ) {
            Guard g = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(g.player);
            if (p == null || !p.isAlive() || p.level() != g.level) { g.remove(); it.remove(); continue; }
            try { g.tick(p); GuardMeta.tick(g, p); guardPassive(g, p); } catch (Exception e) { com.tubilt.jjk.JjkMod.LOG.warn("[JJK] 커맨드 수호 오류", e); }
        }
    }

    static void stopAll() { for (Guard g : GUARDS.values()) g.remove(); GUARDS.clear(); }

    // ── 수호가 떠 있는 동안: 넉백 무효 + 날아오는 투사체 차단
    static final net.minecraft.resources.ResourceLocation KB_ID = com.tubilt.jjk.JjkMod.id("guard_no_knockback");
    static void kb(ServerPlayer p, boolean on) {
        for (var at : List.of(Attributes.KNOCKBACK_RESISTANCE, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)) {
            var a = p.getAttribute(at);
            if (a == null) continue;
            boolean has = a.hasModifier(KB_ID);
            if (on && !has) a.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(KB_ID, 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            else if (!on && has) a.removeModifier(KB_ID);
        }
    }
    static void guardPassive(Guard g, ServerPlayer p) {
        kb(p, true);
        Vec3 c = p.position().add(0, 1, 0);
        for (net.minecraft.world.entity.projectile.Projectile pr : g.level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                p.getBoundingBox().inflate(7), x -> x.isAlive() && x.getOwner() != p)) {
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.01 || v.dot(c.subtract(pr.position())) <= 0) continue;   // 다가오는 것만
            Vec3 at = pr.position();
            Holo.glow(g.level, g.tag, at, 1.6, 0xB45CFF, 1, 1, 4);
            Holo.lightning(g.level, g.tag, g.pos, at, 0xB45CFF, 0.06, 3);
            g.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.6);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.SHULKER_BULLET_HURT, SoundSource.PLAYERS, 1f, 1.4f);
            pr.discard();
        }
    }
    /** 수호가 없는 플레이어에게 남은 넉백 무효를 정리. */
    static void sweepKb(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!GUARDS.containsKey(p.getUUID())) kb(p, false);
    }
    /** 피해 훅: 수호가 떠 있으면 투사체 피해 무효. */
    public static boolean blocksProjectile(net.minecraft.world.entity.player.Player p, net.minecraft.world.damagesource.DamageSource src) {
        return GUARDS.containsKey(p.getUUID()) && (src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile || src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE));
    }

    /** 수호를 들고 있을 때 HUD: [선택, 명령 대기, /kill 쿨, /gamemode 쿨, 방어막 쿨, 골렘 수, 골렘 쿨, 활성(1)] */
    public static List<Integer> hud(ServerPlayer p) {
        if (p.getVehicle() != null || !is(p.getMainHandItem())) return null;
        Guard g = GUARDS.get(p.getUUID());
        if (g == null) return List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, hudFlags(p));
        return List.of(g.mode, g.atkCd, g.ultCd, g.flyCd, g.shieldCd, g.golems.size(), g.golemCd, 1, g.combo, g.od / 10, g.odT, g.domainCd, g.domainOn ? 1 : 0,
                g.absT > 0 ? -g.absT : g.absCd, g.reviveCd, hudFlags(p));
    }

    // ───────────── 표시용 행렬 (item_display 는 원점 중심)
    static float[] mat(double yawRad, double tiltRad, Vec3 c, double sx, double sy, double sz) {
        double ca = Math.cos(yawRad), sa = Math.sin(yawRad), cb = Math.cos(tiltRad), sb = Math.sin(tiltRad);
        double[][] r = {{ca, sa * sb, sa * cb}, {0, cb, -sb}, {-sa, ca * sb, ca * cb}};   // Ry * Rx
        double[] sc = {sx, sy, sz}, cc = {c.x, c.y, c.z};
        float[] m = new float[16];
        for (int i = 0; i < 3; i++) {
            double t = 0;
            for (int j = 0; j < 3; j++) { m[i * 4 + j] = (float) (r[i][j] * sc[j]); t += r[i][j] * cc[j]; }
            m[i * 4 + 3] = (float) t;
        }
        m[15] = 1;
        return m;
    }

    static final class Guard {
        final ServerLevel level; final UUID player; final String tag;
        Vec3 pos; double spin = 0;
        int age = 0, mode = 0, atkCd = 0, ultCd = 0, golemCd = 0, shieldCd = 0, tpCd = 0, shieldT = 0, ultT = 0;
        final List<Golem> golems = new ArrayList<>();
        final List<Bolt> bolts = new ArrayList<>();
        final List<Floater> texts = new ArrayList<>();
        int laserT = 0; Vec3 laserTo; static int uid = 0;
        int flyT = 0, flyCd = 0, followCd = 0, deflectMsg = 0, fireCd = 0, fallCd = 0, nightMsg = 0, appleCd = 0;
        final List<Pending> pending = new ArrayList<>();
        final List<net.minecraft.core.BlockPos> cage = new ArrayList<>();
        static final String[] MODES = {"폭탄", "화살 비", "레이저", "/fill 감옥", "/summon 번개 폭풍", "/tp 공중 낙하", "/effect 시간 감속",
                "/summon 커맨드 메테오", "/execute 특이점", "/worldborder 결계", "/clone 분신", "/tick freeze", "/summon 검의 비",
                "/execute 궤도 폭격", "/summon 커맨드 드래곤", "/fill 블록 해일", "/execute 시공 절단", "/summon 커맨드 거인",
                "/execute 천공의 창", "/function 연쇄 번개", "/data 칼날 폭풍",
                "/particle 성운 붕괴", "/execute 시간 역행", "/summon 커맨드 피닉스", "/kill @e 심판의 눈", "/tp 행성 낙하",
                "/gamerule 중력 붕괴", "/attribute 축소", "/data 체력 1", "/tp 천공 추방", "/item 무장 해제",
                "/summon 커맨드 블록 비", "/setblock 큐브 압축", "/scoreboard 처형", "/clone 지형 압착", "/summon 포탑 진지", "/kill 삭제 코드",
                "/team 세뇌", "/give TNT 부착", "/random 운명의 주사위", "/execute 블랙홀→화이트홀", "/summon 거대 손",
                "∞ /kill @e 종말 명령", "∞ 무한 큐브", "∞ 명령의 신 강림", "∞ /reload", "∞ /execute ∞"};
        int combo = 0, comboT = 0, od = 0, odT = 0, domainCd = 0;
        int absT = 0, absCd = 0, reviveCd = 0;      // 절대 방어막 · 부활
        // 시전 모션: 0 반동, 1 상승, 2 돌진·급강하, 3 전방 채널링
        int motionT = 0, motionLen = 0, motionKind = 0; Vec3 motionTo = Vec3.ZERO;
        int autoCd = 40, retaliateCd = 0;
        // 물리 모션 상태 (스프링·관성·기울기·찌그러짐·잔상)
        double orbA = 0, selfA = 0;      // 작은 커맨드 블록 궤도·자전 각 (누적, 속도 제한)
        Vec3 vel = Vec3.ZERO; double orbitK = 1, orbitV = 0, squash = 1, squashV = 0, jitter = 0; int idleScanT = 0;
        static final java.util.Set<Integer> BLINK = java.util.Set.of(0, 10, 23);
        static final java.util.Set<Integer> ORBIT = java.util.Set.of(20);
        double wireYaw = 0, ringYaw = 0, scaleK = 1, spinSpeed = 1;
        static final java.util.Set<Integer> SKY = java.util.Set.of(1, 4, 7, 12, 13, 18, 21, 24, 25, 26, 29, 31, 39, 41, 42, 44, 46);
        static final java.util.Set<Integer> GROUND = java.util.Set.of(5, 15, 16, 17, 22, 34);
        static final java.util.Set<Integer> CHANNEL = java.util.Set.of(2, 3, 6, 8, 9, 10, 11, 14, 19, 20, 27, 28, 30, 32, 33, 35, 36, 37, 38, 40, 43, 45);
        final Map<LivingEntity, Holo.Elem> bars = new HashMap<>();
        boolean domainOn = false;
        Holo.Elem termEl;
        final List<String[]> term = new ArrayList<>();
        int termAt = 0;
        final List<Fx> fx = new ArrayList<>();

        void later(int ticks, Runnable r) { pending.add(new Pending(ticks, r)); }

        /** Z/C (술식 넘기기 키) 로 명령 선택. */
        void cycle(int dir) {
            int base0 = unsealed(p()) ? MODES.length : BASE_MODES;
            int nm = base0 + (chain(p()) ? CommandInker.MODES.length : 0);
            mode = ((mode + dir) % nm + nm) % nm;
            ServerPlayer p = p();
            if (p == null) return;
            StringBuilder sb = new StringBuilder("§8");
            for (int i = -2; i <= 2; i++) {
                int k = ((mode + i) % nm + nm) % nm;
                String nmk = k >= base0 ? CommandInker.MODES[k - base0] : MODES[k];
                sb.append(i == 0 ? " §d§l▶ " + nmk + " ◀§r§8 " : " " + nmk + " ");
            }
            if (!p.getTags().contains("gojo.modhud")) p.displayClientMessage(Component.literal(sb.toString()), true);   // 모드 HUD 가 있으면 액션바 생략
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.3f + (mode % 5) * 0.08f);
        }

        Guard(ServerLevel level, ServerPlayer p) {
            this.level = level; this.player = p.getUUID(); this.tag = "jjk.cg" + p.getId();
            this.pos = behind(p);
        }

        void cmd(String c) { MechManager.cmd(level.getServer(), level, c); }

        // ── 디스플레이 캐시: @e 선택자(전체 엔티티 검색) + data merge(NBT 왕복) 대신 엔티티를 직접 다룸
        final java.util.Map<String, net.minecraft.world.entity.Entity> subEnt = new java.util.HashMap<>();
        final java.util.Map<String, List<net.minecraft.world.entity.Entity>> grpEnt = new java.util.HashMap<>();
        void spawnFx(String summon, String sub, String grp) {
            net.minecraft.world.entity.Entity e = Holo.Fast.spawn(level, summon);
            if (e == null) { cmd(summon); return; }
            subEnt.put(sub, e);
            if (grp != null) grpEnt.computeIfAbsent(grp, k -> new ArrayList<>()).add(e);
        }
        net.minecraft.world.entity.Entity sub(String sub) {
            net.minecraft.world.entity.Entity e = subEnt.get(sub);
            return e == null || e.isRemoved() ? null : e;
        }
        static void snap(net.minecraft.world.entity.Entity e, Vec3 at) { e.snapTo(at.x, at.y, at.z, e.getYRot(), e.getXRot()); }

        ServerPlayer p() { return level.getServer().getPlayerList().getPlayer(player); }

        Vec3 behind(ServerPlayer p) {
            double r = Math.toRadians(p.getYRot());
            double sway = Math.sin(age * 0.025) * 1.6, back = 5.8, side = 2.2;     // 등 뒤 대각선 위쪽, 거리 넉넉히
            return p.position().add(Math.sin(r) * back - Math.cos(r) * (sway + side), 5.4 + Math.sin(age * 0.06) * 0.45, -Math.cos(r) * back - Math.sin(r) * (sway + side));
        }

        void displayG(String sub, String grp, Vec3 at, String itemId, boolean glint, float view) {
            spawnFx(String.format(Locale.ROOT, "summon item_display %.3f %.3f %.3f {Tags:[\"jjk.laserfx\",\"%s\",\"%s.%s\",\"%s.%s\"],item:{id:\"%s\",count:1%s},"
                            + "brightness:{sky:15,block:15},view_range:%.1ff,teleport_duration:1,interpolation_duration:2,"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.001f,0.001f,0.001f]}}",
                    at.x, at.y, at.z, tag, tag, sub, tag, grp, itemId, glint ? ",components:{\"minecraft:enchantment_glint_override\":true}" : "", view), sub, grp);
        }

        void textG(String sub, String grp, Vec3 at, String text, int color, String billboard, boolean bold) {
            spawnFx(String.format(Locale.ROOT, "summon text_display %.3f %.3f %.3f {Tags:[\"jjk.laserfx\",\"%s\",\"%s.%s\",\"%s.%s\"],billboard:\"%s\",background:0,shadow:1b,"
                            + "see_through:0b,line_width:200,teleport_duration:1,interpolation_duration:2,brightness:{sky:15,block:15},view_range:4f,"
                            + "text:{text:\"%s\",color:\"#%06X\",bold:%s},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.001f,0.001f,0.001f]}}",
                    at.x, at.y, at.z, tag, tag, sub, tag, grp, billboard, text.replace("\"", "'"), color & 0xFFFFFF, bold ? "1b" : "0b"), sub, grp);
        }

        void blockG(String sub, String grp, Vec3 at, String blockId, int glow) {
            spawnFx(String.format(Locale.ROOT, "summon block_display %.3f %.3f %.3f {Tags:[\"jjk.laserfx\",\"%s\",\"%s.%s\",\"%s.%s\"],block_state:{Name:\"%s\"},"
                            + "brightness:{sky:15,block:15},view_range:6f,teleport_duration:1,interpolation_duration:2,%s"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.001f,0.001f,0.001f]}}",
                    at.x, at.y, at.z, tag, tag, sub, tag, grp, blockId, glow >= 0 ? "Glowing:1b,glow_color_override:" + glow + "," : ""), sub, grp);
        }

        /** 변환만 갱신 (위치는 그룹 tp 로). */
        void shape(String sub, float[] m) {
            if (sub(sub) instanceof net.minecraft.world.entity.Display d && Holo.Fast.key(d, m, 2)) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) { if (i > 0) sb.append(','); sb.append(String.format(Locale.ROOT, "%.4ff", m[i])); }
            cmd("data merge entity @e[tag=" + tag + "." + sub + ",limit=1] {transformation:[" + sb + "],start_interpolation:0,interpolation_duration:2}");
        }

        void tpGroup(String grp, Vec3 at) {
            List<net.minecraft.world.entity.Entity> l = grpEnt.get(grp);
            if (l != null) { for (var e : l) if (!e.isRemoved()) snap(e, at); return; }
            cmd(String.format(Locale.ROOT, "tp @e[tag=%s.%s] %.3f %.3f %.3f", tag, grp, at.x, at.y, at.z));
        }

        void killGroup(String grp) {
            List<net.minecraft.world.entity.Entity> l = grpEnt.remove(grp);
            if (l == null) { cmd("kill @e[tag=" + tag + "." + grp + "]"); return; }
            for (var e : l) e.discard();
            subEnt.values().removeIf(net.minecraft.world.entity.Entity::isRemoved);
        }

        /** 목표 지점에 회전하는 명령어 조준진 (글자 원). */
        void glyphs(Vec3 at, double r, int life, int color) { Holo.circle(level, tag, at, r, color, Math.max(14, life), 0); }

        void display(String sub, Vec3 at, String itemId, boolean glint) {
            spawnFx(String.format(Locale.ROOT, "summon item_display %.3f %.3f %.3f {Tags:[\"jjk.laserfx\",\"%s\",\"%s.%s\"],item:{id:\"%s\",count:1%s},"
                            + "brightness:{sky:15,block:15},view_range:4f,teleport_duration:2,interpolation_duration:2,"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.001f,0.001f,0.001f]}}",
                    at.x, at.y, at.z, tag, tag, sub, itemId, glint ? ",components:{\"minecraft:enchantment_glint_override\":true}" : ""), sub, null);
        }

        void move(String sub, Vec3 at, float[] m) {
            if (sub(sub) instanceof net.minecraft.world.entity.Display d) { snap(d, at); if (Holo.Fast.key(d, m, 2)) return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) { if (i > 0) sb.append(','); sb.append(String.format(Locale.ROOT, "%.4ff", m[i])); }
            String sel = "@e[type=item_display,tag=" + tag + "." + sub + ",limit=1]";
            cmd(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f", sel, at.x, at.y, at.z));
            cmd("data merge entity " + sel + " {transformation:[" + sb + "],start_interpolation:0,interpolation_duration:2}");
        }

        void kill(String sub) {
            net.minecraft.world.entity.Entity e = subEnt.remove(sub);
            if (e == null) { cmd("kill @e[tag=" + tag + "." + sub + "]"); return; }
            e.discard();
            for (List<net.minecraft.world.entity.Entity> l : grpEnt.values()) l.remove(e);
        }

        /** 머리 위로 떠오르는 명령어 글자. */
        /** 명령 로그 → 수호 위 홀로 터미널 (타자 효과). */
        void say(String text, int color, Vec3 at, float scale) {
            ServerPlayer op = p();          // 주인 화면: 명령어 타이핑 → 실행
            if (op != null) com.tubilt.jjk.net.JjkNet.toPlayer(op, new com.tubilt.jjk.net.Payloads.CutIn(text, "", color, 2));
            if (op != null && text.startsWith("/")) GuardMeta.maybeTypo(op, text);   // 가끔 오타 내고 고쳐 침
            term.add(new String[]{text, Integer.toString(color)});
            while (term.size() > 4) term.remove(0);
            termAt = age;
        }

        String termText(int t) {
            StringBuilder sb = new StringBuilder("{text:\"\",font:\"minecraft:uniform\",extra:[");
            sb.append(Holo.tx("⌘ COMMAND GUARD", 0xB45CFF, true)).append(',').append(Holo.tx("  ● ONLINE", 0x55FF55, false)).append(",{text:\"\\n\"}");
            if (odT > 0) {
                boolean bl = (age / 3) % 2 == 0;
                sb.append(',').append(Holo.tx("▶ OVERDRIVE  ", bl ? 0xFFD84A : 0xFF8020, true)).append(',').append(Holo.tx(String.format(Locale.ROOT, "%.1fs", odT / 20f), 0xFFFFFF, false));
            } else {
                int fill = od / 100;
                sb.append(',').append(Holo.tx("OD ", 0xFFD84A, true)).append(',').append(Holo.tx("■".repeat(fill), 0xFFD84A, false))
                        .append(',').append(Holo.tx("□".repeat(10 - fill), 0x5A5040, false)).append(',').append(Holo.tx(" " + od / 10 + "%", 0xAAAAAA, false));
            }
            if (combo >= 2) sb.append(',').append(Holo.tx("   COMBO ×" + combo, combo >= 5 ? 0xFFB020 : 0xFFFFFF, true));
            sb.append(",{text:\"\\n\"}");
            sb.append(',').append(Holo.tx("────────────────────────", 0x404050, false));
            for (int i = 0; i < term.size(); i++) {
                String[] ln = term.get(i);
                boolean last = i == term.size() - 1;
                String body = ln[0];
                int col = last ? Integer.parseInt(ln[1]) : 0x707080;
                if (last) {
                    int shown = Math.min(body.length(), (age - termAt) * 3 + 1);
                    body = body.substring(0, shown) + ((age / 6) % 2 == 0 || shown < ln[0].length() ? "▌" : " ");
                }
                sb.append(",{text:\"\\n\"},").append(Holo.tx(last ? "› " : "  ", 0x55FF55, true)).append(',').append(Holo.tx(body, col, last));
            }
            return sb.append("]}").toString();
        }

        void sayFloat(String text, int color, Vec3 at, float scale) {
            String id = "t" + (uid++);
            spawnFx(String.format(Locale.ROOT, "summon text_display %.3f %.3f %.3f {Tags:[\"jjk.laserfx\",\"%s\",\"%s.%s\"],billboard:\"center\",background:1610612736,shadow:1b,"
                            + "line_width:400,teleport_duration:2,brightness:{sky:15,block:15},text:{text:\"%s\",color:\"#%06X\"},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
                    at.x, at.y, at.z, tag, tag, id, text.replace("\"", "'"), color & 0xFFFFFF, scale, scale, scale), id, null);
            texts.add(new Floater(id, at, 30));
        }

        void summon() {
            ServerPlayer p = p();
            display("main", pos, "minecraft:command_block", true);
            // v2 장갑 셸: 정육면체 6면에 떠 있는 장갑판 (모션 때 열리고, 대기 때 닫힘) + 판 테두리 발광 띠
            orbitK = 4.5; orbitV = 0; squash = 0.05; squashV = 0;          // 조립: 판이 멀리서 날아와 맞물림
            java.util.function.Supplier<Vec3> at0 = () -> pos;
            int FOREVER0 = 100_000_000;
            Holo.sprite(level, tag, pos, "runering", 0xB45CFF, "fixed", Holo.m(Holo.FLAT, 0.01, 0.01, 0.01, Vec3.ZERO, false), FOREVER0, 1)
                    .follow(at0, new Vec3(0, -3.4, 0)).spin(-2.5).key(6, Holo.m(Holo.FLAT, 11, 11, 11, Vec3.ZERO, false), 14);
            Holo.sprite(level, tag, pos, "circle", 0x55FFFF, "fixed", Holo.m(Holo.FLAT, 0.01, 0.01, 0.01, Vec3.ZERO, false), FOREVER0, 1)
                    .follow(at0, new Vec3(0, -3.3, 0)).spin(3.5).key(10, Holo.m(Holo.FLAT, 7, 7, 7, Vec3.ZERO, false), 12);
            Holo.sprite(level, tag, pos, "ring", 0xFFFFFF, "fixed", Holo.m(Holo.mul(Holo.rotX(1.2), Holo.FLAT), 0.01, 0.01, 0.01, Vec3.ZERO, false), FOREVER0, 1)
                    .follow(at0, Vec3.ZERO).spin(6).key(14, Holo.m(Holo.mul(Holo.rotX(1.2), Holo.FLAT), 12, 12, 12, Vec3.ZERO, false), 12);
            Holo.sprite(level, tag, pos, "orb", 0xB45CFF, "center", Holo.bb(0.01, 0, Vec3.ZERO), FOREVER0, 1)
                    .follow(at0, Vec3.ZERO).key(8, Holo.bb(9, 0, Vec3.ZERO), 10);
            if (p != null) cutIn(p, "커맨드 수호", "COMMAND GUARD · ONLINE", 0xB45CFF, 1);
            Holo.banner(level, tag + "x", pos.add(0, 7.5, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⌘ COMMAND GUARD", 0xB45CFF, true) + "]}", 2.6, 40);
            Holo.shockTex(level, tag + "x", pos, 14, 0xB45CFF, 10);
            Holo.flare(level, tag + "x", pos, 16, 0xFFFFFF, 12);
            Holo.sfx(level, pos, "choir", 1.8f, 1.1f); Holo.sfx(level, pos, "riser", 1.6f, 1.3f);
            // 오라: 3겹 궤도를 도는 작은 커맨드 블록 12개 + 코드 글자 후광 8개 + 발밑 마법진 글자 16개
            String[] kinds = {"minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block"};
            for (int i = 0; i < 12; i++) displayG("o" + i, "aura", pos, kinds[i % 3], true, 4f);
            java.util.function.Supplier<Vec3> at = () -> pos;
            int FOREVER = 100_000_000;
            double hc = 3.3;
            Vec3[] v = new Vec3[8];
            for (int i = 0; i < 8; i++) v[i] = new Vec3((i & 1) == 0 ? -hc : hc, (i & 2) == 0 ? -hc : hc, (i & 4) == 0 ? -hc : hc);
            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] ed : edges)     // 회전하는 네온 와이어프레임 큐브
                Holo.block(level, tag, pos, "minecraft:white_concrete", 0xB45CFF, Holo.zero(true), FOREVER, 1)
                        .key(2, Holo.bar(v[ed[0]], v[ed[1]], 0.09, 0.09, 0), 12).attach(at, () -> wireYaw).wobble(28, 0.031);
            for (int i = 0; i < 8; i++)   // 꼭짓점 빛 구슬
                Holo.block(level, tag, pos, "minecraft:light_blue_stained_glass", 0x55FFFF, Holo.zero(true), FOREVER, 1)
                        .key(3 + i, Holo.m(Holo.I(), 0.32, 0.32, 0.32, v[i], true), 6).attach(at, () -> wireYaw).wobble(28, 0.031);
            double[][] tilt = Holo.rotX(1.25);
            for (int i = 0; i < 28; i++)  // 기울어진 홀로 고리 (시안)
                Holo.block(level, tag, pos, "minecraft:cyan_stained_glass", 0x40E8FF, Holo.zero(true), FOREVER, 1)
                        .key(2 + i / 4, Holo.seg(tilt, i * Math.PI * 2 / 28, 5.0, 28, 0.32, 0.05), 8).attach(at, () -> -ringYaw * 1.3);
            for (int i = 0; i < 32; i++)  // 수평 홀로 고리 (보라, 반대로)
                Holo.block(level, tag, pos, "minecraft:purple_stained_glass", 0xB45CFF, Holo.zero(true), FOREVER, 1)
                        .key(4 + i / 4, Holo.seg(Holo.I(), i * Math.PI * 2 / 32, 5.8, 32, 0.16, 0.04), 8).attach(at, () -> ringYaw * 0.75);
            // 홀로 터미널 (수호 위, 항상 카메라를 봄)
            termEl = Holo.text(level, tag, pos.add(0, 4.2, 0), "{text:\"\"}", "center", 0xD00A0E16, false,
                    Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), FOREVER, 1);
            termEl.follow(at, new Vec3(0, 4.3, 0)).text(this::termText).key(4, Holo.m(Holo.I(), 0.62, 0.62, 0.62, Vec3.ZERO, false), 6);
            Vec3 feet = p != null ? p.position() : pos;
            for (int i = 0; i < 16; i++) textG("c" + i, "circ", feet.add(0, 0.05, 0), GLYPHS[i % GLYPHS.length], i % 2 == 0 ? 0xB45CFF : 0x55FFFF, "fixed", true);
            textG("ring", "circ", feet.add(0, 0.05, 0), "⟨ /execute as @s run protect ⟩", 0x55FF55, "fixed", false);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 1.2f);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2f, 0.8f);
            level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 200, 2, 2, 2, 1.5);
            say("/summon command_guard ~ ~ ~", 0x55FF55, pos.add(0, 3.2, 0), 1.2f);
            glyphs(feet, 5, 40, 0xB45CFF);
            glyphs(feet, 10, 40, 0x55FFFF);
            pillar(feet, 80, 24, 4, "minecraft:purple_stained_glass", 11820287);
            pillar(feet, 80, 24, 1.2, "minecraft:white_concrete", 16777215);
            wave(feet.add(0, 0.2, 0), 16, 16, 0xB45CFF);
            burst(pos, 1.2, 160, 0xB45CFF);
            flash(feet, 30, "p");
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 80, 0.3, 0.3, 0.3, 0.4);
            if (p != null) p.displayClientMessage(Component.literal("§d§l커맨드 수호 §7— Z/C 명령 선택 · 우클릭 실행 · 웅크리고 우클릭 「/kill」"), true);
        }

        void remove() {
            Holo.shards(level, tag + "x", pos, "minecraft:command_block", 14, 4);
            Holo.shards(level, tag + "x", pos, "minecraft:purple_stained_glass", 18, 5);
            Holo.flare(level, tag + "x", pos, 14, 0xB45CFF, 10);
            Holo.shockTex(level, tag + "x", pos, 12, 0xB45CFF, 8);
            for (int i = 0; i < 6; i++) Holo.codeDrop(level, tag + "x", pos.add((level.random.nextDouble() - 0.5) * 6, 4, (level.random.nextDouble() - 0.5) * 6), 7, 0xB45CFF, new java.util.Random(age * 7L + i));
            Holo.sfx(level, pos, "glitch", 1.5f, 0.8f); Holo.sfx(level, pos, "shatter", 1.4f, 1.1f);
            for (Fx f : fx) { if (f instanceof TickFreeze tf) tf.release(); if (f instanceof Domain dm) dm.abort(this); }
            for (Golem g : golems) g.discard();
            golems.clear();
            bars.clear();
            Holo.killOwner(tag);
            for (var e : subEnt.values()) e.discard();
            subEnt.clear(); grpEnt.clear();
            cmd("kill @e[tag=" + tag + "]");
            ServerPlayer p = p();
            if (p != null) { for (int k = 0; k < 6; k++) LaserFx.kill(level, p, "cgl" + k); if (flyT > 0) { flyT = 0; endCreative(p); } }
            for (net.minecraft.core.BlockPos bp : cage)
                if (level.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS)) level.destroyBlock(bp, false);
            cage.clear();
        }

        Vec3 target(ServerPlayer p, double range) {
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 t = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation();
            // 시선 근처 적이 있으면 그 적을 노림
            LivingEntity best = null; double bd = 1e9;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> e instanceof Enemy && e.isAlive())) {
                Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
                if (to.normalize().dot(look) < 0.96) continue;
                if (to.length() < bd) { bd = to.length(); best = e; }
            }
            return best != null ? best.position().add(0, best.getBbHeight() / 2, 0) : t;
        }

        // ── 우클릭 공격: 폭탄 → 화살 비 → 레이저
        void attack() {
            ServerPlayer p = p();
            if (p == null) return;
            int base0 = unsealed(p) ? MODES.length : BASE_MODES;
            if (mode >= base0) {
                if (chain(p)) { CommandInker.cast(p, mode - base0); return; }   // 체인 수호: 인커의 능력
                mode = 0;
            }
            if (atkCd > 0) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7명령어 실행 대기 %.1f초", atkCd / 20f)), true); return; }
            Vec3 to = target(p, mode == 2 ? 96 : 64);
            switch (mode) {
                case 0 -> {
                    say("/summon tnt ^ ^ ^ {Fuse:0}", 0xFF5555, pos.add(0, 3, 0), 1.0f);
                    for (int i = 0; i < 10; i++) bolts.add(new Bolt(Bolt.BOMB, pos, to.add((level.random.nextDouble() - 0.5) * 10, 0, (level.random.nextDouble() - 0.5) * 10), i * 2));
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 2f, 0.8f);
                    atkCd = 30;
                    final Vec3 bt = to;
                    later(44, () -> {
                        Holo.orb(level, tag, bt.add(0, 1.5, 0), 6, 0xFF7020, 2, 2, 8);
                        Holo.explosion(level, tag, bt, 5, 0xFF7020);
                        Holo.shock(level, tag, bt, 14, 0xFF7020, 10);
                        Holo.shock(level, tag, bt, 9, 0xFFFFFF, 6);
                        Holo.shards(level, tag, bt, groundName(level, bt.add(0, -0.5, 0)), 16, 5);
                        Holo.light(level, bt.add(0, 1, 0), 14);
                        boolean gr = MechManager.griefOn(level.getServer());
                        level.explode(p, bt.x, bt.y, bt.z, 6f, false, gr ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, bt.x, bt.y + 1, bt.z, 4, 2, 1, 2, 0);
                        for (LivingEntity e : foesNear(bt, 8)) hit(e, p, 40f);
                        Holo.slowmo(level, 12, 4);
                        say("/execute run chain_detonate ✔", 0xFF5533, null, 0);
                    });
                }
                case 1 -> {
                    say("/execute run summon arrow ^ ^ ^ x40", 0xFFFF55, pos.add(0, 3, 0), 1.0f);
                    for (int i = 0; i < 40; i++) {
                        Arrow a = new Arrow(level, pos.x, pos.y, pos.z, new ItemStack(Items.ARROW), null);
                        a.setOwner(p);
                        Vec3 d = to.subtract(pos);
                        a.shoot(d.x, d.y + d.horizontalDistance() * 0.04, d.z, 3.2f, 6f);
                        a.pickup = AbstractArrow.Pickup.DISALLOWED;
                        a.setCritArrow(true);
                        a.setBaseDamage(9.0);
                        if (i % 4 == 0) a.igniteForSeconds(20);
                        level.addFreshEntity(a);
                    }
                    // 하늘의 글자 포탈에서 쏟아지는 2차 화살비
                    Vec3 sky = to.add(0, 26, 0);
                    glyphs(sky, 6, 40, 0xFFE040);
                    glyphs(sky, 10, 40, 0xFFFFFF);
                    burst(sky, 0.6, 60, 0xFFE040);
                    final Vec3 tgt = to;
                    for (int w = 0; w < 4; w++) later(8 + w * 5, () -> {
                        for (int i = 0; i < 15; i++) {
                            double ox = (level.random.nextDouble() - 0.5) * 12, oz = (level.random.nextDouble() - 0.5) * 12;
                            Arrow a = new Arrow(level, sky.x + ox, sky.y, sky.z + oz, new ItemStack(Items.ARROW), null);
                            a.setOwner(p); a.shoot(0, -1, 0, 3.5f, 1.5f);
                            a.pickup = AbstractArrow.Pickup.DISALLOWED; a.setCritArrow(true); a.setBaseDamage(10.0);
                            level.addFreshEntity(a);
                        }
                        level.sendParticles(ParticleTypes.FIREWORK, sky.x, sky.y, sky.z, 40, 5, 0.3, 5, 0.05);
                        for (int k = 0; k < 8; k++) {
                            double ox = (level.random.nextDouble() - 0.5) * 12, oz = (level.random.nextDouble() - 0.5) * 12;
                            Holo.beamLayer(level, tag, sky.add(ox, -1, oz), new Vec3(sky.x + ox, tgt.y, sky.z + oz), 0.1, "minecraft:white_concrete", 0xFFE040, 1, 1, 3, k);
                        }
                        level.playSound(null, tgt.x, tgt.y + 20, tgt.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 3f, 0.5f);
                    });
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 2f, 0.7f);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 2f, 0.6f);
                    atkCd = 20;
                    final Vec3 at2 = to;
                    later(30, () -> { Holo.shock(level, tag, at2, 12, 0xFFE040, 10); Holo.circle(level, tag, at2, 6, 0xFFFFFF, 18, 0);
                        for (LivingEntity e : foesNear(at2, 8)) hit(e, p, 20f); });
                }
                case 3 -> { prison(p, to); atkCd = 60; }
                case 4 -> { storm(p, to); atkCd = 60; }
                case 5 -> { liftSlam(p, to); atkCd = 60; }
                case 6 -> { slowField(p); atkCd = 80; }
                case 7 -> { fx.add(new Meteor(this, to, 11, 0, true));
                    for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4;
                        fx.add(new Meteor(this, to.add(Math.cos(a) * 11, 0, Math.sin(a) * 11), 3.5, 10 + i * 3, false)); }
                    atkCd = 120; }
                case 8 -> { fx.add(new Singularity(this, to)); atkCd = 160; }
                case 9 -> { fx.add(new Barrier(this, to)); atkCd = 200; }
                case 10 -> { fx.add(new Clones(this, to)); atkCd = 160; }
                case 11 -> { fx.add(new TickFreeze(this, p, to)); atkCd = 240; }
                case 12 -> { fx.add(new SwordRain(this, to)); atkCd = 180; }
                case 13 -> { fx.add(new Orbital(this, to)); atkCd = 260; }
                case 14 -> { fx.add(new Dragon(this, to)); atkCd = 400; }
                case 15 -> { fx.add(new Tsunami(this, p, to)); atkCd = 120; }
                case 16 -> { fx.add(new WorldSlash(this, to)); atkCd = 160; }
                case 17 -> { fx.add(new Colossus(this, p, to)); atkCd = 300; }
                case 18 -> { fx.add(new Spear(this, to)); atkCd = 220; }
                case 19 -> { fx.add(new ChainLightning(this, to)); atkCd = 120; }
                case 20 -> { fx.add(new BladeStorm(this, p)); atkCd = 200; }
                case 21 -> { fx.add(new Nebula(this, to)); atkCd = 260; }
                case 22 -> { fx.add(new ChronoRewind(this, p, to)); atkCd = 220; }
                case 23 -> { fx.add(new Phoenix(this, to)); atkCd = 240; }
                case 24 -> { fx.add(new JudgmentEye(this, p, to)); atkCd = 300; }
                case 25 -> { fx.add(new CommandPlanet(this, p, target(p, 160))); atkCd = 1000; }
                case 26 -> { fx.add(new GravityCollapse(this, to)); atkCd = 180; }
                case 27 -> { fx.add(new Shrink(this, p, to)); atkCd = 160; }
                case 28 -> { fx.add(new HealthOne(this, to)); atkCd = 260; }
                case 29 -> { fx.add(new Banish(this, to)); atkCd = 200; }
                case 30 -> { fx.add(new Disarm(this, to)); atkCd = 140; }
                case 31 -> { fx.add(new CommandRain(this, p, to)); atkCd = 200; }
                case 32 -> { fx.add(new CubeCrush(this, to)); atkCd = 160; }
                case 33 -> { fx.add(new ScoreExecute(this, to)); atkCd = 220; }
                case 34 -> { fx.add(new TerrainClone(this, to)); atkCd = 160; }
                case 35 -> { fx.add(new Turrets(this, to)); atkCd = 260; }
                case 36 -> { fx.add(new DeleteCode(this, to)); atkCd = 240; }
                case 37 -> { fx.add(new Brainwash(this, to)); atkCd = 300; }
                case 38 -> { fx.add(new StickyTnt(this, to)); atkCd = 160; }
                case 39 -> { fx.add(new Dice(this, to)); atkCd = 200; }
                case 40 -> { fx.add(new HoleFlip(this, to)); atkCd = 220; }
                case 41 -> { fx.add(new GiantHand(this, to)); atkCd = 200; }
                case 42 -> { fx.add(new Apocalypse(this, to)); atkCd = 1200; }
                case 43 -> { fx.add(new InfiniteCube(this, to)); atkCd = 900; }
                case 44 -> { fx.add(new CommandGod(this, to)); atkCd = 1000; }
                case 45 -> { fx.add(new Reload(this, p)); atkCd = 1800; }
                case 46 -> { fx.add(new InfinityExec(this, to)); atkCd = 1200; }
                default -> {
                    say("/execute anchored eyes run laser ^ ^ ^96", 0x55FFFF, pos.add(0, 3, 0), 1.0f);
                    LaserFx.spawn(level, p, new String[][]{{"cgl0", "minecraft:white_concrete", "11820287"}, {"cgl1", "minecraft:magenta_stained_glass", "-1"},
                            {"cgl2", "minecraft:purple_stained_glass", "11820287"}, {"cgl3", "minecraft:white_stained_glass", "-1"},
                            {"cgl4", "minecraft:magenta_stained_glass", "16733695"}, {"cgl5", "minecraft:white_concrete", "16777215"}}, pos);
                    laserT = 50; laserTo = to;
                    Holo.sfx(level, pos, "charge", 1.6f, 1.6f);
                    Holo.screen(level, pos, 24, 7, 0, 500, 0.1f);
                    Holo.screen(level, pos, 24, 6, 0xFF55FF, 500, 1);
                    Holo.orb(level, tag, pos, 3.2, 0xFF55FF, 9, 1, 3);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 1.6f);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 1.8f);
                    atkCd = 40;
                }
            }
            level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 60, 1.5, 1.5, 1.5, 1.0);
            int[] COL = {0xFF5533, 0xFFE040, 0x55FFFF, 0x88DDFF, 0xFFFF66, 0xFFAA33, 0x8888FF, 0xFF3333, 0xB45CFF, 0x40E8FF, 0xFFD84A, 0xAAF0FF, 0xFF55FF, 0xFF3030, 0xB45CFF, 0xC08040, 0xFFFFFF, 0xFFAA33, 0xFFE070, 0x7FDFFF, 0x55FFFF, 0xFF55C8, 0x7FDFFF, 0xFF8A20, 0xFF3040, 0xB45CFF};
            String[] GL = {"minecraft:red_stained_glass", "minecraft:yellow_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:light_blue_stained_glass",
                    "minecraft:yellow_stained_glass", "minecraft:orange_stained_glass", "minecraft:blue_stained_glass", "minecraft:red_stained_glass",
                    "minecraft:purple_stained_glass", "minecraft:cyan_stained_glass", "minecraft:yellow_stained_glass", "minecraft:white_stained_glass", "minecraft:magenta_stained_glass",
                    "minecraft:red_stained_glass", "minecraft:purple_stained_glass", "minecraft:orange_stained_glass", "minecraft:white_stained_glass", "minecraft:orange_stained_glass",
                    "minecraft:yellow_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:cyan_stained_glass",
                    "minecraft:magenta_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:orange_stained_glass", "minecraft:red_stained_glass", "minecraft:purple_stained_glass"};
            castFx(to, COL[mode % COL.length], GL[mode % GL.length]);
            skillCard(COL[mode % COL.length]);
            startMotion(BLINK.contains(mode) ? 4 : ORBIT.contains(mode) ? 5 : SKY.contains(mode) ? 1 : GROUND.contains(mode) ? 2 : CHANNEL.contains(mode) ? 3 : 0, to, true);
            Holo.sfx(level, pos, "drum", 2f, 0.9f + (mode % 4) * 0.05f);
            int mc = COL[mode % COL.length];
            if (motionKind == 1) Holo.sfx(level, pos, "riser", 1.6f, 1.1f);
            if (motionKind == 3) {           // 시선 앞에 서서히 도는 세로 마법진 2겹
                Vec3 look = p.getLookAngle();
                Vec3 cc = p.getEyePosition().add(look.scale(5.2)).add(0, 1.4, 0);
                double[][] Tf = Holo.facing(look);
                for (int k = 0; k < 2; k++) {
                    double dir = k == 0 ? 1 : -1, s0 = k == 0 ? 6 : 7;
                    String tx = k == 0 ? "circle" : "runering";
                    Holo.sprite(level, tag, cc, tx, k == 0 ? mc : 0xFFFFFF, "fixed", Holo.m(Holo.mul(Tf, Holo.FLAT), 0.01, 0.01, 0.01, Vec3.ZERO, false), 34, 0)
                            .key(1, Holo.m(Holo.mul(Tf, Holo.mul(Holo.FLAT, Holo.rotZ(dir * 2.4))), s0, s0, s0, Vec3.ZERO, false), 12)
                            .key(13, Holo.m(Holo.mul(Tf, Holo.mul(Holo.FLAT, Holo.rotZ(dir * 4.8))), s0, s0, s0, Vec3.ZERO, false), 13)
                            .key(26, Holo.m(Holo.mul(Tf, Holo.mul(Holo.FLAT, Holo.rotZ(dir * 6.2))), 0.01, 0.01, 0.01, Vec3.ZERO, false), 6);
                }
                Holo.glow(level, tag, cc, 1.8, mc, 4, 18, 8);
                Holo.sfx(level, cc, "charge", 0.9f, 1.5f);
            }
            {
                double yr = Math.toRadians(p.getYRot());
                Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.7)).add(-Math.cos(yr) * 0.38, -0.35, -Math.sin(yr) * 0.38);
                Holo.flare(level, tag, hand, 1.6, COL[mode % COL.length], 8);
                Holo.glow(level, tag, hand, 0.4, COL[mode % COL.length], 1, 2, 4);
                Holo.decal(level, tag, p.position().add(0, 0.05, 0), "circle", COL[mode % COL.length], 0.5, 3.4, 3, 8, 5, 6, Holo.I());
                Holo.decal(level, tag, p.position().add(0, 0.07, 0), "runering", 0xFFFFFF, 0.5, 3.8, 4, 7, 5, -8, Holo.I());
            }
            int ccol = COL[mode % COL.length];
            cutIn(p, MODES[mode], SUBS[mode % SUBS.length], ccol, BIG.contains(mode) ? 1 : 0);
            final Vec3 ft = to;
            switch (mode) {
                case 7 -> cam(p, to, 26, 12, 70, 1900);
                case 8 -> later(70, () -> cam(p, ft.add(0, 2, 0), 22, 7, 90, 1600));
                case 12 -> later(74, () -> cam(p, ft, 28, 14, 60, 1600));
                case 13 -> later(44, () -> cam(p, ft, 42, 16, 80, 2000));
                case 14 -> cam(p, pos, 18, 6, 120, 1800);
                case 16 -> later(6, () -> cam(p, ft, 30, 10, 50, 1800));
                case 17 -> later(92, () -> cam(p, ft, 40, 16, 70, 2000));
                case 18 -> later(36, () -> cam(p, ft, 36, 10, 80, 1900));
                case 21 -> later(80, () -> cam(p, ft.add(0, 9, 0), 34, 4, 100, 2200));
                case 22 -> later(52, () -> cam(p, ft, 26, 16, 70, 1700));
                case 23 -> later(84, () -> cam(p, ft, 30, 12, 90, 1900));
                case 24 -> later(56, () -> cam(p, ft.add(0, 8, 0), 40, -4, 60, 2000));
                case 25 -> { }   // 전용 시네마틱 (PlanetCine)
                default -> { }
            }
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 30, 0.2, 0.2, 0.2, 0.35);
            // 조준선: 목표 근처의 적에게 LOCK
            LivingEntity lock = null; double ld = 5;
            for (LivingEntity e : foesNear(to, 5)) { double d = e.position().distanceTo(to); if (d < ld) { ld = d; lock = e; } }
            if (lock != null) Holo.reticle(level, tag, lock, COL[mode % COL.length], 34);
            // 콤보: 4초 안에 이어서 실행하면 연속 + 피해 증가
            combo = comboT > 0 ? combo + 1 : 1;
            comboT = 80;
            if (combo >= 2) {
                int cc = combo >= 10 ? 0xFF3040 : combo >= 5 ? 0xFFB020 : 0xFFFFFF;
                Holo.banner(level, tag, pos.add(0, 5.6, 0), "{text:\"\",extra:[" + Holo.tx("COMBO ", 0xB45CFF, true) + "," + Holo.tx("×" + combo, cc, true) + "]}", 1.3, 26);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.2f, Math.min(2f, 0.7f + combo * 0.08f));
            }
            atkCd = Math.max(8, atkCd * 3 / 5);                   // 기본 재사용 대기 40% 감소
            if (odT > 0) atkCd = Math.max(4, atkCd * 2 / 5);   // 오버드라이브: 재사용 대기 60% 감소
            if (combo >= 5 && combo % 5 == 0) later(6, () -> comboBurst(p));
        }

        // ═════ 공용 연출 도우미

        /** 실체 빔: a→b, 굵기 w, life 틱 동안 굵어졌다 가늘어짐. */
        void beam(Vec3 a, Vec3 b, int life, double w, String block, int glow) { Holo.beamLayer(level, tag, a, b, w, block, glow, 2, Math.max(1, life - 6), 4, uid++ * 0.7); }
        /** 하늘로 솟는 빛기둥. */
        void pillar(Vec3 at, double h, int life, double w, String block, int glow) { beam(at, at.add(0, h, 0), life, w, block, glow); }
        /** 바닥을 퍼져나가는 충격파 (파티클 원 + 실체 납작판). */
        void wave(Vec3 at, double r, int life, int rgb) { Holo.shock(level, tag, at, r, rgb, Math.max(4, life)); fx.add(new Wave(this, at, r, life, rgb)); }
        /** 사방으로 튀는 빛 폭발. */
        void burst(Vec3 at, double sp, int n, int rgb) {
            RandomSource rnd = level.random;
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = Math.asin(rnd.nextDouble() * 2 - 1);
                Vec3 v = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : i % 3 == 1 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.FIREWORK,
                        at.x, at.y, at.z, 0, v.x, v.y, v.z, sp * (0.5 + rnd.nextDouble()));
            }
            level.sendParticles(dust(rgb, 3.0f), at.x, at.y, at.z, n / 2, sp * 1.5, sp * 1.5, sp * 1.5, 0);
            level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, n, sp, sp, sp, 2.0);
        }
        void flash(Vec3 at, int dist, String kind) {
            cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..%d] add gojo.fxflash_%s", at.x, at.y, at.z, dist, kind));
        }
        /** 명령 실행 공통: 수호 → 목표로 명령 빔, 수호 아래 마법진, 목표 빛기둥·충격파. */
        void castFx(Vec3 to, int rgb, String glass) {
            Holo.beam(level, tag, pos, to, 1.3, rgb, 2, 4, 5);
            Holo.orb(level, tag, pos, 2.6, rgb, 3, 2, 5);
            Holo.circle(level, tag, pos.add(0, -3.0, 0), 3.6, rgb, 26, 0);
            Holo.circle(level, tag, to, 5.5, rgb, 32, 0);
            Holo.pillar(level, tag, to, 36, 2.4, rgb, 2, 6, 6);
            Holo.shock(level, tag, to, 11, rgb, 10);
            Holo.light(level, to.add(0, 1, 0), 16);
            Holo.light(level, pos, 10);
            Holo.flare(level, tag, to.add(0, 1, 0), 5, rgb, 9);
            Holo.flare(level, tag, pos, 3.5, rgb, 7);
            Holo.sparks(level, tag, to.add(0, 0.5, 0), 12, 5, rgb, 12);
            Holo.embers(level, tag, to, 8, 1.5, rgb, 30);
            Holo.dustRing(level, tag, to, 6, 10, 20);
            Holo.glow(level, tag, to.add(0, 1, 0), 2.2, rgb, 2, 2, 6);
            Holo.decal(level, tag, to.add(0, 0.07, 0), "crack", rgb, 1, 7, 3, 30, 12, 0, Holo.I());
            ServerPlayer op = p();
            if (op != null) {
                com.tubilt.jjk.net.JjkNet.toPlayer(op, new com.tubilt.jjk.net.Payloads.Screen(1, 0, 260, 0.07f));
                com.tubilt.jjk.net.JjkNet.toPlayer(op, new com.tubilt.jjk.net.Payloads.Screen(2, 0, 300, 1.4f));
            }
            Holo.sfx(level, pos, "whoosh", 1.4f, 1.1f);
            Holo.sfx(level, to, "magic", 1.2f, 0.9f + (mode % 5) * 0.05f);
            burst(pos, 0.6, 40, rgb);
            burst(to.add(0, 1, 0), 0.8, 40, rgb);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.5f, 0.6f);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.5f, 0.8f);
            cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..30] add gojo.fxshake_s", to.x, to.y, to.z));
        }

        static final String[] SUBS = {"명령 폭격 ─ TNT 일제 투하", "하늘을 덮는 화살의 장막", "관통하는 명령어 광선", "/fill 로 짓는 결정 감옥",
                "번개를 부르는 명령", "중력 반전 → 지면 강타", "시간의 흐름을 늦추다", "하늘이 갈라지고, 명령이 떨어진다", "모든 것을 삼키는 특이점",
                "세계의 경계를 접어 가두다", "다섯 분신의 일제 사격", "세계의 틱을 멈추다", "마법부여 대검 40자루, 그리고 거검", "궤도 위성 정밀 타격",
                "커맨드 블록 용의 강림", "대지가 일어나 밀려온다", "세계를 가르는 참격", "강철 명령의 거신 강림",
                "하늘에서 내리꽂히는 빛의 창", "적에서 적으로 튀는 번개", "주인을 감싸는 빛의 칼날 3겹",
                "은하가 감겨들어 초신성이 된다", "기록된 시간으로 되돌려 빚을 받아낸다", "불사조의 선회와 강하", "하늘의 눈이 모든 적을 심판한다",
                "우주에서 행성을 끌어와 떨어뜨린다", "중력을 끄고, 다시 켠다", "적의 크기를 4분의 1로", "체력 값을 1로 덮어쓴다",
                "250블록 위 하늘로 추방", "손에 든 것을 공기로 바꾼다",
                "명령 블록 36개가 연쇄로 떨어진다", "유리 큐브에 가둬 1블록으로 압축", "점수가 0이 되면 처형", "지형을 복제해 내리찍는다",
                "명령 블록 포탑 4기 전개", "선택자에 걸린 존재를 삭제",
                "적끼리 싸우게 만든다", "머리 위에 TNT 를 얹어준다", "1부터 6까지, 운명에 맡긴다", "빨아들이고, 뱉어낸다", "하늘에서 손바닥이 내려온다",
                "봉인 해제 ─ 이 세계의 적을 지운다", "봉인 해제 ─ 끝없는 명령 블록 감옥", "봉인 해제 ─ 30블록의 신", "봉인 해제 ─ 세계를 다시 읽는다", "봉인 해제 ─ 모든 명령을 한꺼번에"};
        static final java.util.Set<Integer> BIG = java.util.Set.of(7, 8, 12, 13, 14, 16, 17, 18, 21, 22, 23, 24, 25, 31, 34, 40, 41, 42, 43, 44, 45, 46);

        void cutIn(ServerPlayer p, String title, String sub, int color, int kind) {
            com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.CutIn(title, sub, color, kind));
            cmd(String.format(Locale.ROOT, "playsound jjk:fx.%s master %s %.2f %.2f %.2f %.1f 1", kind == 1 ? "stinger" : "plate", p.getStringUUID(), p.getX(), p.getY() + 1, p.getZ(), kind == 1 ? 1.0 : 0.7));
            if (kind == 1) {
                com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(6, color, 900, 1));
                com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(7, 0, 700, 0.12f));
                com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(5, color, 1400, 0.45f));
            }
        }
        /** 짧은 시네마틱 카메라 (#cam gojo.cfg = 0 이면 끔). */
        void cam(ServerPlayer p, Vec3 focus, double dist, double height, int span, int ms) {
            var sb = level.getServer().getScoreboard();
            var o = sb.getObjective("gojo.cfg");
            if (o != null) { var i = sb.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("#cam"), o); if (i != null && i.value() == 0) return; }
            if (p == null || !p.isAlive()) return;
            com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Cam(focus.x, focus.y, focus.z, (float) dist, (float) height,
                    (ms & 0xFFFF) | (((span + 720) & 0x7FF) << 16)));
        }

        /** 명령 실행 카드: 수호 위에 가로로 펼쳐졌다가 위로 접혀 사라지는 홀로 카드. */
        void skillCard(int rgb) {
            String name = MODES[mode];
            String bar = "━".repeat(Math.max(10, name.length() + 6));
            String t = "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(bar, rgb, false) + ",{text:\"\\n\"},"
                    + Holo.tx("▶ ", rgb, true) + "," + Holo.tx(name, 0xFFFFFF, true) + ",{text:\"\\n\"},"
                    + Holo.tx(String.format("CMD #%02d", mode + 1), 0x888888, false) + "," + Holo.tx(combo >= 1 ? "   COMBO ×" + (combo + 1) : "", 0xFFB020, true)
                    + (odT > 0 ? "," + Holo.tx("   ⚡OD", 0xFFD84A, true) : "") + ",{text:\"\\n\"}," + Holo.tx(bar, rgb, false) + "]}";
            Holo.Elem c = Holo.text(level, tag, pos.add(0, 6.4, 0), t, "center", 0xC8080A12, false, Holo.m(Holo.I(), 0.01, 1.0, 1.0, Vec3.ZERO, false), 34, 1);
            c.follow(() -> pos, new Vec3(0, 6.4, 0))
                    .key(1, Holo.m(Holo.I(), 1.25, 1.0, 1.0, Vec3.ZERO, false), 2)
                    .key(3, Holo.m(Holo.I(), 1.0, 1.0, 1.0, Vec3.ZERO, false), 3)
                    .key(26, Holo.m(Holo.I(), 1.0, 0.01, 1.0, new Vec3(0, 0.9, 0), false), 5);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
        }

        /** 수호의 모든 피해: 오버드라이브·콤보 배율 + 데미지 숫자 + 오버드라이브 게이지. */
        void hit(LivingEntity e, ServerPlayer p, float dmg) {
            if (e == null || !e.isAlive() || p == null) return;
            float mult = (odT > 0 ? 1.5f : 1f) * (1 + Math.min(combo, 20) * 0.03f);
            float before = e.getHealth() + e.getAbsorptionAmount();
            e.invulnerableTime = 0;
            e.hurtServer(level, level.damageSources().playerAttack(p), dmg * mult);
            float dealt = before - (e.isAlive() ? e.getHealth() + e.getAbsorptionAmount() : 0);
            if (dealt <= 0.05f) return;
            Holo.dmgNumber(level, tag, e.position().add(0, e.getBbHeight() + 0.2, 0), dealt, combo);
            if (odT == 0) { od = Math.min(1000, od + (int) Math.min(80, dealt * 0.6f + 2)); if (od >= 1000) overdrive(p); }
        }

        /** 주변 적(최대 10)에게 머리 위 HP 바. */
        void scanBars(ServerPlayer p) {
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> e instanceof Enemy && e.isAlive());
            near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            for (int i = 0; i < near.size() && bars.size() < 10; i++) {
                LivingEntity le = near.get(i);
                if (bars.containsKey(le)) continue;
                String name = le.getName().getString();
                Holo.Elem e = Holo.text(level, tag, le.position().add(0, le.getBbHeight() + 0.6, 0), "{text:\"\"}", "center", 0x70000000, false,
                        Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 100_000_000, 1);
                e.follow(() -> le.position(), new Vec3(0, le.getBbHeight() + 0.55, 0))
                        .key(1, Holo.m(Holo.I(), 0.55, 0.55, 0.55, Vec3.ZERO, false), 4)
                        .text(a -> {
                            float r = Math.max(0, le.getHealth() / Math.max(1, le.getMaxHealth()));
                            int n = 14, f = Math.max(r > 0 ? 1 : 0, Math.round(r * n));
                            int col = r > 0.5f ? 0x55FF55 : r > 0.25f ? 0xFFD84A : 0xFF4040;
                            return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(name + "  ", 0xDDDDDD, false) + ","
                                    + Holo.tx(String.valueOf((int) Math.ceil(le.getHealth())), 0xFFFFFF, true) + ",{text:\"\\n\"},"
                                    + Holo.tx("█".repeat(f), col, false) + "," + Holo.tx("█".repeat(n - f), 0x303030, false) + "]}";
                        });
                bars.put(le, e);
            }
        }

        /** 처치: ELIMINATED + 파편 + 오버드라이브 보너스. */
        void eliminated(ServerPlayer p, LivingEntity le) {
            Vec3 at = le.position().add(0, le.getBbHeight() * 0.6, 0);
            boolean elite = le.getMaxHealth() >= 80;
            Holo.banner(level, tag, at.add(0, 1.2, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("✖ ", 0xFF3040, true) + ","
                    + Holo.tx(elite ? "ELITE ELIMINATED" : "ELIMINATED", 0xFFFFFF, true) + "]}", elite ? 1.6 : 1.0, 26);
            Holo.shards(level, tag, le.position(), "minecraft:command_block", elite ? 14 : 6, elite ? 5 : 2.5);
            Holo.shock(level, tag, le.position(), elite ? 8 : 3.5, 0xFF3040, 7);
            level.sendParticles(dust(0xFF3040, 2.2f), at.x, at.y, at.z, 20, 0.4, 0.5, 0.4, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 0.6f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5f, 0.8f);
            say("/kill " + le.getName().getString() + "  ✔", 0xFF6070, null, 0);
            if (odT == 0) { od = Math.min(1000, od + (elite ? 200 : 40)); if (od >= 1000) overdrive(p); }
            if (elite) Holo.slowmo(level, 10, 6);
        }

        void domain() {
            ServerPlayer p = p();
            if (p == null) return;
            domainCd = 1440;
            fx.add(new Domain(this, p));
            cutIn(p, "領域展開", "개발자 모드 ─ Developer Mode", 0x40E8FF, 1);
            cam(p, p.position().add(0, 2, 0), 30, 16, 160, 2600);
        }

        /** 콤보 5·10·15…: 수호가 주변 적 최대 8명에게 유도 광선 일제 발사. */
        void comboBurst(ServerPlayer p) {
            List<LivingEntity> foes = foesNear(p.position(), 26);
            foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            Holo.banner(level, tag, pos.add(0, 6.6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("COMBO BURST ", 0xFFB020, true) + "," + Holo.tx("×" + combo, 0xFFFFFF, true) + "]}", 1.8, 30);
            Holo.flare(level, tag, pos, 12, 0xFFB020, 12);
            Holo.shockTex(level, tag, pos.add(0, -3, 0), 12, 0xFFB020, 10);
            Holo.sfx(level, pos, "stinger", 1.4f, 1.15f);
            Holo.screen(level, pos, 30, 6, 0xFFB020, 600, 1);
            startMotion(3, pos, true);
            for (int i = 0; i < Math.min(8, foes.size()); i++) {
                LivingEntity e = foes.get(i);
                later(2 + i * 2, () -> {
                    if (!e.isAlive()) return;
                    Vec3 tp = e.position().add(0, e.getBbHeight() / 2, 0);
                    Holo.beam(level, tag, pos, tp, 0.9, 0xFFB020, 1, 2, 4);
                    Holo.explosion(level, tag, e.position(), 2, 0xFFB020);
                    hit(e, p, 45f);
                });
            }
        }

        /** 자동 사격: 2초마다 주인 근처 가장 가까운 적에게 짧은 광선. */
        void autoFire(ServerPlayer p) {
            LivingEntity best = null; double bd = 18 * 18;
            for (LivingEntity e : foesNear(p.position(), 18)) { double d = e.distanceToSqr(p); if (d < bd) { bd = d; best = e; } }
            if (best == null) return;
            Vec3 tp = best.position().add(0, best.getBbHeight() / 2, 0);
            Holo.beam(level, tag, pos, tp, 0.5, 0xB45CFF, 1, 1, 4);
            Holo.flare(level, tag, pos, 2.5, 0xB45CFF, 5);
            Holo.explosion(level, tag, best.position(), 1.1, 0xB45CFF);
            hit(best, p, 14f);
            startMotion(0, tp, false);
        }

        /** 피격 반격: 공격자에게 번개 + 둔화 (3초 쿨). */
        void retaliate(ServerPlayer p, LivingEntity atk) {
            if (retaliateCd > 0 || !atk.isAlive() || atk == p) return;
            retaliateCd = 60;
            Vec3 tp = atk.position().add(0, atk.getBbHeight() / 2, 0);
            Holo.lightning(level, tag, pos, tp, 0xB45CFF, 1.0, 6);
            Holo.explosion(level, tag, atk.position(), 1.6, 0xB45CFF);
            Holo.hitbox(level, tag, atk, 0xFF3040, 14);
            hit(atk, p, 30f);
            atk.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 3));
            say("/damage @s[attacker] 30 · 반격", 0xFF6070, null, 0);
            startMotion(0, tp, false);
        }

        /** 오버드라이브: 12초간 피해 ×1.5, 재사용 대기 60% 감소, 신속·힘, 황금 오라. */
        void plateBlock(String block, int glow) {
            for (int i = 0; i < 6; i++) {
                var e = sub("pl" + i);
                if (e != null) cmd("data merge entity " + e.getStringUUID() + " {block_state:{Name:\"" + block + "\"},glow_color_override:" + glow + "}");
            }
        }

        void overdrive(ServerPlayer p) {
            odT = 240; od = 0;
            Vec3 c = p.position();
            Holo.circle(level, tag, c, 7, 0xFFD84A, 44, 0);
            Holo.circle(level, tag, c.add(0, 5.5, 0), 4.2, 0xFFFFFF, 44, 0);
            Holo.pillar(level, tag, c, 90, 5, 0xFFD84A, 3, 14, 8);
            Holo.shock(level, tag, c, 24, 0xFFD84A, 12);
            Holo.shock(level, tag, c, 14, 0xFFFFFF, 8);
            Holo.orb(level, tag, pos, 4.5, 0xFFD84A, 4, 8, 8);
            Holo.banner(level, tag, c.add(0, 3.4, 0), "{text:\"\",extra:[" + Holo.tx("OVERDRIVE", 0xFFD84A, true) + "]}", 3.2, 44);
            Holo.light(level, c.add(0, 1, 0), 40);
            flash(c, 40, "o");
            Holo.explosion(level, tag, c, 3, 0xFFD84A);
            Holo.embers(level, tag, c, 24, 3, 0xFFD84A, 50);
            Holo.screen(level, c, 30, 5, 0xFFD84A, 2400, 0.6f);
            Titan.shake(level, c, 30, true);
            burst(c.add(0, 1.5, 0), 1.6, 200, 0xFFD84A);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 240, 1, false, false, true));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 240, 1, false, false, true));
            level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5f, 1.3f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 1.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 2f, 0.6f);
            say("/function guard:overdrive", 0xFFD84A, null, 0);
            cutIn(p, "OVERDRIVE", "피해 ×1.5 · 재사용 대기 -60%" + (domainCd == 0 ? " · 각성 가능" : ""), 0xFFD84A, 1);
            p.displayClientMessage(Component.literal(domainCd == 0 ? "§6§l⚡ OVERDRIVE ⚡ §b§l각성 가능 §7— 웅크리고 정면 우클릭: 영역 전개"
                    : "§6§l⚡ OVERDRIVE ⚡ §e피해 ×1.5 · 재사용 대기 60% 감소 (12초)"), true);
        }

        // ═════════════════════════ 모션 엔진
        //  · 위치는 스프링(가속도+감쇠) → 관성과 오버슈트가 있는 유기적 움직임
        //  · 예비동작(움츠림·에너지 수렴) → 타격(늘어남·잔상) → 여운(튕김·안정)
        //  · 몸체: 속도 방향으로 기울고(lean) 늘어나며(stretch) 착지·발사 순간 찌그러짐(squash)
        //  · 오라 궤도는 예비동작 때 조여들었다가 발사 순간 크게 퍼짐
        static final int[] MOTION_LEN = {18, 36, 32, 34, 28, 34};

        void startMotion(int kind, Vec3 to, boolean big) {
            motionKind = kind; motionLen = motionT = MOTION_LEN[Math.max(0, Math.min(kind, MOTION_LEN.length - 1))]; motionTo = to;
            int c = 0xB45CFF;
            if (!big) { converge(pos, 4, 14, c); orbitV -= 0.15; return; }
            // 예비동작 공통: 사방에서 빛줄기가 수호로 빨려듦 + 고리 수축
            Holo.ringT(level, tag, pos, 7.5, 0.6, 22, 0.25, 0.05, c, 7, 0, 3, Holo.rotX(0.4 + level.random.nextDouble()), 9, true);
            Holo.ringT(level, tag, pos, 6.0, 0.4, 18, 0.18, 0.05, 0xFFFFFF, 6, 0, 3, Holo.rotZ(0.9 + level.random.nextDouble()), -11, true);
            converge(pos, 6, 40, c);
            Holo.sfx(level, pos, "charge", 1.0f, 1.9f);
            orbitV -= 0.25;      // 오라 조임
        }

        /** 반지름 r 구면에서 중심으로 빨려드는 입자. */
        void converge(Vec3 c, double r, int n, int rgb) {
            RandomSource rnd = level.random;
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = Math.asin(rnd.nextDouble() * 2 - 1);
                Vec3 o = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(r);
                Vec3 s0 = c.add(o);
                level.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, s0.x, s0.y, s0.z, 0, -o.x, -o.y, -o.z, 0.09);
            }
            level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, n, r * 0.4, r * 0.4, r * 0.4, 1.6);
            level.sendParticles(dust(rgb, 2.0f), c.x, c.y, c.z, n / 3, r * 0.3, r * 0.3, r * 0.3, 0);
        }

        /** 발사 순간 공통 연출: 반동 충격파·섬광·찌그러짐·오라 폭발. */
        void release(Vec3 at, Vec3 dir, int rgb, double power) {
            Holo.shockTex(level, tag, at, 5 + power * 4, rgb, 7);
            Holo.flare(level, tag, at, 4 + power * 3, 0xFFFFFF, 7);
            Holo.ringT(level, tag, at, 0.5, 4 + power * 3, 20, 0.3, 0.05, rgb, 3, 0, 5, Holo.facing(dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir), 0, true);
            Holo.sparks(level, tag, at, 8, 3 + power * 2, rgb, 9);
            Holo.sfx(level, at, "whoosh", 1.2f + (float) power * 0.4f, 0.8f);
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            squashV -= 0.35 * power; orbitV += 0.45 * power;
            ServerPlayer op = p();
            if (op != null && op.position().distanceTo(at) < 20) com.tubilt.jjk.net.JjkNet.toPlayer(op, new com.tubilt.jjk.net.Payloads.Screen(2, 0, 260, (float) (0.8 + power)));
        }

        /** 잔상: 지금 몸체 자세 그대로 보라 유리 실루엣이 남았다 사라짐. */
        void afterimage(double sc, double[][] R) {
            Holo.ghost(level, tag, pos, (age & 1) == 0 ? "minecraft:purple_stained_glass" : "minecraft:magenta_stained_glass", 0xB45CFF, sc * 0.62, R, 7);
        }

        void motionTick(ServerPlayer p) {
            Vec3 goal = behind(p);
            double k = 0.085, damp = 0.78, sqT = 1, orbT = 1, lean = 0;
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            // ── 대기: 가까운 적을 경계 (그쪽으로 살짝 쏠림 + 오라 가속), 숨쉬기, 주기적 스캔 펄스
            LivingEntity watch = null;
            if (motionT == 0 && age % 5 == 0) {
                double bd = 14 * 14;
                for (LivingEntity e : foesNear(p.position(), 14)) { double d = e.distanceToSqr(p); if (d < bd) { bd = d; watch = e; } }
            }
            if (watch != null) { goal = goal.add(watch.position().subtract(goal).normalize().scale(1.2)); orbT = 1.15; }
            if (motionT == 0 && ++idleScanT >= 140) {
                idleScanT = 0;
                Holo.ringT(level, tag, pos, 0.4, 9, 26, 0.12, 0.03, 0x55FFFF, 10, 0, 4, Holo.I(), 0, true);
                Holo.sfx(level, pos, "data", 0.5f, 1.6f);
                jitter = 0.35;
            }
            double boost = 0;
            if (motionT > 0) {
                int e = motionLen - motionT;          // 경과 틱
                double u = e / (double) motionLen;
                Vec3 to = motionTo;
                Vec3 dirT = to.subtract(pos); if (dirT.lengthSqr() < 1e-4) dirT = look; dirT = dirT.normalize();
                int c = 0xB45CFF;
                switch (motionKind) {
                    case 0 -> {      // 반동 사격: 뒤로 움츠림 → 앞으로 찌름 → 튕기며 복귀
                        if (u < 0.3) { goal = goal.subtract(dirT.scale(2.4)).add(0, -0.6, 0); sqT = 0.72; orbT = 0.5; k = 0.18; }
                        else if (u < 0.5) { goal = goal.add(dirT.scale(3.2)).add(0, 0.4, 0); sqT = 1.45; orbT = 1.6; k = 0.5; damp = 0.55; boost = 1; }
                        else { k = 0.12; damp = 0.7; }
                        if (e == (int) (motionLen * 0.3)) release(pos, dirT, c, 0.6);
                    }
                    case 1 -> {      // 상승: 몸을 웅크려 가라앉음 → 솟구침(나선) → 정점 폭발 → 천천히 내려앉음
                        if (u < 0.22) { goal = p.position().add(0, 2.2, 0); sqT = 0.6; orbT = 0.4; k = 0.2; if (e % 2 == 0) converge(pos, 4, 12, c); }
                        else if (u < 0.6) {
                            double h = (u - 0.22) / 0.38, a = h * Math.PI * 3;
                            goal = p.position().add(Math.cos(a) * 3.5 * (1 - h), 4 + 11 * Math.sqrt(h), Math.sin(a) * 3.5 * (1 - h));
                            sqT = 1.5; orbT = 1.4; k = 0.35; damp = 0.62; boost = 1;
                        } else if (u < 0.78) { goal = p.position().add(0, 15, 0); sqT = 1.15; orbT = 2.2; boost = 0.7; }
                        else { k = 0.06; damp = 0.86; }
                        if (e == (int) (motionLen * 0.22)) {
                            release(pos, new Vec3(0, 1, 0), c, 1.0);
                            Holo.decal(level, tag, p.position().add(0, 0.06, 0), "crack", c, 1, 6, 2, 10, 8, 0, Holo.I());
                            Holo.sfx(level, pos, "riser", 1.6f, 1.2f);
                        }
                        if (e == (int) (motionLen * 0.6)) {
                            Holo.pillar(level, tag, pos, 80, 2.4, 0xFFFFFF, 2, 6, 8);
                            Holo.circle(level, tag, pos.add(0, 2, 0), 6, c, 26, 0);
                            Holo.ringT(level, tag, pos, 1, 14, 30, 0.4, 0.06, c, 4, 2, 8, Holo.I(), 6, true);
                            Holo.flare(level, tag, pos, 11, 0xFFFFFF, 12);
                            Holo.shockTex(level, tag, pos, 12, c, 9);
                            Holo.sfx(level, pos, "drum", 2.5f, 0.7f);
                            Holo.sfx(level, pos, "impact", 1.6f, 1.3f);
                            squashV += 0.5;
                        }
                    }
                    case 2 -> {      // 급강하: 목표 위로 날아올라 멈칫 → 내리꽂힘 → 충돌 찌그러짐 → 튕겨 복귀
                        Vec3 above = to.add(0, 9, 0);
                        if (above.distanceTo(p.position()) > 20) above = p.position().add(above.subtract(p.position()).normalize().scale(20));
                        Vec3 floor = new Vec3(above.x, to.y + 2.2, above.z);
                        if (u < 0.32) { goal = above; k = 0.22; damp = 0.7; sqT = 1.2; orbT = 0.6; }
                        else if (u < 0.45) { goal = above.add(0, 1.2, 0); k = 0.3; sqT = 0.7; orbT = 0.35; jitter = 0.25; if (e % 2 == 0) converge(pos, 5, 14, c); }
                        else if (u < 0.58) { goal = floor; k = 0.65; damp = 0.45; sqT = 1.8; orbT = 0.5; boost = 1; }
                        else if (u < 0.7) { goal = floor.add(0, 1, 0); sqT = 0.55; orbT = 2.4; k = 0.25; }
                        else { k = 0.1; damp = 0.74; }
                        if (e == (int) (motionLen * 0.45)) { Holo.sfx(level, pos, "whoosh", 2f, 0.6f); level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0); }
                        if (e == (int) (motionLen * 0.58)) {
                            Vec3 g0 = new Vec3(floor.x, to.y, floor.z);
                            Holo.explosion(level, tag, g0, 3.4, c);
                            Holo.shock(level, tag, g0, 11, 0xFFFFFF, 8);
                            Holo.decal(level, tag, g0.add(0, 0.07, 0), "crack", c, 1, 9, 2, 26, 12, 0, Holo.I());
                            Holo.shards(level, tag, g0, groundName(level, g0.add(0, -0.5, 0)), 12, 4);
                            Holo.dustRing(level, tag, g0, 7, 12, 22);
                            Holo.sfx(level, g0, "impact", 2.2f, 0.7f);
                            Holo.punch(level, g0, 4, c);
                            squashV -= 0.6; orbitV += 0.8;
                        }
                    }
                    case 4 -> {      // 순간이동: 데이터로 분해 → 목표 옆에서 재구성 → 사격 → 다시 분해해 복귀
                        Vec3 side = to.add(look.cross(new Vec3(0, 1, 0)).normalize().scale(5)).add(0, 3.5, 0);
                        if (side.distanceTo(p.position()) > 22) side = p.position().add(side.subtract(p.position()).normalize().scale(22));
                        if (e == 0 || e == (int) (motionLen * 0.62)) {
                            Holo.shards(level, tag, pos, "minecraft:command_block", 10, 2.5);
                            Holo.flare(level, tag, pos, 4, 0x55FFFF, 6);
                            Holo.sfx(level, pos, "glitch", 1.3f, 1.4f);
                            for (int i = 0; i < 6; i++) Holo.codeDrop(level, tag, pos.add((level.random.nextDouble() - 0.5) * 4, 3, (level.random.nextDouble() - 0.5) * 4), 5, 0x55FFFF, new java.util.Random(age * 31L + i));
                        }
                        if (u < 0.16) { sqT = 0.05; orbT = 0.05; k = 0.3; jitter = 0.5; }
                        else if (u < 0.62) {
                            if (e == (int) (motionLen * 0.16)) {   // 재구성: 목표 옆에 순간 등장
                                pos = side; vel = Vec3.ZERO; squash = 0.1;
                                Holo.flare(level, tag, side, 6, 0xFFFFFF, 8);
                                Holo.ringT(level, tag, side, 5, 0.4, 20, 0.3, 0.05, 0x55FFFF, 5, 0, 3, Holo.facing(dirT), 0, true);
                                Holo.sfx(level, side, "glitch", 1.5f, 0.7f);
                                Holo.sfx(level, side, "impact", 1.0f, 1.6f);
                            }
                            goal = side; k = 0.3; damp = 0.6; sqT = u < 0.3 ? 1.25 : 1; orbT = 1.5; boost = 0.6;
                            if (e == (int) (motionLen * 0.3)) release(pos, to.subtract(pos), 0x55FFFF, 0.8);
                        } else if (u < 0.78) { sqT = 0.05; orbT = 0.05; jitter = 0.5; goal = side; }
                        else {
                            if (e == (int) (motionLen * 0.78)) { pos = behind(p); vel = Vec3.ZERO; squash = 0.1; Holo.flare(level, tag, pos, 4, 0xB45CFF, 6); Holo.sfx(level, pos, "glitch", 1f, 1.1f); }
                            k = 0.14; damp = 0.7;
                        }
                    }
                    case 5 -> {      // 궤도 선회: 주인 주위를 고속으로 한 바퀴 돌며 잔상을 남긴 뒤 정면으로
                        if (u < 0.75) {
                            double a = Math.toRadians(p.getYRot()) + u / 0.75 * Math.PI * 2.2;
                            goal = p.position().add(-Math.sin(a) * 6.5, 3 + Math.sin(u * 12) * 1.2, Math.cos(a) * 6.5);
                            k = 0.55; damp = 0.5; sqT = 1.35; orbT = 1.8; boost = 1;
                            if (e % 3 == 0) Holo.sparks(level, tag, pos, 3, 2, c, 6);
                        } else { goal = eye.add(look.scale(5.5)).add(0, 1.6, 0); k = 0.2; damp = 0.7; }
                        if (e == (int) (motionLen * 0.75)) release(pos, look, c, 1.0);
                    }
                    default -> {     // 채널링: 내 앞으로 나와 문(門)처럼 고리를 세우고 떨며 힘을 모음 → 반동으로 밀려남
                        Vec3 front = eye.add(look.scale(5.5)).add(0, 1.8, 0);
                        if (u < 0.7) {
                            goal = front; k = 0.22; damp = 0.68; orbT = 0.55 + u; sqT = 1 - u * 0.25;
                            jitter = u * 0.35; boost = u;
                            if (e % 4 == 0) Holo.ringT(level, tag, pos, 0.5, 4.5 + u * 3, 20, 0.25, 0.05, c, 5, 0, 3, Holo.facing(look), 0, true);
                            if (e % 3 == 0) converge(pos, 5, 10, c);
                        } else {
                            goal = front.subtract(look.scale(2.5)); k = 0.4; damp = 0.6; sqT = 1.3; orbT = 1.9;
                        }
                        if (e == (int) (motionLen * 0.7)) release(pos, look, c, 1.1);
                    }
                }
                motionT--;
                if (motionT == 0) { Holo.flare(level, tag, pos, 3, 0xB45CFF, 6); squashV += 0.15; }
            }
            // ── 스프링 적분
            vel = vel.scale(damp).add(goal.subtract(pos).scale(k));
            double sp = vel.length();
            if (sp > 3.5) { vel = vel.scale(3.5 / sp); sp = 3.5; }
            pos = pos.add(vel);
            if (jitter > 0.01) { pos = pos.add((level.random.nextDouble() - 0.5) * jitter, (level.random.nextDouble() - 0.5) * jitter, (level.random.nextDouble() - 0.5) * jitter); jitter *= 0.8; }
            // 찌그러짐·오라 반지름도 스프링 (말랑한 반동)
            squashV = squashV * 0.7 + (sqT + Math.min(0.5, sp * 0.18) - squash) * 0.3; squash = Math.max(0.03, squash + squashV);
            orbitV = orbitV * 0.72 + (orbT - orbitK) * 0.22; orbitK = Math.max(0.05, orbitK + orbitV);
            spinSpeed += ((1 + boost * 14 + sp * 3) - spinSpeed) * 0.3;
            scaleK += ((1 + boost * 0.35) - scaleK) * 0.35;
            double sv = Math.min(spinSpeed, 5);          // 회전 속도 상한 (너무 빠르게 돌지 않게)
            spin += 0.025 * sv;
            wireYaw += 1.6 * sv;
            ringYaw += 1.8 * sv;
            double breathe = 1 + Math.sin(age * 0.09) * 0.035;
            double sc = 5 * scaleK * breathe;
            double[][] R = bodyRot(spin, Math.sin(age * 0.02) * 0.25 + boost * 0.6, vel);
            move("main", pos, bodyMat(R, sc, squash, vel));
            if (sp > 0.55 && (age % (sp > 1.2 ? 1 : 2)) == 0) afterimage(sc * Math.min(1, squash), R);
            if (sp > 0.9) level.sendParticles(dust(0xB45CFF, 2.4f), pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0);
        }

        static final Vec3[] FACE = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
        /** 장갑판 6장: 면 법선 방향으로 d 만큼 떨어져 몸체와 함께 회전. 열릴수록 멀어지고 바깥으로 젖혀짐. */
        void plates(double[][] R, double sc) {
            if (age % 2 != 0 && motionT == 0) return;
            double open = Math.max(0, orbitK - 1), close = Math.max(0, 1 - orbitK);
            double d = sc * 0.58 + open * 3.2 - close * 0.4, side = sc * 0.82;
            for (int i = 0; i < 6; i++) {
                Vec3 n = FACE[i];
                double[][] Rf = Holo.mul(R, Holo.facing(n));          // 판의 y축 = 면 법선
                double tilt = Math.min(0.7, open * 0.35) * ((i % 2 == 0) ? 1 : -1);
                double[][] Rt = Holo.mul(Rf, Holo.rotX(tilt));
                Vec3 c = Holo.apply(R, n.scale(d));
                shape("pl" + i, Holo.m(Rt, side, 0.32, side, c, true));
                shape("pe" + i, Holo.m(Rt, side * 0.7, 0.36, 0.12, c, true));
            }
        }

        /** 몸체 회전: 자전(yaw·tilt) 후 속도 방향으로 기울기(lean). */
        static double[][] bodyRot(double yaw, double tilt, Vec3 v) {
            double ca = Math.cos(yaw), sa = Math.sin(yaw), cb = Math.cos(tilt), sb = Math.sin(tilt);
            double[][] r = {{ca, sa * sb, sa * cb}, {0, cb, -sb}, {-sa, ca * sb, ca * cb}};
            double sp = v.length();
            if (sp < 1e-3) return r;
            Vec3 axis = new Vec3(0, 1, 0).cross(v);
            if (axis.lengthSqr() < 1e-6) return r;
            axis = axis.normalize();
            double ang = Math.min(0.9, sp * 0.45);
            return Holo.mul(axisRot(axis, ang), r);
        }
        static double[][] axisRot(Vec3 a, double t) {
            double c = Math.cos(t), s = Math.sin(t), C = 1 - c, x = a.x, y = a.y, z = a.z;
            return new double[][]{{c + x * x * C, x * y * C - z * s, x * z * C + y * s}, {y * x * C + z * s, c + y * y * C, y * z * C - x * s}, {z * x * C - y * s, z * y * C + x * s, c + z * z * C}};
        }
        /** 늘어남/찌그러짐: 속도 방향(없으면 세로)으로 squash 배, 수직 방향은 부피 보존. */
        static float[] bodyMat(double[][] R, double sc, double squash, Vec3 v) {
            Vec3 d = v.lengthSqr() > 0.04 ? v.normalize() : new Vec3(0, 1, 0);
            double a = squash, b = 1 / Math.sqrt(Math.max(0.05, squash));
            if (squash < 0.15) b = squash * 6;           // 거의 사라질 땐 전체가 작아짐 (순간이동)
            double[] dv = {d.x, d.y, d.z};
            double[][] S = new double[3][3];
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) S[i][j] = (i == j ? b : 0) + (a - b) * dv[i] * dv[j];
            double[][] M = Holo.mul(S, R);
            float[] o = new float[16];
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) o[i * 4 + j] = (float) (M[i][j] * sc);
            o[15] = 1;
            return o;
        }

        List<LivingEntity> foesNear(Vec3 c, double r) {
            return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> e instanceof Enemy && e.isAlive());
        }

        // ── /fill 감옥: 목표 근처 적들을 유리 감옥에 6초간 가둠
        void prison(ServerPlayer p, Vec3 to) {
            say("/fill ~-1 ~ ~-1 ~1 ~2 ~1 glass hollow", 0x88DDFF, pos.add(0, 3, 0), 1.0f);
            List<net.minecraft.core.BlockPos> placed = new ArrayList<>();
            for (LivingEntity e : foesNear(to, 12)) {
                e.invulnerableTime = 0; hit(e, p, 12f);
                net.minecraft.core.BlockPos b = e.blockPosition();
                int w = Math.max(1, (int) Math.ceil(e.getBbWidth() / 2)), h = (int) Math.ceil(e.getBbHeight()) + 1;
                for (int x = -w; x <= w; x++) for (int y = -1; y <= h; y++) for (int z = -w; z <= w; z++) {
                    boolean wall = x == -w || x == w || z == -w || z == w || y == -1 || y == h;
                    if (!wall) continue;
                    net.minecraft.core.BlockPos bp = b.offset(x, y, z);
                    if (level.getBlockState(bp).isAir()) { level.setBlock(bp, net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 3); placed.add(bp); }
                }
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 120, 4));
                final LivingEntity ce = e;
                double cr = ce.getBbWidth() / 2 + 0.7, ch = ce.getBbHeight() + 0.6;
                for (int k = 0; k < 8; k++) {
                    double ang = k * Math.PI / 4;
                    Vec3 b0 = new Vec3(Math.cos(ang) * cr, -0.1, Math.sin(ang) * cr), b1 = b0.add(0, ch, 0);
                    Holo.block(level, tag, ce.position(), "minecraft:light_blue_stained_glass", 0x88DDFF, Holo.bar(b0, b0.add(0, 0.01, 0), 0.01, 0.01, 0), 122, 1)
                            .follow(ce::position, Vec3.ZERO).key(1 + k, Holo.bar(b0, b1, 0.14, 0.14, 0), 4).key(116, Holo.bar(b0.scale(2), b1.scale(2), 0.01, 0.01, 0), 5);
                }
                Holo.ringT(level, tag, ce.position().add(0, ch, 0), cr * 0.4, cr, 12, 0.18, 0.06, 0x88DDFF, 6, 104, 6, Holo.I(), 3, true);
                Holo.banner(level, tag, ce.position().add(0, ch + 0.8, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⛓ LOCKED", 0x88DDFF, true) + "]}", 0.9, 40);
                level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY() + 1, e.getZ(), 40, 0.6, 1, 0.6, 1);
                pillar(e.position(), 24, 20, 1.6, "minecraft:light_blue_stained_glass", 8973055);
                glyphs(e.position(), 2.4, 120, 0x88DDFF);
                burst(e.position().add(0, 1, 0), 0.5, 30, 0x88DDFF);
            }
            cage.addAll(placed);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 3f, 0.5f);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 3f, 0.5f);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 2f, 0.7f);
            later(120, () -> {
                for (net.minecraft.core.BlockPos bp : placed)
                    if (level.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS)) level.destroyBlock(bp, false);
                cage.removeAll(placed);
            });
        }

        // ── /summon 번개 폭풍: 목표 주변 적마다 번개 + 피해
        void storm(ServerPlayer p, Vec3 to) {
            say("/execute at @e[type=!player,distance=..10] run summon lightning_bolt", 0xFFFF88, pos.add(0, 3, 0), 1.0f);
            List<LivingEntity> foes = foesNear(to, 16);
            for (int k = 0; k < 6; k++) later(k * 4, () -> {       // 먹구름
                level.sendParticles(ParticleTypes.LARGE_SMOKE, to.x, to.y + 22, to.z, 120, 12, 1.5, 12, 0.01);
                level.sendParticles(ParticleTypes.SQUID_INK, to.x, to.y + 21, to.z, 60, 12, 1, 12, 0.01);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 21, to.z, 40, 12, 1, 12, 0.3);
            });
            flash(to, 50, "w");
            for (int i = 0; i < 12; i++) { double a = level.random.nextDouble() * Math.PI * 2, r = 3 + level.random.nextDouble() * 14;
                final Vec3 q = to.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                later(4 + i * 3, () -> { Laser.bolt(level, q); Holo.lightning(level, tag, q.add((level.random.nextDouble() - 0.5) * 8, 36, (level.random.nextDouble() - 0.5) * 8), q, 0xFFFF66, 0.8, 6); wave(q.add(0, 0.2, 0), 5, 8, 0xFFFF66);
                    pillar(q, 30, 6, 1.0, "minecraft:yellow_stained_glass", 16777062);
                    for (LivingEntity e : foesNear(q, 4)) { e.invulnerableTime = 0; hit(e, p, 18f); } }); }
            for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3; final Vec3 q = to.add(Math.cos(a) * 7, 0, Math.sin(a) * 7); later(i * 2, () -> Laser.bolt(level, q)); }
            if (foes.isEmpty()) { Laser.bolt(level, to); return; }
            for (int i = 0; i < foes.size(); i++) {
                LivingEntity e = foes.get(i);
                later(i * 4, () -> {
                    if (!e.isAlive()) return;
                    Laser.bolt(level, e.position());
                    Holo.lightning(level, tag, e.position().add(0, 42, 0), e.position().add(0, 1, 0), 0xFFFFAA, 1.6, 8);
                    Holo.lightning(level, tag, e.position().add(6, 30, -4), e.position().add(0, 1, 0), 0xFFFF66, 0.7, 6);
                    pillar(e.position(), 40, 8, 2.4, "minecraft:yellow_stained_glass", 16777062);
                    pillar(e.position(), 40, 8, 0.6, "minecraft:white_concrete", 16777215);
                    burst(e.position().add(0, 1, 0), 0.9, 40, 0xFFFF66);
                    e.invulnerableTime = 0;
                    hit(e, p, 40f);
                    e.igniteForSeconds(6);
                    later(6, () -> { if (e.isAlive()) { Laser.bolt(level, e.position()); Holo.lightning(level, tag, e.position().add(-5, 34, 5), e.position().add(0, 1, 0), 0xFFFFAA, 1.0, 6); e.invulnerableTime = 0; hit(e, p, 20f); } });
                });
            }
        }

        // ── /tp 공중 낙하: 적을 10칸 띄웠다가 땅에 처박음
        void liftSlam(ServerPlayer p, Vec3 to) {
            say("/tp @e[type=!player,distance=..8] ~ ~10 ~", 0xFFAA55, pos.add(0, 3, 0), 1.0f);
            List<LivingEntity> foes = foesNear(to, 12);
            for (LivingEntity e : foes) {
                e.setDeltaMovement(0, 2.0, 0); e.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(), e.getZ(), 15, 0.5, 0.2, 0.5, 0.05);
                pillar(e.position(), 16, 18, 1.4, "minecraft:orange_stained_glass", 16755251);
                glyphs(e.position(), 2.2, 26, 0xFFAA33);
                for (int k = 0; k < 3; k++) Holo.ringT(level, tag, e.position().add(0, 1 + k * 3, 0), 0.4, 1.4 + e.getBbWidth(), 14, 0.15, 0.04, 0xFFAA33, 6 + k * 3, 4, 4, Holo.I(), 6, true);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 60, 0.5, 2, 0.5, 0.2);
            }
            level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2f, 0.6f);
            later(18, () -> {
                for (LivingEntity e : foes) {
                    if (!e.isAlive()) continue;
                    e.setDeltaMovement(0, -3.5, 0); e.hurtMarked = true;
                }
            });
            later(26, () -> {
                for (LivingEntity e : foes) {
                    if (!e.isAlive()) continue;
                    e.invulnerableTime = 0;
                    hit(e, p, 50f);
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY(), e.getZ(), 1, 0, 0, 0, 0);
                    Laser.ring(level, e.position().add(0, 0.3, 0), 3);
                    wave(e.position().add(0, 0.2, 0), 8, 10, 0xFFAA33);
                    Holo.beamLayer(level, tag, e.position().add(0, 14, 0), e.position(), 0.7, "minecraft:white_concrete", 0xFFAA33, 1, 1, 4, 0);
                    Holo.explosion(level, tag, e.position(), 1.8, 0xFFAA33);
                    burst(e.position().add(0, 0.5, 0), 0.8, 40, 0xFFAA33);
                    Slam.debris(level, e.position(), Slam.ground(level, e.position()), 16);
                    for (LivingEntity o : foesNear(e.position(), 5)) if (o != e) { o.invulnerableTime = 0; hit(o, p, 20f); }
                }
                level.playSound(null, to.x, to.y, to.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2f, 0.5f);
                level.playSound(null, to.x, to.y, to.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3f, 0.6f);
                Titan.shake(level, to, 40, true);
            });
        }

        // ── /effect 시간 감속: 주변 16칸 적 8초간 느려지고 약해짐
        void slowField(ServerPlayer p) {
            say("/effect give @e[type=!player,distance=..16] slowness 8 4", 0xAAAAFF, pos.add(0, 3, 0), 1.0f);
            for (LivingEntity e : foesNear(p.position(), 24)) {
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 5));
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 160, 1));
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 160, 0));
            }
            for (int r = 4; r <= 24; r += 4) Laser.ring(level, p.position().add(0, 0.3, 0), r);
            for (int r = 6; r <= 24; r += 6) glyphs(p.position(), r, 80, r % 12 == 0 ? 0x8888FF : 0xFFFFFF);
            wave(p.position().add(0, 0.2, 0), 24, 16, 0x8888FF);
            wave(p.position().add(0, 0.2, 0), 24, 30, 0xFFFFFF);
            fx.add(new Clock(this, p.position()));
            for (int k = 0; k < 4; k++) Holo.ringT(level, tag, p.position().add(0, 0.3 + k * 0.05, 0), 1, 24, 48, 0.5, 0.04, k % 2 == 0 ? 0x8888FF : 0xFFFFFF, 30 + k * 10, 0, 10, Holo.I(), 0, true);
            for (LivingEntity e : foesNear(p.position(), 24))
                Holo.banner(level, tag, e.position().add(0, e.getBbHeight() + 0.9, 0), "{text:\"⏳\",color:\"#AAAAFF\"}", 1.6, 60);
            for (LivingEntity e : foesNear(p.position(), 24)) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
                pillar(e.position(), 8, 20, 0.8, "minecraft:blue_stained_glass", 8947967);
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 0.6f);
        }

        // ── /gamemode creative (15초 비행 + 저항) — 웅크리고 아래를 보며 우클릭
        void creative() {
            ServerPlayer p = p();
            if (p == null) return;
            if (flyCd > 0) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7「/gamemode」 재사용 %.1f초", flyCd / 20f)), true); return; }
            flyCd = 1080; flyT = 300;
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 300, 1, false, false, true));
            say("/gamemode creative @s (15초)", 0x55FFFF, p.position().add(0, 3, 0), 1.4f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.5f, 1.2f);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1, 0.6, 0.15);
            pillar(p.position(), 60, 20, 2.5, "minecraft:light_blue_stained_glass", 5636095);
            pillar(p.position(), 60, 20, 0.8, "minecraft:white_concrete", 16777215);
            wave(p.position().add(0, 0.2, 0), 12, 14, 0x55FFFF);
            glyphs(p.position(), 4, 40, 0x55FFFF);
            burst(p.position().add(0, 1, 0), 0.9, 80, 0x55FFFF);
            p.displayClientMessage(Component.literal("§b§l비행 가능 §7— 점프 두 번으로 날기 (15초)"), true);
        }

        void endCreative(ServerPlayer p) {
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false, true));
            }
            say("/gamemode survival @s", 0xAAAAAA, p.position().add(0, 3, 0), 1.0f);
        }

        // ── 주인이 때리면 수호가 따라 쏨
        void followUp(LivingEntity target) {
            if (followCd > 0) return;
            followCd = 12;
            ServerPlayer p = p();
            if (p == null) return;
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
            Vec3 d = to.subtract(pos);
            Holo.beamLayer(level, tag, pos, to, 0.35, "minecraft:purple_stained_glass", 0xB45CFF, 1, 1, 3, age);
            double ra = level.random.nextDouble() * Math.PI;
            Holo.slash(level, tag, to, 1.4 + target.getBbWidth(), Holo.facing(new Vec3(Math.cos(ra), (level.random.nextDouble() - 0.5) * 0.8, Math.sin(ra))), 0xB45CFF, 5);
            Holo.sfx(level, to, "slash", 0.8f, 1.3f);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, to.x, to.y, to.z, 12, 0.3, 0.3, 0.3, 0.2);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.6f);
            later(1, () -> { if (target.isAlive()) { target.invulnerableTime = 0; hit(target, p, 12f); } });
            if (level.random.nextInt(4) == 0) say("/damage @e[limit=1,sort=nearest] 5", 0xDD99FF, pos.add(0, 3, 0), 0.7f);
        }

        // ── 궁극기 「/kill」
        void ultimate() {
            ServerPlayer p = p();
            if (p == null) return;
            if (ultCd > 0) { p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "§7「/kill」 재사용 %.1f초", ultCd / 20f)), true); return; }
            ultCd = 720; ultT = 30;
            say("/kill @e[type=!player,distance=..40]", 0xFF3355, p.position().add(0, 6, 0), 3.0f);
            glyphs(p.position(), 12, 40, 0xFF3355);
            glyphs(p.position(), 20, 40, 0xFF3355);
            glyphs(p.position(), 30, 40, 0xFFFFFF);
            glyphs(p.position(), 40, 40, 0xFF3355);
            for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4;
                pillar(p.position().add(Math.cos(a) * 20, 0, Math.sin(a) * 20), 80, 34, 2.2, "minecraft:red_stained_glass", 16724821); }
            pillar(p.position(), 120, 34, 5, "minecraft:red_stained_glass", 16724821);
            pillar(p.position(), 120, 34, 1.6, "minecraft:white_concrete", 16777215);
            cmd(String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] title {\"text\":\"/kill\",\"color\":\"#FF3355\",\"bold\":true}", p.getX(), p.getY(), p.getZ()));
            cmd(String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] subtitle {\"text\":\"@e[type=!player,distance=..40]\",\"color\":\"#FFAAAA\"}", p.getX(), p.getY(), p.getZ()));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.5f, 1.4f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
            cutIn(p, "/kill", "@e[type=!player, distance=..40]", 0xFF3355, 1);
            cam(p, p.position().add(0, 1, 0), 15, 8, 150, 1700);
        }

        // ═════ 절대 방어막: 큰 피해(최대 체력 25%↑) 또는 체력이 40% 아래로 떨어질 피해 → 8초간 모든 피해 무효 (45초)
        static final int ABS_TIME = 160, ABS_CD = 900, REVIVE_CD = 2400;
        boolean absolute(float amount) {
            ServerPlayer p = p();
            if (p == null) return false;
            if (absT > 0) { absHit(p); return true; }
            boolean big = amount >= p.getMaxHealth() * 0.25f || p.getHealth() - amount < p.getMaxHealth() * 0.4f;
            if (!big || absCd > 0) return false;
            absT = ABS_TIME; absCd = ABS_CD;
            final ServerPlayer sp = p;
            Holo.bubble(level, tag, () -> sp.position().add(0, 0.9, 0), 3.4, 0xFFD84A, ABS_TIME);
            Holo.bubble(level, tag, () -> sp.position().add(0, 0.9, 0), 3.0, 0xFFFFFF, ABS_TIME);
            Holo.ringT(level, tag, p.position().add(0, 0.1, 0), 0.5, 6, 30, 0.4, 0.05, 0xFFD84A, 4, ABS_TIME - 10, 6, Holo.I(), 3, true);
            Holo.shockTex(level, tag, p.position(), 12, 0xFFD84A, 8);
            Holo.flare(level, tag, p.position().add(0, 1, 0), 10, 0xFFFFFF, 10);
            Holo.banner(level, tag, p.position().add(0, 3.4, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⬢ ABSOLUTE BARRIER", 0xFFD84A, true) + "]}", 1.6, 34);
            say("/effect give @s resistance infinite 255 · /data merge @s {Invulnerable:1b}", 0xFFD84A, null, 0);
            cutIn(p, "절대 방어막", "ABSOLUTE BARRIER ─ 8초간 모든 피해 무효", 0xFFD84A, 0);
            Holo.sfx(level, p.position(), "choir", 1.6f, 1.3f); Holo.sfx(level, p.position(), "plate", 1.6f, 0.7f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 0.8f);
            return true;
        }
        void absHit(ServerPlayer p) {
            Holo.flare(level, tag, p.position().add(0, 1, 0), 3.5, 0xFFD84A, 4);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f);
            level.sendParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1, p.getZ(), 12, 1.6, 1.2, 1.6, 0.4);
        }
        void absoluteTick(ServerPlayer p) {
            absT--;
            p.clearFire();
            if (absT % 10 == 0) for (LivingEntity e : foesNear(p.position(), 4.5)) {      // 다가오는 적 밀어냄
                Vec3 d = e.position().subtract(p.position()).normalize();
                e.setDeltaMovement(d.scale(1.4).add(0, 0.4, 0)); e.hurtMarked = true;
            }
            if (absT % 4 == 0) {
                double a = absT * 0.3;
                level.sendParticles(dust(0xFFD84A, 1.4f), p.getX() + Math.cos(a) * 3.2, p.getY() + 0.3 + (absT % 20) * 0.15, p.getZ() + Math.sin(a) * 3.2, 1, 0, 0, 0, 0);
            }
            if (absT == 0) {
                Holo.shards(level, tag, p.position().add(0, 1, 0), "minecraft:yellow_stained_glass", 18, 3);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
            }
        }

        // ═════ 부활: 죽는 순간 「/execute revive」 — 체력 가득, 해로운 효과 제거, 4초 절대 방어 (120초)
        boolean revive(ServerPlayer p) {
            if (reviveCd > 0) return false;
            reviveCd = REVIVE_CD;
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.clearFire();
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 2, false, false, true));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 1200, 3, false, false, true));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 400, 0, false, false, true));
            if (p.getY() < level.getMinY() + 2) {          // 공허에 빠졌으면 지상으로
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.getX()), (int) Math.floor(p.getZ()));
                if (top <= level.getMinY()) { int t0 = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0); p.teleportTo(0.5, Math.max(t0, level.getSeaLevel()) + 1, 0.5); }
                else p.teleportTo(p.getX(), top + 1, p.getZ());
                p.fallDistance = 0;
            }
            absT = Math.max(absT, 80);
            final ServerPlayer sp2 = p;
            Holo.bubble(level, tag, () -> sp2.position().add(0, 0.9, 0), 3.2, 0x55FF88, 80);
            Vec3 c = p.position();
            Holo.pillar(level, tag, c, 60, 3, 0x55FF88, 2, 14, 10);
            Holo.pillar(level, tag, c, 60, 1, 0xFFFFFF, 2, 14, 8);
            Holo.circle(level, tag, c, 6, 0x55FF88, 40, 0);
            Holo.shockTex(level, tag, c, 18, 0x55FF88, 12);
            Holo.flare(level, tag, c.add(0, 1, 0), 18, 0xFFFFFF, 14);
            Holo.banner(level, tag, c.add(0, 3.6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("✚ REVIVED", 0x55FF88, true) + "]}", 2.2, 40);
            for (LivingEntity e : foesNear(c, 8)) { Vec3 d = e.position().subtract(c).normalize(); e.setDeltaMovement(d.scale(2).add(0, 0.8, 0)); e.hurtMarked = true; }
            burst(c.add(0, 1, 0), 1.2, 160, 0x55FF88);
            say("/execute as @s run revive --hp max", 0x55FF88, null, 0);
            cutIn(p, "부활", "/execute revive ─ 커맨드 수호가 죽음을 되돌렸다", 0x55FF88, 1);
            Holo.sfx(level, c, "choir", 2.4f, 1.0f); Holo.sfx(level, c, "riser", 2f, 1.4f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 2f, 1.0f);
            return true;
        }

        boolean shield(float amount) {
            if (shieldCd > 0 || amount < 4) return false;
            ServerPlayer p = p();
            if (p == null) return false;
            shieldCd = 240; shieldT = 30;
            final ServerPlayer sp = p;
            Holo.bubble(level, tag, sp::position, 1.9, 0x55FFFF, 34);
            Holo.shock(level, tag, p.position(), 5, 0x55FFFF, 6);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 100, 2, false, false, true));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 300, 2, false, false, true));
            say("/effect give @s resistance 5 2", 0x55FFFF, p.position().add(0, 3, 0), 1.0f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.2f, 1.6f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2f, 0.8f);
            return true;
        }

        void tick(ServerPlayer p) {
            age++;
            RandomSource rnd = level.random;
            if (atkCd > 0) atkCd--;
            if (ultCd > 0) ultCd--;
            if (golemCd > 0) golemCd--;
            if (shieldCd > 0) shieldCd--;
            if (absCd > 0) absCd--;
            if (reviveCd > 0) reviveCd--;
            if (absT > 0) absoluteTick(p);
            if (tpCd > 0) tpCd--;
            if (flyCd > 0) flyCd--;
            if (followCd > 0) followCd--;
            if (deflectMsg > 0) deflectMsg--;
            if (fireCd > 0) fireCd--;
            if (fallCd > 0) fallCd--;
            if (nightMsg > 0) nightMsg--;
            if (appleCd > 0) appleCd--;
            if (comboT > 0 && --comboT == 0) combo = 0;
            if (domainCd > 0) domainCd--;
            if (retaliateCd > 0) retaliateCd--;
            if (--autoCd <= 0) { autoCd = odT > 0 ? 20 : 40; if (motionT == 0) autoFire(p); }
            if (age % 10 == 0) scanBars(p);
            for (Iterator<Map.Entry<LivingEntity, Holo.Elem>> it = bars.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                LivingEntity le = en.getKey();
                if (!le.isAlive() || le.isRemoved()) { en.getValue().kill(); it.remove(); if (le.isDeadOrDying()) eliminated(p, le); }
                else if (le.distanceToSqr(p) > 34 * 34) { en.getValue().kill(); it.remove(); }
            }
            if (odT > 0) {           // 오버드라이브 오라
                odT--;
                for (int k = 0; k < 3; k++) {
                    double a = age * 0.3 + k * Math.PI * 2 / 3;
                    level.sendParticles(dust(0xFFD84A, 1.6f), p.getX() + Math.cos(a) * 1.2, p.getY() + 0.2 + (age % 20) * 0.1, p.getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
                }
                if (age % 2 == 0) level.sendParticles(ParticleTypes.WAX_OFF, pos.x, pos.y, pos.z, 4, 2, 2, 2, 0.2);
                if (age % 20 == 0) Holo.shock(level, tag, p.position(), 6, 0xFFD84A, 10);
                if (odT == 0) { say("/function guard:overdrive_end", 0x888888, null, 0); level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 1.4f); }
            }
            for (int i = 0; i < pending.size(); i++) {
                Pending pd = pending.get(i);
                if (--pd.t <= 0) { pending.remove(i--); try { pd.r.run(); } catch (Exception ignored) { } }
            }
            if (flyT > 0) {
                flyT--;
                if (p.getAbilities().flying && age % 2 == 0) level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
                if (flyT == 0) endCreative(p);
            }
            // 자동: 날아오는 투사체 삭제 「/kill @e[type=arrow]」
            for (var pr : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, p.getBoundingBox().inflate(5),
                    e -> e.getOwner() != p && e.getDeltaMovement().lengthSqr() > 0.04)) {
                level.sendParticles(ParticleTypes.ENCHANT, pr.getX(), pr.getY(), pr.getZ(), 20, 0.2, 0.2, 0.2, 0.6);
                level.sendParticles(PURPLE, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0);
                pr.discard();
                if (deflectMsg == 0) { deflectMsg = 60; say("/kill @e[type=arrow,distance=..5]", 0xFF7777, p.position().add(0, 2.6, 0), 0.8f);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f); }
            }
            // 자동: 불이 붙으면 「/effect fire_resistance」
            if (fireCd == 0 && p.isOnFire()) {
                fireCd = 600; p.clearFire();
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 200, 0, false, false, true));
                say("/effect give @s fire_resistance 10", 0xFF9955, p.position().add(0, 2.6, 0), 0.9f);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1f, 1f);
            }
            // 자동: 높은 곳에서 떨어지면 「/effect slow_falling」
            if (fallCd == 0 && !p.onGround() && p.fallDistance > 7 && !p.getAbilities().flying) {
                fallCd = 200;
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 80, 0, false, false, true));
                say("/effect give @s slow_falling 4", 0xDDDDFF, p.position().add(0, 2.6, 0), 0.9f);
            }
            // 자동: 어두우면 「/effect night_vision」
            if (age % 100 == 0 && level.getMaxLocalRawBrightness(p.blockPosition()) < 5) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 400, 0, false, false, true));
                if (nightMsg == 0) { nightMsg = 1200; say("/effect give @s night_vision", 0xAAFFAA, p.position().add(0, 2.6, 0), 0.9f); }
            }
            // 자동: 배고프면 「/effect give @s saturation」 → 허기·포화 가득
            if (age % 20 == 0 && p.getFoodData().getFoodLevel() < 18) {
                p.getFoodData().setFoodLevel(20);
                p.getFoodData().setSaturation(20f);
                say("/effect give @s saturation 1 255", 0xFFB060, p.position().add(0, 2.6, 0), 0.9f);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6f, 1.4f);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.4, 0.5, 0.4, 0);
            }
            // 자동: 체력 절반 이하일 때 가끔 「/give @s golden_apple」
            if (appleCd == 0 && p.getHealth() < p.getMaxHealth() * 0.5f) {
                appleCd = 1200;
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 120, 1, false, false, true));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 2400, 0, false, false, true));
                say("/give @s golden_apple", 0xFFD84A, p.position().add(0, 2.6, 0), 1.0f);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1f, 1.2f);
            }

            motionTick(p);
            tpGroup("aura", pos);
            tpGroup("circ", p.position().add(0, 0.05, 0));
            double boostK = Math.min(1.6, 1 + (spinSpeed - 1) * 0.05);
            orbA += 0.03 * boostK; selfA += 0.05 * boostK;
            if (age % 2 == 0) {
                for (int i = 0; i < 12; i++) {       // 3겹 궤도 (서로 다른 기울기·속도)
                    int ring = i / 4;
                    double a = orbA * (1 + ring * 0.4) * (ring == 1 ? -1 : 1) + (i % 4) * Math.PI / 2;
                    double tilt = ring == 0 ? 0.35 : ring == 1 ? -0.9 : 1.25, rad = (4.6 + ring * 0.9) * (0.8 + 0.2 * Math.min(2, orbitK));
                    Vec3 c = new Vec3(Math.cos(a) * rad, Math.sin(a) * rad * Math.sin(tilt), Math.sin(a) * rad * Math.cos(tilt));
                    double os = 0.85;
                    shape("o" + i, mat(selfA + i, selfA * 0.6 + i, c, os, os, os));
                }
                for (int i = 0; i < 16; i++) {       // 발밑 마법진
                    double a = age * 0.04 + i * Math.PI / 8;
                    Vec3 c = new Vec3(Math.sin(a) * 2.4, 0, Math.cos(a) * 2.4);
                    shape("c" + i, mat(a + Math.PI, -Math.PI / 2, c, 1.6, 1.6, 1.6));
                }
                shape("ring", mat(-age * 0.03, -Math.PI / 2, Vec3.ZERO, 0.9, 0.9, 0.9));
            }
            for (int i = 0; i < fx.size(); i++) {
                Fx f = fx.get(i);
                boolean done;
                try { done = f.tick(this, p); } catch (Exception ex) { done = true; }
                if (done) fx.remove(i--);
            }
            if (age % 2 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 6, 2.6, 2.6, 2.6, 0.6);
                level.sendParticles(PURPLE, pos.x + Math.cos(age * 0.1) * 3.4, pos.y + Math.sin(age * 0.07), pos.z + Math.sin(age * 0.1) * 3.4, 1, 0, 0, 0, 0);
            }
            if (age % 100 == 50) say(FLAVOR[rnd.nextInt(FLAVOR.length)], 0x55FF55, pos.add(0, 3.4, 0), 0.8f);

            // 떠오르는 글자
            for (Iterator<Floater> it = texts.iterator(); it.hasNext(); ) {
                Floater f = it.next();
                f.at = f.at.add(0, 0.05, 0);
                var fe = sub(f.id);
                if (fe != null) snap(fe, f.at);
                else cmd(String.format(Locale.ROOT, "tp @e[tag=%s.%s,limit=1] %.3f %.3f %.3f", tag, f.id, f.at.x, f.at.y, f.at.z));
                if (--f.life <= 0) { kill(f.id); it.remove(); }
            }

            // 레이저
            if (laserT > 0) {
                laserT--;
                if (laserT >= 40) {     // 충전: 사방에서 수호로 빨려드는 빛줄기
                    for (int k = 0; k < 2; k++) {
                        double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * 2 - 1;
                        Vec3 o = pos.add(Math.cos(a) * 7, b * 4, Math.sin(a) * 7);
                        Holo.beamLayer(level, tag, o, pos, 0.18, "minecraft:magenta_stained_glass", 0xFF55FF, 2, 1, 3, k);
                    }
                    if (laserT % 3 == 0) level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 0.8f + (50 - laserT) * 0.1f);
                    if (laserT == 40) {
                        Vec3 dd = laserTo.subtract(pos).normalize();
                        Holo.ringT(level, tag, pos.add(dd.scale(2)), 0.5, 5, 24, 0.4, 0.06, 0xFF55FF, 4, 0, 4, Holo.facing(dd), 0, true);
                        Holo.ringT(level, tag, pos.add(dd.scale(4)), 0.5, 3.5, 20, 0.3, 0.06, 0xFFFFFF, 5, 0, 4, Holo.facing(dd), 0, true);
                        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 1.2f);
                        Holo.slowmo(level, 12, 3);
                        Holo.sfx(level, pos, "laser", 2f, 1f);
                        Holo.punch(level, pos, 4, 0xFF55FF);
                        Holo.flare(level, tag, pos, 7, 0xFF55FF, 10);
                    }
                } else {
                Vec3 d = laserTo.subtract(pos);
                double len = d.length();
                Vec3 dir = d.normalize();
                double w = 1.1 + Math.sin(laserT * 0.9) * 0.25;
                LaserFx.set(level, p, "cgl0", pos, LaserFx.beam(dir, len, w, laserT * 0.3));
                LaserFx.set(level, p, "cgl1", pos, LaserFx.beam(dir, len, w * 2.4, -laserT * 0.2));
                LaserFx.set(level, p, "cgl2", pos, LaserFx.beam(dir, len, w * 4.0, laserT * 0.12));
                LaserFx.set(level, p, "cgl3", pos, LaserFx.beam(dir, len, w * 5.6 * (1 + Math.sin(laserT * 0.5) * 0.1), -laserT * 0.08));
                LaserFx.set(level, p, "cgl4", laserTo, LaserFx.cube(4 + Math.sin(laserT * 0.7) * 1.2, laserT * 0.3, laserT * 0.2));
                LaserFx.set(level, p, "cgl5", pos, LaserFx.cube(2.2 + Math.sin(laserT * 0.9) * 0.4, -laserT * 0.4, laserT * 0.25));
                if (laserT % 6 == 0) { wave(laserTo, 6, 8, 0xFF55FF); level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x + dir.x * len * 0.5, pos.y + dir.y * len * 0.5, pos.z + dir.z * len * 0.5, 2, 1, 1, 1, 0); }
                if (laserT % 4 == 0) level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2f, 1.8f);
                if (laserT % 3 == 0) {
                    double fr = ((40 - laserT) % 12) / 12.0;
                    Holo.ringT(level, tag, pos.add(dir.scale(len * fr)), 0.6, 3.6, 18, 0.28, 0.05, laserT % 2 == 0 ? 0xFF55FF : 0xFFFFFF, 6, 0, 3, Holo.facing(dir), 0, true);
                }
                if (laserT % 2 == 0) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, laserTo).inflate(2),
                            e -> e.isAlive() && !(e instanceof Player) && !(e instanceof IronGolem))) {
                        if (e.getBoundingBox().inflate(2).clip(pos, laserTo).isEmpty()) continue;
                        e.invulnerableTime = 0;
                        hit(e, p, 30f);
                        e.igniteForSeconds(4);
                    }
                    level.sendParticles(PURPLE, laserTo.x, laserTo.y, laserTo.z, 10, 0.5, 0.5, 0.5, 0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, laserTo.x, laserTo.y, laserTo.z, 8, 0.4, 0.4, 0.4, 0.3);
                    if (laserT % 8 == 0) { boolean gr = MechManager.griefOn(level.getServer());
                        level.explode(p, laserTo.x, laserTo.y, laserTo.z, 3f, false, gr ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE); }
                    for (double k = 1; k < len; k += 2.5) { double an = k * 0.4 + laserT * 0.6; Vec3 q = pos.add(dir.scale(k));
                        level.sendParticles(PURPLE, q.x + Math.cos(an) * 1.6, q.y + Math.sin(an) * 1.6, q.z, 1, 0, 0, 0, 0); }
                }
                if (laserT == 0) { for (int k = 0; k < 6; k++) LaserFx.kill(level, p, "cgl" + k);
                    wave(laserTo, 12, 12, 0xB45CFF); burst(laserTo, 1.0, 80, 0xFF55FF); flash(laserTo, 40, "p");
                    Holo.explosion(level, tag, laserTo, 3.5, 0xFF55FF);
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, laserTo.x, laserTo.y, laserTo.z, 3, 2, 1, 2, 0);
                    level.playSound(null, laserTo.x, laserTo.y, laserTo.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4f, 0.6f); }
            }}

            // 폭탄
            for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) { Bolt b = it.next(); if (b.tick(this, p)) it.remove(); }

            // 궁극기 진행: 1.5초 뒤 보라 충격파
            if (ultT > 0 && --ultT == 0) {
                Vec3 c = p.position();
                for (int r = 4; r <= 40; r += 4) Laser.ring(level, c.add(0, 0.5, 0), r);
                level.sendParticles(PURPLE, c.x, c.y + 1, c.z, 400, 20, 3, 20, 0);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; Vec3 q = c.add(Math.cos(a) * 20, 0, Math.sin(a) * 20);
                    Laser.bolt(level, q); Holo.lightning(level, tag, q.add(0, 50, 0), q, 0xFF3040, 2.0, 10); }
                Holo.ringT(level, tag, c.add(0, 0.3, 0), 1, 40, 64, 1.8, 0.06, 0xFF3040, 14, 4, 8, Holo.I(), 0, true);
                Holo.shockTex(level, tag, c, 44, 0xFF3040, 14);
                Holo.sfx(level, c, "boom", 4f, 0.7f); Holo.sfx(level, c, "thunder", 4f, 0.8f);
                Holo.punch(level, c, 9, 0xFF3040);
                Holo.screen(level, c, 60, 5, 0xB01020, 2500, 0.8f);
                Holo.ringT(level, tag, c.add(0, 12, 0), 1, 30, 48, 0.8, 0.06, 0xFFFFFF, 10, 4, 8, Holo.rotX(0.25), 6, true);
                flash(c, 60, "r");
                wave(c.add(0, 0.2, 0), 40, 14, 0xFF3355);
                wave(c.add(0, 0.2, 0), 40, 24, 0xFFFFFF);
                burst(c.add(0, 2, 0), 2.0, 300, 0xFF3355);
                Titan.shake(level, c, 60, true);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 0.6f);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2f, 1.2f);
                int n = 0;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40), e -> e instanceof Enemy && e.isAlive())) {
                    level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                    pillar(e.position(), 30, 10, 1.4, "minecraft:red_stained_glass", 16724821);
                    Holo.lightning(level, tag, e.position().add(0, 40, 0), e.position().add(0, 0.5, 0), 0xFF3040, 1.2, 8);
                    Holo.explosion(level, tag, e.position(), 1.6, 0xFF3040);
                    Holo.banner(level, tag, e.position().add(0, e.getBbHeight() + 1.2, 0), "{text:\"☠\",color:\"#FF3040\"}", 2.6, 26);
                    level.sendParticles(dust(0xFF3355, 2.5f), e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.8, 0.4, 0);
                    e.invulnerableTime = 0;
                    e.hurtServer(level, level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                    n++;
                }
                p.displayClientMessage(Component.literal("§c§l「/kill」 §7— " + n + "개의 엔티티를 제거했습니다"), true);
            }

            // 방어막 연출
            if (shieldT > 0) {
                shieldT--;
                for (int i = 0; i < 16; i++) {
                    double a = i / 16.0 * Math.PI * 2 + age * 0.2;
                    level.sendParticles(GREEN, p.getX() + Math.cos(a) * 1.4, p.getY() + 1 + Math.sin(age * 0.3 + i) * 0.8, p.getZ() + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
                }
            }

            // 긴급 회피 「/tp」
            if (tpCd == 0 && p.getHealth() < p.getMaxHealth() * 0.3f) {
                tpCd = 540;
                Vec3 from = p.position();
                double r = Math.toRadians(p.getYRot());
                for (int k = 0; k < 8; k++) if (p.randomTeleport(from.x + Math.sin(r) * 8, from.y + 1, from.z - Math.cos(r) * 8, true)) break;
                p.heal(10f);
                say("/tp @s ~ ~ ~-8", 0xFFAA00, p.position().add(0, 3, 0), 1.0f);
                Holo.circle(level, tag, from, 2.6, 0xFFAA00, 22, 0);
                Holo.circle(level, tag, p.position(), 2.6, 0xFFFFFF, 22, 0);
                Holo.beam(level, tag, from.add(0, 1, 0), p.position().add(0, 1, 0), 0.9, 0xFFAA00, 1, 2, 5);
                Holo.ghost(level, tag, from.add(0, 1, 0), "minecraft:orange_stained_glass", 0xFFAA00, 1.6, Holo.I(), 10);
                level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 80, 0.5, 1, 0.5, 0.5);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 1f);
            }

            // 적 접근 → 커맨드 골렘
            if (!golemAllowed(p) && !golems.isEmpty()) { for (Golem gl : golems) gl.discard(this); golems.clear(); }
            if (golemCd == 0 && golems.size() < 3 && golemAllowed(p)) {
                List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(14), e -> e instanceof Enemy && e.isAlive());
                if (!foes.isEmpty()) {
                    LivingEntity foe = foes.get(0);
                    Vec3 spot = p.position().lerp(foe.position(), 0.5);
                    golems.add(new Golem(this, spot));
                    golemCd = 144;
                    say("/summon iron_golem ~ ~ ~ {PlayerCreated:1b}", 0xAAAAAA, pos.add(0, 3, 0), 1.0f);
                }
            }
            for (Iterator<Golem> it = golems.iterator(); it.hasNext(); ) { Golem g = it.next(); if (g.tick(this)) it.remove(); }
        }
    }

    static final String[] GLYPHS = {"/", "@", "~", "^", "{", "}", "[", "]", "#", ";", "$", "Σ", "Ω", "Δ", "λ", "∞", "⌘", "§"};

    interface Fx { boolean tick(Guard g, ServerPlayer p); }

    /** 회전하는 글자 원 (조준진·마법진). */
    static final class GlyphCircle implements Fx {
        final String grp; final Vec3 at; final double r; int life, t = 0; final int n;
        GlyphCircle(String grp, Vec3 at, double r, int life, int n) { this.grp = grp; this.at = at; this.r = r; this.life = life; this.n = n; }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t % 2 == 1) {
                double grow = Math.min(1, t / 6.0), fade = life - t < 6 ? (life - t) / 6.0 : 1;
                for (int i = 0; i < n; i++) {
                    double a = t * 0.12 + i * Math.PI * 2 / n;
                    Vec3 c = new Vec3(Math.sin(a) * r * grow, 0, Math.cos(a) * r * grow);
                    g.shape(grp + "x" + i, mat(a + Math.PI, -Math.PI / 2, c, 2.4 * fade, 2.4 * fade, 2.4 * fade));
                }
            }
            if (t % 3 == 0) g.level.sendParticles(PURPLE, at.x, at.y + 0.2, at.z, 6, r * 0.6, 0.05, r * 0.6, 0);
            if (t >= life) { g.killGroup(grp); return true; }
            return false;
        }
    }

    /** /summon 커맨드 메테오: 하늘에서 거대한 커맨드 블록이 회전하며 떨어짐. */
    static final class Meteor implements Fx {
        final String id = "mt" + (Guard.uid++); final Vec3 to; Vec3 at; int t = 0, delay; final double size; final boolean main; boolean spawned;
        Meteor(Guard g, Vec3 to, double size, int delay, boolean main) {
            this.to = to; this.at = to.add(18, 60, 10); this.size = size; this.delay = delay; this.main = main;
            if (main) {
                g.say("/summon command_block_meteor ~ ~60 ~", 0xFF5555, g.pos.add(0, 3, 0), 1.6f);
                g.glyphs(to, 7, 34, 0xFF4444);
                g.glyphs(to, 14, 34, 0xFFAA33);
                g.glyphs(to, 21, 34, 0xFF4444);
                g.level.playSound(null, to.x, to.y, to.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 4f, 0.3f);
                g.level.playSound(null, to.x, to.y, to.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3f, 1.4f);
                Vec3 sk = to.add(18, 60, 10);
                Holo.ringT(g.level, g.tag, sk, 1, 14, 32, 1.2, 0.2, 0xFF4433, 8, 20, 6, Holo.facing(to.subtract(sk)), 4, true);
                Holo.ringT(g.level, g.tag, sk, 1, 9, 24, 0.6, 0.2, 0xFFFFFF, 6, 22, 6, Holo.facing(to.subtract(sk)), -6, false);
                Holo.lightning(g.level, g.tag, sk.add(-12, 4, 6), sk.add(10, -2, -8), 0xFF7020, 0.8, 10);
                Holo.screen(g.level, to, 90, 5, 0xFF6020, 2600, 0.75f);
                Holo.lightning(g.level, g.tag, sk.add(6, 8, -10), sk.add(-8, -4, 12), 0xFF7020, 0.8, 10);
            }
        }
        public boolean tick(Guard g, ServerPlayer p) {
            if (delay > 0) { delay--; return false; }
            if (!spawned) { spawned = true; g.displayG(id, id, at, main ? "minecraft:command_block" : "minecraft:chain_command_block", true, 10f);
                if (main) g.beam(to.add(18, 60, 10), to, 30, 1.2, "minecraft:red_stained_glass", 16724787); }
            t++;
            if (main && t % 3 == 0) {   // 불꼬리 + 대기 진동
                for (int k = 0; k < 6; k++) { Vec3 q = at.add((g.level.random.nextDouble() - 0.5) * 6, 3 + g.level.random.nextDouble() * 8, (g.level.random.nextDouble() - 0.5) * 6);
                    g.level.sendParticles(ParticleTypes.LAVA, q.x, q.y, q.z, 2, 0.5, 0.5, 0.5, 0); }
                g.level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 6, at.z, 6, 2, 3, 2, 0.02);
            }
            double u = Math.min(1, t / 30.0);
            Vec3 start = to.add(18, 60, 10);
            at = start.lerp(to, u * u);
            g.tpGroup(id, at);
            g.shape(id, mat(t * 0.35, t * 0.22, Vec3.ZERO, size, size, size));
            if (t % 2 == 0) Holo.ghost(g.level, g.tag, at, main ? "minecraft:magma_block" : "minecraft:orange_stained_glass", main ? 0xFF7020 : -1,
                    size * 0.75, Holo.mul(Holo.rotX(t * 0.4), Holo.rotY(t * 0.3)), main ? 10 : 6);
            g.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, main ? 40 : 10, size * 0.3, size * 0.3, size * 0.3, 0.05);
            g.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, main ? 15 : 4, size * 0.3, size * 0.3, size * 0.3, 0.05);
            g.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 2, 2, 2, 0.05);
            g.level.sendParticles(PURPLE, at.x, at.y, at.z, 10, 2.5, 2.5, 2.5, 0);
            g.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 3, at.z, 8, 1.5, 1.5, 1.5, 0.02);
            if (t % 5 == 0) g.level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 4f, 0.5f);
            if (u < 1) return false;
            g.killGroup(id);
            boolean grief = MechManager.griefOn(g.level.getServer());
            if (!main) {
                g.level.explode(p, to.x, to.y, to.z, 4f, false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                g.wave(to.add(0, 0.2, 0), 6, 8, 0xFF8833); g.burst(to.add(0, 1, 0), 0.6, 30, 0xFF5533);
                Holo.explosion(g.level, g.tag, to, 2, 0xFF8833);
                for (LivingEntity e : g.foesNear(to, 5)) { e.invulnerableTime = 0; g.hit(e, p, 30f); e.igniteForSeconds(5); }
                return true;
            }
            g.wave(to.add(0, 0.2, 0), 26, 14, 0xFF4433);
            Holo.slowmo(g.level, 9, 10);
            Holo.explosion(g.level, g.tag, to, 8, 0xFF5020);
            Holo.embers(g.level, g.tag, to, 30, 8, 0xFF7020, 80);
            Holo.smoke(g.level, g.tag, to.add(0, 2, 0), 24, 9, -1, 26, 60);
            for (int k = 0; k < 6; k++) {          // 버섯구름: 기둥 → 갓
                final int kk = k;
                g.later(k * 2, () -> Holo.orb(g.level, g.tag, to.add(0, 3 + kk * 4.5, 0), 5 + kk * 0.8, kk < 3 ? 0xFF7020 : 0x5A5A5A, 4, 18, 10));
            }
            g.later(12, () -> { Holo.orb(g.level, g.tag, to.add(0, 30, 0), 22, 0x5A5A5A, 8, 14, 12); Holo.ringT(g.level, g.tag, to.add(0, 26, 0), 2, 26, 40, 3, 0.6, 0xFF7020, 10, 10, 10, Holo.I(), 2, true); });
            g.wave(to.add(0, 0.2, 0), 26, 24, 0xFFAA33);
            g.burst(to.add(0, 2, 0), 1.8, 250, 0xFF4433);
            g.pillar(to, 70, 20, 8, "minecraft:orange_stained_glass", 16755251);
            g.pillar(to, 70, 20, 3, "minecraft:white_concrete", 16777215);
            g.flash(to, 80, "o");
            Slam.debris(g.level, to, Slam.ground(g.level, to), 60);
            for (int i = 0; i < 40; i++) { double an = i / 40.0 * Math.PI * 2;
                g.level.sendParticles(ParticleTypes.EXPLOSION, to.x + Math.cos(an) * 8, to.y + 18, to.z + Math.sin(an) * 8, 2, 2, 2, 2, 0);
                g.level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, to.x + Math.cos(an) * 6, to.y + 14, to.z + Math.sin(an) * 6, 2, 1.5, 2, 1.5, 0.02); }
            for (int i = 0; i < 12; i++) g.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y + i * 1.5, to.z, 1, 1, 0.5, 1, 0);
            g.level.explode(p, to.x, to.y, to.z, 10f, false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            g.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y, to.z, 4, 3, 1, 3, 0);
            g.level.sendParticles(ParticleTypes.ENCHANT, to.x, to.y + 1, to.z, 400, 6, 3, 6, 2);
            for (int r = 3; r <= 15; r += 3) Laser.ring(g.level, to.add(0, 0.5, 0), r);
            for (int i = 0; i < 3; i++) Laser.bolt(g.level, to.add((g.level.random.nextDouble() - 0.5) * 12, 0, (g.level.random.nextDouble() - 0.5) * 12));
            for (LivingEntity e : g.foesNear(to, 12)) { e.invulnerableTime = 0; g.hit(e, p, 90f); e.igniteForSeconds(8); }
            g.level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4f, 0.5f);
            Titan.shake(g.level, to, 40, true);
            return true;
        }
    }

    /** /execute 특이점: 블랙홀이 적을 빨아들였다가 붕괴. */
    static final class Singularity implements Fx {
        final String grp = "bh" + (Guard.uid++); final Vec3 at; int t = 0;
        Singularity(Guard g, Vec3 to) {
            this.at = to.add(0, 2.5, 0);
            g.say("/execute positioned ^ ^ ^ run singularity", 0xB45CFF, g.pos.add(0, 3, 0), 1.2f);
            g.blockG(grp + "core", grp, at, "minecraft:black_concrete", 11820287);
            g.blockG(grp + "r1", grp, at, "minecraft:purple_stained_glass", -1);
            g.blockG(grp + "r2", grp, at, "minecraft:magenta_stained_glass", 16733695);
            g.blockG(grp + "r3", grp, at, "minecraft:black_stained_glass", -1);
            g.blockG(grp + "r4", grp, at, "minecraft:white_stained_glass", 16777215);
            g.blockG(grp + "r5", grp, at, "minecraft:purple_stained_glass", 11820287);
            g.blockG(grp + "halo", grp, at, "minecraft:purple_stained_glass", -1);
            g.pillar(to, 60, 90, 0.6, "minecraft:black_concrete", 11820287);
            Holo.ringT(g.level, g.tag, at, 0.5, 10, 44, 1.6, 0.04, 0xB45CFF, 14, 68, 8, Holo.rotX(0.38), 7, true);
            Holo.ringT(g.level, g.tag, at, 0.5, 6.5, 32, 0.9, 0.04, 0xFFFFFF, 12, 70, 8, Holo.mul(Holo.rotX(-0.5), Holo.rotZ(0.3)), -11, false);
            Holo.ringT(g.level, g.tag, at, 0.5, 14, 52, 0.4, 0.04, 0xFF55FF, 16, 66, 8, Holo.rotZ(1.1), 4, true);
            g.glyphs(to, 8, 90, 0xB45CFF);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 3f, 0.6f);
            Holo.screen(g.level, at, 50, 5, 0x6020A0, 4800, 0.7f);
            Holo.sfx(g.level, at, "deep", 3f, 1.4f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            double grow = Math.min(1, t / 12.0), pulse = 1 + Math.sin(t * 0.5) * 0.1;
            if (t % 2 == 0) {
                g.shape(grp + "core", LaserFx.cube(2.2 * grow * pulse, t * 0.3, t * 0.2));
                g.shape(grp + "r1", ringMat(9 * grow, t * 0.12, 0.5));
                g.shape(grp + "r2", ringMat(7 * grow, -t * 0.18, -0.9));
                g.shape(grp + "r3", ringMat(11 * grow / pulse, t * 0.08, 1.3));
                g.shape(grp + "r4", ringMat(15 * grow, -t * 0.05, 0.2 + Math.sin(t * 0.05) * 0.3));
                g.shape(grp + "r5", ringMat(5 * grow * pulse, t * 0.3, -0.4));
                g.shape(grp + "halo", LaserFx.cube(5 * grow / pulse, -t * 0.15, t * 0.1));
            }
            for (int i = 0; i < 16; i++) {          // 나선으로 빨려드는 입자
                double a = g.level.random.nextDouble() * Math.PI * 2, r = 6 + g.level.random.nextDouble() * 8;
                Vec3 o = at.add(Math.cos(a) * r, (g.level.random.nextDouble() - 0.5) * 6, Math.sin(a) * r);
                Vec3 v = at.subtract(o).scale(0.1).add(new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.4));
                g.level.sendParticles(i % 2 == 0 ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
            }
            for (LivingEntity e : g.foesNear(at, 14)) {   // 끌어당김
                Vec3 d = at.subtract(e.position()).normalize().scale(0.35);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(d));
                e.hurtMarked = true;
                if (t % 10 == 0) { e.invulnerableTime = 0; g.hit(e, p, 8f); }
            }
            if (t % 8 == 0) g.level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 3f, 0.4f);
            if (t == 78) {        // 사건의 지평선: 고리들이 안으로 빨려듦
                for (int k = 0; k < 4; k++)
                    Holo.ringT(g.level, g.tag, at, 20 - k * 3, 0.4, 36, 0.6, 0.06, k % 2 == 0 ? 0xB45CFF : 0xFFFFFF, 12, 0, 2, Holo.mul(Holo.rotX(k * 0.8), Holo.rotZ(k * 0.5)), 8, true);
                g.level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.5f);
                Holo.screen(g.level, at, 50, 7, 0, 600, 0.18f);
                Holo.screen(g.level, at, 50, 6, 0xB45CFF, 600, 1);
            }
            if (t < 90) return false;
            Holo.slowmo(g.level, 8, 10);
            Holo.explosion(g.level, g.tag, at.add(0, -2.5, 0), 6, 0xB45CFF);
            Holo.flare(g.level, g.tag, at, 24, 0xFFFFFF, 14);
            Holo.sfx(g.level, at, "shatter", 3f, 0.6f);
            g.killGroup(grp);                         // 붕괴
            g.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 3, 1, 1, 1, 0);
            g.level.sendParticles(PURPLE, at.x, at.y, at.z, 300, 5, 5, 5, 0);
            g.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 120, 0.2, 0.2, 0.2, 0.8);
            g.burst(at, 2.2, 300, 0xB45CFF);
            g.wave(at.add(0, -2.3, 0), 22, 14, 0xB45CFF);
            g.wave(at.add(0, -2.3, 0), 22, 24, 0x000000);
            g.pillar(at.add(0, -3, 0), 80, 16, 6, "minecraft:purple_stained_glass", 11820287);
            g.pillar(at.add(0, -3, 0), 80, 16, 2, "minecraft:white_concrete", 16777215);
            g.flash(at, 60, "p");
            g.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 6, 4, 4, 4, 0);
            for (LivingEntity e : g.foesNear(at, 10)) {
                e.invulnerableTime = 0;
                g.hit(e, p, 70f);
                e.setDeltaMovement(e.position().subtract(at).normalize().scale(2).add(0, 0.8, 0)); e.hurtMarked = true;
            }
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 0.5f);
            Titan.shake(g.level, at, 40, true);
            return true;
        }
    }

    /** 납작한 고리 판 (정사각 판을 기울여 회전). */
    static float[] ringMat(double size, double spin, double tilt) {
        double ca = Math.cos(spin), sa = Math.sin(spin), cb = Math.cos(tilt), sb = Math.sin(tilt);
        double[][] r = {{ca, sa * sb, sa * cb}, {0, cb, -sb}, {-sa, ca * sb, ca * cb}};
        double[] sc = {size, 0.15, size};
        float[] m = new float[16];
        for (int i = 0; i < 3; i++) {
            double t = 0;
            for (int j = 0; j < 3; j++) { m[i * 4 + j] = (float) (r[i][j] * sc[j]); t -= r[i][j] * sc[j] * 0.5; }
            m[i * 4 + 3] = (float) t;
        }
        m[15] = 1;
        return m;
    }

    /** /worldborder 결계: 거대한 빛나는 유리 큐브 안에 적을 가두고 지속 피해. */
    static final class Barrier implements Fx {
        final String grp = "wb" + (Guard.uid++); final Vec3 at; int t = 0;
        static final double R = 9;
        Barrier(Guard g, Vec3 to) {
            this.at = to;
            g.say("/worldborder center ~ ~ & set 18", 0x55FFFF, g.pos.add(0, 3, 0), 1.2f);
            g.blockG(grp + "a", grp, at, "minecraft:light_blue_stained_glass", 4253951);
            g.blockG(grp + "b", grp, at, "minecraft:cyan_stained_glass", -1);
            for (int i = 0; i < 4; i++) g.textG(grp + "t" + i, grp, at.add(0, R + 1, 0), "WORLD BORDER", 0x55FFFF, "fixed", true);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                g.pillar(at.add(sx * R, -R, sz * R), R * 2 + 30, 170, 1.0, "minecraft:cyan_stained_glass", 4253951);
                g.pillar(at.add(sx * R, -R, sz * R), R * 2 + 30, 170, 0.3, "minecraft:white_concrete", 16777215);
            }
            g.wave(at, R * 1.4, 12, 0x40E8FF);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.7f);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 3f, 0.5f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            double grow = Math.min(1, t / 10.0), fade = t > 150 ? (170 - t) / 20.0 : 1;
            double size = R * 2 * grow * fade;
            if (t % 2 == 0) {
                g.shape(grp + "a", LaserFx.cube(size, t * 0.01, 0));
                g.shape(grp + "b", LaserFx.cube(size * 0.985, -t * 0.01, 0));
                for (int i = 0; i < 4; i++) {
                    double a = t * 0.03 + i * Math.PI / 2;
                    g.shape(grp + "t" + i, mat(a, 0, new Vec3(Math.sin(a) * R * grow * 0.9, 0, Math.cos(a) * R * grow * 0.9), 3, 3, 3));
                }
            }
            if (t % 20 == 0) for (LivingEntity e : g.foesNear(at, R)) {
                e.invulnerableTime = 0;
                g.hit(e, p, 14f);
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 2));
                g.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 15, 0.4, 0.6, 0.4, 0.2);
            }
            for (LivingEntity e : g.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(R + 3), e -> e instanceof Enemy && e.isAlive())) {
                Vec3 d = e.position().subtract(at);
                if (Math.max(Math.abs(d.x), Math.abs(d.z)) > R - 1.2) {     // 벽에 닿으면 튕겨 들어옴
                    e.setDeltaMovement(at.subtract(e.position()).normalize().scale(0.8).add(0, 0.2, 0)); e.hurtMarked = true;
                    g.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 10, 0.2, 0.5, 0.2, 0.3);
                }
            }
            if (t % 10 == 0) for (int k = 0; k < 2; k++) {   // 결계 벽면을 타고 흐르는 전류
                double a = g.level.random.nextDouble() * 4; int side = (int) a; double u = (a - side) * 2 - 1;
                Vec3 q = at.add(side == 0 ? R : side == 1 ? -R : u * R, (g.level.random.nextDouble() - 0.5) * R * 2, side == 2 ? R : side == 3 ? -R : u * R);
                g.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 20, 0.2, 2, 0.2, 0.4);
                g.level.sendParticles(dust(0x40E8FF, 2.5f), q.x, q.y, q.z, 10, 0.1, 2, 0.1, 0);
            }
            if (t >= 170) { g.killGroup(grp); g.wave(at, R * 2, 14, 0x40E8FF); g.burst(at, 1.2, 150, 0x40E8FF); g.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3f, 0.7f); return true; }
            return false;
        }
    }

    /** /clone 분신: 커맨드 블록 분신 3개가 적 주위를 돌며 광선 사격. */
    static final class Clones implements Fx {
        final String grp = "cl" + (Guard.uid++); final Vec3 at; int t = 0; final Vec3[] last = new Vec3[5];
        Clones(Guard g, Vec3 to) {
            this.at = to.add(0, 4, 0);
            g.say("/clone ~ ~ ~ ~ ~ ~ ^ ^ ^ x3", 0xFFFF55, g.pos.add(0, 3, 0), 1.2f);
            for (int i = 0; i < 5; i++) g.displayG(grp + i, grp + i, g.pos, i == 0 ? "minecraft:command_block" : i == 1 ? "minecraft:chain_command_block" : "minecraft:repeating_command_block", true, 4f);
            g.level.playSound(null, g.pos.x, g.pos.y, g.pos.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 3f, 0.8f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            for (int i = 0; i < 5; i++) {
                double a = t * 0.12 + i * Math.PI * 2 / 5;
                Vec3 c = t < 15 ? g.pos.lerp(at.add(Math.cos(a) * 6, 0, Math.sin(a) * 6), t / 15.0) : at.add(Math.cos(a) * 6, Math.sin(t * 0.1 + i) * 1.2, Math.sin(a) * 6);
                g.tpGroup(grp + i, c); last[i] = c;
                g.shape(grp + i, mat(t * 0.2 + i, t * 0.15, Vec3.ZERO, 2.2, 2.2, 2.2));
                g.level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 4, 0.6, 0.6, 0.6, 0.6);
                if (t > 15 && (t + i * 4) % 12 == 0) {
                    List<LivingEntity> foes = g.foesNear(at, 14);
                    if (!foes.isEmpty()) {
                        LivingEntity e = foes.get(g.level.random.nextInt(foes.size()));
                        Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0), d = to.subtract(c);
                        for (double k = 0; k < d.length(); k += 0.4) {
                            Vec3 q = c.add(d.normalize().scale(k));
                            g.level.sendParticles(i == 0 ? PURPLE : i == 1 ? GREEN : new DustParticleOptions(0xFFD84A, 1.4f), q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
                        }
                        e.invulnerableTime = 0;
                        g.hit(e, p, 22f);
                        String[] bl = {"minecraft:purple_stained_glass", "minecraft:lime_stained_glass", "minecraft:yellow_stained_glass"};
                        int[] gc = {11820287, 4259696, 16766026};
                        g.beam(c, to, 5, 0.5, bl[i % 3], gc[i % 3]);
                        g.burst(to, 0.4, 16, gc[i % 3]);
                        g.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 10, 0.3, 0.3, 0.3, 0.3);
                        g.level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.5f, 1.8f);
                    }
                }
            }
            if (t == 126) {       // 수렴포: 다섯 분신이 중심으로 동시 발사
                g.say("/execute as @e[tag=clone] run converge", 0xFFD84A, null, 0);
                for (int i = 0; i < 5; i++) if (last[i] != null) Holo.beam(g.level, g.tag, last[i], at.add(0, -3, 0), 1.0, i % 2 == 0 ? 0xFFD84A : 0xB45CFF, 2, 6, 5);
                Holo.orb(g.level, g.tag, at.add(0, -3, 0), 4.5, 0xFFD84A, 6, 4, 6);
                g.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3f, 0.6f);
            }
            if (t == 136) {
                Vec3 c0 = at.add(0, -3.8, 0);
                Holo.shock(g.level, g.tag, c0, 14, 0xFFD84A, 10);
                Holo.explosion(g.level, g.tag, c0, 5, 0xFFD84A);
                Holo.light(g.level, c0.add(0, 1, 0), 12);
                g.burst(c0.add(0, 1, 0), 1.2, 120, 0xFFD84A);
                for (LivingEntity e : g.foesNear(c0, 9)) g.hit(e, p, 80f);
                g.level.playSound(null, c0.x, c0.y, c0.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3f, 0.7f);
                Holo.slowmo(g.level, 12, 4);
            }
            if (t >= 140) {
                for (int i = 0; i < 5; i++) g.killGroup(grp + i);
                g.burst(at, 1.0, 100, 0xFFD84A); g.wave(at.add(0, -3.8, 0), 10, 10, 0xFFD84A);
                g.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 200, 6, 2, 6, 1.5);
                return true;
            }
            return false;
        }
    }

    /** /tick freeze: 반경 20칸 적의 시간을 6초 정지 → 해제 순간 누적 피해 폭발. */
    static final class TickFreeze implements Fx {
        final String grp = "tf" + (Guard.uid++); final Vec3 at; int t = 0;
        final List<net.minecraft.world.entity.Mob> frozen = new ArrayList<>();
        TickFreeze(Guard g, ServerPlayer p, Vec3 to) {
            this.at = to;
            g.say("/tick freeze", 0x88EEFF, g.pos.add(0, 3, 0), 1.6f);
            g.textG(grp + "big", grp, to.add(0, 6, 0), "⏸ TICK FROZEN", 0x88EEFF, "center", true);
            g.blockG(grp + "dome", grp, to, "minecraft:light_blue_stained_glass", 8973055);
            g.glyphs(to, 10, 120, 0x88EEFF);
            g.glyphs(to, 20, 120, 0x55FFFF);
            g.glyphs(to.add(0, 12, 0), 14, 120, 0xAAF0FF);
            g.fx.add(new Clock(g, to));
            for (LivingEntity e : g.foesNear(to, 20)) {
                if (e instanceof net.minecraft.world.entity.Mob mob && !mob.isNoAi()) { mob.setNoAi(true); frozen.add(mob); }
                double cw = e.getBbWidth() / 2 + 0.3, chh = e.getBbHeight();
                for (int k = 0; k < 5; k++) {
                    double a = k * Math.PI * 2 / 5 + 0.3;
                    double[][] rr = Holo.mul(Holo.rotY(-a), Holo.rotZ(0.35 + (k % 2) * 0.2));
                    Vec3 cc = new Vec3(Math.cos(a) * cw, chh * (0.3 + (k % 3) * 0.2), Math.sin(a) * cw);
                    Holo.block(g.level, g.tag, e.position(), k % 2 == 0 ? "minecraft:packed_ice" : "minecraft:light_blue_stained_glass", k % 2 == 0 ? -1 : 0xAAF0FF,
                            Holo.m(rr, 0.01, 0.01, 0.01, cc, true), 122, 0)
                            .key(2 + k, Holo.m(rr, 0.45, chh * 0.9, 0.45, cc, true), 5).key(116, Holo.m(rr, 0.01, 0.01, 0.01, cc, true), 3);
                }
                e.setDeltaMovement(Vec3.ZERO); e.hurtMarked = true;
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 130, 0));
            }
            g.level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3f, 0.5f);
            g.level.playSound(null, to.x, to.y, to.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 3f, 0.5f);
            Holo.screen(g.level, to, 50, 3, 0, 170, 1);
            Holo.screen(g.level, to, 50, 5, 0xC8E8FF, 6000, 0.7f);
            MechManager.cmd(g.level.getServer(), g.level, String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..40] add gojo.fxflash_w", to.x, to.y, to.z));
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            double grow = Math.min(1, t / 8.0);
            if (t % 2 == 0) {
                g.shape(grp + "dome", LaserFx.cube(40 * grow, t * 0.004, 0));
                g.shape(grp + "big", mat(0, 0, Vec3.ZERO, 4, 4, 4));
            }
            for (var mob : frozen) if (mob.isAlive()) {
                mob.setDeltaMovement(Vec3.ZERO);
                if (t % 5 == 0) g.level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 4, 0.3, 0.5, 0.3, 0);
            }
            if (t % 3 == 0) for (int i = 0; i < 20; i++) {
                double a = g.level.random.nextDouble() * Math.PI * 2, r = g.level.random.nextDouble() * 20;
                g.level.sendParticles(ParticleTypes.WHITE_ASH, at.x + Math.cos(a) * r, at.y + g.level.random.nextDouble() * 10, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t % 20 == 0) g.level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 3f, 0.5f);
            if (t < 120) return false;
            g.killGroup(grp);
            g.say("/tick unfreeze", 0x88EEFF, g.pos.add(0, 3, 0), 1.2f);
            for (var mob : frozen) {
                mob.setNoAi(false);
                if (!mob.isAlive()) continue;
                mob.invulnerableTime = 0;
                g.hit(mob, p, 80f);
                Holo.shards(g.level, g.tag, mob.position(), "minecraft:packed_ice", 8, 2.5);
                Holo.flare(g.level, g.tag, mob.position().add(0, 1, 0), 4, 0xAAF0FF, 8);
                g.level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 1, mob.getZ(), 2, 0.3, 0.3, 0.3, 0);
                g.level.sendParticles(ParticleTypes.ITEM_SNOWBALL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
            }
            for (int r = 4; r <= 20; r += 4) Laser.ring(g.level, at.add(0, 0.3, 0), r);
            g.wave(at.add(0, 0.2, 0), 24, 12, 0xAAF0FF); g.wave(at.add(0, 0.2, 0), 24, 22, 0xFFFFFF);
            Holo.sfx(g.level, at, "shatter", 3f, 0.8f); Holo.sfx(g.level, at, "impact", 2f, 0.7f);
            g.burst(at.add(0, 3, 0), 1.6, 220, 0xAAF0FF);
            g.flash(at, 50, "w");
            for (var mob : frozen) if (mob.isAlive()) g.pillar(mob.position(), 20, 10, 1.2, "minecraft:light_blue_stained_glass", 11202815);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4f, 0.5f);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 1.2f);
            Titan.shake(g.level, at, 40, true);
            return true;
        }
        void release() { for (var mob : frozen) mob.setNoAi(false); }
    }

    /** /summon 검의 비: 하늘에 마법부여된 네더라이트 검 24자루가 떠올랐다가 일제히 내리꽂힘. */
    static final class SwordRain implements Fx {
        final String grp = "sr" + (Guard.uid++); final Vec3 at; int t = 0;
        final Vec3[] spots = new Vec3[40]; final String giant;
        SwordRain(Guard g, Vec3 to) {
            this.at = to;
            g.say("/execute repeat 40 run summon netherite_sword ~ ~30 ~", 0xFF55FF, g.pos.add(0, 3, 0), 1.4f);
            giant = grp + "G";
            g.displayG(giant, giant, to.add(0, 45, 0), "minecraft:netherite_sword", true, 12f);
            g.glyphs(to.add(0, 30, 0), 16, 120, 0xFF55FF);
            g.glyphs(to.add(0, 30, 0), 24, 120, 0xFFFFFF);
            for (int i = 0; i < spots.length; i++) {
                double a = i * 2.39996, r = 1.5 + Math.sqrt(i) * 2.0;
                spots[i] = to.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                g.displayG(grp + i, grp + i, spots[i].add(0, 30, 0), "minecraft:netherite_sword", true, 6f);
            }
            g.glyphs(to, 13, 70, 0xFF55FF);
            g.level.playSound(null, to.x, to.y + 30, to.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 4f, 0.5f);
            g.level.playSound(null, to.x, to.y + 30, to.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 4f, 0.6f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            for (int i = 0; i < spots.length; i++) {
                int drop = 30 + i;          // 1.5초 떠 있다가 차례로 떨어짐
                Vec3 s = spots[i];
                if (t < drop) {
                    double hover = Math.sin(t * 0.2 + i) * 0.4;
                    if (t % 2 == 0) { g.tpGroup(grp + i, s.add(0, 30 + hover, 0)); g.shape(grp + i, mat(t * 0.05 + i, Math.PI * 0.75, Vec3.ZERO, 4, 4, 4)); }
                    if (t % 4 == 0) g.level.sendParticles(ParticleTypes.ENCHANT, s.x, s.y + 30, s.z, 3, 0.5, 0.5, 0.5, 0.5);
                } else if (t < drop + 6) {
                    double u = (t - drop + 1) / 6.0;
                    Vec3 q = s.add(0, 30 * (1 - u * u), 0);
                    g.tpGroup(grp + i, q);
                    g.shape(grp + i, mat(i, Math.PI * 0.75, Vec3.ZERO, 4, 4, 4));
                    g.level.sendParticles(PURPLE, q.x, q.y + 2, q.z, 3, 0.1, 1.5, 0.1, 0);
                    Holo.beamLayer(g.level, g.tag, q.add(0, 1, 0), q.add(0, 7, 0), 0.25, "minecraft:magenta_stained_glass", 0xFF55FF, 1, 0, 3, i);
                    if (t == drop + 5) {
                        g.level.sendParticles(ParticleTypes.EXPLOSION, s.x, s.y + 0.5, s.z, 1, 0, 0, 0, 0);
                        g.level.sendParticles(ParticleTypes.SWEEP_ATTACK, s.x, s.y + 1, s.z, 3, 1, 0.3, 1, 0);
                        g.level.sendParticles(ParticleTypes.ENCHANTED_HIT, s.x, s.y + 1, s.z, 20, 1, 0.5, 1, 0.3);
                        g.level.playSound(null, s.x, s.y, s.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 2f, 0.6f);
                        if (i % 4 == 0) g.level.playSound(null, s.x, s.y, s.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5f, 1.4f);
                        for (LivingEntity e : g.foesNear(s, 3.2)) { e.invulnerableTime = 0; g.hit(e, p, 35f); }
                        g.pillar(s, 12, 6, 0.6, "minecraft:magenta_stained_glass", 16733695);
                        g.level.sendParticles(dust(0xFF55FF, 2.0f), s.x, s.y + 0.5, s.z, 16, 1, 0.2, 1, 0);
                    }
                }
            }
            // 마지막: 거대한 검 (길이 약 16칸) 이 중심에 내리꽂힘
            int G0 = 30 + spots.length + 12;
            if (t < G0) { if (t % 2 == 0) { g.tpGroup(giant, at.add(0, 45 + Math.sin(t * 0.1), 0)); g.shape(giant, mat(t * 0.03, Math.PI * 0.75, Vec3.ZERO, 16, 16, 16)); }
                if (t % 3 == 0) g.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 45, at.z, 30, 3, 3, 3, 1.0); }
            else if (t < G0 + 8) {
                double u = (t - G0 + 1) / 8.0;
                Vec3 q = at.add(0, 45 * (1 - u * u) + 6, 0);
                g.tpGroup(giant, q); g.shape(giant, mat(0.5, Math.PI * 0.75, Vec3.ZERO, 16, 16, 16));
                g.level.sendParticles(PURPLE, q.x, q.y + 6, q.z, 20, 0.3, 6, 0.3, 0);
                if (t == G0) g.level.playSound(null, at.x, at.y + 40, at.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 5f, 0.4f);
                if (t == G0 + 7) {
                    g.wave(at.add(0, 0.2, 0), 22, 14, 0xFF55FF); g.wave(at.add(0, 0.2, 0), 22, 24, 0xFFFFFF);
                    g.burst(at.add(0, 2, 0), 1.6, 220, 0xFF55FF);
                    g.pillar(at, 60, 16, 5, "minecraft:magenta_stained_glass", 16733695);
                    g.pillar(at, 60, 16, 1.6, "minecraft:white_concrete", 16777215);
                    g.flash(at, 60, "p");
                    Slam.debris(g.level, at, Slam.ground(g.level, at), 50);
                    Holo.explosion(g.level, g.tag, at, 6, 0xFF55FF);
                    Holo.slowmo(g.level, 9, 8);
                    g.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 6, 4, 1, 4, 0);
                    g.level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 5f, 0.4f);
                    g.level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 5f, 0.5f);
                    g.level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4f, 0.8f);
                    Titan.shake(g.level, at, 60, true);
                    for (LivingEntity e : g.foesNear(at, 10)) { e.invulnerableTime = 0; g.hit(e, p, 120f);
                        e.setDeltaMovement(e.position().subtract(at).normalize().scale(1.8).add(0, 1.2, 0)); e.hurtMarked = true; }
                }
            }
            if (t < G0 + 40) return false;
            g.killGroup(giant);
            for (int i = 0; i < spots.length; i++) g.killGroup(grp + i);
            g.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1, at.z, 300, 8, 1, 8, 1.5);
            return true;
        }
    }

    /** 실체 빔 (block_display). */
    static final class Beam implements Fx {
        final String id = "bm" + (Guard.uid++); final Vec3 a, b; final int life; final double w; int t = 0;
        Beam(Guard g, Vec3 a, Vec3 b, int life, double w, String block, int glow) {
            this.a = a; this.b = b; this.life = Math.max(2, life); this.w = w;
            g.blockG(id, id, a, block, glow);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            Vec3 d = b.subtract(a);
            double len = d.length();
            if (len < 0.01) { g.killGroup(id); return true; }
            double k = t <= 2 ? t / 2.0 : Math.max(0.02, 1 - (t - 2) / (double) (life - 2));
            if (t % 2 == 1 || t >= life) g.shape(id, LaserFx.beam(d, len, w * k, t * 0.3));
            if (t >= life) { g.killGroup(id); return true; }
            return false;
        }
    }

    /** 바닥 충격파: 퍼지는 빛 원 + 솟는 먼지. */
    static final class Wave implements Fx {
        final Vec3 c; final double r; final int life; final DustParticleOptions col; int t = 0;
        Wave(Guard g, Vec3 c, double r, int life, int rgb) { this.c = c; this.r = r; this.life = Math.max(2, life); this.col = new DustParticleOptions(rgb, 2.6f); }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            double u = t / (double) life, rr = r * (1 - (1 - u) * (1 - u));
            int n = (int) (rr * 2.2) + 8;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                double x = c.x + Math.cos(a) * rr, z = c.z + Math.sin(a) * rr;
                g.level.sendParticles(col, x, c.y, z, 1, 0.05, 0.05, 0.05, 0);
                if (i % 3 == 0) g.level.sendParticles(ParticleTypes.END_ROD, x, c.y, z, 0, 0, 0.5, 0, 0.3);
                if (i % 4 == 0) g.level.sendParticles(ParticleTypes.CLOUD, x, c.y, z, 0, Math.cos(a) * 0.3, 0.1, Math.sin(a) * 0.3, 1.0);
            }
            return t >= life;
        }
    }

    /** 거대한 시계: 회전하는 바늘(빔) 2개 + 숫자 글자. */
    static final class Clock implements Fx {
        final String grp = "ck" + (Guard.uid++); final Vec3 at; int t = 0;
        static final String[] NUM = {"XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};
        Clock(Guard g, Vec3 at) {
            this.at = at.add(0, 0.3, 0);
            g.blockG(grp + "h", grp, this.at, "minecraft:white_concrete", 11202815);
            g.blockG(grp + "m", grp, this.at, "minecraft:light_blue_stained_glass", 8947967);
            for (int i = 0; i < 12; i++) g.textG(grp + "n" + i, grp, this.at, NUM[i], 0xAAF0FF, "fixed", true);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            double grow = Math.min(1, t / 10.0), fade = t > 100 ? (120 - t) / 20.0 : 1;
            if (t % 2 == 0) {
                double ah = -t * 0.02, am = -t * 0.12;     // 거꾸로 천천히 도는 시계
                g.shape(grp + "h", LaserFx.beam(new Vec3(Math.sin(ah), 0, Math.cos(ah)), 10 * grow * fade, 0.8, 0));
                g.shape(grp + "m", LaserFx.beam(new Vec3(Math.sin(am), 0, Math.cos(am)), 16 * grow * fade, 0.6, 0));
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI - i * Math.PI / 6;
                    g.shape(grp + "n" + i, mat(a + Math.PI, -Math.PI / 2, new Vec3(Math.sin(a) * 19 * grow, 0, Math.cos(a) * 19 * grow), 5 * fade, 5 * fade, 5 * fade));
                }
            }
            if (t % 20 == 0) g.level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 3f, 0.4f);
            if (t >= 120) { g.killGroup(grp); return true; }
            return false;
        }
    }

    /** 지면 블록 이름 (디스플레이용). */
    static String groundName(ServerLevel level, Vec3 c) {
        var st = Slam.ground(level, c);
        if (st == null || st.isAir() || !st.getFluidState().isEmpty()) return "minecraft:deepslate";
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
    }

    /**
     * /execute 궤도 폭격: 하늘 70칸 위에 커맨드 위성이 나타나 붉은 조준 고리가 조여들고(카운트다운),
     * 2.5초 뒤 지름 20칸짜리 4겹 궤도 레이저가 1.6초간 내리꽂힘 → 충격파 3겹·파편·화구.
     */
    static final class Orbital implements Fx {
        final Vec3 at, sat; int t = 0; final Holo.Elem label;
        static final int STRIKE = 50;
        Orbital(Guard g, Vec3 to) {
            at = to; sat = to.add(0, 70, 0);
            ServerLevel l = g.level;
            g.say("/execute positioned ~ ~70 ~ run orbital_strike", 0xFF3030, null, 0);
            Holo.circle(l, g.tag, to, 11, 0xFF3030, STRIKE + 60, 0);
            Holo.circle(l, g.tag, to.add(0, 0.3, 0), 6, 0xFFFFFF, STRIKE + 10, 0);
            Holo.ring(l, g.tag, to.add(0, 0.25, 0), 20, 2.5, 36, 0.45, 0.05, 0xFF3030, STRIKE, 4, 6, 0, 2.5, true);    // 조여드는 조준 고리
            Holo.ring(l, g.tag, to.add(0, 0.28, 0), 28, 4.0, 40, 0.25, 0.05, 0xFF7020, STRIKE, 4, 6, 0, -3.5, false);
            // 커맨드 위성: 코어 + 태양광 패널 2장 + 안테나 고리
            int life = STRIKE + 70;
            Holo.item(l, g.tag, sat, "minecraft:command_block", true, 0xFF3030, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), life, 4)
                    .key(1, Holo.m(Holo.I(), 6, 6, 6, Vec3.ZERO, false), 10).key(life - 8, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 6).spin(2).wobble(8, 0.1);
            for (int s = -1; s <= 1; s += 2) {
                Holo.block(l, g.tag, sat, "minecraft:light_blue_stained_glass", 0x40E8FF, Holo.zero(true), life, 4)
                        .key(6, Holo.bar(new Vec3(3.4 * s, 0, 0), new Vec3(15 * s, 0, 0), 5, 0.15, 0), 10)
                        .key(life - 8, Holo.bar(new Vec3(3.4 * s, 0, 0), new Vec3(3.6 * s, 0, 0), 0.1, 0.1, 0), 6).spin(2).wobble(8, 0.1);
                Holo.block(l, g.tag, sat, "minecraft:white_concrete", -1, Holo.zero(true), life, 4)
                        .key(6, Holo.bar(new Vec3(3.2 * s, 0, 0), new Vec3(15.2 * s, 0, 0), 0.25, 0.3, 0), 10)
                        .key(life - 8, Holo.bar(new Vec3(3.2 * s, 0, 0), new Vec3(3.4 * s, 0, 0), 0.05, 0.05, 0), 6).spin(2).wobble(8, 0.1);
            }
            Holo.ring(l, g.tag, sat, 1, 7, 24, 0.3, 0.05, 0xFF3030, 10, life - 26, 6, 1.57, 4, true);
            // 경고 라벨 (카운트다운)
            label = Holo.text(l, g.tag, to.add(0, 7, 0), "{text:\"\"}", "center", 0xB0200000, true, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), STRIKE + 20, 0);
            label.key(1, Holo.m(Holo.I(), 2.2, 2.2, 2.2, Vec3.ZERO, false), 4).key(STRIKE + 14, Holo.m(Holo.I(), 3, 0.01, 3, Vec3.ZERO, false), 4)
                    .text(a -> {
                        boolean bl = (a / 4) % 2 == 0;
                        String top = Holo.tx("⚠ ORBITAL STRIKE ⚠", bl ? 0xFF3030 : 0xFFFFFF, true);
                        int left = STRIKE - a;
                        String bot = left > 0 ? Holo.tx(String.format(Locale.ROOT, "T-%.1f", left / 20f), 0xFFD84A, true) : Holo.tx("IMPACT", 0xFFFFFF, true);
                        return "{text:\"\",font:\"minecraft:uniform\",extra:[" + top + ",{text:\"\\n\"}," + bot + "]}";
                    });
            l.playSound(null, sat.x, sat.y, sat.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6f, 0.5f);
            Holo.sfx(l, to, "deep", 4f, 1f);
            Holo.screen(l, to, 90, 5, 0xFF2020, 3600, 0.75f);
            Holo.sfx(l, to, "charge", 3f, 0.6f);
            l.playSound(null, to.x, to.y, to.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.5f, 1.6f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            RandomSource rnd = l.random;
            if (t < STRIKE) {
                if (t % 10 == 0) {
                    l.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 3f, 0.8f + t / (float) STRIKE);
                    Holo.beamLayer(l, g.tag, sat, at, 0.25 + t * 0.01, "minecraft:red_stained_glass", 0xFF3030, 2, 3, 3, t);
                }
                for (int i = 0; i < 6; i++) {        // 위성으로 빨려드는 에너지
                    double a = rnd.nextDouble() * Math.PI * 2, r = 8 + rnd.nextDouble() * 10;
                    Vec3 o = sat.add(Math.cos(a) * r, (rnd.nextDouble() - 0.5) * 6, Math.sin(a) * r);
                    Vec3 v = sat.subtract(o).scale(0.12);
                    l.sendParticles(ParticleTypes.END_ROD, o.x, o.y, o.z, 0, v.x, v.y, v.z, 1);
                }
                if (t == STRIKE - 14) Holo.orb(l, g.tag, sat.add(0, -4, 0), 7, 0xFF3030, 12, 2, 4);
                return false;
            }
            if (t == STRIKE) {   // 발사
                Vec3 top = at.add(0, 130, 0);
                Holo.beamLayer(l, g.tag, top, at, 22, "minecraft:white_stained_glass", -1, 3, 26, 8, 0.0);
                Holo.beamLayer(l, g.tag, top, at, 15, "minecraft:orange_stained_glass", 0xFF7020, 3, 26, 8, 0.4);
                Holo.beamLayer(l, g.tag, top, at, 9, "minecraft:red_stained_glass", 0xFF3030, 2, 28, 7, 0.8);
                Holo.beamLayer(l, g.tag, top, at, 4, "minecraft:white_concrete", 0xFFFFFF, 2, 28, 8, 1.2);
                Holo.shock(l, g.tag, at, 18, 0xFFFFFF, 6);
                Holo.shock(l, g.tag, at, 30, 0xFF3030, 12);
                Holo.shock(l, g.tag, at, 44, 0xFF7020, 20);
                Holo.circle(l, g.tag, at, 16, 0xFF7020, 46, 0);
                Holo.shards(l, g.tag, at, groundName(l, at), 30, 10);
                Holo.light(l, at.add(0, 1, 0), 60);
                Holo.explosion(l, g.tag, at, 9, 0xFF3030);
                Holo.smoke(l, g.tag, at.add(0, 3, 0), 22, 10, -1, 30, 70);
                Holo.slowmo(l, 8, 10);
                Holo.sfx(l, at, "thunder", 5f, 0.6f);
                g.flash(at, 140, "r");
                Titan.shake(l, at, 140, true);
                boolean grief = MechManager.griefOn(l.getServer());
                l.explode(p, at.x, at.y, at.z, 8f, grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.5f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 6f, 0.5f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 8f, 0.4f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 6f, 0.4f);
            }
            int k = t - STRIKE;
            if (k <= 32) {
                for (int i = 0; i < 3; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 9;
                    l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x + Math.cos(a) * r, at.y + 0.5, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                l.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1, at.z, 12, 6, 1, 6, 0);
                l.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 4, at.z, 6, 6, 6, 6, 0.05);
                l.sendParticles(dust(0xFF3030, 4f), at.x, at.y + 20, at.z, 30, 4, 20, 4, 0);
                if (k % 4 == 0) {
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(10, 60, 10), x -> x instanceof Enemy && x.isAlive())) {
                        if (Math.hypot(e.getX() - at.x, e.getZ() - at.z) > 10) continue;
                        g.hit(e, p, 70f);
                        e.igniteForSeconds(8);
                        Vec3 out = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
                        if (out.lengthSqr() > 0.01) { e.setDeltaMovement(out.normalize().scale(0.9).add(0, 0.6, 0)); e.hurtMarked = true; }
                    }
                    Laser.ring(l, at.add(0, 0.5, 0), 6 + k * 0.6);
                    if (k % 8 == 0 && MechManager.griefOn(l.getServer())) Titan.carve(l, at, Math.min(8, 3 + k / 6.0), rnd);
                }
            }
            if (k == 34) {
                g.burst(at.add(0, 2, 0), 2.0, 260, 0xFF3030);
                for (int i = 0; i < 20; i++) l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 10 + i * 2, at.z, 3, 3, 1, 3, 0);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6f, 0.6f);
            }
            return k >= 70;
        }
    }

    /**
     * /summon 커맨드 드래곤: 커맨드 블록 22마디로 된 용이 날개를 퍼덕이며 적을 쫓아 물고, 보랏빛 브레스를 뿜음 (15초).
     * 마디는 머리가 지나간 경로를 그대로 따라감 → 뱀처럼 휘어지는 몸.
     */
    static final class Dragon implements Fx {
        static final int N = 22, GAP = 2, LIFE = 300;
        final Holo.Elem[] seg = new Holo.Elem[N]; final Holo.Elem[] wing = new Holo.Elem[2];
        final List<Vec3> path = new ArrayList<>();
        Vec3 head, vel; int t = 0, biteCd = 0, breathT = 0, breathCd = 30; LivingEntity tgt; boolean dying;
        Dragon(Guard g, Vec3 to) {
            ServerLevel l = g.level;
            head = g.pos; vel = to.subtract(g.pos).normalize().scale(0.8);
            for (int i = 0; i < N; i++) {
                double s = i == 0 ? 2.8 : i < N - 5 ? 2.1 - i * 0.05 : 1.0 - (i - (N - 5)) * 0.15;
                String kind = i == 0 ? "minecraft:command_block" : i < N - 5 ? (i % 3 == 0 ? "minecraft:repeating_command_block" : "minecraft:chain_command_block") : "minecraft:repeating_command_block";
                seg[i] = Holo.item(l, g.tag, head, kind, true, i == 0 ? 0xB45CFF : i % 4 == 0 ? 0x55FFFF : -1, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), LIFE + 40, 1);
                seg[i].key(1 + i / 2, Holo.m(Holo.I(), s, s, s, Vec3.ZERO, false), 6);
            }
            for (int w = 0; w < 2; w++) {
                wing[w] = Holo.block(l, g.tag, head, "minecraft:purple_stained_glass", 0xB45CFF, Holo.zero(true), LIFE + 40, 1);
                wing[w].key(4, wingMat(w, 0), 6);
            }
            for (int i = 0; i < N * GAP + 2; i++) path.add(head);
            g.say("/summon ender_dragon ~ ~ ~ {CustomName:'커맨드 드래곤'}", 0xB45CFF, null, 0);
            Holo.circle(l, g.tag, g.pos.add(0, -1, 0), 5, 0xB45CFF, 30, 0.0);
            Holo.circle(l, g.tag, g.pos.add(vel.normalize().scale(3)), 6, 0xB45CFF, 34, Math.PI / 2);
            Holo.shock(l, g.tag, g.pos.add(0, -3, 0), 16, 0xB45CFF, 12);
            Holo.ringT(l, g.tag, g.pos, 0.5, 9, 32, 0.8, 0.1, 0xFFFFFF, 6, 4, 6, Holo.facing(vel), 5, true);
            l.playSound(null, head.x, head.y, head.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3f, 1.3f);
            Holo.sfx(l, head, "deep", 3f, 1.3f);
        }
        static float[] wingMat(int w, double flap) {
            double s = w == 0 ? -1 : 1;
            Vec3 a = new Vec3(0.6 * s, 0.4, -0.6), b = new Vec3(7.5 * s, 0.4 + flap * 4.5, -2.6);
            return Holo.bar(a, b, 3.4, 0.12, s * (0.25 + flap * 0.5));
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            RandomSource rnd = l.random;
            if (dying) return t > LIFE + 30;
            if (biteCd > 0) biteCd--;
            if (breathCd > 0) breathCd--;
            if (tgt == null || !tgt.isAlive() || t % 20 == 0) {
                tgt = null; double bd = 44 * 44;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(44), x -> x instanceof Enemy && x.isAlive())) {
                    double d = e.distanceToSqr(head);
                    if (d < bd) { bd = d; tgt = e; }
                }
            }
            Vec3 goal = tgt != null ? tgt.position().add(0, tgt.getBbHeight() * 0.6, 0)
                    : p.position().add(Math.cos(t * 0.045) * 11, 8 + Math.sin(t * 0.09) * 2.5, Math.sin(t * 0.045) * 11);
            Vec3 want = goal.subtract(head);
            want = want.lengthSqr() < 1e-4 ? vel : want.normalize().scale(tgt != null ? 1.3 : 0.85);
            Vec3 side = want.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            want = want.add(side.scale(Math.sin(t * 0.22) * 0.4)).add(0, Math.cos(t * 0.17) * 0.18, 0);
            vel = vel.lerp(want, 0.13);
            head = head.add(vel);
            path.add(0, head);
            while (path.size() > N * GAP + 2) path.remove(path.size() - 1);
            for (int i = 0; i < N; i++) {
                Vec3 a = path.get(Math.min(i * GAP, path.size() - 1)), b = path.get(Math.min(i * GAP + 1, path.size() - 1));
                Vec3 d = a.subtract(b);
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.max(1e-4, d.horizontalDistance())));
                seg[i].place(a.x, a.y, a.z, yaw, pitch);
                if (i == 3) for (Holo.Elem w : wing) w.place(a.x, a.y, a.z, yaw, 0);
                if (i % 3 == 0) l.sendParticles(dust(i % 2 == 0 ? 0xB45CFF : 0x55FFFF, 1.6f), a.x, a.y, a.z, 1, 0.4, 0.4, 0.4, 0);
            }
            if (t % 8 == 0) {      // 날갯짓
                double flap = (t / 8) % 2 == 0 ? 1 : -0.4;
                for (int w = 0; w < 2; w++) wing[w].merge("{transformation:" + Holo.mat(wingMat(w, flap)) + ",start_interpolation:0,interpolation_duration:8}");
                if ((t / 8) % 2 == 0) l.playSound(null, head.x, head.y, head.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5f, 1.4f);
            }
            l.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 2, 0.6, 0.6, 0.6, 0.02);
            l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, head.x, head.y, head.z, 2, 0.5, 0.5, 0.5, 0.01);
            if (tgt != null) {
                double dist = head.distanceTo(goal);
                if (dist < 3.2 && biteCd == 0) {   // 물기
                    biteCd = 12;
                    g.hit(tgt, p, 45f);
                    tgt.setDeltaMovement(vel.scale(0.8).add(0, 0.5, 0)); tgt.hurtMarked = true;
                    g.burst(goal, 0.7, 40, 0xB45CFF);
                    Holo.shock(l, g.tag, tgt.position(), 4.5, 0xB45CFF, 6);
                    l.playSound(null, goal.x, goal.y, goal.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 2f, 0.6f);
                    Holo.flare(l, g.tag, goal, 5, 0xB45CFF, 8);
                    Holo.sfx(l, goal, "impact", 1.5f, 1.2f);
                    l.playSound(null, goal.x, goal.y, goal.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8f, 1.8f);
                } else if (breathCd == 0 && dist > 5 && dist < 18) { breathT = 24; breathCd = 70;
                    l.playSound(null, head.x, head.y, head.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 3f, 0.8f); }
            }
            if (breathT > 0) {     // 보랏빛 브레스
                breathT--;
                Vec3 dir = (tgt != null ? goal : head.add(vel.scale(10))).subtract(head).normalize();
                for (int i = 0; i < 16; i++) {
                    Vec3 v = dir.add(rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12).scale(1.2 + rnd.nextDouble());
                    l.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL, head.x, head.y, head.z, 0, v.x, v.y, v.z, 1.0);
                }
                if (breathT % 6 == 0) Holo.beamLayer(l, g.tag, head, head.add(dir.scale(18)), 2.2, "minecraft:magenta_stained_glass", 0xFF55FF, 2, 2, 3, t);
                if (breathT % 2 == 0) for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(head, head.add(dir.scale(18))).inflate(4), x -> x instanceof Enemy && x.isAlive())) {
                    Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(head);
                    if (to.length() < 19 && to.normalize().dot(dir) > 0.92) { g.hit(e, p, 7f); e.igniteForSeconds(4); }
                }
            }
            if (t >= LIFE) {       // 꼬리부터 차례로 분해
                dying = true;
                g.say("/kill @e[name=커맨드 드래곤]", 0x888888, null, 0);
                for (int i = N - 1; i >= 0; i--) {
                    final int k = i;
                    g.later((N - 1 - i) + 1, () -> {
                        Vec3 a = path.get(Math.min(k * GAP, path.size() - 1));
                        l.sendParticles(ParticleTypes.EXPLOSION, a.x, a.y, a.z, 1, 0, 0, 0, 0);
                        l.sendParticles(ParticleTypes.ENCHANT, a.x, a.y, a.z, 30, 0.6, 0.6, 0.6, 1.0);
                        l.playSound(null, a.x, a.y, a.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f + k * 0.05f);
                        seg[k].kill();
                        if (k == 3) for (Holo.Elem w : wing) w.kill();
                    });
                }
            }
            return false;
        }
    }

    /**
     * /fill 블록 해일: 발밑 지면이 7줄 폭으로 솟구치며 앞으로 밀려가는 대지의 파도 (약 50칸).
     * 솟는 블록은 실제 그 자리 지면 재질 디스플레이 → 지형이 살아 움직이는 느낌. 휩쓸린 적은 하늘로 튕겨짐.
     */
    static final class Tsunami implements Fx {
        final Vec3 start, dir, side; int t = 0; final Set<UUID> hitSet = new HashSet<>();
        static final int LEN = 32;
        Tsunami(Guard g, ServerPlayer p, Vec3 to) {
            Vec3 d = to.subtract(p.position());
            d = new Vec3(d.x, 0, d.z);
            if (d.lengthSqr() < 1) { Vec3 lk = p.getLookAngle(); d = new Vec3(lk.x, 0, lk.z); }
            dir = d.normalize(); side = new Vec3(-dir.z, 0, dir.x);
            start = p.position().add(dir.scale(3));
            g.say("/fill ^-3 ^ ^ ^3 ^5 ^50 stone replace air", 0xC08040, null, 0);
            Holo.circle(g.level, g.tag, start, 4.5, 0xC08040, 30, 0);
            g.level.playSound(null, start.x, start.y, start.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2f, 0.5f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            RandomSource rnd = l.random;
            if (t <= LEN) {
                double dist = t * 1.6;
                Vec3 front = start.add(dir.scale(dist));
                for (int k = -3; k <= 3; k++) {
                    Vec3 c0 = front.add(side.scale(k * 1.05));
                    int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(c0.x), (int) Math.floor(c0.z));
                    if (Math.abs(gy - front.y) > 12) gy = (int) Math.floor(front.y);
                    Vec3 c = new Vec3(Math.floor(c0.x) + 0.5, gy, Math.floor(c0.z) + 0.5);
                    String name = k == 0 && t % 4 == 0 ? "minecraft:command_block" : groundName(l, c.add(0, -0.5, 0));
                    double hgt = (4.6 - Math.abs(k) * 0.75) * (0.8 + rnd.nextDouble() * 0.4) * Math.min(1, 0.4 + t / 6.0);
                    double[][] R1 = Holo.mul(Holo.rotX((rnd.nextDouble() - 0.5) * 0.5), Holo.rotZ((rnd.nextDouble() - 0.5) * 0.5));
                    float[] down = Holo.m(R1, 1.04, hgt, 1.04, new Vec3(0, -hgt / 2 - 0.6, 0), true);
                    float[] up = Holo.m(R1, 1.04, hgt, 1.04, new Vec3(0, hgt / 2 - 0.2, 0), true);
                    Holo.block(l, g.tag, c, name, k == 0 && t % 4 == 0 ? 0xC08040 : -1, down, 24, 0).key(1, up, 3).key(9, down, 8);
                    if (k % 2 == 0) l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Slam.ground(l, c.add(0, -0.5, 0))),
                            c.x, c.y + 0.5, c.z, 8, 0.5, 0.6, 0.5, 0.3);
                }
                l.sendParticles(ParticleTypes.CLOUD, front.x, front.y + 0.5, front.z, 12, 3, 0.3, 3, 0.08);
                l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, front.x, front.y + 1, front.z, 3, 3, 0.5, 3, 0.03);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(4.8, 6, 4.8), x -> x instanceof Enemy && x.isAlive())) {
                    if (!hitSet.add(e.getUUID())) continue;
                    g.hit(e, p, 55f);
                    e.setDeltaMovement(dir.scale(1.1).add(0, 1.5, 0)); e.hurtMarked = true;
                    l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.5, e.getZ(), 1, 0, 0, 0, 0);
                }
                if (t % 2 == 0) l.playSound(null, front.x, front.y, front.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 2.5f, 0.5f + rnd.nextFloat() * 0.2f);
                if (t % 4 == 0) l.playSound(null, front.x, front.y, front.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.4f);
                if (t % 6 == 0) { Titan.shake(l, front, 24, false); Holo.shock(l, g.tag, front, 7, 0xC08040, 8); }
            }
            if (t == LEN + 1) {
                Vec3 end = start.add(dir.scale(LEN * 1.6));
                Holo.shock(l, g.tag, end, 12, 0xC08040, 10);
                Holo.shards(l, g.tag, end, groundName(l, end.add(0, -0.5, 0)), 18, 6);
                Holo.explosion(l, g.tag, end, 4, 0xC08040);
                l.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3f, 0.5f);
            }
            return t >= LEN + 26;
        }
    }

    /**
     * 각성 궁극기 「영역 전개 ─ 개발자 모드」 (오버드라이브 중 웅크리고 정면 우클릭).
     *  · 지오데식 돔(유리 판 약 140장)이 아래부터 조립, 바닥엔 빛나는 사이버 격자, 하늘에선 코드 비
     *  · 필중: 2초마다 영역 안 모든 적을 글리치(순간이동·히트박스 노출·피해), 적은 영역 밖으로 못 나감
     *  · 주인은 무한 명령(재사용 대기 거의 없음)·저항·재생
     *  · 18초 뒤 「/reload」: 슬로모션 속에 돔이 산산조각 → 영역 안 전원 대피해
     */
    static final class Domain implements Fx {
        static final double R = 26; static final int LIFE = 360;
        final Vec3 c; int t = 0; final List<Holo.Elem> panels = new ArrayList<>(), grid = new ArrayList<>();
        final String marker = "jjk.devdom" + (Guard.uid++);
        final java.util.Random rnd = new java.util.Random();
        Domain(Guard g, ServerPlayer p) {
            ServerLevel l = g.level;
            c = p.position();
            g.domainOn = true;
            g.say("/execute in jjk:developer_mode run domain expand", 0x40E8FF, null, 0);
            // 영역 판정용 마커 (클라이언트 화면 효과)
            g.cmd(String.format(Locale.ROOT, "summon marker %.2f %.2f %.2f {Tags:[\"gojo.dev\",\"jjk.laserfx\",\"jjk.holo\",\"%s\",\"%s\",\"%s\"]}", c.x, c.y, c.z, Holo.SESSION, marker, g.tag));
            // 돔: 위도 6줄, 경도는 위도에 맞춰 (아래부터 차례로 조립)
            for (int i = 0; i < 6; i++) {
                double phi = Math.toRadians(7.5 + 15 * i), dphi = Math.toRadians(15);
                int n = Math.max(6, (int) Math.round(36 * Math.cos(phi)));
                for (int j = 0; j < n; j++) {
                    double lam = j * Math.PI * 2 / n + i * 0.13;
                    float[] mm = panel(phi, lam, n, dphi, 1);
                    boolean dark = (i + j) % 3 != 0;
                    mm = panelS(phi, lam, n, dphi, 1);
                    Holo.Elem e = Holo.sprite(l, g.tag, c, "hex", dark ? 0x40E8FF : 0xB45CFF, "fixed", panelS(phi, lam, n, dphi, 0.02), LIFE + 40, 0);
                    e.key(2 + i * 5 + (j % 5), mm, 6);
                    panels.add(e);
                }
            }
            // 꼭대기 덮개
            Holo.Elem cap = Holo.block(l, g.tag, c, "minecraft:cyan_stained_glass", 0x40E8FF,
                    Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, R, 0), true), LIFE + 40, 0);
            cap.key(34, Holo.m(Holo.I(), R * 0.36, 0.12, R * 0.36, new Vec3(0, R * 0.995, 0), true), 6);
            panels.add(cap);
            // 사이버 격자 바닥 (중심에서 바깥으로 그어짐)
            for (int k = -8; k <= 8; k++) {
                double off = k * 3, half = Math.sqrt(Math.max(0, R * R - off * off)) - 0.5;
                for (int axis = 0; axis < 2; axis++) {
                    Vec3 a = axis == 0 ? new Vec3(-half, 0.06, off) : new Vec3(off, 0.06, -half);
                    Vec3 b = axis == 0 ? new Vec3(half, 0.06, off) : new Vec3(off, 0.06, half);
                    Holo.Elem e = Holo.block(l, g.tag, c, k == 0 ? "minecraft:white_concrete" : "minecraft:light_blue_concrete", 0x40E8FF,
                            Holo.bar(Vec3.ZERO, new Vec3(0, 0.001, 0).add(a.scale(0.001)), 0.001, 0.001, 0), LIFE + 40, 0);
                    e.key(4 + Math.abs(k) * 2, Holo.bar(a, b, k == 0 ? 0.22 : 0.1, 0.04, 0), 10);
                    grid.add(e);
                }
            }
            Holo.circle(l, g.tag, c, R * 0.98, 0x40E8FF, LIFE, 0);
            Holo.circle(l, g.tag, c.add(0, 0.2, 0), 8, 0xFFFFFF, LIFE, 0);
            // 개막: 검은 섬광 → 슬로모션 → 타이틀
            g.cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] add gojo.fxflash_k", c.x, c.y, c.z));
            Holo.slowmo(l, 10, 24);
            Holo.banner(l, g.tag, c.add(0, 6, 0), "{text:\"\",extra:[" + Holo.tx("領域展開", 0xFFFFFF, true) + "]}", 5.0, 70);
            Holo.banner(l, g.tag, c.add(0, 4.2, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("─ 개발자 모드 : ON ─", 0x40E8FF, true) + "]}", 2.0, 70);
            g.cmd(String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] times 5 50 15", c.x, c.y, c.z));
            g.cmd(String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] subtitle {text:\"Developer Mode\",color:\"#40E8FF\",italic:true}", c.x, c.y, c.z));
            g.cmd(String.format(Locale.ROOT, "title @a[x=%.1f,y=%.1f,z=%.1f,distance=..60] title {text:\"영역 전개\",color:\"#FFFFFF\",bold:true}", c.x, c.y, c.z));
            Holo.shock(l, g.tag, c, R, 0x40E8FF, 14);
            Holo.shock(l, g.tag, c, R * 0.6, 0xFFFFFF, 8);
            Holo.pillar(l, g.tag, c, 120, 6, 0x40E8FF, 4, 20, 10);
            Holo.light(l, c.add(0, 1, 0), LIFE);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 4f, 0.6f);
            Holo.sfx(l, c, "deep", 5f, 1f); Holo.sfx(l, c, "magic", 4f, 0.6f);
            Holo.flare(l, g.tag, c.add(0, 3, 0), 40, 0x40E8FF, 16);
            Holo.screen(l, c, 60, 5, 0x0A3040, 18000, 0.85f);
            Holo.punch(l, c, 8, 0x40E8FF);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4f, 0.5f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 0.6f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 2f, 0.5f);
            Titan.shake(l, c, 60, true);
        }

        /** 돔 판 (텍스처 평면, 중심 기준). */
        static float[] panelS(double phi, double lam, int n, double dphi, double k) {
            double cp = Math.cos(phi), sp = Math.sin(phi), cl = Math.cos(lam), sl = Math.sin(lam);
            Vec3 radial = new Vec3(cp * cl, sp, cp * sl), east = new Vec3(-sl, 0, cl), north = new Vec3(-sp * cl, cp, -sp * sl);
            double[][] rm = {{east.x, north.x, radial.x}, {east.y, north.y, radial.y}, {east.z, north.z, radial.z}};
            double w = 2 * Math.PI * R * cp / n * 1.03, h = R * dphi * 1.03;
            return Holo.m(rm, w * k, h * k, 1, radial.scale(R), false);
        }

        /** 돔 판 하나의 행렬 (중심 c 기준). k = 크기 배율(0~1). */
        static float[] panel(double phi, double lam, int n, double dphi, double k) {
            double cp = Math.cos(phi), sp = Math.sin(phi), cl = Math.cos(lam), sl = Math.sin(lam);
            Vec3 radial = new Vec3(cp * cl, sp, cp * sl), east = new Vec3(-sl, 0, cl), north = new Vec3(-sp * cl, cp, -sp * sl);
            double[][] rm = {{east.x, north.x, radial.x}, {east.y, north.y, radial.y}, {east.z, north.z, radial.z}};
            double w = 2 * Math.PI * R * cp / n * 1.06, h = R * dphi * 1.06;
            return Holo.m(rm, w * k, h * k, 0.14, radial.scale(R), true);
        }

        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t <= LIFE) {
                // 코드 비
                if (t > 10) for (int i = 0; i < 2; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * (R - 3);
                    double top = 8 + rnd.nextDouble() * (R * 0.6);
                    Holo.codeDrop(l, g.tag, c.add(Math.cos(a) * r, top, Math.sin(a) * r), top - 1, i == 0 ? 0x40FF70 : 0x40E8FF, rnd);
                }
                // 주인: 무한 명령
                g.atkCd = Math.min(g.atkCd, 5);
                if (t % 20 == 0) {
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 40, 3, false, false, true));
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 40, 2, false, false, true));
                }
                // 적: 영역 밖으로 못 나감 + 둔화
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(R + 6), x -> x instanceof Enemy && x.isAlive())) {
                    double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                    if (d > R + 4) continue;
                    if (d > R - 1.5) {
                        Vec3 in = new Vec3(c.x - e.getX(), 0, c.z - e.getZ()).normalize().scale(0.9);
                        e.setDeltaMovement(in.add(0, 0.25, 0)); e.hurtMarked = true;
                        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 12, 0.2, 0.6, 0.2, 0.3);
                    }
                    if (t % 20 == 0) {
                        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 3));
                        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 30, 0));
                    }
                }
                // 필중 글리치: 2초마다
                if (t > 30 && t % 40 == 0) {
                    g.say("/execute as @e[type=!player] run glitch", 0x40FF70, null, 0);
                    int n = 0;
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(R), x -> x instanceof Enemy && x.isAlive())) {
                        if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) > R) continue;
                        Vec3 from = e.position();
                        double ox = (rnd.nextDouble() - 0.5) * 8, oz = (rnd.nextDouble() - 0.5) * 8;
                        int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(from.x + ox), (int) Math.floor(from.z + oz));
                        if (Math.abs(gy - from.y) < 6 && Math.hypot(from.x + ox - c.x, from.z + oz - c.z) < R - 2) e.teleportTo(from.x + ox, gy, from.z + oz);
                        Holo.hitbox(l, g.tag, e, n % 2 == 0 ? 0xFF3040 : 0x40FF70, 14);
                        Holo.beamLayer(l, g.tag, from.add(0, e.getBbHeight() / 2, 0), e.position().add(0, e.getBbHeight() / 2, 0), 0.5, "minecraft:lime_stained_glass", 0x40FF70, 1, 2, 3, n);
                        l.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.5);
                        g.hit(e, p, 60f);
                        n++;
                    }
                    for (int i = 0; i < 6; i++) {   // 바닥 글리치 판 (사각 노이즈)
                        double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * (R - 4);
                        Vec3 q = c.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        double w = 1 + rnd.nextDouble() * 4, d = 0.5 + rnd.nextDouble() * 2;
                        Holo.Elem e = Holo.block(l, g.tag, q, rnd.nextBoolean() ? "minecraft:lime_stained_glass" : "minecraft:magenta_stained_glass", -1,
                                Holo.m(Holo.I(), w, 0.05, d, Vec3.ZERO, true), 8, 0);
                        e.key(3, Holo.m(Holo.I(), w * 1.5, 0.05, d * 0.2, new Vec3(rnd.nextDouble() - 0.5, 0, 0), true), 2).key(6, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, true), 2);
                    }
                    l.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 3f, 0.5f);
                    Holo.sfx(l, c, "glitch", 3f, 1f);
                    l.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 3f, 0.5f);
                    l.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 3f, 0.53f);
                    if (n > 0) g.cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..40] add gojo.fxshake_s", c.x, c.y, c.z));
                }
                if (t % 10 == 0) l.playSound(null, c.x, c.y + 10, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3f, 0.5f + (t % 40) / 80f);
                if (t == LIFE - 40) {      // 붕괴 예고
                    g.say("/reload", 0xFF3040, null, 0);
                    Holo.banner(l, g.tag, c.add(0, 8, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⚠ /reload ⚠", 0xFF3040, true) + "]}", 3.0, 40);
                    l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4f, 0.8f);
                    for (Holo.Elem e : grid) e.keyIn(1, Holo.m(Holo.I(), 0.001, 0.001, 0.001, Vec3.ZERO, true), 30);
                }
                if (t == LIFE) collapse(g, p);
                return false;
            }
            return t > LIFE + 40;
        }

        /** /reload: 슬로모션 속에 돔 판이 사방으로 산산조각 + 영역 안 대피해. */
        void collapse(Guard g, ServerPlayer p) {
            ServerLevel l = g.level;
            Holo.slowmo(l, 6, 16);
            g.flash(c, 80, "w");
            for (Holo.Elem e : panels) {
                double[][] rr = Holo.mul(Holo.rotX(rnd.nextDouble() * 6), Holo.rotZ(rnd.nextDouble() * 6));
                Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() * 0.8, rnd.nextDouble() - 0.5).normalize();
                Vec3 out = new Vec3((rnd.nextDouble() - 0.5) * 2 * R, R * (0.2 + rnd.nextDouble()), (rnd.nextDouble() - 0.5) * 2 * R).add(dir.scale(R * 0.6));
                double s = 0.6 + rnd.nextDouble() * 2;
                e.keyIn(1, Holo.m(rr, s, 0.1, s, out, true), 16).keyIn(18, Holo.m(rr, 0.01, 0.01, 0.01, out.scale(1.2), true), 6).endIn(26);
            }
            for (Holo.Elem e : grid) e.endIn(2);
            g.cmd("kill @e[type=marker,tag=" + marker + "]");
            Holo.shock(l, g.tag, c, R * 1.5, 0xFFFFFF, 10);
            Holo.shock(l, g.tag, c, R * 1.2, 0x40E8FF, 16);
            Holo.orb(l, g.tag, c.add(0, R * 0.4, 0), R * 0.5, 0x40E8FF, 3, 2, 8);
            Holo.banner(l, g.tag, c.add(0, 5, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("SYSTEM RELOADED", 0xFFFFFF, true) + "]}", 3.0, 40);
            g.burst(c.add(0, 3, 0), 3.0, 400, 0x40E8FF);
            int n = 0;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(R + 2), x -> x instanceof Enemy && x.isAlive())) {
                if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) > R + 2) continue;
                g.hit(e, p, 220f);
                e.setDeltaMovement(new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).normalize().scale(1.6).add(0, 1.4, 0)); e.hurtMarked = true;
                n++;
            }
            for (int i = 0; i < 30; i++) l.playSound(null, c.x + (rnd.nextDouble() - 0.5) * R, c.y + rnd.nextDouble() * R, c.z + (rnd.nextDouble() - 0.5) * R,
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2f, 0.5f + rnd.nextFloat() * 0.8f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6f, 0.4f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 6f, 0.5f);
            Holo.explosion(l, g.tag, c, 10, 0x40E8FF);
            Holo.sfx(l, c, "shatter", 6f, 0.5f);
            Titan.shake(l, c, 80, true);
            g.domainOn = false;
            g.say("/reload ✔ " + n + "개 엔티티 정리됨", 0xAAAAAA, null, 0);
        }
        void abort(Guard g) { g.cmd("kill @e[type=marker,tag=" + marker + "]"); g.domainOn = false; }
    }

    /**
     * /execute 시공 절단: 화면이 어두워진 뒤 목표를 가르는 거대한 참격 3줄기 (길이 64칸 평면).
     * 평면에 닿은 모든 것이 베이고(#grief 면 지형도 한 줄로 갈라짐), 마지막에 세계가 갈라지듯 파편이 터짐.
     */
    static final class WorldSlash implements Fx {
        final Vec3 at; int t = 0; final Vec3[] us = new Vec3[3], vs = new Vec3[3]; final java.util.Random rnd = new java.util.Random();
        static final double HALF = 32;
        WorldSlash(Guard g, Vec3 to) {
            at = to;
            ServerLevel l = g.level;
            g.say("/execute positioned ~ ~ ~ run slash ^ ^ ^64", 0xFFFFFF, null, 0);
            for (int i = 0; i < 3; i++) {
                double yaw = rnd.nextDouble() * Math.PI + i * 1.05, roll = (rnd.nextDouble() - 0.5) * 1.1;
                Vec3 u = new Vec3(Math.cos(yaw), (rnd.nextDouble() - 0.5) * 0.25, Math.sin(yaw)).normalize();
                Vec3 side = u.cross(new Vec3(0, 1, 0)).normalize(), up = side.cross(u).normalize();
                us[i] = u; vs[i] = up.scale(Math.cos(roll)).add(side.scale(Math.sin(roll))).normalize();
            }
            g.cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..50] add gojo.fxflash_k", to.x, to.y, to.z));
            Holo.orb(l, g.tag, g.pos, 3, 0xFFFFFF, 6, 4, 4);
            Holo.circle(l, g.tag, to, 9, 0xFFFFFF, 50, 0);
            l.playSound(null, to.x, to.y, to.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3f, 1.6f);
        }
        void slash(Guard g, ServerPlayer p, int i) {
            ServerLevel l = g.level;
            Vec3 a = us[i].scale(-HALF), b = us[i].scale(HALF);
            double roll = Math.atan2(vs[i].dot(us[i].cross(new Vec3(0, 1, 0)).normalize()), vs[i].dot(us[i].cross(new Vec3(0, 1, 0)).normalize().cross(us[i]).normalize()));
            // 흰 칼날 · 검은 잔상 · 붉은 테두리
            Holo.Elem w = Holo.block(l, g.tag, at, "minecraft:white_concrete", 0xFFFFFF, Holo.bar(a.scale(0.02), b.scale(0.02), 0.04, 0.02, roll), 16, 0);
            w.key(1, Holo.bar(a, b, 0.05, 0.04, roll), 1).key(3, Holo.bar(a.scale(1.05), b.scale(1.05), 0.06, 1.4, roll), 2).key(6, Holo.bar(a.scale(1.1), b.scale(1.1), 0.02, 0.001, roll), 8);
            Holo.Elem k = Holo.block(l, g.tag, at, "minecraft:black_stained_glass", -1, Holo.bar(a.scale(0.02), b.scale(0.02), 0.2, 0.02, roll), 20, 0);
            k.key(2, Holo.bar(a, b, 0.3, 4.5, roll), 3).key(8, Holo.bar(a.scale(1.08), b.scale(1.08), 0.05, 0.001, roll), 10);
            Holo.Elem r = Holo.block(l, g.tag, at, "minecraft:red_stained_glass", 0xFF2030, Holo.bar(a.scale(0.02), b.scale(0.02), 0.1, 0.02, roll), 18, 0);
            r.key(2, Holo.bar(a, b, 0.12, 2.2, roll), 2).key(6, Holo.bar(a.scale(1.06), b.scale(1.06), 0.02, 0.001, roll), 9);
            Vec3 n = us[i].cross(vs[i]).normalize();
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(HALF), x -> x instanceof Enemy && x.isAlive())) {
                Vec3 q = e.position().add(0, e.getBbHeight() / 2, 0).subtract(at);
                if (Math.abs(q.dot(us[i])) > HALF || Math.abs(q.dot(n)) > 1.6 + e.getBbWidth() / 2 || Math.abs(q.dot(vs[i])) > 12) continue;
                g.hit(e, p, 160f);
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 3, 0.3, 0.3, 0.3, 0);
                l.sendParticles(dust(0xFF2030, 2f), e.getX(), e.getY() + 1, e.getZ(), 30, 0.3, 0.6, 0.3, 0);
            }
            if (MechManager.griefOn(l.getServer())) {     // 지형 절단: 칼날이 지나간 한 줄
                for (double s = -HALF; s <= HALF; s += 0.5) for (double h = -6; h <= 6; h += 0.7) {
                    BlockPosHolder.set(at.add(us[i].scale(s)).add(vs[i].scale(h)));
                    var bp = BlockPosHolder.pos;
                    var st = l.getBlockState(bp);
                    if (!st.isAir() && st.getDestroySpeed(l, bp) >= 0) l.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
            for (double s = -HALF; s <= HALF; s += 2) {
                Vec3 q = at.add(us[i].scale(s));
                l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0.05);
                if (rnd.nextInt(3) == 0) l.sendParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            l.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 4f, 0.5f);
            Holo.sfx(l, at, "slash", 4f, 0.7f + i * 0.1f);
            Holo.flare(l, g.tag, at, 14, 0xFFFFFF, 6);
            Holo.screen(l, at, 60, 3, 0, 170, 1);
            Holo.screen(l, at, 60, 4, 0, 400, 0.9f);
            Holo.screen(l, at, 60, 2, 0, 500, 5f);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 3f, 0.5f);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 1.8f);
            Holo.slowmo(l, 12, 3);
            g.cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..50] add gojo.fxshake_s", at.x, at.y, at.z));
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t == 10) slash(g, p, 0);
            if (t == 17) slash(g, p, 1);
            if (t == 24) slash(g, p, 2);
            if (t == 40) {   // 세계가 갈라진다
                ServerLevel l = g.level;
                g.flash(at, 60, "w");
                Holo.slowmo(l, 8, 10);
                for (int i = 0; i < 3; i++) for (int k = -2; k <= 2; k++) {
                    Vec3 q = at.add(us[i].scale(k * 9));
                    Holo.shards(l, g.tag, q, Domainless.ground(l, q), 6, 4);
                    l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
                Holo.shock(l, g.tag, at, 24, 0xFFFFFF, 10);
                Holo.shock(l, g.tag, at, 16, 0xFF2030, 8);
                Holo.explosion(l, g.tag, at, 6, 0xFF2030);
                Holo.sfx(l, at, "shatter", 4f, 0.5f);
                g.burst(at.add(0, 1, 0), 1.8, 200, 0xFFFFFF);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5f, 0.4f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4f, 0.6f);
                Titan.shake(l, at, 60, true);
            }
            return t >= 60;
        }
    }

    static final class BlockPosHolder { static net.minecraft.core.BlockPos pos; static void set(Vec3 v) { pos = net.minecraft.core.BlockPos.containing(v); } }
    static final class Domainless { static String ground(ServerLevel l, Vec3 q) { return groundName(l, q.add(0, -0.5, 0)); } }

    /**
     * /summon 커맨드 거인 (재제작): 높이 약 22칸 기사형 거신.
     *  하늘의 차원문에서 부품이 유성처럼 떨어져 맞물림 → 코어 점화·포효 → 오른 주먹 강타 → 양 주먹 대지 분쇄(슬로모션)
     *  → 피니시: 가슴 코어 빔으로 앞을 휩쓸기 → 빛기둥과 함께 승천 해체.
     */
    static final class Colossus implements Fx {
        static final double K = 4.2;
        static final Vec3[] PIV = {Vec3.ZERO, new Vec3(-1.6, 3.6, 0), new Vec3(1.6, 3.6, 0), new Vec3(-0.55, 2.0, 0), new Vec3(0.55, 2.0, 0)};
        static final double[][] I0 = Holo.I();
        static final Golem.Part[] P = {
                new Golem.Part("minecraft:polished_blackstone", -1, 1.8, 0.5, 1.05, new Vec3(0, 2.15, 0), 0, I0),
                new Golem.Part("minecraft:gold_block", -1, 1.85, 0.14, 1.1, new Vec3(0, 2.43, 0), 0, I0),
                new Golem.Part("minecraft:command_block", -1, 2.3, 1.6, 1.35, new Vec3(0, 3.1, 0), 0, I0),
                new Golem.Part("minecraft:chain_command_block", -1, 1.75, 0.95, 0.25, new Vec3(0, 3.25, 0.72), 0, I0),
                new Golem.Part("minecraft:orange_stained_glass", 0xFFAA33, 0.55, 0.55, 0.15, new Vec3(0, 3.28, 0.88), 0, Holo.rotZ(Math.PI / 4)),
                new Golem.Part("minecraft:repeating_command_block", -1, 1.2, 0.8, 1.4, new Vec3(-1.65, 3.98, 0), 0, Holo.rotZ(0.35)),
                new Golem.Part("minecraft:repeating_command_block", -1, 1.2, 0.8, 1.4, new Vec3(1.65, 3.98, 0), 0, Holo.rotZ(-0.35)),
                new Golem.Part("minecraft:gold_block", -1, 1.25, 0.12, 1.45, new Vec3(-1.62, 4.36, 0), 0, Holo.rotZ(0.35)),
                new Golem.Part("minecraft:gold_block", -1, 1.25, 0.12, 1.45, new Vec3(1.62, 4.36, 0), 0, Holo.rotZ(-0.35)),
                new Golem.Part("minecraft:command_block", -1, 1.0, 1.0, 1.0, new Vec3(0, 4.42, 0.02), 0, I0),
                new Golem.Part("minecraft:white_concrete", 0xFF6020, 0.82, 0.12, 0.06, new Vec3(0, 4.48, 0.55), 0, I0),
                new Golem.Part("minecraft:gold_block", -1, 0.14, 0.55, 0.14, new Vec3(-0.32, 5.05, 0.3), 0, Holo.rotZ(0.25)),
                new Golem.Part("minecraft:gold_block", -1, 0.16, 0.7, 0.16, new Vec3(0, 5.12, 0.34), 0, I0),
                new Golem.Part("minecraft:gold_block", -1, 0.14, 0.55, 0.14, new Vec3(0.32, 5.05, 0.3), 0, Holo.rotZ(-0.25)),
                new Golem.Part("minecraft:white_concrete", 0xFFAA33, 0.07, 1.25, 0.02, new Vec3(0, 3.0, 0.68), 0, I0),
                new Golem.Part("minecraft:white_concrete", 0xFFAA33, 1.75, 0.05, 0.03, new Vec3(0, 3.74, 0.68), 0, I0),
                new Golem.Part("fx:orb", 0xFF3020, 1.5, 0.55, 1, new Vec3(0, 4.48, 0.6), 0, I0),
                new Golem.Part("fx:orb", 0xFFAA33, 2.0, 2.0, 1, new Vec3(0, 3.28, 0.98), 0, I0),
                new Golem.Part("fx:runering", 0xFFAA33, 4.6, 4.6, 1, new Vec3(0, 3.8, -0.95), 0, I0),
                new Golem.Part("fx:circle", 0xFF7020, 3.4, 3.4, 1, new Vec3(0, 3.8, -0.98), 0, I0),
                // 왼팔
                new Golem.Part("minecraft:chain_command_block", -1, 0.72, 1.6, 0.72, new Vec3(0, -0.8, 0), 1, I0),
                new Golem.Part("minecraft:command_block", -1, 1.1, 1.0, 1.1, new Vec3(0, -1.95, 0), 1, I0),
                new Golem.Part("minecraft:gold_block", -1, 1.12, 0.2, 0.3, new Vec3(0, -2.32, 0.45), 1, I0),
                new Golem.Part("fx:orb", 0xFFAA33, 1.8, 1.8, 1, new Vec3(0, -2.0, 0.62), 1, I0),
                // 오른팔
                new Golem.Part("minecraft:chain_command_block", -1, 0.72, 1.6, 0.72, new Vec3(0, -0.8, 0), 2, I0),
                new Golem.Part("minecraft:command_block", -1, 1.1, 1.0, 1.1, new Vec3(0, -1.95, 0), 2, I0),
                new Golem.Part("minecraft:gold_block", -1, 1.12, 0.2, 0.3, new Vec3(0, -2.32, 0.45), 2, I0),
                new Golem.Part("fx:orb", 0xFFAA33, 1.8, 1.8, 1, new Vec3(0, -2.0, 0.62), 2, I0),
                // 다리
                new Golem.Part("minecraft:repeating_command_block", -1, 0.85, 1.7, 0.9, new Vec3(0, -0.85, 0), 3, I0),
                new Golem.Part("minecraft:polished_blackstone", -1, 1.05, 0.38, 1.25, new Vec3(0, -1.83, 0.1), 3, I0),
                new Golem.Part("minecraft:white_concrete", 0xFFAA33, 0.06, 1.1, 0.02, new Vec3(0, -0.85, 0.46), 3, I0),
                new Golem.Part("minecraft:repeating_command_block", -1, 0.85, 1.7, 0.9, new Vec3(0, -0.85, 0), 4, I0),
                new Golem.Part("minecraft:polished_blackstone", -1, 1.05, 0.38, 1.25, new Vec3(0, -1.83, 0.1), 4, I0),
                new Golem.Part("minecraft:white_concrete", 0xFFAA33, 0.06, 1.1, 0.02, new Vec3(0, -0.85, 0.46), 4, I0)};
        static final double[] REST = {-0.15, -0.15, 0, 0};

        final Vec3 base, tgt; final double[][] F; int t = 0;
        final Holo.Elem[] el = new Holo.Elem[P.length];
        final int[] landT = new int[P.length];
        Holo.Elem bw, bc, bg1, bg2;
        Vec3 sweepFrom, sweepTo;

        static boolean fx(Golem.Part p) { return p.block().startsWith("fx:"); }
        float[] mat(Golem.Part p, double[] pose) {
            double[][] r; Vec3 c;
            if (p.pivot() == 0) { r = p.extra(); c = p.c(); }
            else { double[][] rx = Holo.rotX(pose[p.pivot() - 1]); r = Holo.mul(rx, p.extra()); c = PIV[p.pivot()].add(Holo.apply(rx, p.c())); }
            return Holo.m(Holo.mul(F, r), p.sx() * K, p.sy() * K, p.sz() * K, Holo.apply(F, c.scale(K)), !fx(p));
        }
        Vec3 world(Vec3 local) { return base.add(Holo.apply(F, local.scale(K))); }
        Vec3 fist(int side, double phi) {
            Vec3 pv = PIV[side < 0 ? 1 : 2];
            return world(pv.add(Holo.apply(Holo.rotX(phi), new Vec3(0, -2.0, 0.3))));
        }
        Vec3 core() { return world(new Vec3(0, 3.28, 1.0)); }
        void pose(double[] p, int dur) { for (int i = 0; i < P.length; i++) if (P[i].pivot() != 0) el[i].keyIn(1, mat(P[i], p), dur); }

        Colossus(Guard g, ServerPlayer p, Vec3 to) {
            ServerLevel l = g.level;
            tgt = to;
            Vec3 f = new Vec3(to.x - p.getX(), 0, to.z - p.getZ());
            f = f.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : f.normalize();
            Vec3 b0 = to.subtract(f.scale(11));
            int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(b0.x), (int) Math.floor(b0.z));
            base = new Vec3(b0.x, Math.abs(gy - to.y) < 12 ? gy : to.y, b0.z);
            F = Holo.rotY(Math.atan2(f.x, f.z));
            g.say("/summon command_colossus ~ ~ ~ {Size:22,Crown:1b}", 0xFFAA33, null, 0);
            // 차원문 + 하늘 균열
            Holo.circle(l, g.tag, base, 18, 0xFFAA33, 70, 0);
            Holo.circle(l, g.tag, base.add(0, 0.2, 0), 9, 0xFFFFFF, 60, 0);
            Holo.circle(l, g.tag, base.add(0, 70, 0), 22, 0xFF7020, 50, 0);
            Holo.pillar(l, g.tag, base, 70, 8, 0xFFAA33, 4, 30, 10);
            Holo.screen(l, base, 70, 5, 0xFF8020, 4000, 0.6f);
            Holo.sfx(l, base, "deep", 4f, 0.9f);
            // 부품: 하늘(70칸 위)에서 유성처럼 떨어져 제자리에
            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < P.length; i++) {
                Golem.Part pt = P[i];
                boolean fx = fx(pt);
                Vec3 sky = new Vec3((rnd.nextDouble() - 0.5) * 30, 60 + rnd.nextDouble() * 20, (rnd.nextDouble() - 0.5) * 30);
                double[][] spin = Holo.mul(Holo.rotX(rnd.nextDouble() * 6), Holo.rotZ(rnd.nextDouble() * 6));
                int start = 6 + i, dur = 7 + rnd.nextInt(4);
                landT[i] = fx ? -1 : start + dur;
                el[i] = fx ? Holo.sprite(l, g.tag, base, pt.block().substring(3), pt.glow(), "fixed", Holo.m(F, 0.01, 0.01, 0.01, Vec3.ZERO, false), 190, 0)
                        : Holo.block(l, g.tag, base, pt.block(), pt.glow(), Holo.m(spin, pt.sx() * K * 0.8, pt.sy() * K * 0.8, pt.sz() * K * 0.8, sky, true), 190, 0);
                el[i].key(fx ? 46 : start, mat(pt, REST), fx ? 6 : dur);
            }
        }

        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            java.util.Random rnd = new java.util.Random();
            // 부품 착지
            for (int i = 0; i < P.length; i++) if (landT[i] == t) {
                Vec3 w = world(P[i].pivot() == 0 ? P[i].c() : PIV[P[i].pivot()].add(P[i].c()));
                Holo.flare(l, g.tag, w, 6, 0xFFAA33, 6);
                Holo.sparks(l, g.tag, w, 6, 4, 0xFFAA33, 8);
                if (i % 3 == 0) Holo.sfx(l, w, "impact", 2f, 0.6f + rnd.nextFloat() * 0.3f);
                if (P[i].pivot() >= 3) Holo.explosion(l, g.tag, w.add(0, -w.y + base.y, 0), 2.5, 0xFFAA33);
            }
            if (t > 4 && t < 44 && t % 2 == 0) {     // 떨어지는 부품의 불꼬리
                Vec3 q = base.add((rnd.nextDouble() - 0.5) * 20, 30 + rnd.nextDouble() * 40, (rnd.nextDouble() - 0.5) * 20);
                Holo.beamGlow(l, g.tag, q.add(0, 10, 0), q, 1.4, 0xFF7020, 1, 1, 4);
            }
            if (t == 46) {          // 코어 점화
                Vec3 c = core();
                Holo.flare(l, g.tag, c, 18, 0xFFAA33, 14);
                Holo.glow(l, g.tag, c, 4, 0xFFAA33, 3, 4, 10);
                Holo.shock(l, g.tag, base, 20, 0xFFAA33, 12);
                Holo.sfx(l, c, "charge", 3f, 1.4f);
                Holo.light(l, c, 120);
                l.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5f, 0.5f);
            }
            if (t == 52) { pose(new double[]{-2.6, -2.6, -0.15, 0.15}, 6); }       // 포효
            if (t == 58) {
                l.playSound(null, base.x, base.y + 20, base.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 5f, 0.6f);
                l.playSound(null, base.x, base.y + 20, base.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 5f, 0.5f);
                Holo.sfx(l, base, "deep", 5f, 1.2f);
                Holo.punch(l, base, 6, 0xFFAA33);
                Holo.screen(l, base, 70, 6, 0xFFAA33, 900, 1);
                Holo.shockTex(l, g.tag, base, 34, 0xFFAA33, 14);
                Holo.dustRing(l, g.tag, base, 26, 22, 30);
                Holo.flare(l, g.tag, world(new Vec3(0, 4.48, 0.7)), 10, 0xFF3020, 12);
            }
            if (t == 66) pose(REST, 6);
            // 오른 주먹 강타
            if (t == 72) pose(new double[]{-0.15, 0.6, 0.2, -0.2}, 5);
            if (t == 78) { pose(new double[]{-0.3, -1.35, -0.25, 0.3}, 2); Holo.sfx(l, base, "whoosh", 3f, 0.5f); }
            if (t == 80) impact(g, p, fist(1, -1.35), 8, 130f, false);
            if (t == 86) pose(REST, 5);
            // 양 주먹 대지 분쇄
            if (t == 90) { pose(new double[]{-2.9, -2.9, -0.2, 0.2}, 8); Holo.sfx(l, base, "charge", 3f, 0.8f); }
            if (t == 100) { pose(new double[]{-0.95, -0.95, 0.35, -0.35}, 3); Holo.sfx(l, base, "whoosh", 4f, 0.4f); }
            if (t == 103) impact(g, p, fist(1, -0.95).add(fist(-1, -0.95)).scale(0.5), 14, 230f, true);
            if (t == 110) pose(REST, 6);
            // 피니시: 코어 빔 휩쓸기
            if (t >= 114 && t < 128) {
                Vec3 c = core();
                if (t == 114) { Holo.sfx(l, c, "charge", 4f, 1f); Holo.screen(l, c, 70, 7, 0, 700, 0.14f); Holo.screen(l, c, 70, 6, 0xFFAA33, 700, 1); }
                for (int k = 0; k < 3; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2, b2 = rnd.nextDouble() * 2 - 1;
                    Vec3 o = c.add(Math.cos(a) * 14, b2 * 8, Math.sin(a) * 14);
                    Holo.beamLayer(l, g.tag, o, c, 0.4, "minecraft:orange_stained_glass", 0xFFAA33, 2, 1, 3, k);
                }
                if (t % 3 == 0) Holo.glow(l, g.tag, c, 1.5 + (t - 114) * 0.25, 0xFFAA33, 2, 1, 3);
            }
            if (t == 128) {
                Vec3 c = core();
                Vec3 fwd = Holo.apply(F, new Vec3(0, 0, 1)), side = Holo.apply(F, new Vec3(1, 0, 0));
                Vec3 mid = new Vec3(tgt.x, base.y, tgt.z);
                sweepFrom = mid.add(side.scale(-22)).add(fwd.scale(-4));
                sweepTo = mid.add(side.scale(22)).add(fwd.scale(6));
                Vec3 d0 = sweepFrom.subtract(c);
                bw = Holo.block(l, g.tag, c, "minecraft:white_concrete", 0xFFFFFF, Holo.bar(Vec3.ZERO, d0, 0.01, 0.01, 0), 40, 1);
                bc = Holo.block(l, g.tag, c, "minecraft:orange_stained_glass", 0xFFAA33, Holo.bar(Vec3.ZERO, d0, 0.01, 0.01, 0), 40, 1);
                bw.key(1, Holo.bar(Vec3.ZERO, d0, 1.6, 1.6, 0), 2);
                bc.key(1, Holo.bar(Vec3.ZERO, d0, 4.2, 4.2, 0.5), 2);
                Holo.flare(l, g.tag, c, 30, 0xFFAA33, 16);
                Holo.sfx(l, c, "laser", 5f, 0.6f);
                Holo.punch(l, c, 7, 0xFFAA33);
                Holo.slowmo(l, 12, 6);
            }
            if (t > 128 && t <= 150 && bw != null) {
                double u = (t - 128) / 22.0;
                Vec3 c = core(), hitP = sweepFrom.lerp(sweepTo, u * u * (3 - 2 * u));
                int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(hitP.x), (int) Math.floor(hitP.z));
                if (Math.abs(gy - hitP.y) < 12) hitP = new Vec3(hitP.x, gy, hitP.z);
                Vec3 d = hitP.subtract(c);
                double wob = 1 + Math.sin(t * 1.3) * 0.12;
                bw.keyIn(1, Holo.bar(Vec3.ZERO, d, 1.6 * wob, 1.6 * wob, t * 0.3), 1);
                bc.keyIn(1, Holo.bar(Vec3.ZERO, d, 4.2 / wob, 4.2 / wob, -t * 0.2), 1);
                Holo.beamGlow(l, g.tag, c, hitP, 9, 0xFFAA33, 1, 0, 2);
                if (t % 3 == 0) Holo.explosion(l, g.tag, hitP, 3.2, 0xFFAA33);
                else { Holo.glow(l, g.tag, hitP.add(0, 1, 0), 2.5, 0xFFAA33, 1, 1, 4); Holo.sparks(l, g.tag, hitP, 6, 5, 0xFFAA33, 8); }
                Holo.decal(l, g.tag, hitP.add(0, 0.08, 0), "crack", 0xFF5020, 2, 7, 2, 80, 20, 0, Holo.I());
                if (MechManager.griefOn(l.getServer()) && t % 2 == 0) Titan.carve(l, hitP, 2.5, l.random);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(hitP, hitP).inflate(4, 6, 4), x -> x instanceof Enemy && x.isAlive())) {
                    g.hit(e, p, 60f); e.igniteForSeconds(8);
                }
                if (t == 150) { bw.keyIn(1, Holo.bar(Vec3.ZERO, d, 0.01, 0.01, 0), 4).endIn(6); bc.keyIn(1, Holo.bar(Vec3.ZERO, d, 0.01, 0.01, 0), 5).endIn(7); }
            }
            // 승천 해체
            if (t == 160) {
                for (Holo.Elem e : el) {
                    Vec3 up = new Vec3((rnd.nextDouble() - 0.5) * 16, 70 + rnd.nextDouble() * 30, (rnd.nextDouble() - 0.5) * 16);
                    e.keyIn(1, Holo.m(Holo.mul(Holo.rotX(rnd.nextDouble() * 5), Holo.rotZ(rnd.nextDouble() * 5)), 0.3, 0.3, 0.3, up, true), 20).endIn(24);
                }
                Holo.pillar(l, g.tag, base, 120, 14, 0xFFAA33, 3, 16, 10);
                Holo.circle(l, g.tag, base, 16, 0xFFFFFF, 34, 0);
                Holo.embers(l, g.tag, base.add(0, 8, 0), 30, 8, 0xFFAA33, 70);
                Holo.sfx(l, base, "magic", 4f, 0.5f);
                Holo.sfx(l, base, "deep", 3f, 1.5f);
            }
            if (t > 46 && t < 160 && t % 3 == 0) {
                Vec3 c = core();
                l.sendParticles(dust(0xFFAA33, 2.5f), c.x, c.y, c.z, 4, 1.5, 1.5, 1.5, 0);
                l.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 2, 1, 1, 1, 0.02);
            }
            return t >= 190;
        }

        void impact(Guard g, ServerPlayer p, Vec3 at0, double r, float dmg, boolean big) {
            ServerLevel l = g.level;
            int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at0.x), (int) Math.floor(at0.z));
            Vec3 at = new Vec3(at0.x, Math.abs(gy - at0.y) < 16 ? gy : base.y, at0.z);
            Holo.explosion(l, g.tag, at, big ? 10 : 5.5, 0xFFAA33);
            Holo.shock(l, g.tag, at, r * 1.8, 0xFFAA33, big ? 16 : 11);
            Holo.circle(l, g.tag, at, r * 0.8, 0xFF7020, 40, 0);
            Holo.shards(l, g.tag, at, groundName(l, at.add(0, -0.5, 0)), big ? 34 : 18, big ? 11 : 6);
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, big ? 6 : 2, r * 0.4, 0.3, r * 0.4, 0);
            Slam.dustRing(l, at, r, Slam.ground(l, at));
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 8, r), x -> x instanceof Enemy && x.isAlive())) {
                g.hit(e, p, dmg);
                e.setDeltaMovement(new Vec3(e.getX() - at.x, 0, e.getZ() - at.z).normalize().scale(1.6).add(0, 1.4, 0)); e.hurtMarked = true;
            }
            if (MechManager.griefOn(l.getServer())) Titan.carve(l, at, big ? 6 : 3.5, l.random);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 6f, 0.4f);
            Titan.shake(l, at, big ? 100 : 60, true);
            if (big) { Holo.slowmo(l, 6, 12); g.flash(at, 70, "o"); Holo.smoke(l, g.tag, at.add(0, 2, 0), 22, 9, -1, 18, 60); }
        }
    }

    /**
     * /execute 천공의 창: 하늘 40칸 위에 길이 30칸 빛의 창이 형성 → 고리가 돌며 충전 → 순식간에 내리꽂힘.
     * 착탄: 대폭발 + 빛기둥 + 여진 충격파 3번, 창은 땅에 박힌 채 빛나다가 사라짐.
     */
    static final class Spear implements Fx {
        final Vec3 at, top; int t = 0; final List<Holo.Elem> parts = new ArrayList<>(); final List<float[]> up = new ArrayList<>();
        static final double H = 40;
        Spear(Guard g, Vec3 to) {
            ServerLevel l = g.level;
            at = to; top = to.add(0, H, 0);
            g.say("/execute positioned ~ ~40 ~ run summon spear_of_heaven", 0xFFE070, null, 0);
            // 창 (top 기준 지역 좌표, 끝 = (0,0,0) 아래쪽, 자루 끝 = (0,30,0))
            add(Holo.block(l, g.tag, top, "minecraft:white_concrete", 0xFFE070, Holo.bar(new Vec3(0, 4, 0), new Vec3(0, 4.01, 0), 0.01, 0.01, 0), 140, 0),
                    Holo.bar(new Vec3(0, 4, 0), new Vec3(0, 30, 0), 0.7, 0.7, 0), 2);
            add(Holo.block(l, g.tag, top, "minecraft:yellow_stained_glass", 0xFFE070, Holo.bar(new Vec3(0, 4, 0), new Vec3(0, 4.01, 0), 0.01, 0.01, 0), 140, 0),
                    Holo.bar(new Vec3(0, 4, 0), new Vec3(0, 30, 0), 1.6, 1.6, 0.78), 3);
            for (int k = 0; k < 2; k++) {        // 창날 (늘린 마름모 2겹)
                double[][] r = Holo.mul(Holo.rotY(k * Math.PI / 4), Holo.rotZ(0));
                double[][] dia = Holo.mul(r, Holo.mul(Holo.rotX(0), Holo.rotZ(Math.PI / 4)));
                add(Holo.block(l, g.tag, top, k == 0 ? "minecraft:white_concrete" : "minecraft:yellow_stained_glass", 0xFFE070, Holo.m(dia, 0.01, 0.01, 0.01, new Vec3(0, 2.5, 0), true), 140, 0),
                        Holo.m(Holo.mul(Holo.rotY(k * Math.PI / 4), Holo.mul(Holo.rotZ(Math.PI / 4), Holo.I())), 2.4 - k * 0.6, 2.4 - k * 0.6, 0.5 + k * 0.6, new Vec3(0, 2.6, 0), true), 4 + k);
            }
            add(Holo.block(l, g.tag, top, "minecraft:gold_block", -1, Holo.bar(new Vec3(-0.01, 5, 0), new Vec3(0.01, 5, 0), 0.01, 0.01, 0), 140, 0),
                    Holo.bar(new Vec3(-3.2, 5.2, 0), new Vec3(3.2, 5.2, 0), 0.5, 0.5, 0), 5);
            for (int s = -1; s <= 1; s += 2)   // 날개 (초승달 텍스처)
                add(Holo.sprite(l, g.tag, top, "slash", 0xFFE070, "fixed", Holo.m(Holo.rotZ(s * 0.9), 0.01, 0.01, 1, new Vec3(s * 2.5, 7, 0), false), 140, 0),
                        Holo.m(Holo.rotZ(s > 0 ? -0.6 : Math.PI + 0.6), 8, 8, 1, new Vec3(s * 2.6, 8, 0), false), 6);
            for (int k = 0; k < 2; k++)        // 자루를 감싼 빛 (교차)
                add(Holo.sprite(l, g.tag, top, "beam", 0xFFE070, "fixed", Holo.m(Holo.rotY(k * Math.PI / 2), 0.01, 26, 1, new Vec3(0, 17, 0), false), 140, 0),
                        Holo.m(Holo.rotY(k * Math.PI / 2), 6, 28, 1, new Vec3(0, 17, 0), false), 3);
            Holo.circle(l, g.tag, top.add(0, 32, 0), 14, 0xFFE070, 46, 0);
            Holo.circle(l, g.tag, to, 11, 0xFFE070, 60, 0);
            Holo.screen(l, to, 70, 5, 0xFFD040, 3000, 0.55f);
            Holo.sfx(l, top, "magic", 4f, 0.7f);
            Holo.sfx(l, to, "deep", 3f, 1.3f);
        }
        void add(Holo.Elem e, float[] full, int at0) { e.key(at0, full, 8); parts.add(e); up.add(full); }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t >= 10 && t < 40) {      // 충전: 창을 감싸며 도는 고리, 사방에서 모이는 빛
                if (t % 6 == 0) Holo.ringT(l, g.tag, top.add(0, 6 + (t % 18), 0), 0.5, 5 + (t % 12) * 0.3, 20, 0.35, 0.08, 0xFFE070, 5, 2, 4, Holo.I(), 12, true);
                for (int k = 0; k < 2; k++) {
                    double a = l.random.nextDouble() * Math.PI * 2;
                    Vec3 o = top.add(Math.cos(a) * 16, 10 + l.random.nextDouble() * 20, Math.sin(a) * 16);
                    Holo.beamLayer(l, g.tag, o, top.add(0, 6, 0), 0.25, "minecraft:yellow_stained_glass", 0xFFE070, 2, 1, 3, k);
                }
                if (t == 12) Holo.sfx(l, top, "charge", 4f, 0.7f);
                if (t == 30) { Holo.screen(l, at, 70, 7, 0, 600, 0.15f); Holo.screen(l, at, 70, 6, 0xFFE070, 600, 1); }
            }
            if (t == 40) {               // 내리꽂기 (4틱)
                for (int i = 0; i < parts.size(); i++) {
                    float[] m = up.get(i).clone();
                    m[7] -= (float) (H - 1.5);
                    parts.get(i).keyIn(1, m, 4);
                }
                Holo.sfx(l, top, "whoosh", 5f, 0.4f);
                Holo.beamGlow(l, g.tag, top.add(0, 30, 0), at, 7, 0xFFFFFF, 1, 2, 5);
            }
            if (t == 45) {
                Holo.explosion(l, g.tag, at, 10, 0xFFD040);
                Holo.pillar(l, g.tag, at, 140, 12, 0xFFE070, 2, 14, 10);
                Holo.slowmo(l, 7, 12);
                g.flash(at, 90, "w");
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(18, 10, 18), x -> x instanceof Enemy && x.isAlive())) {
                    double d = Math.hypot(e.getX() - at.x, e.getZ() - at.z);
                    if (d > 18) continue;
                    g.hit(e, p, d < 10 ? 260f : 100f);
                    e.setDeltaMovement(new Vec3(e.getX() - at.x, 0, e.getZ() - at.z).normalize().scale(1.8).add(0, 1.6, 0)); e.hurtMarked = true;
                }
                if (MechManager.griefOn(l.getServer())) Titan.carve(l, at, 6, l.random);
                Holo.sfx(l, at, "thunder", 5f, 0.6f);
            }
            if (t == 50 || t == 58 || t == 68) {     // 여진
                int k = (t - 50) / 8;
                Holo.shockTex(l, g.tag, at, 20 + k * 12, k % 2 == 0 ? 0xFFE070 : 0xFFFFFF, 12);
                Holo.dustRing(l, g.tag, at, 18 + k * 10, 18, 24);
                Holo.screen(l, at, 70, 2, 0, 400, 3f);
                Holo.sfx(l, at, "impact", 3f, 0.6f - k * 0.05f);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(22 + k * 10, 6, 22 + k * 10), x -> x instanceof Enemy && x.isAlive())) g.hit(e, p, 25f);
            }
            if (t > 45 && t < 110 && t % 4 == 0) {
                Vec3 q = at.add(0, 6 + l.random.nextDouble() * 20, 0);
                l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 4, 0.8, 4, 0.8, 0.02);
            }
            if (t == 110) for (Holo.Elem e : parts) e.keyIn(1, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, -H + 20, 0), true), 10).endIn(12);
            return t >= 124;
        }
    }

    /**
     * /function 연쇄 번개: 수호에서 시작한 번개가 적에서 적으로 최대 14번 튀며 점점 굵어짐 → 마지막 적에게 하늘의 낙뢰 3줄기.
     */
    static final class ChainLightning implements Fx {
        int t = 0, hops = 0; Vec3 from; LivingEntity cur; final Set<UUID> hitSet = new HashSet<>(); final Vec3 at;
        ChainLightning(Guard g, Vec3 to) {
            at = to; from = g.pos;
            g.say("/function lightning:chain {max:14}", 0x7FDFFF, null, 0);
            Holo.sfx(g.level, g.pos, "charge", 2f, 1.8f);
        }
        LivingEntity next(ServerLevel l, Vec3 c, double r) {
            LivingEntity best = null; double bd = r * r;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), x -> x instanceof Enemy && x.isAlive())) {
                if (hitSet.contains(e.getUUID())) continue;
                double d = e.distanceToSqr(c);
                if (d < bd) { bd = d; best = e; }
            }
            return best;
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t % 3 != 1) return false;
            LivingEntity n = next(l, hops == 0 ? at : from, hops == 0 ? 30 : 16);
            if (n == null || hops >= 14) {
                if (cur != null && cur.isAlive()) {        // 마무리 낙뢰
                    Vec3 c = cur.position();
                    for (int k = 0; k < 3; k++) Holo.lightning(l, g.tag, c.add((k - 1) * 8, 50, (k % 2) * 6 - 3), c.add(0, 0.5, 0), k == 1 ? 0xFFFFFF : 0x7FDFFF, 2.2, 10);
                    Holo.explosion(l, g.tag, c, 5, 0x7FDFFF);
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, cur.getBoundingBox().inflate(6), x -> x instanceof Enemy && x.isAlive())) g.hit(e, p, 120f);
                    Holo.banner(l, g.tag, c.add(0, cur.getBbHeight() + 2, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("CHAIN ×" + hops + " FINISH", 0x7FDFFF, true) + "]}", 1.6, 30);
                } else if (hops == 0) Holo.lightning(l, g.tag, at.add(0, 40, 0), at, 0x7FDFFF, 1.6, 8);
                return true;
            }
            hops++;
            hitSet.add(n.getUUID());
            Vec3 to = n.position().add(0, n.getBbHeight() * 0.6, 0);
            double w = 0.5 + hops * 0.12;
            Holo.lightning(l, g.tag, from, to, hops % 2 == 0 ? 0xFFFFFF : 0x7FDFFF, w, 6);
            Holo.beamGlow(l, g.tag, from, to, w * 6, 0x7FDFFF, 1, 1, 4);
            Holo.explosion(l, g.tag, n.position(), 1.3 + hops * 0.12, 0x7FDFFF);
            Holo.hitbox(l, g.tag, n, 0x7FDFFF, 10);
            Holo.banner(l, g.tag, to.add(0, n.getBbHeight() * 0.5 + 1, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("×" + hops, 0x7FDFFF, true) + "]}", 1.0 + hops * 0.05, 20);
            g.hit(n, p, 35f + hops * 6f);
            n.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 4));
            Holo.sfx(l, to, "thunder", 1.5f + hops * 0.1f, 1.4f - hops * 0.04f);
            from = to; cur = n;
            return false;
        }
    }

    /**
     * /data 칼날 폭풍: 주인을 중심으로 빛의 초승달 칼날 3겹(반지름 3·6·9)이 서로 다른 속도로 돌며 6초간 주변을 갈아버림.
     * 끝나면 칼날이 사방으로 튕겨 나가며 폭발.
     */
    static final class BladeStorm implements Fx {
        int t = 0; final List<Holo.Elem> blades = new ArrayList<>(); final List<double[]> info = new ArrayList<>();
        static final int LIFE = 120;
        BladeStorm(Guard g, ServerPlayer p) {
            ServerLevel l = g.level;
            g.say("/data modify entity @s blades set value 36b", 0x55FFFF, null, 0);
            final ServerPlayer own = p;
            double[][] rings = {{3.2, 8, 14, 1.2}, {6.2, 12, -10, 2.0}, {9.2, 16, 7, 1.0}};
            for (double[] rg : rings) {
                int n = (int) rg[1];
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    double[][] R = Holo.mul(Holo.rotY(-a - (rg[2] > 0 ? 0 : Math.PI)), Holo.mul(Holo.rotX(-Math.PI / 2 + 0.25), Holo.rotZ(-0.6)));
                    Vec3 c = new Vec3(Math.cos(a) * rg[0], rg[3], Math.sin(a) * rg[0]);
                    double s = 2.4 + rg[0] * 0.25;
                    Holo.Elem e = Holo.sprite(l, g.tag, p.position(), "slash", i % 3 == 0 ? 0xFFFFFF : 0x55FFFF, "fixed", Holo.m(R, 0.01, 0.01, 1, c.scale(0.2), false), LIFE + 20, 1);
                    e.follow(own::position, Vec3.ZERO).spin(rg[2]).key(1 + i % 6, Holo.m(R, s, s, 1, c, false), 6);
                    blades.add(e); info.add(new double[]{a, rg[0], rg[3], s});
                }
            }
            Holo.circle(l, g.tag, p.position(), 10, 0x55FFFF, LIFE, 0);
            Holo.sfx(l, p.position(), "slash", 2f, 0.6f);
            Holo.screen(l, p.position(), 30, 6, 0x55FFFF, 600, 1);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            Vec3 c = p.position();
            if (t <= LIFE) {
                if (t % 5 == 0) {
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10.5, 4, 10.5), x -> x instanceof Enemy && x.isAlive())) {
                        double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                        if (d > 10.5) continue;
                        g.hit(e, p, 16f);
                        Vec3 tan = new Vec3(-(e.getZ() - c.z), 0, e.getX() - c.x).normalize().scale(0.5);
                        e.setDeltaMovement(e.getDeltaMovement().add(tan).add(new Vec3(c.x - e.getX(), 0, c.z - e.getZ()).normalize().scale(0.15)).add(0, 0.12, 0)); e.hurtMarked = true;
                        if (l.random.nextInt(2) == 0) Holo.slash(l, g.tag, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.2 + e.getBbWidth(),
                                Holo.facing(new Vec3(l.random.nextDouble() - 0.5, 0.4, l.random.nextDouble() - 0.5)), 0x55FFFF, 4);
                    }
                    Holo.sfx(l, c, "slash", 1.2f, 1.2f + l.random.nextFloat() * 0.4f);
                }
                if (t % 3 == 0) {
                    double a = t * 0.7;
                    l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * 6, c.y + 1.2, c.z + Math.sin(a) * 6, 1, 0, 0, 0, 0);
                    l.sendParticles(dust(0x55FFFF, 1.5f), c.x, c.y + 1, c.z, 10, 5, 1, 5, 0);
                }
                if (t % 10 == 0) { Holo.dustRing(l, g.tag, c, 9, 10, 16); Holo.sfx(l, c, "whoosh", 1.4f, 0.8f); }
            }
            if (t == LIFE) {              // 칼날이 사방으로 튕겨 나감
                for (int i = 0; i < blades.size(); i++) {
                    double[] in = info.get(i);
                    double[][] R = Holo.mul(Holo.rotY(-in[0]), Holo.mul(Holo.rotX(-Math.PI / 2 + 0.25), Holo.rotZ(-0.6)));
                    Vec3 out = new Vec3(Math.cos(in[0]) * in[1] * 3.2, in[2] + 1.5, Math.sin(in[0]) * in[1] * 3.2);
                    blades.get(i).keyIn(1, Holo.m(R, in[3] * 1.4, in[3] * 1.4, 1, out, false), 8).keyIn(10, Holo.m(R, 0.01, 0.01, 1, out.scale(1.1), false), 4).endIn(15);
                }
                Holo.explosion(l, g.tag, c, 4, 0x55FFFF);
                Holo.shockTex(l, g.tag, c, 30, 0x55FFFF, 14);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24, 6, 24), x -> x instanceof Enemy && x.isAlive())) {
                    g.hit(e, p, 60f);
                    e.setDeltaMovement(new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).normalize().scale(1.5).add(0, 0.8, 0)); e.hurtMarked = true;
                }
            }
            return t >= LIFE + 18;
        }
    }

    static final class Pending { int t; final Runnable r; Pending(int t, Runnable r) { this.t = t; this.r = r; } }

    static final class Floater { final String id; Vec3 at; int life; Floater(String id, Vec3 at, int life) { this.id = id; this.at = at; this.life = life; } }

    /** 폭탄 (TNT 모양, 포물선). */
    static final class Bolt {
        static final int BOMB = 0;
        static int n = 0;
        final String id = "b" + (n++);
        Vec3 pos, vel; int delay, life; boolean spawned;
        Bolt(int type, Vec3 from, Vec3 to, int delay) {
            this.pos = from; this.delay = delay;
            double T = 22, g = 0.06;
            this.vel = new Vec3((to.x - from.x) / T, (to.y - from.y + 0.5 * g * T * T) / T, (to.z - from.z) / T);
        }
        boolean tick(Guard gd, ServerPlayer p) {
            if (delay > 0) { delay--; return false; }
            if (!spawned) { spawned = true; gd.display(id, pos, "minecraft:tnt", true); }
            life++;
            vel = vel.add(0, -0.06, 0);
            Vec3 next = pos.add(vel);
            BlockHitResult hit = gd.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean near = !gd.level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(1.2), e -> e instanceof Enemy).isEmpty();
            if (hit.getType() != HitResult.Type.MISS || near || life > 80) {
                Vec3 at = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : next;
                gd.kill(id);
                boolean grief = MechManager.griefOn(gd.level.getServer());
                gd.level.explode(p, at.x, at.y, at.z, 4.5f, false, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                gd.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                gd.level.sendParticles(PURPLE, at.x, at.y, at.z, 30, 1.5, 1, 1.5, 0);
                gd.level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 10, 1, 0.5, 1, 0);
                gd.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, 1.5, 1, 1.5, 0.2);
                gd.wave(at.add(0, 0.2, 0), 7, 10, 0xFF6633);
                gd.burst(at.add(0, 1, 0), 0.7, 40, 0xFF5533);
                Holo.explosion(gd.level, gd.tag, at, 2.2, 0xFF7020);
                gd.pillar(at, 14, 8, 1.6, "minecraft:orange_stained_glass", 16737843);
                gd.level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3f, 0.6f);
                return true;
            }
            pos = next;
            gd.move(id, pos, mat(life * 0.4, life * 0.25, Vec3.ZERO, 2.2, 2.2, 2.2));
            gd.level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.3, 0.3, 0.3, 0.02);
            for (int k = 0; k < 2; k++) { double an = life * 0.8 + k * Math.PI;
                gd.level.sendParticles(PURPLE, pos.x + Math.cos(an) * 1.2, pos.y + Math.sin(an) * 1.2, pos.z, 1, 0, 0, 0, 0); }
            gd.level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.01);
            gd.level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 3, 0.2, 0.2, 0.2, 0.5);
            return false;
        }
    }

    /**
     * 커맨드 골렘 — 기사형 블록 디스플레이 모델 (실제 몸은 무한 투명 철 골렘).
     *  골반·몸통·가슴판·보랏빛 코어·금 벨트·견갑·투구(시안 바이저·볏) / 왼팔 방패 / 오른팔 빛나는 대검 / 다리·장화
     *  부품은 수호에서 날아와 제자리에 맞물리며 조립 → 걷기(다리·팔 교차), 대검 내려찍기(들어올림→강타→복귀) 키프레임 애니메이션.
     */
    static final class Golem {
        record Part(String block, int glow, double sx, double sy, double sz, Vec3 c, int pivot, double[][] extra) {}
        static final Vec3[] PIV = {Vec3.ZERO, new Vec3(-1.4, 3.6, 0), new Vec3(1.4, 3.6, 0), new Vec3(-0.55, 2.0, 0), new Vec3(0.55, 2.0, 0)};
        static final double[][] I = Holo.I();
        static final Part[] PARTS = {
                new Part("minecraft:polished_blackstone", -1, 1.7, 0.45, 1.0, new Vec3(0, 2.15, 0), 0, I),
                new Part("minecraft:gold_block", -1, 1.75, 0.14, 1.05, new Vec3(0, 2.42, 0), 0, I),
                new Part("minecraft:command_block", -1, 2.1, 1.5, 1.25, new Vec3(0, 3.05, 0), 0, I),
                new Part("minecraft:chain_command_block", -1, 1.6, 0.9, 0.25, new Vec3(0, 3.2, 0.68), 0, I),
                new Part("minecraft:purple_stained_glass", 0xB45CFF, 0.5, 0.5, 0.14, new Vec3(0, 3.25, 0.84), 0, Holo.rotZ(Math.PI / 4)),
                new Part("minecraft:repeating_command_block", -1, 0.95, 0.6, 1.15, new Vec3(-1.5, 3.92, 0), 0, Holo.rotZ(0.3)),
                new Part("minecraft:repeating_command_block", -1, 0.95, 0.6, 1.15, new Vec3(1.5, 3.92, 0), 0, Holo.rotZ(-0.3)),
                new Part("minecraft:command_block", -1, 0.95, 0.95, 0.95, new Vec3(0, 4.27, 0.02), 0, I),
                new Part("minecraft:white_concrete", 0x55FFFF, 0.78, 0.13, 0.06, new Vec3(0, 4.33, 0.52), 0, I),
                new Part("minecraft:chain_command_block", -1, 0.16, 0.5, 0.8, new Vec3(0, 4.92, -0.05), 0, I),
                // 왼팔 + 방패
                new Part("minecraft:chain_command_block", -1, 0.6, 1.6, 0.6, new Vec3(0, -0.8, 0), 1, I),
                new Part("minecraft:command_block", -1, 0.82, 0.75, 0.82, new Vec3(0, -1.85, 0), 1, I),
                new Part("minecraft:purple_stained_glass", 0xB45CFF, 0.12, 1.55, 1.25, new Vec3(-0.5, -1.45, 0.1), 1, I),
                // 오른팔 + 대검
                new Part("minecraft:chain_command_block", -1, 0.6, 1.6, 0.6, new Vec3(0, -0.8, 0), 2, I),
                new Part("minecraft:command_block", -1, 0.82, 0.75, 0.82, new Vec3(0, -1.85, 0), 2, I),
                new Part("minecraft:gold_block", -1, 1.0, 0.16, 0.2, new Vec3(0, -2.35, 0), 2, I),
                new Part("minecraft:light_blue_stained_glass", 0x55FFFF, 0.28, 3.4, 0.1, new Vec3(0, -4.15, 0), 2, I),
                new Part("minecraft:white_concrete", -1, 0.08, 3.3, 0.06, new Vec3(0, -4.15, 0), 2, I),
                // 다리
                new Part("minecraft:repeating_command_block", -1, 0.75, 1.7, 0.8, new Vec3(0, -0.85, 0), 3, I),
                new Part("minecraft:polished_blackstone", -1, 0.95, 0.35, 1.15, new Vec3(0, -1.83, 0.1), 3, I),
                new Part("minecraft:repeating_command_block", -1, 0.75, 1.7, 0.8, new Vec3(0, -0.85, 0), 4, I),
                new Part("minecraft:polished_blackstone", -1, 0.95, 0.35, 1.15, new Vec3(0, -1.83, 0.1), 4, I),
                // 에너지 라인 (발광 테두리)
                new Part("minecraft:white_concrete", 0x55FFFF, 0.07, 1.2, 0.02, new Vec3(0, 2.95, 0.64), 0, I),
                new Part("minecraft:white_concrete", 0xB45CFF, 1.62, 0.05, 0.03, new Vec3(0, 3.66, 0.64), 0, I),
                new Part("minecraft:white_concrete", 0x55FFFF, 0.06, 1.1, 0.02, new Vec3(0, -0.85, 0.41), 3, I),
                new Part("minecraft:white_concrete", 0x55FFFF, 0.06, 1.1, 0.02, new Vec3(0, -0.85, 0.41), 4, I),
                // 발광 텍스처: 눈빛·코어·등 후광·방패 문장·대검 빛
                new Part("fx:orb", 0x55FFFF, 1.3, 0.5, 1, new Vec3(0, 4.33, 0.58), 0, I),
                new Part("fx:orb", 0xB45CFF, 1.6, 1.6, 1, new Vec3(0, 3.25, 0.94), 0, I),
                new Part("fx:runering", 0xB45CFF, 3.6, 3.6, 1, new Vec3(0, 3.7, -0.9), 0, I),
                new Part("fx:circle", 0xB45CFF, 1.3, 1.3, 1, new Vec3(-0.58, -1.45, 0.1), 1, Holo.rotY(-Math.PI / 2)),
                new Part("fx:beam", 0x55FFFF, 1.1, 3.9, 1, new Vec3(0, -4.15, 0), 2, I),
                new Part("fx:beam", 0x55FFFF, 1.1, 3.9, 1, new Vec3(0, -4.15, 0), 2, Holo.rotY(Math.PI / 2))};
        static final double[] REST = {-0.2, -1.2, 0, 0};

        final Vec3 spot;
        final Holo.Elem[] el = new Holo.Elem[PARTS.length];
        int t = 0, life = 1200, swingT = 0, lastAtk = 0, walkPhase = 0, swings = 0, swingType = 0;
        double spinOff = 0;
        Holo.Elem hpBar;
        boolean walking = false;
        double bob = 0;
        IronGolem body;

        static boolean fx(Part p) { return p.block.startsWith("fx:"); }
        static float[] mat(Part p, double[] pose) {
            if (p.pivot == 0) return Holo.m(p.extra, p.sx, p.sy, p.sz, p.c, !fx(p));
            double[][] rx = Holo.rotX(pose[p.pivot - 1]);
            return Holo.m(Holo.mul(rx, p.extra), p.sx, p.sy, p.sz, PIV[p.pivot].add(Holo.apply(rx, p.c)), !fx(p));
        }
        static Vec3 worldC(Part p) { return p.pivot == 0 ? p.c : PIV[p.pivot].add(p.c); }

        Golem(Guard g, Vec3 spot) {
            this.spot = spot;
            ServerLevel l = g.level;
            Vec3 from = g.pos.subtract(spot);
            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < PARTS.length; i++) {      // 수호에서 부품이 날아와 맞물림
                Part pt = PARTS[i];
                double[][] spinR = Holo.mul(Holo.rotX(rnd.nextDouble() * 6), Holo.rotY(rnd.nextDouble() * 6));
                Vec3 scatter = from.add((rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 3);
                boolean f = fx(pt);
                el[i] = f ? Holo.sprite(l, g.tag, spot, pt.block.substring(3), pt.glow, "fixed", Holo.m(spinR, 0.01, 0.01, 0.01, scatter, false), 100_000_000, 1)
                        : Holo.block(l, g.tag, spot, pt.block, pt.glow, Holo.m(spinR, pt.sx * 0.3, pt.sy * 0.3, pt.sz * 0.3, scatter, true), 100_000_000, 1);
                Vec3 mid = worldC(pt).add(0, 2.5 + rnd.nextDouble() * 2, 0).add(Holo.apply(Holo.rotY(i), new Vec3(2.5, 0, 0)));
                if (!f) el[i].key(1 + i / 2, Holo.m(Holo.mul(spinR, Holo.rotZ(1)), pt.sx * 0.7, pt.sy * 0.7, pt.sz * 0.7, mid, true), 8);
                el[i].key(f ? 33 : Math.min(32, 10 + i), mat(pt, REST), f ? 6 : 6);
            }
            Holo.circle(l, g.tag, spot, 3.6, 0xB45CFF, 40, 0);
            Holo.beam(l, g.tag, g.pos, spot.add(0, 3, 0), 0.8, 0xB45CFF, 2, 20, 6);
            l.playSound(null, g.pos.x, g.pos.y, g.pos.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2f, 1.2f);
        }

        void pose(double[] p, int dur) {
            for (int i = 0; i < PARTS.length; i++) if (PARTS[i].pivot != 0) el[i].keyIn(1, mat(PARTS[i], p), dur);
        }

        /** true = 끝 */
        boolean tick(Guard g) {
            t++;
            ServerLevel level = g.level;
            if (t < 34) {
                if (t % 3 == 0) l(level).sendParticles(ParticleTypes.ENCHANT, spot.x, spot.y + 2.5, spot.z, 10, 1, 1.5, 1, 0.8);
                if (t % 4 == 0) level.playSound(null, spot.x, spot.y + 2, spot.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.2f + t * 0.02f);
                return false;
            }
            if (t == 34) {     // 조립 완료 → 몸 생성, 부품을 몸에 부착
                body = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (body == null) { for (Holo.Elem e : el) e.kill(); return true; }
                body.snapTo(spot.x, spot.y, spot.z, 0, 0);
                body.setYBodyRot(0); body.setYHeadRot(0);
                body.setPlayerCreated(true);
                body.setInvisible(true);
                body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, false, false, false));
                body.addTag("jjk.cmdgolem");
                body.setCustomName(Component.literal("§d커맨드 골렘"));
                body.setCustomNameVisible(false);
                var atk = body.getAttribute(Attributes.ATTACK_DAMAGE); if (atk != null) atk.setBaseValue(45);
                var hp = body.getAttribute(Attributes.MAX_HEALTH); if (hp != null) hp.setBaseValue(400);
                var sp = body.getAttribute(Attributes.MOVEMENT_SPEED); if (sp != null) sp.setBaseValue(0.45);
                var sc = body.getAttribute(Attributes.SCALE); if (sc != null) sc.setBaseValue(1.6);
                var kb = body.getAttribute(Attributes.KNOCKBACK_RESISTANCE); if (kb != null) kb.setBaseValue(1.0);
                body.setHealth(400);
                level.addFreshEntity(body);
                final IronGolem bd = body;
                for (Holo.Elem e : el) e.attach(() -> bd.position().add(0, bob, 0), () -> bd.yBodyRot + spinOff);
                hpBar = Holo.text(level, g.tag, spot.add(0, 5.8, 0), "{text:\"\"}", "center", 0x90000000, false, Holo.m(Holo.I(), 0.6, 0.6, 0.6, Vec3.ZERO, false), 100_000_000, 1);
                hpBar.follow(() -> bd.position(), new Vec3(0, 5.8, 0)).text(a -> {
                    float r = Math.max(0, bd.getHealth() / Math.max(1, bd.getMaxHealth()));
                    int n = 16, f = Math.max(r > 0 ? 1 : 0, Math.round(r * n));
                    int left = Math.max(0, (life) / 20);
                    return "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⚔ COMMAND GOLEM ", 0xB45CFF, true) + "," + Holo.tx(left + "s", 0x888888, false)
                            + ",{text:\"\\n\"}," + Holo.tx("█".repeat(f), 0x55FFFF, false) + "," + Holo.tx("█".repeat(n - f), 0x203040, false) + "]}";
                });
                Holo.lightning(level, g.tag, spot.add(3, 40, -2), spot, 0xB45CFF, 1.5, 8);
                Holo.explosion(level, g.tag, spot, 2.5, 0xB45CFF);
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 2f, 0.8f);
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5f, 0.8f);
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 0.6f);
                Holo.pillar(level, g.tag, spot, 40, 3, 0xB45CFF, 2, 10, 6);
                Holo.shock(level, g.tag, spot, 10, 0xB45CFF, 10);
                Holo.orb(level, g.tag, spot.add(0, 3.25, 0), 1.6, 0xB45CFF, 2, 2, 5);
                g.burst(spot.add(0, 3, 0), 1.0, 100, 0xB45CFF);
                Holo.banner(level, g.tag, spot.add(0, 5.6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("COMMAND GOLEM ", 0xB45CFF, true) + "," + Holo.tx("ONLINE", 0x55FF55, true) + "]}", 1.1, 34);
                return false;
            }
            if (body == null || !body.isAlive() || --life <= 0) { discard(g); return true; }
            if (!body.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY))
                body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, false, false, false));
            Vec3 b = body.position();
            double speed = body.getDeltaMovement().horizontalDistance();
            boolean nowWalk = speed > 0.03;
            // 공격 감지 → 대검 내려찍기
            int atkT = body.getAttackAnimationTick();
            if (atkT >= 9 && lastAtk < 9 && swingT == 0) { swingT = 1; swings++; swingType = swings % 3 == 0 ? 1 : 0; }
            lastAtk = atkT;
            if (swingT > 0 && swingType == 1) {        // 회전베기: 대검을 수평으로 들고 한 바퀴
                swingT++;
                if (swingT == 2) { pose(new double[]{-1.3, -1.57, -0.2, 0.2}, 3); Holo.sfx(level, b, "charge", 1f, 2f); }
                if (swingT >= 5 && swingT <= 12) spinOff += 45;
                if (swingT == 6 || swingT == 10) {
                    Holo.decal(level, g.tag, b.add(0, 2.0, 0), "slash", 0x55FFFF, 3, 12.5, 3, 2, 4, 30, Holo.I());
                    Holo.shockTex(level, g.tag, b, 7, 0x55FFFF, 7);
                    Holo.sparks(level, g.tag, b.add(0, 2, 0), 14, 6, 0x55FFFF, 10);
                    Holo.sfx(level, b, "slash", 1.6f, 0.8f + swingT * 0.03f);
                    ServerPlayer owner = g.p();
                    for (LivingEntity e : g.foesNear(b, 6)) {
                        if (owner != null) g.hit(e, owner, 30f);
                        e.setDeltaMovement(e.position().subtract(b).normalize().scale(1.6).add(0, 0.6, 0)); e.hurtMarked = true;
                    }
                    Holo.screen(level, b, 24, 2, 0, 350, 2.5f);
                }
                if (swingT == 13) { spinOff = 0; pose(REST, 6); }
                if (swingT == 16) { swingT = 0; walking = false; }
            } else if (swingT > 0) {
                swingT++;
                if (swingT == 2) {
                    pose(new double[]{-0.9, -3.0, -0.3, 0.3}, 3);
                    level.playSound(null, b.x, b.y + 4, b.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.5f);
                    Vec3 tip = b.add(Holo.apply(Holo.rotY(-Math.toRadians(body.yBodyRot)), new Vec3(1.4, 9.2, 0.8)));
                    Holo.flare(level, g.tag, tip, 4, 0x55FFFF, 10);
                    Holo.glow(level, g.tag, tip, 1.2, 0x55FFFF, 3, 3, 4);
                }
                if (swingT == 6) pose(new double[]{-0.4, -0.75, 0.35, -0.35}, 2);
                if (swingT == 8) swingImpact(g, level, b);
                if (swingT == 14) { pose(REST, 6); swingT = 0; walking = false; }
            } else if (nowWalk) {
                if (!walking || t % 7 == 0) {
                    walking = true;
                    walkPhase ^= 1;
                    double s = walkPhase == 0 ? 1 : -1;
                    pose(new double[]{-0.2 - 0.45 * s, -1.2 + 0.15 * s, 0.5 * s, -0.5 * s}, 7);
                    if (walkPhase == 0) level.playSound(null, b.x, b.y, b.z, SoundEvents.IRON_GOLEM_STEP, SoundSource.NEUTRAL, 1.2f, 0.7f);
                    Vec3 foot = b.add(Holo.apply(Holo.rotY(-Math.toRadians(body.yBodyRot)), new Vec3(walkPhase == 0 ? 0.55 : -0.55, 0.05, 0.4)));
                    Holo.dustRing(level, g.tag, foot, 2.2, 5, 14);
                    Holo.shockTex(level, g.tag, foot, 1.4, 0xB45CFF, 5);
                }
            } else if (walking) { walking = false; pose(REST, 6); }
            bob = walking ? Math.abs(Math.sin(t * Math.PI / 7)) * 0.12 : Math.sin(t * 0.08) * 0.04;
            if (t % 3 == 0) {
                Vec3 core = b.add(Holo.apply(Holo.rotY(-Math.toRadians(body.yBodyRot)), new Vec3(0, 3.25, 0.95)));
                level.sendParticles(PURPLE, core.x, core.y, core.z, 1, 0.08, 0.08, 0.08, 0);
            }
            if (t % 6 == 0) level.sendParticles(ParticleTypes.ENCHANT, b.x, b.y + 3, b.z, 4, 0.8, 1.2, 0.8, 0.5);
            return false;
        }

        static ServerLevel l(ServerLevel x) { return x; }

        /** 대검이 땅을 때린 순간: 칼날 궤적·충격파·피해. */
        void swingImpact(Guard g, ServerLevel level, Vec3 b) {
            double yawR = Math.toRadians(body.yBodyRot);
            Vec3 fw = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR));
            Vec3 f = b.add(fw.scale(3.6)).add(0, 0.2, 0);
            Holo.slash(level, g.tag, b.add(0, 3.4, 0).add(fw.scale(1.2)), 3.6, Holo.mul(Holo.facing(fw.cross(new Vec3(0, 1, 0))), Holo.rotY(Math.PI / 2)), 0x55FFFF, 6);
            Holo.explosion(level, g.tag, f, 2.6, 0xB45CFF);
            Holo.sfx(level, f, "slash", 1.4f, 0.9f);
            Holo.shock(level, g.tag, f, 7, 0xB45CFF, 8);
            Holo.shock(level, g.tag, f, 4, 0x55FFFF, 5);
            Holo.shards(level, g.tag, f, groundName(level, f.add(0, -0.5, 0)), 10, 3.5);
            Holo.light(level, f.add(0, 1, 0), 8);
            g.burst(f.add(0, 1, 0), 0.6, 30, 0x55FFFF);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, f.x, f.y + 1, f.z, 4, 1.2, 0.3, 1.2, 0);
            level.playSound(null, f.x, f.y, f.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.8f, 0.8f);
            level.playSound(null, f.x, f.y, f.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5f, 0.6f);
            g.cmd(String.format(Locale.ROOT, "tag @a[x=%.1f,y=%.1f,z=%.1f,distance=..16] add gojo.fxshake_s", f.x, f.y, f.z));
            ServerPlayer owner = g.p();
            for (LivingEntity e : g.foesNear(f, 5)) {
                if (owner != null) g.hit(e, owner, 25f);
                e.setDeltaMovement(e.position().subtract(b).normalize().scale(1.4).add(0, 0.7, 0)); e.hurtMarked = true;
            }
        }

        void discard(Guard g) {
            Vec3 at = body != null ? body.position() : spot;
            final Vec3 fixed = at;
            final double fyaw = body != null ? body.yBodyRot : 0;
            if (body != null && body.isAlive()) body.discard();
            java.util.Random rnd = new java.util.Random();
            for (Holo.Elem e : el) {
                if (e == null || e.dead()) continue;
                Vec3 up = new Vec3((rnd.nextDouble() - 0.5) * 3, 4 + rnd.nextDouble() * 3, (rnd.nextDouble() - 0.5) * 3);
                e.attach(() -> fixed, () -> fyaw);
                e.keyIn(1, Holo.m(Holo.mul(Holo.rotX(rnd.nextDouble() * 5), Holo.rotZ(rnd.nextDouble() * 5)), 0.15, 0.15, 0.15, up, true), 12).endIn(14);
            }
            g.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 2.5, at.z, 150, 1, 2, 1, 1.5);
            if (hpBar != null) hpBar.kill();
            Holo.explosion(g.level, g.tag, at, 2, 0xB45CFF);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5f, 0.6f);
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
        }

        void discard() { if (body != null && body.isAlive()) body.discard(); }
    }

    // ═════════════════════════════════════════ 신규 명령 (v0.22)

    /**
     * /particle 성운 붕괴: 목표 상공에 3갈래 나선 은하가 펼쳐져 회전하며 적을 빨아들이고,
     * 팔이 중심으로 감겨들어 백색 핵이 된 뒤 초신성 폭발.
     */
    static final class Nebula implements Fx {
        int t = 0; final Vec3 c; final List<Holo.Elem> stars = new ArrayList<>();
        static final int COLLAPSE = 70, NOVA = 92;
        static final int[] COLS = {0xB45CFF, 0x55FFFF, 0xFF55C8, 0xFFFFFF};
        Nebula(Guard g, Vec3 to) {
            ServerLevel l = g.level;
            c = to.add(0, 9, 0);
            g.say("/particle minecraft:nebula ~ ~9 ~ 12 0 12 0 4096 force", 0xFF55C8, null, 0);
            java.util.Random r = new java.util.Random(to.hashCode());
            for (int arm = 0; arm < 3; arm++) {
                for (int i = 0; i < 18; i++) {
                    double rad = 1.2 + i * 0.75, a = arm * Math.PI * 2 / 3 + rad * 0.42 + r.nextGaussian() * 0.12;
                    Vec3 off = new Vec3(Math.cos(a) * rad, r.nextGaussian() * 0.25, Math.sin(a) * rad);
                    String tex = i % 3 == 0 ? "flare" : i % 3 == 1 ? "orb" : "smoke" + (i % 2);
                    double sz = (tex.startsWith("smoke") ? 3.2 : 1.6) * (1.1 - i / 26.0);
                    int col = COLS[(arm + i) % COLS.length];
                    double[][] Rf = Holo.mul(Holo.FLAT, Holo.rotZ(r.nextDouble() * 6));
                    Holo.Elem e = Holo.sprite(l, g.tag, c, tex, col, "fixed", Holo.m(Rf, 0.01, 0.01, 0.01, Vec3.ZERO, false), NOVA + 4, 1);
                    e.spin(7 - i * 0.18).key(1 + i / 2, Holo.m(Rf, sz, sz, sz, off, false), 10)
                            .key(COLLAPSE - 2 + i / 3, Holo.m(Rf, sz * 0.3, sz * 0.3, sz * 0.3, off.scale(0.08), false), 14 + i / 2);
                    stars.add(e);
                }
            }
            Holo.decal(l, g.tag, c, "runering", 0xB45CFF, 1, 26, 12, COLLAPSE - 12, 10, 3, Holo.I());
            Holo.decal(l, g.tag, c.add(0, -0.1, 0), "circle", 0x55FFFF, 1, 18, 14, COLLAPSE - 14, 10, -4, Holo.I());
            Holo.glow(l, g.tag, c, 2.4, 0xB45CFF, 10, COLLAPSE - 10, 8);
            Holo.sfx(l, c, "choir", 2.5f, 0.8f);
            Holo.sfx(l, c, "riser", 2f, 0.6f);
            Holo.screen(l, c, 50, 5, 0x401060, 4200, 0.55f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t < COLLAPSE) {
                if (t % 2 == 0) for (LivingEntity e : g.foesNear(c, 18)) {      // 은하 중심으로 끌려감
                    Vec3 d = c.subtract(e.position()); double len = d.length();
                    if (len < 1.5) continue;
                    Vec3 tan = new Vec3(-d.z, 0, d.x).normalize().scale(0.25);
                    e.setDeltaMovement(d.normalize().scale(0.32).add(tan).add(0, 0.08, 0)); e.hurtMarked = true;
                    if (t % 10 == 0) g.hit(e, p, 8f);
                }
                if (t % 3 == 0) {
                    double a = t * 0.31;
                    for (int k = 0; k < 3; k++) {
                        double aa = a + k * Math.PI * 2 / 3, rr = 4 + (t % 9);
                        l.sendParticles(dust(COLS[k], 2.2f), c.x + Math.cos(aa) * rr, c.y, c.z + Math.sin(aa) * rr, 2, 0.3, 0.3, 0.3, 0);
                    }
                    l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 6, 0.6, 6, 0.02);
                    l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 30, 8, 1, 8, 0.6);
                }
                if (t % 20 == 0) Holo.sfx(l, c, "deep", 1.6f, 0.6f + t * 0.006f);
            }
            if (t == COLLAPSE) {
                g.say("/execute at @e[type=star] run tp @s ~ ~ ~ → core", 0xFFFFFF, null, 0);
                Holo.ringT(l, g.tag, c, 18, 0.5, 34, 0.5, 0.06, 0xFF55C8, 18, 0, 4, Holo.I(), 14, true);
                Holo.ringT(l, g.tag, c, 14, 0.4, 28, 0.4, 0.06, 0x55FFFF, 18, 0, 4, Holo.rotX(1.2), -12, true);
                Holo.sfx(l, c, "charge", 2.5f, 0.5f);
                Holo.screen(l, c, 60, 7, 0, 900, 0.18f);
            }
            if (t > COLLAPSE && t < NOVA) {
                if (t % 2 == 0) l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.2, 0.2, 0.2, 0.7);
                if (t == COLLAPSE + 6) Holo.orb(l, g.tag, c, 2.5, 0xFFFFFF, 14, 2, 2);
                if (t % 4 == 0) Holo.flare(l, g.tag, c, 3 + (t - COLLAPSE) * 0.3, 0xFFFFFF, 4);
            }
            if (t == NOVA) {
                g.say("/kill @e[distance=..16] · SUPERNOVA", 0xFFFFFF, null, 0);
                Holo.explosion(l, g.tag, c, 9, 0xFF55C8);
                Holo.orb(l, g.tag, c, 10, 0xFFFFFF, 3, 4, 14);
                Holo.shockTex(l, g.tag, c, 34, 0xFF55C8, 14);
                Holo.shockTex(l, g.tag, c, 24, 0x55FFFF, 10);
                Holo.ringT(l, g.tag, c, 1, 30, 40, 1.0, 0.1, 0xFFFFFF, 6, 2, 10, Holo.I(), 0, true);
                Holo.ringT(l, g.tag, c, 1, 24, 36, 0.8, 0.1, 0xB45CFF, 7, 2, 10, Holo.rotX(1.57), 0, true);
                Holo.ringT(l, g.tag, c, 1, 20, 32, 0.8, 0.1, 0x55FFFF, 8, 2, 10, Holo.rotZ(1.0), 0, true);
                Holo.pillar(l, g.tag, c.add(0, -9, 0), 120, 6, 0xFF55C8, 2, 10, 12);
                Holo.flare(l, g.tag, c, 28, 0xFFFFFF, 16);
                Holo.light(l, c, 30);
                Vec3 ground = c.add(0, -9, 0);
                Holo.decal(l, g.tag, ground.add(0, 0.07, 0), "crack", 0xFF55C8, 2, 16, 3, 50, 20, 0, Holo.I());
                Holo.shards(l, g.tag, ground, groundName(l, ground.add(0, -0.5, 0)), 24, 7);
                Holo.smoke(l, g.tag, ground, 16, 6, -1, 8, 50);
                for (LivingEntity e : g.foesNear(c, 18)) {
                    g.hit(e, p, 160f);
                    Vec3 d = e.position().subtract(c).normalize();
                    e.setDeltaMovement(d.scale(2.2).add(0, 0.9, 0)); e.hurtMarked = true;
                }
                g.burst(c, 2.2, 400, 0xFF55C8);
                g.flash(c, 70, "w");
                Titan.shake(l, c, 70, true);
                Holo.punch(l, c, 10, 0xFF55C8);
                Holo.sfx(l, c, "boom", 3f, 0.55f);
                Holo.sfx(l, c, "choir", 2.5f, 1.2f);
                Holo.sfx(l, c, "stinger", 2f, 0.9f);
                Holo.banner(l, g.tag, c.add(0, 4, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("✦ SUPERNOVA ✦", 0xFF55C8, true) + "]}", 3.0, 40);
            }
            return t >= NOVA + 6;
        }
    }

    /**
     * /execute 시간 역행: 거대한 시계가 바닥에 펼쳐지고, 시전 순간 적들의 위치를 기록.
     * 시곗바늘이 거꾸로 감기면 적들은 기록된 자리로 끌려가며 '시간의 빚'을 치르고, 시계는 산산이 부서짐.
     */
    static final class ChronoRewind implements Fx {
        int t = 0; final Vec3 c; final Map<LivingEntity, Vec3> mark = new HashMap<>();
        double handA = 0, handV = 0.05;
        static final double R = 13;
        ChronoRewind(Guard g, ServerPlayer p, Vec3 to) {
            ServerLevel l = g.level;
            c = to;
            g.say("/execute store result storage time:rewind pos run data get @e", 0x7FDFFF, null, 0);
            for (LivingEntity e : g.foesNear(c, R + 4)) {
                mark.put(e, e.position());
                Holo.hitbox(l, g.tag, e, 0x7FDFFF, 70);
                Holo.ghost(l, g.tag, e.position().add(0, e.getBbHeight() / 2, 0), "minecraft:light_blue_stained_glass", 0x7FDFFF, Math.max(1, e.getBbHeight()), Holo.I(), 80);
            }
            Holo.decal(l, g.tag, c.add(0, 0.06, 0), "circle", 0x7FDFFF, 1, R * 2.1, 10, 60, 10, -2, Holo.I());
            Holo.decal(l, g.tag, c.add(0, 0.08, 0), "runering", 0xFFFFFF, 1, R * 2.4, 12, 58, 10, 3, Holo.I());
            for (int i = 0; i < 12; i++) {           // 시각 눈금 (공중에 뜬 숫자)
                double a = i * Math.PI / 6;
                Holo.Elem n = Holo.text(l, g.tag, c.add(Math.sin(a) * R, 0.6, Math.cos(a) * R),
                        "{text:\"" + (i == 0 ? 12 : i) + "\",color:\"#AAF0FF\",bold:1b,font:\"minecraft:uniform\"}", "center", 0, false, Holo.m(Holo.I(), 0.01, 0.01, 0.01, Vec3.ZERO, false), 82, 1);
                n.key(2 + i, Holo.m(Holo.I(), 4, 4, 4, Vec3.ZERO, false), 6).key(70, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 3, 0), false), 8);
            }
            Holo.circle(l, g.tag, c, R, 0x7FDFFF, 78, 0);
            Holo.sfx(l, c, "magic", 2f, 0.6f);
            Holo.screen(l, c, 40, 5, 0x103050, 4000, 0.4f);
        }
        void hands(Guard g) {
            ServerLevel l = g.level;
            for (int k = 0; k < 2; k++) {
                double a = k == 0 ? handA : handA * 12, len = k == 0 ? R * 0.6 : R * 0.9;
                Vec3 tip = c.add(Math.sin(a) * len, 0.35, Math.cos(a) * len);
                Holo.beamLayer(l, g.tag, c.add(0, 0.35, 0), tip, k == 0 ? 0.6 : 0.35, k == 0 ? "minecraft:white_concrete" : "minecraft:light_blue_stained_glass", 0x7FDFFF, 0, 1, 1, 0);
            }
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t < 40) handV += 0.002; else if (t < 56) handV -= 0.03; else handV *= 0.9;    // 정방향 → 급격히 역회전
            handA += handV;
            if (t % 2 == 0 && t < 70) hands(g);
            if (t < 40) {
                for (LivingEntity e : mark.keySet()) if (e.isAlive()) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 10, 3));
                if (t % 10 == 0) Holo.sfx(l, c, "plate", 1f, 1.8f);
                if (t % 3 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 20, R * 0.6, 1, R * 0.6, 0.02);
            }
            if (t == 40) {
                g.say("/tick rewind 40", 0xFFFFFF, null, 0);
                Holo.sfx(l, c, "riser", 2f, 0.5f);
                Holo.sfx(l, c, "glitch", 2f, 0.6f);
                Holo.screen(l, c, 40, 4, 0, 1500, 0.8f);
                Holo.ringT(l, g.tag, c.add(0, 0.5, 0), R + 2, 1, 36, 0.4, 0.06, 0x7FDFFF, 14, 0, 4, Holo.I(), -14, true);
            }
            if (t > 40 && t < 60 && t % 4 == 0) Holo.sfx(l, c, "plate", 1.2f, 0.5f + (60 - t) * 0.03f);
            if (t == 58) {
                for (Map.Entry<LivingEntity, Vec3> en : mark.entrySet()) {
                    LivingEntity e = en.getKey(); if (!e.isAlive()) continue;
                    Vec3 from = e.position(), back = en.getValue();
                    Holo.beamGlow(l, g.tag, from.add(0, 1, 0), back.add(0, 1, 0), 1.6, 0x7FDFFF, 1, 2, 8);
                    for (int i = 0; i <= 4; i++) {
                        Vec3 q = from.lerp(back, i / 4.0);
                        Holo.ghost(l, g.tag, q.add(0, e.getBbHeight() / 2, 0), "minecraft:light_blue_stained_glass", 0x7FDFFF, Math.max(0.8, e.getBbHeight() * 0.8), Holo.rotY(i), 8 + i * 2);
                    }
                    e.teleportTo(back.x, back.y + 0.2, back.z);
                    e.setDeltaMovement(0, 0.8, 0); e.hurtMarked = true;
                    double debt = 70 + from.distanceTo(back) * 12;      // 시간의 빚: 멀리 움직였을수록 큰 피해
                    g.hit(e, p, (float) debt);
                    Holo.explosion(l, g.tag, back, 2.2, 0x7FDFFF);
                    Holo.banner(l, g.tag, back.add(0, e.getBbHeight() + 1.4, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("⟲ REWIND", 0x7FDFFF, true) + "]}", 1.2, 24);
                }
                Holo.punch(l, c, 6, 0x7FDFFF);
                Holo.sfx(l, c, "impact", 2.5f, 0.8f);
            }
            if (t == 72) {          // 시계가 깨짐
                Holo.shards(l, g.tag, c.add(0, 0.5, 0), "minecraft:light_blue_stained_glass", 30, 6);
                Holo.shards(l, g.tag, c.add(0, 0.5, 0), "minecraft:white_stained_glass", 20, 5);
                Holo.shockTex(l, g.tag, c, R * 1.6, 0x7FDFFF, 10);
                Holo.flare(l, g.tag, c.add(0, 1, 0), 12, 0xFFFFFF, 10);
                Holo.sfx(l, c, "shatter", 2.5f, 0.7f);
                for (LivingEntity e : g.foesNear(c, R)) g.hit(e, p, 40f);
                g.burst(c.add(0, 1, 0), 1.4, 160, 0x7FDFFF);
            }
            return t >= 80;
        }
    }

    /**
     * /summon 커맨드 피닉스: 빛의 불사조가 날개를 퍼덕이며 목표 주위를 선회해 적을 불태우고,
     * 하늘 높이 솟구쳤다가 목표에 내리꽂혀 불기둥 폭발.
     */
    static final class Phoenix implements Fx {
        int t = 0; Vec3 at, prev; double yaw = 0; final Vec3 tgt, start;
        static final int CIRCLE = 70, CLIMB = 86, DIVE = 98;
        Phoenix(Guard g, Vec3 to) {
            ServerLevel l = g.level;
            tgt = to; start = g.pos; at = start; prev = start;
            g.say("/summon phoenix ~ ~ ~ {Fire:32767,Invulnerable:1b}", 0xFF8A20, null, 0);
            java.util.function.Supplier<Vec3> P = () -> at;
            java.util.function.DoubleSupplier Y = () -> yaw;
            int life = DIVE + 4;
            // 몸통·머리
            Holo.sprite(l, g.tag, at, "orb", 0xFF8A20, "center", Holo.bb(0.01, 0, Vec3.ZERO), life, 1).follow(P, Vec3.ZERO).key(1, Holo.bb(3.4, 0, Vec3.ZERO), 6);
            Holo.sprite(l, g.tag, at, "orb", 0xFFFFFF, "center", Holo.bb(0.01, 0, Vec3.ZERO), life, 1).follow(P, Vec3.ZERO).key(1, Holo.bb(1.6, 0, Vec3.ZERO), 6);
            Holo.sprite(l, g.tag, at, "flare", 0xFFD84A, "center", Holo.bb(0.01, 0, Vec3.ZERO), life, 1).follow(P, Vec3.ZERO).key(2, Holo.bb(6, 0.6, Vec3.ZERO), 8);
            // 날개: 좌우 각 5장 깃털(초승달 텍스처), 6틱마다 퍼덕임 키프레임
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < 5; f++) {
                    double span = 1.4 + f * 1.15;
                    int col = f < 2 ? 0xFFFFFF : f < 4 ? 0xFFD84A : 0xFF5020;
                    Holo.Elem w = Holo.sprite(l, g.tag, at, "slash", col, "fixed", wing(side, span, f, 0), life, 1);
                    w.attach(P, Y);
                    for (int k = 1; k * 5 < life; k++) w.key(k * 5, wing(side, span, f, k % 2 == 0 ? 0.7 : -0.5), 5);
                }
            }
            Holo.sfx(l, at, "choir", 2f, 1.3f);
            Holo.sfx(l, at, "whoosh", 2f, 0.6f);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3f, 0.5f);
        }
        static float[] wing(int side, double span, int f, double flap) {
            double[][] R = Holo.mul(Holo.rotZ(side * (0.15 + flap)), Holo.mul(Holo.rotX(-Math.PI / 2), Holo.rotZ(side > 0 ? -0.5 : Math.PI + 0.5)));
            Vec3 off = Holo.apply(Holo.rotZ(side * (0.15 + flap)), new Vec3(side * span, 0, -f * 0.35));
            double s = 2.2 + f * 0.25;
            return Holo.m(R, s, s, 1, off, false);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            prev = at;
            if (t <= CIRCLE) {
                double u = t / (double) CIRCLE, a = u * Math.PI * 3.2, rad = 11 - u * 3;
                Vec3 want = tgt.add(Math.cos(a) * rad, 5 + Math.sin(u * 9) * 2, Math.sin(a) * rad);
                at = t < 10 ? start.lerp(want, t / 10.0) : want;
            } else if (t <= CLIMB) {
                double u = (t - CIRCLE) / (double) (CLIMB - CIRCLE);
                at = prev.lerp(tgt.add(0, 30, 0), 0.18 + u * 0.2);
            } else if (t <= DIVE) {
                double u = (t - CLIMB) / (double) (DIVE - CLIMB);
                at = tgt.add(0, 30, 0).lerp(tgt.add(0, 1, 0), u * u);
            }
            Vec3 v = at.subtract(prev);
            if (v.horizontalDistanceSqr() > 1e-4) yaw = Math.toDegrees(Math.atan2(-v.x, v.z));
            // 꼬리 불꽃 + 잔불
            l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.5, 0.3, 0.5, 0.04);
            l.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0.3, 0.2, 0.3, 0);
            l.sendParticles(dust(0xFF8A20, 2.6f), prev.x, prev.y, prev.z, 6, 0.4, 0.4, 0.4, 0);
            if (t % 2 == 0) Holo.sprite(l, g.tag, prev, "smoke" + (t / 2 % 2), 0xFF6020, "center", Holo.bb(2.2, t * 0.4, Vec3.ZERO), 12, 0).key(1, Holo.bb(0.2, t * 0.4 + 1, new Vec3(0, 1.2, 0)), 10);
            if (t % 8 == 0 && t < CLIMB) Holo.sfx(l, at, "whoosh", 1.2f, 0.9f + l.random.nextFloat() * 0.3f);
            if (t <= CIRCLE && t % 3 == 0) for (LivingEntity e : g.foesNear(at, 4.5)) {
                g.hit(e, p, 22f); e.igniteForSeconds(6);
                Holo.slash(l, g.tag, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.6, Holo.facing(v.lengthSqr() > 1e-4 ? v : new Vec3(1, 0, 0)), 0xFF8A20, 5);
            }
            if (t == CIRCLE + 1) { Holo.sfx(l, at, "riser", 2f, 0.9f); Holo.sfx(l, at, "choir", 2f, 1.0f); }
            if (t == CLIMB) { Holo.flare(l, g.tag, at, 14, 0xFFD84A, 10); Holo.circle(l, g.tag, tgt, 9, 0xFF8A20, 20, 0); Holo.sfx(l, at, "stinger", 2f, 1.1f); }
            if (t == DIVE) {
                Holo.explosion(l, g.tag, tgt, 7, 0xFF8A20);
                Holo.pillar(l, g.tag, tgt, 70, 5, 0xFF8A20, 2, 10, 12);
                Holo.pillar(l, g.tag, tgt, 70, 2, 0xFFFFFF, 2, 10, 10);
                Holo.shockTex(l, g.tag, tgt, 24, 0xFF8A20, 12);
                Holo.ringT(l, g.tag, tgt.add(0, 0.4, 0), 1, 16, 30, 0.6, 0.1, 0xFFD84A, 5, 3, 10, Holo.I(), 0, true);
                Holo.embers(l, g.tag, tgt, 40, 8, 0xFF8A20, 60);
                Holo.decal(l, g.tag, tgt.add(0, 0.07, 0), "crack", 0xFF6020, 2, 14, 3, 50, 16, 0, Holo.I());
                Holo.light(l, tgt.add(0, 1, 0), 30);
                for (LivingEntity e : g.foesNear(tgt, 12)) { g.hit(e, p, 130f); e.igniteForSeconds(10); e.setDeltaMovement(e.position().subtract(tgt).normalize().scale(1.6).add(0, 1, 0)); e.hurtMarked = true; }
                if (MechManager.griefOn(l.getServer()))
                    for (int i = 0; i < 24; i++) {
                        net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(tgt.add((l.random.nextDouble() - 0.5) * 16, 0.5, (l.random.nextDouble() - 0.5) * 16));
                        if (l.getBlockState(bp).isAir() && !l.getBlockState(bp.below()).isAir()) l.setBlockAndUpdate(bp, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                    }
                g.burst(tgt.add(0, 1, 0), 1.8, 260, 0xFF8A20);
                g.flash(tgt, 60, "o");
                Titan.shake(l, tgt, 60, true);
                Holo.punch(l, tgt, 8, 0xFF8A20);
                Holo.sfx(l, tgt, "boom", 3f, 0.7f);
                Holo.sfx(l, tgt, "choir", 2f, 1.4f);
                Holo.banner(l, g.tag, tgt.add(0, 5, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("🔥 PHOENIX DIVE", 0xFF8A20, true) + "]}", 2.4, 36);
            }
            return t >= DIVE + 2;
        }
    }

    /**
     * /kill @e 심판의 눈: 하늘에 거대한 눈이 떠서 주변의 모든 적을 훑어 표적으로 삼고,
     * 동시에 심판의 광선을 내리꽂음.
     */
    static final class JudgmentEye implements Fx {
        int t = 0; final Vec3 eye; final List<LivingEntity> marked = new ArrayList<>();
        Holo.Elem iris, pupil;
        static final int SCAN = 26, FIRE = 64, END = 86;
        JudgmentEye(Guard g, ServerPlayer p, Vec3 to) {
            ServerLevel l = g.level;
            eye = to.add(0, 24, 0);
            g.say("/kill @e[type=!player,distance=..40,sort=nearest]", 0xFF3040, null, 0);
            int life = END;
            // 흰자 (세로로 열림) · 홍채 · 동공 · 뒤 후광 2겹
            Holo.sprite(l, g.tag, eye, "orb", 0xFFFFFF, "center", Holo.m(Holo.I(), 14, 0.2, 1, Vec3.ZERO, false), life, 1)
                    .key(4, Holo.m(Holo.I(), 14, 8, 1, Vec3.ZERO, false), 10).key(END - 10, Holo.m(Holo.I(), 14, 0.2, 1, Vec3.ZERO, false), 6);
            iris = Holo.sprite(l, g.tag, eye, "circle", 0xFF3040, "center", Holo.m(Holo.I(), 0.01, 0.01, 1, new Vec3(0, 0, 0.05), false), life, 1);
            iris.key(10, Holo.m(Holo.I(), 6.5, 6.5, 1, new Vec3(0, 0, 0.05), false), 8).key(END - 10, Holo.m(Holo.I(), 0.01, 0.01, 1, Vec3.ZERO, false), 6);
            pupil = Holo.sprite(l, g.tag, eye, "orb", 0x000000, "center", Holo.m(Holo.I(), 0.01, 0.01, 1, new Vec3(0, 0, 0.1), false), life, 1);
            pupil.key(12, Holo.m(Holo.I(), 1.6, 3.6, 1, new Vec3(0, 0, 0.1), false), 8).key(FIRE - 6, Holo.m(Holo.I(), 0.9, 4.4, 1, new Vec3(0, 0, 0.1), false), 4)
                    .key(END - 10, Holo.m(Holo.I(), 0.01, 0.01, 1, Vec3.ZERO, false), 6);
            Holo.decal(l, g.tag, eye.add(0, 0, 0), "runering", 0xFF3040, 1, 26, 12, END - 22, 10, 2.5, Holo.rotX(Math.PI / 2));
            Holo.decal(l, g.tag, eye, "circle", 0xFFD84A, 1, 20, 14, END - 24, 10, -3, Holo.rotX(Math.PI / 2));
            Holo.glow(l, g.tag, eye, 6, 0xFF3040, 10, END - 20, 8);
            Holo.screen(l, eye, 70, 5, 0x400008, 4400, 0.6f);
            Holo.sfx(l, eye, "deep", 2.5f, 0.5f);
            Holo.sfx(l, eye, "choir", 2.5f, 0.6f);
            g.flash(eye, 60, "r");
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t % 3 == 0) l.sendParticles(dust(0xFF3040, 3f), eye.x, eye.y, eye.z, 8, 7, 3, 7, 0);
            if (t >= SCAN && t < FIRE && (t - SCAN) % 3 == 0) {      // 훑기: 한 명씩 표적 지정
                List<LivingEntity> foes = g.foesNear(eye.add(0, -24, 0), 40);
                foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
                for (LivingEntity e : foes) {
                    if (marked.contains(e) || marked.size() >= 20) continue;
                    marked.add(e);
                    Vec3 tp = e.position().add(0, e.getBbHeight() / 2, 0);
                    Holo.reticle(l, g.tag, e, 0xFF3040, FIRE - t + 6);
                    Holo.beamGlow(l, g.tag, eye, tp, 0.35, 0xFF3040, 1, FIRE - t - 2, 2);
                    Holo.sfx(l, tp, "data", 1f, 1.8f);
                    break;
                }
            }
            if (t == FIRE - 8) { Holo.sfx(l, eye, "charge", 3f, 0.8f); Holo.orb(l, g.tag, eye, 5, 0xFF3040, 7, 1, 2);
                Holo.banner(l, g.tag, eye.add(0, -6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("JUDGMENT  ×" + marked.size(), 0xFF3040, true) + "]}", 3.2, 30); }
            if (t == FIRE) {
                for (LivingEntity e : marked) {
                    if (!e.isAlive()) continue;
                    Vec3 tp = e.position().add(0, e.getBbHeight() / 2, 0);
                    Holo.beam(l, g.tag, eye, tp, 1.6, 0xFF3040, 1, 4, 6);
                    Holo.beamGlow(l, g.tag, eye, tp, 6, 0xFF3040, 1, 3, 6);
                    Holo.pillar(l, g.tag, e.position(), 40, 2.2, 0xFFFFFF, 1, 4, 6);
                    Holo.explosion(l, g.tag, e.position(), 2.6, 0xFF3040);
                    Holo.shockTex(l, g.tag, e.position(), 6, 0xFF3040, 7);
                    g.hit(e, p, 150f);
                }
                Holo.flare(l, g.tag, eye, 26, 0xFFFFFF, 12);
                Holo.shockTex(l, g.tag, eye, 30, 0xFF3040, 12);
                Holo.punch(l, eye.add(0, -24, 0), 8, 0xFF3040);
                Titan.shake(l, eye.add(0, -24, 0), 60, true);
                g.flash(eye, 70, "w");
                Holo.sfx(l, eye, "laser", 3f, 0.6f);
                Holo.sfx(l, eye.add(0, -24, 0), "boom", 3f, 0.8f);
                Holo.sfx(l, eye, "stinger", 2f, 0.7f);
            }
            return t >= END;
        }
    }

    /**
     * /tp 행성 낙하 (v3): 시전자에게 전용 시네마틱 — 하늘이 찢어지고, 카메라가 균열을 뚫고 우주로 올라가
     * /fill 선택 상자에 갇힌 실제 행성(지름 220)을 /tp 견인 광선으로 끌어내림 → 대기권 돌입(균열·작열·파편 비)
     * → 충돌: 섬광·충격파·화염 돔·버섯구름 + 실제 분화구(반경 48·깊이 26, 틱당 나눠 삭제) + 반경 60 소멸.
     */
    // ═════════════════════════ 추가 명령 (26~30)
    /** /gamerule doGravity false → 적이 떠오름 → true → 지면에 내리꽂힘. */
    static final class GravityCollapse implements Fx {
        int t = 0; final Vec3 at; final List<LivingEntity> caught = new ArrayList<>();
        GravityCollapse(Guard g, Vec3 to) {
            at = to;
            g.say("/gamerule doGravity false", 0x88DDFF, null, 0);
            caught.addAll(g.foesNear(to, 22));
            Holo.circle(g.level, g.tag, to, 22, 0x88DDFF, 46, 0);
            Holo.sfx(g.level, to, "riser", 2.5f, 0.9f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t <= 30) {
                for (LivingEntity e : caught) if (e.isAlive()) {
                    Vec3 sw = new Vec3(-(e.getZ() - at.z), 0, e.getX() - at.x).normalize().scale(0.12);
                    e.setDeltaMovement(sw.x, 0.42 - t * 0.008, sw.z); e.hurtMarked = true;
                }
                if (t % 4 == 0) l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 4, at.z, 30, 10, 4, 10, 0.05);
                if (t == 12) Holo.ring(l, g.tag, at.add(0, 10, 0), 2, 24, 28, 0.6, 0.05, 0x88DDFF, 14, 4, 6, 0, 3, true);
                return false;
            }
            if (t == 31) {
                g.say("/gamerule doGravity true  ×20", 0xFF5533, null, 0);
                for (LivingEntity e : caught) if (e.isAlive()) { e.setDeltaMovement(0, -4.2, 0); e.hurtMarked = true; }
                Holo.sfx(l, at, "whoosh", 3f, 0.5f);
            }
            if (t == 36) {
                for (LivingEntity e : caught) if (e.isAlive()) {
                    g.hit(e, p, 70f);
                    Holo.explosion(l, g.tag, e.position(), 2.4, 0x88DDFF);
                }
                Holo.shock(l, g.tag, at, 24, 0x88DDFF, 10);
                Holo.sfx(l, at, "boom", 3f, 0.5f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3f, 0.6f);
                Titan.shake(l, at, 60, true);
                return true;
            }
            return false;
        }
    }

    static final net.minecraft.resources.ResourceLocation SHRINK_ID = com.tubilt.jjk.JjkMod.id("cmd_shrink");
    /** /attribute @e minecraft:scale → 4분의 1, 그동안 받는 피해 증가. */
    static final class Shrink implements Fx {
        int t = 0; final List<LivingEntity> caught = new ArrayList<>();
        Shrink(Guard g, ServerPlayer p, Vec3 to) {
            g.say("/attribute @e[distance=..14] minecraft:scale base set 0.25", 0x55FFFF, null, 0);
            for (LivingEntity e : g.foesNear(to, 14)) {
                var a = e.getAttribute(Attributes.SCALE);
                if (a == null || a.hasModifier(SHRINK_ID)) continue;
                a.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SHRINK_ID, -0.75,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                caught.add(e);
                Holo.hitbox(g.level, g.tag, e, 0x55FFFF, 16);
                Holo.glow(g.level, g.tag, e.position().add(0, e.getBbHeight() / 2, 0), 1.2, 0x55FFFF, 2, 2, 6);
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200, 2));
                g.hit(e, p, 20f);
            }
            Holo.sfx(g.level, to, "glitch", 2f, 1.4f);
            g.level.playSound(null, to.x, to.y, to.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 2f, 1.6f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t % 10 == 0) for (LivingEntity e : caught) if (e.isAlive())
                g.level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY() + 0.3, e.getZ(), 4, 0.2, 0.2, 0.2, 0.3);
            if (t < 200) return false;
            for (LivingEntity e : caught) {
                var a = e.getAttribute(Attributes.SCALE);
                if (a != null) a.removeModifier(SHRINK_ID);
            }
            return true;
        }
    }

    /** /data modify entity @e Health set value 1f */
    static final class HealthOne implements Fx {
        int t = 0; final List<LivingEntity> caught = new ArrayList<>();
        HealthOne(Guard g, Vec3 to) {
            g.say("/data modify entity @e[distance=..16] Health set value 1f", 0xFF3040, null, 0);
            caught.addAll(g.foesNear(to, 16));
            for (LivingEntity e : caught) {
                Holo.hitbox(g.level, g.tag, e, 0xFF3040, 14);
                String txt = String.format(java.util.Locale.ROOT, "Health: %.1ff → 1.0f", e.getHealth());
                Holo.banner(g.level, g.tag, e.position().add(0, e.getBbHeight() + 1.0, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(txt, 0xFF3040, true) + "]}", 0.8, 30);
            }
            Holo.sfx(g.level, to, "data", 2.5f, 0.8f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t != 12) return false;
            for (LivingEntity e : caught) {
                if (!e.isAlive() || e.getMaxHealth() > 400) continue;   // 보스급은 버팀
                e.setHealth(1f);
                Holo.glow(g.level, g.tag, e.position().add(0, e.getBbHeight() / 2, 0), 1.4, 0xFF3040, 1, 2, 6);
                g.level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
            }
            g.level.playSound(null, g.pos.x, g.pos.y, g.pos.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.5f, 1.4f);
            return true;
        }
    }

    /** /tp @e ~ ~250 ~ — 빛기둥과 함께 하늘 저편으로 추방 (낙하). */
    static final class Banish implements Fx {
        int t = 0; final List<LivingEntity> caught = new ArrayList<>(); final Vec3 at;
        Banish(Guard g, Vec3 to) {
            at = to;
            g.say("/tp @e[distance=..12] ~ ~250 ~", 0xFFE070, null, 0);
            caught.addAll(g.foesNear(to, 12));
            for (LivingEntity e : caught) Holo.pillar(g.level, g.tag, e.position(), 60, 1.6, 0xFFE070, 3, 8, 8);
            Holo.circle(g.level, g.tag, to, 12, 0xFFE070, 20, 0);
            Holo.sfx(g.level, to, "charge", 2.5f, 1.2f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t != 10) return false;
            for (LivingEntity e : caught) {
                if (!e.isAlive()) continue;
                Vec3 o = e.position();
                Holo.flare(g.level, g.tag, o.add(0, 1, 0), 4, 0xFFE070, 8);
                e.teleportTo(o.x, Math.min(o.y + 250, g.level.getMaxY() + 60), o.z);
                e.setDeltaMovement(0, -0.5, 0); e.hurtMarked = true;
            }
            g.level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2f, 0.6f);
            Holo.sfx(g.level, at, "whoosh", 2.5f, 1.4f);
            return true;
        }
    }

    /** /item replace entity @e weapon.mainhand with air */
    static final class Disarm implements Fx {
        int t = 0;
        Disarm(Guard g, Vec3 to) {
            g.say("/item replace entity @e[distance=..18] weapon.* with air", 0xFFAA33, null, 0);
            ServerPlayer owner = g.p();
            for (LivingEntity e : g.foesNear(to, 18)) {
                for (net.minecraft.world.entity.EquipmentSlot sl : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND}) {
                    ItemStack st = e.getItemBySlot(sl);
                    if (st.isEmpty()) continue;
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    Vec3 hand = e.position().add(0, e.getBbHeight() * 0.6, 0);
                    Holo.item(g.level, g.tag, hand, id, false, 0xFFAA33, Holo.m(Holo.I(), 0.8, 0.8, 0.8, Vec3.ZERO, false), 20, 0)
                            .key(1, Holo.m(Holo.I(), 0.8, 0.8, 0.8, new Vec3(0, 3, 0), false), 10).key(12, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 3.5, 0), false), 6).spin(24);
                    if (e instanceof Player pl) pl.drop(st.copy(), true);
                    e.setItemSlot(sl, ItemStack.EMPTY);
                }
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200, 1));
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1));
                if (owner != null) g.hit(e, owner, 15f);
            }
            Holo.sfx(g.level, to, "glitch", 2f, 1.0f);
            g.level.playSound(null, to.x, to.y, to.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2f, 0.8f);
        }
        public boolean tick(Guard g, ServerPlayer p) { return ++t > 2; }
    }

    // ═════════ v0.37 신규 명령 6종
    static final String[] CMD_BLOCKS = {"minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block"};

    /** /summon 커맨드 블록 비 — 하늘에서 명령 블록 36개가 순서대로 떨어져 연쇄 폭발, 마지막에 거대 반복 블록. */
    static final class CommandRain implements Fx {
        int t = 0; final Vec3 at; final ServerPlayer owner;
        CommandRain(Guard g, ServerPlayer p, Vec3 to) {
            at = to; owner = p;
            ServerLevel l = g.level;
            g.say("/execute repeat 36 run summon falling_block ~ ~30 ~ {BlockState:{Name:command_block}}", 0xFFAA33, null, 0);
            java.util.Random r = new java.util.Random();
            for (int i = 0; i < 36; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 14;
                Vec3 q = to.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                int delay = 4 + i * 2;
                String b = CMD_BLOCKS[i % 3];
                int col = i % 3 == 0 ? 0xFFAA33 : i % 3 == 1 ? 0x55FF88 : 0xB45CFF;
                Holo.block(l, g.tag, q, b, col, Holo.m(Holo.I(), 1.6, 1.6, 1.6, new Vec3(0, 34, 0), true), delay + 9, 0)
                        .key(delay, Holo.m(Holo.I(), 1.6, 1.6, 1.6, new Vec3(0, 0.8, 0), true), 6).spin(18);
                Holo.beamGlow(l, g.tag, q.add(0, 34, 0), q, 0.25, col, delay, 2, 4);
                final Vec3 qq = q; final int cc = col;
                g.later(delay + 6, () -> {
                    Holo.explosion(l, g.tag, qq, 3.2, cc);
                    Holo.circle(l, g.tag, qq.add(0, 0.1, 0), 3.5, cc, 14, 0);
                    for (LivingEntity e : g.foesNear(qq, 3.8)) g.hit(e, owner, 18f);
                    l.playSound(null, qq.x, qq.y, qq.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.6f + (float) Math.random() * 0.4f);
                });
            }
            // 마지막: 거대 반복 명령 블록
            Holo.block(l, g.tag, to, "minecraft:repeating_command_block", 0xB45CFF, Holo.m(Holo.I(), 8, 8, 8, new Vec3(0, 60, 0), true), 92, 0)
                    .key(70, Holo.m(Holo.I(), 8, 8, 8, new Vec3(0, 4, 0), true), 8).spin(6);
            Holo.sfx(l, to, "riser", 2.5f, 0.8f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t == 78) {
                ServerLevel l = g.level;
                g.say("/execute if block ~ ~ ~ repeating_command_block run kill @e[distance=..10]", 0xB45CFF, null, 0);
                Holo.explosion(l, g.tag, at, 9, 0xB45CFF);
                Holo.shock(l, g.tag, at, 18, 0xB45CFF, 12);
                Holo.shards(l, g.tag, at, "minecraft:repeating_command_block", 24, 8);
                for (LivingEntity e : g.foesNear(at, 10)) g.hit(e, p, 70f);
                Holo.sfx(l, at, "boom", 3f, 0.6f);
                Titan.shake(l, at, 60, true);
            }
            return t >= 80;
        }
    }

    /** /setblock 큐브 압축 — 적을 유리 큐브에 가두고 1블록으로 압축. */
    static final class CubeCrush implements Fx {
        int t = 0; final Vec3 at; final List<LivingEntity> caught = new ArrayList<>();
        CubeCrush(Guard g, Vec3 to) {
            at = to.add(0, 6, 0);
            ServerLevel l = g.level;
            g.say("/fill ~-6 ~ ~-6 ~6 ~12 ~6 minecraft:light_blue_stained_glass hollow", 0x88DDFF, null, 0);
            caught.addAll(g.foesNear(to, 9));
            for (String b : new String[]{"minecraft:light_blue_stained_glass", "minecraft:white_stained_glass"}) {
                double s0 = b.contains("white") ? 12.4 : 12;
                Holo.block(l, g.tag, at, b, 0x88DDFF, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), 44, 0)
                        .key(1, Holo.m(Holo.I(), s0, s0, s0, Vec3.ZERO, true), 6)
                        .key(10, Holo.m(Holo.I(), s0, s0, s0, Vec3.ZERO, true), 2)
                        .key(14, Holo.m(Holo.I(), 1, 1, 1, Vec3.ZERO, true), 22).spin(3);
            }
            for (LivingEntity e : caught) Holo.hitbox(l, g.tag, e, 0x88DDFF, 38);
            Holo.sfx(l, at, "plate", 2.5f, 0.7f);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 2f, 0.5f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t > 12 && t < 36) {
                double half = Math.max(0.6, 6 * (1 - (t - 12) / 22.0));
                for (LivingEntity e : caught) if (e.isAlive()) {
                    Vec3 d = at.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                    e.setDeltaMovement(d.scale(0.25)); e.hurtMarked = true;
                    if (d.length() > half) e.teleportTo(at.x - d.normalize().x * half, at.y - d.normalize().y * half - e.getBbHeight() / 2, at.z - d.normalize().z * half);
                }
                if (t % 4 == 0) { l.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 2f, 0.4f + t * 0.03f); l.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, half, half, half, 0.02); }
            }
            if (t == 37) {
                g.say("/setblock ~ ~ ~ minecraft:light_blue_stained_glass  ← " + caught.size() + " entities", 0x88DDFF, null, 0);
                for (LivingEntity e : caught) if (e.isAlive()) {
                    g.hit(e, p, 95f);
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 80, 4));
                }
                Holo.explosion(l, g.tag, at, 5, 0x88DDFF);
                Holo.shards(l, g.tag, at, "minecraft:light_blue_stained_glass", 40, 7);
                Holo.flare(l, g.tag, at, 8, 0xFFFFFF, 8);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3f, 0.5f);
                Holo.sfx(l, at, "shatter", 3f, 0.8f);
                return true;
            }
            return false;
        }
    }

    /** /scoreboard 처형 카운트 — 적 머리 위 점수 3 → 2 → 1 → 0 이 되면 처형. */
    static final class ScoreExecute implements Fx {
        int t = 0; final List<LivingEntity> caught = new ArrayList<>();
        ScoreExecute(Guard g, Vec3 to) {
            g.say("/scoreboard players set @e[distance=..20] jjk.exec 3", 0xFF3040, null, 0);
            caught.addAll(g.foesNear(to, 20));
            for (LivingEntity e : caught) Holo.hitbox(g.level, g.tag, e, 0xFF3040, 66);
            Holo.sfx(g.level, to, "data", 2.5f, 0.7f);
        }
        void label(Guard g, LivingEntity e, String s, int rgb, double sc) {
            Holo.banner(g.level, g.tag, e.position().add(0, e.getBbHeight() + 1.3, 0),
                    "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("jjk.exec ", 0xAAAAAA, false) + "," + Holo.tx(s, rgb, true) + "]}", sc, 19);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t == 1 || t == 21 || t == 41) {
                int n = 3 - (t - 1) / 20;
                for (LivingEntity e : caught) if (e.isAlive()) {
                    label(g, e, String.valueOf(n), n == 1 ? 0xFF3040 : 0xFFE070, 1.0 + (3 - n) * 0.3);
                    Holo.glow(l, g.tag, e.position().add(0, e.getBbHeight() / 2, 0), 1.0 + (3 - n) * 0.5, 0xFF3040, 1, 1, 4);
                }
                l.playSound(null, g.pos.x, g.pos.y, g.pos.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 2f, 0.6f + (3 - n) * 0.2f);
            }
            if (t == 61) {
                g.say("/execute as @e[scores={jjk.exec=0}] run kill @s", 0xFF3040, null, 0);
                for (LivingEntity e : caught) if (e.isAlive()) {
                    label(g, e, "0  ✖", 0xFF3040, 1.8);
                    Vec3 o = e.position();
                    Holo.pillar(l, g.tag, o, 40, 1.4, 0xFF3040, 1, 3, 6);
                    Holo.lightning(l, g.tag, o.add(0, 40, 0), o, 0xFF3040, 0.5, 8);
                    if (e.getMaxHealth() <= 60) { e.invulnerableTime = 0; e.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
                    else g.hit(e, p, 140f);
                }
                Holo.sfx(l, g.pos, "stinger", 2.5f, 0.9f);
                l.playSound(null, g.pos.x, g.pos.y, g.pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2f, 1.4f);
                return true;
            }
            return false;
        }
    }

    /** /clone 지형 압착 — 발밑 지형 한 판을 복제해 띄웠다가 내리찍음. */
    static final class TerrainClone implements Fx {
        int t = 0; final Vec3 at; final List<Holo.Elem> slab = new ArrayList<>();
        TerrainClone(Guard g, Vec3 to) {
            at = to;
            ServerLevel l = g.level;
            g.say("/clone ~-7 ~-2 ~-7 ~7 ~ ~7 ~-7 ~24 ~-7", 0xC08040, null, 0);
            String gb = groundName(l, to.add(0, -0.5, 0));
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                if (x * x + z * z > 12) continue;
                Vec3 q = to.add(x * 2.2, 0, z * 2.2);
                int d = (Math.abs(x) + Math.abs(z)) * 2;
                Holo.Elem e = Holo.block(l, g.tag, q, (x + z) % 4 == 0 ? "minecraft:dirt" : gb, 0xC08040, Holo.m(Holo.I(), 2.2, 0.05, 2.2, new Vec3(0, -0.5, 0), true), 46, 0)
                        .key(1 + d / 2, Holo.m(Holo.I(), 2.2, 2.2, 2.2, new Vec3(0, 0.6, 0), true), 4)
                        .key(8 + d / 2, Holo.m(Holo.I(), 2.2, 2.2, 2.2, new Vec3(0, 24, 0), true), 16)
                        .key(34, Holo.m(Holo.I(), 2.2, 2.2, 2.2, new Vec3(0, 0.6, 0), true), 4);
                slab.add(e);
            }
            Holo.circle(l, g.tag, to.add(0, 0.1, 0), 9, 0xC08040, 40, 0);
            Holo.sfx(l, to, "riser", 2.5f, 0.6f);
            l.playSound(null, to.x, to.y, to.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 3f, 0.4f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t < 30 && t % 3 == 0) l.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 20, at.z, 12, 7, 1, 7, 0.02);
            if (t == 38) {
                for (LivingEntity e : g.foesNear(at, 9.5)) { g.hit(e, p, 85f); e.setDeltaMovement(e.getDeltaMovement().add(0, -1, 0)); e.hurtMarked = true; }
                Holo.shock(l, g.tag, at, 20, 0xC08040, 12);
                Holo.shards(l, g.tag, at, groundName(l, at.add(0, -0.5, 0)), 40, 8);
                Holo.explosion(l, g.tag, at, 6, 0xC08040);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3f, 0.5f);
                Holo.sfx(l, at, "boom", 3f, 0.5f);
                Titan.shake(l, at, 60, true);
            }
            return t >= 46;
        }
    }

    /** /summon 포탑 진지 — 명령 블록 포탑 4기가 8초 동안 가장 가까운 적을 광선으로 사격. */
    static final class Turrets implements Fx {
        int t = 0; final List<Vec3> guns = new ArrayList<>();
        Turrets(Guard g, Vec3 to) {
            ServerLevel l = g.level;
            g.say("/summon jjk:turret ×4  {Target:\"@e[sort=nearest,limit=1]\"}", 0x40E8FF, null, 0);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                Vec3 q = to.add(Math.cos(a) * 9, 0, Math.sin(a) * 9);
                guns.add(q.add(0, 3.2, 0));
                Holo.block(l, g.tag, q, "minecraft:repeating_command_block", 0x40E8FF, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 1, 0), true), 170, 0)
                        .key(1 + i * 2, Holo.m(Holo.I(), 2, 2, 2, new Vec3(0, 1, 0), true), 6);
                Holo.block(l, g.tag, q, "minecraft:observer", 0x40E8FF, Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 3.2, 0), true), 170, 0)
                        .key(3 + i * 2, Holo.m(Holo.I(), 1.2, 1.2, 1.2, new Vec3(0, 3.2, 0), true), 6).spin(12);
                Holo.circle(l, g.tag, q.add(0, 0.1, 0), 2.2, 0x40E8FF, 170, 0);
            }
            Holo.sfx(l, to, "charge", 2.5f, 1.0f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t > 14 && t % 8 == 0) {
                ServerLevel l = g.level;
                for (int i = 0; i < guns.size(); i++) {
                    Vec3 gun = guns.get(i);
                    LivingEntity best = null; double bd = 28 * 28;
                    for (LivingEntity e : g.foesNear(gun, 28)) { double d = e.distanceToSqr(gun); if (d < bd) { bd = d; best = e; } }
                    if (best == null) continue;
                    Vec3 hp = best.position().add(0, best.getBbHeight() * 0.6, 0);
                    Holo.beamGlow(l, g.tag, gun, hp, 0.35, 0x40E8FF, 1, 1, 3);
                    Holo.glow(l, g.tag, hp, 1.4, 0x40E8FF, 1, 1, 3);
                    g.hit(best, p, 12f);
                    l.playSound(null, gun.x, gun.y, gun.z, SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.8f, 1.8f);
                }
            }
            return t >= 170;
        }
    }

    /** /kill 삭제 코드 — 선택자에 걸린 적을 데이터 조각으로 분해. */
    static final class DeleteCode implements Fx {
        int t = 0; final List<LivingEntity> caught = new ArrayList<>(); final Vec3 at;
        DeleteCode(Guard g, Vec3 to) {
            at = to;
            ServerLevel l = g.level;
            g.say("/kill @e[distance=..14,type=!player]", 0xFF55FF, null, 0);
            caught.addAll(g.foesNear(to, 14));
            for (LivingEntity e : caught) {
                Holo.hitbox(l, g.tag, e, 0xFF55FF, 24);
                Holo.banner(l, g.tag, e.position().add(0, e.getBbHeight() + 1.0, 0), "{text:\"\",font:\"minecraft:uniform\",extra:["
                        + Holo.tx("DELETE ", 0xFF55FF, true) + "," + Holo.tx(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 0xFFFFFF, false) + "]}", 0.9, 24);
            }
            Holo.circle(l, g.tag, to.add(0, 0.1, 0), 14, 0xFF55FF, 26, 0);
            Holo.sfx(l, to, "glitch", 2.5f, 0.7f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t < 22 && t % 3 == 0) for (LivingEntity e : caught) if (e.isAlive()) {
                l.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 20, 0.4, e.getBbHeight() / 2, 0.4, 0.4);
                e.setDeltaMovement(0, 0.02, 0); e.hurtMarked = true;
            }
            if (t == 22) {
                for (LivingEntity e : caught) if (e.isAlive()) {
                    Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
                    Holo.shards(l, g.tag, c, "minecraft:magenta_stained_glass", 16, 5);
                    Holo.shards(l, g.tag, c, "minecraft:command_block", 6, 4);
                    Holo.flare(l, g.tag, c, 3, 0xFF55FF, 6);
                    if (e.getMaxHealth() <= 100) { e.invulnerableTime = 0; e.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
                    else g.hit(e, p, 150f);
                }
                l.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.5f, 0.4f);
                Holo.sfx(l, at, "shatter", 3f, 1.2f);
                return true;
            }
            return false;
        }
    }

    // ═════════ v0.40 신규 명령 5종
    /** /team join 세뇌 — 적들이 15초 동안 서로를 공격. */
    static final class Brainwash implements Fx {
        int t = 0; final List<net.minecraft.world.entity.Mob> caught = new ArrayList<>();
        Brainwash(Guard g, Vec3 to) {
            ServerLevel l = g.level;
            g.say("/team join 아군 @e[distance=..16,type=!player]", 0x55FF55, null, 0);
            for (LivingEntity e : g.foesNear(to, 16)) if (e instanceof net.minecraft.world.entity.Mob m) {
                caught.add(m);
                m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 300, 0));
                Holo.banner(l, g.tag, m.position().add(0, m.getBbHeight() + 1, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("[아군] 🧠", 0x55FF55, true) + "]}", 0.9, 40);
                Holo.lightning(l, g.tag, g.pos, m.position().add(0, m.getBbHeight() / 2, 0), 0x55FF55, 0.08, 8);
            }
            Holo.sfx(l, to, "glitch", 2f, 1.6f);
            l.playSound(null, to.x, to.y, to.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.5f, 1.4f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t % 10 == 0) for (net.minecraft.world.entity.Mob m : caught) {
                if (!m.isAlive()) continue;
                net.minecraft.world.entity.Mob best = null; double bd = 400;
                for (net.minecraft.world.entity.Mob o : caught) if (o != m && o.isAlive()) { double d = o.distanceToSqr(m); if (d < bd) { bd = d; best = o; } }
                if (best == null) for (LivingEntity o : g.foesNear(m.position(), 20)) if (o != m && o instanceof net.minecraft.world.entity.Mob om && !caught.contains(om)) { best = om; break; }
                m.setTarget(best);
                if (best != null && bd < 6) { best.hurtServer(g.level, g.level.damageSources().mobAttack(m), 4f); }
            }
            if (t < 300) return false;
            for (net.minecraft.world.entity.Mob m : caught) if (m.isAlive()) m.setTarget(null);
            return true;
        }
    }

    /** /give @e tnt — 적 머리 위에 TNT 가 붙고 3초 뒤 연쇄 폭발. */
    static final class StickyTnt implements Fx {
        int t = 0; final List<LivingEntity> caught = new ArrayList<>();
        StickyTnt(Guard g, Vec3 to) {
            g.say("/item replace entity @e[distance=..14] armor.head with tnt", 0xFF5533, null, 0);
            caught.addAll(g.foesNear(to, 14));
            for (LivingEntity e : caught) {
                final LivingEntity ee = e;
                Holo.block(g.level, g.tag, e.position(), "minecraft:tnt", 0xFF3030, Holo.m(Holo.I(), 0.8, 0.8, 0.8, Vec3.ZERO, true), 62, 1)
                        .follow(() -> ee.position().add(0, ee.getBbHeight() + 0.6, 0), Vec3.ZERO).wobble(15, 3);
            }
            g.level.playSound(null, to.x, to.y, to.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 2f, 1f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t == 20 || t == 40) for (LivingEntity e : caught) if (e.isAlive())
                Holo.banner(g.level, g.tag, e.position().add(0, e.getBbHeight() + 1.6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(t == 20 ? "2" : "1", 0xFF3030, true) + "]}", 1.2, 18);
            if (t < 60) return false;
            int i = 0;
            for (LivingEntity e : caught) {
                if (!e.isAlive()) continue;
                final LivingEntity ee = e;
                g.later(i++ * 2, () -> {
                    Vec3 c = ee.position().add(0, ee.getBbHeight() + 0.5, 0);
                    Holo.explosion(g.level, g.tag, c, 3, 0xFF5533);
                    for (LivingEntity o : g.foesNear(c, 3.5)) g.hit(o, p, 45f);
                    g.level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 1f);
                });
            }
            return true;
        }
    }

    /** /random 운명의 주사위 — 거대 주사위를 굴려 1~6 결과. */
    static final class Dice implements Fx {
        int t = 0; final Vec3 at; final int roll; final String[] FACE = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
        Dice(Guard g, Vec3 to) {
            at = to; roll = 1 + g.level.random.nextInt(6);
            g.say("/random value 1..6", 0xFFFFFF, null, 0);
            Holo.block(g.level, g.tag, to.add(0, 9, 0), "minecraft:white_concrete", 0xFFFFFF, Holo.m(Holo.I(), 3, 3, 3, Vec3.ZERO, true), 46, 0)
                    .key(1, Holo.m(Holo.mul(Holo.rotX(5), Holo.rotZ(3)), 3, 3, 3, Vec3.ZERO, true), 30).spin(25);
            for (int i = 0; i < 6; i++) g.later(i * 5, () -> g.level.playSound(null, to.x, to.y + 9, to.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.5f, 1.2f));
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t != 34) return false;
            ServerLevel l = g.level;
            Holo.banner(l, g.tag, at.add(0, 13, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx(FACE[roll - 1] + " " + roll, roll == 6 ? 0xFFD84A : 0xFFFFFF, true) + "]}", 3, 40);
            switch (roll) {
                case 1 -> { g.say("/random → 1 … 꽝. 미안.", 0x888888, null, 0); Holo.sfx(l, at, "glitch", 1.5f, 0.5f); }
                case 2 -> { for (int i = 0; i < 6; i++) { Vec3 q = at.add((l.random.nextDouble() - 0.5) * 12, 0, (l.random.nextDouble() - 0.5) * 12);
                    Holo.lightning(l, g.tag, q.add(0, 30, 0), q, 0xFFFF66, 0.4, 8); for (LivingEntity e : g.foesNear(q, 4)) g.hit(e, p, 30f); } }
                case 3 -> g.fx.add(new CubeCrush(g, at));
                case 4 -> g.fx.add(new CommandRain(g, p, at));
                case 5 -> g.fx.add(new Turrets(g, at));
                default -> { g.fx.add(new Meteor(g, at, 11, 0, true)); g.say("/random → 6 !!!", 0xFFD84A, null, 0); Holo.sfx(l, at, "stinger", 2.5f, 1.2f); }
            }
            return true;
        }
    }

    /** /execute 블랙홀 → 화이트홀 — 빨아들였다가 정반대로 뱉어냄. */
    static final class HoleFlip implements Fx {
        int t = 0; final Vec3 at;
        HoleFlip(Guard g, Vec3 to) {
            at = to.add(0, 3, 0);
            g.say("/execute positioned ~ ~3 ~ run summon black_hole", 0x8040FF, null, 0);
            Holo.block(g.level, g.tag, at, "minecraft:black_concrete", 0x8040FF, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), 52, 0)
                    .key(1, Holo.m(Holo.I(), 3, 3, 3, Vec3.ZERO, true), 10).key(44, Holo.m(Holo.I(), 0.3, 0.3, 0.3, Vec3.ZERO, true), 4).spin(30);
            Holo.ring(g.level, g.tag, at, 3, 7, 40, 0.5, 0.05, 0x8040FF, 4, 40, 6, 0.3, 6, true);
            Holo.sfx(g.level, at, "deep", 2.5f, 0.6f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t < 48) {
                for (LivingEntity e : g.foesNear(at, 18)) {
                    Vec3 d = at.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                    e.setDeltaMovement(d.normalize().scale(Math.min(1.2, 0.25 + 6 / Math.max(1, d.length())))); e.hurtMarked = true;
                    if (t % 10 == 0 && d.length() < 3) g.hit(e, p, 8f);
                }
                if (t % 3 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 30, 6, 6, 6, 0.1);
                return false;
            }
            if (t == 48) {
                g.say("/execute … run summon white_hole", 0xFFFFFF, null, 0);
                Holo.flare(l, g.tag, at, 10, 0xFFFFFF, 10);
                Holo.shock(l, g.tag, at, 22, 0xFFFFFF, 10);
                for (LivingEntity e : g.foesNear(at, 8)) {
                    Vec3 d = e.position().subtract(at).normalize();
                    e.setDeltaMovement(d.scale(3).add(0, 1.2, 0)); e.hurtMarked = true;
                    g.hit(e, p, 60f);
                }
                Holo.sfx(l, at, "boom", 3f, 1.2f);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 0.8f);
                return true;
            }
            return false;
        }
    }

    /** /summon 거대 손 — 하늘에서 블록 손바닥이 내려와 찰싹, 이어서 손가락 튕기기. */
    static final class GiantHand implements Fx {
        int t = 0; final Vec3 at;
        GiantHand(Guard g, Vec3 to) {
            at = to;
            ServerLevel l = g.level;
            g.say("/summon jjk:hand ~ ~40 ~ {Gesture:\"slap\"}", 0xFFCC99, null, 0);
            Holo.circle(l, g.tag, to.add(0, 0.1, 0), 7, 0xFFCC99, 30, 0);
            String skin = "minecraft:smooth_sandstone";
            // 손바닥 + 손가락 4 + 엄지 (손 기준 좌표, 위에서 내려옴)
            double[][] parts = {{0, 0, 0, 8, 1.6, 7}, {-3, 0, 6, 1.6, 1.4, 5}, {-1, 0, 6.8, 1.6, 1.4, 5.8}, {1, 0, 6.6, 1.6, 1.4, 5.4}, {3, 0, 5.8, 1.6, 1.4, 4.4}, {5.2, 0, 1, 1.6, 1.4, 4}};
            for (double[] q : parts) {
                Vec3 off = new Vec3(q[0], 0, q[2] - 3);
                Holo.block(l, g.tag, to, skin, 0xFFCC99, Holo.m(Holo.I(), q[3], q[4], q[5], off.add(0, 40, 0), true), 60, 0)
                        .key(14, Holo.m(Holo.I(), q[3], q[4], q[5], off.add(0, 0.8, 0), true), 6)
                        .key(40, Holo.m(Holo.I(), q[3], q[4], q[5], off.add(0, 50, 0), true), 14);
            }
            Holo.sfx(l, to, "whoosh", 2.5f, 0.6f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t == 20) {
                for (LivingEntity e : g.foesNear(at, 7)) { g.hit(e, p, 90f); e.setDeltaMovement(0, -1, 0); e.hurtMarked = true; }
                Holo.shock(l, g.tag, at, 16, 0xFFCC99, 10);
                Holo.shards(l, g.tag, at, groundName(l, at.add(0, -0.5, 0)), 30, 6);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3f, 0.7f);
                Holo.banner(l, g.tag, at.add(0, 6, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("찰싹!", 0xFFCC99, true) + "]}", 3, 20);
                Titan.shake(l, at, 40, true);
            }
            if (t == 34) {   // 손가락 튕기기: 남은 적을 멀리
                for (LivingEntity e : g.foesNear(at, 10)) { Vec3 d = e.position().subtract(at).normalize(); e.setDeltaMovement(d.scale(2.5).add(0, 1, 0)); e.hurtMarked = true; g.hit(e, p, 25f); }
                l.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 3f, 0.5f);
            }
            return t >= 40;
        }
    }

    // ═════════ 인피니티 키 · 숨겨진 필살기
    static final String UNSEAL = "jjk.guard_unsealed";
    static final int BASE_MODES = 42;
    static int hudFlags(ServerPlayer p) { return (unsealed(p) ? 1 : 0) | (chain(p) ? 2 : 0); }
    static int baseModes(ServerPlayer p) { return unsealed(p) ? MODES0 : BASE_MODES; }
    static final int MODES0 = 46;
    static boolean unsealed(ServerPlayer p) { return p != null && p.getTags().contains(UNSEAL); }

    /** 인피니티 키 우클릭: 수호의 봉인을 푼다. */
    public static void useKey(ServerPlayer p, ItemStack st) {
        Guard g = GUARDS.get(p.getUUID());
        if (g == null) { p.displayClientMessage(Component.literal("§b인피니티 키 §7— 커맨드 수호를 먼저 소환해야 한다"), true); return; }
        if (unsealed(p)) { p.displayClientMessage(Component.literal("§b봉인은 이미 풀려 있다. §7(Z/C 로 숨겨진 필살기 선택)"), true); return; }
        for (Fx f : g.fx) if (f instanceof Unseal) return;
        if (!p.isCreative()) st.shrink(1);
        g.fx.add(new Unseal(g, p));
    }

    static final class Unseal implements Fx {
        int t = 0; final Vec3 c; final List<Holo.Elem> chains = new ArrayList<>(); Holo.Elem key;
        Unseal(Guard g, ServerPlayer p) {
            ServerLevel l = g.level;
            c = g.pos;
            g.say("/execute as @s run data remove entity @s Sealed", 0x55FFFF, null, 0);
            // 수호를 묶은 사슬 12줄
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6, el = (i % 3 - 1) * 0.6;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el));
                double[][] r = rotTo(d);
                chains.add(Holo.block(l, g.tag, c, "minecraft:iron_chain", 0xAAAAAA, Holo.m(r, 0.5, 9, 0.5, d.scale(4.5), true), 120, 0));
            }
            // 거대한 황금 열쇠 (고리 + 몸통 + 이빨)
            key = Holo.block(l, g.tag, c.add(0, 10, 6), "minecraft:gold_block", 0x55FFFF, Holo.m(Holo.I(), 0.6, 0.6, 5, Vec3.ZERO, true), 110, 0)
                    .key(10, Holo.m(Holo.I(), 0.6, 0.6, 5, new Vec3(0, -10, -4), true), 14)
                    .key(28, Holo.m(Holo.rotZ(Math.PI / 2), 0.6, 0.6, 5, new Vec3(0, -10, -4), true), 30);
            Holo.block(l, g.tag, c.add(0, 10, 6), "minecraft:gold_block", 0x55FFFF, Holo.m(Holo.I(), 2.2, 2.2, 0.5, new Vec3(0, 0, 2.6), true), 110, 0)
                    .key(10, Holo.m(Holo.I(), 2.2, 2.2, 0.5, new Vec3(0, -10, -1.4), true), 14)
                    .key(28, Holo.m(Holo.rotZ(Math.PI / 2), 2.2, 2.2, 0.5, new Vec3(0, -10, -1.4), true), 30);
            Holo.banner(l, g.tag, c.add(0, 7, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("∞ INFINITY KEY", 0x55FFFF, true) + "]}", 2, 100);
            Holo.sfx(l, c, "choir", 3f, 0.6f);
            Holo.sfx(l, c, "riser", 3f, 0.5f);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 2f, 0.5f);
        }
        static double[][] rotTo(Vec3 d) {   // y축을 d 로 돌리는 회전
            Vec3 y = new Vec3(0, 1, 0);
            Vec3 ax = y.cross(d);
            double s = ax.length(), co = y.dot(d);
            if (s < 1e-6) return Holo.I();
            ax = ax.scale(1 / s);
            double ang = Math.atan2(s, co), cs = Math.cos(ang), sn = Math.sin(ang), k = 1 - cs;
            return new double[][]{{cs + ax.x * ax.x * k, ax.x * ax.y * k - ax.z * sn, ax.x * ax.z * k + ax.y * sn},
                    {ax.y * ax.x * k + ax.z * sn, cs + ax.y * ax.y * k, ax.y * ax.z * k - ax.x * sn},
                    {ax.z * ax.x * k - ax.y * sn, ax.z * ax.y * k + ax.x * sn, cs + ax.z * ax.z * k}};
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t == 26) l.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 2f, 0.5f);
            if (t >= 30 && t < 30 + 12 * 4 && (t - 30) % 4 == 0) {   // 사슬이 하나씩 끊어짐
                int i = (t - 30) / 4;
                Holo.Elem e = chains.get(i);
                e.endIn(1);
                Vec3 at = c.add(new Vec3(Math.cos(i * Math.PI / 6), (i % 3 - 1) * 0.6, Math.sin(i * Math.PI / 6)).scale(4));
                Holo.shards(l, g.tag, at, "minecraft:iron_chain", 6, 3);
                l.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 2f, 0.6f + i * 0.08f);
                if (i % 3 == 2) com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(4, 0, 300, 0.8f));
            }
            if (t == 84) {
                p.addTag(UNSEAL);
                Holo.flare(l, g.tag, c, 12, 0x55FFFF, 12);
                Holo.shock(l, g.tag, c, 30, 0x55FFFF, 16);
                Holo.pillar(l, g.tag, c, 200, 4, 0x55FFFF, 4, 16, 14);
                g.cutIn(p, "∞ 봉인 해제", "숨겨진 필살기 5종 — /kill @e · 무한 큐브 · 명령의 신 · /reload · /execute ∞", 0x55FFFF, 1);
                GuardMeta.send(p, 9, "§b§l∞ 봉인 해제\n§f「…이게 내 진짜 명령이야.」\n§7Z/C 로 43번 이후 필살기 선택");
                GuardMeta.send(p, 4, "커맨드 수호의 봉인을 풀었다");
                Holo.sfx(l, c, "stinger", 3f, 0.7f);
                Holo.sfx(l, c, "boom", 3f, 0.5f);
                Titan.shake(l, c, 60, true);
            }
            return t >= 100;
        }
    }

    /** 42 「종말 명령」 /kill @e — 하늘이 검게 꺼지고 빛줄기가 적 하나하나를 지움. */
    static final class Apocalypse implements Fx {
        int t = 0; final Vec3 at; final List<LivingEntity> caught = new ArrayList<>();
        Apocalypse(Guard g, Vec3 to) {
            at = to;
            ServerLevel l = g.level;
            g.say("/kill @e[type=!player,distance=..64]", 0xFFFFFF, null, 0);
            caught.addAll(g.foesNear(g.pos, 64));
            caught.sort((a, b) -> Double.compare(a.distanceToSqr(g.pos), b.distanceToSqr(g.pos)));
            for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(g.pos) < 90) {
                com.tubilt.jjk.net.JjkNet.toPlayer(sp, new com.tubilt.jjk.net.Payloads.Screen(5, 0x000000, 5000, 0.85f));
                GuardMeta.send(sp, 9, "§f§l/kill @e\n§7대상 " + caught.size() + "개 · 이 세계에서 삭제합니다");
            }
            Holo.banner(l, g.tag, g.pos.add(0, 30, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("/kill @e", 0xFFFFFF, true) + "]}", 12, 100);
            for (LivingEntity e : caught) Holo.hitbox(l, g.tag, e, 0xFFFFFF, 100);
            Holo.sfx(l, g.pos, "deep", 3f, 0.4f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            int idx = (t - 30) / 2;
            if (t >= 30 && (t - 30) % 2 == 0 && idx < caught.size()) {
                LivingEntity e = caught.get(idx);
                if (e.isAlive()) {
                    Vec3 o = e.position();
                    Holo.pillar(l, g.tag, o, 80, 1.2, 0xFFFFFF, 1, 3, 6);
                    Holo.flare(l, g.tag, o.add(0, 1, 0), 2.5, 0xFFFFFF, 6);
                    if (e.getMaxHealth() <= 300) { e.invulnerableTime = 0; e.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
                    else g.hit(e, p, 500f);
                    l.playSound(null, o.x, o.y, o.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 1.6f);
                }
            }
            if (t == 32 + caught.size() * 2) { Holo.shock(l, g.tag, g.pos, 64, 0xFFFFFF, 18); Holo.sfx(l, g.pos, "boom", 3f, 0.4f); }
            return t > 40 + caught.size() * 2;
        }
    }

    /** 43 「무한 큐브」 — 반경 20 명령 블록 큐브 감옥이 돌며 안을 갈아버리고 붕괴. */
    static final class InfiniteCube implements Fx {
        int t = 0; final Vec3 at;
        InfiniteCube(Guard g, Vec3 to) {
            at = to.add(0, 10, 0);
            ServerLevel l = g.level;
            g.say("/fill ~-20 ~ ~-20 ~20 ~20 ~20 command_block hollow ×∞", 0xB45CFF, null, 0);
            String[] B = {"minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block"};
            for (int k = 0; k < 3; k++) {
                double s = 40 - k * 8;
                Holo.block(l, g.tag, at, B[k], k == 2 ? 0xB45CFF : 0x55FFFF, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), 230, 0)
                        .key(1 + k * 4, Holo.m(Holo.mul(Holo.rotX(0.4 * k), Holo.rotZ(0.3 * k)), s * 0.02, s, s * 0.02, Vec3.ZERO, true), 10).spin(2 + k * 2);
                Holo.ringT(l, g.tag, at, s / 2, s / 2 + 0.6, 64, 0.4, 0.05, k == 2 ? 0xB45CFF : 0x55FFFF, 8, 190, 10, Holo.rotX(0.5 * k), 3 + k, true);
            }
            Holo.circle(l, g.tag, to.add(0, 0.1, 0), 20, 0xB45CFF, 220, 0);
            Holo.sfx(l, at, "riser", 3f, 0.5f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            if (t < 200) {
                if (t % 10 == 0) for (LivingEntity e : g.foesNear(at, 22)) {
                    g.hit(e, p, 14f);
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 20, 3));
                    Holo.lightning(l, g.tag, at, e.position().add(0, 1, 0), 0xB45CFF, 0.08, 4);
                }
                if (t % 4 == 0) l.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 40, 14, 8, 14, 1.0);
                return false;
            }
            if (t == 200) {
                g.say("/fill … replace air  — 붕괴", 0xFF3040, null, 0);
                for (LivingEntity e : g.foesNear(at, 24)) { g.hit(e, p, 200f); Vec3 d = at.subtract(e.position()).normalize(); e.setDeltaMovement(d.scale(1.5)); e.hurtMarked = true; }
                Holo.explosion(l, g.tag, at, 14, 0xB45CFF);
                Holo.shards(l, g.tag, at, "minecraft:command_block", 60, 12);
                Holo.sfx(l, at, "boom", 3f, 0.4f);
                Titan.shake(l, at, 80, true);
            }
            return t >= 210;
        }
    }

    /** 44 「명령의 신 강림」 — 30블록 높이 명령 블록 거신이 광선을 휩쓸고 내리찍음. */
    static final class CommandGod implements Fx {
        int t = 0; final Vec3 at, base;
        CommandGod(Guard g, Vec3 to) {
            at = to;
            ServerLevel l = g.level;
            Vec3 f = to.subtract(g.pos).multiply(1, 0, 1);
            f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
            base = to.subtract(f.scale(22));
            g.say("/summon jjk:command_god ~ ~ ~ {Scale:30f}", 0xFFD84A, null, 0);
            // 몸통·머리·팔·후광
            Object[][] parts = {{"minecraft:command_block", 0, 12, 0, 8, 12, 5}, {"minecraft:repeating_command_block", 0, 22, 0, 6, 6, 6},
                    {"minecraft:chain_command_block", -7, 14, 0, 3, 12, 3}, {"minecraft:chain_command_block", 7, 14, 0, 3, 12, 3},
                    {"minecraft:command_block", -2.5, 3, 0, 3, 6, 3}, {"minecraft:command_block", 2.5, 3, 0, 3, 6, 3}};
            for (Object[] q : parts) {
                Vec3 off = new Vec3(((Number) q[1]).doubleValue(), ((Number) q[2]).doubleValue(), ((Number) q[3]).doubleValue());
                double sx = ((Number) q[4]).doubleValue(), sy = ((Number) q[5]).doubleValue(), sz = ((Number) q[6]).doubleValue();
                Holo.block(l, g.tag, base, (String) q[0], 0xFFD84A, Holo.m(Holo.I(), sx, 0.1, sz, off.add(0, -12, 0), true), 190, 0)
                        .key(1, Holo.m(Holo.I(), sx, sy, sz, off, true), 20);
            }
            Holo.ringT(l, g.tag, base.add(0, 22, -3.5), 6, 7, 48, 0.5, 0.05, 0xFFD84A, 20, 160, 10, Holo.I(), 2, true);
            Holo.pillar(l, g.tag, base, 120, 10, 0xFFD84A, 10, 160, 10);
            Holo.sfx(l, base, "choir", 3f, 0.5f);
            Titan.shake(l, base, 80, true);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            Vec3 eye = base.add(0, 22, 0);
            if (t >= 40 && t < 140 && t % 4 == 0) {   // 눈에서 휩쓰는 광선
                double a = (t - 40) / 100.0 * Math.PI * 2;
                Vec3 hitp = at.add(Math.cos(a) * 12, 0, Math.sin(a) * 12);
                Holo.beamGlow(l, g.tag, eye, hitp, 1.2, 0xFFD84A, 1, 2, 3);
                Holo.explosion(l, g.tag, hitp, 3, 0xFFD84A);
                for (LivingEntity e : g.foesNear(hitp, 4)) g.hit(e, p, 30f);
                if (t % 12 == 0) Holo.sfx(l, hitp, "laser", 2f, 0.8f);
            }
            if (t == 150) {
                g.say("/execute as @e[type=command_god] run smash", 0xFFD84A, null, 0);
                for (LivingEntity e : g.foesNear(at, 18)) g.hit(e, p, 250f);
                Holo.shock(l, g.tag, at, 40, 0xFFD84A, 16);
                Holo.explosion(l, g.tag, at, 12, 0xFFD84A);
                Holo.shards(l, g.tag, at, groundName(l, at.add(0, -0.5, 0)), 60, 12);
                Holo.sfx(l, at, "boom", 3f, 0.3f);
                Titan.shake(l, at, 100, true);
            }
            return t >= 170;
        }
    }

    /** 45 「/reload」 — 세계를 다시 읽어 들임: 주인 완전 회복·쿨타임 초기화, 적은 데이터 초기화. */
    static final class Reload implements Fx {
        int t = 0;
        Reload(Guard g, ServerPlayer p) {
            ServerLevel l = g.level;
            g.say("/reload", 0x55FF55, null, 0);
            for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(g.pos) < 60) {
                GuardMeta.send(sp, 9, "§a§l/reload\n§7Reloading all data packs… " + "§f[■■■■■■■■■■] 100%");
                com.tubilt.jjk.net.JjkNet.toPlayer(sp, new com.tubilt.jjk.net.Payloads.Screen(4, 0, 1500, 1.5f));
            }
            Holo.sfx(l, g.pos, "glitch", 3f, 0.8f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t != 30) return false;
            ServerLevel l = g.level;
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 1200, 4));
            g.ultCd = 0; g.flyCd = 0; g.shieldCd = 0; g.golemCd = 0; g.reviveCd = 0; g.absCd = 0; g.domainCd = 0;
            for (LivingEntity e : g.foesNear(g.pos, 40)) {
                e.removeAllEffects();
                g.hit(e, p, 150f);
                Holo.glow(l, g.tag, e.position().add(0, e.getBbHeight() / 2, 0), 1.6, 0x55FF55, 1, 2, 5);
                Holo.banner(l, g.tag, e.position().add(0, e.getBbHeight() + 1, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("data reset", 0x55FF55, true) + "]}", 0.8, 24);
            }
            Holo.shock(l, g.tag, g.pos, 40, 0x55FF55, 14);
            Holo.flare(l, g.tag, g.pos, 8, 0xFFFFFF, 8);
            Holo.sfx(l, g.pos, "stinger", 3f, 1.2f);
            p.sendSystemMessage(Component.literal("§a<커맨드 수호> §fReload complete. 쿨타임 전부 초기화."));
            return true;
        }
    }

    /** 46 「/execute ∞」 — 수호의 명령을 연속으로 쏟아붓고 블랙홀·메테오로 마무리. */
    static final class InfinityExec implements Fx {
        int t = 0; final Vec3 at;
        InfinityExec(Guard g, Vec3 to) {
            at = to;
            g.say("/execute repeat ∞ run function guard:all", 0x55FFFF, null, 0);
            Holo.ringT(g.level, g.tag, to.add(0, 0.2, 0), 10, 11, 64, 0.5, 0.05, 0x55FFFF, 6, 120, 10, Holo.I(), 4, true);
            Holo.sfx(g.level, to, "riser", 3f, 0.6f);
        }
        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            if (t % 8 == 0 && t <= 96) {
                int k = t / 8;
                Vec3 q = at.add(Math.cos(k * 1.3) * 7, 0, Math.sin(k * 1.3) * 7);
                switch (k % 6) {
                    case 0 -> g.fx.add(new StickyTnt(g, q));
                    case 1 -> g.fx.add(new CubeCrush(g, q));
                    case 2 -> g.fx.add(new DeleteCode(g, q));
                    case 3 -> g.fx.add(new GravityCollapse(g, q));
                    case 4 -> g.fx.add(new ScoreExecute(g, q));
                    default -> g.fx.add(new Meteor(g, q, 4, 0, false));
                }
                com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Screen(4, 0, 200, 0.6f));
            }
            if (t == 104) g.fx.add(new HoleFlip(g, at));
            if (t == 120) { g.fx.add(new Meteor(g, at, 13, 0, true)); g.say("/execute ∞ — return 2147483647", 0xFFD84A, null, 0); }
            return t >= 124;
        }
    }

    static final class CommandPlanet implements Fx {
        int t = 0; final Vec3 to, rift; Vec3 pp;
        final List<Holo.Elem> body = new ArrayList<>();
        final List<Vec3> anchors = new ArrayList<>();
        int slice = Integer.MIN_VALUE;
        // 클라이언트 PlanetCine 과 같은 시간축 (행성·우주·사슬·화구는 클라이언트 셰이더가 그림)
        static final int RIFT = 60, ASCEND = 120, SPACE = 150, REVEAL = 215, BOXLOCK = 240, LOCK = 270, HAUL = 320, ORBIT = 420,
                DESCENT = 470, ENTRY = 590, HIT = 680, END = 900;
        static final double GR = 700, FAR = 30000, NEARALT = 2600, KILL_R = 80, CR = 64, CD = 32;
        static double alt(double t) {
            if (t < HAUL + 10) return FAR;
            if (t < DESCENT) { double u = (t - HAUL - 10) / (DESCENT - HAUL - 10); return FAR + (NEARALT - FAR) * u * u; }
            if (t < HIT) { double u = (t - DESCENT) / (HIT - DESCENT); return NEARALT - (NEARALT - GR) * Math.pow(u, 1.8); }
            double u = Math.min(1, (t - HIT) / 45.0);
            return GR * (1 - 0.75 * (1 - (1 - u) * (1 - u)));
        }
        static void wide(Holo.Elem e) { e.merge("{view_range:24f,width:400f,height:400f}"); }

        CommandPlanet(Guard g, ServerPlayer p, Vec3 target) {
            ServerLevel l = g.level;
            to = target; rift = target.add(0, 180, 0); pp = target.add(0, FAR, 0);
            g.say("/locate planet kepler-442b", 0xFF8A50, null, 0);
            // 3D 행성·우주 돔은 클라이언트가 직접 렌더 (PlanetRender): 시전자는 시네마틱 카메라, 주변은 연출만
            com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.Planet(p.getX(), p.getEyeY(), p.getZ(), to.x, to.y, to.z));
            for (ServerPlayer sp : l.players()) if (sp != p && sp.position().distanceTo(to) < 320)
                com.tubilt.jjk.net.JjkNet.toPlayer(sp, new com.tubilt.jjk.net.Payloads.Planet(Double.NaN, 0, 0, to.x, to.y, to.z));
            // 견인 앵커 4곳 (목표 둘레)
            for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2 + 0.4; anchors.add(to.add(Math.cos(a) * 40, 0.3, Math.sin(a) * 40)); }
            for (Vec3 a : anchors) {
                Holo.circle(l, g.tag, a, 6, 0x55FFFF, HIT, 0);
                Holo.block(l, g.tag, a, "minecraft:command_block", 0x55FFFF, Holo.m(Holo.I(), 3, 3, 3, new Vec3(0, 1.5, 0), true), HIT, 0).spin(4);
            }
            Holo.screen(l, to, 200, 5, 0x02030A, 46000, 0.85f);
            Holo.sfx(l, to, "deep", 3f, 0.35f); Holo.sfx(l, to, "riser", 3f, 0.3f); Holo.sfx(l, to, "choir", 3f, 0.4f);
            for (ServerPlayer sp : l.players()) if (sp != p && sp.position().distanceTo(to) < 200)
                com.tubilt.jjk.net.JjkNet.toPlayer(sp, new com.tubilt.jjk.net.Payloads.CutIn("/tp 행성 낙하", "KEPLER-442b ─ 우주에서 끌어내리다", 0xFF8A50, 1));
        }

        public boolean tick(Guard g, ServerPlayer p) {
            t++;
            ServerLevel l = g.level;
            java.util.Random r = new java.util.Random(t * 7919L);
            pp = to.add(Math.sin(t * 0.5) * (t > DESCENT && t < HIT ? 0.6 : 0), alt(t), 0);
            boolean low = alt(t) < 2500;   // 행성이 낮게 내려왔을 때만 월드 연출 (너무 높으면 안 보임)
            if (t % 20 == 1 && t < END) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 40, 4, false, false));   // 긴 시네마틱 동안 시전자 보호
            if (t == RIFT) { Holo.sfx(l, rift, "shatter", 3f, 0.4f); Holo.sfx(l, rift, "glitch", 3f, 0.5f); Titan.shake(l, to, 200, true);
                g.say("/fill ~ ~180 ~ ~ ~200 ~ structure_void", 0x55FFFF, null, 0); }
            if (t == 16) Holo.sfx(l, to, "data", 2f, 0.6f);
            if (t > RIFT && t < DESCENT && t % 2 == 0) l.sendParticles(ParticleTypes.END_ROD, rift.x, rift.y - 3, rift.z, 30, 60, 3, 60, 0.02);
            if (t == ASCEND) { Holo.sfx(l, rift, "whoosh", 3f, 0.5f); Holo.sfx(l, rift, "riser", 3f, 0.6f); }
            if (t == SPACE) { Holo.sfx(l, to, "stinger", 3f, 0.5f); Holo.sfx(l, to, "deep", 3f, 0.4f); }
            if (t == SPACE + 8) { Holo.sfx(l, to, "whoosh", 3f, 0.3f); Holo.sfx(l, to, "riser", 3f, 0.8f); }
            if (t == REVEAL) { Holo.sfx(l, to, "choir", 3f, 0.3f); Holo.sfx(l, to, "deep", 3f, 0.25f); }
            if (t == BOXLOCK) { g.say("/fill hitbox @e[type=planet]", 0x55FFFF, null, 0); Holo.sfx(l, to, "plate", 3f, 0.6f); Holo.sfx(l, to, "data", 3f, 0.8f); }
            if (t == LOCK) { g.say("/ride @e[type=planet] mount @s", 0xB45CFF, null, 0); Holo.sfx(l, to, "charge", 3f, 0.5f); }
            if (t == 292) { Holo.sfx(l, to, "impact", 3f, 0.5f); Holo.sfx(l, to, "drum", 3f, 0.4f); }
            if (t > LOCK && t < ENTRY && t % 15 == 0) Holo.sfx(l, to, "laser", 2f, 0.4f + (t - LOCK) * 0.002f);
            if (t == HAUL) {   // 시전자 머리 위 거대 명령 블록: 4 앵커로 명령을 내려보냄
                Vec3 top = p.position().add(0, 34, 0);
                Holo.block(l, g.tag, top, "minecraft:command_block", 0x55FFFF, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), HIT - HAUL + 20, 0)
                        .key(1, Holo.m(Holo.I(), 14, 14, 14, Vec3.ZERO, true), 12).key(HIT - HAUL, Holo.m(Holo.I(), 0.1, 0.1, 0.1, Vec3.ZERO, true), 10).spin(1.5);
                Holo.ring(l, g.tag, top, 11, 12, 48, 0.6, 0.05, 0x55FFFF, 10, HIT - HAUL, 10, 0, 2, true);
                for (Vec3 a : anchors) Holo.beamGlow(l, g.tag, top, a.add(0, 1.5, 0), 0.6, 0x55FFFF, 8, HIT - HAUL - 10, 10);
                Holo.pillar(l, g.tag, top, 300, 3, 0x55FFFF, 8, HIT - HAUL - 10, 10);
            }
            if (t == HAUL) { g.say("/tp @e[type=planet,limit=1] @s", 0x55FFFF, null, 0); Holo.sfx(l, to, "boom", 3f, 0.3f); Holo.sfx(l, to, "slash", 3f, 0.4f); Titan.shake(l, to, 200, true); }
            if (t == ORBIT) { g.say("/data merge entity @e[type=planet] {Motion:[0d,-31d,0d]}", 0xFF8A50, null, 0); Holo.sfx(l, to, "thunder", 3f, 0.3f); Holo.sfx(l, to, "riser", 3f, 0.4f); }
            if (t == HIT - 6) { g.say("/function jjk:planet/impact", 0xFFFFFF, null, 0); Holo.sfx(l, to, "glitch", 3f, 0.5f); }
            if (t == ENTRY) for (int i = 0; i < 24; i++) {   // 처형 구역 경계에 명령 블록 기둥
                double a = i * Math.PI / 12;
                Vec3 q = to.add(Math.cos(a) * KILL_R, 0, Math.sin(a) * KILL_R);
                Holo.block(l, g.tag, q, i % 3 == 0 ? "minecraft:repeating_command_block" : i % 3 == 1 ? "minecraft:chain_command_block" : "minecraft:command_block", 0xFF3040,
                        Holo.m(Holo.I(), 0.01, 0.01, 0.01, new Vec3(0, 1.5, 0), true), HIT - ENTRY + 20, 0)
                        .key(i, Holo.m(Holo.I(), 3, 3, 3, new Vec3(0, 1.5, 0), true), 6).spin(6);
            }
            // ── 대기권 돌입
            if (t == DESCENT) {
                g.say("/attribute @planet gravity base set 9.8", 0xFF3020, null, 0);
                Holo.circle(l, g.tag, to, KILL_R, 0xFF3020, END - t, 0);
                Holo.circle(l, g.tag, to.add(0, 0.2, 0), KILL_R * 0.5, 0xFFB040, END - t, 0);
                Holo.screen(l, to, 220, 5, 0x5A1406, 14000, 0.7f);
                Holo.sfx(l, to, "siren", 3f, 0.5f);
            }
            if (t > DESCENT && t < HIT) {
                if (low && t % 2 == 0) {   // 불타는 파편 비 (행성 아랫면에서)
                    Vec3 s0 = pp.add(r.nextGaussian() * GR * 0.3, -GR * 0.95, r.nextGaussian() * GR * 0.3);
                    Vec3 g0 = to.add(r.nextGaussian() * KILL_R * 0.7, 0, r.nextGaussian() * KILL_R * 0.7);
                    Holo.beamGlow(l, g.tag, s0, g0, 2.4, 0xFF7030, 1, 1, 3);
                    final Vec3 gg = g0;
                    g.later(4, () -> { Holo.explosion(l, g.tag, gg, 3.5, 0xFF6020); for (LivingEntity e : g.foesNear(gg, 5)) { g.hit(e, p, 80f); e.igniteForSeconds(8); }
                        if (MechManager.griefOn(l.getServer())) l.explode(p, gg.x, gg.y, gg.z, 4f, true, Level.ExplosionInteraction.TNT); });
                }
                if (t % 10 == 0) { Holo.sfx(l, to, "thunder", 3f, 0.35f); Titan.shake(l, to, 220, t > ENTRY); }
                if (t % 8 == 0) l.playSound(null, to.x, to.y + 30, to.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 10f, 0.5f);
                for (LivingEntity e : g.foesNear(to, KILL_R)) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 10, 6));
            }
            if (t == ENTRY) { g.say("/execute at @planet run kill @e[distance=..60]", 0xFFFFFF, null, 0); Holo.sfx(l, to, "riser", 3f, 1.3f); Holo.sfx(l, to, "whoosh", 3f, 0.2f); }
            // ── 충돌
            if (t == HIT) {
                for (Holo.Elem e : body) e.endIn(2);
                Vec3 c = to.add(0, 2, 0);
                Holo.explosion(l, g.tag, to, 24, 0xFF6020);
                Holo.shockTex(l, g.tag, c, 260, 0xFF8030, 30);
                Holo.shockTex(l, g.tag, c.add(0, 1, 0), 180, 0xFFFFFF, 20);
                Holo.ringT(l, g.tag, c, 6, 200, 72, 6, 1, 0xFFB060, 14, 4, 20, Holo.I(), 0, true);
                Holo.decal(l, g.tag, to.add(0, 0.12, 0), "crack", 0xFF4010, 10, KILL_R * 3, 3, 80, 20, 0, Holo.I());
                Holo.pillar(l, g.tag, to, 300, 40, 0xFF8030, 2, 20, 24);
                Holo.light(l, c, 80);
                Holo.shards(l, g.tag, c, groundName(l, to.add(0, -0.5, 0)), 60, 22);
                Holo.shards(l, g.tag, c, "minecraft:magma_block", 40, 20);
                Holo.embers(l, g.tag, to, 120, KILL_R * 0.7, 0xFF6020, 100);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(to, to).inflate(KILL_R), x -> x != p && x.isAlive() && !(x instanceof Player))) {
                    if (e.position().distanceTo(to) > KILL_R) continue;
                    g.hit(e, p, 99999f);
                    if (e.isAlive()) { e.invulnerableTime = 0; e.hurtServer(l, l.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
                }
                // 섬광·잔상·임팩트 프레임은 클라이언트 후처리(PlanetShader)가 그림
                Titan.shake(l, to, 300, true);
                Holo.sfx(l, to, "boom", 3f, 0.2f); Holo.sfx(l, to, "boom", 3f, 0.35f); Holo.sfx(l, to, "impact", 3f, 0.25f); Holo.sfx(l, to, "choir", 3f, 0.35f);
                l.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 30f, 0.25f);
                l.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 30f, 0.25f);
                Holo.banner(l, g.tag, to.add(0, 70, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("PLANET IMPACT", 0xFFFFFF, true) + "]}", 40, 100);
                Holo.banner(l, g.tag, to.add(0, 52, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("KEPLER-442b · 2.1e24 kg · /tp ✔", 0xFF8A50, true) + "]}", 14, 98);
                slice = (int) -CR;
            }
            // ── 분화구: 타원체(반경 48·깊이 26)를 틱당 나눠 지움 + 가장자리 마그마
            if (slice != Integer.MIN_VALUE && slice <= CR) {
                int budget = 50000;
                while (budget > 0 && slice <= CR) budget -= craterSlice(l, slice++);
            }
            if (t == HIT + 6 || t == HIT + 16 || t == HIT + 28) {        // 화염 고리 3파
                int k = (t - HIT) / 10;
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8 + k * 0.3, rr = 30 + k * 26;
                    Vec3 q = to.add(Math.cos(a) * rr, 1, Math.sin(a) * rr);
                    Holo.explosion(l, g.tag, q, 7, 0xFF6020);
                    for (LivingEntity e : g.foesNear(q, 10)) g.hit(e, p, 500f);
                }
                Holo.shockTex(l, g.tag, to.add(0, 1, 0), 120 + k * 70, 0xFF8030, 14);
                Holo.sfx(l, to, "boom", 3f, 0.4f + k * 0.1f);
                Titan.shake(l, to, 260, true);
            }
            if (t == HIT + 40) { Holo.shockTex(l, g.tag, to.add(0, 2, 0), 400, 0x806050, 40); Holo.sfx(l, to, "boom", 3f, 0.2f); Titan.shake(l, to, 300, true); }
            if (t > HIT && t < END && t % 2 == 0) {   // 버섯구름
                double h = Math.min(140, (t - HIT) * 2.0);
                Holo.sprite(l, g.tag, to.add(r.nextGaussian() * 8, h * 0.55, r.nextGaussian() * 8), "smoke" + (t % 2), 0x4A3028, "center", Holo.bb(36, t, Vec3.ZERO), 70, 0)
                        .key(1, Holo.bb(56, t + 1, Vec3.ZERO), 68).merge("{view_range:24f,width:120f,height:120f}");
                if (t > HIT + 20) {
                    double a = r.nextDouble() * Math.PI * 2;
                    Holo.sprite(l, g.tag, to.add(Math.cos(a) * 40, h + 10, Math.sin(a) * 40), "smoke" + (t / 2 % 2), 0x5A3A30, "center", Holo.bb(48, t, Vec3.ZERO), 70, 0)
                            .key(1, Holo.bb(72, t + 1, Vec3.ZERO), 68).merge("{view_range:24f,width:150f,height:150f}");
                }
                l.sendParticles(ParticleTypes.LAVA, to.x, to.y + 1, to.z, 16, CR * 0.6, 1, CR * 0.6, 0);
                l.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, to.x, to.y + 2, to.z, 12, CR * 0.7, 3, CR * 0.7, 0.05);
            }
            return t >= END && (slice == Integer.MIN_VALUE || slice > CR);
        }

        /** x = 중심 + dx 인 분화구 단면을 지움 (가장자리는 마그마·불). 방문 칸 수 반환. */
        int craterSlice(ServerLevel l, int dx) {
            int cx = (int) Math.floor(to.x), cy = (int) Math.floor(to.y), cz = (int) Math.floor(to.z), n = 0;
            double rx2 = 1 - (dx * dx) / (CR * CR);
            if (rx2 <= 0) return 1;
            int rz = (int) (CR * Math.sqrt(rx2));
            net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int dz = -rz; dz <= rz; dz++) {
                double q = 1 - (dx * dx + dz * dz) / (CR * CR);
                if (q <= 0) continue;
                int depth = (int) (CD * Math.sqrt(q)), up = (int) (16 * Math.sqrt(q)) + 6;
                for (int dy = -depth; dy <= up; dy++) {
                    int y = cy + dy;
                    if (y < l.getMinY() || y > l.getMaxY()) continue;
                    n++;
                    m.set(cx + dx, y, cz + dz);
                    var st = l.getBlockState(m);
                    if (st.isAir() || st.getDestroySpeed(l, m) < 0) continue;
                    boolean rim = dy == -depth && (q < 0.25 || l.random.nextInt(9) == 0);
                    l.setBlock(m, rim ? net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                }
                if (q < 0.12 && l.random.nextInt(5) == 0) {
                    m.set(cx + dx, cy - depth + 1, cz + dz);
                    if (l.getBlockState(m).isAir()) l.setBlock(m, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 2);
                }
            }
            return Math.max(1, n);
        }
    }
}
