execute positioned ^ ^ ^0.5 unless block ~ ~ ~ #gojo:passable run return 0
execute positioned ^ ^ ^0.5 unless block ~ ~1 ~ #gojo:passable run return 0
execute positioned ^ ^ ^0.5 run tp @s ~ ~ ~
scoreboard players remove #kb gojo.tmp 1
execute if score #kb gojo.tmp matches 1.. positioned as @s run function gojo:flash/kb
