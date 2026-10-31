scoreboard players add #n gojo.tmp 1
execute unless block ~ ~ ~ #gojo:passable run return 0
execute if entity @e[type=!#gojo:ignore,type=!player,distance=..1.5] run return 0
tp @s ^ ^ ^1
execute if score #n gojo.tmp matches ..70 at @s run function gojo:meteor/ray
