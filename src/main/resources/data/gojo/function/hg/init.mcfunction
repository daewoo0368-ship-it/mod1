tag @s add gojo.hg
tag @s add gojo.dom
tp @s ~ ~ ~ ~ ~
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
scoreboard players set @s gojo.adapt -1
execute rotated as @s run function gojo:hg/build
function gojo:hg/shell
execute as @e[tag=gojo.hgsnew] run function gojo:hg/shell_init
execute rotated as @s as @e[tag=gojo.hgnew] run function gojo:hg/disp_init
