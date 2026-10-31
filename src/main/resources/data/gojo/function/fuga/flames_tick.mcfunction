scoreboard players add @s gojo.life 1
execute if score @s gojo.life matches 60.. run return run kill @s
scoreboard players operation #owner gojo.id = @s gojo.id
particle minecraft:flame ~ ~0.5 ~ 4 0.5 4 0.03 25 force
particle minecraft:large_smoke ~ ~2 ~ 3 1 3 0.02 5 force
execute if predicate gojo:chance10 run playsound minecraft:block.fire.ambient master @a ~ ~ ~ 1.5 0.7
execute if score @s gojo.life matches 10 run function gojo:fuga/field
execute if score @s gojo.life matches 30 run function gojo:fuga/field
execute if score @s gojo.life matches 50 run function gojo:fuga/field
