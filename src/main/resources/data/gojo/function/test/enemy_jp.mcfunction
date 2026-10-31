scoreboard players set #cast gojo.id 9997
execute rotated ~ 0 positioned ^ ^ ^20 run function gojo:jp/open
execute rotated ~ 0 positioned ^ ^ ^20 as @n[type=marker,tag=gojo.jp,distance=..1] run tag @s add gojo.npc
tellraw @s {"text":"[테스트] 적 마히토의 자폐원돈과 전개 (60초 후 자동 해제)","color":"gray"}
