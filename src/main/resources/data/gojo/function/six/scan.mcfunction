tag @a remove gojo.six
execute as @a if items entity @s container.* *[minecraft:custom_data~{gojo_rikugan:1b}] run tag @s add gojo.six
execute as @a if items entity @s weapon.offhand *[minecraft:custom_data~{gojo_rikugan:1b}] run tag @s add gojo.six
execute as @a[tag=gojo.six,tag=!gojo.sixon] at @s run function gojo:six/awaken
execute as @a[tag=!gojo.six,tag=gojo.sixon] run function gojo:six/lose
execute as @a[tag=gojo.six] at @s run function gojo:six/tick
