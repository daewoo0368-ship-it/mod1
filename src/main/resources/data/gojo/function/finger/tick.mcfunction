# 손가락 수에 따라 지속 강화 (2초마다 갱신, 효과 아이콘만)
effect give @s[scores={gojo.finger=1..4}] minecraft:strength 3 0 true
effect give @s[scores={gojo.finger=5..9}] minecraft:strength 3 1 true
effect give @s[scores={gojo.finger=10..14}] minecraft:strength 3 2 true
effect give @s[scores={gojo.finger=15..19}] minecraft:strength 3 3 true
effect give @s[scores={gojo.finger=20}] minecraft:strength 3 4 true
effect give @s[scores={gojo.finger=7..13}] minecraft:resistance 3 0 true
effect give @s[scores={gojo.finger=14..}] minecraft:resistance 3 1 true
effect give @s[scores={gojo.finger=10..}] minecraft:speed 3 0 true
effect give @s[scores={gojo.finger=15..}] minecraft:regeneration 3 0 true
effect give @s[scores={gojo.finger=20}] minecraft:fire_resistance 3 0 true
# 완전 부활: 은은한 붉은 기운
execute if entity @s[tag=gojo.sukuna] run particle minecraft:dust{color:[0.5,0.0,0.05],scale:1.0} ~ ~1 ~ 0.3 0.6 0.3 0 3
