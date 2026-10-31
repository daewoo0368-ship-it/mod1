title @a[distance=..48] times 5 50 15
title @a[distance=..48] subtitle {"text":"후루베 유라유라","color":"gray","italic":true}
title @a[distance=..48] title {"text":"팔악검 이계신장 마허라","color":"white","bold":true}
playsound minecraft:entity.warden.emerge master @a ~ ~ ~ 2 0.7
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 0.6
playsound minecraft:entity.wither.ambient master @a ~ ~ ~ 1.5 0.5
execute rotated ~ 0 positioned ^ ^ ^3 run particle minecraft:squid_ink ~ ~0.2 ~ 1.5 0.1 1.5 0.05 120 force
execute rotated ~ 0 positioned ^ ^ ^3 run particle minecraft:large_smoke ~ ~0.5 ~ 1.5 1 1.5 0.02 60 force
execute rotated ~ 0 positioned ^ ^-4.2 ^3 summon iron_golem run function gojo:maho/init
