data modify storage gojo:cs line set value {text:""}
execute as @a[tag=gojo.csv] at @s run function gojo:cs/view_off
execute as @a[tag=gojo.cstp] run damage @s 100000 minecraft:magic by @a[tag=gojo.csg,limit=1]
kill @e[type=marker,tag=gojo.csb]
kill @e[type=marker,tag=gojo.csret]
kill @e[type=marker,tag=gojo.cslook]
kill @e[type=item_display,tag=gojo.cscam]
kill @e[tag=gojo.csactor]
tag @a remove gojo.csg
tag @a remove gojo.cstp
kill @e[type=marker,tag=gojo.cs]
