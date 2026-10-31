data modify storage gojo:cs2 line set value {text:""}
execute as @a[tag=gojo.cs2v] at @s run function gojo:cs2/view_off
execute as @a[tag=gojo.cs2p] at @s run tp @s @e[type=mannequin,tag=gojo.cs2s,limit=1]
execute as @a[tag=gojo.cs2mp] run damage @s 100000 minecraft:magic by @a[tag=gojo.cs2p,limit=1]
tag @a remove gojo.cs2mp
execute as @e[tag=gojo.csm,limit=1] on passengers run kill @s
execute as @e[tag=gojo.csm] at @s run tp @s ~ -400 ~
kill @e[tag=gojo.csm]
kill @e[tag=gojo.cs2actor]
kill @e[type=marker,tag=gojo.cs2ret]
tag @a remove gojo.cs2p
kill @e[type=marker,tag=gojo.cs2]
