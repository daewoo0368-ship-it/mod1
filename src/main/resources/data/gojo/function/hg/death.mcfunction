title @a[distance=..32] subtitle {"text":"형벌: 사형 — 사형 집행인의 검이 주어진다","color":"gold"}
effect give @s minecraft:glowing 60 0 true
execute as @a if score @s gojo.id = #owner gojo.id run function gojo:hg/give_sword
