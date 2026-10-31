execute if entity @s[tag=gojo.clashing] as @e[type=marker,tag=gojo.clash] if score @s gojo.ca = #nul gojo.id run function gojo:clash/end
execute if entity @s[tag=gojo.clashing] as @e[type=marker,tag=gojo.clash] if score @s gojo.cb = #nul gojo.id run function gojo:clash/end
tag @s remove gojo.clashing
execute if entity @s[tag=gojo.domain] run function gojo:domain/close
execute if entity @s[tag=gojo.ms] run function gojo:ms/close
execute if entity @s[tag=gojo.jp] run function gojo:jp/close
execute if entity @s[tag=gojo.jg] run function gojo:jg/close
execute if entity @s[tag=gojo.hg] run function gojo:hg/close
execute if entity @s[tag=gojo.tk] run function gojo:tk/close
execute if entity @s[tag=gojo.hk] run function gojo:hk/close
title @a[distance=..40] actionbar {"text":"주력이 바닥나 영역이 붕괴했다","color":"dark_aqua"}
