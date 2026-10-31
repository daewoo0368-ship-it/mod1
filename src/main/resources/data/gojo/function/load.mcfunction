scoreboard objectives add gojo.cd dummy
scoreboard objectives add gojo.life dummy
scoreboard objectives add gojo.id dummy
scoreboard objectives add gojo.tmp dummy
scoreboard objectives add gojo.cfg dummy
scoreboard objectives add gojo.chant dummy
scoreboard objectives add gojo.ct dummy
scoreboard objectives add gojo.hold dummy
scoreboard objectives add gojo.dcd dummy
scoreboard objectives add gojo.fuga dummy
scoreboard objectives add gojo.mcd dummy
scoreboard objectives add gojo.pos dummy
scoreboard objectives add gojo.adapt dummy
scoreboard objectives add gojo.acd dummy
scoreboard objectives add gojo.px dummy
scoreboard objectives add gojo.pz dummy
scoreboard objectives add gojo.af dummy
scoreboard objectives add gojo.atk dummy
scoreboard objectives add gojo.hcd dummy
scoreboard objectives add gojo.push dummy
scoreboard objectives add gojo.jcd dummy
scoreboard objectives add gojo.ucd dummy
scoreboard objectives add gojo.gcd dummy
scoreboard objectives add gojo.hrlock dummy
scoreboard objectives add gojo.pcd dummy
scoreboard objectives add gojo.hgcd dummy
scoreboard objectives add gojo.wcd dummy
scoreboard objectives add gojo.kcd dummy
scoreboard objectives add gojo.tcd dummy
scoreboard objectives add gojo.hkcd dummy
scoreboard objectives add gojo.cscd dummy
scoreboard objectives add gojo.ce dummy
scoreboard objectives add gojo.cemax dummy
scoreboard objectives add gojo.finger dummy
scoreboard objectives add gojo.r1 dummy
scoreboard objectives add gojo.r2 dummy
scoreboard objectives add gojo.r3 dummy
scoreboard objectives add gojo.jpt dummy
scoreboard objectives add gojo.hp dummy
scoreboard players set #80 gojo.cfg 80
scoreboard players set #7 gojo.cfg 7
scoreboard players set #3 gojo.cfg 3
scoreboard players set #4 gojo.cfg 4
scoreboard objectives add gojo.dfn dummy
scoreboard objectives add gojo.cft dummy
scoreboard objectives add gojo.ext dummy
scoreboard objectives add gojo.plead trigger
scoreboard objectives add gojo.kills minecraft.custom:minecraft.mob_kills
scoreboard objectives add gojo.prt dummy
scoreboard objectives add gojo.prown dummy
scoreboard objectives add gojo.ca dummy
scoreboard objectives add gojo.cb dummy
scoreboard objectives add gojo.cv dummy
scoreboard players set #200 gojo.cfg 200
scoreboard players set #100 gojo.cfg 100
bossbar add gojo:clash "영역 싸움"
bossbar set gojo:clash max 200
bossbar set gojo:clash color purple
bossbar set gojo:clash style notched_10
scoreboard objectives add gojo.dt dummy
team add gojo.mahito
team modify gojo.mahito friendlyFire false
team modify gojo.mahito color dark_aqua
scoreboard players set #-1 gojo.cfg -1
scoreboard players set #40 gojo.cfg 40
scoreboard players set #2 gojo.cfg 2
scoreboard players set #20 gojo.cfg 20
scoreboard players set #10 gojo.cfg 10
execute unless score #grief gojo.cfg matches 0.. run scoreboard players set #grief gojo.cfg 1
scoreboard players set #strong gojo.tmp 0
tellraw @a [{"text":"[주술회전 모드 0.3.0] ","color":"light_purple"},{"text":"파랑=창 / 빨강=혁 / 보라=무라사키 (우클릭, 쉬프트=영창) / 검정=흑섬(타격) / 부싯돌=무량공처 / 팬텀막=무하한 / 철검=해(쉬프트=세계참) / 화살=푸가 / 벽돌=복마어주자 / 뼈=마허라 / 썩은살점=무위전변 / 거미눈=자폐원돈과 / 네더사마귀=운 / 마그마크림=개관철위산 / 가죽=천여주박(+천역모) / 메아리조각=옥문강 / 책=주복사사 / 막대기=목구 / 분홍염료=공화 / 초록염료=타이코카이 / 금조각=좌살박도 / 육안(엔더의 눈 모양) / 레드스톤=주력 최대치+100(소지만 해도 발동)","color":"gray"}]
tellraw @a {"text":"키: R 시전 / Z·C 술식 변경 / V 술식 목록 (조작 설정에서 변경 가능)","color":"aqua"}
