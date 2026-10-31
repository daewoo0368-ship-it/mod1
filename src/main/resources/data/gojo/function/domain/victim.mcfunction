tag @s add gojo.trapped
execute if entity @s[type=!player,tag=!gojo.frozen] unless data entity @s {NoAI:1b} run function gojo:domain/freeze
effect give @s minecraft:slowness 2 255 true
effect give @s minecraft:jump_boost 2 250 true
effect give @s minecraft:weakness 2 255 true
effect give @s minecraft:mining_fatigue 2 4 true
effect give @s minecraft:nausea 4 0 true
effect give @s minecraft:glowing 2 0 true
execute if entity @s[type=player] run title @s actionbar {"text":"아무것도 할 수 없다... 모든 것이 보이고, 모든 것이 느껴진다","color":"white"}
particle minecraft:enchant ~ ~2.5 ~ 0.5 0.5 0.5 1.5 6 force
