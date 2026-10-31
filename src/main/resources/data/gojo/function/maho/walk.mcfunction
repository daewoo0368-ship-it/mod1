scoreboard players add @s gojo.af 1
execute if score @s gojo.af matches 11.. run scoreboard players set @s gojo.af 1
execute if score @s gojo.af matches 1 run function gojo:maho/anim/walk_a
execute if score @s gojo.af matches 6 run function gojo:maho/anim/walk_b
execute if score @s gojo.af matches 1 run playsound minecraft:entity.iron_golem.step master @a ~ ~ ~ 1 0.5
execute if score @s gojo.af matches 6 run playsound minecraft:entity.iron_golem.step master @a ~ ~ ~ 1 0.5
