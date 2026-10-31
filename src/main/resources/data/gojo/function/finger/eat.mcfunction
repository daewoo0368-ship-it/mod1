advancement revoke @s only gojo:eat_finger
# 이미 20개: 되돌려 줌
execute if score @s gojo.finger matches 20.. run give @s jjk:sukuna_finger[minecraft:item_model="jjk:sukuna_finger",minecraft:item_name={"text":"료멘 스쿠나의 손가락","color":"dark_red","italic":false},minecraft:lore=[{"text":"특급 주물 — 삼키면 스쿠나의 힘이 깃든다","color":"gray","italic":true},{"text":"모두 20개","color":"dark_gray","italic":true}],minecraft:custom_data={jjk_finger:1b},minecraft:rarity="epic",minecraft:max_stack_size=20,minecraft:consumable={consume_seconds:2.0f,animation:"eat",sound:"minecraft:entity.generic.eat",has_consume_particles:true},minecraft:food={nutrition:0,saturation:0f,can_always_eat:true}] 1
execute if score @s gojo.finger matches 20.. run return run title @s actionbar {"text":"이미 모든 손가락을 삼켰다 — 료멘 스쿠나","color":"dark_red"}
scoreboard players add @s gojo.finger 1
# 주력 최대치 +200 (최대 9000) 후 가득 채움
scoreboard players add @s gojo.cemax 200
execute if score @s gojo.cemax matches 9001.. run scoreboard players set @s gojo.cemax 9000
scoreboard players operation @s gojo.ce = @s gojo.cemax
# 연출
title @s times 5 40 15
title @s title {"text":"","color":"dark_red"}
title @s subtitle [{"text":"료멘 스쿠나의 손가락  ","color":"dark_red","bold":true},{"score":{"name":"@s","objective":"gojo.finger"},"color":"white","bold":true},{"text":" / 20","color":"gray"}]
playsound minecraft:entity.wither.ambient master @a[distance=..24] ~ ~ ~ 1 0.5
playsound minecraft:block.sculk_shrieker.shriek master @a[distance=..24] ~ ~ ~ 0.8 0.6
playsound minecraft:entity.evoker.prepare_summon master @a[distance=..24] ~ ~ ~ 1 0.6
particle minecraft:dust{color:[0.55,0.0,0.05],scale:1.6} ~ ~1 ~ 0.6 0.9 0.6 0 80 force
particle minecraft:soul ~ ~1 ~ 0.5 0.8 0.5 0.03 30 force
particle minecraft:large_smoke ~ ~0.5 ~ 0.6 0.3 0.6 0.02 30 force
tag @s add gojo.fxshake_s
tag @s add gojo.fxflash_r
# 첫 손가락: 그릇
execute if score @s gojo.finger matches 1 run tag @s add gojo.vessel
execute if score @s gojo.finger matches 1 run tellraw @s {"text":"몸 안에서 무언가가 웃는다... 스쿠나의 그릇이 되었다.","color":"dark_red","italic":true}
# 20개: 완전 부활
execute if score @s gojo.finger matches 20 run function gojo:finger/full
