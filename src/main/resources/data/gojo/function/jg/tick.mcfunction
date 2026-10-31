scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=gojo.closing] run return run function gojo:jg/closing_tick
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players add @s gojo.adapt 1
execute if entity @s[tag=gojo.npc] if score @s gojo.adapt matches 1200.. run return run function gojo:jg/close
execute if score #found gojo.tmp matches 0 run return run function gojo:jg/close
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
execute as @a[distance=..14] if score @s gojo.id = #owner gojo.id run effect give @s minecraft:fire_resistance 2 0 true
particle minecraft:flame ~ ~1 ~ 11 5 11 0.03 40 force
particle minecraft:lava ~ ~1 ~ 11 4 11 0 12 force
particle minecraft:ash ~ ~4 ~ 11 6 11 0 40 force
particle minecraft:dust{color:[1.0,0.35,0.0],scale:1.5} ~ ~2 ~ 11 6 11 0 20 force
particle minecraft:large_smoke ~ ~8 ~ 9 3 9 0.01 8 force
execute if predicate gojo:chance10 run playsound minecraft:block.lava.pop master @a ~ ~ ~ 2 0.6
execute if predicate gojo:chance10 run function gojo:jg/geyser
execute if entity @s[tag=gojo.clashing] run return 0
execute as @e[tag=gojo.gtrap,distance=12.5..80] facing entity @s feet positioned ^ ^ ^11.5 run function gojo:domain/pullback
scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #10 gojo.cfg
execute if score #m gojo.tmp matches 0 as @e[type=!#gojo:ignore,distance=..13.5] unless score @s gojo.id = #owner gojo.id if entity @s[tag=!gojo.hr] if data entity @s Health at @s run function gojo:jg/surehit
