scoreboard players set #found gojo.tmp 1
tp @s ~ ~ ~
effect give @s minecraft:slowness 2 255 true
effect give @s minecraft:weakness 2 255 true
effect give @s minecraft:mining_fatigue 2 255 true
effect give @s minecraft:invisibility 2 0 true
effect give @s minecraft:resistance 2 4 true
effect give @s minecraft:blindness 2 0 true
tag @s remove gojo.inf
execute if entity @s[type=player] run title @s actionbar {"text":"옥문강에 봉인되어 있다 — 아무것도 할 수 없다","color":"dark_red"}
