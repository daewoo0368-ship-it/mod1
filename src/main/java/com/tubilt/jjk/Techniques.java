package com.tubilt.jjk;

import java.util.List;

/** 키로 쓸 수 있는 술식 목록 (서버/클라이언트 공용). */
public final class Techniques {
    private Techniques() {}

    /**
     * @param id        키 전송용 id
     * @param name      표시 이름
     * @param user      사용자(캐릭터)
     * @param function  서버에서 실행할 데이터팩 함수
     * @param cdIndex   쿨타임 배열 인덱스 (STATUS_CD 순서), -1 = 없음
     * @param cost      주력 소모 (육안이면 1/4)
     * @param color     표시 색 (RGB)
     * @param desc      한 줄 설명
     */
    public record Tech(String id, String name, String user, String function, int cdIndex, int cost, int color, String desc) {}

    /** 상태 패킷으로 보내는 쿨타임 objective 순서. */
    public static final List<String> STATUS_CD = List.of(
            "gojo.cd", "gojo.dcd", "gojo.mcd", "gojo.jcd", "gojo.gcd", "gojo.ucd", "gojo.pcd",
            "gojo.wcd", "gojo.kcd", "gojo.tcd", "gojo.hkcd", "gojo.hgcd", "gojo.hcd", "gojo.cscd");

