package com.wrz.reading.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理器
 * 使用共享线程池避免每个Fragment/Activity都创建新的线程池导致资源泄露
 */
public class ExecutorManager {

    private static volatile ExecutorManager instance;

    // 共享的单线程执行器（用于需要顺序执行的任务）
    private final ExecutorService singleThreadExecutor;

    // 共享的线程池（用于并行任务）
    private final ExecutorService threadPool;

    // 引用计数，用于管理生命周期
    private final AtomicInteger refCount = new AtomicInteger(0);

    private ExecutorManager() {
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        threadPool = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors())
        );
    }

    public static ExecutorManager getInstance() {
        if (instance == null) {
            synchronized (ExecutorManager.class) {
                if (instance == null) {
                    instance = new ExecutorManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取单线程执行器
     */
    public ExecutorService getSingleThreadExecutor() {
        return singleThreadExecutor;
    }

    /**
     * 获取线程池
     */
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * 增加引用计数
     */
    public void addReference() {
        refCount.incrementAndGet();
    }

    /**
     * 减少引用计数
     * 线程池为应用级单例，不在引用计数归零时关闭，避免 Activity 切换时
     * 已持有的执行器被 shutdown 导致 RejectedExecutionException
     */
    public void removeReference() {
        int count = refCount.decrementAndGet();
        if (count < 0) {
            refCount.set(0);
        }
    }

    /**
     * 关闭线程池
     */
    private void shutdown() {
        if (singleThreadExecutor != null && !singleThreadExecutor.isShutdown()) {
            singleThreadExecutor.shutdown();
        }
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
    }

    /**
     * 检查线程池是否已关闭
     */
    public boolean isShutdown() {
        return singleThreadExecutor.isShutdown() || threadPool.isShutdown();
    }

    /**
     * 重新初始化（如果已关闭）
     * 重新创建前先关闭旧实例的线程池，避免线程与任务泄漏。
     */
    public static synchronized void reinitialize() {
        if (instance != null && !instance.isShutdown()) {
            instance.shutdown();
        }
        instance = new ExecutorManager();
    }
}
