tag @s remove gojo.jnew
tag @s add gojo.dom
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute at @s run function gojo:jp/build
execute as @e[type=block_display,tag=gojo.jwnew] run function gojo:jp/wall_init
