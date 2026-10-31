scoreboard players operation #owner gojo.id = @a[tag=gojo.cs2p,limit=1] gojo.id
particle minecraft:crimson_spore ~ ~3 ~ 20 6 20 0 50 force
particle minecraft:ash ~ ~3 ~ 20 6 20 0 40 force
particle minecraft:dust{color:[0.6,0.0,0.0],scale:1.2} ~ ~3 ~ 20 6 20 0 25 force
function gojo:ms/cut
function gojo:ms/cut
function gojo:ms/cut
function gojo:ms/cut
function gojo:ms/cut
function gojo:ms/cut
execute if predicate gojo:half run playsound minecraft:entity.player.attack.sweep master @a ~ ~ ~ 3 0.8
scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #4 gojo.cfg
execute if score #m gojo.tmp matches 0 if score @s gojo.life matches 300..619 run function gojo:cs2/shred
execute if score #m gojo.tmp matches 2 at @e[tag=gojo.csm,limit=1] run function gojo:cs2/slash_on_maho
