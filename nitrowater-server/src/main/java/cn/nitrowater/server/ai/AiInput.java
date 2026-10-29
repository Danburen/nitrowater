package cn.nitrowater.server.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared validation for chat/agent user input. */
public final class AiInput {

    private AiInput() {
    }

    /**
     * @param question raw question
     * @param maxCharacters configured maximum ({@code <= 0} disables the upper bound)
     * @return the trimmed question
     * @throws ResponseStatusException 400 when blank or too long
     */
    public static String question(String question, int maxCharacters) {
        String sanitized = question == null ? "" : question.trim();
        if (sanitized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must not be blank");
        }
        if (maxCharacters > 0 && sanitized.length() > maxCharacters) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Question is too long (max " + maxCharacters + " characters)");
        }
        return sanitized;
    }
}
