tag @a[distance=..48] add gojo.fxshake
tag @a[distance=..48] add gojo.fxflash_w
scoreboard players operation #cast gojo.id = @s gojo.id
scoreboard players operation #owner gojo.id = @s gojo.id
title @a[distance=..64] times 0 40 15
title @a[distance=..64] subtitle {"text":"— 세계를 가르는 참격 —","color":"dark_red","italic":true}
title @a[distance=..64] title {"text":"「해(解)」","color":"white","bold":true}
playsound minecraft:entity.player.attack.sweep master @a ~ ~ ~ 3 0.5
playsound minecraft:item.trident.thunder master @a ~ ~ ~ 3 0.6
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 2 1.4
playsound minecraft:block.glass.break master @a ~ ~ ~ 3 0.5
execute anchored eyes positioned ^ ^ ^1.5 summon marker run function gojo:ws/run
