scoreboard players add @a gojo.chant 0
scoreboard players add @a gojo.cd 0
scoreboard players add @a gojo.fuga 0
execute as @a unless score @s gojo.id matches 1.. store result score @s gojo.id run scoreboard players add #next gojo.id 1
execute as @a[scores={gojo.chant=1..}] at @s run function gojo:chant/tick
scoreboard players remove @a[scores={gojo.cd=1..,gojo.chant=0}] gojo.cd 1
scoreboard players remove @a[scores={gojo.hold=1..}] gojo.hold 1
scoreboard players remove @a[scores={gojo.dcd=1..}] gojo.dcd 1
scoreboard players remove @a[scores={gojo.hcd=1..}] gojo.hcd 1
scoreboard players remove @a[scores={gojo.push=1..}] gojo.push 1
scoreboard players remove @a[scores={gojo.jcd=1..}] gojo.jcd 1
scoreboard players remove @a[scores={gojo.ucd=1..}] gojo.ucd 1
scoreboard players remove @a[scores={gojo.gcd=1..}] gojo.gcd 1
scoreboard players remove @a[scores={gojo.hrlock=1..}] gojo.hrlock 1
scoreboard players remove @a[scores={gojo.pcd=1..}] gojo.pcd 1
scoreboard players remove @a[scores={gojo.hgcd=1..}] gojo.hgcd 1
execute as @a if items entity @s container.* *[minecraft:custom_data~{gojo_exec:1b}] run scoreboard players set @s gojo.hgcd 300
execute as @a if items entity @s weapon.offhand *[minecraft:custom_data~{gojo_exec:1b}] run scoreboard players set @s gojo.hgcd 300
scoreboard players remove @a[scores={gojo.wcd=1..}] gojo.wcd 1
scoreboard players remove @a[scores={gojo.kcd=1..}] gojo.kcd 1
scoreboard players remove @a[scores={gojo.tcd=1..}] gojo.tcd 1
scoreboard players remove @a[scores={gojo.hkcd=1..}] gojo.hkcd 1
scoreboard players remove @a[scores={gojo.cscd=1..}] gojo.cscd 1
function gojo:ce/tick
execute as @e[type=marker,tag=gojo.cs] at @s run function gojo:cs/tick
execute as @e[type=marker,tag=gojo.cs2] at @s run function gojo:cs2/tick
execute as @e[type=marker,tag=gojo.cs2a] at @s run function gojo:cs2/arrow
execute as @e[type=marker,tag=gojo.csb] at @s run function gojo:cs/beam
execute as @e[type=marker,tag=gojo.hk] at @s run function gojo:hk/tick
execute as @a[tag=gojo.jackpot] at @s run function gojo:hk/jackpot_tick
execute as @e[type=block_display,tag=gojo.wb] at @s run function gojo:wb/tick
execute as @e[type=marker,tag=gojo.kf] at @s run function gojo:kf/tick
execute as @e[type=marker,tag=gojo.tk] at @s run function gojo:tk/tick
scoreboard players remove @a[scores={gojo.cft=1..}] gojo.cft 1
execute as @a[tag=gojo.confisc,scores={gojo.cft=..0}] run function gojo:hg/confisc_end
scoreboard players remove @a[scores={gojo.ext=1..}] gojo.ext 1
execute as @a[tag=gojo.exec,scores={gojo.ext=..0}] run function gojo:hg/sword_end
execute as @e[type=marker,tag=gojo.hg] at @s run function gojo:hg/tick
execute as @e[type=marker,tag=gojo.pr] at @s run function gojo:prison/tick
execute as @a[tag=gojo.hr] at @s run function gojo:hr/tick
execute as @e[type=marker,tag=gojo.jg] at @s run function gojo:jg/tick
execute as @e[type=marker,tag=gojo.met] at @s run function gojo:meteor/tick
execute as @e[type=marker,tag=gojo.clash] at @s run function gojo:clash/tick
execute as @e[tag=gojo.deformed] run function gojo:mahito/deform_tick
execute as @e[type=#gojo:transfig,tag=gojo.tf] at @s run function gojo:mahito/tf_tick
execute as @e[type=trident] if data entity @s item.components."minecraft:custom_data".gojo_ten at @s as @a[distance=..2] run function gojo:ten/nullify
execute as @a[tag=gojo.inf] at @s run function gojo:inf/tick
execute as @e[tag=gojo.halted] at @s unless entity @a[tag=gojo.inf,distance=..5.5] run function gojo:inf/release
execute as @e[type=marker,tag=gojo.kai] at @s run function gojo:kai/tick
execute as @a[scores={gojo.fuga=1..}] at @s run function gojo:fuga/charge
execute as @e[type=marker,tag=gojo.fuga] at @s run function gojo:fuga/tick
execute as @e[type=marker,tag=gojo.flames] at @s run function gojo:fuga/flames_tick
scoreboard players remove @a[scores={gojo.mcd=1..}] gojo.mcd 1
execute as @e[type=marker,tag=gojo.ms] at @s run function gojo:ms/tick
execute as @e[type=iron_golem,tag=gojo.maho,tag=!gojo.csm] at @s run function gojo:maho/tick
execute as @e[type=block_display,tag=gojo.mpart] at @s unless entity @e[type=iron_golem,tag=gojo.maho,distance=..8] run kill @s
execute as @e[type=marker,tag=gojo.jp] at @s run function gojo:jp/tick
execute as @e[type=marker,tag=gojo.domain] at @s run function gojo:domain/tick
execute as @e[type=marker,tag=gojo.blue] at @s run function gojo:blue/tick
execute as @e[type=marker,tag=gojo.red] at @s run function gojo:red/tick
execute as @e[type=marker,tag=gojo.orb] at @s run function gojo:purple/orb_tick
execute as @e[type=marker,tag=gojo.center] at @s run function gojo:purple/center_tick
execute as @e[type=marker,tag=gojo.purple] at @s run function gojo:purple/tick

execute store result score #fg gojo.tmp run time query gametime
scoreboard players operation #fg gojo.tmp %= #40 gojo.cfg
execute if score #fg gojo.tmp matches 0 as @a[scores={gojo.finger=1..}] at @s run function gojo:finger/tick
