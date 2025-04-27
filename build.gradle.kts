/*
 * MIT License
 *
 * Copyright (c) 2025 VidTu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * SPDX-License-Identifier: MIT
 */

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.ZipUtils.UnsafeUnaryOperator

plugins {
    alias(libs.plugins.architectury.loom)
}

java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21
java.toolchain.languageVersion = JavaLanguageVersion.of(21)

group = "ru.vidtu.gyro"
base.archivesName = "gyro"
description = "Abuses the newly introduced (1.21.6) Minecraft waypoint system to get player positions."

loom {
    accessWidenerPath = file("src/main/resources/gyro.accesswidener")
    log4jConfigs.setFrom("log4j2.xml")
    silentMojangMappingsLicense()
    runs.named("client") {
        vmArgs(
            // Allow JVM without hotswap to work.
            "-XX:+IgnoreUnrecognizedVMOptions",

            // Set up RAM.
            "-Xmx2G",

            // Force UNIX newlines.
            "-Dline.separator=\n",

            // Debug arguments.
            "-ea",
            "-esa",
            "-Dmixin.debug=true",
            "-Dmixin.debug.strict.unique=true",
            "-Dmixin.checks=true",
            "-Dio.netty.tryReflectionSetAccessible=true",
            "-Dio.netty.leakDetection.level=PARANOID",

            // Allow hot swapping on supported JVM.
            "-XX:+AllowEnhancedClassRedefinition",
            "-XX:+AllowRedefinitionToAddDeleteMethods",
            "-XX:HotswapAgent=fatjar",
            "-Dfabric.debug.disableClassPathIsolation=true",

            // Open modules for Netty.
            "--add-opens",
            "java.base/java.nio=ALL-UNNAMED",
            "--add-opens",
            "java.base/jdk.internal.misc=ALL-UNNAMED"
        )
    }
    @Suppress("UnstableApiUsage") // <- I want the fancy refmap name. It's completely optional and can be removed anytime.
    mixin {
        defaultRefmapName = "gyro.mixins.refmap.json"
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") // Fabric.
}

dependencies {
    // Annotations
    compileOnly(libs.jspecify)
    compileOnly(libs.jetbrains.annotations)

    // Minecraft
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())

    // Fabric
    modImplementation(libs.fabric.loader)
    modRuntimeOnly(fabricApi.module("fabric-resource-loader-v0", libs.fabric.api.get().version))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-g", "-parameters"))
    options.release = 21
}

tasks.withType<ProcessResources> {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand(inputs.properties)
    }
    val files = fileTree(outputs.files.asPath)
    doLast {
        files.forEach {
            if (it.name.endsWith(".json", ignoreCase = true)) {
                it.writeText(Gson().fromJson(it.readText(), JsonElement::class.java).toString())
            }
        }
    }
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Jar> {
    from(rootDir.resolve("LICENSE"))
    manifest {
        attributes(
            "Specification-Title" to "gyro",
            "Specification-Version" to version,
            "Specification-Vendor" to "VidTu",
            "Implementation-Title" to "gyro",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "VidTu"
        )
    }
}

tasks.withType<RemapJarTask> {
    val minifier = UnsafeUnaryOperator<String> { Gson().fromJson(it, JsonElement::class.java).toString() }
    doLast {
        ZipUtils.transformString(archiveFile.get().asFile.toPath(), mapOf(
            "gyro.mixins.json" to minifier,
            "gyro.mixins.refmap.json" to minifier,
        ))
    }
}
