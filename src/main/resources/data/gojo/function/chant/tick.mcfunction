scoreboard players add @s gojo.ct 1
execute unless predicate gojo:sneaking run return run function gojo:chant/cancel
effect give @s minecraft:slowness 1 3 true
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 1 run title @s subtitle {"text":"「위상」","color":"aqua","italic":true}
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 1 run title @s title ""
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 1 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.5
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 16 run title @s subtitle {"text":"「황혼」","color":"aqua","italic":true}
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 16 run title @s title ""
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 16 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.7
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 31 run title @s subtitle {"text":"「지혜의 눈」","color":"aqua","italic":true}
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 31 run title @s title ""
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 31 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.9
execute if score @s gojo.chant matches 1 run particle minecraft:dust{color:[0.1,0.35,1.0],scale:1.5} ~ ~1 ~ 1 1 1 0 6 force
execute if score @s gojo.chant matches 1 if score @s gojo.ct matches 45.. run return run function gojo:chant/fire
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 1 run title @s subtitle {"text":"「위상」","color":"red","italic":true}
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 1 run title @s title ""
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 1 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.5
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 16 run title @s subtitle {"text":"「바라밀」","color":"red","italic":true}
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 16 run title @s title ""
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 16 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.7
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 31 run title @s subtitle {"text":"「빛의 기둥」","color":"red","italic":true}
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 31 run title @s title ""
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 31 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.9
execute if score @s gojo.chant matches 2 run particle minecraft:dust{color:[1.0,0.08,0.08],scale:1.5} ~ ~1 ~ 1 1 1 0 6 force
execute if score @s gojo.chant matches 2 if score @s gojo.ct matches 45.. run return run function gojo:chant/fire
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 1 run title @s subtitle {"text":"「구강」","color":"light_purple","italic":true}
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 1 run title @s title ""
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 1 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.5
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 16 run title @s subtitle {"text":"「편광」","color":"light_purple","italic":true}
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 16 run title @s title ""
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 16 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.7
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 31 run title @s subtitle {"text":"「까마귀와 성명」","color":"light_purple","italic":true}
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 31 run title @s title ""
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 31 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 0.9
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 46 run title @s subtitle {"text":"「표리의 틈새」","color":"light_purple","italic":true}
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 46 run title @s title ""
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 46 run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 2 1.1
execute if score @s gojo.chant matches 3 run particle minecraft:dust{color:[0.6,0.1,1.0],scale:1.5} ~ ~1 ~ 1 1 1 0 6 force
execute if score @s gojo.chant matches 3 if score @s gojo.ct matches 60.. run return run function gojo:chant/fire
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 1 run title @s subtitle {"text":"「용린」","color":"white","italic":true}
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 1 run title @s title ""
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 1 run playsound minecraft:item.trident.return master @a ~ ~ ~ 2 0.50
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 16 run title @s subtitle {"text":"「반발」","color":"white","italic":true}
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 16 run title @s title ""
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 16 run playsound minecraft:item.trident.return master @a ~ ~ ~ 2 0.75
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 31 run title @s subtitle {"text":"「한쌍의 유성」","color":"white","italic":true}
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 31 run title @s title ""
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 31 run playsound minecraft:item.trident.return master @a ~ ~ ~ 2 1.00
execute if score @s gojo.chant matches 4 run particle minecraft:dust{color:[1.0,1.0,1.0],scale:1.2} ~ ~1 ~ 1 1 1 0 4 force
execute if score @s gojo.chant matches 4 run particle minecraft:dust{color:[0.6,0.0,0.0],scale:1.2} ~ ~1 ~ 1 1 1 0 3 force
execute if score @s gojo.chant matches 4 if score @s gojo.ct matches 45.. run return run function gojo:chant/fire
title @s actionbar {"text":"영창 중... (쉬프트 유지)","color":"gray"}
