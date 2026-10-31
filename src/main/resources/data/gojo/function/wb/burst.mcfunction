execute as @e[tag=gojo.hit] run damage @s 7 minecraft:mob_attack
tag @e[tag=gojo.hit] remove gojo.hit
particle minecraft:block{block_state:"minecraft:oak_log"} ~ ~ ~ 0.3 0.3 0.3 0 25 force
particle minecraft:cherry_leaves ~ ~ ~ 0.4 0.4 0.4 0 6 force
playsound minecraft:block.wood.break master @a ~ ~ ~ 1.2 0.8
kill @s
