scoreboard players add @s gojo.life 1
execute unless entity @a[tag=gojo.csg] run return run function gojo:cs/end
execute as @a[tag=gojo.csv] run spectate @e[type=item_display,tag=gojo.cscam,limit=1] @s
title @a[tag=gojo.csv] actionbar {"nbt":"line","storage":"gojo:cs","interpret":true}
execute if score @s gojo.life matches ..899 as @e[tag=gojo.cst,limit=1] at @s run rotate @s facing entity @e[type=mannequin,tag=gojo.csga,limit=1] eyes
execute as @e[type=mannequin,tag=gojo.csga,limit=1] at @s run rotate @s facing entity @e[tag=gojo.cst,limit=1] eyes
execute if score @s gojo.life matches 200..279 as @e[type=mannequin,tag=gojo.csga,limit=1] at @s run tp @s ~ ~0.08 ~
execute if score @s gojo.life matches 600..699 as @e[type=mannequin,tag=gojo.csga,limit=1] at @s if block ~ ~-0.1 ~ #gojo:passable run tp @s ~ ~-0.07 ~
execute if score @s gojo.life matches 140..199 at @e[type=mannequin,tag=gojo.csga,limit=1] run particle minecraft:dust{color:[0.7,0.0,0.0],scale:0.8} ~ ~1.8 ~ 0.15 0.15 0.15 0 2
execute if score @s gojo.life matches 200..559 at @e[type=mannequin,tag=gojo.csga,limit=1] run particle minecraft:end_rod ~ ~1 ~ 0.6 0.9 0.6 0.01 2
execute if score @s gojo.life matches 250..559 at @e[type=mannequin,tag=gojo.csga,limit=1] run particle minecraft:dust{color:[1.0,1.0,1.0],scale:0.8} ~ ~1 ~ 1.5 1.5 1.5 0 4
execute if score @s gojo.life matches 380..529 at @e[type=mannequin,tag=gojo.csga,limit=1] rotated as @e[type=mannequin,tag=gojo.csga,limit=1] positioned ^-1.0 ^1.3 ^0.8 run function gojo:cs/orb_blue
execute if score @s gojo.life matches 380..529 at @e[type=mannequin,tag=gojo.csga,limit=1] rotated as @e[type=mannequin,tag=gojo.csga,limit=1] positioned ^1.0 ^1.3 ^0.8 run function gojo:cs/orb_red
execute if score @s gojo.life matches 530..559 at @e[type=mannequin,tag=gojo.csga,limit=1] rotated as @e[type=mannequin,tag=gojo.csga,limit=1] positioned ^ ^1.4 ^1.2 run function gojo:cs/orb_merge
execute if score @s gojo.life matches 600..899 at @e[tag=gojo.cst,limit=1] run particle minecraft:dust{color:[0.6,0.0,0.0],scale:1} ~ ~0.8 ~ 0.3 0.4 0.3 0 3
execute if score @s gojo.life matches 1..59 run function gojo:cs/shot {lx:0,ly:1,lz:5.5,cx:-9,cy:6,cz:3}
execute if score @s gojo.life matches 60..119 run function gojo:cs/shot {lx:0,ly:1.6,lz:11,cx:-0.9,cy:2.3,cz:-3}
execute if score @s gojo.life matches 120..199 run function gojo:cs/shot {lx:0,ly:1.7,lz:0,cx:0.3,cy:1.8,cz:2.6}
execute if score @s gojo.life matches 200..249 run function gojo:cs/shot_up
execute if score @s gojo.life matches 250..319 run function gojo:cs/shot {lx:0,ly:7.4,lz:0,cx:-1.5,cy:3,cz:15}
execute if score @s gojo.life matches 320..379 run function gojo:cs/shot {lx:0,ly:1.6,lz:11,cx:0.4,cy:1.7,cz:8.4}
execute if score @s gojo.life matches 380..529 store result storage gojo:cs a int 2 run scoreboard players get @s gojo.life
execute if score @s gojo.life matches 380..529 run function gojo:cs/orbit with storage gojo:cs
execute if score @s gojo.life matches 530..559 run function gojo:cs/shot_orb
execute if score @s gojo.life matches 560..589 as @e[type=marker,tag=gojo.csb] at @s positioned ^ ^0.9 ^-4.5 facing entity @s feet run tp @e[type=item_display,tag=gojo.cscam,limit=1] ~ ~ ~ ~ ~
execute if score @s gojo.life matches 590..639 run function gojo:cs/shot {lx:0,ly:1,lz:11,cx:-8,cy:3,cz:8}
execute if score @s gojo.life matches 640..699 run function gojo:cs/shot {lx:0,ly:4.5,lz:0,cx:0.9,cy:2.3,cz:13.5}
execute if score @s gojo.life matches 700..899 run function gojo:cs/shot {lx:0,ly:1.4,lz:11,cx:-0.6,cy:1.5,cz:8.6}
execute if score @s gojo.life matches 900..969 run function gojo:cs/shot {lx:0,ly:0.5,lz:11,cx:0,cy:9,cz:5}
execute if score @s gojo.life matches 59 run effect give @a[tag=gojo.csv] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 199 run effect give @a[tag=gojo.csv] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 319 run effect give @a[tag=gojo.csv] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 589 run effect give @a[tag=gojo.csv] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 899 run effect give @a[tag=gojo.csv] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 1 run title @a[tag=gojo.csv] times 5 60 10
execute if score @s gojo.life matches 1 run title @a[tag=gojo.csv] subtitle {"text":"懐玉・玉折","color":"gray","italic":true}
execute if score @s gojo.life matches 1 run title @a[tag=gojo.csv] title {"text":"회옥 · 옥절","color":"white","bold":true}
execute if score @s gojo.life matches 60 run data modify storage gojo:cs line set value [{text:"후시구로 토우지  ",color:"dark_green",bold:1b},{text:"…어이어이, 살아 있었냐.",color:"white"}]
execute if score @s gojo.life matches 60 run tellraw @a[distance=..80] [{"text": "후시구로 토우지  ", "color": "dark_green", "bold": true}, {"text": "…어이어이, 살아 있었냐.", "color": "white"}]
execute if score @s gojo.life matches 115 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 60 run playsound minecraft:entity.player.breath master @a ~ ~ ~ 1 0.6
execute if score @s gojo.life matches 120 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"…하.",color:"white"}]
execute if score @s gojo.life matches 120 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "…하.", "color": "white"}]
execute if score @s gojo.life matches 138 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 140 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:entity.evoker.celebrate master @a ~ ~ ~ 1.6 0.90
execute if score @s gojo.life matches 150 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:entity.evoker.celebrate master @a ~ ~ ~ 1.6 0.94
execute if score @s gojo.life matches 158 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:entity.evoker.celebrate master @a ~ ~ ~ 1.6 0.98
execute if score @s gojo.life matches 165 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:entity.evoker.celebrate master @a ~ ~ ~ 1.6 1.02
execute if score @s gojo.life matches 171 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:entity.evoker.celebrate master @a ~ ~ ~ 1.6 1.06
execute if score @s gojo.life matches 176 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:entity.evoker.celebrate master @a ~ ~ ~ 1.6 1.10
execute if score @s gojo.life matches 140 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"하하… 하하하하하하!",color:"white"}]
execute if score @s gojo.life matches 140 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "하하… 하하하하하하!", "color": "white"}]
execute if score @s gojo.life matches 195 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 205 run title @a[tag=gojo.csv] times 5 40 10
execute if score @s gojo.life matches 205 run title @a[tag=gojo.csv] subtitle {"text":"天上天下","color":"gray","italic":true}
execute if score @s gojo.life matches 205 run title @a[tag=gojo.csv] title {"text":"천상천하","color":"white","bold":true}
execute if score @s gojo.life matches 205 run playsound minecraft:block.beacon.power_select master @a ~ ~ ~ 2 0.7
execute if score @s gojo.life matches 205 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run particle minecraft:flash{color:[1.0,1.0,1.0,1.0]} ~ ~1 ~ 0 0 0 0 1 force
execute if score @s gojo.life matches 205 run tellraw @a[distance=..80] [{"text":"고죠 사토루  ","color":"aqua","bold":true},{"text":"천상천하(天上天下)","color":"white"}]
execute if score @s gojo.life matches 255 run title @a[tag=gojo.csv] times 5 60 10
execute if score @s gojo.life matches 255 run title @a[tag=gojo.csv] subtitle {"text":"唯我独尊","color":"gray","italic":true}
execute if score @s gojo.life matches 255 run title @a[tag=gojo.csv] title {"text":"유아독존","color":"white","bold":true}
execute if score @s gojo.life matches 255 run tellraw @a[distance=..80] [{"text":"고죠 사토루  ","color":"aqua","bold":true},{"text":"유아독존(唯我独尊)","color":"white"}]
execute if score @s gojo.life matches 255 run playsound minecraft:block.beacon.activate master @a ~ ~ ~ 3 0.6
execute if score @s gojo.life matches 255 run playsound minecraft:block.end_portal.spawn master @a ~ ~ ~ 1 1.6
execute if score @s gojo.life matches 255 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run particle minecraft:end_rod ~ ~1 ~ 0 0 0 0.35 120 force
execute if score @s gojo.life matches 325 run data modify storage gojo:cs line set value [{text:"후시구로 토우지  ",color:"dark_green",bold:1b},{text:"…이 녀석, 아까랑은 완전히 딴사람이잖아.",color:"white"}]
execute if score @s gojo.life matches 325 run tellraw @a[distance=..80] [{"text": "후시구로 토우지  ", "color": "dark_green", "bold": true}, {"text": "…이 녀석, 아까랑은 완전히 딴사람이잖아.", "color": "white"}]
execute if score @s gojo.life matches 375 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 385 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"「구강」",color:"white"}]
execute if score @s gojo.life matches 385 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "「구강」", "color": "white"}]
execute if score @s gojo.life matches 411 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 385 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.5
execute if score @s gojo.life matches 415 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"「편광」",color:"white"}]
execute if score @s gojo.life matches 415 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "「편광」", "color": "white"}]
execute if score @s gojo.life matches 441 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 415 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.7
execute if score @s gojo.life matches 445 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"「까마귀와 성명」",color:"white"}]
execute if score @s gojo.life matches 445 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "「까마귀와 성명」", "color": "white"}]
execute if score @s gojo.life matches 471 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 445 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.9
execute if score @s gojo.life matches 475 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"「표리의 틈새」",color:"white"}]
execute if score @s gojo.life matches 475 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "「표리의 틈새」", "color": "white"}]
execute if score @s gojo.life matches 501 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 475 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 1.1
execute if score @s gojo.life matches 505 run data modify storage gojo:cs line set value {text:"순전과 반전, 각각의 무한을 충돌시켜 생성되는 가상의 질량",color:"gray",italic:1b}
execute if score @s gojo.life matches 555 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 505 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] run playsound minecraft:block.conduit.activate master @a ~ ~ ~ 2 1.6
execute if score @s gojo.life matches 560 run title @a[tag=gojo.csv] times 5 35 10
execute if score @s gojo.life matches 560 run title @a[tag=gojo.csv] subtitle {"text":"","color":"gray","italic":true}
execute if score @s gojo.life matches 560 run title @a[tag=gojo.csv] title {"text":"허식 「무라사키」","color":"dark_purple","bold":true}
execute if score @s gojo.life matches 560 run tellraw @a[distance=..80] [{"text":"고죠 사토루  ","color":"aqua","bold":true},{"text":"허식 「무라사키(茈)」","color":"dark_purple","bold":true}]
execute if score @s gojo.life matches 560 run execute at @e[type=mannequin,tag=gojo.csga,limit=1] rotated as @e[type=mannequin,tag=gojo.csga,limit=1] positioned ^ ^1.4 ^1.5 summon marker run function gojo:cs/beam_init
execute if score @s gojo.life matches 560 run playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 3 0.5
execute if score @s gojo.life matches 560 run playsound minecraft:entity.generic.explode master @a ~ ~ ~ 2 0.5
execute if score @s gojo.life matches 589 run execute as @e[type=marker,tag=gojo.csb] run function gojo:cs/impact
execute if score @s gojo.life matches 645 run data modify storage gojo:cs line set value [{text:"고죠 사토루  ",color:"aqua",bold:1b},{text:"남길 말은 있냐?",color:"white"}]
execute if score @s gojo.life matches 645 run tellraw @a[distance=..80] [{"text": "고죠 사토루  ", "color": "aqua", "bold": true}, {"text": "남길 말은 있냐?", "color": "white"}]
execute if score @s gojo.life matches 695 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 705 run data modify storage gojo:cs line set value [{text:"후시구로 토우지  ",color:"dark_green",bold:1b},{text:"…없어.",color:"white"}]
execute if score @s gojo.life matches 705 run tellraw @a[distance=..80] [{"text": "후시구로 토우지  ", "color": "dark_green", "bold": true}, {"text": "…없어.", "color": "white"}]
execute if score @s gojo.life matches 750 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 760 run data modify storage gojo:cs line set value [{text:"후시구로 토우지  ",color:"dark_green",bold:1b},{text:"…아니. 2, 3년 있으면 내 자식이 젠인가에 팔려갈 거다.",color:"white"}]
execute if score @s gojo.life matches 760 run tellraw @a[distance=..80] [{"text": "후시구로 토우지  ", "color": "dark_green", "bold": true}, {"text": "…아니. 2, 3년 있으면 내 자식이 젠인가에 팔려갈 거다.", "color": "white"}]
execute if score @s gojo.life matches 840 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 850 run data modify storage gojo:cs line set value [{text:"후시구로 토우지  ",color:"dark_green",bold:1b},{text:"멋대로 해라.",color:"white"}]
execute if score @s gojo.life matches 850 run tellraw @a[distance=..80] [{"text": "후시구로 토우지  ", "color": "dark_green", "bold": true}, {"text": "멋대로 해라.", "color": "white"}]
execute if score @s gojo.life matches 895 run data modify storage gojo:cs line set value {text:""}
execute if score @s gojo.life matches 900 run function gojo:cs/toji_falls
execute if score @s gojo.life matches 920 run title @a[tag=gojo.csv] times 5 40 10
execute if score @s gojo.life matches 920 run title @a[tag=gojo.csv] subtitle {"text":"懐玉・玉折","color":"gray","italic":true}
execute if score @s gojo.life matches 920 run title @a[tag=gojo.csv] title {"text":"— 끝 —","color":"white","bold":true}
execute if score @s gojo.life matches 970 run function gojo:cs/end
