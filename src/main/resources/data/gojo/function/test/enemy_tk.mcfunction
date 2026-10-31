scoreboard players set #cast gojo.id 9995
execute rotated ~ 0 positioned ^ ^ ^20 run function gojo:tk/open
execute rotated ~ 0 positioned ^ ^ ^20 as @n[type=marker,tag=gojo.tk,distance=..1] run tag @s add gojo.npc
tellraw @s {"text":"[테스트] 적 하나미의 타이코카이 전개 (60초 후 자동 해제)","color":"gray"}
