tag @s add gojo.gtrap
damage @s 6 minecraft:in_fire
execute if entity @s[type=!player] run data merge entity @s {Fire:100s}
particle minecraft:flame ~ ~1 ~ 0.3 0.6 0.3 0.05 15 force
execute if entity @s[type=player] run title @s actionbar {"text":"몸이 타들어간다 — 개관철위산","color":"gold"}