    public static final List<Tech> ALL = List.of(
            new Tech("blue", "술식순전 「창」", "고죠 사토루", "gojo:use/blue", 0, 60, 0x3A7BFF, "끌어당기는 푸른 구체 (웅크리기 = 영창)"),
            new Tech("red", "술식반전 「혁」", "고죠 사토루", "gojo:use/red", 0, 80, 0xFF2A2A, "밀쳐내는 붉은 탄 (웅크리기 = 영창)"),
            new Tech("purple", "허식 「무라사키」", "고죠 사토루", "gojo:use/purple", 0, 250, 0xA040FF, "창과 혁의 충돌 (웅크리기 = 영창)"),
            new Tech("infinity", "무하한", "고죠 사토루", "gojo:use/membrane", -1, 50, 0xBFE6FF, "켜기/끄기"),
            new Tech("void", "영역전개 「무량공처」", "고죠 사토루", "gojo:use/flint", 1, 400, 0xE8F4FF, "켜기/끄기, 영역 싸움 중엔 연타"),
            new Tech("kai", "「해」 / 세계참", "료멘 스쿠나", "gojo:use/sword", 0, 15, 0xFFFFFF, "참격 (웅크리기 = 영창 후 세계참)"),
            new Tech("fuga", "「푸가(開)」", "료멘 스쿠나", "gojo:use/arrow", 0, 250, 0xFF9020, "불화살"),
            new Tech("shrine", "영역전개 「복마어주자」", "료멘 스쿠나", "gojo:use/brick", 2, 400, 0xB01010, "켜기/끄기"),
            new Tech("maho", "마허라 소환", "료멘 스쿠나", "gojo:use/bone", -1, 300, 0xF0F0F0, "소환/해제"),
            new Tech("jp", "영역전개 「자폐원돈과」", "마히토", "gojo:use/spider_eye", 3, 400, 0x40A0A8, "켜기/끄기"),
            new Tech("meteor", "극번 「운」", "죠고", "gojo:use/wart", 5, 350, 0xFF5020, "운석 낙하"),
            new Tech("jg", "영역전개 「개관철위산」", "죠고", "gojo:use/magma", 4, 400, 0xFF7A00, "켜기/끄기"),
            new Tech("hr", "천여주박", "후시구로 토우지", "gojo:use/leather", -1, 0, 0x2E7D32, "켜기/끄기 (천역모 지급)"),
            new Tech("prison", "옥문강", "특급 주물", "gojo:use/echo", 6, 0, 0x8B0010, "설치 / 봉인 해제"),
            new Tech("court", "영역전개 「주복사사」", "히구루마 히로미", "gojo:use/book", 11, 400, 0xE0B030, "재판"),
            new Tech("wood", "「목구」", "하나미", "gojo:use/stick", 7, 20, 0x8B6A3E, "나무 공 6발"),
            new Tech("flowers", "「공화」", "하나미", "gojo:use/pink", 8, 150, 0xF28CC0, "꽃밭"),
            new Tech("tk", "영역전개 「타이코카이」", "하나미", "gojo:use/green", 9, 400, 0x4CAF50, "켜기/끄기"),
            new Tech("hk", "영역전개 「좌살박도」", "하카리 킨지", "gojo:use/nugget", 10, 300, 0x7CFF6A, "켜기/끄기"),
            new Tech("j_ladder", "야곱의 사다리", "쿠루스 하나", "java:jacob/ladder", -1, 140, 0xFFE070, "대상 위로 빛의 사다리를 내리꽂음 · 강화 효과 소거"),
            new Tech("j_climb", "승천의 사다리", "쿠루스 하나", "java:jacob/climb", -1, 100, 0xFFFFFF, "빛의 사다리를 타고 하늘로 솟구침 · 낙하 무효"),
            new Tech("j_judge", "심판의 사다리", "쿠루스 하나", "java:jacob/judge", -1, 380, 0xFFE070, "사다리 여러 줄을 연달아 내림 (궁극기)"),
            new Tech("r_heal", "반전술식 「치유」", "범용", "java:rct/heal", -1, 120, 0xFFE9A0, "체력 회복 · 재생 · 나쁜 효과 제거"),
            new Tech("r_out", "반전술식 「출력」", "범용", "java:rct/out", -1, 260, 0xFFE9A0, "정(正)의 에너지 관통 광선"),
            new Tech("b_bind", "적박", "초소", "java:blood/bind", -1, 70, 0xC01020, "붉은 사슬로 대상을 4초 속박 (체력 소모)"),
            new Tech("b_pierce", "백렴·천혈", "초소", "java:blood/pierce", -1, 160, 0xFF3040, "피를 압축해 관통 광선 (체력 소모)"),
            new Tech("b_nova", "초신성", "초소", "java:blood/nova", -1, 220, 0xC01020, "피 구체 5발 연사 · 닿으면 폭발 (체력 소모)"),
            new Tech("b_scale", "적린", "초소", "java:blood/scale", -1, 100, 0xFF3040, "15초간 근력·속도·내성 강화 (체력 소모)"),
            new Tech("p_frame", "투사 가속", "젠인 나오야", "java:proj/frame", -1, 60, 0x7CFF6A, "켜기/끄기 · 24프레임 가속·잔상·회피"),
            new Tech("p_slice", "24프레임 연격", "젠인 나오야", "java:proj/slice", -1, 150, 0x7CFF6A, "대상을 24컷 동안 사방에서 베고 지나감"),
            new Tech("p_freeze", "프레임 고정", "젠인 나오야", "java:proj/freeze", -1, 120, 0xFFFFFF, "룰 — 대상을 틀에 가둬 3초간 정지"),
            new Tech("p_rush", "24fps 폭주", "젠인 나오야", "java:proj/rush", -1, 300, 0xB8FFB0, "6초간 주변 전원을 묶고 초고속 난타 (궁극기)"),
            new Tech("k_sik", "술식순전 「식(吸)」", "코밍", "java:koming/sik", -1, 80, 0x3A7BFF, "공간째 빨아들이는 푸른 구체 (웅크리기 = 영창)"),
            new Tech("k_gyo", "술식반전 「교(膠)」", "코밍", "java:koming/gyo", -1, 100, 0xFF2A2A, "공간을 밀어내며 날아가 과부하 폭발 (웅크리기 = 영창)"),
            new Tech("k_muryeo", "급식 「무려3끼」", "코밍", "java:koming/muryeo", -1, 250, 0xB050FF, "존재 소거 · 연속 시 무려(n+2)끼 (웅크리기 = 영창)"),
            new Tech("k_lego", "술식전개 「레고사주자」", "코밍", "java:koming/lego", -1, 400, 0xFF3030, "반경 200 레고 십자 투사체 난사 · 다시 쓰면 해제"),
            new Tech("k_shield", "급식한", "코밍", "java:koming/shield", -1, 50, 0x9FD8FF, "켜기/끄기 · 상대 접근 차단"),
            new Tech("k_hunsu", "술식전개 「무료훈수」", "코밍", "java:koming/hunsu", -1, 400, 0x55FFFF, "PC방 · 반경 200 구속·능력 봉쇄 · 다시 쓰면 해제"),
            new Tech("k_lunch", "점심시간 유아독존", "코밍", "java:koming/lunch", -1, 0, 0xFFD84A, "주력 100% · 피로·부담 초기화"),
            new Tech("k_yu", "술력 「유(有)」", "코밍", "java:koming/yu", -1, 60, 0xFFE070, "켜기/끄기 · 강화·회복·방어 관통"),
            new Tech("k_brain", "뇌 파괴와 재생", "코밍", "java:koming/brain", -1, 0, 0xFF6060, "영역 후 술식 과부하 해소"),
            new Tech("m_dogs", "십종영법 「옥견」", "후시구로 메구미", "java:chars/m_dogs", -1, 120, 0xE8E8F0, "흰 개와 검은 개를 소환 · 6초간 적을 물어뜯음"),
            new Tech("m_rabbits", "십종영법 「탈토」", "후시구로 메구미", "java:chars/m_rabbits", -1, 150, 0xFFFFFF, "토끼 떼가 퍼져나가며 실명·둔화 (반경 15)"),
            new Tech("m_serpent", "십종영법 「대사」", "후시구로 메구미", "java:chars/m_serpent", -1, 260, 0x2E7D32, "큰 뱀이 돌진해 일직선을 관통 (32)"),
            new Tech("n_nail", "공명 「못박기」", "쿠기사키 노바라", "java:chars/n_nail", -1, 40, 0xFF9020, "못을 쏘아 대상에 공명 표식을 남김 (12)"),
            new Tech("n_resonance", "공명 「공명」", "쿠기사키 노바라", "java:chars/n_resonance", -1, 120, 0xFFD080, "표식이 붙은 대상을 원격으로 공명 폭발 (40 · 주변 18)"),
            new Tech("n_hairpin", "공명 「철정」", "쿠기사키 노바라", "java:chars/n_hairpin", -1, 200, 0xC8D0E0, "정면 최대 8명에게 못을 일제 사출 (각 16)"),
            new Tech("na_ratio", "칠할삼분 「십수할」", "나나미 켄토", "java:chars/na_ratio", -1, 90, 0xFFE070, "대상 앞으로 이동해 7:3 약점 일격 (18 + 최대체력 10%)"),
            new Tech("na_overtime", "칠할삼분 「초과근무」", "나나미 켄토", "java:chars/na_overtime", -1, 120, 0xFFE070, "12초간 근력·속도·내성 강화 · 끝나면 피로"),
            new Tech("na_cleave", "칠할삼분 「해체」", "나나미 켄토", "java:chars/na_cleave", -1, 180, 0xFFF0B0, "전방 부채꼴 6칸 참격 (26)"),
            new Tech("y_rika", "리카 「리카 완전현현」", "오코츠 유타", "java:chars/y_rika", -1, 300, 0xFFFFFF, "리카를 15초간 현현 · 근처 적을 자동 공격 (궁극기)"),
            new Tech("y_copy", "리카 「모방」", "오코츠 유타", "java:chars/y_copy", -1, 60, 0xC080FF, "마지막에 쓴 술식을 공짜로 다시 사용"),
            new Tech("y_love", "리카 「순애빔」", "오코츠 유타", "java:chars/y_love", -1, 350, 0xFF60C0, "충전 후 폭 3의 거대 광선 (50)"),
            new Tech("t_halberd", "천여주박 「천역모 투척」", "후시구로 토우지", "java:chars/t_halberd", -1, 60, 0x7CFF6A, "천역모를 던져 일직선을 관통 · 방어 무시 (30)"),
            new Tech("t_dash", "천여주박 「순보」", "후시구로 토우지", "java:chars/t_dash", -1, 40, 0xE0FFE0, "바라보는 방향 9칸 순간 돌진 · 경로의 적 타격"),
            new Tech("t_heaven", "천여주박 「주구 해방」", "후시구로 토우지", "java:chars/t_heaven", -1, 100, 0x2E7D32, "20초간 근력·속도·점프 강화"));

    public static Tech byId(String id) {
        for (Tech t : ALL) if (t.id().equals(id)) return t;
        return null;
    }
}
