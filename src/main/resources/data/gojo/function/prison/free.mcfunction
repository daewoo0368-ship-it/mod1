tag @s remove gojo.sealed
scoreboard players reset @s gojo.prown
attribute @s minecraft:jump_strength modifier remove gojo:pr
attribute @s minecraft:gravity modifier remove gojo:pr
effect clear @s minecraft:slowness
effect clear @s minecraft:weakness
effect clear @s minecraft:mining_fatigue
effect clear @s minecraft:invisibility
effect clear @s minecraft:resistance
effect clear @s minecraft:blindness
execute if entity @s[tag=gojo.prfroze] run data merge entity @s {NoAI:0b,Invulnerable:0b}
tag @s remove gojo.prfroze
