package cn.nitrowater.server.service.judge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Single background consumer of the {@link JudgeQueue}.
 *
 * <p>Starts with the application context and drains submissions one at a time via a blocking poll,
 * so it idles without busy-waiting. Judging itself is delegated to {@link JudgeService}; any error
 * is logged and the loop continues (with a short backoff when the queue is unreachable).</p>
 */
@Slf4j
@Component
public class JudgeWorker implements SmartLifecycle {

    private static final int POLL_TIMEOUT_SECONDS = 5;
    private static final long ERROR_BACKOFF_MS = 2000;

    private final JudgeQueue queue;
    private final JudgeService judgeService;

    private volatile boolean running;
    private Thread thread;

    public JudgeWorker(JudgeQueue queue, JudgeService judgeService) {
        this.queue = queue;
        this.judgeService = judgeService;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "judge-worker");
        thread.setDaemon(true);
        thread.start();
        log.info("Judge worker started");
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
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
