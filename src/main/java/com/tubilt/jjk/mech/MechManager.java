package com.tubilt.jjk.mech;

import com.tubilt.jjk.JjkMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/** 개발자 토큰3 → 탈것 소환/탑승/귀환, 미사일 관리. */
public final class MechManager {
    private MechManager() {}

    public static final String TOKEN_NAME = "개발자 토큰3";
    public static final String TOKEN4_NAME = "개발자 토큰4";
    public static final String FINAL_NAME = "개발자의 최종토큰";
    public static final String MACHINE_NAME = "개발자의 기계 토큰";
    static final Map<UUID, UUID> OWNED4 = new HashMap<>();   // 주인 → 개발자의 기계
    static final Map<UUID, UUID> OWNED3 = new HashMap<>();   // 주인 → 거인봇
    static final Set<UUID> TITAN_RIDERS = new HashSet<>();
    static final Map<UUID, UUID> OWNED2 = new HashMap<>();   // 주인 → 탑승물2
    static final List<Shell> SHELLS = new ArrayList<>();
    static final Map<UUID, UUID> OWNED = new HashMap<>();     // 주인 → 탈것
    static final List<Missile> MISSILES = new ArrayList<>();

    public static void init() {
        // 금 조각 "개발자 토큰3" 우클릭
        PlayerInteractEvent.RightClickItem.BUS.addListener((java.util.function.Predicate<PlayerInteractEvent.RightClickItem>) e -> {
            ItemStack st = e.getItemStack();
            // 무료훈수 후유증: 술식 아이템 사용 불가
            if (e.getEntity().getTags().contains("jjk.hunsu") && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals(JjkMod.MOD_ID)) {
                e.getEntity().displayClientMessage(Component.literal("§c훈수의 열량이 뇌를 메워 술식을 쓸 수 없다"), true);
                e.setCancellationResult(InteractionResult.FAIL);
                return true;
            }
            if (LegendSword.isLight(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) LegendSword.useLight(sp, sl, st);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (LegendSword.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) LegendSword.use(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevGesture.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (DevGlove.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (Abilities.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (e.getEntity() instanceof ServerPlayer ag && Abilities.gate(ag, st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (JacobLadder.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (ReverseTech.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (Spirits.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (Chars.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (BloodManip.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (Projection.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (Koming.onUse(e.getEntity(), st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (e.getEntity() instanceof ServerPlayer gp && gp.level() instanceof ServerLevel gl && CommandGuard.tryFuse(gp, gl, st)) {
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (e.getEntity() instanceof ServerPlayer fp && CommandInker.tryFuse(fp)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (e.getEntity() instanceof ServerPlayer ip && CommandInker.onUse(ip, st)) { e.setCancellationResult(InteractionResult.SUCCESS); return true; }
            if (CommandGuard.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) CommandGuard.use(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (CtrlZ.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) CtrlZ.use(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (HyungSword.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) HyungSword.use(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            String bugK = st.is(com.tubilt.jjk.JjkItems.BUG_EGG_V.get()) ? "v" : st.is(com.tubilt.jjk.JjkItems.BUG_EGG_P.get()) ? "p" : st.is(com.tubilt.jjk.JjkItems.BUG_EGG_E.get()) ? "e" : null;
            if (bugK != null || st.is(com.tubilt.jjk.JjkItems.PURIFIED_EGG.get())) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    var eye = sp.getEyePosition();
                    var hit = sl.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(sp.getLookAngle().scale(6)), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
                    net.minecraft.world.phys.Vec3 at = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(3)) : hit.getLocation();
                    if (bugK != null) BugMobs.spawnBug(sl, at, bugK); else BugMobs.spawnPurified(sl, at);
                    if (!sp.isCreative()) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (st.is(com.tubilt.jjk.JjkItems.INFINITY_KEY.get())) {
                if (e.getEntity() instanceof ServerPlayer sp) CommandGuard.useKey(sp, st);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (GoldenClasp.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) GoldenClasp.use(sp, sl, st);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (st.is(com.tubilt.jjk.JjkItems.ARCHMAGE_BOOK.get())) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    net.minecraft.world.phys.Vec3 at = sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(10));
                    int top = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z));
                    Bosses.archMage(sl, new net.minecraft.world.phys.Vec3(at.x, Math.max(top, sp.getY()), at.z));
                    if (!sp.isCreative()) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevBoss.isMasterKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = DevBoss.running();
                    DevBoss.summon(sp, sl, true);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (KraluBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = KraluBoss.running();
                    KraluBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DonquixoteBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = DonquixoteBoss.running();
                    DonquixoteBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (HackerBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = HackerBoss.running();
                    HackerBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DoctorBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = DoctorBoss.running();
                    DoctorBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (SukunaBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = SukunaBoss.running();
                    SukunaBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (KomingBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = KomingBoss.running();
                    KomingBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (GojoBoss.isKey(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    boolean was = GojoBoss.running();
                    GojoBoss.summon(sp, sl);
                    if (!was && !sp.getAbilities().instabuild) st.shrink(1);
                }
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevBoss.isEye(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl && !DevBoss.running()) {
                    DevBoss.summon(sp, sl);
                    if (!sp.getAbilities().instabuild) st.shrink(1);
                } else if (e.getEntity() instanceof ServerPlayer sp) DevBoss.summon(sp, (ServerLevel) sp.level());
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevHand.kind(st) >= 0) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) DevHand.use(sp, sl, DevHand.kind(st));
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevWorm.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) DevWorm.use(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DimensionBoss.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl && DimensionBoss.place(sp, sl) && !sp.getAbilities().instabuild) st.shrink(1);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevPhone.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) DevPhone.use(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevLever.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) DevLever.flip(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevButton.is(st) && e.getEntity() instanceof ServerPlayer mp && mp.isShiftKeyDown() && tryFuseMachine(mp)) {
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (isMachineToken(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) useMachine(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (DevButton.is(st)) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) DevButton.press(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            // 토큰3 + 토큰4 를 양손에 들고 우클릭 → 개발자의 최종토큰
            if (e.getEntity() instanceof ServerPlayer fp && isToken3(fp.getMainHandItem()) && isToken4(fp.getOffhandItem())
                    || e.getEntity() instanceof ServerPlayer fp2 && isToken4(fp2.getMainHandItem()) && isToken3(fp2.getOffhandItem())) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) fuse(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (com.tubilt.jjk.JjkItems.is(st, net.minecraft.world.item.Items.NETHER_STAR) && FINAL_NAME.equals(st.getHoverName().getString())) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) useFinal(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (com.tubilt.jjk.JjkItems.is(st, net.minecraft.world.item.Items.NETHERITE_SCRAP) && TOKEN4_NAME.equals(st.getHoverName().getString())) {
                if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) useToken4(sp, sl);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (!com.tubilt.jjk.JjkItems.is(st, net.minecraft.world.item.Items.GOLD_NUGGET) || !TOKEN_NAME.equals(st.getHoverName().getString())) return false;
            if (e.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) useToken(sp, sl);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return true;   // 취소 → 바닐라/데이터팩 사용 처리 막음
        });
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) { tick(server); Koming.tick(server); Projection.tick(server); BloodManip.tick(server); Chars.tick(server); DevGesture.tick(server); Spirits.tick(server); ReverseTech.tick(server); JacobLadder.tick(server); DevGlove.tick(server); CommandInker.tick(server); GojoFx.tick(server); }
        });
        // 탑승자 무적 (/kill 처럼 무적 무시 피해는 통과) + 탈것 화염 피해 무시
        LivingAttackEvent.BUS.addListener((java.util.function.Predicate<LivingAttackEvent>) e -> {
            LivingEntity le = e.getEntity();
            if (le instanceof net.minecraft.world.entity.player.Player && le.getTags().contains("jjk.cine")) return true;   // 컷신 중 무적
            if (BossPuppet.capHit(le, e.getSource(), e.getAmount())) return true;   // 보스가 쓴 원래 술식: 한 방 상한
            if (Bosses.onHurt(le, e.getSource(), e.getAmount())) return true;       // 보스 히트박스: 가상 체력으로 처리
            BugMobs.onHurt(le);
            if (le instanceof net.minecraft.world.entity.player.Player gp0 && CommandGuard.blocksProjectile(gp0, e.getSource())) return true;   // 커맨드 수호: 투사체 차단
            CtrlZ.onDamage(le, e.getSource().getEntity(), e.getAmount());
            if (CodeArmor.dodge(le, e.getSource())) return true;
            if (GojoBlindfold.infinity(le, e.getSource(), e.getAmount())) return true;   // 고죠의 안대: 무하한   // 코드 갑옷: 낙하 무효 · 25% 회피
            if (e.getSource().getEntity() instanceof ServerPlayer hp0 && e.getSource().getDirectEntity() == hp0 && HyungSword.is(hp0.getMainHandItem()) && le != hp0) HyungSword.onHit(hp0, le);
            if (e.getSource().getEntity() instanceof ServerPlayer sw && sw.getMainHandItem().is(com.tubilt.jjk.JjkItems.DEV_SWORD.get()) && sw.level() instanceof ServerLevel swl) {
                net.minecraft.world.phys.Vec3 hc = le.position().add(0, le.getBbHeight() / 2, 0);   // 개발자 검 타격 연출
                Holo.flare(swl, "jjk.devsword", hc, 2.5, 0xB45CFF, 6);
                Holo.banner(swl, "jjk.devsword", hc.add(0, le.getBbHeight() / 2 + 1, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("-999999", 0xFF55FF, true) + "]}", 1.2, 20);
                swl.playSound(null, hc.x, hc.y, hc.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 1.4f);
            }
            if (DevLever.intercept(le, e.getSource(), e.getAmount())) return true;   // 개발자의 레버: 피해를 로그로
            if (!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && DevPhone.absorb(le, e.getSource(), e.getAmount())) return true;   // 핸드폰: 매직 쉴드
            if (le instanceof ServerPlayer pp && Projection.dodge(pp, e.getSource())) return true;   // 투사주법: 가속 회피
            if (le instanceof ServerPlayer kp && Koming.blockDamage(kp, e.getSource(), e.getAmount())) return true;   // 코밍: 급식한·전투 예측
            if (le instanceof net.minecraft.world.entity.player.Player hp && e.getSource().getEntity() instanceof LivingEntity atk && atk != hp)
                CommandGuard.onHurtBy(hp, atk);
            if (le instanceof net.minecraft.world.entity.player.Player pl && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && CommandGuard.onHurt(pl, e.getAmount())) return true;   // 커맨드 수호 방어막
            // 커맨드 수호 주인이 근접 공격하면 후속타
            if (e.getSource().getDirectEntity() instanceof ServerPlayer owner && owner.swinging
                    && e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) && le != owner)
                CommandGuard.onOwnerHit(owner, le);
            if (le.getVehicle() instanceof DevVehicle && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            return le instanceof DevVehicle && e.getSource().is(DamageTypeTags.IS_FIRE);
        });
        // 커맨드 수호 부활: 주인의 죽음을 취소
        net.minecraftforge.event.entity.living.LivingDeathEvent.BUS.addListener((java.util.function.Predicate<net.minecraftforge.event.entity.living.LivingDeathEvent>) e -> { BugMobs.onDeath(e.getEntity()); if (e.getSource().getEntity() instanceof ServerPlayer kp && HyungSword.is(kp.getMainHandItem()) && e.getEntity() != kp) HyungSword.onKill(kp, e.getEntity()); return false; });
        net.minecraftforge.event.entity.EntityJoinLevelEvent.BUS.addListener((java.util.function.Predicate<net.minecraftforge.event.entity.EntityJoinLevelEvent>) e -> { try { BugMobs.onJoin(e.getEntity(), false); Spirits.onJoin(e.getEntity()); } catch (Exception ignored) { } return false; });
        net.minecraftforge.event.entity.living.LivingDeathEvent.BUS.addListener((java.util.function.Predicate<net.minecraftforge.event.entity.living.LivingDeathEvent>) e ->
                e.getEntity() instanceof net.minecraft.world.entity.player.Player pl && !pl.isSpectator() && CommandGuard.onDeath(pl));
        ServerStoppingEvent.BUS.addListener(e -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (Missile m : MISSILES) m.discard(server);
                for (ServerLevel lv : server.getAllLevels()) cmd(server, lv, "kill @e[tag=jjk.laserfx]");
                LaserFx.CACHE.clear();
            }
            MISSILES.clear();
            SHELLS.clear();
            DevButton.stopAll();
            DevLever.stopAll();
            DevPhone.stopAll();
            DevHand.stopAll();
            DevWorm.stopAll();
            GoldenClasp.stopAll(server);
            Bosses.stopAll();
            CommandGuard.stopAll();
            Koming.stopAll();
            Projection.stopAll();
            BloodManip.stopAll();
            Chars.stopAll();
            Spirits.stopAll();
            ReverseTech.stopAll();
            JacobLadder.stopAll();
            DevGlove.stopAll();
            DevGesture.stopAll();
            CommandInker.stopAll();
            GojoFx.stopAll();
            Abilities.stopAll();
            BossPuppet.stopAll();
            GojoBlindfold.stopAll();
            if (server != null) Holo.stopAll(server);
            else Holo.ACTIVE.clear();
            for (Titan.Bomb b : Titan.BOMBS) b.discard();
            Titan.BOMBS.clear();
            OWNED3.clear();
            OWNED4.clear();
            for (Machine.Drone d : Machine.DRONES) d.discard();
            Machine.DRONES.clear();
            OWNED.clear();
            OWNED2.clear();
        });
    }

    static void useToken(ServerPlayer p, ServerLevel level) {
        if (p.getVehicle() instanceof DevMech riding) {              // 타고 있으면 돌려보내기
            p.stopRiding();
            poof(level, riding.position());
            riding.discard();
            OWNED.remove(p.getUUID());
            p.displayClientMessage(Component.literal("§7개발자의 귀여운 탑승물이 돌아갔다"), true);
            return;
        }
        UUID old = OWNED.get(p.getUUID());
        if (old != null && level.getEntity(old) instanceof DevMech mine && mine.isAlive()) {
            if (mine.distanceTo(p) < 24) { p.startRiding(mine); return; }
            mine.discard();
        }
        double r = Math.toRadians(p.getYRot());
        double x = p.getX() - Math.sin(r) * 5, z = p.getZ() + Math.cos(r) * 5;
        DevMech mech = MechEntities.DEV_MECH.get().create(level, EntitySpawnReason.TRIGGERED);
        if (mech == null) return;
        mech.snapTo(x, p.getY() + 0.5, z, p.getYRot(), 0);
        mech.setYBodyRot(p.getYRot());
        mech.setYHeadRot(p.getYRot());
        level.addFreshEntity(mech);
        OWNED.put(p.getUUID(), mech.getUUID());
        level.playSound(null, x, p.getY(), z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 2f, 0.5f);
        level.playSound(null, x, p.getY(), z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 2f, 0.5f);
        poof(level, mech.position());
        p.startRiding(mech);
        p.displayClientMessage(Component.literal("§b개발자의 귀여운 탑승물 §7— [C] 미니건 / [Z] 미사일 / [X] 레이저 / [G] 대지 분쇄 / 웅크리기 하차"), true);
    }

    static boolean isToken3(ItemStack s) { return com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.GOLD_NUGGET) && TOKEN_NAME.equals(s.getHoverName().getString()); }
    static boolean isToken4(ItemStack s) { return com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.NETHERITE_SCRAP) && TOKEN4_NAME.equals(s.getHoverName().getString()); }

    /** 최종토큰 아이템. */
    public static ItemStack finalToken() {
        ItemStack st = new ItemStack(com.tubilt.jjk.JjkItems.DEV_TOKEN_FINAL.get());
        st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(FINAL_NAME).withStyle(s -> s.withItalic(false).withColor(0xFFD84A).withBold(true)));
        st.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                List.of(Component.literal("개발자의 매우 귀여운 탑승물").withStyle(s -> s.withItalic(true).withColor(0x55FFFF)))));
        st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, JjkMod.id("dev_token_final"));
        return st;
    }

    /** 토큰3 + 토큰4 합체. */
    static void fuse(ServerPlayer p, ServerLevel level) {
        p.getMainHandItem().shrink(1);
        p.getOffhandItem().shrink(1);
        ItemStack fin = finalToken();
        if (!p.getInventory().add(fin)) p.drop(fin, false);
        Vec3 c = p.position().add(0, 1, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2f, 1.2f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 120, 0.6, 0.8, 0.6, 0.6);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.5, 0.8, 0.5, 0.2);
        p.displayClientMessage(Component.literal("§6§l귀여운 탑승물들이 하나로 합쳐졌다! §e개발자의 최종토큰"), false);
    }

    /** 최종토큰: 거인봇 소환/탑승/귀환. */
    static void useFinal(ServerPlayer p, ServerLevel level) {
        if (p.getVehicle() instanceof DevTitan riding) {
            p.stopRiding();
            poof(level, riding.position());
            riding.discard();
            OWNED3.remove(p.getUUID());
            p.displayClientMessage(Component.literal("§7개발자의 매우 귀여운 탑승물이 돌아갔다"), true);
            return;
        }
        if (p.getVehicle() != null) return;
        UUID old = OWNED3.get(p.getUUID());
        if (old != null && level.getEntity(old) instanceof DevTitan mine && mine.isAlive()) {
            if (mine.distanceTo(p) < 80) { p.startRiding(mine); TITAN_RIDERS.add(p.getUUID()); return; }
            mine.discard();
        }
        double r = Math.toRadians(p.getYRot());
        double x = p.getX() - Math.sin(r) * 14, z = p.getZ() + Math.cos(r) * 14;
        DevTitan t = MechEntities.DEV_TITAN.get().create(level, EntitySpawnReason.TRIGGERED);
        if (t == null) return;
        t.snapTo(x, p.getY() + 1, z, p.getYRot(), 0);
        t.setYBodyRot(p.getYRot());
        t.setYHeadRot(p.getYRot());
        level.addFreshEntity(t);
        OWNED3.put(p.getUUID(), t.getUUID());
        level.playSound(null, x, p.getY(), z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3f, 0.5f);
        level.playSound(null, x, p.getY(), z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4f, 0.5f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, p.getY() + 20, z, 400, 12, 30, 12, 0.08);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, x, p.getY() + 2, z, 8, 10, 1, 10, 0);
        Laser.bolt(level, new Vec3(x, p.getY(), z));
        p.startRiding(t);
        TITAN_RIDERS.add(p.getUUID());
        p.displayClientMessage(Component.literal("§6§l개발자의 매우 귀여운 탑승물 §7— C 화염 / Z 미사일 / V 폭탄 / R 소형 아크 / X 아크레이저 / G 비행"), true);
    }

    /** 개발자 토큰4: 탑승물2(4족 보행 요새) 소환/탑승/귀환. */
    static void useToken4(ServerPlayer p, ServerLevel level) {
        if (p.getVehicle() instanceof DevWalker riding) {
            p.stopRiding();
            poof(level, riding.position());
            riding.discard();
            OWNED2.remove(p.getUUID());
            p.displayClientMessage(Component.literal("§7개발자의 귀여운 탑승물2가 돌아갔다"), true);
            return;
        }
        if (p.getVehicle() != null) return;
        UUID old = OWNED2.get(p.getUUID());
        if (old != null && level.getEntity(old) instanceof DevWalker mine && mine.isAlive()) {
            if (mine.distanceTo(p) < 28) { p.startRiding(mine); return; }
            mine.discard();
        }
        double r = Math.toRadians(p.getYRot());
        double x = p.getX() - Math.sin(r) * 7, z = p.getZ() + Math.cos(r) * 7;
        DevWalker w = MechEntities.DEV_WALKER.get().create(level, EntitySpawnReason.TRIGGERED);
        if (w == null) return;
        w.snapTo(x, p.getY() + 0.5, z, p.getYRot(), 0);
        w.setYBodyRot(p.getYRot());
        w.setYHeadRot(p.getYRot());
        level.addFreshEntity(w);
        OWNED2.put(p.getUUID(), w.getUUID());
        level.playSound(null, x, p.getY(), z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 3f, 0.4f);
        level.playSound(null, x, p.getY(), z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2f, 0.4f);
        poof(level, w.position());
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, p.getY() + 5, z, 80, 4, 4, 4, 0.05);
        p.startRiding(w);
        p.displayClientMessage(Component.literal("§b개발자의 귀여운 탑승물2 §7— [Z] 주포 연사 / [X] 중포 / 웅크리기 하차"), true);
    }

    // ───────────── 개발자의 기계 토큰
    static boolean isMachineToken(ItemStack s) { return com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.RECOVERY_COMPASS) && MACHINE_NAME.equals(s.getHoverName().getString()); }

    public static ItemStack machineToken() {
        ItemStack st = new ItemStack(com.tubilt.jjk.JjkItems.DEV_MACHINE_TOKEN.get());
        st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(MACHINE_NAME).withStyle(s -> s.withItalic(false).withColor(0xFF4040).withBold(true)));
        st.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("지금까지의 모든 기계를 응축한 것").withStyle(s -> s.withItalic(true).withColor(0xAAAAAA)),
                Component.literal("우클릭: 개발자의 기계 소환·탑승·귀환").withStyle(s -> s.withItalic(false).withColor(0x55FFFF)))));
        st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, JjkMod.id("dev_machine_token"));
        st.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        st.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        return st;
    }

    /** 웅크리고 「개발자의 버튼」 우클릭 + 인벤토리에 커맨드 수호 · 최종토큰 → 개발자의 기계 토큰. */
    static boolean tryFuseMachine(ServerPlayer p) {
        var inv = p.getInventory();
        int g = -1, f = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (g < 0 && CommandGuard.is(s)) g = i;
            if (f < 0 && com.tubilt.jjk.JjkItems.is(s, net.minecraft.world.item.Items.NETHER_STAR) && FINAL_NAME.equals(s.getHoverName().getString())) f = i;
        }
        if (g < 0 || f < 0) return false;   // 재료 없으면 평소처럼 버튼 사용
        ServerLevel level = (ServerLevel) p.level();
        inv.getItem(g).shrink(1);
        inv.getItem(f).shrink(1);
        ItemStack out = machineToken();
        if (!inv.add(out)) p.drop(out, false);
        Vec3 c = p.position().add(0, 1, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 200, 1.2, 1.2, 1.2, 0.6);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, c.x, c.y, c.z, 100, 0.8, 0.8, 0.8, 0.2);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 150, 0.6, 0.8, 0.6, 0.7);
        for (int r = 2; r <= 8; r += 2) Laser.ring(level, p.position().add(0, 0.2, 0), r);
        p.displayClientMessage(Component.literal("§c§l모든 기계가 하나로 응축되었다! §f개발자의 기계 토큰"), false);
        return true;
    }

    /** 개발자의 기계: 소환/탑승/귀환. */
    static void useMachine(ServerPlayer p, ServerLevel level) {
        if (p.getVehicle() instanceof DevMachine riding) {
            p.stopRiding();
            poof(level, riding.position());
            riding.discard();
            OWNED4.remove(p.getUUID());
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 400, 0));
            p.displayClientMessage(Component.literal("§7개발자의 기계가 돌아갔다"), true);
            return;
        }
        if (p.getVehicle() != null) return;
        UUID old = OWNED4.get(p.getUUID());
        if (old != null && level.getEntity(old) instanceof DevMachine mine && mine.isAlive()) {
            if (mine.distanceTo(p) < 60) { p.startRiding(mine); return; }
            mine.discard();
        }
        double r = Math.toRadians(p.getYRot());
        double x = p.getX() - Math.sin(r) * 16, z = p.getZ() + Math.cos(r) * 16;
        DevMachine m = MechEntities.DEV_MACHINE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (m == null) return;
        double gy = p.getY();
        BlockHitResult gh = level.clip(new ClipContext(new Vec3(x, p.getY() + 4, z), new Vec3(x, p.getY() - 30, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (gh.getType() != HitResult.Type.MISS) gy = gh.getLocation().y;
        m.snapTo(x, gy + 55, z, p.getYRot(), 0);        // 하늘에서 강하 → 착지 충격
        m.setYBodyRot(p.getYRot());
        m.setYHeadRot(p.getYRot());
        level.addFreshEntity(m);
        OWNED4.put(p.getUUID(), m.getUUID());
        Vec3 c = new Vec3(x, p.getY(), z);
        level.playSound(null, x, p.getY(), z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3f, 0.4f);
        level.playSound(null, x, p.getY(), z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3f, 0.3f);
        level.playSound(null, x, p.getY(), z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 3f, 0.3f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, p.getY() + 12, z, 300, 10, 14, 10, 0.08);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, x, p.getY() + 2, z, 6, 8, 1, 8, 0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, p.getY() + 10, z, 200, 8, 10, 8, 0.4);
        for (int i = 0; i < 4; i++) Laser.ring(level, c.add(0, 0.5, 0), 6 + i * 6);
        Laser.bolt(level, c);
        p.startRiding(m);
        // 강하 연출: 하늘에서 내려오는 표적 빛기둥 + 착지 지점 마법진 + 컷인
        Vec3 g0 = new Vec3(x, gy, z);
        String ow = LaserFx.tag(m);
        Holo.pillar(level, ow, g0, 120, 10, 0x40E8FF, 3, 30, 10);
        Holo.circle(level, ow, g0, 22, 0x40E8FF, 50, 0);
        Holo.circle(level, ow, g0.add(0, 0.2, 0), 12, 0xFF3030, 50, 0);
        Holo.ringT(level, ow, g0.add(0, 40, 0), 26, 4, 36, 0.6, 0.1, 0x40E8FF, 30, 0, 4, Holo.I(), 6, true);
        Holo.banner(level, ow, g0.add(0, 34, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("DEVELOPER MACHINE", 0xFF3030, true) + "]}", 7, 50);
        Holo.banner(level, ow, g0.add(0, 29, 0), "{text:\"\",font:\"minecraft:uniform\",extra:[" + Holo.tx("SYSTEM ONLINE · DROP SEQUENCE", 0x40E8FF, true) + "]}", 3.2, 48);
        Holo.sfx(level, g0, "riser", 3f, 0.6f); Holo.sfx(level, g0, "choir", 2.5f, 0.7f);
        com.tubilt.jjk.net.JjkNet.toPlayer(p, new com.tubilt.jjk.net.Payloads.CutIn("개발자의 기계", "DEVELOPER MACHINE · ONLINE", 0xFF3030, 1));
        p.displayClientMessage(Component.literal("§c§l개발자의 기계 §7— Z/C 무기 14종 · R 발사 · X 코어 과부하 · G 비행 · V 자동 포탑"), true);
    }

    static void poof(ServerLevel level, Vec3 at) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, at.x, at.y + 2, at.z, 60, 3, 1.5, 3, 0.05);
    }

    /** 클라이언트가 Z(미사일) 키를 눌렀을 때. */
    public static void fire(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevMech m)) return;
        if (m.missileCd > 0) {
            p.displayClientMessage(Component.literal(String.format("§7미사일 재장전 중 §f%.1f초", m.missileCd / 20f)), true);
            return;
        }
        m.missileCd = 60;
        m.triggerAnim("main", "fire");
        ServerLevel level = (ServerLevel) m.level();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(90));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        List<LivingEntity> locks = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(8),
                e -> e != p && e != m && e.isAlive()));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48),
                e -> e instanceof Enemy && e.isAlive() && look.dot(e.position().subtract(eye).normalize()) > 0.82))
            if (!locks.contains(e)) locks.add(e);
        Vec3[] pods = m.pods();
        Random rng = new Random();
        double ry = Math.toRadians(m.getYRot());
        for (int i = 0; i < 8; i++) {
            Entity lock = locks.isEmpty() ? null : locks.get(i % locks.size());
            Vec3 tgt = target.add((rng.nextDouble() - 0.5) * 5, 0, (rng.nextDouble() - 0.5) * 5);
            double side = i < 4 ? 1 : -1;
            Vec3 outward = new Vec3(Math.cos(ry) * side, 0, Math.sin(ry) * side);
            Vec3 launch = new Vec3(0, 0.9 + rng.nextDouble() * 0.3, 0).add(outward.scale(0.25 + rng.nextDouble() * 0.2)).add(look.scale(0.3));
            MISSILES.add(new Missile(level, p.getUUID(), pods[i], launch, tgt, lock, i * 2));
        }
        p.addTag("gojo.fxshake_s");
        p.displayClientMessage(Component.literal("§6§l미사일 8발 발사! §c" + (locks.isEmpty() ? "" : "락온 " + Math.min(8, locks.size()) + "개")), true);
    }

    public static boolean riding(ServerPlayer p) { return p.getVehicle() instanceof DevVehicle; }

    public static boolean ridingWalker(ServerPlayer p) { return p.getVehicle() instanceof DevWalker; }

    /** 거인봇 상태 비트: 8192 탑승, 256 미사일, 512 아크 준비, 1024 아크 작동, 2048 폭탄, 16384 소형 아크, 32768 비행, 65536 화염 */
    public static int titanFlags(ServerPlayer p) {
        if (!(p.getVehicle() instanceof DevTitan t)) return 0;
        int f = 8192;
        if (t.missileCd <= 0) f |= 256;
        if (t.arcCd <= 0 && t.arcCharge == 0 && t.arcTime == 0) f |= 512;
        if (t.arcCharge > 0 || t.arcTime > 0) f |= 1024;
        if (t.bombCd <= 0) f |= 2048;
        if (t.smallCd <= 0 && t.smallT == 0) f |= 16384;
        if (t.flying()) f |= 32768;
        if (t.flameOn) f |= 65536;
        return f;
    }

    public static int missileCd(ServerPlayer p) {
        if (p.getVehicle() instanceof DevWalker w) return Walker.cannonReady(w) ? 0 : 1;
        return p.getVehicle() instanceof DevMech m ? m.missileCd : 0;
    }

    public static boolean slamReady(ServerPlayer p) { return p.getVehicle() instanceof DevMech m && Slam.ready(m); }

    public static boolean laserReady(ServerPlayer p) {
        if (p.getVehicle() instanceof DevWalker w) return Walker.heavyReady(w);
        return p.getVehicle() instanceof DevMech m && m.laserCd <= 0;
    }

    public static boolean laserActive(ServerPlayer p) {
        if (p.getVehicle() instanceof DevWalker w) return w.heavyCharge > 0;
        return p.getVehicle() instanceof DevMech m && (m.laserCharge > 0 || m.laserTime > 0);
    }

    // 탑승 중엔 플레이어 3인칭 카메라 거리도 늘림 (탈것 속성과 함께 이중으로 보장)
    static final net.minecraft.resources.ResourceLocation CAM_ID = JjkMod.id("mech_camera");
    static void cameraDistance(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var inst = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.CAMERA_DISTANCE);
            if (inst == null) continue;
            boolean riding = p.getVehicle() instanceof DevVehicle;
            boolean has = inst.getModifier(CAM_ID) != null;
            if (riding && !has) inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    CAM_ID, 14.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            else if (!riding && has) inst.removeModifier(CAM_ID);
        }
    }

    static void tick(MinecraftServer server) {
        cameraDistance(server);
        LegendSword.tick(server);
        Titan.tickGlobal(server);
        Machine.tickGlobal(server);
        DevButton.tickAll();
        DevLever.tickAll();
        DevPhone.tickAll(server);
        GoldenClasp.tickAll(server);
        Bosses.tickAll();
        BugMobs.tick();
        BugMobs.mansionSpawn(server);
        HyungSword.tick(server);
        CodeArmor.tick(server);
        GojoBlindfold.tick(server);
        CtrlZ.tick(server);
        DevHand.tick(server);
        DevWorm.tick(server);
        CommandGuard.tickAll(server);
        Holo.tick(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // 거인봇에서 내리면 높은 곳 → 느린 낙하
            if (p.getVehicle() instanceof DevTitan || p.getVehicle() instanceof DevMachine) TITAN_RIDERS.add(p.getUUID());
            else if (TITAN_RIDERS.remove(p.getUUID()))
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 600, 0));
            // 데이터팩 주력 보스바를 숨기기 위한 탑승 태그
            if (p.getVehicle() instanceof DevVehicle) p.addTag("jjk.riding"); else p.removeTag("jjk.riding");
            if (!(p.getVehicle() instanceof DevVehicle)) continue;
            if (p.isOnFire()) p.clearFire();
            p.setAirSupply(p.getMaxAirSupply());
        }
        for (Iterator<Shell> it = SHELLS.iterator(); it.hasNext(); ) {
            Shell sh = it.next();
            try { sh.tick(server); } catch (Exception ex) { sh.dead = true; }
            if (sh.dead) it.remove();
        }
        for (Iterator<Missile> it = MISSILES.iterator(); it.hasNext(); ) {
            Missile mi = it.next();
            try { mi.tick(server); } catch (Exception e) { mi.dead = true; mi.discard(server); }
            if (mi.dead) it.remove();
        }
    }

    public static void cmd(MinecraftServer server, ServerLevel level, String command) {
        try {
            CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput().withPermission(4);
            server.getCommands().performPrefixedCommand(src, command);
        } catch (Exception e) {
            JjkMod.LOG.warn("[JJK] 명령 실패: {}", command, e);
        }
    }

    /** gojo.cfg 의 #이름 점수 (없으면 def) != 0 ? */
    static boolean cfgOn(MinecraftServer server, String name, int def) {
        Objective o = server.getScoreboard().getObjective("gojo.cfg");
        if (o == null) return def != 0;
        ReadOnlyScoreInfo i = server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name), o);
        return (i == null ? def : i.value()) != 0;
    }

    static boolean griefOn(MinecraftServer server) {
        Objective o = server.getScoreboard().getObjective("gojo.cfg");
        if (o == null) return true;
        ReadOnlyScoreInfo i = server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly("#grief"), o);
        return i == null || i.value() == 1;
    }
}
