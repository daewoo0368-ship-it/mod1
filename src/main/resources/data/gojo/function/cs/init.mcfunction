tag @s add gojo.cs
tag @s add gojo.csany
tp @s ~ ~ ~ ~ 0
scoreboard players set @s gojo.life 0
data modify storage gojo:cs line set value {text:""}
execute as @a[distance=..64] at @s run function gojo:cs/view_on
execute positioned ^ ^ ^ run function gojo:cs/spawn_gojo
execute positioned ^ ^ ^11 run function gojo:cs/spawn_toji
summon item_display ~ ~2 ~-4 {Tags:["gojo.cscam"],teleport_duration:3}
summon marker ~ ~ ~ {Tags:["gojo.cslook"]}
effect give @a[tag=gojo.csv] minecraft:darkness 3 0 true
