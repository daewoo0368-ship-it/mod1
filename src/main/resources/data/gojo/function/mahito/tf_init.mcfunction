tag @s remove gojo.tfnew
team join gojo.mahito @s
scoreboard players set @s gojo.life 1800
data merge entity @s {PersistenceRequired:1b,CanPickUpLoot:0b,IsBaby:0b,DeathLootTable:"minecraft:empty",CustomName:{"text":"개조인간","color":"dark_aqua"},attributes:[{id:"minecraft:max_health",base:30d},{id:"minecraft:attack_damage",base:8d},{id:"minecraft:movement_speed",base:0.3d},{id:"minecraft:follow_range",base:32d}]}
data merge entity @s {Health:30f}
effect give @s minecraft:fire_resistance infinite 0 true
execute store result score #r gojo.tmp run random value 0..4
execute if score #r gojo.tmp matches 0 run attribute @s minecraft:scale base set 0.6
execute if score #r gojo.tmp matches 1 run attribute @s minecraft:scale base set 0.8
execute if score #r gojo.tmp matches 2 run attribute @s minecraft:scale base set 1.2
execute if score #r gojo.tmp matches 3 run attribute @s minecraft:scale base set 1.5
execute if score #r gojo.tmp matches 4 run attribute @s minecraft:scale base set 1.8
