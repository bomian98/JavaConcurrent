package wangsc.jcip;

import java.util.concurrent.*;

public class ThreadDeadlock {

    static ExecutorService exec = Executors.newSingleThreadExecutor();

    public static class LoadFileTask implements Callable<String> {
        private final String fileName;

        public LoadFileTask(String fileName) {
            this.fileName = fileName;
        }

        public String call() throws Exception {
            // Here's where we would actually read the file
            return "";
        }
    }

    public static class RenderPageTask implements Callable<String> {
        public String call() throws Exception {
            Future<String> header, footer;
            header = exec.submit(new LoadFileTask("header.html"));
            footer = exec.submit(new LoadFileTask("footer.html"));
            String page = renderBody();
            System.out.println("试试");
            // Will deadlock -- task waiting for result of subtask
            String h = header.get();
            System.out.println("试试");
            String f = footer.get();
            System.out.println("试试");
            System.out.println(new String(h + page + f));
            return "1";
        }

        private String renderBody() {
            // Here's where we would actually render the page
            return "";
        }
    }

    public static void main(String[] args) {
        RenderPageTask threadDeadlock = new RenderPageTask();
        FutureTask<String> task = new FutureTask(threadDeadlock);
        new Thread(task).start();
    }
}
