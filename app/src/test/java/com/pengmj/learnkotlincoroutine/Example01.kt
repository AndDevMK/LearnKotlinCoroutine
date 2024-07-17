package com.pengmj.learnkotlincoroutine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.measureTimeMillis

/**
 * @author MinKin
 * @date 2024/7/15
 * @desc
 */
class Example01 {

    /**
     * 1、launch返回一个Job对象；async返回一个Deferred对象，它是Job的子类。通过Deferred对象的await方法可以获取async的返回值
     *
     * 2、Job的join方法会挂起协程，直到此作业完成。Deferred的await方法与此相似
     *
     * 看到runBlocking，想拓展一下协程作用域知识：
     *
     * coroutineScope（拥有父协程上下文）与runBlocking区别：
     * - runBlocking是常规函数，而coroutineScope是挂起函数。
     * - 它们都会等待其协程体以及所有子协程结束，主要区别在于runBlocking方法会阻塞当前线程来等待，而coroutineScope只是挂起，会释放底层线程用于其他用途。
     *
     * coroutineScope与supervisorScope（拥有父协程上下文）区别：
     * - 对于coroutineScope，一个协程失败了，所有其他兄弟协程也会被取消。
     * - 对于supervisorScope，一个协程失败了，不会影响其他兄弟协程。
     *
     * 看到Job，想拓展一下Job对象的生命周期知识：
     *
     * 一个任务可以包含一系列状态：新创建（New）、活跃（Active）、完成中（Completing）、已完成（Completed）、取消中（Cancelling）和已取消（Cancelled）。
     * 虽然我们无法直接访问这些状态，但是我们可以访问Job的属性：isActive、isCancelled和isCompleted
     *
     * <img width="640" height="320" src="/images/Job的生命周期.png" alt=""/>
     *
     */
    @Test
    fun `launch和async的返回值比较、等待协程Job完成`() = runBlocking {
        val job1 = launch {
            delay(3000)
            println("job1 finished")
        }

        val job2 = launch {
            delay(1000)
            println("job2 finished")
        }

        job1.join()
        job2.join()

        val deferred = async {
            delay(2000)
            println("deferred finished")
            123456
        }

        println("deferred返回值为：${deferred.await()}")
    }


    @Test
    fun `async并发`() = runBlocking {
        val time = measureTimeMillis {
            val deferred1 = async {
                delay(2000)
                200
            }
            val deferred2 = async {
                delay(3000)
                300
            }
            println("result: ${deferred1.await() + deferred2.await()}")
        }
        println("cost time: $time ms")
    }

    /**
     * DEFAULT：协程创建后，立即开始调度，在调度前如果协程被取消，其将直接进入取消响应的状态。
     *
     * ATOMIC：协程创建后，立即开始调度，协程执行到第一个挂起点之前不响应取消。
     *
     * LAZY：只有协程被需要时，包括主动调用协程的start、join或者await等函数时才会开始调度，如果调度前就被取消，那么该协程将直接进入异常结束状态。
     *
     * UNDISPATCHED：协程创建后立即在当前函数调用栈中执行，直到遇到第一个真正挂起的点。
     *
     * Tips：不能在Test中使用Dispatchers.Main
     *
     * 参考：https://blog.csdn.net/NAPO687/article/details/105497575
     */
    @Test
    fun `协程启动模式`() = runBlocking {
        /**
         * 设置了CoroutineStart.UNDISPATCHED，那么job会在当前函数调用栈中执行（和主协程在同一个线程中执行）。即使指定了子线程Dispatchers.IO
         *
         * 尝试将CoroutineStart.UNDISPATCHED修改为CoroutineStart.DEFAULT，那么job会在子线程中执行
         */
        val job = launch(
                context = Dispatchers.IO,
                start = CoroutineStart.UNDISPATCHED,
        ) {
            println("job start: ${Thread.currentThread().name}")
            delay(2000)
            println("job end")
        }
        println("主协程启动: ${Thread.currentThread().name}")
        delay(100)
        job.cancel()
        println("job cancel")
    }


