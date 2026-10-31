particle minecraft:squid_ink ~ ~0.2 ~ 1.5 0.1 1.5 0.05 100 force
particle minecraft:large_smoke ~ ~2 ~ 1 2 1 0.02 40 force
playsound minecraft:entity.warden.dig master @a ~ ~ ~ 2 0.8
execute on passengers run kill @s
tp @s ~ -400 ~
kill @s
