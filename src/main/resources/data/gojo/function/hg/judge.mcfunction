scoreboard players set #pl gojo.tmp 2
execute if entity @s[type=player,scores={gojo.plead=1..3}] run scoreboard players operation #pl gojo.tmp = @s gojo.plead
execute if entity @s[type=!player] store result score #pl gojo.tmp run random value 1..3
execute if score #pl gojo.tmp matches 1 run tellraw @a[distance=..24] [{"selector":"@s","color":"white"},{"text":": \"...내가 했다.\" (자백)","color":"red"}]
execute if score #pl gojo.tmp matches 2 run tellraw @a[distance=..24] [{"selector":"@s","color":"white"},{"text":": \"...\" (묵비)","color":"yellow"}]
execute if score #pl gojo.tmp matches 3 run tellraw @a[distance=..24] [{"selector":"@s","color":"white"},{"text":": \"나는 결백하다!\" (반론)","color":"green"}]
scoreboard players set #ch gojo.tmp 60
execute if score #pl gojo.tmp matches 1 run scoreboard players set #ch gojo.tmp 100
execute if score #pl gojo.tmp matches 3 run scoreboard players set #ch gojo.tmp 35
execute if entity @s[type=player] if score @s gojo.kills matches 50.. run scoreboard players add #ch gojo.tmp 20
execute if entity @s[type=!player] run scoreboard players add #ch gojo.tmp 10
scoreboard players reset @s gojo.plead
execute store result score #r gojo.tmp run random value 1..100
execute if score #r gojo.tmp <= #ch gojo.tmp run return run function gojo:hg/guilty
function gojo:hg/innocent
