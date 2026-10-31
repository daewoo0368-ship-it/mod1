scoreboard players remove @s gojo.life 1
execute if score @s gojo.life matches ..0 run particle minecraft:soul ~ ~1 ~ 0.3 0.6 0.3 0.03 20 force
execute if score @s gojo.life matches ..0 run tp @s ~ -500 ~
execute if score @s gojo.life matches ..0 run return run kill @s
execute if predicate gojo:chance10 run particle minecraft:dust{color:[0.25,0.55,0.6],scale:0.8} ~ ~1 ~ 0.3 0.5 0.3 0 2
execute if score @s gojo.life matches 1.. store result score #m gojo.tmp run scoreboard players get @s gojo.life
scoreboard players operation #m gojo.tmp %= #40 gojo.cfg
execute if score #m gojo.tmp matches 0 run damage @n[type=#gojo:hostile,type=!#gojo:transfig,distance=..12] 0.5 minecraft:mob_attack by @s
