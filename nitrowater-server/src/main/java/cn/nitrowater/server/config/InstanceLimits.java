package cn.nitrowater.server.config;

/** Hard limits of the VeloChatX remote-instance feature. */
public final class InstanceLimits {

    /** Maximum number of live instances a single api-key may register. */
    public static final int MAX_INSTANCES_PER_KEY = 3;

    private InstanceLimits() {
    }
}
