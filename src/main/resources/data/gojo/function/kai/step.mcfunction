scoreboard players add #s gojo.tmp 1
execute if score @s gojo.ct matches 0 run function gojo:kai/draw0
execute if score @s gojo.ct matches 1 run function gojo:kai/draw1
execute if score @s gojo.ct matches 2 run function gojo:kai/draw2
execute if score @s gojo.ct matches 3 run function gojo:kai/draw3
execute if score @s gojo.ct matches 4 run function gojo:kai/draw4
execute if score @s gojo.ct matches 5 run function gojo:kai/draw5
execute if score @s gojo.ct matches 6 run function gojo:kai/draw6
execute if score @s gojo.ct matches 7 run function gojo:kai/draw7

execute as @e[type=!#gojo:ignore,distance=..2.8] unless score @s gojo.id = #owner gojo.id if data entity @s Health run function gojo:kai/hit
tp @s ^ ^ ^0.8
execute if score #s gojo.tmp matches ..1 at @s run function gojo:kai/step
