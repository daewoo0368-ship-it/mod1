scoreboard players add @s gojo.fuga 1
effect give @s minecraft:slowness 1 3 true
execute anchored eyes positioned ^ ^ ^ rotated ~ ~ run function gojo:fuga/bow
particle minecraft:flame ~ ~1 ~ 0.6 0.8 0.6 0.02 6 force
execute if score @s gojo.fuga matches 10 run playsound minecraft:entity.blaze.ambient master @a ~ ~ ~ 1.5 0.5
execute if score @s gojo.fuga matches 25.. run function gojo:fuga/shoot
