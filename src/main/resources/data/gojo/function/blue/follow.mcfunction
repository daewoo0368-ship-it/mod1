# 영창 창: 발사하지 않고 사용자 십자선(시선) 끝을 따라다닌다 (막히면 가까운 곳으로)
tag @s add gojo.me
execute as @a if score @s gojo.id = #owner gojo.id at @s anchored eyes positioned ^ ^ ^10 if block ~ ~ ~ air run tp @e[type=marker,tag=gojo.me,limit=1] ~ ~ ~
execute as @a if score @s gojo.id = #owner gojo.id at @s anchored eyes positioned ^ ^ ^10 unless block ~ ~ ~ air positioned ^ ^ ^-5 if block ~ ~ ~ air run tp @e[type=marker,tag=gojo.me,limit=1] ~ ~ ~
execute as @a if score @s gojo.id = #owner gojo.id at @s anchored eyes positioned ^ ^ ^10 unless block ~ ~ ~ air positioned ^ ^ ^-5 unless block ~ ~ ~ air positioned ^ ^ ^-3 if block ~ ~ ~ air run tp @e[type=marker,tag=gojo.me,limit=1] ~ ~ ~
tag @s remove gojo.me
