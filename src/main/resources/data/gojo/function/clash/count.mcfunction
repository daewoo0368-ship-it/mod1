scoreboard players add #n gojo.tmp 1
tp @s ^ ^ ^0.5
execute if score #n gojo.tmp matches ..160 at @s unless entity @e[type=marker,tag=gojo.cB,distance=..0.8] positioned ~ ~-2 ~ unless entity @e[type=marker,tag=gojo.cB,distance=..0.8] positioned ~ ~2 ~ run function gojo:clash/count
