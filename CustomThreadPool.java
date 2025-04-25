import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class CustomThreadPool implements CustomExecutor {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final List<Worker> workers = new CopyOnWriteArrayList<>();

    private final AtomicInteger workerCount = new AtomicInteger(0);
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    private volatile boolean isShutdown = false;

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
                            int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;

        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    private void addWorker() {
        if (workers.size() >= maxPoolSize) return;
        String workerName = "MyPool-worker-" + workerCount.incrementAndGet();
        Worker worker = new Worker(workerName);
        workers.add(worker);
        log("[ThreadFactory] Creating new thread: " + worker.getName());
        worker.start();
    }

    private int getIdleCountWorkers(){
        int idleCountWorkers = 0;
        for (Worker w : workers) {
            if (w.isIdle()) idleCountWorkers++;
        }
        return idleCountWorkers;
    }

    @Override
    public void execute(Runnable task) {
        if (isShutdown) {
            throw new RejectedExecutionException("ThreadPool is already shutting down.");
        }

        if (getIdleCountWorkers() < minSpareThreads && workers.size() < maxPoolSize) {
            addWorker();
        }

        if (!workers.isEmpty()) {
            int index = roundRobinCounter.getAndIncrement() % workers.size();
            Worker worker = workers.get(index);
            boolean offered = worker.offerTask(task);
            if (offered) {
                log("[Pool] Task accepted into queue #" + index + ": " + task.toString());
            } else {
                log("[Rejected] Task " + task.toString() +
                        " was rejected due to overload! Executing in caller thread.");
                task.run();
            }
        } else {
            addWorker();
            boolean offered = workers.getFirst().offerTask(task);
            if (offered) {
                log("[Pool] Task accepted into queue #0: " + task.toString());
            } else {
                log("[Rejected] Task " + task.toString() +
                        " was rejected due to overload! Executing in caller thread.");
                task.run();
            }
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        log("[Pool] Shutdown (soft) initiated.");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        log("[Pool] Immediate shutdown initiated.");
        for (Worker worker : workers) {
            worker.interrupt();
            worker.clearQueue();
        }
    }

    private class Worker extends Thread {
        private final BlockingQueue<Runnable> taskQueue;

        private volatile boolean idle = false;

        public Worker(String name) {
            super(name);
            taskQueue = new ArrayBlockingQueue<>(queueSize);
        }

        public boolean isIdle() {
            return idle && taskQueue.isEmpty();
        }

        public boolean offerTask(Runnable task) {
            return taskQueue.offer(task);
        }

        public void clearQueue() {
            taskQueue.clear();
        }

        @Override
        public void run() {
            try {
                while (!isShutdown) {
                    idle = true;
                    Runnable task = taskQueue.poll(keepAliveTime, timeUnit);
                    idle = false;

                    if (task != null) {

                        log("[Worker] " + getName() + " started execute " + task.toString());
                        try {
                            task.run();
                        } catch (Exception e) {
                            log("[Worker] Exception in task: " + e.getMessage());
                        }
                    } else {
                        if (workers.size() > corePoolSize) {
                            log("[Worker] " + getName() + " idle timeout, stopping.");
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                log(e.getMessage());
            } finally {
                log("[Worker] " + getName() + " terminated.");
                workers.remove(this);
            }
        }
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}