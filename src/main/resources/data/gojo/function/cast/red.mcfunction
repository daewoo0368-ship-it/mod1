scoreboard players operation #cast gojo.id = @s gojo.id
title @s actionbar {"text":"술식반전 「혁(赫)」","color":"red","bold":true}
execute if score #strong gojo.tmp matches 1 run title @a[distance=..48] title {"text":"술식반전 「혁」","color":"red","bold":true}
execute if score #strong gojo.tmp matches 1 run title @a[distance=..48] subtitle ""
playsound minecraft:entity.wither.shoot master @a ~ ~ ~ 1 1.2
playsound minecraft:entity.blaze.shoot master @a ~ ~ ~ 1 0.6
execute anchored eyes positioned ^ ^ ^1.5 summon marker run function gojo:red/init
