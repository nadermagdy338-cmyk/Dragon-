/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
// [FIX] Menggunakan blok buildscript agar plugin StringFog punya akses ke library XOR
// [CRITICAL] Blok buildscript harus jadi blok pertama
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
