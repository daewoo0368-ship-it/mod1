clear @s *[minecraft:custom_data~{gojo_exec:1b}]
kill @e[type=item,nbt={Item:{components:{"minecraft:custom_data":{gojo_exec:1b}}}}]
tag @s remove gojo.exec
title @s actionbar {"text":"사형 집행인의 검이 사라졌다","color":"gray"}
