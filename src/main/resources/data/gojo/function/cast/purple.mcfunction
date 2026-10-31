scoreboard players operation #cast gojo.id = @s gojo.id
title @s actionbar {"text":"허식 「무라사키(茈)」","color":"dark_purple","bold":true}
effect give @s minecraft:slowness 2 4 true
playsound minecraft:block.conduit.activate master @a ~ ~ ~ 1 1.6
playsound minecraft:entity.blaze.shoot master @a ~ ~ ~ 1 0.6
execute anchored eyes positioned ^ ^ ^2.5 summon marker run function gojo:purple/init_center
execute anchored eyes positioned ^-2 ^ ^2.5 summon marker run function gojo:purple/init_orb_b
execute anchored eyes positioned ^2 ^ ^2.5 summon marker run function gojo:purple/init_orb_r
