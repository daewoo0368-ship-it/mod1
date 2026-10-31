scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=gojo.closing] run return run function gojo:tk/closing_tick
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players add @s gojo.adapt 1
execute if entity @s[tag=gojo.npc] if score @s gojo.adapt matches 1200.. run return run function gojo:tk/close
execute if score #found gojo.tmp matches 0 run return run function gojo:tk/close
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
particle minecraft:cherry_leaves ~ ~4 ~ 11 5 11 0 30 force
particle minecraft:spore_blossom_air ~ ~2 ~ 11 3 11 0 15 force
particle minecraft:happy_villager ~ ~1 ~ 10 2 10 0 6 force
execute as @a[distance=..14] if score @s gojo.id = #owner gojo.id run effect give @s minecraft:regeneration 2 1 true
execute if entity @s[tag=gojo.clashing] run return 0
execute as @e[tag=gojo.ttrap,distance=12.5..80] facing entity @s feet positioned ^ ^ ^11.5 run function gojo:domain/pullback
scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #20 gojo.cfg
execute if score #m gojo.tmp matches 0 as @e[type=!#gojo:ignore,distance=..13.5] unless score @s gojo.id = #owner gojo.id if entity @s[tag=!gojo.hr] if data entity @s Health at @s run function gojo:tk/surehit
