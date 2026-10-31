tag @s add gojo.cs2
tag @s add gojo.csany
tp @s ~ ~ ~ ~ 0
scoreboard players set @s gojo.life 0
execute store result score @s gojo.pos run data get entity @s Pos[0]
execute store result score @s gojo.chant run data get entity @s Pos[1]
execute store result score @s gojo.ct run data get entity @s Pos[2]
scoreboard players add @s gojo.ct 0
data modify storage gojo:cs2 line set value {text:""}
execute as @a[distance=..64] at @s run function gojo:cs2/view_on
summon minecraft:mannequin ~ ~ ~ {Tags:["gojo.cs2s","gojo.cs2actor"],hide_description:1b,NoGravity:1b,Invulnerable:1b,equipment:{chest:{id:"minecraft:leather_chestplate",components:{"minecraft:dyed_color":1909560}},legs:{id:"minecraft:leather_leggings",components:{"minecraft:dyed_color":1315860}},feet:{id:"minecraft:leather_boots",components:{"minecraft:dyed_color":1315860}}}}
execute as @a[tag=gojo.cs2p,limit=1] run data modify entity @e[type=mannequin,tag=gojo.cs2s,limit=1] profile set value {id:[I;0,0,0,0]}
execute as @a[tag=gojo.cs2p,limit=1] run data modify entity @e[type=mannequin,tag=gojo.cs2s,limit=1] profile.id set from entity @s UUID
execute if entity @a[tag=gojo.cs2mp] positioned ^ ^ ^12 run function gojo:cs2/maho_player
execute unless entity @a[tag=gojo.cs2mp] positioned ^ ^ ^12 summon iron_golem run function gojo:cs2/maho_init
summon item_display ~ ~2 ~-4 {Tags:["gojo.cs2cam","gojo.cs2actor"],teleport_duration:3}
summon marker ~ ~ ~ {Tags:["gojo.cs2look","gojo.cs2actor"]}
effect give @a[tag=gojo.cs2v] minecraft:darkness 3 0 true
