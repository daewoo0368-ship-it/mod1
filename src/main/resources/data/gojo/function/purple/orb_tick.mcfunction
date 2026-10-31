execute if entity @s[tag=gojo.orb_b] run particle minecraft:dust{color:[0.1,0.35,1.0],scale:2} ~ ~ ~ 0.15 0.15 0.15 0 6 force
execute if entity @s[tag=gojo.orb_b] run particle minecraft:reverse_portal ~ ~ ~ 0.6 0.6 0.6 0.02 5 force
execute if entity @s[tag=gojo.orb_r] run particle minecraft:dust{color:[1.0,0.08,0.08],scale:2} ~ ~ ~ 0.15 0.15 0.15 0 6 force
execute if entity @s[tag=gojo.orb_r] run particle minecraft:electric_spark ~ ~ ~ 0.3 0.3 0.3 0.05 3 force
execute facing entity @n[type=marker,tag=gojo.center] feet run tp @s ^ ^ ^0.1
