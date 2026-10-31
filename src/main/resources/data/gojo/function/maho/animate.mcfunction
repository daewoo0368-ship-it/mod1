execute store result score #nx gojo.tmp run data get entity @s Pos[0] 100
execute store result score #nz gojo.tmp run data get entity @s Pos[2] 100
scoreboard players operation #dx gojo.tmp = #nx gojo.tmp
scoreboard players operation #dx gojo.tmp -= @s gojo.px
scoreboard players operation #dz gojo.tmp = #nz gojo.tmp
scoreboard players operation #dz gojo.tmp -= @s gojo.pz
scoreboard players operation @s gojo.px = #nx gojo.tmp
scoreboard players operation @s gojo.pz = #nz gojo.tmp
execute if score #dx gojo.tmp matches ..-1 run scoreboard players operation #dx gojo.tmp *= #-1 gojo.cfg
execute if score #dz gojo.tmp matches ..-1 run scoreboard players operation #dz gojo.tmp *= #-1 gojo.cfg
scoreboard players operation #dx gojo.tmp += #dz gojo.tmp
execute if score @s gojo.atk matches -1 run function gojo:maho/anim/idle
execute if score @s gojo.atk matches ..-1 run return run scoreboard players add @s gojo.atk 1
execute if score @s gojo.atk matches 1.. run return run function gojo:maho/atk_tick
scoreboard players set #tg gojo.tmp 0
execute on target if entity @s[distance=..4.5] run scoreboard players set #tg gojo.tmp 1
execute if score #tg gojo.tmp matches 1 run scoreboard players set @s gojo.atk 20
execute if score #tg gojo.tmp matches 1 run scoreboard players set #tg gojo.tmp 0
execute if score @s gojo.atk matches 20 run return run function gojo:maho/atk_tick
execute if score #dx gojo.tmp matches 4.. run function gojo:maho/walk
execute if score #dx gojo.tmp matches ..3 if score @s gojo.af matches 1.. run function gojo:maho/stop
