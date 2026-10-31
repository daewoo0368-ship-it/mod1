scoreboard players set #cast gojo.id 9999
execute rotated ~ 0 positioned ^ ^ ^20 rotated ~180 0 run function gojo:ms/open
execute rotated ~ 0 positioned ^ ^ ^20 as @n[type=marker,tag=gojo.ms,distance=..1] run tag @s add gojo.npc
tellraw @s {"text":"[테스트] 적 스쿠나의 복마어주자 전개 (60초 후 자동 해제)","color":"gray"}
