package com.tubilt.jjk.sim;

/**
 * 기계 블록 id → 시뮬레이션 부품 사양 (Minecraft 비의존).
 * 게임의 기계 블록과 자체 검증(설계도 회전 후 네트워크 동일성 등)이 같은 사양을 쓰도록 한곳에 모은다.
 */
public final class MachineSpecs {
    private MachineSpecs() {}

    public static final String[] IDS = {"mech_shaft", "mech_gear_s", "mech_gear_m", "mech_gear_l", "mech_motor", "mech_flywheel", "mech_fan"};

    /** 기어면 잇수, 아니면 0. */
    public static int teeth(String id) {
        return switch (id) {
            case "mech_gear_s" -> 12;
            case "mech_gear_m" -> 20;
            case "mech_gear_l" -> 28;
            default -> 0;
        };
    }

    /** 블록 id 에 해당하는 새 부품. 모르는 id 면 null. */
    public static Part create(String id) {
        return switch (id) {
            case "mech_shaft" -> new Parts.Shaft("회전축", 0.2, 400);
            case "mech_gear_s" -> new Parts.Gear("기어(12T)", 12, 0.08, 300);
            case "mech_gear_m" -> new Parts.Gear("기어(20T)", 20, 0.25, 600);
            case "mech_gear_l" -> new Parts.Gear("기어(28T)", 28, 0.6, 1000);
            /* 시험용 전동기: 정지토크 120 N·m, 무부하 약 1000 RPM. (전력 시스템은 5단계에서 연결 — 지금은 외부 동력 없이 도는 시험용) */
            case "mech_motor" -> new Parts.Motor("전동기", 1.5, 120, 104.72, 300);
            case "mech_flywheel" -> new Parts.Flywheel("플라이휠", 40, 0.05, 800);
            /* 팬 부하: 토크 = 0.02·ω² (속도의 제곱에 비례하는 공기저항). */
            case "mech_fan" -> new Parts.Load("팬 부하", 2, 0.02, 0, 600);
            default -> null;
        };
    }
}
