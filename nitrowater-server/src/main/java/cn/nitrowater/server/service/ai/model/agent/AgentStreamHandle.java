package cn.nitrowater.server.service.ai.model.agent;

import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Control surface of a streamed turn that is running on a worker thread.
 *
 * <p>The web layer holds this handle so a disconnect, error or timeout can stop writing (and,
 * when configured, stop the model call). The pipeline itself reads {@link #clientGone()} to decide
 * whether frames still have a reader, while it keeps accumulating the answer so the turn is
 * persisted either way.</p>
 */
public final class AgentStreamHandle {

    private final boolean interruptOnDisconnect;
    private final AtomicBoolean clientGone = new AtomicBoolean(false);
    private final AtomicReference<Disposable> running = new AtomicReference<>();

    /**
     * @param interruptOnDisconnect whether a disconnect also cancels the model subscription
     */
    public AgentStreamHandle(boolean interruptOnDisconnect) {
        this.interruptOnDisconnect = interruptOnDisconnect;
    }

    /** @return whether the client can no longer receive frames */
    public boolean clientGone() {
        return clientGone.get();
    }

    /** Marks the client as gone without touching the model subscription. */
    public void markClientGone() {
        clientGone.set(true);
    }

    /**
     * @param subscription pipeline subscription, attached once the turn has been started
     */
    public void attach(Disposable subscription) {
        running.set(subscription);
    }

    /**
     * Releases the handle because the response ended or the browser went away.
     *
     * <p>Writing stops in every case; the model is only interrupted when the deployment asks for
     * it, so the full answer can still be stored by default.</p>
     */
    public void cancel() {
        clientGone.set(true);
        if (!interruptOnDisconnect) {
            return;
        }
        Disposable subscription = running.get();
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
