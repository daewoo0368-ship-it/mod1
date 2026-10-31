scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=gojo.closing] run return run function gojo:hk/closing_tick
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players set #found gojo.tmp 1
execute if score #found gojo.tmp matches 0 run return run function gojo:hk/close
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
particle minecraft:dust{color:[0.3,1.0,0.4],scale:1} ~ ~3 ~ 11 5 11 0 10 force
particle minecraft:dust{color:[1.0,0.9,0.2],scale:1} ~ ~3 ~ 11 5 11 0 10 force
particle minecraft:dust{color:[1.0,0.3,0.9],scale:1} ~ ~3 ~ 11 5 11 0 8 force
execute if entity @s[tag=gojo.clashing] run scoreboard players remove @s gojo.life 1
execute if entity @s[tag=gojo.clashing] run return 0
execute as @e[tag=gojo.htrap,distance=12.5..80] facing entity @s feet positioned ^ ^ ^11.5 run function gojo:domain/pullback
execute as @e[type=!#gojo:ignore,distance=..13.5,tag=!gojo.htrap] unless score @s gojo.id = #owner gojo.id if data entity @s Health run tag @s add gojo.htrap
scoreboard players operation #t gojo.tmp = @s gojo.life
scoreboard players operation #t gojo.tmp %= #80 gojo.cfg
tag @s add gojo.hkcur
execute if score #t gojo.tmp matches 1 run function gojo:hk/roll
execute if score #t gojo.tmp matches 1..55 run function gojo:hk/spin
tag @s remove gojo.hkcur
