title @a[distance=..16] times 0 12 4
scoreboard players operation #m gojo.tmp = #t gojo.tmp
scoreboard players operation #m gojo.tmp %= #3 gojo.cfg
execute if score #t gojo.tmp matches ..29 if score #m gojo.tmp matches 0 run playsound minecraft:block.note_block.hat master @a ~ ~ ~ 1 1.8
execute if score #t gojo.tmp matches ..29 if score #m gojo.tmp matches 0 run title @a[distance=..16] title [{"text":"?","color":"gray","bold":true},{"text":"  ·  ","color":"white"},{"text":"?","color":"gray","bold":true},{"text":"  ·  ","color":"white"},{"text":"?","color":"gray","bold":true}]
execute if score #t gojo.tmp matches 30 run playsound minecraft:block.note_block.bit master @a ~ ~ ~ 2 1.0
execute if score #t gojo.tmp matches 30..39 run title @a[distance=..16] title [{"score":{"name":"@e[type=marker,tag=gojo.hkcur,limit=1]","objective":"gojo.r1"},"color":"yellow","bold":true},{"text":"  ·  ","color":"white"},{"text":"?","color":"gray","bold":true},{"text":"  ·  ","color":"white"},{"text":"?","color":"gray","bold":true}]
execute if score #t gojo.tmp matches 40 run playsound minecraft:block.note_block.bit master @a ~ ~ ~ 2 1.2
execute if score #t gojo.tmp matches 40..54 run title @a[distance=..16] title [{"score":{"name":"@e[type=marker,tag=gojo.hkcur,limit=1]","objective":"gojo.r1"},"color":"yellow","bold":true},{"text":"  ·  ","color":"white"},{"score":{"name":"@e[type=marker,tag=gojo.hkcur,limit=1]","objective":"gojo.r2"},"color":"yellow","bold":true},{"text":"  ·  ","color":"white"},{"text":"?","color":"gray","bold":true}]
execute if score #t gojo.tmp matches 40 if score @s gojo.r1 = @s gojo.r2 run function gojo:hk/riichi
execute if score #t gojo.tmp matches 55 run title @a[distance=..16] title [{"score":{"name":"@e[type=marker,tag=gojo.hkcur,limit=1]","objective":"gojo.r1"},"color":"yellow","bold":true},{"text":"  ·  ","color":"white"},{"score":{"name":"@e[type=marker,tag=gojo.hkcur,limit=1]","objective":"gojo.r2"},"color":"yellow","bold":true},{"text":"  ·  ","color":"white"},{"score":{"name":"@e[type=marker,tag=gojo.hkcur,limit=1]","objective":"gojo.r3"},"color":"yellow","bold":true}]
execute if score #t gojo.tmp matches 55 run function gojo:hk/result
