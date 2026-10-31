tag @s add gojo.hr
function gojo:ten/give
tag @s remove gojo.inf
scoreboard players set @s gojo.chant 0
scoreboard players set @s gojo.fuga 0
title @s times 0 30 10
title @s subtitle {"text":"주력을 버리고, 육체를 얻는다","color":"gray","italic":true}
title @s title {"text":"천여주박","color":"dark_green","bold":true}
playsound minecraft:entity.iron_golem.repair master @a ~ ~ ~ 1.5 0.6
playsound minecraft:item.armor.equip_netherite master @a ~ ~ ~ 1.5 0.7
playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 2 1.2
particle minecraft:dust{color:[0.15,0.35,0.15],scale:1.6} ~ ~1 ~ 0.5 0.9 0.5 0 40 force
particle minecraft:crit ~ ~1 ~ 0.4 0.8 0.4 0.4 20 force
attribute @s minecraft:attack_damage modifier add gojo:hr 6 add_value
attribute @s minecraft:movement_speed modifier add gojo:hr 0.6 add_multiplied_base
attribute @s minecraft:jump_strength modifier add gojo:hr 0.3 add_multiplied_base
attribute @s minecraft:safe_fall_distance modifier add gojo:hr 12 add_value
attribute @s minecraft:attack_speed modifier add gojo:hr 1.5 add_value
attribute @s minecraft:max_health modifier add gojo:hr 20 add_value
attribute @s minecraft:knockback_resistance modifier add gojo:hr 0.5 add_value
attribute @s minecraft:step_height modifier add gojo:hr 0.5 add_value
