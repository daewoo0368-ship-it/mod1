execute if score @s gojo.atk matches 20 run function gojo:maho/anim/windup
execute if score @s gojo.atk matches 20 run playsound minecraft:item.trident.return master @a ~ ~ ~ 1.5 0.6
execute if score @s gojo.atk matches 14 run function gojo:maho/anim/strike
execute if score @s gojo.atk matches 14 run playsound minecraft:entity.player.attack.sweep master @a ~ ~ ~ 2 0.5
execute if score @s gojo.atk matches 14 run playsound minecraft:entity.ravager.attack master @a ~ ~ ~ 1.5 0.7
execute if score @s gojo.atk matches 14 rotated as @s rotated ~ 0 positioned ^ ^1.5 ^2.5 run particle minecraft:sweep_attack ~ ~ ~ 0.6 0.4 0.6 0 4 force
execute if score @s gojo.atk matches 6 run function gojo:maho/anim/idle
execute if score @s gojo.atk matches 6 run scoreboard players set @s gojo.af 0
scoreboard players remove @s gojo.atk 1
