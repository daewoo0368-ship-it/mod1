scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if entity @s[tag=gojo.prc] run return run function gojo:prison/hold
execute if score @s gojo.life matches 200.. run return run function gojo:prison/fail
execute if score @s gojo.life matches 1..20 run particle minecraft:dust{color:[0.45,0.0,0.05],scale:1} ~ ~0.8 ~ 0.5 0.5 0.5 0 6 force
execute if score @s gojo.life matches 20 run playsound minecraft:block.iron_door.open master @a ~ ~ ~ 2 0.4
execute if score @s gojo.life matches 20 run playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 2 0.5
execute if score @s gojo.life matches 20 run title @a[distance=..16] actionbar {"text":"옥문 개문 — 4칸 안에 2초 머무르면 봉인된다","color":"dark_red"}
execute if score @s gojo.life matches 20.. run function gojo:prison/ring
execute if score @s gojo.life matches 20.. if predicate gojo:chance10 run playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 1 0.6
execute if score @s gojo.life matches 20.. run function gojo:prison/scan
