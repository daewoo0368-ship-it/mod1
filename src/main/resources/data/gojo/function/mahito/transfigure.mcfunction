particle minecraft:explosion ~ ~1 ~ 0.3 0.3 0.3 0 2 force
particle minecraft:dust{color:[0.6,0.1,0.1],scale:1.5} ~ ~1 ~ 0.5 0.6 0.5 0 30 force
execute store result score #r gojo.tmp run random value 0..2
execute if score #r gojo.tmp matches 0 run summon zombie ~ ~ ~ {Tags:["gojo.tf","gojo.tfnew"]}
execute if score #r gojo.tmp matches 1 run summon husk ~ ~ ~ {Tags:["gojo.tf","gojo.tfnew"]}
execute if score #r gojo.tmp matches 2 run summon zombie_villager ~ ~ ~ {Tags:["gojo.tf","gojo.tfnew"]}
execute as @n[tag=gojo.tfnew] run function gojo:mahito/tf_init
tp @s ~ -500 ~
kill @s
