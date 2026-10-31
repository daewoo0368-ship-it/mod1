scoreboard players set #found gojo.tmp 0
execute as @e[tag=gojo.sealed] if score @s gojo.prown = #owner gojo.id run function gojo:prison/hold_target
execute if score #found gojo.tmp matches 0 run return run function gojo:prison/release
execute if predicate gojo:chance10 run particle minecraft:end_rod ~ ~1.2 ~ 0.7 1 0.7 0 2
