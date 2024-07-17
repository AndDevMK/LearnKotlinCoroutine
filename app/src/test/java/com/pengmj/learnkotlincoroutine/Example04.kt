package com.pengmj.learnkotlincoroutine

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author MinKin
 * @date 2024/7/17
 * @desc
 */
class Example04 {

    /**
     * 1、Channel实际上是一个并发安全的队列，它可以用来连接协程，实现不同协程的通信
     *
     * 2、Channel实际上就是一个队列，队列中一定存在缓冲区，那么一旦这个缓冲区满了，并且也一直没有人调用receive并取走函数，send就需要挂起。直到receive之后才会继续往下执行。
     */
    @Test
    fun `使用Channel进行协程通信`() = runBlocking {
        val channel = Channel<Int>(capacity = 5)

        val producer = GlobalScope.launch {
            var i = 0
            while (true) {
                delay(1000)
                channel.send(++i)
                println("send $i")
            }
        }

        val consumer = GlobalScope.launch {
            while (true) {
                delay(8 * 1000)
                println("receive ${channel.receive()}")
            }
        }

        // 迭代器写法
        // val consumer = GlobalScope.launch {
        //     val iterator = channel.iterator()
        //     while (iterator.hasNext()) {
        //         delay(8 * 1000)
        //         println("receive ${iterator.next()}")
        //     }
        // }

        // for-in写法
        // val consumer = GlobalScope.launch {
        //     for (element in channel) {
        //         delay(8 * 1000)
        //         println("receive $element")
        //     }
        // }

        joinAll(producer, consumer)
    }

    /**
     * 构造生产者与消费者的便捷方法。
     *
     * 可以通过produce方法启动一个生产者协程，并返回一个ReceiveChannel，其他协程就可以用这个Channel来接收数据了。
     *
     * 反过来，我们可以用actor肩动一个消费者协程。
     */
    @Test
    fun `produce`() = runBlocking {
        val receiveChannel: ReceiveChannel<Int> = GlobalScope.produce(capacity = 5) {
            repeat(100) {
                delay(1000)
                send(it)
                println("send $it")
            }
        }

        val consumer = GlobalScope.launch {
            for (element in receiveChannel) {
                delay(8 * 1000)
                println("receive $element")
            }
        }

        consumer.join()
    }

    /**
     * 构造生产者与消费者的便捷方法。
     *
     * 可以通过produce方法启动一个生产者协程，并返回一个ReceiveChannel，其他协程就可以用这个Channel来接收数据了。
     *
     * 反过来，我们可以用actor肩动一个消费者协程。
     */
    @Test
    fun `actor`() = runBlocking {
        val sendChannel: SendChannel<Int> = GlobalScope.actor(capacity = 5) {
            while (true) {
                delay(8 * 1000)
                println("receive ${receive()}")
            }
        }

        val producer = GlobalScope.launch {
            var i = 0
            while (true) {
                delay(1000)
                sendChannel.send(++i)
                println("send $i")
            }
        }

        producer.join()
    }

    /**
     * produce和actor返回的Channel都会随着对应的协程执行完毕而关闭，也正是这样，Channel才被称沩热数据流。
     *
     * 对于一个Channel，如果我们调用了它的close方法，它会立即停止接收新元素，也就是说这时它的isClosedForSend会立即返回true。
     *
     * 而由于Channel缓冲区的存在，这时候可能还有一些元素没有被处理完，因此要等所有的元素都被读取之后isClosedForReceive才会返回true。
     *
     * Channel的生命周期最好由主导方来维护，建议由主导的一方实现关闭。
     */
    @Test
    fun `Channel关闭`() = runBlocking {
        val channel = Channel<Int>(capacity = 5)

        val producer = GlobalScope.launch {
            for (i in 0 until 5) {
                channel.send(i)
                println("send $i")
            }
            channel.close()  // 由主导的一方实现关闭
            println(
                    """
                    - isClosedForSend: ${channel.isClosedForSend}
                    - isClosedForReceive: ${channel.isClosedForReceive}
                    """.trimIndent()
            )
        }

        val consumer = GlobalScope.launch {
            for (element in channel) {
                delay(1000)
                println("receive $element")
            }
            println(
                    """
                    - isClosedForSend: ${channel.isClosedForSend}
                    - isClosedForReceive: ${channel.isClosedForReceive}
                    """.trimIndent()
            )
        }

        joinAll(producer, consumer)
    }

