tag @e[tag=gojo.prin] remove gojo.prin
execute as @e[type=!#gojo:ignore,distance=..4.5,tag=!gojo.sealed] unless score @s gojo.id = #owner gojo.id if data entity @s Health run function gojo:prison/gaze
execute as @e[scores={gojo.prt=1..},tag=!gojo.prin] run scoreboard players set @s gojo.prt 0
