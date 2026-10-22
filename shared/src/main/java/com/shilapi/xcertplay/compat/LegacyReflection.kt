package com.shilapi.xcertplay.compat

import java.lang.reflect.InvocationTargetException

/**
 * True for the checked failures reflection raises on API 17.
 *
 * `ReflectiveOperationException` is API 19. Naming it in a `catch` clause makes Dalvik reject the
 * whole class with `VerifyError` on Android 4.2 and 4.3, even when the handler can never run,
 * because the verifier resolves every handler type while linking the class. That killed
 * `CarPlayController` before it was constructed.
 *
 * Every subclass exists since API 1, so this predicate keeps the same set without the missing
 * type. Use it in `is` checks, and rethrow from a broad `catch (error: Exception)` when the
 * original only caught reflection failures.
 */
internal fun Throwable.isReflectiveFailure(): Boolean =
    this is InvocationTargetException ||
        this is IllegalAccessException ||
        this is NoSuchMethodException ||
        this is NoSuchFieldException ||
        this is InstantiationException ||
        this is ClassNotFoundException
