scoreboard players add @s gojo.life 1
execute if score @s gojo.life matches 13.. run return run kill @s
scoreboard players operation #owner gojo.id = @s gojo.id
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..3.5] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
scoreboard players set #s gojo.tmp 0
function gojo:kai/step
