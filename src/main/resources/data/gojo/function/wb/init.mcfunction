tag @s add gojo.wb
data merge entity @s {teleport_duration:1,block_state:{Name:"minecraft:stripped_oak_wood"},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[-0.35f,-0.35f,-0.35f],scale:[0.7f,0.7f,0.7f]}}
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
tp @s ~ ~ ~ ~ ~
