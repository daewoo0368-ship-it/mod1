scoreboard players add @s gojo.adapt 1
scoreboard players set @s gojo.acd 40
effect give @s minecraft:instant_health 1 4 true
playsound minecraft:block.bell.use master @a ~ ~ ~ 2 0.6
playsound minecraft:block.chain.place master @a ~ ~ ~ 2 0.5
particle minecraft:dust{color:[1.0,0.85,0.2],scale:1.5} ~ ~4.8 ~ 1 0.2 1 0 30 force
execute if score @s gojo.adapt matches 1 run effect give @s minecraft:resistance infinite 0 true
execute if score @s gojo.adapt matches 1 on passengers if entity @s[tag=gojo.mwheel] run rotate @s 45 0
execute if score @s gojo.adapt matches 2 run effect give @s minecraft:resistance infinite 1 true
execute if score @s gojo.adapt matches 2 on passengers if entity @s[tag=gojo.mwheel] run rotate @s 90 0
execute if score @s gojo.adapt matches 3 run effect give @s minecraft:resistance infinite 2 true
execute if score @s gojo.adapt matches 3 on passengers if entity @s[tag=gojo.mwheel] run rotate @s 135 0
execute if score @s gojo.adapt matches 4 run effect give @s minecraft:resistance infinite 3 true
execute if score @s gojo.adapt matches 4 on passengers if entity @s[tag=gojo.mwheel] run rotate @s 180 0
execute if score @s gojo.adapt matches 5 run effect give @s minecraft:resistance infinite 4 true
execute if score @s gojo.adapt matches 5 on passengers if entity @s[tag=gojo.mwheel] run rotate @s 225 0
title @a[tag=gojo.mo] actionbar [{"text":"마허라의 법진이 돈다 — 적응 ","color":"gold"},{"score":{"name":"@s","objective":"gojo.adapt"},"color":"white"},{"text":"/5","color":"gold"}]
execute if score @s gojo.adapt matches 5 run title @a[distance=..32] actionbar {"text":"마허라 — 완전 적응","color":"gold","bold":true}
