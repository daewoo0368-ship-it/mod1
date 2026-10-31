execute as @a unless score @s gojo.cemax matches 0.. run function gojo:ce/init
scoreboard players add @a[tag=!gojo.hr] gojo.ce 2
scoreboard players set @a[tag=gojo.hr] gojo.ce 0
execute as @a[tag=gojo.jackpot] run scoreboard players operation @s gojo.ce = @s gojo.cemax
scoreboard players remove @a[tag=gojo.inf,tag=!gojo.jackpot] gojo.ce 3
tag @a remove gojo.downer
execute as @e[type=marker,tag=gojo.dom,tag=!gojo.closing,tag=!gojo.npc] run function gojo:ce/mark_owner
scoreboard players remove @a[tag=gojo.downer,tag=!gojo.jackpot] gojo.ce 3
function gojo:six/scan
execute as @a if score @s gojo.ce > @s gojo.cemax run scoreboard players operation @s gojo.ce = @s gojo.cemax
scoreboard players set @a[scores={gojo.ce=..-1}] gojo.ce 0
execute as @a[tag=gojo.inf,scores={gojo.ce=..0}] run function gojo:ce/inf_out
execute as @e[type=marker,tag=gojo.dom,tag=!gojo.closing,tag=!gojo.npc] at @s run function gojo:ce/dom_check
scoreboard players add #gt gojo.tmp 1
execute as @a run function gojo:ce/bar
