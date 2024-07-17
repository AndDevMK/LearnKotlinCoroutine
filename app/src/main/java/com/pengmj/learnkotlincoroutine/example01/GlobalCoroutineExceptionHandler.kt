package com.pengmj.learnkotlincoroutine.example01

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * @author MinKin
 * @date 2024/7/17
 * @desc
 */

class GlobalCoroutineExceptionHandler(
    override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler,
) : CoroutineExceptionHandler {
    override fun handleException(
        context: CoroutineContext,
        exception: Throwable
    ) {
        // 此处可记录协程异常日志
        println("=======throwable=======: $exception")
    }
}