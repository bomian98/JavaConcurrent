package wangsc.sourcecode;

import java.util.concurrent.*;

public class ThreadPoolExecutorDemo {

    public static void main(String[] args) {
        ExecutorService service = new ThreadPoolExecutor(2, 3, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
//        Executors.
    }
}
