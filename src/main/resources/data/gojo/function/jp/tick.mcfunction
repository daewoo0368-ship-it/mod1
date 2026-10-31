scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=gojo.closing] run return run function gojo:jp/closing_tick
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players add @s gojo.adapt 1
execute if entity @s[tag=gojo.npc] if score @s gojo.adapt matches 1200.. run return run function gojo:jp/close
execute if score #found gojo.tmp matches 0 run return run function gojo:jp/close
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
particle minecraft:sculk_soul ~ ~2 ~ 10 6 10 0.01 8 force
particle minecraft:dust{color:[0.25,0.55,0.6],scale:1.2} ~ ~2 ~ 11 7 11 0 25 force
particle minecraft:dust{color:[0.15,0.12,0.18],scale:1.5} ~ ~2 ~ 11 7 11 0 15 force
execute if entity @s[tag=gojo.clashing] run return 0
execute as @e[tag=gojo.jtrap,distance=12.5..80] facing entity @s feet positioned ^ ^ ^11.5 run function gojo:domain/pullback
scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #20 gojo.cfg
execute if score #m gojo.tmp matches 0 as @e[type=!#gojo:ignore,distance=..13.5] unless score @s gojo.id = #owner gojo.id if data entity @s Health at @s run function gojo:jp/surehit
