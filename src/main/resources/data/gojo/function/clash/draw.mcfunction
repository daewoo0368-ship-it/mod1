scoreboard players add #i gojo.tmp 1
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 1 run particle minecraft:dust{color:[0.8,0.92,1.0],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 2 run particle minecraft:dust{color:[0.75,0.0,0.0],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 1 run particle minecraft:dust{color:[0.8,0.92,1.0],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 2 run particle minecraft:dust{color:[0.75,0.0,0.0],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 3 run particle minecraft:dust{color:[0.25,0.6,0.65],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 3 run particle minecraft:dust{color:[0.25,0.6,0.65],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 4 run particle minecraft:dust{color:[1.0,0.45,0.0],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 4 run particle minecraft:dust{color:[1.0,0.45,0.0],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 5 run particle minecraft:dust{color:[1.0,0.8,0.2],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 5 run particle minecraft:dust{color:[1.0,0.8,0.2],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 6 run particle minecraft:dust{color:[0.95,0.55,0.75],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 6 run particle minecraft:dust{color:[0.95,0.55,0.75],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp < #k gojo.tmp if score #ta gojo.tmp matches 7 run particle minecraft:dust{color:[0.3,1.0,0.4],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp > #k gojo.tmp if score #tb gojo.tmp matches 7 run particle minecraft:dust{color:[0.3,1.0,0.4],scale:1.4} ~ ~ ~ 0.1 0.1 0.1 0 2 force
execute if score #i gojo.tmp = #k gojo.tmp run function gojo:clash/front
tp @s ^ ^ ^0.5
execute if score #i gojo.tmp < #n gojo.tmp at @s run function gojo:clash/draw
