scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if score @s gojo.life matches 40.. run return run function gojo:fuga/boom
scoreboard players set #s gojo.tmp 0
function gojo:fuga/step
