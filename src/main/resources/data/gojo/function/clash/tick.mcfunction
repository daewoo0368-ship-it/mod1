scoreboard players add @s gojo.life 1
scoreboard players operation #a gojo.tmp = @s gojo.ca
scoreboard players operation #b gojo.tmp = @s gojo.cb
tag @e[type=marker,tag=gojo.dom] remove gojo.cA
tag @e[type=marker,tag=gojo.dom] remove gojo.cB
execute as @e[type=marker,tag=gojo.dom,tag=!gojo.closing] if score @s gojo.id = #a gojo.tmp run tag @s add gojo.cA
execute as @e[type=marker,tag=gojo.dom,tag=!gojo.closing] if score @s gojo.id = #b gojo.tmp run tag @s add gojo.cB
execute unless entity @e[type=marker,tag=gojo.cA] run return run function gojo:clash/end
execute unless entity @e[type=marker,tag=gojo.cB] run return run function gojo:clash/end
scoreboard players set #pa gojo.tmp 1
scoreboard players set #pb gojo.tmp 1
execute as @a[scores={gojo.push=1..}] if score @s gojo.id = #a gojo.tmp run scoreboard players add #pa gojo.tmp 3
execute as @a[scores={gojo.push=1..}] if score @s gojo.id = #b gojo.tmp run scoreboard players add #pb gojo.tmp 3
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.npc] store result score #r gojo.tmp run random value 0..3
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.npc] run scoreboard players operation #pa gojo.tmp += #r gojo.tmp
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.npc] store result score #r gojo.tmp run random value 0..3
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.npc] run scoreboard players operation #pb gojo.tmp += #r gojo.tmp
scoreboard players operation @s gojo.cv += #pa gojo.tmp
scoreboard players operation @s gojo.cv -= #pb gojo.tmp
execute store result bossbar gojo:clash value run scoreboard players operation #bv gojo.tmp = @s gojo.cv
execute store result bossbar gojo:clash value run scoreboard players operation #bv gojo.tmp += #100 gojo.cfg
bossbar set gojo:clash players @a[distance=..80]
execute as @a[distance=..80] if score @s gojo.id = #a gojo.tmp run title @s actionbar [{"text":"영역 싸움 ","color":"light_purple"},{"text":"— 도구 우클릭을 꾹 눌러 밀어붙여라!","color":"gray"}]
execute as @a[distance=..80] if score @s gojo.id = #b gojo.tmp run title @s actionbar [{"text":"영역 싸움 ","color":"light_purple"},{"text":"— 도구 우클릭을 꾹 눌러 밀어붙여라!","color":"gray"}]
execute if score @s gojo.life matches 1.. store result score #m gojo.tmp run scoreboard players get @s gojo.life
scoreboard players operation #m gojo.tmp %= #2 gojo.cfg
execute if score #m gojo.tmp matches 0 at @e[type=marker,tag=gojo.cA,limit=1] positioned ~ ~2 ~ run function gojo:clash/line
execute if predicate gojo:chance10 run playsound minecraft:block.glass.break master @a ~ ~ ~ 1.5 0.6
execute if score @s gojo.cv matches 100.. as @e[type=marker,tag=gojo.cB] at @s run return run function gojo:clash/lose
execute if score @s gojo.cv matches ..-100 as @e[type=marker,tag=gojo.cA] at @s run return run function gojo:clash/lose
