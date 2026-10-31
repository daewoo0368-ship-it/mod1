scoreboard players operation #a gojo.tmp = @s gojo.id
tag @s add gojo.cself
execute as @e[type=marker,tag=gojo.dom,tag=!gojo.clashing,tag=!gojo.closing,distance=0.1..40,sort=nearest,limit=1] unless score @s gojo.id = #a gojo.tmp run function gojo:clash/start
tag @s remove gojo.cself
