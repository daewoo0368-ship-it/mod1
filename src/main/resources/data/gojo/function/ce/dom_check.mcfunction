scoreboard players operation #nul gojo.id = @s gojo.id
scoreboard players set #out gojo.tmp 0
execute as @a if score @s gojo.id = #nul gojo.id if score @s gojo.ce matches ..0 unless entity @s[tag=gojo.jackpot] run scoreboard players set #out gojo.tmp 1
execute if score #out gojo.tmp matches 1 run function gojo:ce/collapse
