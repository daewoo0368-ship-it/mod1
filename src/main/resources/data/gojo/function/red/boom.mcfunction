tag @e[tag=gojo.hit] remove gojo.hit
execute if entity @s[tag=!gojo.strong] run function gojo:red/boom_normal
execute if entity @s[tag=gojo.strong] run function gojo:red/boom_strong
kill @s
