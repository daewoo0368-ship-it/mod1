scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if score @s gojo.life matches 30.. run return run function gojo:wb/burst
particle minecraft:block{block_state:"minecraft:oak_log"} ~ ~ ~ 0.1 0.1 0.1 0 1
execute positioned ^ ^ ^0.6 unless block ~ ~ ~ #gojo:passable run return run function gojo:wb/burst
execute positioned ^ ^ ^0.6 as @e[type=!#gojo:ignore,distance=..1.3] unless score @s gojo.id = #owner gojo.id if data entity @s Health run tag @s add gojo.hit
execute if entity @e[tag=gojo.hit,limit=1] run return run function gojo:wb/burst
tp @s ^ ^ ^1.2 ~ ~0.7
