#!/bin/sh
./gradlew release
./gradlew -p build-logic release
./gradlew -p gradle-plugin release
