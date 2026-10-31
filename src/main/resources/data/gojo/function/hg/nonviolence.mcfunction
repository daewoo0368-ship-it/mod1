effect give @s minecraft:weakness 2 255 true
effect give @s minecraft:mining_fatigue 2 4 true
execute if entity @s[tag=gojo.defend] if score @s gojo.dfn = #owner gojo.id run effect give @s minecraft:slowness 2 255 true
