title @a[distance=..32] times 0 50 15
title @a[distance=..32] title {"text":"유죄","color":"dark_red","bold":true}
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 0.5
execute store result score #pen gojo.tmp run random value 0..2
execute if entity @s[type=!player] run scoreboard players set #pen gojo.tmp 0
execute if score #pen gojo.tmp matches 0 run return run function gojo:hg/death
function gojo:hg/confiscate
