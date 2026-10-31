execute as @e[tag=gojo.csm,limit=1] on passengers if entity @s[tag=gojo.mbody] run tag @s add gojo.mpick
execute as @e[tag=gojo.mpick,sort=random,limit=1] run tag @s add gojo.mshred
execute at @e[tag=gojo.csm,limit=1] if entity @e[tag=gojo.mshred] run particle minecraft:dust{color:[0.85,0.85,0.85],scale:1.5} ~ ~2.5 ~ 1 1.5 1 0 15 force
execute at @e[tag=gojo.csm,limit=1] if entity @e[tag=gojo.mshred] run particle minecraft:block{block_state:"minecraft:white_concrete"} ~ ~2.5 ~ 0.8 1.4 0.8 0 12 force
kill @e[tag=gojo.mshred]
tag @e[tag=gojo.mpick] remove gojo.mpick
execute as @e[type=mannequin,tag=gojo.cs2mm] at @s run function gojo:cs2/shred_mann
