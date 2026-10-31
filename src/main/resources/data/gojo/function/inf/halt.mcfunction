tag @s add gojo.halted
data merge entity @s {Motion:[0.0,0.0,0.0],NoGravity:1b}
execute if entity @s[type=#minecraft:impact_projectiles] run data merge entity @s {acceleration_power:0.0d}
particle minecraft:reverse_portal ~ ~ ~ 0.2 0.2 0.2 0.02 15 force
particle minecraft:dust{color:[0.75,0.9,1.0],scale:1} ~ ~ ~ 0.1 0.1 0.1 0 6 force
playsound minecraft:block.amethyst_block.hit master @a ~ ~ ~ 1 1.5
