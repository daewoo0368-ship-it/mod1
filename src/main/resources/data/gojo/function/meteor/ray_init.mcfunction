tp @s ~ ~ ~ ~ ~
scoreboard players set #n gojo.tmp 0
execute at @s run function gojo:meteor/ray
execute at @s rotated ~ 0 positioned ^ ^45 ^-18 summon marker run function gojo:meteor/init
kill @s
