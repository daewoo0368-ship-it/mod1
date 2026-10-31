scoreboard players set @p[tag=gojo.atk] gojo.hcd 20
title @p[tag=gojo.atk] actionbar {"text":"무위전변(無為転変)","color":"dark_aqua","bold":true}
particle minecraft:soul ~ ~1 ~ 0.4 0.7 0.4 0.03 25 force
particle minecraft:sculk_soul ~ ~1 ~ 0.4 0.7 0.4 0.03 15 force
particle minecraft:dust{color:[0.25,0.55,0.6],scale:1.6} ~ ~1 ~ 0.5 0.8 0.5 0 40 force
particle minecraft:dust{color:[0.15,0.15,0.25],scale:1.2} ~ ~1 ~ 0.5 0.8 0.5 0 25 force
playsound minecraft:block.sculk_shrieker.shriek master @a ~ ~ ~ 1 1.4
playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 2 0.6
playsound minecraft:entity.zombie_villager.converted master @a ~ ~ ~ 1.5 0.7
execute if entity @s[type=!player,type=!#gojo:no_transfig,team=!gojo.mahito,tag=!gojo.maho] run return run function gojo:mahito/transfigure
execute if entity @s[tag=gojo.hr] run function gojo:hr/break
damage @s 20 minecraft:magic by @p[tag=gojo.atk]
effect give @s minecraft:nausea 8 0
effect give @s minecraft:slowness 4 2
effect give @s minecraft:wither 4 1
function gojo:mahito/deform
