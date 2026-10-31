execute store result entity @s Rotation[0] float 1 run random value 0..359
execute store result entity @s Rotation[1] float 1 run random value -60..60
scoreboard players operation #step gojo.tmp = #blen gojo.tmp
execute at @s run function gojo:flash/bolt_step
kill @s