    /**
     * runBlocking不会等待CoroutineScope完成，因为它们作用于不同作用域（注意和小写开头的coroutineScope{}作用域之间的区别），
     *
     * 可以简单通过delay延时等待CoroutineScope完成，也可以通过CoroutineScope的cancel方法可以取消它自己
     *
     * 总结：
     *
     * 1、取消作用域会取消它的所有子协程。
     *
     * 2、被取消的子协程并不会影响其余兄弟协程。
     *
     * 3、协程通过抛出一个特殊的异常 CancellationException 来处理取消操作。
     *
     * 4、所有kotlinx.coroutines中的挂起函数（withContext、delay等）都是可取消的
     */
    @Test
    fun `取消协程`() = runBlocking {
        val coroutineScope = CoroutineScope(EmptyCoroutineContext)
        val job1 = coroutineScope.launch {
            println("job1 start")
            try {
                delay(1000)
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
            println("job1 end")
        }
        // 等价于cancelAndJoin方法
        job1.cancel(CancellationException("取消job1"))
        job1.join()

        coroutineScope.launch {
            println("job2 start")
            delay(2000)
            println("job2 end")
        }

        println("主协程启动")
        delay(100)
        coroutineScope.cancel()

        println("主协程结束")
    }

    @Test
    fun `CPU密集型任务取消`() = runBlocking {
        // 方式一
        val job1 = launch(context = Dispatchers.Default) {
            var printTime = System.currentTimeMillis()
            var i = 0
            while (i <= 5 && isActive) {
                if (System.currentTimeMillis() >= printTime) {
                    println("print ${i++}")
                    printTime += 500
                }
            }
        }
        delay(1300)
        println("Job1取消开始")
        job1.cancelAndJoin()

        // 方式二
        val job2 = launch(context = Dispatchers.Default) {
            var printTime = System.currentTimeMillis()
            var i = 0
            while (i <= 5) {
                ensureActive()  // 原理也是isActive，只不过会抛出CancellationException异常，可以根据需求捕获
                if (System.currentTimeMillis() >= printTime) {
                    println("print ${i++}")
                    printTime += 500
                }
            }
        }
        delay(1300)
        println("Job2取消开始")
        job2.cancelAndJoin()

        // 方式三
        val job3 = launch(context = Dispatchers.Default) {
            var printTime = System.currentTimeMillis()
            var i = 0
            while (i <= 5) {
                yield()  // yield函数会检查所在协程的状态，如果已经取消，则抛出CancellationException予以响应。此外它还会尝试出让线程的执行权，给其他协程提供执行机会。
                if (System.currentTimeMillis() >= printTime) {
                    println("print ${i++}")
                    printTime += 500
                }
            }
        }
        delay(1300)
        println("job3取消开始")
        job3.cancelAndJoin()

        println("主协程结束")
    }

    /**
     * Use函数用于取消协程副作用
     */
    @Test
    fun `Use函数`() = runBlocking {
        // 方式一
        BufferedReader(
                FileReader("/Users/pengmingjian/Desktop/LearnKotlinCoroutine/app/src/test/java/com/pengmj/learnkotlincoroutine/files/test.txt")
        ).use { br ->
            var line: String?
            while (br.readLine().also { line = it } != null) {
                println(line)
            }
        }

        // 方式二：封装了bufferedReader和use函数，返回一个Sequence<String>
        File(
                "/Users/pengmingjian/Desktop/LearnKotlinCoroutine/app/src/test/java/com/pengmj/learnkotlincoroutine" + "/files/test.txt"
        ).useLines { sequence ->
            sequence.forEach {
                println(it)
            }
        }
    }

    @Test
    fun `不能取消的任务`() = runBlocking {
        val job = launch(context = Dispatchers.Default) {
            try {
                var printTime = System.currentTimeMillis()
                var i = 0
                while (i <= 5) {
                    ensureActive()
                    if (System.currentTimeMillis() >= printTime) {
                        println("print ${i++}")
                        printTime += 500
                    }
                }
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
            finally {
                withContext(NonCancellable) {
                    println("finally start")
                    delay(1000)  // 确保此处挂起任务能执行完毕，不被取消
                    println("finally end")
                }
            }
        }
        delay(1300)
        println("Job取消开始")
        job.cancelAndJoin()
    }

    /**
     * 任务超时后，withTimeout会抛出TimeoutCancellationException异常；withTimeoutOrNull会返回一个null
     */
    @Test
    fun `超时任务`() = runBlocking {
        try {
            withTimeout(1300) {
                repeat(10) {
                    println("print $it")
                    delay(500)
                }
            }
        }
        catch (e: Exception) {

        }

        val result = withTimeoutOrNull(1300) {
            repeat(10) {
                println("print $it")
                delay(500)
            }
        }
        println("result: $result")
    }
}