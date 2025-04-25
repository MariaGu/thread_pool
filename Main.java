import java.util.concurrent.*;

public class Main {

    public static void main(String[] args) {

        CustomExecutor threadPool = new CustomThreadPool(
                2,
                4,
                5,
                TimeUnit.SECONDS,
                5,
                1);

        log("Starting submitting tasks.");

        for (int i = 0; i < 22; i++) {
            final int taskId = i;
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    log("Task " + taskId + " started execution.");
                    try {
                        Thread.sleep(8000); // working 8 secs
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    log("Task " + taskId + " completed execution.");
                }

                @Override
                public String toString() {
                    return "Task-" + taskId;
                }
            });
        }

        Future<String> futureResult = threadPool.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                log("Callable Task started execution.");
                Thread.sleep(1000);
                log("Callable Task completed execution.");
                return "Result from Callable Task";
            }

            @Override
            public String toString() {
                return "Callable-Task";
            }
        });

        try {
            String result = futureResult.get();
            log("Callable task finished: " + result);
        } catch (Exception e) {
            log(e.getMessage());
        }

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        threadPool.shutdown();
        log("Pool shutdown(soft) initiated.");

        try {
            Thread.sleep(5000 + 8000); //pool keep alive time + imitation of task working
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log("Demonstration finished.");
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}
