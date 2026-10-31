tag @s add gojo.jtrap
particle minecraft:soul ~ ~1 ~ 0.4 0.7 0.4 0.03 15 force
particle minecraft:dust{color:[0.25,0.55,0.6],scale:1.5} ~ ~1 ~ 0.5 0.8 0.5 0 25 force
playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 1.5 0.6
execute if entity @s[type=!player,type=!#gojo:no_transfig,team=!gojo.mahito,tag=!gojo.maho] run return run function gojo:mahito/transfigure
execute if entity @s[team=gojo.mahito] run return 0
execute if entity @s[tag=gojo.hr] run function gojo:hr/break
damage @s 10 minecraft:magic
effect give @s minecraft:nausea 4 0
effect give @s minecraft:slowness 2 2
effect give @s minecraft:wither 2 1
execute unless entity @s[tag=gojo.deformed] run function gojo:mahito/deform
execute if entity @s[type=player] run title @s actionbar {"text":"영혼에 손이 닿았다 — 무위전변","color":"dark_aqua"}
