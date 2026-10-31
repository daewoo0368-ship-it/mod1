execute facing entity @e[type=marker,tag=gojo.cB,limit=1] feet run tp @s ~ ~ ~ ~ ~
scoreboard players set #n gojo.tmp 0
execute at @s run function gojo:clash/count
execute facing entity @e[type=marker,tag=gojo.cA,limit=1] feet positioned as @e[type=marker,tag=gojo.cA,limit=1] positioned ~ ~2 ~ run tp @s ~ ~ ~
execute at @s facing entity @e[type=marker,tag=gojo.cB,limit=1] feet run tp @s ~ ~ ~ ~ ~
scoreboard players operation #k gojo.tmp = @e[type=marker,tag=gojo.clash,limit=1] gojo.cv
scoreboard players operation #k gojo.tmp += #100 gojo.cfg
scoreboard players operation #k gojo.tmp *= #n gojo.tmp
scoreboard players operation #k gojo.tmp /= #200 gojo.cfg
scoreboard players set #i gojo.tmp 0
scoreboard players set #ta gojo.tmp 1
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.ms] run scoreboard players set #ta gojo.tmp 2
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.jp] run scoreboard players set #ta gojo.tmp 3
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.jg] run scoreboard players set #ta gojo.tmp 4
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.hg] run scoreboard players set #ta gojo.tmp 5
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.tk] run scoreboard players set #ta gojo.tmp 6
execute if entity @e[type=marker,tag=gojo.cA,tag=gojo.hk] run scoreboard players set #ta gojo.tmp 7
scoreboard players set #tb gojo.tmp 1
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.ms] run scoreboard players set #tb gojo.tmp 2
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.jp] run scoreboard players set #tb gojo.tmp 3
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.jg] run scoreboard players set #tb gojo.tmp 4
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.hg] run scoreboard players set #tb gojo.tmp 5
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.tk] run scoreboard players set #tb gojo.tmp 6
execute if entity @e[type=marker,tag=gojo.cB,tag=gojo.hk] run scoreboard players set #tb gojo.tmp 7
execute at @s run function gojo:clash/draw
kill @s
