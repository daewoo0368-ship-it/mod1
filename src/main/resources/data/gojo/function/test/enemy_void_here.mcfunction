scoreboard players set #cast gojo.id 9998
function gojo:domain/open
execute as @n[type=marker,tag=gojo.domain,distance=..1] run tag @s add gojo.npc
tellraw @s {"text":"[테스트] 적 고죠의 무량공처를 내 위치에 전개 (60초 후 자동 해제)","color":"gray"}
