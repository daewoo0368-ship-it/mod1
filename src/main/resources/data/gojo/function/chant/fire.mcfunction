scoreboard players set #strong gojo.tmp 1
execute if score @s gojo.chant matches 1 run scoreboard players set @s gojo.cd 40
execute if score @s gojo.chant matches 2 run scoreboard players set @s gojo.cd 60
execute if score @s gojo.chant matches 3 run scoreboard players set @s gojo.cd 200
execute if score @s gojo.chant matches 4 run scoreboard players set @s gojo.cd 200
execute if score @s gojo.chant matches 4 run function gojo:ws/cast
execute if score @s gojo.chant matches 1 run function gojo:cast/blue
execute if score @s gojo.chant matches 2 run function gojo:cast/red
execute if score @s gojo.chant matches 3 run function gojo:cast/purple
scoreboard players set #strong gojo.tmp 0
scoreboard players set @s gojo.chant 0
