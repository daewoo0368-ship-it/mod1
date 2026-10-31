damage @s 15 minecraft:magic by @p[tag=gojo.atk]
particle minecraft:dust{color:[0.0,0.0,0.0],scale:2.5} ~ ~1 ~ 0.4 0.6 0.4 0 40 force
particle minecraft:dust{color:[0.9,0.0,0.0],scale:1.8} ~ ~1 ~ 0.6 0.8 0.6 0 30 force
particle minecraft:crit ~ ~1 ~ 0.3 0.5 0.3 0.6 20 force
scoreboard players set #bolts gojo.tmp 6
scoreboard players set #blen gojo.tmp 8
execute positioned ~ ~1 ~ run function gojo:flash/bolts
playsound minecraft:entity.lightning_bolt.impact master @a ~ ~ ~ 1.5 1.6
playsound minecraft:block.anvil.land master @a ~ ~ ~ 1 0.6
playsound minecraft:entity.player.attack.crit master @a ~ ~ ~ 1.5 0.5
scoreboard players set #kb gojo.tmp 6
execute rotated as @p[tag=gojo.atk] rotated ~ 0 run function gojo:flash/kb
