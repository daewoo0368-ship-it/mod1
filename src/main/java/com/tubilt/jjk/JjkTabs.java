package com.tubilt.jjk;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 크리에이티브 탭 2개: 「주술회전」(술식 아이템) · 「개발자」(토큰·커맨드 수호·버튼).
 * 아이템은 데이터팩 give 명령과 똑같은 문자열을 파싱해서 만듦 → 명령어로 받은 것과 동일.
 */
public final class JjkTabs {
    private JjkTabs() {}

    static final String[] JJK = {
            "jjk:blue[minecraft:item_name={\"text\": \"술식순전 「창」\", \"color\": \"aqua\", \"italic\": false}]",
            "jjk:red[minecraft:item_name={\"text\": \"술식반전 「혁」\", \"color\": \"red\", \"italic\": false}]",
            "jjk:purple[minecraft:item_name={\"text\": \"허식 「무라사키」\", \"color\": \"dark_purple\", \"italic\": false}]",
            "jjk:black_flash[minecraft:item_name={\"text\": \"흑섬\", \"color\": \"dark_red\", \"italic\": false}]",
            "jjk:infinity[minecraft:item_name={\"text\": \"무하한\", \"color\": \"aqua\", \"italic\": false}]",
            "jjk:unlimited_void[minecraft:item_name={\"text\": \"영역전개 「무량공처」\", \"color\": \"white\", \"italic\": false}]",
            "jjk:dismantle[minecraft:item_name={\"text\": \"「해」 / 세계참\", \"color\": \"white\", \"italic\": false}]",
            "jjk:fuga[minecraft:item_name={\"text\": \"「푸가」\", \"color\": \"gold\", \"italic\": false}]",
            "jjk:malevolent_shrine[minecraft:item_name={\"text\": \"영역전개 「복마어주자」\", \"color\": \"dark_red\", \"italic\": false}]",
            "jjk:mahoraga[minecraft:item_name={\"text\": \"마허라 소환\", \"color\": \"white\", \"italic\": false}]",
            "jjk:idle_transfiguration[minecraft:item_name={\"text\": \"무위전변\", \"color\": \"dark_aqua\", \"italic\": false}]",
            "jjk:self_embodiment[minecraft:item_name={\"text\": \"영역전개 「자폐원돈과」\", \"color\": \"dark_aqua\", \"italic\": false}]",
            "jjk:maximum_meteor[minecraft:item_name={\"text\": \"극번 「운」\", \"color\": \"red\", \"italic\": false}]",
            "jjk:coffin_iron_mountain[minecraft:item_name={\"text\": \"영역전개 「개관철위산」\", \"color\": \"gold\", \"italic\": false}]",
            "jjk:heavenly_restriction[minecraft:item_name={\"text\": \"천여주박\", \"color\": \"dark_green\", \"italic\": false}]",
            "jjk:prison_realm[minecraft:item_name={\"text\": \"옥문강\", \"color\": \"dark_red\", \"italic\": false}]",
            "jjk:deadly_sentencing[minecraft:item_name={\"text\": \"영역전개 「주복사사」\", \"color\": \"gold\", \"italic\": false}]",
            "jjk:mokugu[minecraft:item_name={\"text\": \"「목구」\", \"color\": \"dark_green\", \"italic\": false}]",
            "jjk:kuka[minecraft:item_name={\"text\": \"「공화」\", \"color\": \"light_purple\", \"italic\": false}]",
            "jjk:taikokai[minecraft:item_name={\"text\": \"영역전개 「타이코카이」\", \"color\": \"dark_green\", \"italic\": false}]",
            "jjk:zasatsu[minecraft:item_name={\"text\": \"영역전개 「좌살박도」\", \"color\": \"green\", \"italic\": false}]",
            "jjk:six_eyes[minecraft:custom_data={gojo_rikugan:1b},minecraft:item_model=\"jjk:six_eyes\",minecraft:item_name={\"text\": \"육안(六眼)\", \"color\": \"aqua\", \"italic\": false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"지니고만 있어도 발동한다\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:sukuna_finger[minecraft:item_model=\"jjk:sukuna_finger\",minecraft:item_name={\"text\":\"료멘 스쿠나의 손가락\",\"color\":\"dark_red\",\"italic\":false},minecraft:lore=[{\"text\":\"특급 주물 — 삼키면 스쿠나의 힘이 깃든다\",\"color\":\"gray\",\"italic\":true},{\"text\":\"모두 20개\",\"color\":\"dark_gray\",\"italic\":true}],minecraft:custom_data={jjk_finger:1b},minecraft:rarity=\"epic\",minecraft:max_stack_size=20,minecraft:consumable={consume_seconds:2.0f,animation:\"eat\",sound:\"minecraft:entity.generic.eat\",has_consume_particles:true},minecraft:food={nutrition:0,saturation:0f,can_always_eat:true}]",
            "jjk:exec_sword[minecraft:custom_data={gojo_exec:1b},minecraft:item_model=\"jjk:exec_sword\",minecraft:item_name={\"text\":\"사형 집행인의 검\",\"color\":\"gold\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"벤 대상은 반드시 죽는다 (60초)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"지니고 있는 동안 주복사사 봉인\",\"color\":\"dark_red\",\"italic\":false}]]",
            "jjk:inverted_spear[minecraft:custom_data={gojo_ten:1b},minecraft:item_model=\"jjk:inverted_spear\",minecraft:item_name={\"text\":\"천역모(天逆鉾)\",\"color\":\"dark_purple\",\"italic\":false},minecraft:lore=[{\"text\":\"닿은 술식을 강제로 해제한다\",\"color\":\"gray\",\"italic\":false},{\"text\":\"무하한을 꿰뚫는다\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantments={\"minecraft:loyalty\":3},minecraft:enchantment_glint_override=false,minecraft:attribute_modifiers=[{type:\"minecraft:attack_damage\",id:\"gojo:ten\",amount:11.0d,operation:\"add_value\",slot:\"mainhand\"},{type:\"minecraft:attack_speed\",id:\"gojo:ten_s\",amount:-2.6d,operation:\"add_value\",slot:\"mainhand\"}],minecraft:unbreakable={}]",
            "jjk:purify_orb[minecraft:item_name={\"text\":\"정화의 구\",\"color\":\"#FFF2B0\",\"italic\":false},minecraft:lore=[{\"text\":\"삼키면 몸에 깃든 스쿠나의 손가락이 모두 정화된다\",\"color\":\"gray\",\"italic\":false}],minecraft:custom_data={jjk_purify:1b},minecraft:rarity=\"epic\",minecraft:enchantment_glint_override=true,minecraft:consumable={consume_seconds:1.6f,animation:\"drink\",sound:\"minecraft:block.amethyst_block.resonate\",has_consume_particles:false},minecraft:food={nutrition:0,saturation:0f,can_always_eat:true}]",
            "jjk:gojo_blindfold[minecraft:lore=[{\"text\":\"머리에 쓰기: 눈을 가려 육안·급식안 투시 봉인 · 무하한(투사체 정지·근접 피해 절반)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 육안 + 검은 양털 3 / 고죠 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:projection_frame[minecraft:item_name={\"text\":\"투사주법 「투사 가속」\",\"color\":\"#7CFF6A\",\"italic\":false},minecraft:lore=[{\"text\":\"젠인 나오야\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"켜기/끄기 · 24프레임 가속 · 잔상 · 공격 회피\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:projection_slice[minecraft:item_name={\"text\":\"투사주법 「24프레임 연격」\",\"color\":\"#7CFF6A\",\"italic\":false},minecraft:lore=[{\"text\":\"젠인 나오야\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"대상을 1초 동안 24컷으로 사방에서 베고 지나감\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:projection_freeze[minecraft:item_name={\"text\":\"투사주법 「프레임 고정」\",\"color\":\"#FFFFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"젠인 나오야\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"룰: 대상을 틀에 가둬 3초간 정지\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:projection_rush[minecraft:item_name={\"text\":\"투사주법 「24fps 폭주」\",\"color\":\"#B8FFB0\",\"italic\":false},minecraft:lore=[{\"text\":\"젠인 나오야\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"6초간 주변 전원을 묶고 초고속 난타\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:blood_bind[minecraft:item_name={\"text\":\"적혈조술 「적박」\",\"color\":\"#C01020\",\"italic\":false},minecraft:lore=[{\"text\":\"초소\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"붉은 사슬로 대상을 4초 속박 · 체력 소모\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:blood_pierce[minecraft:item_name={\"text\":\"적혈조술 「백렴·천혈」\",\"color\":\"#FF3040\",\"italic\":false},minecraft:lore=[{\"text\":\"초소\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"피를 압축해 1초 뒤 관통 광선 · 체력 소모\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:blood_nova[minecraft:item_name={\"text\":\"적혈조술 「초신성」\",\"color\":\"#C01020\",\"italic\":false},minecraft:lore=[{\"text\":\"초소\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"피 구체 5발 연사, 닿으면 폭발 · 체력 소모\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:blood_scale[minecraft:item_name={\"text\":\"적혈조술 「적린」\",\"color\":\"#FF3040\",\"italic\":false},minecraft:lore=[{\"text\":\"초소\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"15초간 근력·속도·내성 강화 · 체력 소모\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_scroll[minecraft:enchantment_glint_override=true,minecraft:item_name={\"text\":\"주령 소환서\",\"color\":\"#8040C0\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 선택한 주령 소환 · 웅크리고 우클릭: 종류 변경\",\"color\":\"gray\",\"italic\":false},{\"text\":\"4급 파리머리·손가락·눈알 / 3급 인형·지네·입 / 2급 거미·백귀·사신 / 1급 거인·저주룡 / 특급 저주의 왕\",\"color\":\"dark_gray\",\"italic\":false}]]",
            "jjk:spirit_egg_fly[minecraft:item_name={\"text\":\"파리머리 소환 알 (4급)\",\"color\":\"#9060C0\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_hand[minecraft:item_name={\"text\":\"손가락 주령 소환 알 (4급)\",\"color\":\"#D8D0C0\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_eye[minecraft:item_name={\"text\":\"눈알 주령 소환 알 (4급)\",\"color\":\"#F0F0F0\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_doll[minecraft:item_name={\"text\":\"왜곡 인형 소환 알 (3급)\",\"color\":\"#C89090\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_centi[minecraft:item_name={\"text\":\"지네 주령 소환 알 (3급)\",\"color\":\"#A02020\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_maw[minecraft:item_name={\"text\":\"입 주령 소환 알 (3급)\",\"color\":\"#B04080\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_spider[minecraft:item_name={\"text\":\"거미 주령 소환 알 (2급)\",\"color\":\"#9060C0\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_hundred[minecraft:item_name={\"text\":\"백귀 소환 알 (2급)\",\"color\":\"#E8E8E8\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_reaper[minecraft:item_name={\"text\":\"사신 소환 알 (2급)\",\"color\":\"#50307A\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_giant[minecraft:item_name={\"text\":\"저주 거인 소환 알 (1급)\",\"color\":\"#FF7A00\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_dragon[minecraft:item_name={\"text\":\"저주룡 소환 알 (1급)\",\"color\":\"#8030C0\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:spirit_egg_king[minecraft:item_name={\"text\":\"저주의 왕 소환 알 (특급)\",\"color\":\"#FFD84A\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:meg_dogs[minecraft:item_name={\"text\":\"십종영법 「옥견」\",\"color\":\"#E8E8F0\",\"italic\":false},minecraft:lore=[{\"text\":\"후시구로 메구미\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"흰 개와 검은 개를 소환 · 6초간 적을 물어뜯음\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:meg_rabbits[minecraft:item_name={\"text\":\"십종영법 「탈토」\",\"color\":\"#FFFFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"후시구로 메구미\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"토끼 떼가 퍼져나가며 실명·둔화 (반경 15)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:meg_serpent[minecraft:item_name={\"text\":\"십종영법 「대사」\",\"color\":\"#2E7D32\",\"italic\":false},minecraft:lore=[{\"text\":\"후시구로 메구미\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"큰 뱀이 돌진해 일직선을 관통 (32)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:nob_nail[minecraft:item_name={\"text\":\"공명 「못박기」\",\"color\":\"#FF9020\",\"italic\":false},minecraft:lore=[{\"text\":\"쿠기사키 노바라\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"못을 쏘아 대상에 공명 표식을 남김 (12)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:nob_resonance[minecraft:item_name={\"text\":\"공명 「공명」\",\"color\":\"#FFD080\",\"italic\":false},minecraft:lore=[{\"text\":\"쿠기사키 노바라\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"표식이 붙은 대상을 원격으로 공명 폭발 (40 · 주변 18)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:nob_hairpin[minecraft:item_name={\"text\":\"공명 「철정」\",\"color\":\"#C8D0E0\",\"italic\":false},minecraft:lore=[{\"text\":\"쿠기사키 노바라\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"정면 최대 8명에게 못을 일제 사출 (각 16)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:nan_ratio[minecraft:item_name={\"text\":\"칠할삼분 「십수할」\",\"color\":\"#FFE070\",\"italic\":false},minecraft:lore=[{\"text\":\"나나미 켄토\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"대상 앞으로 이동해 7:3 약점 일격 (18 + 최대체력 10%)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:nan_overtime[minecraft:item_name={\"text\":\"칠할삼분 「초과근무」\",\"color\":\"#FFE070\",\"italic\":false},minecraft:lore=[{\"text\":\"나나미 켄토\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"12초간 근력·속도·내성 강화 · 끝나면 피로\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:nan_cleave[minecraft:item_name={\"text\":\"칠할삼분 「해체」\",\"color\":\"#FFF0B0\",\"italic\":false},minecraft:lore=[{\"text\":\"나나미 켄토\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"전방 부채꼴 6칸 참격 (26)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:yut_rika[minecraft:item_name={\"text\":\"리카 「리카 완전현현」\",\"color\":\"#FFFFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"오코츠 유타\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"리카를 15초간 현현 · 근처 적을 자동 공격 (궁극기)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:yut_copy[minecraft:item_name={\"text\":\"리카 「모방」\",\"color\":\"#C080FF\",\"italic\":false},minecraft:lore=[{\"text\":\"오코츠 유타\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"마지막에 쓴 술식을 공짜로 다시 사용\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:yut_love[minecraft:item_name={\"text\":\"리카 「순애빔」\",\"color\":\"#FF60C0\",\"italic\":false},minecraft:lore=[{\"text\":\"오코츠 유타\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"충전 후 폭 3의 거대 광선 (50)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:toj_halberd[minecraft:item_name={\"text\":\"천여주박 「천역모 투척」\",\"color\":\"#7CFF6A\",\"italic\":false},minecraft:lore=[{\"text\":\"후시구로 토우지\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"천역모를 던져 일직선을 관통 · 방어 무시 (30)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:toj_dash[minecraft:item_name={\"text\":\"천여주박 「순보」\",\"color\":\"#E0FFE0\",\"italic\":false},minecraft:lore=[{\"text\":\"후시구로 토우지\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"바라보는 방향 9칸 순간 돌진 · 경로의 적 타격\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:toj_heaven[minecraft:item_name={\"text\":\"천여주박 「주구 해방」\",\"color\":\"#2E7D32\",\"italic\":false},minecraft:lore=[{\"text\":\"후시구로 토우지\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"20초간 근력·속도·점프 강화\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:jacob_ladder[minecraft:item_name={\"text\":\"야곱의 사다리\",\"color\":\"#FFE070\",\"italic\":false},minecraft:lore=[{\"text\":\"쿠루스 하나\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"대상 위로 빛의 사다리를 내리꽂음 · 강화 효과 소거\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:jacob_climb[minecraft:item_name={\"text\":\"승천의 사다리\",\"color\":\"#FFFFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"쿠루스 하나\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"빛의 사다리를 타고 하늘로 솟구침 · 낙하 무효\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:jacob_judge[minecraft:item_name={\"text\":\"심판의 사다리\",\"color\":\"#FFE070\",\"italic\":false},minecraft:lore=[{\"text\":\"쿠루스 하나\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"사다리 여러 줄을 연달아 내림 (궁극기)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:reverse_heal[minecraft:item_name={\"text\":\"반전술식 「치유」\",\"color\":\"#FFE9A0\",\"italic\":false},minecraft:lore=[{\"text\":\"범용\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"체력 회복 · 재생 · 나쁜 효과 제거\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:reverse_out[minecraft:item_name={\"text\":\"반전술식 「출력」\",\"color\":\"#FFE9A0\",\"italic\":false},minecraft:lore=[{\"text\":\"범용\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"정(正)의 에너지 관통 광선\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:job_scroll[minecraft:item_name={\"text\":\"직업 선택서\",\"color\":\"#FFD84A\",\"italic\":false},minecraft:lore=[{\"text\":\"직업\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"우클릭: 직업 고르기 · 웅크리고 우클릭: 확정 (직업을 골라야 술식 창이 나타남)\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true]",
            "jjk:jujutsu_guide[minecraft:item_name={\"text\":\"주술 설명서\",\"color\":\"#FFD84A\",\"italic\":false},minecraft:lore=[{\"text\":\"직업 · 해금 · 모든 능력의 이름과 효과\",\"color\":\"gray\",\"italic\":false},{\"text\":\"우클릭: 다음 쪽 / 웅크리고 우클릭: 이전 쪽\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true]",
            "jjk:dev_weapon_guide[minecraft:item_name={\"text\":\"개발자 무기 설명서\",\"color\":\"#40E8FF\",\"italic\":false},minecraft:lore=[{\"text\":\"개발자 무기 · 탈것 · 열쇠 조합법\",\"color\":\"gray\",\"italic\":false},{\"text\":\"우클릭: 다음 쪽 / 웅크리고 우클릭: 이전 쪽\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true]"};
    public static final String[] DEV = {
            "jjk:dev_token[minecraft:item_model=\"jjk:dev_token\",minecraft:custom_name=\"개발자의 토큰\",minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"회옥 · 옥절\",\"color\":\"gray\",\"italic\":true}]]",
            "jjk:dev_token2[minecraft:item_model=\"jjk:dev_token2\",minecraft:custom_name=\"개발자의 토큰2\",minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"시부야 사변\",\"color\":\"gray\",\"italic\":true}]]",
            "jjk:dev_token3[minecraft:item_model=\"jjk:dev_token3\",minecraft:custom_name=\"개발자 토큰3\",minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"개발자의 귀여운 탑승물\",\"color\":\"aqua\",\"italic\":true}]]",
            "jjk:dev_token4[minecraft:item_model=\"jjk:dev_token4\",minecraft:custom_name=\"개발자 토큰4\",minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"개발자의 귀여운 탑승물2\",\"color\":\"gold\",\"italic\":true}]]",
            "jjk:dev_token_final[minecraft:item_model=\"jjk:dev_token_final\",minecraft:custom_name={\"text\":\"개발자의 최종토큰\",\"color\":\"#FFD84A\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"개발자의 매우 귀여운 탑승물\",\"color\":\"aqua\",\"italic\":true}]]",
            "jjk:dev_machine_token[minecraft:item_model=\"jjk:dev_machine_token\",minecraft:custom_name={\"text\":\"개발자의 기계 토큰\",\"color\":\"#FF4040\",\"bold\":true,\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:max_stack_size=1,minecraft:lore=[{\"text\":\"지금까지의 모든 기계를 응축한 것\",\"color\":\"gray\",\"italic\":true},{\"text\":\"우클릭: 개발자의 기계 소환·탑승·귀환\",\"color\":\"aqua\",\"italic\":false}]]",
            "jjk:command_guard[minecraft:item_model=\"minecraft:command_block\",minecraft:custom_name={\"text\":\"커맨드 수호\",\"color\":\"#B45CFF\",\"bold\":true,\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:max_stack_size=1,minecraft:lore=[{\"text\":\"개발자의 토큰을 모두 합친 것\",\"color\":\"gray\",\"italic\":true},{\"text\":\"우클릭 소환·공격 / 웅크리고 우클릭 「/kill」 / 하늘 보며 웅크리고 우클릭 해제\",\"color\":\"aqua\",\"italic\":false}]]",
            "jjk:error_common[minecraft:item_model=\"jjk:error_common\",minecraft:custom_name={\"text\":\"일반 오류\",\"color\":\"#FF5050\",\"italic\":false},minecraft:lore=[{\"text\":\"버그걸린 주민에게서 · 커맨데이션 재료\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:error_copy[minecraft:item_model=\"jjk:error_copy\",minecraft:custom_name={\"text\":\"복사 오류\",\"color\":\"#55FFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"버그걸린 피글린에게서 · 커맨데이션 재료\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:error_chain[minecraft:item_model=\"jjk:error_chain\",minecraft:custom_name={\"text\":\"체인 오류\",\"color\":\"#B45CFF\",\"italic\":false},minecraft:lore=[{\"text\":\"버그걸린 엔더맨에게서 · 커맨데이션 재료\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:purify_object[minecraft:item_model=\"jjk:purify_object\",minecraft:custom_name={\"text\":\"정화의 물체\",\"color\":\"white\",\"italic\":false},minecraft:lore=[{\"text\":\"적대 정화 우민(좀비 주민 15%%)에게서 · 커맨데이션 재료\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:bug_egg_v[minecraft:item_model=\"jjk:bug_egg_v\",minecraft:custom_name={\"text\":\"버그걸린 주민 소환 알\",\"color\":\"#FF5050\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 버그걸린 주민 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:bug_egg_p[minecraft:item_model=\"jjk:bug_egg_p\",minecraft:custom_name={\"text\":\"버그걸린 피글린 소환 알\",\"color\":\"#55FFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 버그걸린 피글린 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:bug_egg_e[minecraft:item_model=\"jjk:bug_egg_e\",minecraft:custom_name={\"text\":\"버그걸린 엔더맨 소환 알\",\"color\":\"#B45CFF\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 버그걸린 엔더맨 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:purified_egg[minecraft:item_model=\"jjk:purified_egg\",minecraft:custom_name={\"text\":\"정화 우민 소환 알\",\"color\":\"white\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 바라보는 곳에 적대 정화 우민 소환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:commandation[minecraft:item_model=\"jjk:commandation\",minecraft:custom_name={\"text\":\"커맨데이션\",\"color\":\"#FFD84A\",\"italic\":false},minecraft:lore=[{\"text\":\"오류 셋 + 정화의 물체 · 인피니티 키 재료\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:infinity_key[minecraft:item_model=\"jjk:infinity_key\",minecraft:custom_name={\"text\":\"인피니티 키\",\"color\":\"aqua\",\"italic\":false},minecraft:lore=[{\"text\":\"수호 소환 중 우클릭: 숨겨진 필살기 5종 봉인 해제\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:dev_eye[minecraft:custom_name={\"text\":\"개발의 눈\",\"color\":\"aqua\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 개발자와 보스전 (3페이지)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 어 형이야~ 4 + 인피니티 키 4 + 코드 1\",\"color\":\"dark_gray\",\"italic\":false}]]",
            "jjk:dev_signature[minecraft:custom_name={\"text\":\"개발자의 사인\",\"color\":\"gold\",\"italic\":false},minecraft:lore=[{\"text\":\"「다음 업데이트 때 보자~」\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:gojo_key[minecraft:custom_name={\"text\":\"고죠의 열쇠\",\"color\":\"aqua\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 고죠 사토루와 보스전\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 육안 + 다이아 블록 + 네더라이트 주괴 + 황금걸쇠\",\"color\":\"dark_gray\",\"italic\":false}]]",
            "jjk:dev_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"개발자 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:gojo_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"고죠 사토루 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:dev_scissors[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"웅크리고 우클릭: 거대한 가위 손 소환/해제\",\"color\":\"gray\",\"italic\":false},{\"text\":\"우클릭: 바라보는 곳을 싹둑 ×2 (150)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:dev_cloth[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"웅크리고 우클릭: 거대한 보자기 손 소환/해제\",\"color\":\"gray\",\"italic\":false},{\"text\":\"우클릭: 위에서 덮쳐 감싸기 (반경 6, 120 + 둔화·실명)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:dev_fist[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"웅크리고 우클릭: 거대한 주먹 소환/해제\",\"color\":\"gray\",\"italic\":false},{\"text\":\"우클릭: 로켓 펀치 (폭발 반경 5, 200)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:dev_worm[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 굴착기 MK-0 소환·탑승 / 탑승 중 우클릭: 드릴 돌진\",\"color\":\"gray\",\"italic\":false},{\"text\":\"W 전진 · 달리기 부스트 · 점프 위 · 웅크리기 아래 · 웅크리고 우클릭: 내리기/귀환\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:legend_sword[minecraft:custom_name={\"text\":\"레전드 소드\",\"color\":\"gold\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"레전드 + '어 형이야~' 검\",\"color\":\"gray\",\"italic\":false},{\"text\":\"선택받은 자만 들 수 있다 (아니면 땅에 박힘)\",\"color\":\"yellow\",\"italic\":false},{\"text\":\"우클릭: 레전드 슬래시 · 웅크리고 우클릭: 전설의 심판\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:chosen_light[minecraft:custom_name={\"text\":\"선택의 빛\",\"color\":\"yellow\",\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 전설의 검에게 선택받는다\",\"color\":\"gray\",\"italic\":false},{\"text\":\"크랄루 격파 시 드랍\",\"color\":\"dark_gray\",\"italic\":false}]]",
            "jjk:dimension_beacon[minecraft:custom_name={\"text\":\"차원 신호기\",\"color\":\"light_purple\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 바라보는 땅에 설치 → 차원 균열에서 파괴자·몰살자·파수꾼 중 하나 등장\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 우는 흑요석 4 + 엔더의 눈 4 + 신호기\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:dimension_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"차원의 침입자 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:red_lego",
            "jjk:yellow_lego",
            "jjk:blue_lego",
            "jjk:hell_key[minecraft:lore=[{\"text\":\"팬텀 막 + 엔더의 눈 + 블레이즈 가루\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_key[minecraft:custom_name={\"text\":\"코밍의 열쇠\",\"color\":\"gold\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 코밍과 보스전 (2페이지)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 빨강·파랑·노랑 레고 + 지옥의 열쇠\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:jammin_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"코밍 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:myeong_key[minecraft:lore=[{\"text\":\"빨간 콘크리트 + 지옥의 열쇠\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:binding[minecraft:lore=[{\"text\":\"네더 벽돌 + 엔드 돌 벽돌 + 철 블록\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:sukuna_key[minecraft:custom_name={\"text\":\"스쿠나 열쇠\",\"color\":\"dark_red\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 료멘 스쿠나와 보스전\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 속박 2 + 명명백백 열쇠\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:sukuna_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"료멘 스쿠나 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:machine_part[minecraft:lore=[{\"text\":\"철 블록 + 레드스톤 블록 + 에메랄드\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:illager_key[minecraft:lore=[{\"text\":\"회색 양털 + 회색 콘크리트\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:doctor_key[minecraft:custom_name={\"text\":\"박사의 열쇠\",\"color\":\"aqua\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 우민 박사와 보스전\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 우민 열쇠 + 기계장치\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:robot_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우민 박사 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:hacker_key[minecraft:custom_name={\"text\":\"해커의 열쇠\",\"color\":\"green\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 해커와 보스전 (2페이지)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 코드 4(모서리) + 양자블록 4(변) + 황금걸쇠(중앙)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:hacker_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"해커 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:donquixote_key[minecraft:custom_name={\"text\":\"돈키호테의 열쇠\",\"color\":\"gold\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 라 만차의 기사 돈키호테와 결투 (2페이지)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 책 4(모서리) + 철 블록 4(변) + 황금 투구(중앙)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:worldview_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"돈키호테 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:curse[minecraft:lore=[{\"text\":\"검은 콘크리트 8 + 속박(중앙)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:sun_stone[minecraft:lore=[{\"text\":\"노란 콘크리트 + 발광석\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:kralu_key[minecraft:custom_name={\"text\":\"크랄루의 열쇠\",\"color\":\"gold\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 저주의 신 크랄루와 보스전 (2페이지)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"조합: 황금걸쇠 + 태양석 + 저주\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:ugh_code[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"크랄루 격파 드랍\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:all_all[minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"모든 code 9개를 합친 것\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:code_master_key[minecraft:custom_name={\"text\":\"코드의 대열쇠\",\"color\":\"gold\",\"italic\":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{\"text\":\"우클릭: 개발자 재등장 (롤백 · 어려움, 3페이지 + 부활)\",\"color\":\"red\",\"italic\":false},{\"text\":\"고죠·코밍·명명백백·스쿠나·우민·박사·해커·돈키호테·크랄루 열쇠를 합친 것\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:all_orb[minecraft:item_name={\"text\":\"만물의 구\",\"color\":\"#FFFFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"개발자\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"우클릭: 모든 주술 능력 해금 · 능력이 추가될 때마다 함께 갱신\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true]",
            "jjk:dev_glove[minecraft:item_name={\"text\":\"개발자의 장갑\",\"color\":\"#40E8FF\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"들고 있으면 팔이 기계팔로\",\"color\":\"dark_aqua\",\"italic\":true},{\"text\":\"우클릭: 톱니 충전 후 관통 레이저\",\"color\":\"gray\",\"italic\":false},{\"text\":\"웅크리고 우클릭: 회전 톱니 레이저 난사 3초\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:rarity=\"epic\"]",
            "jjk:dev_gesture[minecraft:enchantment_glint_override=true,minecraft:item_name={\"text\":\"개발자의 손동작\",\"color\":\"#FF3040\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"우클릭: 손가락이 돋아나며 복마어주자의 인을 맺음 → 사당 전개, 6초간 참격\",\"color\":\"dark_red\",\"italic\":false},{\"text\":\"주력 소모 없음 · 쿨타임 45초\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:ctrl_z[minecraft:item_model=\"jjk:ctrl_z\",minecraft:custom_name={\"text\":\"개발자의 키캡 「Ctrl+Z」\",\"color\":\"#55DDFF\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"세계에 실행 취소를 건다\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"지니면 지난 10초를 기록 (위치·체력·몹·블록·내 공격)\",\"color\":\"gray\",\"italic\":false},{\"text\":\"우클릭 Ctrl+Z: VHS 되감기로 10초 전으로 — 체력·부서진 블록 복원, 몹은 시간 멀미\",\"color\":\"gray\",\"italic\":false},{\"text\":\"웅크리고 우클릭 Ctrl+Y: 지난 10초의 내 공격을 잔상과 함께 ×1.5 재생\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:rarity=\"epic\"]",
            "jjk:golden_clasp[minecraft:item_model=\"jjk:golden_clasp\",minecraft:custom_name={\"text\":\"황금 걸쇠\",\"color\":\"gold\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"커맨드 수호의 잠긴 명령을 연다\",\"color\":\"yellow\",\"italic\":true},{\"text\":\"수호 소환 중 우클릭 · 일반/장난/희귀/영웅/★전설★\",\"color\":\"gray\",\"italic\":false},{\"text\":\"제작: 금 주괴 4 + 금 조각 4 + 철사 덫 갈고리\",\"color\":\"dark_gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:max_stack_size=16]",
            "jjk:archmage_book[minecraft:item_model=\"jjk:archmage_book\",minecraft:custom_name={\"text\":\"우민의 대마법서\",\"color\":\"dark_purple\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"읽는 순간 우민의 대마법사가 강림한다\",\"color\":\"light_purple\",\"italic\":true},{\"text\":\"우클릭: 보스전 시작 (우민 퀴즈 주의)\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:rarity=\"epic\"]",
            "jjk:dev_phone[minecraft:item_model=\"jjk:dev_phone\",minecraft:custom_name={\"text\":\"개발자의 초 울트라 진짜 겁나 짱짱 센 슈퍼 울트라 데너레이터 맥스 얼티메이트 그레이트 레볼루션스페셜 네임드 인피니티 오메가 레전드 더 엔드 매직 쉴드 핸드폰\",\"color\":\"#FF8AD8\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"이름에 들어간 단어마다 앱이 하나씩\",\"color\":\"light_purple\",\"italic\":true},{\"text\":\"Z/C: 앱 넘기기 · 우클릭: 실행 · 들고 있으면 힘 II·신속\",\"color\":\"gray\",\"italic\":false},{\"text\":\"매직쉴드 인피니티 오메가 더엔드 데너레이터 레볼루션 네임드 레전드 그레이트 스페셜 얼티메이트 전화\",\"color\":\"dark_gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:rarity=\"epic\"]",
            "jjk:dev_lever[minecraft:item_model=\"jjk:dev_lever\",minecraft:custom_name={\"text\":\"개발자의 레버\",\"color\":\"green\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"세계의 디버그 모드 스위치\",\"color\":\"dark_green\",\"italic\":true},{\"text\":\"우클릭(내림): 반경 48 모든 몹·투사체 정지 · 준 피해는 변경 로그에 쌓임\",\"color\":\"gray\",\"italic\":false},{\"text\":\"다시 우클릭(올림): COMMIT — 로그 ×2 일괄 적용 · 투사체 반사\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:rarity=\"epic\"]",
            "jjk:dev_button[minecraft:item_model=\"jjk:dev_button\",minecraft:custom_name={\"text\":\"개발자의 버튼\",\"color\":\"red\",\"bold\":true,\"italic\":false},minecraft:lore=[{\"text\":\"절대 누르지 마시오\",\"color\":\"dark_red\",\"italic\":true},{\"text\":\"우클릭: 바라보는 곳을 지름 100블럭 삭제\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true,minecraft:rarity=\"epic\"]"};
            "jjk:structure_spawner[minecraft:lore=[{\"text\":\"개발자 전용 · 설계도로 작동하는 건축물을 소환\",\"color\":\"aqua\",\"italic\":false},{\"text\":\"우클릭: 선택한 설계도 소환 · 웅크리고 우클릭: 설계도 관리\",\"color\":\"gray\",\"italic\":false},{\"text\":\"OP 레벨 2 이상 또는 태그 jjk.blueprint 필요\",\"color\":\"dark_gray\",\"italic\":false}],minecraft:enchantment_glint_override=true]",

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JjkMod.MOD_ID);

    static final String[] KOMING = {
            "jjk:koming_eye[minecraft:item_name={\"text\":\"급식안\",\"color\":\"#7FD8FF\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"지니면 영적 시야·능력자 감지·루카력 조절(소모 5%) · 우클릭: 루카력 분석\",\"color\":\"gray\",\"italic\":false}],minecraft:enchantment_glint_override=true]",
            "jjk:koming_sik[minecraft:item_name={\"text\":\"술식순전 「식(吸)」\",\"color\":\"#3A7BFF\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"열량을 지워 공간째 빨아들이는 푸른 구체\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_gyo[minecraft:item_name={\"text\":\"술식반전 「교(膠)」\",\"color\":\"#FF2A2A\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"공간을 밀어내며 날아가 접촉 시 과부하 폭발\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_muryeo[minecraft:item_name={\"text\":\"급식 「무려3끼」\",\"color\":\"#B050FF\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"존재 자체를 소거 · 연속 사용 시 무려(n+2)끼\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_lego[minecraft:item_name={\"text\":\"술식전개 「레고사주자」\",\"color\":\"#FF3030\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"네 뿔의 레고 신전 · 반경 200 십자 투사체\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_shield[minecraft:item_name={\"text\":\"급식한\",\"color\":\"#9FD8FF\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"켜기/끄기 · 상대는 닿지 못하고 나는 닿는다\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_hunsu[minecraft:item_name={\"text\":\"술식전개 「무료훈수」\",\"color\":\"#55FFFF\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"PC방 · 반경 200 구속·능력 봉쇄·훈수 과부하\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_lunch[minecraft:item_name={\"text\":\"점심시간 유아독존\",\"color\":\"#FFD84A\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"주력 100% · 피로·술식 부담 초기화\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_yu[minecraft:item_name={\"text\":\"술력 「유(有)」\",\"color\":\"#FFE070\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"켜기/끄기 · 술식 강화·회복·방어 관통 (+급식안: 전투 예측)\",\"color\":\"gray\",\"italic\":false}]]",
            "jjk:koming_brain[minecraft:item_name={\"text\":\"뇌 파괴와 재생\",\"color\":\"#FF6060\",\"italic\":false},minecraft:lore=[{\"text\":\"코밍\",\"color\":\"aqua\",\"italic\":true},{\"text\":\"영역전개 후 술식·술식전개 과부하 해소\",\"color\":\"gray\",\"italic\":false}]]"};

    public static final RegistryObject<CreativeModeTab> JJK_TAB = TABS.register("jjk", () -> CreativeModeTab.builder()
            .title(Component.literal("주술회전"))
            .icon(() -> new ItemStack(JjkItems.BLUE.get()))
            .displayItems((params, out) -> { for (String s : JJK) add(params.holders(), out, s); })
            .build());

    public static final RegistryObject<CreativeModeTab> DEV_TAB = TABS.register("dev", () -> CreativeModeTab.builder()
            .title(Component.literal("개발자"))
            .icon(com.tubilt.jjk.mech.CommandGuard::item)
            .withTabsBefore(JJK_TAB.getKey())
            .displayItems((params, out) -> { for (String s : DEV) add(params.holders(), out, s); out.accept(com.tubilt.jjk.mech.CommandInker.item()); })
            .build());

    public static final RegistryObject<CreativeModeTab> KOMING_TAB = TABS.register("koming", () -> CreativeModeTab.builder()
            .title(Component.literal("코밍"))
            .icon(() -> new ItemStack(JjkItems.KOMING_EYE.get()))
            .withTabsBefore(DEV_TAB.getKey())
            .displayItems((params, out) -> { for (String s : KOMING) add(params.holders(), out, s); })
            .build());

    public static final RegistryObject<CreativeModeTab> DIRT_TAB = TABS.register("dirt", () -> CreativeModeTab.builder()
            .title(Component.literal("압축 흙 → 개발자 검"))
            .icon(() -> new ItemStack(JjkItems.DEV_SWORD.get()))
            .withTabsBefore(KOMING_TAB.getKey())
            .displayItems((params, out) -> {
                out.accept(new ItemStack(net.minecraft.world.item.Items.DIRT));
                for (String id : JjkItems.DIRT_IDS) out.accept(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(JjkMod.id(id))));
            })
            .build());

    static void add(HolderLookup.Provider reg, CreativeModeTab.Output out, String s) {
        try {
            ItemParser.ItemResult r = new ItemParser(reg).parse(new StringReader(s));
            out.accept(new ItemStack(r.item(), 1, r.components()));
        } catch (Exception e) {
            JjkMod.LOG.warn("[JJK] 크리에이티브 탭 아이템 해석 실패: {}", s, e);
        }
    }

    public static void init(BusGroup modBus) { TABS.register(modBus); }
}
