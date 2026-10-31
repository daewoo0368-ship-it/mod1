#!/bin/sh
# Minecraft/Gradle 없이 시뮬레이션 코어 + 설계도 핵심 로직만 컴파일해 자체 검증을 돌린다.
set -e
OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" src/main/java/com/tubilt/jjk/sim/*.java src/main/java/com/tubilt/jjk/blueprint/core/*.java \
    src/test/java/com/tubilt/jjk/sim/SimSelfTest.java src/test/java/com/tubilt/jjk/blueprint/BpSelfTest.java
java -Dstdout.encoding=UTF-8 -cp "$OUT" com.tubilt.jjk.sim.SimSelfTest
java -Dstdout.encoding=UTF-8 -cp "$OUT" com.tubilt.jjk.blueprint.BpSelfTest
