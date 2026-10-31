#!/bin/sh
# Minecraft/Gradle 없이 시뮬레이션 코어만 컴파일해 자체 검증을 돌린다.
set -e
OUT=$(mktemp -d)
javac -d "$OUT" src/main/java/com/tubilt/jjk/sim/*.java src/test/java/com/tubilt/jjk/sim/SimSelfTest.java
java -Dstdout.encoding=UTF-8 -cp "$OUT" com.tubilt.jjk.sim.SimSelfTest
