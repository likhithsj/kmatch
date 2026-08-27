#!/bin/bash
# Builds the SKIE frameworks and runs the Swift assertions (macOS only).
set -euo pipefail
cd "$(dirname "$0")"

echo "== Building SKIE frameworks (macosArm64 + iosSimulatorArm64) =="
# The sample reuses the repository root's Gradle wrapper.
../../gradlew -p . linkReleaseFrameworkMacosArm64 linkReleaseFrameworkIosSimulatorArm64 --stacktrace

echo "== Compiling and running Swift verification against macosArm64 =="
mkdir -p build/swift
swiftc -F build/bin/macosArm64/releaseFramework swift/Verify.swift -o build/swift/verify
./build/swift/verify
