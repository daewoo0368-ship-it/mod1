execute store result entity @s Pos[0] double 1 run scoreboard players get #x gojo.tmp
execute store result entity @s Pos[1] double 1 run scoreboard players get #y gojo.tmp
execute store result entity @s Pos[2] double 1 run scoreboard players get #z gojo.tmp
execute store result entity @s Rotation[0] float 1 run random value 0..359
execute store result entity @s Rotation[1] float 1 run random value -30..30
execute store result score #cv gojo.tmp run random value 0..7
scoreboard players set #near gojo.tmp 0
execute at @s as @a[distance=..6] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute at @s if entity @e[type=block_display,tag=gojo.shrine,distance=..9] run scoreboard players set #near gojo.tmp 1
execute if score #cv gojo.tmp matches 0 at @s run function gojo:ms/draw0
execute if score #cv gojo.tmp matches 1 at @s run function gojo:ms/draw1
execute if score #cv gojo.tmp matches 2 at @s run function gojo:ms/draw2
execute if score #cv gojo.tmp matches 3 at @s run function gojo:ms/draw3
execute if score #cv gojo.tmp matches 4 at @s run function gojo:ms/draw4
execute if score #cv gojo.tmp matches 5 at @s run function gojo:ms/draw5
execute if score #cv gojo.tmp matches 6 at @s run function gojo:ms/draw6
execute if score #cv gojo.tmp matches 7 at @s run function gojo:ms/draw7
kill @s
