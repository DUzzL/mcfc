package dev.epicpix.minecraftfunctioncompiler;

public final class CommonConstants {
    private CommonConstants() {}

    public static final boolean DEBUG_DUMP_CLASS_FILES = false;
    public static final boolean DEBUG_DUMP_INSTRUCTIONS = false;
    public static final boolean DEBUG_GENERATE_GRAPH = false;
    public static final boolean DEBUG_PRINT_TIMINGS = false;
    public static final boolean AUTO_SWITCH_FUNCTIONS = true;
    public static final boolean ALLOW_EXTERNAL_DATA_ACCESS = false;
    public static final boolean GENERATE_REGISTRY_FILE = false;
    public static final boolean BLOCKING_COMPILATION = false;
    public static final boolean ENABLE_REPORTING = true;
    public static final boolean INCLUDE_SIMPLE_COMPILER = false;
    public static final String DATA_CLASS = "mcfc/function/data";

    public static final String EMIT_SERVER_FUNCTION_MANAGER_NAME = "_ServerFunctionManager";
    public static final String RUN_RESULT_FIELD = "_FunctionResult";

}
