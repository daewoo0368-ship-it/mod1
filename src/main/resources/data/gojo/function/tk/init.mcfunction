tag @s remove gojo.tknew
tag @s add gojo.dom
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute at @s run function gojo:tk/build
execute as @e[type=block_display,tag=gojo.twnew] run function gojo:tk/wall_init
