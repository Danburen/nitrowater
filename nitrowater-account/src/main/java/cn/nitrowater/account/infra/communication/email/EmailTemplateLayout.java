package cn.nitrowater.account.infra.communication.email;

/**
 * Wrapper templates under {@code templates/email/layout/}.
 *
 * <p>The layout renders the fragment produced for a message and adds the shared chrome
 * (header logo, footer). A new message type normally only needs a new
 * {@link EmailTemplateFragment}, not a new layout.</p>
 */
public enum EmailTemplateLayout {

    /** Standard single-column message. */
    BASE("base");

    private final String templateKey;

    EmailTemplateLayout(String templateKey) {
        this.templateKey = templateKey;
    }

    /**
     * @return file name (without directory and extension) under {@code layout/}
     */
    public String templateKey() {
        return templateKey;
    }
}
