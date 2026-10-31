scoreboard players set #cast gojo.id 9994
execute rotated ~ 0 positioned ^ ^ ^20 run function gojo:hk/open
execute rotated ~ 0 positioned ^ ^ ^20 as @n[type=marker,tag=gojo.hk,distance=..1] run tag @s add gojo.npc
tellraw @s {"text":"[테스트] 적 하카리의 좌살박도 전개","color":"gray"}
