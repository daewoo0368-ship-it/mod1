scoreboard players set @s gojo.fuga 0
scoreboard players operation #cast gojo.id = @s gojo.id
playsound minecraft:entity.arrow.shoot master @a ~ ~ ~ 2 0.5
playsound minecraft:entity.blaze.shoot master @a ~ ~ ~ 2 0.5
playsound minecraft:item.trident.thunder master @a ~ ~ ~ 1 2
execute anchored eyes positioned ^ ^ ^1.5 summon marker run function gojo:fuga/init
