tag @s add gojo.sukuna
title @s times 10 70 20
title @s title {"text":"료멘 스쿠나","color":"dark_red","bold":true}
title @s subtitle {"text":"저주의 왕, 완전 부활","color":"red"}
tellraw @a [{"selector":"@s","color":"dark_red"},{"text":" 이(가) 스쿠나의 손가락 20개를 모두 삼켰다. 저주의 왕이 깨어난다.","color":"red"}]
summon lightning_bolt ~ ~ ~
playsound minecraft:entity.wither.spawn master @a[distance=..64] ~ ~ ~ 2 0.6
playsound minecraft:entity.ender_dragon.growl master @a[distance=..64] ~ ~ ~ 2 0.5
particle minecraft:dust{color:[0.6,0.0,0.05],scale:2.5} ~ ~1 ~ 2 2 2 0 400 force
particle minecraft:explosion_emitter ~ ~1 ~ 0 0 0 0 1 force
effect give @s minecraft:instant_health 1 4 true
# 스쿠나 술식 지급
give @s jjk:dismantle[minecraft:item_name={"text": "「해」 / 세계참", "color": "white", "italic": false}] 1
give @s jjk:fuga[minecraft:item_name={"text": "「푸가」", "color": "gold", "italic": false}] 1
give @s jjk:malevolent_shrine[minecraft:item_name={"text": "영역전개 「복마어주자」", "color": "dark_red", "italic": false}] 1
give @s jjk:mahoraga[minecraft:item_name={"text": "마허라 소환", "color": "white", "italic": false}] 1
