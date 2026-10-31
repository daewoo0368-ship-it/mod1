tag @s add gojo.met
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute facing entity @n[type=marker,tag=gojo.ray] feet run tp @s ~ ~ ~ ~ ~
execute at @s run function gojo:meteor/build
execute as @e[type=block_display,tag=gojo.mtnew] run function gojo:meteor/part_init
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 4 0.3
playsound minecraft:block.fire.ambient master @a ~ ~ ~ 4 0.5
