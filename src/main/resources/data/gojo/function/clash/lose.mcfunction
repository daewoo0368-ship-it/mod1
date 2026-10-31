tag @a[distance=..64] add gojo.fxshake
tag @a[distance=..64] add gojo.fxflash_w
particle minecraft:explosion_emitter ~ ~3 ~ 3 2 3 0 3 force
particle minecraft:dust{color:[0.0,0.0,0.0],scale:3} ~ ~3 ~ 8 5 8 0 200 force
particle minecraft:end_rod ~ ~3 ~ 6 4 6 0.2 80 force
playsound minecraft:block.glass.break master @a ~ ~ ~ 4 0.4
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 3 0.6
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 3 0.5
scoreboard players operation #loser gojo.tmp = @s gojo.id
tag @s remove gojo.clashing
execute if entity @s[tag=gojo.domain] run function gojo:domain/close
execute if entity @s[tag=gojo.ms] run function gojo:ms/close
execute if entity @s[tag=gojo.jp] run function gojo:jp/close
execute if entity @s[tag=gojo.jg] run function gojo:jg/close
execute if entity @s[tag=gojo.hg] run function gojo:hg/close
execute if entity @s[tag=gojo.tk] run function gojo:tk/close
execute if entity @s[tag=gojo.hk] run function gojo:hk/close
execute as @a if score @s gojo.id = #loser gojo.tmp run function gojo:clash/loser_fx
execute as @a[distance=..80] unless score @s gojo.id = #loser gojo.tmp run title @s title {"text":"영역 붕괴","color":"gray","bold":true}
execute as @a[distance=..80] unless score @s gojo.id = #loser gojo.tmp run title @s subtitle {"text":"영역 싸움 승부가 났다","color":"light_purple"}
execute as @e[type=marker,tag=gojo.clash] if score @s gojo.ca = #loser gojo.tmp run function gojo:clash/end
execute as @e[type=marker,tag=gojo.clash] if score @s gojo.cb = #loser gojo.tmp run function gojo:clash/end
