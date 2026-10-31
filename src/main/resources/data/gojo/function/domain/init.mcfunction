tag @s remove gojo.dnew
tag @s add gojo.dom
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute at @s run function gojo:domain/build
