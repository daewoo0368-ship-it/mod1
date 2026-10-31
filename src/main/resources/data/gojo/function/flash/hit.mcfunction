title @a[tag=gojo.atk] actionbar {"text":"흑섬(黑閃)!","color":"dark_red","bold":true}
execute if score #bf_sneak gojo.tmp matches 0 run function gojo:flash/normal
execute if score #bf_sneak gojo.tmp matches 1 run function gojo:flash/strong
