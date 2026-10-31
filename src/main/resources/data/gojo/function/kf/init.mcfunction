tag @s remove gojo.kfnew
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute at @s run function gojo:kf/build
execute as @e[tag=gojo.kfdnew] run function gojo:kf/disp_init