    /**
     * 发送端和接收端在Channel中存在一对多的情形，多个接收端不存在互斥行为。
     *
     * BroadcastChannel过期了，了解即可。1.7.0移除。使用Flow.shareIn替代
     */
    @OptIn(ObsoleteCoroutinesApi::class)
    @Test
    fun `使用BroadcastChannel`() = runBlocking {
        // val broadcastChannel = BroadcastChannel<Int>(capacity = 3)

        val channel = Channel<Int>(3)
        val broadcastChannel = channel.broadcast(capacity = 3)

        val producer = GlobalScope.launch {
            List(3) {
                delay(1000)
                broadcastChannel.send(it)
            }
            broadcastChannel.close()
        }
        List(3) { index ->
            GlobalScope.launch {
                val receiveChannel = broadcastChannel.openSubscription()
                for (element in receiveChannel) {
                    println("[#$index] receive $element")
                }
            }
        }.joinAll()
    }

    private suspend fun getFromLocal() = coroutineScope {
        async {
            delay(1000)
            100
        }
    }

    private suspend fun getFromRemote() = coroutineScope {
        async {
            delay(2000)
            200
        }
    }

    data class Response<T>(
        val result: T,
        val isLocal: Boolean,
    )

    /**
     * 我们怎么知道哪些事件可以被select呢？其实所有能够被select的事件都是SelectClauseN类型，包括：
     *
     * • SelectClauseO：对应事件没有返回值，例如join没有返回值，那么onJoin就是SelectClauseN类型。使用时，onJoin的参数是一个无参函数。
     *
     * • SelectClause1：对应事件有返回值，前面的onAwait和onReceive都是此类情况。
     *
     * • SelectClause2：对应事件有返回值，此外还需要一个额外的参数，例如Channel.onSend有两个参数，第一个是Channel数据类型的值，表示即将发送的值；第二个是发送成功时的回调参数。
     *
     * 如果想要确认挂起函数是否支持select，只需要查看其是否存在对应的SelectClauseN类型可回调即可。
     */
    @Test
    fun `select之await多路复用`() = runBlocking {
        val job = GlobalScope.launch {
            val localData = getFromLocal()
            val remoteData = getFromRemote()
            val response = select<Response<Int>> {
                localData.onAwait {
                    Response(it, true)
                }
                remoteData.onAwait {
                    Response(it, false)
                }
            }
            println("result: ${response.result}")
        }
        job.join()
    }


    @Test
    fun `select之Channel多路复用`() = runBlocking {
        val channels = List(2) { Channel<Int>() }
        GlobalScope.launch {
            delay(100)
            channels[0].send(100)
        }

        GlobalScope.launch {
            delay(200)
            channels[1].send(200)
        }

        val result = select<Int> {
            channels.forEach { channel ->
                channel.onReceive { it }
            }
        }
        println("result: $result")
    }

    @Test
    fun `Flow多路复用`() = runBlocking {
        listOf(::getFromLocal, ::getFromRemote).map {
            it.invoke()
        }.map { deferred ->
            flow {
                emit(deferred.await())
            }
        }.merge().collect {
            println("result: $it")
        }
    }

    /**
     * 协程并发的解决方案：
     *
     * Channel：并发安全的消息通道，我们已经非常熟悉。
     *
     * Mutex：轻量级锁，它的lock和unlock从语义上与线程锁比较类似，之所以轻量是因为它在获取不到锁时不会阻塞线程，而是挂起等待锁的释放。
     *
     * Semaphore：轻量级信号量，信号量可以有多个，协程在获取到信号量后即可执行并发操作。当Semaphore的参数为1时，效果等价于Mutex
     */
    @Test
    fun `协程并发安全`() = runBlocking {
        // 采用线程的解决方案
        val count1 = AtomicInteger(0)
        List(1000) {
            GlobalScope.launch {
                count1.getAndIncrement()
            }
        }.joinAll()
        println("count1: ${count1.get()}")

        // 采用协程的解决方案
        // Mutex
        var count2 = 0
        val mutex = Mutex()
        List(1000) {
            GlobalScope.launch {
                mutex.withLock {
                    count2++
                }
            }
        }.joinAll()
        println("count2: $count2")

        // Semaphore
        var count3 = 0
        val semaphore = Semaphore(1)
        List(1000) {
            GlobalScope.launch {
                semaphore.withPermit {
                    count3++
                }
            }
        }.joinAll()
        println("count3: $count3")

        // 另外，还可以在编写函数时要求它不得访问外部状态，只能基于参数做运算，通过返回值提供运算结果
        var count4 = 0  // 此处count4表示参数
        val result = count4 + List(1000) {
            GlobalScope.async {
                1
            }
        }.sumOf { it.await() }
        println("result: $result")
    }
}