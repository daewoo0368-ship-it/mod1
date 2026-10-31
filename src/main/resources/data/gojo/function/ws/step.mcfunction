scoreboard players add #s gojo.tmp 1
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..6.5] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #v gojo.tmp matches 0 run function gojo:ws/draw0
execute if score #v gojo.tmp matches 1 run function gojo:ws/draw1
execute if score #v gojo.tmp matches 2 run function gojo:ws/draw2
execute if score #v gojo.tmp matches 3 run function gojo:ws/draw3
execute if score #v gojo.tmp matches 4 run function gojo:ws/draw4
execute if score #v gojo.tmp matches 5 run function gojo:ws/draw5
execute if score #v gojo.tmp matches 6 run function gojo:ws/draw6
execute if score #v gojo.tmp matches 7 run function gojo:ws/draw7
execute as @e[type=!#gojo:ignore,distance=..4.5] unless score @s gojo.id = #owner gojo.id if data entity @s Health run function gojo:ws/hit
tp @s ^ ^ ^0.5
execute if score #s gojo.tmp matches ..119 at @s run function gojo:ws/step
