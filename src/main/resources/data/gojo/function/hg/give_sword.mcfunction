give @s jjk:exec_sword[minecraft:custom_data={gojo_exec:1b},minecraft:item_model="jjk:exec_sword",minecraft:item_name={"text":"사형 집행인의 검","color":"gold","italic":false},minecraft:enchantment_glint_override=true,minecraft:lore=[{"text":"벤 대상은 반드시 죽는다 (60초)","color":"gray","italic":false},{"text":"지니고 있는 동안 주복사사 봉인","color":"dark_red","italic":false}]] 1
tag @s add gojo.exec
scoreboard players set @s gojo.ext 1200
playsound minecraft:item.armor.equip_gold master @s ~ ~ ~ 1 0.6
