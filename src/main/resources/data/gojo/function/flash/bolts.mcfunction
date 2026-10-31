summon marker ~ ~ ~ {Tags:["gojo.bolt"]}
execute as @n[type=marker,tag=gojo.bolt,distance=..0.1] run function gojo:flash/bolt_init
scoreboard players remove #bolts gojo.tmp 1
execute if score #bolts gojo.tmp matches 1.. run function gojo:flash/bolts
