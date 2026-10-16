package cn.nitrowater.autogen.ast;

import java.util.List;

/** One field declaration: name, declared type and modifiers. */
public record FieldFact(String name, String type, List<String> modifiers) {
}
