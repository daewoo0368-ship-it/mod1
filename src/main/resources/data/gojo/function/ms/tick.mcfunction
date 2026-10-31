scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players set #found gojo.tmp 1
execute if entity @s[tag=gojo.npc] run scoreboard players add @s gojo.adapt 1
execute if entity @s[tag=gojo.npc] if score @s gojo.adapt matches 1200.. run return run function gojo:ms/close
execute if score #found gojo.tmp matches 0 run return run function gojo:ms/close
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
particle minecraft:crimson_spore ~ ~3 ~ 16 4 16 0 40 force
particle minecraft:dust{color:[0.6,0.0,0.0],scale:1.2} ~ ~3 ~ 16 4 16 0 20 force
particle minecraft:ash ~ ~3 ~ 16 4 16 0 30 force
execute if entity @s[tag=gojo.clashing] run return 0
function gojo:ms/cut
function gojo:ms/cut
function gojo:ms/cut
function gojo:ms/cut
execute if predicate gojo:half run playsound minecraft:entity.player.attack.sweep master @a ~ ~ ~ 2 0.8
execute if score @s gojo.life matches 5.. run scoreboard players set @s gojo.life 0
execute if score @s gojo.life matches 0 as @e[type=!#gojo:ignore,distance=..24] unless score @s gojo.id = #owner gojo.id if entity @s[tag=!gojo.hr] if data entity @s Health at @s run function gojo:ms/cleave
