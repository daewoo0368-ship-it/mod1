scoreboard players set #did gojo.tmp 1
execute if entity @s[tag=gojo.clashing] as @e[type=marker,tag=gojo.clash] if score @s gojo.ca = #nul gojo.id run function gojo:clash/end
execute if entity @s[tag=gojo.clashing] as @e[type=marker,tag=gojo.clash] if score @s gojo.cb = #nul gojo.id run function gojo:clash/end
tag @s remove gojo.clashing
execute at @s if entity @s[tag=gojo.domain] run function gojo:domain/close
execute at @s if entity @s[tag=gojo.ms] run function gojo:ms/close
execute at @s if entity @s[tag=gojo.jp] run function gojo:jp/close
execute at @s if entity @s[tag=gojo.jg] run function gojo:jg/close
execute at @s if entity @s[tag=gojo.hg] run function gojo:hg/close
execute at @s if entity @s[tag=gojo.tk] run function gojo:tk/close
execute at @s if entity @s[tag=gojo.hk] run function gojo:hk/close
execute at @s run title @a[distance=..40] actionbar {"text":"천역모가 결계를 꿰뚫었다 — 영역 붕괴","color":"dark_purple"}
