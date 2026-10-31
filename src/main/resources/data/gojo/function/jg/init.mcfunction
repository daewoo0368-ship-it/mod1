tag @s remove gojo.gnew
tag @s add gojo.dom
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute at @s run function gojo:jg/build
execute as @e[type=block_display,tag=gojo.gwnew] run function gojo:jg/wall_init
