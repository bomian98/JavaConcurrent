package wangsc.thread;

import java.util.concurrent.TimeUnit;

public class ThreadSleepAndYield {
    /**
     * 1. 调用 sleep 会让当前线程从 Running 进入 Timed Waiting 状态（阻塞）
     * 2. 其它线程可以使用 interrupt 方法打断正在睡眠的线程，这时 sleep 方法会抛出 InterruptedException
     * 3. 睡眠结束后的线程未必会立刻得到执行
     */
    class ThreadSleep{
        public void sleep(){
            Thread t = new Thread(()->{
                try {
                    TimeUnit.SECONDS.sleep(1); // 可读性更好
                    Thread.sleep(1); // 也可以使用
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            t.start();
        }
    }

    /**
     * 1. 调用 yield 会让当前线程从 Running 进入 Runnable 就绪状态，然后调度执行其它线程
     * 2. 具体的实现依赖于操作系统的任务调度器
     *
     * 最好在单核的情况下测试，否则虽然yield使线程进入Runnable状态，也会由于有充足的CPU使得线程又从Runnable进入Running状态
     */
    class ThreadYield{
        public void yield(){

        }
    }
}

