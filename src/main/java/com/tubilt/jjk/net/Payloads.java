package com.tubilt.jjk.net;

import com.tubilt.jjk.JjkMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public final class Payloads {
    private Payloads() {}

    /** C→S: 클라이언트에 모드가 있음을 알림 (보스바 대신 HUD 사용). */
    public record Hello(int version) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(JjkMod.id("hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Hello::version, Hello::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: 선택한 술식 시전. */
    public record Cast(String tech) implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(JjkMod.id("cast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Cast::tech, Cast::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: 탈것 미사일 발사. */
    public record Fire(int n) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(JjkMod.id("fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Fire::n, Fire::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * S→C: 주력/쿨타임/상태.
     * flags 비트: 0 육안, 1 무하한, 2 천여주박, 3 대박, 4 봉인, 5 몰수, 6 장면 재생 중, 7 탈것 탑승, 8 미사일 준비
     * domain: 0 없음, 1 무량공처, 2 복마어주자, 3 자폐원돈과, 4 개관철위산, 5 주복사사, 6 타이코카이, 7 좌살박도
     */
    public record Status(int ce, int ceMax, int flags, int domain, List<Integer> cooldowns) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(JjkMod.id("status"));
        private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Status::ce,
                ByteBufCodecs.VAR_INT, Status::ceMax,
                ByteBufCodecs.VAR_INT, Status::flags,
                ByteBufCodecs.VAR_INT, Status::domain,
                INT_LIST, Status::cooldowns,
                Status::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 화면 흔들림. */
    public record Shake(int ticks, float power) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(JjkMod.id("shake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Shake> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Shake::ticks, ByteBufCodecs.FLOAT, Shake::power, Shake::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 화면 번쩍임 (색 ARGB, 지속 틱). */
    public record Flash(int argb, int ticks) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(JjkMod.id("flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flash> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, Flash::argb, ByteBufCodecs.VAR_INT, Flash::ticks, Flash::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 스킬 컷인 (kind 0 이름판, 1 궁극기 대형 컷인). */
    public record CutIn(String title, String sub, int color, int kind) implements CustomPacketPayload {
        public static final Type<CutIn> TYPE = new Type<>(JjkMod.id("cutin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CutIn> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CutIn::title, ByteBufCodecs.STRING_UTF8, CutIn::sub,
                ByteBufCodecs.INT, CutIn::color, ByteBufCodecs.VAR_INT, CutIn::kind, CutIn::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 짧은 시네마틱 궤도 카메라 (초점, 거리, 높이, meta = 지속ms | (회전각+720)<<16). */
    public record Cam(double fx, double fy, double fz, float dist, float height, int meta) implements CustomPacketPayload {
        public static final Type<Cam> TYPE = new Type<>(JjkMod.id("cam"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cam> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Cam::fx, ByteBufCodecs.DOUBLE, Cam::fy, ByteBufCodecs.DOUBLE, Cam::fz,
                ByteBufCodecs.FLOAT, Cam::dist, ByteBufCodecs.FLOAT, Cam::height, ByteBufCodecs.INT, Cam::meta, Cam::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 화면 반응 (kind 1 FOV 펀치, 2 카메라 롤 흔들림, 3 임팩트 프레임, 4 색수차, 5 하늘·안개 색, 6 집중선, 7 FOV 줌인). */
    public record Screen(int kind, int color, int ms, float power) implements CustomPacketPayload {
        public static final Type<Screen> TYPE = new Type<>(JjkMod.id("screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Screen> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Screen::kind, ByteBufCodecs.INT, Screen::color, ByteBufCodecs.VAR_INT, Screen::ms, ByteBufCodecs.FLOAT, Screen::power, Screen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 커맨드 수호의 메타 연출 (kind: 0 채팅 대사 · 1 가짜 크래시 · 2 가짜 일시정지 · 3 HUD 침입 · 4 도전 과제 토스트
     *  · 5 화면 금 · 6 가짜 연결 끊김 · 7 가짜 사망 화면 · 8 채팅창 입력 · 9 퀴즈 자막). text 의 {name} {fov} {rd} {fps} {time} 은 클라이언트가 채움. */
    public record Meta(int kind, String text) implements CustomPacketPayload {
        public static final Type<Meta> TYPE = new Type<>(JjkMod.id("meta"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Meta> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Meta::kind, ByteBufCodecs.STRING_UTF8, Meta::text, Meta::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 직업과 해금된 능력 id 목록 (쉼표 구분). job 이 빈 문자열이면 직업 미선택. */
    public record Unlocks(String job, String ids) implements CustomPacketPayload {
        public static final Type<Unlocks> TYPE = new Type<>(JjkMod.id("unlocks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unlocks> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Unlocks::job, ByteBufCodecs.STRING_UTF8, Unlocks::ids, Unlocks::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 행성 낙하 시네마틱 (플레이어 눈, 목표, 시작 게임 시각). */
    public record Planet(double px, double py, double pz, double tx, double ty, double tz) implements CustomPacketPayload {
        public static final Type<Planet> TYPE = new Type<>(JjkMod.id("planet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Planet> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Planet::px, ByteBufCodecs.DOUBLE, Planet::py, ByteBufCodecs.DOUBLE, Planet::pz,
                ByteBufCodecs.DOUBLE, Planet::tx, ByteBufCodecs.DOUBLE, Planet::ty, ByteBufCodecs.DOUBLE, Planet::tz, Planet::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 개발자의 버튼 컷신 시작 (플레이어 눈 위치, 목표 지점). */
    public record Cine(double px, double py, double pz, double tx, double ty, double tz) implements CustomPacketPayload {
        public static final Type<Cine> TYPE = new Type<>(JjkMod.id("cine"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cine> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Cine::px, ByteBufCodecs.DOUBLE, Cine::py, ByteBufCodecs.DOUBLE, Cine::pz,
                ByteBufCodecs.DOUBLE, Cine::tx, ByteBufCodecs.DOUBLE, Cine::ty, ByteBufCodecs.DOUBLE, Cine::tz, Cine::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ───────────── 건축물 소환기 ─────────────

    /** C→S: 건축물 소환기 요청. op 는 StructureSpawner.OP_*, id 는 설계도 id, text 는 이름·설명·설정 등. 서버가 모두 다시 검증한다. */
    public record BpReq(int op, String id, String text) implements CustomPacketPayload {
        public static final Type<BpReq> TYPE = new Type<>(JjkMod.id("bp_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BpReq> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BpReq::op, ByteBufCodecs.STRING_UTF8, BpReq::id, ByteBufCodecs.STRING_UTF8, BpReq::text, BpReq::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 설계도 목록의 한 줄. */
    public record BpEntry(String id, boolean fixed, boolean editable, String name, String desc, String author, String version,
                          int sx, int sy, int sz, int blocks, int machines, int materials, int warnings, String features) {
        static void write(RegistryFriendlyByteBuf b, BpEntry e) {
            b.writeUtf(e.id); b.writeBoolean(e.fixed); b.writeBoolean(e.editable); b.writeUtf(e.name); b.writeUtf(e.desc); b.writeUtf(e.author); b.writeUtf(e.version);
            b.writeVarInt(e.sx); b.writeVarInt(e.sy); b.writeVarInt(e.sz); b.writeVarInt(e.blocks); b.writeVarInt(e.machines); b.writeVarInt(e.materials); b.writeVarInt(e.warnings);
            b.writeUtf(e.features);
        }
        static BpEntry read(RegistryFriendlyByteBuf b) {
            return new BpEntry(b.readUtf(), b.readBoolean(), b.readBoolean(), b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf());
        }
    }

    /**
     * S→C: 건축물 소환기 상태 (설계도 목록·선택·소환 설정·영역 선택·작업 진행). open 이면 화면을 연다.
     * settings = {회전 0..3, 반전 0/1, 정책 0..2, 기준점 0/1, X, Y, Z 오프셋, 영역 선택 모드 0/1, 덮어쓰기 권한 0/1}
     */
    public record BpList(boolean open, String selected, List<Integer> settings, String selection, String job, float jobFrac,
                         String message, List<BpEntry> entries) implements CustomPacketPayload {
        public static final Type<BpList> TYPE = new Type<>(JjkMod.id("bp_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BpList> CODEC = StreamCodec.of((b, v) -> {
            b.writeBoolean(v.open); b.writeUtf(v.selected);
            b.writeVarInt(v.settings.size()); for (int i : v.settings) b.writeVarInt(i);
            b.writeUtf(v.selection); b.writeUtf(v.job); b.writeFloat(v.jobFrac); b.writeUtf(v.message);
            b.writeVarInt(v.entries.size()); for (BpEntry e : v.entries) BpEntry.write(b, e);
        }, b -> {
            boolean open = b.readBoolean(); String sel = b.readUtf();
            int n = Math.min(b.readVarInt(), 64); List<Integer> st = new java.util.ArrayList<>(n); for (int i = 0; i < n; i++) st.add(b.readVarInt());
            String selection = b.readUtf(), job = b.readUtf(); float frac = b.readFloat(); String msg = b.readUtf();
            int m = Math.min(b.readVarInt(), 4096); List<BpEntry> es = new java.util.ArrayList<>(m); for (int i = 0; i < m; i++) es.add(BpEntry.read(b));
            return new BpList(open, sel, st, selection, job, frac, msg, es);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: 설계도 상세 — 미리보기 격자 2개({w, h, ARGB...} 위/앞에서 본 모습), 재료 목록("번역키\t개수" 줄), 경고·검사 결과. */
    public record BpDetail(String id, int[] top, int[] side, String materials, String info) implements CustomPacketPayload {
        public static final Type<BpDetail> TYPE = new Type<>(JjkMod.id("bp_detail"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BpDetail> CODEC = StreamCodec.of((b, v) -> {
            b.writeUtf(v.id); b.writeVarIntArray(v.top); b.writeVarIntArray(v.side); b.writeUtf(v.materials); b.writeUtf(v.info);
        }, b -> new BpDetail(b.readUtf(), b.readVarIntArray(4096), b.readVarIntArray(4096), b.readUtf(), b.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
