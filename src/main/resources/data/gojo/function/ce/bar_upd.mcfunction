$execute store result bossbar gojo:ce$(id) value run scoreboard players get @s gojo.ce
scoreboard players operation #m gojo.tmp = #gt gojo.tmp
scoreboard players operation #m gojo.tmp %= #10 gojo.cfg
execute unless score #m gojo.tmp matches 0 run return 0
$execute if entity @s[tag=jjk.riding] run return run bossbar set gojo:ce$(id) players
$execute if entity @s[tag=!gojo.modhud] run bossbar set gojo:ce$(id) players @s
$execute if entity @s[tag=gojo.modhud] run bossbar set gojo:ce$(id) players
$execute store result bossbar gojo:ce$(id) max run scoreboard players get @s gojo.cemax
$execute if entity @s[tag=gojo.hr] run return run bossbar set gojo:ce$(id) name {"text":"주력 없음 — 천여주박","color":"dark_green","bold":true}
$execute if entity @s[tag=gojo.jackpot] run bossbar set gojo:ce$(id) color yellow
$execute if entity @s[tag=gojo.jackpot] run return run bossbar set gojo:ce$(id) name {"text":"주력 ∞ — 대박","color":"gold","bold":true}
$execute if score @s gojo.ce matches ..199 run bossbar set gojo:ce$(id) color red
$execute if score @s gojo.ce matches 200.. run bossbar set gojo:ce$(id) color blue
$execute if entity @s[tag=gojo.six] run return run bossbar set gojo:ce$(id) name [{"text":"주력  ","color":"aqua","bold":true},{"score":{"name":"@s","objective":"gojo.ce"},"color":"white"},{"text":" / ","color":"gray"},{"score":{"name":"@s","objective":"gojo.cemax"},"color":"gray"},{"text":"  · 육안","color":"aqua"}]
$bossbar set gojo:ce$(id) name [{"text":"주력  ","color":"aqua","bold":true},{"score":{"name":"@s","objective":"gojo.ce"},"color":"white"},{"text":" / ","color":"gray"},{"score":{"name":"@s","objective":"gojo.cemax"},"color":"gray"}]
