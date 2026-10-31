function gojo:cs2/end
tag @a remove gojo.six
tag @a remove gojo.sixon
execute as @a[tag=gojo.cebar] run function gojo:ce/uninstall_bar
function gojo:cs/end
kill @e[type=marker,tag=gojo.hk]
kill @e[tag=gojo.hwall]
tag @e remove gojo.htrap
tag @a remove gojo.jackpot
kill @e[tag=gojo.wb]
kill @e[type=marker,tag=gojo.kf]
kill @e[tag=gojo.kfd]
kill @e[type=marker,tag=gojo.tk]
kill @e[tag=gojo.twall]
tag @e remove gojo.ttrap
kill @e[tag=gojo.hgd]
kill @e[type=marker,tag=gojo.hg]
tag @e remove gojo.defend
tag @a remove gojo.confisc
execute as @a[tag=gojo.exec] run function gojo:hg/sword_end
execute as @e[tag=gojo.sealed] run function gojo:prison/free
kill @e[tag=gojo.prd]
kill @e[type=marker,tag=gojo.pr]
tag @a remove gojo.inf
execute as @e[tag=gojo.halted] run function gojo:inf/release
kill @e[type=marker,tag=gojo.kai]
kill @e[type=block_display,tag=gojo.mpart]
tp @e[type=iron_golem,tag=gojo.maho] ~ -400 ~
kill @e[type=iron_golem,tag=gojo.maho]
kill @e[type=marker,tag=gojo.ms]
kill @e[type=block_display,tag=gojo.shrine]
kill @e[type=marker,tag=gojo.fuga]
kill @e[type=marker,tag=gojo.flames]
kill @e[type=marker,tag=gojo.blue]
kill @e[type=marker,tag=gojo.red]
kill @e[type=marker,tag=gojo.orb]
kill @e[type=marker,tag=gojo.center]
kill @e[type=marker,tag=gojo.purple]
kill @e[type=marker,tag=gojo.domain]
kill @e[type=block_display,tag=gojo.wall]
execute as @e[tag=gojo.trapped] run function gojo:domain/release
scoreboard objectives remove gojo.cd
scoreboard objectives remove gojo.life
scoreboard objectives remove gojo.id
scoreboard objectives remove gojo.tmp
scoreboard objectives remove gojo.cfg
scoreboard objectives remove gojo.chant
scoreboard objectives remove gojo.ct
scoreboard objectives remove gojo.hold
scoreboard objectives remove gojo.dcd
scoreboard objectives remove gojo.fuga
scoreboard objectives remove gojo.mcd
scoreboard objectives remove gojo.pos
scoreboard objectives remove gojo.adapt
scoreboard objectives remove gojo.acd
scoreboard objectives remove gojo.px
scoreboard objectives remove gojo.pz
scoreboard objectives remove gojo.af
scoreboard objectives remove gojo.atk
scoreboard objectives remove gojo.hcd
scoreboard objectives remove gojo.push
scoreboard objectives remove gojo.ca
scoreboard objectives remove gojo.cb
scoreboard objectives remove gojo.cv
kill @e[type=marker,tag=gojo.clash]
kill @e[type=marker,tag=gojo.met]
kill @e[type=marker,tag=gojo.jg]
kill @e[type=block_display,tag=gojo.gwall]
tag @e remove gojo.gtrap
scoreboard objectives remove gojo.gcd
execute as @a[tag=gojo.hr] run function gojo:hr/strip
scoreboard objectives remove gojo.hrlock
scoreboard objectives remove gojo.pcd
scoreboard objectives remove gojo.hgcd
scoreboard objectives remove gojo.wcd
scoreboard objectives remove gojo.kcd
scoreboard objectives remove gojo.tcd
scoreboard objectives remove gojo.hkcd
scoreboard objectives remove gojo.cscd
scoreboard objectives remove gojo.ce
scoreboard objectives remove gojo.cemax
scoreboard objectives remove gojo.finger
scoreboard objectives remove gojo.r1
scoreboard objectives remove gojo.r2
scoreboard objectives remove gojo.r3
scoreboard objectives remove gojo.jpt
scoreboard objectives remove gojo.hp
scoreboard objectives remove gojo.dfn
scoreboard objectives remove gojo.cft
scoreboard objectives remove gojo.ext
scoreboard objectives remove gojo.plead
scoreboard objectives remove gojo.kills
scoreboard objectives remove gojo.prt
scoreboard objectives remove gojo.prown
kill @e[type=block_display,tag=gojo.mpt]
scoreboard objectives remove gojo.ucd
kill @e[type=marker,tag=gojo.jp]
kill @e[type=block_display,tag=gojo.jwall]
tag @e remove gojo.jtrap
scoreboard objectives remove gojo.jcd
bossbar remove gojo:clash
scoreboard objectives remove gojo.dt
execute as @e[tag=gojo.deformed] run attribute @s minecraft:scale modifier remove gojo:idle
tp @e[tag=gojo.tf] ~ -500 ~
kill @e[tag=gojo.tf]
team remove gojo.mahito
tellraw @s {"text":"고죠 술식 데이터팩 제거 완료. /datapack disable 하세요.","color":"yellow"}
