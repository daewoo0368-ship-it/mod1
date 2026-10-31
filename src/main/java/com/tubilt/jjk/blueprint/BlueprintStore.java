package com.tubilt.jjk.blueprint;

import com.tubilt.jjk.JjkMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 설계도 저장소.
 *  · 고정 설계도: 모드 리소스 data/jjk/blueprints/*.snbt (버전 관리되는 텍스트). 읽기 전용 — 게임 안에서 고치거나 덮어쓸 수 없다.
 *  · 사용자 설계도: 월드 폴더 data/jjk_blueprints/user/&lt;id&gt;.nbt (월드마다 분리, 서버 재시작·재접속 후에도 유지).
 *    쓰기는 별도 스레드에서 임시 파일 → 원자적 교체, 이전 판은 .bak 으로 보관. 읽을 때 포맷 버전·내용을 검증한다.
 */
public final class BlueprintStore {
    private BlueprintStore() {}

    static Map<String, Blueprint> fixed;
    static final List<String> fixedErrors = new ArrayList<>();
    static final Map<String, Blueprint> user = new LinkedHashMap<>();
    static final List<String> userErrors = new ArrayList<>();
    static Path dir;
    static ExecutorService io;

    public static String modVersion() {
        return ModList.get().getModContainerById(JjkMod.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    /** 서버(월드)가 시작될 때: 이 월드의 저장 공간을 정하고 사용자 설계도를 읽는다. */
    public static synchronized void open(MinecraftServer server) {
        fixed = null; fixedErrors.clear(); user.clear(); userErrors.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("jjk_blueprints");
        if (io == null) io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "jjk-blueprint-io"); t.setDaemon(true); return t; });
        Path ud = dir.resolve("user");
        if (!Files.isDirectory(ud)) return;
        try (Stream<Path> files = Files.list(ud)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".nbt")).sorted().collect(Collectors.toList())) {
                Blueprint b = readUser(f);
                if (b != null) user.put(b.id, b);
            }
        } catch (IOException e) {
            JjkMod.LOG.warn("[Dan] 사용자 설계도 목록 읽기 실패", e);
        }
        JjkMod.LOG.info("[Dan] 사용자 설계도 {}개 불러옴 ({})", user.size(), ud);
    }

    /** 서버가 멈출 때: 밀린 저장을 끝까지 기다린다. */
    public static void close() {
        flush();
        synchronized (BlueprintStore.class) { fixed = null; user.clear(); dir = null; }
    }

    public static void flush() {
        if (io == null) return;
        try { io.submit(() -> {}).get(30, TimeUnit.SECONDS); } catch (Exception e) { JjkMod.LOG.warn("[Dan] 설계도 저장 대기 실패", e); }
    }

    static Blueprint readUser(Path f) {
        List<String> errs = new ArrayList<>();
        try {
            CompoundTag t = NbtIo.readCompressed(f, NbtAccounter.create(256L * 1024 * 1024));
            Blueprint b = Blueprint.fromTag(t, false, errs);
            String want = "user:" + stem(f);
            if (b != null && !want.equals(b.id)) { b.warnings.add("파일 이름과 id 가 달라 파일 이름 기준으로 맞춤"); b.id = want; }
            if (b == null) userErrors.add(f.getFileName() + ": " + String.join(", ", errs));
            return b;
        } catch (Exception e) {
            userErrors.add(f.getFileName() + ": 파일 손상 — " + e.getMessage());
            JjkMod.LOG.warn("[Dan] 설계도 파일 손상: {}", f, e);
            Path bak = f.resolveSibling(f.getFileName() + ".bak");
            if (Files.exists(bak)) {
                try {
                    Blueprint b = Blueprint.fromTag(NbtIo.readCompressed(bak, NbtAccounter.create(256L * 1024 * 1024)), false, errs);
                    if (b != null) { b.id = "user:" + stem(f); b.warnings.add("손상된 파일 대신 백업(.bak)에서 복구"); return b; }
                } catch (Exception ignored) { }
            }
            return null;
        }
    }

    static String stem(Path f) { String n = f.getFileName().toString(); return n.substring(0, n.length() - 4); }

    // ───────────── 고정 설계도 ─────────────
    public static synchronized Map<String, Blueprint> fixed(MinecraftServer server) {
        if (fixed != null) return fixed;
        Map<String, Blueprint> out = new TreeMap<>();
        fixedErrors.clear();
        Map<ResourceLocation, Resource> res = server.getResourceManager().listResources("blueprints", rl -> rl.getPath().endsWith(".snbt"));
        for (Map.Entry<ResourceLocation, Resource> e : res.entrySet()) {
            ResourceLocation rl = e.getKey();
            String file = rl.getPath().substring("blueprints/".length(), rl.getPath().length() - ".snbt".length());
            String want = rl.getNamespace() + ":" + file;
            try (BufferedReader r = e.getValue().openAsReader()) {
                String text = r.lines().collect(Collectors.joining("\n"));
                List<String> errs = new ArrayList<>();
                Blueprint b = Blueprint.fromTag(TagParser.parseCompoundFully(text), true, errs);
                if (b == null) { fixedErrors.add(want + ": " + String.join(", ", errs)); continue; }
                if (!want.equals(b.id)) { fixedErrors.add(want + ": 파일 이름과 id(" + b.id + ") 불일치"); continue; }
                out.put(b.id, b);
            } catch (Exception ex) {
                fixedErrors.add(want + ": 읽기 실패 — " + ex.getMessage());
            }
        }
        for (String s : fixedErrors) JjkMod.LOG.warn("[Dan] 고정 설계도 오류 {}", s);
        JjkMod.LOG.info("[Dan] 고정 설계도 {}개 불러옴", out.size());
        fixed = Collections.unmodifiableMap(out);
        return fixed;
    }

    public static List<String> errors() {
        List<String> l = new ArrayList<>(fixedErrors);
        l.addAll(userErrors);
        return l;
    }

    // ───────────── 조회 ─────────────
    public static synchronized Blueprint get(MinecraftServer server, String id) {
        if (id == null) return null;
        if (id.startsWith("user:")) return user.get(id);
        return fixed(server).get(id);
    }

    public static synchronized List<Blueprint> users() { return new ArrayList<>(user.values()); }

    // ───────────── 사용자 설계도 쓰기 ─────────────
    public static String newUserId() {
        String id;
        do { id = "user:" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); } while (user.containsKey(id));
        return id;
    }

    /** 메모리에 넣고 파일 저장을 예약한다. done 은 저장 결과(null = 성공, 아니면 오류)로 IO 스레드에서 불린다. */
    public static synchronized void put(Blueprint b, java.util.function.Consumer<String> done) {
        user.put(b.id, b);
        if (dir == null) { if (done != null) done.accept("저장 공간이 열려 있지 않음"); return; }
        CompoundTag tag = b.toTag();
        Path f = dir.resolve("user").resolve(b.id.substring(5) + ".nbt");
        io.execute(() -> {
            String err = null;
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                NbtIo.writeCompressed(tag, tmp);
                // 저장 직후 다시 읽어 검증 (디스크 손상·직렬화 오류 감지)
                CompoundTag back = NbtIo.readCompressed(tmp, NbtAccounter.create(256L * 1024 * 1024));
                if (!back.equals(tag)) throw new IOException("저장 검증 실패 (다시 읽은 내용이 다름)");
                if (Files.exists(f)) Files.copy(f, f.resolveSibling(f.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException atomicFail) { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING); }
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
                JjkMod.LOG.warn("[Dan] 설계도 저장 실패: {}", f, e);
            }
            if (done != null) done.accept(err);
        });
    }

    public static synchronized boolean delete(String id) {
        if (!id.startsWith("user:") || user.remove(id) == null) return false;
        if (dir == null) return true;
        Path f = dir.resolve("user").resolve(id.substring(5) + ".nbt");
        io.execute(() -> {
            try {
                Path trash = dir.resolve("deleted");
                Files.createDirectories(trash);
                if (Files.exists(f)) Files.move(f, trash.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(f.resolveSibling(f.getFileName() + ".bak"));
            } catch (IOException e) { JjkMod.LOG.warn("[Dan] 설계도 삭제 실패: {}", f, e); }
        });
        return true;
    }

    /** 테스트용: 메모리 캐시를 비우고 디스크에서 다시 읽는다. */
    public static synchronized Blueprint reloadFromDisk(String id) {
        flush();
        if (dir == null || !id.startsWith("user:")) return null;
        Path f = dir.resolve("user").resolve(id.substring(5) + ".nbt");
        if (!Files.exists(f)) return null;
        Blueprint b = readUser(f);
        if (b != null) user.put(b.id, b);
        return b;
    }

    static Path dir() { return dir; }
}
