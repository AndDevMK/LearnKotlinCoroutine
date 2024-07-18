package com.pengmj.learnkotlincoroutine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * @author MinKin
 * @date 2024/7/17
 * @desc
 */
class Example03 {

    /**
     * 1、冷流
     *
     * Flow是一种类似于序列的冷流，flow构建器中的代码直到流被收集的时候才运行。
     *
     * 2、流的连续性
     *
     * 流的每次单独收集都是按顺序执行的，除非使用特殊操作符。从上游到下游每个过渡操作符都会处理每个发射出的值，然后再交给末端操作符。
     */
    @Test
    fun `flow异步返回多个值`() = runBlocking {
        val simpleFlow = flow {
            for (i in 0 until 8) {
                delay(1000)
                emit(i)
            }
        }

        simpleFlow.map {
            "abcdefghijklmnopqrstuvwxyz".random()
        }.filter {
            it.code % 2 == 0
        }.collect {
            println("value: $it")
        }
    }

    @Test
    fun `流构建器`() = runBlocking {
        flowOf(1, 2, 3, 4, 5, 6).onEach {
            delay(1000)
        }.collect {
            println("value: $it")
        }

        (97..122).asFlow().map {
            it.toChar()
        }.onEach {
            delay(500)
        }.collect {
            println("value: $it")
        }
    }

    /**
     * 流的收集总是在调用协程的上下文中发生，流的该属性称为上下文保存。
     *
     * 流构建器中的代码必须遵循上下文保存属性，并且不允许从其他上下文中发射，例如：不能采用withContext切换上下文
     */
    @Test
    fun `流的上下文`() = runBlocking {
        flowOf(1, 2, 3).onEach {
            println("flowOf thread: ${Thread.currentThread().name}")
            delay(1000)
        }.flowOn(Dispatchers.Default).collect {
            println("value: $it, thread: ${Thread.currentThread().name}")
        }
    }

    @Test
    fun `在指定协程中收集流`() = runBlocking {
        val job = flowOf(1, 2, 3, 4, 5).onEach {
            delay(1000)
            println("value: $it")
        }.launchIn(this) // launchIn内部调用collect

        delay(3000)
        println("取消收集")
        job.cancelAndJoin()
    }

    /**
     * 流构建器对每个发射值执行附加的 ensureActive 检测以进行取消
     *
     */
    @Test
    fun `流的取消`() = runBlocking {
        withTimeoutOrNull(3000) {
            flowOf(1, 2, 3, 4, 5).onEach {
                delay(1000)
            }.collect {
                println("value: $it")
            }
        }

        (97..122).asFlow().onEach {
            delay(1000)
        }.map {
            it.toChar()
            // 出于性能原因，大多数其他流操作不会自行执行其他取消检测，在协程处于繁忙循环的情况下，必须明确检测是否取消。
            // cancellable可以明确一定要检查是否取消
        }.cancellable().collect {
            if (it.code > 105) cancel(CancellationException("取消流"))   // 抛出异常
            println("value: $it")
        }
    }

    @Test
    fun `背压`() = runBlocking {
        val time = measureTimeMillis {
            flowOf(1, 2, 3).onEach {
                delay(1000)
                println("emit $it")
            }
                    // .flowOn(Dispatchers.Default)   // 此处设置协程上下文，具有和buffer一样的背压功能，Why？当必须更改CoroutineDispatcher时，flowOn操作符使用了相同的缓冲机制，但是buffer函数显式地请求缓冲而不改变执行上下文。
                    .buffer(10).collect {
                        delay(3000)
                        println("collect $it")
                    }
        }
        println("cost time: $time")
    }

    @Test
    fun `合并与处理最新值`() = runBlocking {
        val time1 = measureTimeMillis {
            flowOf(1, 2, 3).onEach {
                delay(1000)
                println("conflate使用====emit $it")

            }
                    // 合并发射项，不对每个值进行处理（忽略中间一些值）
                    .conflate().collect {
                        delay(3000)
                        println("conflate使用====collect $it")
                    }
        }
        println("conflate使用====cost time: $time1")


        val time2 = measureTimeMillis {
            flowOf(1, 2, 3).onEach {
                delay(1000)
                println("collectLatest使用====emit $it")

                // 取消并重新发射最后一个值
            }.collectLatest {
                delay(3000)
                println("collectLatest使用====collect $it")
            }
        }
        println("collectLatest使用====cost time: $time2")
    }

    @Test
    fun `转换操作符`() = runBlocking {
        flowOf(1, 2, 3).transform {
            emit("emit: $it")
        }.collect {
            println(it)
        }
    }

