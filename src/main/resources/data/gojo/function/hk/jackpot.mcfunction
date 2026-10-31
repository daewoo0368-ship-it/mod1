tag @a[distance=..32] add gojo.fxflash_w
title @a[distance=..48] times 0 60 20
title @a[distance=..48] subtitle {"text":"무한의 주력 — 4분 11초간 불사신","color":"gold"}
execute if score @s gojo.r1 matches 7 run title @a[distance=..48] subtitle {"text":"확변 777 — 무한의 주력 — 4분 11초간 불사신","color":"gold","bold":true}
playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 2 1
playsound minecraft:entity.player.levelup master @a ~ ~ ~ 2 0.8
playsound minecraft:block.note_block.chime master @a ~ ~ ~ 2 1.5
particle minecraft:firework ~ ~4 ~ 8 4 8 0.2 300 force
particle minecraft:totem_of_undying ~ ~3 ~ 6 3 6 0.5 300 force
particle minecraft:flash{color:[1.0,1.0,0.6,1.0]} ~ ~3 ~ 0 0 0 0 1 force
execute as @a if score @s gojo.id = #owner gojo.id run function gojo:hk/give_jackpot
function gojo:hk/close
