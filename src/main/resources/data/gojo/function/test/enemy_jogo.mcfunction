scoreboard players set #cast gojo.id 9996
execute rotated ~ 0 positioned ^ ^ ^20 run function gojo:jg/open
execute rotated ~ 0 positioned ^ ^ ^20 as @n[type=marker,tag=gojo.jg,distance=..1] run tag @s add gojo.npc
tellraw @s {"text":"[테스트] 적 죠고의 개관철위산 전개 (60초 후 자동 해제)","color":"gray"}
