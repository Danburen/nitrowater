package cn.nitrowater.resource.web.dto;

/**
 * One selectable option of a console filter dictionary.
 *
 * @param value the machine value echoed back on the query (e.g. {@code ACTIVE}, {@code nitrowater})
 * @param label the human label rendered in the console (e.g. {@code ACTIVE(1)})
 */
public record OptionResp(String value, String label) {
}
