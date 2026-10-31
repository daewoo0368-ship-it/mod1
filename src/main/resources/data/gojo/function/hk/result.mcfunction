title @a[distance=..16] times 0 40 10
execute if score @s gojo.r1 = @s gojo.r2 if score @s gojo.r2 = @s gojo.r3 run return run function gojo:hk/jackpot
title @a[distance=..16] subtitle {"text":"꽝","color":"gray"}
playsound minecraft:block.note_block.bass master @a ~ ~ ~ 2 0.5
execute if score @s gojo.adapt matches 5.. run function gojo:hk/close
