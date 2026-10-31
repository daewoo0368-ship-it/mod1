execute at @e[tag=gojo.cst,limit=1] run particle minecraft:dust{color:[0.6,0.0,0.0],scale:1.5} ~ ~1 ~ 0.4 0.8 0.4 0 40 force
execute at @e[tag=gojo.cst,limit=1] run playsound minecraft:entity.player.death master @a ~ ~ ~ 1 0.7
data merge entity @e[type=mannequin,tag=gojo.cst,limit=1] {pose:"sleeping"}
