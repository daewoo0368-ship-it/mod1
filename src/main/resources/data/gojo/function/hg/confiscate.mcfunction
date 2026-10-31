title @a[distance=..32] subtitle {"text":"형벌: 몰수 — 60초간 술식을 쓸 수 없다","color":"gold"}
tag @s add gojo.confisc
scoreboard players set @s gojo.cft 1200
tag @s remove gojo.inf
scoreboard players set @s gojo.chant 0
scoreboard players set @s gojo.fuga 0
particle minecraft:dust{color:[1.0,0.85,0.2],scale:1.5} ~ ~1 ~ 0.5 1 0.5 0 40 force
