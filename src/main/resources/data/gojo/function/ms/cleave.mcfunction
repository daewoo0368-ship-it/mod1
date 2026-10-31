damage @s 5 minecraft:magic
particle minecraft:sweep_attack ~ ~1 ~ 0.4 0.6 0.4 0 3 force
particle minecraft:dust{color:[0.7,0.0,0.0],scale:1.2} ~ ~1 ~ 0.3 0.6 0.3 0 10 force
execute if entity @s[type=player] run title @s actionbar {"text":"베이고, 또 베인다","color":"dark_red"}
