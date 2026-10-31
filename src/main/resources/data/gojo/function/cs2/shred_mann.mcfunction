particle minecraft:dust{color:[0.7,0.0,0.0],scale:1.2} ~ ~1 ~ 0.4 0.8 0.4 0 12 force
particle minecraft:sweep_attack ~ ~1 ~ 0.3 0.6 0.3 0 2 force
execute if score @e[type=marker,tag=gojo.cs2,limit=1] gojo.life matches 380 run attribute @s minecraft:scale base set 0.9
execute if score @e[type=marker,tag=gojo.cs2,limit=1] gojo.life matches 460 run attribute @s minecraft:scale base set 0.8
execute if score @e[type=marker,tag=gojo.cs2,limit=1] gojo.life matches 540 run attribute @s minecraft:scale base set 0.72
