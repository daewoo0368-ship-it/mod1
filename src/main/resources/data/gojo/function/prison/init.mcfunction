tag @s add gojo.pr
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute at @s run function gojo:prison/build_small
execute as @e[tag=gojo.prdnew] run function gojo:prison/disp_init
