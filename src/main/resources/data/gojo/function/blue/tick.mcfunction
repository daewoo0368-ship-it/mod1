scoreboard players add @s gojo.life 1
execute if score @s gojo.life matches 120.. run return run function gojo:blue/end
execute if entity @s[tag=!gojo.strong] if score @s gojo.life matches 50.. run return run function gojo:blue/end
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=!gojo.strong] run function gojo:blue/normal
execute if entity @s[tag=gojo.strong] run function gojo:blue/strong
execute if entity @s[tag=!gojo.strong] run tp @s ^ ^ ^0.6
execute if entity @s[tag=gojo.strong] run function gojo:blue/follow
