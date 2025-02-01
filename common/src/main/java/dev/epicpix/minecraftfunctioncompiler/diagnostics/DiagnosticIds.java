package dev.epicpix.minecraftfunctioncompiler.diagnostics;

public final class DiagnosticIds {
    private DiagnosticIds() {}

    public static final String UNSPECIFIED_COMMAND_ERROR = "command.error.unspecified";

    public static final String COMMAND_INVALID_STRING_QUOTE = "command.error.invalid.string_quote";
    public static final String COMMAND_INVALID_BOOLEAN = "command.error.invalid.boolean";
    public static final String COMMAND_INVALID_INTEGER = "command.error.invalid.integer";
    public static final String COMMAND_INVALID_FLOAT = "command.error.invalid.float";
    public static final String COMMAND_INVALID_DOUBLE = "command.error.invalid.double";
    public static final String COMMAND_MISSING_RANGE = "command.error.missing.range";
    public static final String COMMAND_INVALID_RANGE = "command.error.invalid.range";
    public static final String COMMAND_INVALID_DATA_LOCATION = "command.error.invalid.data_location";
    public static final String COMMAND_INVALID_SWIZZLE = "command.error.invalid.swizzle";
    public static final String COMMAND_INVALID_VEC3 = "command.error.invalid.vec3";
    public static final String COMMAND_INVALID_VEC3_LOCAL = "command.error.invalid.vec3_local";
    public static final String COMMAND_INVALID_ROTATION = "command.error.invalid.rotation";
    public static final String COMMAND_INVALID_GAMEMODE = "command.error.invalid.gamemode";
    public static final String COMMAND_INVALID_SCOREBOARD_COMPARE_OPERATION = "command.error.invalid.scoreboard_compare_operation";
    public static final String COMMAND_INVALID_SCOREBOARD_OPERATION = "command.error.invalid.scoreboard_operation";
    public static final String COMMAND_INVALID_ATTRIBUTE = "command.error.invalid.attribute";
    public static final String COMMAND_INVALID_TIME_KIND = "command.error.invalid.time_kind";
    public static final String COMMAND_INVALID_DIFFICULTY = "command.error.invalid.difficulty";
    public static final String COMMAND_INVALID_MOB_EFFECT = "command.error.invalid.mob_effect";
    public static final String COMMAND_INVALID_RESULT_STORE_TYPE = "command.error.invalid.result_store_type";
    public static final String COMMAND_INVALID_LOCATION_ANCHOR = "command.error.invalid.location_anchor";
    public static final String COMMAND_INVALID_SCORE_ACCESSOR = "command.error.invalid.score_accessor";
    public static final String COMMAND_INVALID_JSON = "command.error.invalid.json";
    public static final String COMMAND_INVALID_NBT_DATA_LOCATION = "command.error.invalid.nbt_data_location";

    public static final String COMMAND_UNSUPPORTED_MACRO_LOCATION = "command.error.macro.unsupported";
    public static final String COMMAND_EXPECTED_MACRO = "command.error.macro.expected";
    public static final String COMMAND_INVALID_MACRO_NAME = "command.error.invalid.macro_name";

    public static final String COMMAND_INVALID_SELECTOR_SORT = "command.error.invalid.selector.sort";
    public static final String SELECTOR_EXPECTED_PLAYER_TARGETS = "selector.error.expected.player_targets";
    public static final String SELECTOR_EXPECTED_SINGULAR_TARGET = "selector.error.expected.singular_target";
    public static final String SELECTOR_WRONG_MINECRAFT_VERSION = "selector.error.unsupported_in_minecraft_version";
    public static final String SELECTOR_INVALID_TARGET = "selector.error.invalid.target";
    public static final String SELECTOR_INVALID_SYNTAX = "selector.error.invalid.syntax";
    public static final String SELECTOR_UNKNOWN_OPTION = "selector.error.option.unknown";
    public static final String SELECTOR_OPTION_CANNOT_REPEAT = "selector.error.option.cannot_repeat";
    public static final String SELECTOR_OPTION_INVALID = "selector.error.option.invalid";
    public static final String SELECTOR_INVALID_ENTITY = "selector.error.option.type.invalid";

    public static final String NBT_INVALID_SYNTAX = "nbt.error.invalid.syntax";
    public static final String NBT_EMPTY_KEY = "nbt.error.empty_key";
    public static final String NBT_MISMATCHED_TYPE = "nbt.error.mismatched_type";
    public static final String NBT_INVALID_ARRAY_TYPE = "nbt.error.invalid.array_type";

    public static final String TEXT_INVALID_COLOR = "text.invalid.color";
    public static final String TEXT_INVALID = "text.invalid";
    public static final String TEXT_INVALID_UNSUPPORTED_CLICK_EVENT = "text.unsupported.click_event";
    public static final String TEXT_INVALID_UNSUPPORTED_HOVER_EVENT = "text.unsupported.hover_event";


    public static final String COMMAND_COMPILE_UNKNOWN_COMMAND = "command.compile.unknown_command";
    public static final String COMMAND_COMPILE_UNSUPPORTED_STORE = "command.compile.unsupported_store";
    public static final String COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS = "command.compile.unsupported_nbt_access";
    public static final String COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS = "command.compile.unsupported_nbt_source_access";
    public static final String COMMAND_COMPILE_UNREACHABLE_CODE = "command.compile.unreachable_code";
    public static final String COMMAND_COMPILE_UNEXPECTED_CONTEXT_END = "command.compile.unexpected_context_end";
    public static final String COMMAND_COMPILE_EXPECTED_RESULT = "command.compile.result_storage.expected.result";
    public static final String COMMAND_COMPILE_EXPECTED_SUCCESS = "command.compile.result_storage.expected.success";



    public static final String EMITTER_INVALID_LOAD_LOCATION = "emitter.high_level.error.invalid.location.load";
    public static final String EMITTER_INVALID_STORE_LOCATION = "emitter.high_level.error.invalid.location.store";
    public static final String EMITTER_NOT_REGISTERED_INSTRUCTION = "emitter.high_level.error.missing_instruction_emitter";



    public static final String FUNCTION_CLASS_LOAD_FAILED = "function.load.failed.class";
    public static final String FUNCTION_LOAD_FAILED = "function.load.failed";



    public static final String FUNCTION_EXECUTION_FAILED = "function.execute.failed";



    public static final String COMPILER_UNCAUGHT_EXCEPTION = "compiler.uncaught_exception";

}
