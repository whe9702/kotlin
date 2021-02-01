/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.config

import java.util.*

enum class DceMode {
    REMOVAL_DECLARATION,
    LOGGING,
    THROWING_EXCEPTION;

    companion object {
        val DEFAULT = REMOVAL_DECLARATION

        fun resolvePolicy(key: String): DceMode {
            return when (key.toLowerCase(Locale.US)) {
                "removal-declaration" -> REMOVAL_DECLARATION
                "logging" -> LOGGING
                "throwing-exception" -> THROWING_EXCEPTION
                else -> error("Unknown DCE mode '$key'")
            }
        }
    }
}