    @Test
    fun `限长操作符`() = runBlocking {
        flowOf(1, 2, 3).take(2).collect {
            println("value: $it")
        }
    }

    @Test
    fun `末端操作符`() = runBlocking {
        val sum = flowOf(1, 2, 3).reduce { accumulator, value -> accumulator + value }
        println("sum: $sum")

        val first = flowOf(1, 2, 3).first()
        println("first: $first")

        // ...
    }

    @Test
    fun `组合操作符`() = runBlocking {
        val flowOne = flowOf("A", "B", "C").onEach { delay(100) }
        val flowTwo = flowOf(1, 2, 3, 4).onEach { delay(400) }
        val startTime = System.currentTimeMillis()
        val time = measureTimeMillis {
            flowOne.zip(flowTwo) { t1, t2 ->
                "$t1$t2"
            }.collect {
                println("value: $it at ${System.currentTimeMillis() - startTime} ms")
            }
        }
        println("time: $time")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `展平操作符`() = runBlocking {
        val startTime = System.currentTimeMillis()
        flowOf(1, 2, 3).onEach {
            delay(100)
        }
                // 连接模式
                // .flatMapConcat {

                // 合并模式
                // .flatMapMerge {

                // 最新模式
                .flatMapLatest {
                    flow {
                        emit("$it first")
                        delay(200)
                        emit("$it second")
                    }
                }.collect {
                    println("value: $it at ${System.currentTimeMillis() - startTime} ms")
                }
    }

    @Test
    fun `流的异常处理`() = runBlocking {

        // 流构建时的异常处理
        flowOf(1, 2, 3).onEach {
            if (it > 2) throw IllegalStateException("it > 2")
        }.catch { cause: Throwable ->
            println("cause: $cause")
            emit(0)  // 在异常中恢复
        }.collect {
            println("流构建时的异常处理========value: $it")
        }

        // 收集时的异常处理
        try {
            flowOf(1, 2, 3).collect {
                check(it <= 2) { "it > 2" }
                println("收集时的异常处理========value: $it")
            }
        }
        catch (e: Exception) {
            println("exception: $e")
        }
    }

    @Test
    fun `流的完成`() = runBlocking {
        // onCompletion可以获取上游/下游异常
        flowOf(1, 2, 3).onEach {
            if (it > 2) throw IllegalStateException("it > 2")
        }.onCompletion { cause: Throwable? ->
            if (cause != null) println("上游发生了异常: $cause")
            println("done")
        }.catch { cause: Throwable ->
            println("捕获上游异常: $cause")
        }.collect {
            println("value: $it")
        }

        try {
            flowOf(1, 2, 3).onCompletion { cause: Throwable? ->
                if (cause != null) println("下游发生了异常: $cause")
                println("done")
            }.collect {
                check(it <= 2) { "it > 2" }
                println("value: $it")
            }
        }
        catch (e: Exception) {
            println("捕获下游异常: $e")
        }
    }

    /**
     * Flow是冷流，与之相对的是热流，StateFlow 和 SharedFlow 是热流，在垃圾回收之前，都是存在内存之中，并且处于活跃状态的
     *
     * StateFlow类似于LiveData
     */
    @Test
    fun `StateFlow`() = runBlocking {

        val stateFlow = MutableStateFlow<Int>(0)

        val job1 = GlobalScope.launch {
            while (true) {
                delay(1000)
                stateFlow.value++
                println("执行了+1")
            }
        }

        val job2 = GlobalScope.launch {
            while (true) {
                delay(3000)
                stateFlow.value--
                println("执行了-1")
            }
        }

        val job3 = GlobalScope.launch {
            stateFlow.collect {
                println("value: $it")
            }
        }

        joinAll(job1, job2, job3)
    }

    /**
     * Flow是冷流，与之相对的是热流，StateFlow 和 SharedFlow 是热流，在垃圾回收之前，都是存在内存之中，并且处于活跃状态的
     *
     * StaredFlow类似于BroadcastChannel
     */
    @Test
    fun `StaredFlow`() = runBlocking {
        val sharedFlow = MutableStateFlow<Long>(0L)

        val job1 = GlobalScope.launch {
            while (true) {
                delay(1000)
                sharedFlow.emit(System.currentTimeMillis())
            }
        }

        val job2 = GlobalScope.launch {
            sharedFlow.collect{
                println("job2 value: $it")
            }
        }

        val job3 = GlobalScope.launch {
            sharedFlow.collect{
                println("job3 value: $it")
            }
        }

        joinAll(job1, job2, job3)
    }
}