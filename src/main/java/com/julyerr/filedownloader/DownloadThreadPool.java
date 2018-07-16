package com.julyerr.filedownloader;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DownloadThreadPool extends ThreadPoolExecutor {
    private ConcurrentHashMap<Future<?>, Runnable> mRunnableMonitor = new ConcurrentHashMap<>();
    //    每个mission_id，对应队列，队列中存储同一个mission的多个线程运行之后的结果
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Future<?>>> mMissionsMonitor = new ConcurrentHashMap<>();

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                handler);
    }

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory);
    }

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, handler);
    }

    @Override
    public Future<?> submit(Runnable task) {
//		提交任务，先运行
        Future<?> future = super.submit(task);
        if (task instanceof DownloadRunnable) {
            DownloadRunnable runnable = (DownloadRunnable) task;

            if (mMissionsMonitor.containsKey(runnable.MISSION_ID)) {
//			结果添加到 mMissionsMonitor 队列中
                mMissionsMonitor.get(runnable.MISSION_ID).add(future);
            } else {
//                构建新的任务队列
                ConcurrentLinkedQueue<Future<?>> queue = new ConcurrentLinkedQueue<>();
                queue.add(future);
                mMissionsMonitor.put(runnable.MISSION_ID, queue);
            }

            mRunnableMonitor.put(future, task);

        } else {
            throw new RuntimeException(
                    "runnable is not an instance of DownloadRunnable!");
        }
        return future;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t == null) {
            System.out.println(Thread.currentThread().getId()
                    + " has been succeesfully finished!");
        } else {
            System.out.println(Thread.currentThread().getId()
                    + " errroed! Retry");
        }
//        自己忽略
        for (Future<?> future : mRunnableMonitor.keySet()) {
            //    如果有新的线程完成，开启新的线程继续分担下载任务
            if (future.isDone() == false) {
                DownloadRunnable runnable = (DownloadRunnable) mRunnableMonitor
                        .get(future);
                DownloadRunnable newRunnable = runnable.split();
                if (newRunnable != null) {
//                    只新建一个线程
                    submit(newRunnable);
                    break;
                }
            }
        }
    }

    public boolean isFinished(int mission_id) {
        ConcurrentLinkedQueue<Future<?>> futures = mMissionsMonitor
                .get(mission_id);
//        没有此任务
        if (futures == null)
            return true;

//        任务是否全部运行完成
        for (Future<?> future : futures) {
            if (future.isDone() == false) {
                return false;
            }
        }
        return true;
    }

    public void pause(int mission_id) {
        ConcurrentLinkedQueue<Future<?>> futures = mMissionsMonitor
                .get(mission_id);
        if (futures == null) {
            return;
        }
//        中断该mission_id的所有线程
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    public void cancel(int mission_id) {
        ConcurrentLinkedQueue<Future<?>> futures = mMissionsMonitor
                .remove(mission_id);
        if (futures == null) {
            return;
        }
        for (Future<?> future : futures) {
//            移除，取消
            Runnable runnable = mRunnableMonitor.remove(future);
            future.cancel(true);
        }
    }
}
