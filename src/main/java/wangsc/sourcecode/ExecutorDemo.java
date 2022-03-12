package wangsc.sourcecode;

import lombok.SneakyThrows;

import java.lang.Thread;
import java.util.BitSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorDemo {

    public static void main2(String[] args) throws InterruptedException {
//        ExecutorService service;
//        service = Executors.newCachedThreadPool();
//        service = Executors.newSingleThreadExecutor();
//        service = Executors.newFixedThreadPool(10);
//        service = Executors.newScheduledThreadPool(10);
//        service.submit()
//        Executor
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 10; i++) {
            Runnable run = new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    System.out.println("线程运行" + Thread.currentThread().getName());
                    TimeUnit.SECONDS.sleep(1);
                }
            };
            pool.execute(run);
        }
        pool.shutdown();
        while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
            System.out.println("线程池没有关闭");
        }
        System.out.println("线程池已经关闭");
    }

    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int COUNT_MASK = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    private static int runStateOf(int c)     { return c & ~COUNT_MASK; }
    private static int workerCountOf(int c)  { return c & COUNT_MASK; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    public static void main(String[] args) {
        System.out.println(RUNNING+" "+Integer.toBinaryString(RUNNING));
        System.out.println(SHUTDOWN+" "+Integer.toBinaryString(SHUTDOWN));
        System.out.println(STOP+" "+Integer.toBinaryString(STOP));
        System.out.println(TIDYING+" "+Integer.toBinaryString(TIDYING));
        System.out.println(TERMINATED+" "+Integer.toBinaryString(TERMINATED));
        System.out.println(Integer.toBinaryString(ctlOf(RUNNING, 0)));
        System.out.println(Integer.toBinaryString(COUNT_BITS));
        System.out.println(Integer.toBinaryString(COUNT_MASK));
        System.out.println(Integer.toBinaryString(~COUNT_MASK));
    }
}
