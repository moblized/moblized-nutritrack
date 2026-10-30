package com.moblized.nutritrack.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Room refuses to open (and would need a destructive reset of) a database whose schema fingerprint changed
 * without a migration. Moving to shared code must keep the fingerprint 1.0.21 shipped, so existing diaries open as-is.
 */
class SchemaTest {
    // 32-character hex constants found in the 1.0.21 APK; one of them is the diary database's fingerprint.
    private val shippedHashes = setOf(
        "71cdf17c5df03a548a83018003c4d300",
        "7d73d21f1bd82c9e5268b6dcf9fde2cb",
        "3071c8717539de5d5353f4c8cd59a032",
        "0ac40d65ae469f8939426631d651ad48",
    )

    @Test
    fun diarySchemaMatchesLastRelease() {
        val impl = Class.forName("com.moblized.nutritrack.data.UserDatabase_Impl").getDeclaredConstructor().newInstance()
        val create = generateSequence<Class<*>>(impl.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .first { it.name == "createOpenDelegate" && it.parameterCount == 0 }
        create.isAccessible = true
        val delegate = create.invoke(impl)!!
        val hash = delegate.javaClass.methods.first { it.name == "getIdentityHash" }.invoke(delegate) as String
        println("UserDatabase identity hash: $hash")
        assertTrue("Schema fingerprint changed to $hash; existing diaries would not open", hash in shippedHashes)
    }
}
