tag @a[distance=..40] add gojo.fxshake_s
title @a[distance=..48] times 5 50 15
title @a[distance=..48] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..48] title {"text":"좌살박도(坐殺博徒)","color":"green","bold":true}
playsound minecraft:block.note_block.bell master @a ~ ~ ~ 2 1.0
playsound minecraft:block.note_block.bit master @a ~ ~ ~ 2 1.5
playsound minecraft:block.chain.place master @a ~ ~ ~ 2 0.6
tellraw @a[distance=..20] ["",{"text":"[좌살박도] ","color":"green","bold":true},{"text":"규칙이 머릿속에 새겨진다 — 세 숫자가 모두 맞으면 대박. 대박이 나면 4분 11초간 주력이 무한해진다.","color":"gray"}]
summon marker ~ ~ ~ {Tags:["gojo.hk","gojo.hknew"]}
execute as @n[type=marker,tag=gojo.hknew] run function gojo:hk/init
