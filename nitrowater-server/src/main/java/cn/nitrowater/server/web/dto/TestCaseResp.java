package cn.nitrowater.server.web.dto;

/**
 * A test case view. Only sample cases are exposed to non-admins.
 *
 * @param idx zero-based case index
 * @param stdin case input (null for hidden cases)
 * @param expectedOutput expected output (null for hidden cases)
 * @param isSample whether this case is a sample
 * @param score optional partial score
 */
public record TestCaseResp(
        Integer idx,
        String stdin,
        String expectedOutput,
        Boolean isSample,
        Integer score) {
}
