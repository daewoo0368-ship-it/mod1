scoreboard players set @s gojo.life 0
execute as @e[type=#gojo:hostile,distance=..16] unless function gojo:maho/targets_me run damage @s 0.5 minecraft:mob_attack by @n[type=iron_golem,tag=gojo.maho]
