advancement revoke @s only gojo:hit_ten
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
tag @s add gojo.atk
execute as @e[type=!#gojo:ignore,distance=..7,nbt={HurtTime:10s}] if function gojo:flash/is_mine at @s run function gojo:ten/hit
tag @s remove gojo.atk
