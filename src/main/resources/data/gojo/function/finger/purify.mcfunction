advancement revoke @s only gojo:eat_purify
execute unless score @s gojo.finger matches 1.. unless entity @s[tag=gojo.vessel] run return run title @s actionbar {"text":"정화할 것이 없다 — 몸은 이미 깨끗하다","color":"#FFF2B0"}
# 손가락으로 늘어난 주력 최대치 되돌림 (손가락 1개 = +200)
scoreboard players operation #pf gojo.finger = @s gojo.finger
scoreboard players set #200 gojo.finger 200
scoreboard players operation #pf gojo.finger *= #200 gojo.finger
scoreboard players operation @s gojo.cemax -= #pf gojo.finger
execute if score @s gojo.cemax matches ..999 run scoreboard players set @s gojo.cemax 1000
execute if score @s gojo.ce > @s gojo.cemax run scoreboard players operation @s gojo.ce = @s gojo.cemax
# 손가락 수·그릇·완전 부활 초기화 + 지속 강화 해제
scoreboard players set @s gojo.finger 0
tag @s remove gojo.vessel
tag @s remove gojo.sukuna
effect clear @s minecraft:strength
effect clear @s minecraft:resistance
effect clear @s minecraft:speed
effect clear @s minecraft:regeneration
effect clear @s minecraft:fire_resistance
effect give @s minecraft:instant_health 1 1 true
# 연출
title @s times 5 40 15
title @s title {"text":"정화","color":"#FFF2B0","bold":true}
title @s subtitle {"text":"스쿠나의 손가락이 모두 사라졌다","color":"white"}
tellraw @s {"text":"몸 안의 웃음소리가 멎었다... 저주의 기운이 빠져나간다.","color":"#FFF2B0","italic":true}
playsound minecraft:block.beacon.activate master @a[distance=..24] ~ ~ ~ 1 1.4
playsound minecraft:block.amethyst_block.resonate master @a[distance=..24] ~ ~ ~ 1.5 0.8
playsound minecraft:entity.wither.death master @a[distance=..24] ~ ~ ~ 0.4 1.6
particle minecraft:end_rod ~ ~1 ~ 0.4 0.8 0.4 0.08 80 force
particle minecraft:dust{color:[1.0,0.95,0.7],scale:1.8} ~ ~1 ~ 0.6 0.9 0.6 0 60 force
particle minecraft:dust{color:[0.55,0.0,0.05],scale:1.4} ~ ~1.4 ~ 0.3 0.4 0.3 0.05 40 force
particle minecraft:large_smoke ~ ~1.8 ~ 0.3 0.3 0.3 0.04 30 force
tag @s add gojo.fxflash_w
