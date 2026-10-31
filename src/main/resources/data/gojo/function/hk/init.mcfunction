tag @s remove gojo.hknew
tag @s add gojo.dom
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
scoreboard players set @s gojo.adapt 0
execute at @s run function gojo:hk/build
execute as @e[type=block_display,tag=gojo.hwnew] run function gojo:hk/wall_init
