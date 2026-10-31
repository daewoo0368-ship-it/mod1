scoreboard players operation #nul gojo.id = @s gojo.id
scoreboard players set #did gojo.tmp 0
execute if entity @s[tag=gojo.inf] run scoreboard players set #did gojo.tmp 1
tag @s remove gojo.inf
effect clear @s[type=player] minecraft:resistance
execute if score @s gojo.chant matches 1.. run scoreboard players set #did gojo.tmp 1
scoreboard players set @s gojo.chant 0
execute if score @s gojo.fuga matches 1.. run scoreboard players set #did gojo.tmp 1
scoreboard players set @s gojo.fuga 0
execute as @e[type=marker,tag=gojo.dom,tag=!gojo.closing] if score @s gojo.id = #nul gojo.id run function gojo:ten/break_domain
execute if score #did gojo.tmp matches 1 if entity @s[type=player] unless score @s gojo.cd matches 40.. run scoreboard players set @s gojo.cd 40
execute if score #did gojo.tmp matches 1 run particle minecraft:flash{color:[0.6,0.3,0.8,1.0]} ~ ~1 ~ 0 0 0 0 1 force
execute if score #did gojo.tmp matches 1 run playsound minecraft:block.beacon.deactivate master @a ~ ~ ~ 2 1.5
execute if score #did gojo.tmp matches 1 if entity @s[type=player] run title @s actionbar {"text":"천역모 — 술식이 강제로 해제되었다","color":"dark_purple"}
