package wangsc.juc.tool;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CyclicBarrierDemo {

    static CyclicBarrier barrier = new CyclicBarrier(3);

    static class ThreadDemo implements Runnable {
        private int time;
        CyclicBarrier barrier;
        ThreadDemo(int time, CyclicBarrier barrier) {
            this.time = time;
            this.barrier = barrier;
        }

        @SneakyThrows
        @Override
        public void run() {
            log.info("准备开始");
            barrier.await();
            log.info("开始环节1");
            TimeUnit.SECONDS.sleep(time);
            log.info("完成环节1");
            barrier.await();
            log.info("开始环节2");
            TimeUnit.SECONDS.sleep(time);
            log.info("完成环节2");
        }
    }

    public static void main(String[] args) {
        int[] arr = new int[]{2, 4, 6};
        for(int i = 0; i < 3; i++){
            new Thread(new ThreadDemo(arr[i], barrier)).start();
        }
    }
}
