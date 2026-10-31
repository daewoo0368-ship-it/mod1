scoreboard players add @s gojo.life 1
execute if score @s gojo.life matches 80.. run return run function gojo:purple/end
execute if entity @s[tag=!gojo.strong] if score @s gojo.life matches 50.. run return run function gojo:purple/end
scoreboard players operation #owner gojo.id = @s gojo.id
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..8] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 if entity @s[tag=!gojo.strong] run function gojo:purple/erase
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 if entity @s[tag=gojo.strong] run function gojo:purple/erase_strong
scoreboard players set #s gojo.tmp 0
function gojo:purple/step
