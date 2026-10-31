tag @s add gojo.hgdone
scoreboard players set @s gojo.adapt 70
playsound minecraft:block.anvil.land master @a ~ ~ ~ 2 0.6
execute as @e[tag=gojo.defend] if score @s gojo.dfn = #owner gojo.id at @s run function gojo:hg/judge
