package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.config.properties.JudgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Background consumers of the {@link JudgeQueue}.
 *
 * <p>Starts with the application context and drains submissions with a pool of
 * {@code app.judge.workers} threads, each blocking-polling the queue so it idles without
 * busy-waiting. Because the queue's pop is atomic, the thread count is the effective concurrency
 * gate on judging (and therefore on the number of in-flight sandbox executions). Judging itself is
 * delegated to {@link JudgeService}; any error is logged and the loop continues (with a short
 * backoff when the queue is unreachable).</p>
 */
@Slf4j
@Component
public class JudgeWorker implements SmartLifecycle {

    private static final int POLL_TIMEOUT_SECONDS = 5;
    private static final long ERROR_BACKOFF_MS = 2000;
    private static final String THREAD_NAME_PREFIX = "judge-worker-";

    private final JudgeQueue queue;
    private final JudgeService judgeService;
    private final int workers;

    private volatile boolean running;
    private final List<Thread> threads = new ArrayList<>();

    public JudgeWorker(JudgeQueue queue, JudgeService judgeService, JudgeProperties properties) {
        this.queue = queue;
        this.judgeService = judgeService;
        this.workers = Math.max(1, properties.workers());
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 0; i < workers; i++) {
            Thread thread = new Thread(this::loop, THREAD_NAME_PREFIX + i);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
        log.info("Judge worker started with {} consumer thread(s)", workers);
    }

    @Override
    public void stop() {
        running = false;
        for (Thread thread : threads) {
            thread.interrupt();
        }
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start last, stop first.
        return Integer.MAX_VALUE;
    }

    private void loop() {
        while (running) {
            try {
                Long submissionId = queue.poll(POLL_TIMEOUT_SECONDS);
                if (submissionId != null) {
                    judgeService.judge(submissionId);
                }
            } catch (Exception ex) {
                if (!running) {
                    return;
                }
                log.warn("Judge worker loop error: {}", ex.getMessage());
                sleepQuietly();
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(ERROR_BACKOFF_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
