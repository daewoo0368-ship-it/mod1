scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
scoreboard players set #found gojo.tmp 0
execute as @a[distance=..64,tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run scoreboard players set #found gojo.tmp 1
execute if score #found gojo.tmp matches 0 run return run function gojo:hg/close
particle minecraft:dust{color:[0.6,0.5,0.2],scale:1} ~ ~1 ~ 11 3 11 0 6 force
execute if entity @s[tag=!gojo.clashing] if entity @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40] run function gojo:clash/try
execute if entity @s[tag=gojo.clashing] run scoreboard players remove @s gojo.life 1
execute if entity @s[tag=gojo.clashing] run return 0
execute as @e[tag=gojo.defend,distance=12.5..80] if score @s gojo.dfn = #owner gojo.id facing entity @s feet positioned ^ ^ ^11.5 run function gojo:domain/pullback
execute as @e[type=!#gojo:ignore,distance=..14] if data entity @s Health run function gojo:hg/nonviolence
execute if score @s gojo.adapt matches 0.. run scoreboard players remove @s gojo.adapt 1
execute if score @s gojo.adapt matches 0 run return run function gojo:hg/close
execute if entity @s[tag=gojo.hgdone] run return 0
execute if score @s gojo.life matches 30 run function gojo:hg/pick
execute if score @s gojo.life matches 31.. unless function gojo:hg/has_def run return run function gojo:hg/close
execute if score @s gojo.life matches 50 run function gojo:hg/accuse
scoreboard players set #pd gojo.tmp 0
execute as @e[tag=gojo.defend,type=player,scores={gojo.plead=1..}] if score @s gojo.dfn = #owner gojo.id run scoreboard players set #pd gojo.tmp 1
execute if score @s gojo.life matches 60.. if score #pd gojo.tmp matches 1 run return run function gojo:hg/verdict
execute if score @s gojo.life matches 110 as @e[tag=gojo.defend,type=!player] if score @s gojo.dfn = #owner gojo.id run scoreboard players set #pd gojo.tmp 1
execute if score @s gojo.life matches 110 if score #pd gojo.tmp matches 1 run return run function gojo:hg/verdict
execute if score @s gojo.life matches 250 run function gojo:hg/verdict
