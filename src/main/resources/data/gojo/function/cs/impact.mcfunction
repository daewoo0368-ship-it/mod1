tag @a[tag=gojo.csv] add gojo.fxshake
tag @a[tag=gojo.csv] add gojo.fxflash_p
execute at @e[tag=gojo.cst,limit=1] run particle minecraft:flash{color:[0.8,0.4,1.0,1.0]} ~ ~1 ~ 0 0 0 0 1 force
execute at @e[tag=gojo.cst,limit=1] run particle minecraft:dust{color:[0.6,0.1,1.0],scale:4} ~ ~1 ~ 2 2 2 0 200 force
execute at @e[tag=gojo.cst,limit=1] run particle minecraft:explosion_emitter ~ ~1 ~ 1 1 1 0 3 force
execute at @e[tag=gojo.cst,limit=1] run particle minecraft:dust{color:[0.6,0.0,0.0],scale:2} ~ ~1 ~ 0.6 0.8 0.6 0 60 force
execute at @e[tag=gojo.cst,limit=1] run playsound minecraft:entity.generic.explode master @a ~ ~ ~ 4 0.4
execute at @e[tag=gojo.cst,limit=1] run playsound minecraft:entity.warden.sonic_charge master @a ~ ~ ~ 3 0.5
execute as @e[tag=gojo.cst,limit=1] run attribute @s minecraft:scale base set 0.85
kill @s
