scoreboard players operation #x gojo.tmp = @s gojo.pos
scoreboard players operation #y gojo.tmp = @s gojo.chant
scoreboard players operation #z gojo.tmp = @s gojo.ct
execute store result score #r gojo.tmp run random value -22..22
scoreboard players operation #x gojo.tmp += #r gojo.tmp
execute store result score #r gojo.tmp run random value -3..8
scoreboard players operation #y gojo.tmp += #r gojo.tmp
execute store result score #r gojo.tmp run random value -22..22
scoreboard players operation #z gojo.tmp += #r gojo.tmp
summon marker ~ ~ ~ {Tags:["gojo.mcut"]}
execute as @n[type=marker,tag=gojo.mcut] run function gojo:ms/cut_at
