package wangsc.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

@Slf4j(topic = "threadTest")
public class ThreadCreation {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // 直接创建 Runnable 放入Thread 对象中
        Thread tmp = new Thread(() -> log.debug("111"));
        tmp.setName("thread-1");
        tmp.start();

        // 将 Runnable 与 Thread 分隔开，之后可以与线程池进行联用
        Runnable run = () -> log.debug("222");
        Thread t2 = new Thread(run, "thread-2");
        t2.start();

        // FutureTask 包含返回信息的 Runnable 实现类
        // public class FutureTask<V> implements RunnableFuture<V>
        // public interface RunnableFuture<V> extends Runnable, Future<V>
        // 传入 Callable 对象，该对象是一个函数式接口
        FutureTask<Integer> future = new FutureTask<>(()->{
            log.debug("333");
            return 100;
        });
        Thread t3 = new Thread(future, "thread-3");
        t3.start();
        System.out.println(future.get());

        // 通过源码可以看到，FutureTask 只会运行一次，因此下面的线程不会去执行
        // run 方法最开始会判断： state != NEW || !RUNNER.compareAndSet(this, null, Thread.currentThread()))
        // Thread 线程状态非 NEW 或者 判断当前Task是否被赋予线程执行
        Thread t4 = new Thread(future, "thread-4");
        t4.start();
        System.out.println(future.get());

    }
}
