package wangsc.juc.tool;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SemaphoreDemo {

    static Semaphore semaphore = new Semaphore(10);

    static class ThreadDemo implements Runnable {
        int permits;
        ThreadDemo(int a) {
            permits = a;
        }
        @SneakyThrows
        @Override
        public void run() {
            log.info("尝试获取 "+permits+" 资源中....");
            semaphore.acquire(permits);
            log.info("获得 "+permits+" 资源啦！！！");
            TimeUnit.SECONDS.sleep(2);
            log.info("不需要啦，准备释放 "+permits+" 资源");
            semaphore.release(permits);
            log.info("结束啦");
        }
    }

    public static void main(String[] args) {
        int permits[] = new int[]{5, 4, 3};
        for(int i = 0; i < 3; i++){
            new Thread(new ThreadDemo(permits[i])).start();
        }
    }
}
