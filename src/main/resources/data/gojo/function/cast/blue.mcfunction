scoreboard players operation #cast gojo.id = @s gojo.id
title @s actionbar {"text":"술식순전 「창(蒼)」","color":"aqua","bold":true}
execute if score #strong gojo.tmp matches 1 run title @a[distance=..48] title {"text":"술식순전 「창」","color":"aqua","bold":true}
execute if score #strong gojo.tmp matches 1 run title @a[distance=..48] subtitle ""
playsound minecraft:block.conduit.activate master @a ~ ~ ~ 1 1.6
playsound minecraft:entity.illusioner.cast_spell master @a ~ ~ ~ 1 0.6
execute anchored eyes positioned ^ ^ ^2 summon marker run function gojo:blue/init
