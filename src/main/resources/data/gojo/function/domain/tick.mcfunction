scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=gojo.closing] run return run function gojo:domain/closing_tick
execute if score @s gojo.life matches 2 as @e[type=block_display,tag=gojo.wnew] run function gojo:domain/grow
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players add @s gojo.adapt 1
execute if entity @s[tag=gojo.npc] if score @s gojo.adapt matches 1200.. run return run function gojo:domain/close
execute if score #found gojo.tmp matches 0 run return run function gojo:domain/close
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
particle minecraft:end_rod ~ ~2 ~ 9 6 9 0.02 25 force
particle minecraft:dust{color:[1.0,1.0,1.0],scale:0.6} ~ ~2 ~ 10 7 10 0 40 force
particle minecraft:dust{color:[0.55,0.75,1.0],scale:1} ~ ~2 ~ 10 7 10 0 15 force
particle minecraft:enchant ~ ~2 ~ 8 5 8 1 30 force
execute if predicate gojo:chance10 run particle minecraft:flash{color:[0.8,0.9,1.0,1.0]} ~ ~3 ~ 6 3 6 0 1 force
execute if entity @s[tag=gojo.clashing] run return 0
execute as @e[type=!#gojo:ignore,distance=..13.5] unless score @s gojo.id = #owner gojo.id if entity @s[tag=!gojo.hr] if data entity @s Health run function gojo:domain/victim
execute as @e[tag=gojo.trapped,distance=12.5..80] facing entity @s feet positioned ^ ^ ^11.5 run function gojo:domain/pullback
execute if score @s gojo.life matches 20 run tellraw @a[distance=..14] {"text":"무한한 정보가 쏟아져 들어온다...","color":"white","italic":true}
