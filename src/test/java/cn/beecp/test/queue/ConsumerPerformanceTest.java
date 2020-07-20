/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.beecp.test.queue;

import cn.beecp.util.FastTransferQueue;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Consumer Time PerformanceTest
 *
 * @author Chris.Liao
 */
public class ConsumerPerformanceTest {
    private static final Object transferObject = new Object();

    public static void main(String[] args) throws Exception {
        int producerSize = 10, consumerSize = 100, takeTimes = 100;
        System.out.println(".................ConsumerPerformanceTest......................");
        testTransferQueue("ArrayBlockingQueue", new ArrayBlockingQueue<Object>(1000), producerSize, consumerSize, takeTimes);
        testTransferQueue("LinkedBlockingQueue", new LinkedBlockingQueue<Object>(), producerSize, consumerSize, takeTimes);
        testTransferQueue("LinkedTransferQueue", new LinkedTransferQueue<Object>(), producerSize, consumerSize, takeTimes);
        testTransferQueue("SynchronousQueue", new SynchronousQueue<Object>(), producerSize, consumerSize, takeTimes);
        testTransferQueue("FastTransferQueue", new FastTransferQueue<Object>(), producerSize, consumerSize, takeTimes);
    }

    private static void testTransferQueue(String queueName, Queue<Object> queue, int producerSize, int consumerSize, int pollSize) throws Exception {
        Consumer[] consumers = new Consumer[consumerSize];
        CountDownLatch producersDownLatch = new CountDownLatch(producerSize);
        CountDownLatch consumersDownLatch = new CountDownLatch(consumerSize);
        AtomicBoolean existConsumerInd = new AtomicBoolean(true);
        Method pollMethod = queue.getClass().getMethod("poll", Long.TYPE, TimeUnit.class);
        long startTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        //Consumers
        for (int i = 0; i < consumerSize; i++) {
            consumers[i] = new Consumer(queue, startTime, pollSize, consumersDownLatch, pollMethod);
            consumers[i].start();
        }

        //Producers
        for (int i = 0; i < producerSize; i++) {
            new Producer(queue, startTime, existConsumerInd, producersDownLatch).start();
        }

        consumersDownLatch.await();
        existConsumerInd.set(false);
        producersDownLatch.await();

        //Summary and Conclusion
        int totalExeSize = consumerSize * pollSize;
        BigDecimal totTime = new BigDecimal(0);
        for (int i = 0; i < consumerSize; i++) {
            totTime = totTime.add(new BigDecimal(consumers[i].getTookTime()));
        }

        if (totTime.longValue() > 0) {
            BigDecimal avgTime = totTime.divide(new BigDecimal(totalExeSize), 0, BigDecimal.ROUND_HALF_UP);
            System.out.println("<" + queueName + "> producer-size:" + producerSize + ",consumer-size:"
                    + consumerSize + ",poll total count:" + totalExeSize + ",total time:" + totTime.longValue()
                    + "(ns),avg time:" + avgTime + "(ns)");
        }
    }

    private static final Object poll(Queue<Object> queue, Method pollMethod) {
        try {
            return pollMethod.invoke(queue, Integer.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    static final class Producer extends Thread {
        private long startTime;
        private Queue<Object> queue;
        private AtomicBoolean activeInd;
        private CountDownLatch producersDownLatch;

        public Producer(Queue<Object> queue, long startTime, AtomicBoolean activeInd, CountDownLatch producersDownLatch) {
            this.queue = queue;
            this.startTime = startTime;
            this.activeInd = activeInd;
            this.producersDownLatch = producersDownLatch;
        }

        public void run() {
            while (activeInd.get()) {
                queue.offer(transferObject);
            }
            producersDownLatch.countDown();
        }
    }

    static final class Consumer extends Thread {
        private long startTime;
        private int loopTimes;
        private CountDownLatch latch;
        private Method pollMethod;
        private Queue<Object> queue;
        private long tookTime;

        public Consumer(Queue<Object> queue, long startTime, int pollTimes, CountDownLatch latch, Method pollMethod) {
            this.queue = queue;
            this.startTime = startTime;
            this.loopTimes = pollTimes;
            this.latch = latch;
            this.pollMethod = pollMethod;
        }

        public long getTookTime() {
            return tookTime;
        }

        public void run() {
            LockSupport.parkNanos(startTime - System.nanoTime());
            long time1 = System.nanoTime();
            for (int i = 0; i < loopTimes; i++) {
                poll(queue, pollMethod);
            }
            tookTime = System.nanoTime() - time1;
            latch.countDown();
        }
    }
}





