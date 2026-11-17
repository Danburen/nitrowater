package cn.nitrowater.account.infra.communication.email;

/**
 * Content templates under {@code templates/email/fragment/}.
 *
 * <p>A fragment is rendered on its own first, then injected into the layout as the
 * {@code content} variable, so the fragment never needs to know about the surrounding chrome.</p>
 */
public enum EmailTemplateFragment {

    /** Six-digit verification code (login / register / password reset / binding). */
    VERIFY_CODE("verify-code");

    private final String templateKey;

    EmailTemplateFragment(String templateKey) {
        this.templateKey = templateKey;
    }

    /**
     * @return file name (without directory and extension) under {@code fragment/}
     */
    public String templateKey() {
        return templateKey;
    }
}
