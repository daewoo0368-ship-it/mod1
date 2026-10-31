scoreboard players add @s gojo.life 1
execute unless entity @a[tag=gojo.cs2p] run return run function gojo:cs2/end
execute as @a[tag=gojo.cs2v] run spectate @e[type=item_display,tag=gojo.cs2cam,limit=1] @s
title @a[tag=gojo.cs2v] actionbar {"nbt":"line","storage":"gojo:cs2","interpret":true}
execute as @e[type=mannequin,tag=gojo.cs2s,limit=1] at @s run rotate @s facing entity @e[tag=gojo.csm,limit=1] eyes
execute if score @s gojo.life matches ..649 as @e[tag=gojo.csm,limit=1] at @s run rotate @s facing entity @e[type=mannequin,tag=gojo.cs2s,limit=1] feet
execute if score @s gojo.life matches ..649 as @e[tag=gojo.csm,limit=1] on passengers if entity @s[tag=gojo.mbody] rotated as @e[tag=gojo.csm,limit=1] run rotate @s ~ 0
execute if score @s gojo.life matches 1..79 run function gojo:cs2/shot {lx:0,ly:2,lz:6,cx:-10,cy:5,cz:2}
execute if score @s gojo.life matches 80..129 run function gojo:cs2/shot {lx:0,ly:3,lz:12,cx:-1,cy:2.3,cz:-3}
execute if score @s gojo.life matches 130..229 run function gojo:cs2/shot {lx:0,ly:1.7,lz:0,cx:0.3,cy:1.8,cz:2.6}
execute if score @s gojo.life matches 230..269 run function gojo:cs2/shot {lx:0,ly:1.2,lz:0,cx:0,cy:1.3,cz:1.6}
execute if score @s gojo.life matches 270..349 run function gojo:cs2/shot {lx:0,ly:0,lz:2,cx:0,cy:24,cz:-22}
execute if score @s gojo.life matches 350..449 store result storage gojo:cs2 a int 1 run scoreboard players get @s gojo.life
execute if score @s gojo.life matches 350..449 run function gojo:cs2/orbit with storage gojo:cs2
execute if score @s gojo.life matches 450..519 run function gojo:cs2/shot {lx:0,ly:2.5,lz:12,cx:-3,cy:3,cz:8.5}
execute if score @s gojo.life matches 520..559 run function gojo:cs2/shot {lx:0,ly:3,lz:-5.5,cx:1.5,cy:3.2,cz:-1}
execute if score @s gojo.life matches 560..619 run function gojo:cs2/shot {lx:0,ly:2,lz:12,cx:-1.2,cy:1.9,cz:-2.5}
execute if score @s gojo.life matches 620..649 as @e[type=marker,tag=gojo.cs2a] at @s positioned ^ ^0.8 ^-4 facing entity @s feet run tp @e[type=item_display,tag=gojo.cs2cam,limit=1] ~ ~ ~ ~ ~
execute if score @s gojo.life matches 650..719 run function gojo:cs2/shot {lx:0,ly:2,lz:12,cx:-18,cy:8,cz:4}
execute if score @s gojo.life matches 720..799 run function gojo:cs2/shot {lx:0,ly:1.7,lz:0,cx:0.3,cy:1.8,cz:2.6}
execute if score @s gojo.life matches 800..899 run function gojo:cs2/shot {lx:0,ly:0,lz:6,cx:0,cy:30,cz:-10}
execute if score @s gojo.life matches 79 run effect give @a[tag=gojo.cs2v] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 269 run effect give @a[tag=gojo.cs2v] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 349 run effect give @a[tag=gojo.cs2v] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 519 run effect give @a[tag=gojo.cs2v] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 619 run effect give @a[tag=gojo.cs2v] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 799 run effect give @a[tag=gojo.cs2v] minecraft:blindness 1 0 true
execute if score @s gojo.life matches 30..129 run function gojo:cs2/duel
execute if score @s gojo.life matches 230..269 run execute at @e[type=mannequin,tag=gojo.cs2s,limit=1] rotated as @e[type=mannequin,tag=gojo.cs2s,limit=1] positioned ^ ^1.3 ^0.5 run particle minecraft:dust{color:[0.7,0.0,0.0],scale:0.8} ~ ~ ~ 0.15 0.15 0.15 0 4
execute if score @s gojo.life matches 270 run execute rotated as @e[type=marker,tag=gojo.cs2,limit=1] positioned as @e[type=mannequin,tag=gojo.cs2s,limit=1] positioned ^ ^ ^-8 align xyz positioned ~0.5 ~ ~0.5 run function gojo:ms/shrine
execute if score @s gojo.life matches 270 run execute as @e[tag=gojo.snew] run function gojo:cs2/shrine_tag
execute if score @s gojo.life matches 270 run playsound minecraft:entity.wither.spawn master @a ~ ~ ~ 4 0.5
execute if score @s gojo.life matches 270 run playsound minecraft:block.bell.resonate master @a ~ ~ ~ 4 0.5
execute if score @s gojo.life matches 270 run playsound minecraft:entity.warden.emerge master @a ~ ~ ~ 3 0.6
execute if score @s gojo.life matches 280..640 run function gojo:cs2/domain
execute if score @s gojo.life matches 560..619 run execute as @e[type=mannequin,tag=gojo.cs2s,limit=1] at @s anchored eyes rotated as @s run function gojo:fuga/bow
execute if score @s gojo.life matches 560..619 run execute at @e[type=mannequin,tag=gojo.cs2s,limit=1] run particle minecraft:flame ~ ~1 ~ 0.5 0.8 0.5 0.02 4 force
execute if score @s gojo.life matches 575 run playsound minecraft:block.fire.ambient master @a ~ ~ ~ 3 0.5
execute if score @s gojo.life matches 590 run playsound minecraft:entity.blaze.ambient master @a ~ ~ ~ 3 0.5
execute if score @s gojo.life matches 620 run execute as @e[type=mannequin,tag=gojo.cs2s,limit=1] at @s anchored eyes positioned ^ ^ ^1.5 summon marker run function gojo:cs2/arrow_init
execute if score @s gojo.life matches 620 run playsound minecraft:entity.arrow.shoot master @a ~ ~ ~ 3 0.5
execute if score @s gojo.life matches 620 run playsound minecraft:entity.blaze.shoot master @a ~ ~ ~ 3 0.5
execute if score @s gojo.life matches 620 run playsound minecraft:item.trident.thunder master @a ~ ~ ~ 2 2
execute if score @s gojo.life matches 648 run execute as @e[type=marker,tag=gojo.cs2a] at @s run function gojo:cs2/impact
execute if score @s gojo.life matches 650..800 run execute positioned as @e[type=marker,tag=gojo.cs2,limit=1] rotated as @e[type=marker,tag=gojo.cs2,limit=1] positioned ^ ^ ^12 run particle minecraft:flame ~ ~1 ~ 5 1.5 5 0.03 30 force
execute if score @s gojo.life matches 650..800 run execute positioned as @e[type=marker,tag=gojo.cs2,limit=1] rotated as @e[type=marker,tag=gojo.cs2,limit=1] positioned ^ ^ ^12 run particle minecraft:large_smoke ~ ~4 ~ 5 3 5 0.03 12 force
execute if score @s gojo.life matches 970 run function gojo:cs2/end
execute if score @s gojo.life matches 1 run title @a[tag=gojo.cs2v] times 5 60 10
execute if score @s gojo.life matches 1 run title @a[tag=gojo.cs2v] subtitle {"text":"渋谷事変","color":"gray","italic":true}
execute if score @s gojo.life matches 1 run title @a[tag=gojo.cs2v] title {"text":"시부야 사변","color":"white","bold":true}
execute if score @s gojo.life matches 90 run data modify storage gojo:cs2 line set value {text:"팔악검 이계신장 마허라 — 모든 사상에 적응하는 최강의 식신",color:"gray",italic:1b}
execute if score @s gojo.life matches 145 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 140 run data modify storage gojo:cs2 line set value [{text:"료멘 스쿠나  ",color:"red",bold:1b},{text:"…호오. 벌써 「해」에 적응했나.",color:"white"}]
execute if score @s gojo.life matches 140 run tellraw @a[distance=..96] [{"text": "료멘 스쿠나  ", "color": "red", "bold": true}, {"text": "…호오. 벌써 「해」에 적응했나.", "color": "white"}]
execute if score @s gojo.life matches 195 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 200 run data modify storage gojo:cs2 line set value [{text:"료멘 스쿠나  ",color:"red",bold:1b},{text:"재밌군. 그렇다면—",color:"white"}]
execute if score @s gojo.life matches 200 run tellraw @a[distance=..96] [{"text": "료멘 스쿠나  ", "color": "red", "bold": true}, {"text": "재밌군. 그렇다면—", "color": "white"}]
execute if score @s gojo.life matches 230 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 245 run title @a[tag=gojo.cs2v] times 5 25 10
execute if score @s gojo.life matches 245 run title @a[tag=gojo.cs2v] subtitle {"text":"","color":"gray","italic":true}
execute if score @s gojo.life matches 245 run title @a[tag=gojo.cs2v] title {"text":"영역전개","color":"dark_red","bold":true}
execute if score @s gojo.life matches 245 run playsound minecraft:block.respawn_anchor.deplete master @a ~ ~ ~ 3 0.5
execute if score @s gojo.life matches 272 run title @a[tag=gojo.cs2v] times 5 60 10
execute if score @s gojo.life matches 272 run title @a[tag=gojo.cs2v] subtitle {"text":"伏魔御廚子","color":"gray","italic":true}
execute if score @s gojo.life matches 272 run title @a[tag=gojo.cs2v] title {"text":"복마어주자","color":"dark_red","bold":true}
execute if score @s gojo.life matches 272 run tellraw @a[distance=..96] [{"text":"료멘 스쿠나  ","color":"red","bold":true},{"text":"영역전개 「복마어주자(伏魔御廚子)」","color":"dark_red","bold":true}]
execute if score @s gojo.life matches 285 run data modify storage gojo:cs2 line set value {text:"결계를 닫지 않는 영역 — 반경 200m 안의 모든 것을 끝없이 베어낸다",color:"gray",italic:1b}
execute if score @s gojo.life matches 350 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 360 run function gojo:cs2/wheel
execute if score @s gojo.life matches 430 run function gojo:cs2/wheel
execute if score @s gojo.life matches 500 run function gojo:cs2/wheel
execute if score @s gojo.life matches 455 run data modify storage gojo:cs2 line set value [{text:"료멘 스쿠나  ",color:"red",bold:1b},{text:"적응하기 전에 깎아 버리면 그만이다.",color:"white"}]
execute if score @s gojo.life matches 455 run tellraw @a[distance=..96] [{"text": "료멘 스쿠나  ", "color": "red", "bold": true}, {"text": "적응하기 전에 깎아 버리면 그만이다.", "color": "white"}]
execute if score @s gojo.life matches 510 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 525 run data modify storage gojo:cs2 line set value [{text:"료멘 스쿠나  ",color:"red",bold:1b},{text:"…끝이다.",color:"white"}]
execute if score @s gojo.life matches 525 run tellraw @a[distance=..96] [{"text": "료멘 스쿠나  ", "color": "red", "bold": true}, {"text": "…끝이다.", "color": "white"}]
execute if score @s gojo.life matches 555 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 565 run title @a[tag=gojo.cs2v] times 5 30 10
execute if score @s gojo.life matches 565 run title @a[tag=gojo.cs2v] subtitle {"text":"","color":"gray","italic":true}
execute if score @s gojo.life matches 565 run title @a[tag=gojo.cs2v] title {"text":"■","color":"dark_red","bold":true}
execute if score @s gojo.life matches 620 run title @a[tag=gojo.cs2v] times 5 35 10
execute if score @s gojo.life matches 620 run title @a[tag=gojo.cs2v] subtitle {"text":"","color":"gray","italic":true}
execute if score @s gojo.life matches 620 run title @a[tag=gojo.cs2v] title {"text":"「푸가(開)」","color":"gold","bold":true}
execute if score @s gojo.life matches 620 run tellraw @a[distance=..96] [{"text":"료멘 스쿠나  ","color":"red","bold":true},{"text":"■ 「푸가(開)」","color":"gold","bold":true}]
execute if score @s gojo.life matches 730 run data modify storage gojo:cs2 line set value [{text:"료멘 스쿠나  ",color:"red",bold:1b},{text:"…즐거웠다, 마허라.",color:"white"}]
execute if score @s gojo.life matches 730 run tellraw @a[distance=..96] [{"text": "료멘 스쿠나  ", "color": "red", "bold": true}, {"text": "…즐거웠다, 마허라.", "color": "white"}]
execute if score @s gojo.life matches 790 run data modify storage gojo:cs2 line set value {text:""}
execute if score @s gojo.life matches 840 run title @a[tag=gojo.cs2v] times 5 50 10
execute if score @s gojo.life matches 840 run title @a[tag=gojo.cs2v] subtitle {"text":"渋谷事変","color":"gray","italic":true}
execute if score @s gojo.life matches 840 run title @a[tag=gojo.cs2v] title {"text":"— 끝 —","color":"white","bold":true}
