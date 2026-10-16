package cn.nitrowater.resource.web.dto;

import java.util.List;

/**
 * Filter dictionaries for the file-management console.
 *
 * <p>Shipped by the server so the front-end never hardcodes enum labels: a drifted hardcoded label
 * (the console previously listed the wrong statuses) is a real defect class. The enum groups are
 * derived from the domain enums themselves; {@code app} is data-driven, listing the namespaces that
 * actually exist rather than a fixed list.</p>
 *
 * @param app application namespaces present in the data (distinct, ascending)
 * @param sourceType one option per {@code SourceType} constant, as {@code NAME(value)}
 * @param status one option per {@code ResourceStatus} constant, as {@code NAME(value)}
 */
public record DictionaryResp(
        List<OptionResp> app,
        List<OptionResp> sourceType,
        List<OptionResp> status) {
}
