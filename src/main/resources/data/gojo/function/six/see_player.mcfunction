particle minecraft:dust{color:[0.3,0.75,1.0],scale:1.2} ~ ~1 ~ 0.35 0.7 0.35 0 4 force @a[tag=gojo.sixv]
execute if score @s gojo.ce matches 600.. run particle minecraft:dust{color:[0.6,0.95,1.0],scale:1.6} ~ ~1 ~ 0.5 0.9 0.5 0 6 force @a[tag=gojo.sixv]
execute if score @s gojo.ce matches ..199 run particle minecraft:dust{color:[0.2,0.3,0.5],scale:0.6} ~ ~1 ~ 0.3 0.6 0.3 0 2 force @a[tag=gojo.sixv]
execute if entity @s[tag=gojo.inf] run particle minecraft:end_rod ~ ~1 ~ 0.6 0.9 0.6 0 1 force @a[tag=gojo.sixv]
