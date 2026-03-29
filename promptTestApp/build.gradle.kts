import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// --- Prompt Data Code Generation Task ---
val generatePromptData by tasks.registering {
    val inputDir = rootProject.file("prompt-eval")
    val outputDir = layout.buildDirectory.dir("generated/promptData/com/example/prompttest/generated")
    inputs.files(inputDir.resolve("test_cases.json"), inputDir.resolve("prompts.json"))
    outputs.dir(layout.buildDirectory.dir("generated/promptData"))

    doLast {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        // Parse test_cases.json
        val testCasesJson = inputDir.resolve("test_cases.json").readText()
        val testCasesCode = buildString {
            appendLine("package com.example.prompttest.generated")
            appendLine()
            appendLine("data class TestCase(val id: String, val input: String, val tags: List<String>, val notes: String)")
            appendLine()
            appendLine("object GeneratedTestCases {")
            appendLine("    val cases = listOf(")

            // Simple JSON array parsing using regex (avoids external dependency)
            val objectPattern = Regex("""\{[^}]+\}""", RegexOption.DOT_MATCHES_ALL)
            val objects = objectPattern.findAll(testCasesJson).toList()
            for ((i, match) in objects.withIndex()) {
                val obj = match.value
                val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: ""
                val input = Regex(""""input"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: ""
                val notes = Regex(""""notes"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: ""
                val tagsMatch = Regex(""""tags"\s*:\s*\[([^\]]*)\]""").find(obj)?.groupValues?.get(1) ?: ""
                val tags = Regex(""""([^"]+)"""").findAll(tagsMatch).map { "\"${it.groupValues[1]}\"" }.joinToString(", ")

                val comma = if (i < objects.size - 1) "," else ""
                appendLine("        TestCase(id = \"$id\", input = \"$input\", tags = listOf($tags), notes = \"$notes\")$comma")
            }
            appendLine("    )")
            appendLine("}")
        }
        outDir.resolve("GeneratedTestCases.kt").writeText(testCasesCode)

        // Parse prompts.json
        val promptsJson = inputDir.resolve("prompts.json").readText()
        val promptsCode = buildString {
            appendLine("package com.example.prompttest.generated")
            appendLine()
            appendLine("data class PromptTemplate(val id: String, val name: String, val template: String)")
            appendLine()
            appendLine("object GeneratedPrompts {")
            appendLine("    val prompts = listOf(")

            // Parse each prompt object - templates can contain special chars so use triple-quote
            val promptObjects = mutableListOf<String>()
            var depth = 0
            var start = -1
            for ((idx, ch) in promptsJson.withIndex()) {
                if (ch == '{') { if (depth == 0) start = idx; depth++ }
                if (ch == '}') { depth--; if (depth == 0 && start >= 0) { promptObjects.add(promptsJson.substring(start, idx + 1)); start = -1 } }
            }

            for ((i, obj) in promptObjects.withIndex()) {
                val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: ""
                val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: ""
                // Extract template value - it's between "template": " and the closing "
                val templateStart = obj.indexOf("\"template\"")
                val colonIdx = obj.indexOf(':', templateStart)
                val firstQuote = obj.indexOf('"', colonIdx + 1)
                // Find the closing quote (not escaped)
                var endQuote = firstQuote + 1
                while (endQuote < obj.length) {
                    if (obj[endQuote] == '"' && obj[endQuote - 1] != '\\') break
                    endQuote++
                }
                val template = obj.substring(firstQuote + 1, endQuote)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")

                val comma = if (i < promptObjects.size - 1) "," else ""
                appendLine("        PromptTemplate(")
                appendLine("            id = \"$id\",")
                appendLine("            name = \"$name\",")
                appendLine("            template = \"\"\"")
                appendLine(template)
                appendLine("            \"\"\".trimIndent()")
                appendLine("        )$comma")
            }
            appendLine("    )")
            appendLine("}")
        }
        outDir.resolve("GeneratedPrompts.kt").writeText(promptsCode)

        println("Generated prompt data: ${outDir.absolutePath}")
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            outputModuleName = "promptTestApp"
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "promptTestApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generatePromptData.map { it.outputs.files.first() })
        }

        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.mlkit.genai.prompt)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.example.prompttest"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.prompttest"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.example.prompttest.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.example.prompttest"
            packageVersion = "1.0.0"
        }
    }
}
