effect give @s minecraft:night_vision 15 0 true
execute unless entity @s[tag=gojo.hr] run scoreboard players add @s gojo.ce 2
execute if entity @s[tag=gojo.inf] run scoreboard players add @s gojo.ce 2
execute if entity @s[tag=gojo.downer] run scoreboard players add @s gojo.ce 2
scoreboard players operation #m gojo.tmp = #gt gojo.tmp
scoreboard players operation #m gojo.tmp %= #4 gojo.cfg
execute unless score #m gojo.tmp matches 0 run return 0
execute if entity @s[tag=jjk.blindfold] run return 0
tag @s add gojo.sixv
execute as @e[type=!#gojo:ignore,distance=1..40,tag=!gojo.hr] if data entity @s Health at @s run function gojo:six/see
execute as @e[type=marker,tag=gojo.dom,distance=..64] at @s run particle minecraft:dust{color:[0.5,0.9,1.0],scale:1.5} ~ ~1 ~ 6 3 6 0 10 force @a[tag=gojo.sixv]
tag @s remove gojo.sixv
