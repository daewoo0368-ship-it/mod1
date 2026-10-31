execute as @e[type=!#gojo:ignore,distance=..14,sort=nearest] unless score @s gojo.id = #owner gojo.id if data entity @s Health unless entity @s[tag=gojo.defend] run return run function gojo:hg/set_def
tellraw @a[distance=..20] {"text":"[저지맨] 피고인이 없다. 폐정한다.","color":"gray"}
