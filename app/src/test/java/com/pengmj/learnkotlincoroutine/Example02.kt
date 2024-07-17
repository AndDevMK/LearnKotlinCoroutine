package com.pengmj.learnkotlincoroutine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author MinKin
 * @date 2024/7/16
 * @desc
 */
class Example02 {

    /**
     * 1、CoroutineContext是一组用于定义协程行为的元素。它由如下几项构成：
     *
     * - Job：控制协程的生命周期
     *
     * - CoroutineDispatcher：向合适的线程分发任务
     *
     * - CoroutineName：协程的名称，调试的时候很有用
     *
     * - CoroutineExceptionHandler：处理未被捕捉的异常
     *
     * 2、需要在协程上下文中定义多个元素。可以使用＋操作符来实现。比如说，可以显式指定一个调度器来启动协程并且同时显式指定一个命名：
     *
     * Dispatchers.Default + CoroutineName("MinKin")
     *
     * 3、协程上下文继承
     *
     * 对于新创建的协程，它的CoroutineContext是 一个全新的Job实例（例如：StandaloneCoroutine、DeferredCoroutine等），它会帮助我们控制协程的生命周期。
     *
     * 而该全新的Job实例会包装父类的CoroutineContext，该父类可能是另外一个协程或者创建该协程的CoroutineScope。
     *
     * 4、总结
     *
     * 协程的上下文 = 默认值 + 继承的CoroutineContext + 参数
     *
     * - 一些元素包含默认值：Dispatchers.Default是默认的CoroutineDispatcher，以及“coroutine"作为默认的CoroutineName；
     * - 继承的CoroutineContext是CoroutineScope或者其父协程的CoroutineContext ；
     * - 传入协程构建器的参数的优先级高于继承的上下文参数，因此会覆盖对应的参数值。
     */
    @Test
    fun `协程上下文`() = runBlocking {
        launch(context = Dispatchers.Default + CoroutineName("MinKin")) {
            println("thread: ${Thread.currentThread().name}")  // thread: DefaultDispatcher-worker-1 @MinKin#2
        }

        val coroutineScope = CoroutineScope(Job() + Dispatchers.IO + CoroutineName("Jerry"))
        val job = coroutineScope.launch {
            // 协程上下文继承
            // launch: "Jerry#3":StandaloneCoroutine{Active}@2f2e5c7d, thread: DefaultDispatcher-worker-2 @Jerry#3
            println("launch: ${coroutineContext[Job]}, thread: ${Thread.currentThread().name}")
            val result = async {
                // async: "Jerry#4":DeferredCoroutine{Active}@2b10f38f, thread: DefaultDispatcher-worker-1 @Jerry#4
                println("async: ${coroutineContext[Job]}, thread: ${Thread.currentThread().name}")
                123
            }
            println("result: ${result.await()}")
        }
        job.join()
    }

    /**
     *
     * 1、协程构建器有两种形式：自动传播异常（launch与actor），向用户暴露异常（async与produce）
     *
     * - 对于根协程（该协程不是另一个协程的子协程）：
     *
     * 前者这类构建器，异常会在它发生的第一时间被抛出，而后者则依赖用户来最终消费异常，例如通过await或receive
     *
     * - 对于非根协程：
     *
     * 产生的异常总是会被传播
     *
     * 2、异常的传播特性
     *
     * 当一个协程由于一个异常而运行失败时，它会传播这个异常并传递给它的父级。接下来父级会进行下面几步操作：
     *
     * - 取消它自己的子级
     *
     * - 取消它自己
     *
     * - 将异常传播并传递给它的父级
     *
     */
    @Test
    fun `协程异常的传播`() = runBlocking {
        // 对于根协程（该协程不是另一个协程的子协程）
        val job1 = GlobalScope.launch {
            try {
                // 异常会在它发生的第一时间被抛出
                throw IllegalStateException()
            }
            catch (e: Exception) {
                println("IllegalStateException")
            }
        }
        job1.join()

        val deferred = GlobalScope.async {
            throw IndexOutOfBoundsException()
        }
        // 依赖用户来最终消费异常，此处需要调用.await()才会抛出异常
        try {
            deferred.await()
        }
        catch (e: Exception) {
            println("IndexOutOfBoundsException")
        }

        // 对于非根协程
        val coroutineScope = CoroutineScope(EmptyCoroutineContext)
        val job2 = coroutineScope.launch {
            // 如果async抛出异常，launch就会立即抛出异常，而不会调用.await()
            val deferred1 = async {
                throw ClassNotFoundException()
            }
        }
        job2.join()
    }

