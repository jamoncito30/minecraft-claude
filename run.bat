@echo off
title Minecraft Classic 0.0.14a
cd /d "%~dp0"
set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED
call ".mvn\apache-maven-3.9.9\bin\mvn.cmd" compile exec:java -Dexec.mainClass="com.mojang.minecraft.Minecraft"
pause
