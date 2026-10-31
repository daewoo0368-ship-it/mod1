effect give @s minecraft:weakness 3 2 true
effect give @s minecraft:slowness 3 3 true
damage @s 3 minecraft:magic
particle minecraft:dust{color:[0.95,0.55,0.75],scale:1} ~ ~1 ~ 0.3 0.6 0.3 0 8
execute if entity @s[type=player] run title @s actionbar {"text":"꽃향기에 싸울 의지가 꺾인다...","color":"light_purple"}
execute as @a if score @s gojo.id = #owner gojo.id run effect give @s minecraft:regeneration 2 1 true
