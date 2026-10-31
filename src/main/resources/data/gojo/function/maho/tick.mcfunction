execute if score @s gojo.ct matches 1.. run return run function gojo:maho/rise
scoreboard players operation #owner gojo.id = @s gojo.id
tag @a remove gojo.mo
execute as @a[tag=!gojo.sealed] if score @s gojo.id = #owner gojo.id run tag @s add gojo.mo
execute unless entity @a[tag=gojo.mo] run return run function gojo:maho/dismiss
execute unless entity @a[tag=gojo.mo,distance=..18] run tp @s @p[tag=gojo.mo]
execute on passengers if entity @s[tag=gojo.mbody] rotated as @n[type=iron_golem,tag=gojo.maho] run rotate @s ~ 0
scoreboard players remove @s[scores={gojo.acd=1..}] gojo.acd 1
execute if score @s gojo.acd matches 0 if score @s gojo.adapt matches ..4 if data entity @s {HurtTime:10s} run function gojo:maho/adapt
particle minecraft:squid_ink ~ ~0.1 ~ 0.6 0 0.6 0 1
function gojo:maho/animate
scoreboard players add @s gojo.life 1
execute if score @s gojo.life matches 20.. run function gojo:maho/taunt
