scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if score @s gojo.life matches 1 run playsound minecraft:block.beacon.power_select master @a ~ ~ ~ 1.5 0.5
execute if score @s gojo.life matches 12.. run particle minecraft:dust_color_transition{from_color:[0.1,0.35,1.0],to_color:[1.0,0.08,0.08],scale:1.5} ~ ~ ~ 0.4 0.4 0.4 0 8 force
execute if score @s gojo.life matches 16.. run particle minecraft:dust{color:[0.6,0.1,1.0],scale:2} ~ ~ ~ 0.2 0.2 0.2 0 6 force
execute if score @s gojo.life matches 20.. run function gojo:purple/fire
