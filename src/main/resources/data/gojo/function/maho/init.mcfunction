tag @s add gojo.maho
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.adapt 0
scoreboard players set @s gojo.acd 0
scoreboard players set @s gojo.atk 0
scoreboard players set @s gojo.af 0
data merge entity @s {PlayerCreated:1b,PersistenceRequired:1b,DeathLootTable:"minecraft:empty",CustomName:{"text":"마허라","color":"white","bold":true},CustomNameVisible:1b,attributes:[{id:"minecraft:max_health",base:300d},{id:"minecraft:attack_damage",base:25d},{id:"minecraft:movement_speed",base:0.35d},{id:"minecraft:scale",base:1.5d},{id:"minecraft:follow_range",base:48d},{id:"minecraft:knockback_resistance",base:1d}]}
data merge entity @s {Health:300f}
effect give @s minecraft:invisibility infinite 0 true
data merge entity @s {NoAI:1b,Invulnerable:1b,NoGravity:1b}
scoreboard players set @s gojo.ct 21
tp @s ~ ~ ~ ~ 0
function gojo:maho/model
