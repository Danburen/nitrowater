package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.service.ai.agent.AiStreamChunk;
import cn.nitrowater.server.service.ai.model.agent.AgentRun;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the cancel signal the agent loop reads between model rounds. */
class AgentRunTest {

    @Test
    void isNotCancelledBeforeTheConsumerGoesAway() {
        AgentRun run = new AgentRun();

        assertThat(run.cancelled()).isFalse();
    }

    /**
     * Disposing the stream must be visible to the loop: disposing the in-flight subscription only
     * stops the current round, so a cancel that lands between rounds has to be observable by a flag.
     */
    @Test
    void turnsTheCancelIntoAFlagTheLoopCanCheck() {
        AgentRun run = new AgentRun();
        Disposable subscription = run.events().subscribe();

        subscription.dispose();

        assertThat(run.cancelled()).isTrue();
    }

    @Test
    void deliversStepsToTheSubscriber() {
        AgentRun run = new AgentRun();
        AtomicReference<AiStreamChunk> received = new AtomicReference<>();
        Disposable subscription = run.events().subscribe(received::set);

        run.step(AiStreamChunk.Step.thinking());

        assertThat(received.get().step().phase()).isEqualTo("thinking");
        assertThat(run.cancelled()).isFalse();
        subscription.dispose();
    }
}