    /**
     * 使用SupervisorJob时，一个子协程的运行失败不会影响到其他子协程。SupervisorJob不会传播异常给它的父级，它会让子协程自己处理异常。
     *
     * 对于supervisorScope，当作业自身执行失败的时候，所有子作业将会被全部取消。
     */
    @Test
    fun `一个协程失败了，不会影响其它兄弟协程`() = runBlocking {
        // 方式一：SupervisorJob
        val supervisorScope1 = CoroutineScope(SupervisorJob())
        val job1 = supervisorScope1.launch {
            delay(1000)
            println("方式一：=====job1 end")
            // 一个子协程的运行失败不会影响到其他子协程
            throw IndexOutOfBoundsException()
        }
        val job2 = supervisorScope1.launch {
            try {
                delay(Long.MAX_VALUE)
            }
            finally {
                println("方式一：=====job2 end")
            }
        }

        delay(2000)
        supervisorScope1.cancel(CancellationException("方式一：=====取消所有协程"))
        joinAll(job1, job2)

        // 方式二：supervisorScope
        val supervisorScope2 = supervisorScope {
            launch {
                delay(1000)
                println("方式二：=====job1 end")
                // 一个子协程的运行失败不会影响到其他子协程
                throw IndexOutOfBoundsException()
            }
            try {
                delay(3000)
            }
            finally {
                println("方式二：=====job2 end")
            }
        }

        try {
            supervisorScope {
                launch {
                    try {
                        delay(Long.MAX_VALUE)
                    }
                    finally {
                        println("=====job1 end")
                    }
                }
                yield()
                println("=====主协程结束")
                // 当作业自身执行失败的时候，所有子作业将会被全部取消。
                throw ClassNotFoundException()
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 使用CoroutineExceptionHandler对协程的异常进行捕获。
     *
     * 以下的条件被满足时，异常就会被捕获：
     *
     * • 时机：异常是被自动抛出异常的协程所抛出的（使用 launch，而不是 async 时）；
     *
     * • 位置：在CoroutineScope的CoroutineContext中或在一个根协程（CoroutineScope 或者 supervisorScope 的直接子协程）中。
     *
     */
    @Test
    fun `异常捕获的时机`() = runBlocking {
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            println("throwable: $throwable")
        }

        // 异常是被自动抛出异常的协程所抛出的（使用 launch，而不是 async 时）
        // CoroutineExceptionHandler在CoroutineScope的CoroutineContext中或在一个根协程（CoroutineScope 或者 supervisorScope 的直接子协程）中
        val job1 = GlobalScope.launch(coroutineExceptionHandler) {
            throw AssertionError()
        }

        val deferred = GlobalScope.async(coroutineExceptionHandler) {
            throw IndexOutOfBoundsException()
        }

        job1.join()
        try {
            deferred.await()
        }
        catch (e: Exception) {

        }

        val coroutineScope2 = CoroutineScope(Job())
        // 异常是被自动抛出异常的协程所抛出的（使用 launch，而不是 async 时）
        // CoroutineExceptionHandler在CoroutineScope的CoroutineContext中或在一个根协程（CoroutineScope 或者 supervisorScope 的直接子协程）中
        val job2 = coroutineScope2.launch(coroutineExceptionHandler) {
            launch {
                throw ClassNotFoundException()
            }
        }
        job2.join()


        val coroutineScope3 = CoroutineScope(Job())
        val job3 = coroutineScope3.launch {
            // 虽然异常是被自动抛出异常的协程所抛出的（使用 launch，而不是 async 时）
            // 但是，CoroutineExceptionHandler不在CoroutineScope的CoroutineContext中或在一个根协程（CoroutineScope 或者 supervisorScope 的直接子协程）中，所以无法捕获异常
            launch(coroutineExceptionHandler) {
                throw IllegalStateException()
            }
        }
        job3.join()
    }

    /**
     * 1、取消与异常
     *
     * 取消与异常紧密相关，协程内部使用CancellationException来进行取消，这个异常会被忽略。
     *
     * 当子协程被取消时，不会取消它的父协程。
     *
     * 如果一个协程遇到了CancellationException以外的异常，它将使用该异常取消它的父协程。当父协程的所有子协程都结束后，异常才会被父协程处理。
     *
     * 2、异常聚合
     *
     * 当协程的多个子协程因为异常而失败时，一般情况下取第一个异常进行处理。在第一个异常之后发生的所有其他异常，都将被绑定到第一个异常之上。
     *
     */
    @Test
    fun `取消与异常`() = runBlocking {
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            println("first throwable: $throwable, other throwable: ${throwable.suppressed.contentToString()}")
        }
        val job = GlobalScope.launch(coroutineExceptionHandler) {
            launch {
                try {
                    delay(Long.MAX_VALUE)
                }
                finally {
                    withContext(NonCancellable) {
                        delay(1000)
                        println("first job finished")
                        throw ClassNotFoundException()
                    }
                }
            }
            launch {
                try {
                    delay(Long.MAX_VALUE)
                }
                finally {
                    println("second job finished")
                    throw IndexOutOfBoundsException()
                }
            }
            launch {
                delay(1000)
                println("third job finished")
                throw IllegalStateException()
            }
        }
        job.join()
    }
}