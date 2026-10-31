scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #20 gojo.cfg
execute if score #m gojo.tmp matches 0 as @e[tag=gojo.csm,limit=1] run function gojo:maho/anim/windup
execute if score #m gojo.tmp matches 6 as @e[tag=gojo.csm,limit=1] run function gojo:maho/anim/strike
execute if score #m gojo.tmp matches 6 at @e[type=mannequin,tag=gojo.cs2s,limit=1] run playsound minecraft:entity.player.attack.sweep master @a ~ ~ ~ 2 0.5
execute if score #m gojo.tmp matches 14 as @e[tag=gojo.csm,limit=1] run function gojo:maho/anim/idle
scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #10 gojo.cfg
scoreboard players set #near gojo.tmp 1
execute if score #m gojo.tmp matches 3 at @e[tag=gojo.csm,limit=1] positioned ~ ~2.5 ~ facing entity @e[type=mannequin,tag=gojo.cs2s,limit=1] eyes run function gojo:kai/draw2
execute if score #m gojo.tmp matches 8 at @e[tag=gojo.csm,limit=1] positioned ~ ~3 ~ facing entity @e[type=mannequin,tag=gojo.cs2s,limit=1] eyes run function gojo:kai/draw6
execute if score #m gojo.tmp matches 3 at @e[tag=gojo.csm,limit=1] run playsound minecraft:block.anvil.land master @a ~ ~ ~ 1.5 1.6
execute if score #m gojo.tmp matches 8 at @e[tag=gojo.csm,limit=1] run playsound minecraft:block.anvil.land master @a ~ ~ ~ 1.5 1.4
execute if score #m gojo.tmp matches 3 at @e[tag=gojo.csm,limit=1] run particle minecraft:crit ~ ~2.5 ~ 0.6 0.8 0.6 0.5 15 force
execute if score @s gojo.life matches 100 run function gojo:cs2/wheel
