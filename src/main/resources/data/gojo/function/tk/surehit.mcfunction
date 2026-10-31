tag @s add gojo.ttrap
damage @s 6 minecraft:magic
effect give @s minecraft:slowness 2 4 true
effect give @s minecraft:weakness 2 1 true
effect give @s minecraft:wither 2 0 true
particle minecraft:block{block_state:"minecraft:mangrove_roots"} ~ ~0.5 ~ 0.4 0.5 0.4 0 25 force
particle minecraft:cherry_leaves ~ ~1.5 ~ 0.4 0.4 0.4 0 6 force
playsound minecraft:block.roots.place master @a ~ ~ ~ 1.5 0.6
execute as @a if score @s gojo.id = #owner gojo.id run effect give @s minecraft:instant_health 1 0 true
execute if entity @s[type=player] run title @s actionbar {"text":"뿌리가 몸을 휘감고 주술의 싹이 생명을 빨아들인다","color":"dark_green"}
