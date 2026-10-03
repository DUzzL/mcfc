package dev.epicpix.minecraftfunctioncompiler.emitter;

// Used in bytecode instructions, to not insert too much code at once
@SuppressWarnings("unused")
public class ExtensionFunctions {

    /** Element of an array-backed data-class field (the emitter has no array-load instruction). */
    public static Object arrayElement(Object[] array, int index) {
        return array[index];
    }

    /** Brigadier readInt: only [0-9-] may appear (e.g. "+5" is rejected), then Integer.parseInt. */
    public static boolean isValidInteger(String value) {
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if((c < '0' || c > '9') && c != '-') return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        }catch(NumberFormatException ignored) {}
        return false;
    }

    /** Macro score holder name that vanilla resolves as a plain name: not a selector and not UUID-shaped
     * (vanilla resolves UUID-shaped names to entities). */
    public static boolean isPlainScoreHolderName(String value) {
        if(value.isEmpty() || value.charAt(0) == '@') return false;
        if(value.charAt(0) == '#' || value.indexOf('-') < 0) return true;
        try {
            java.util.UUID.fromString(value);
            return false;
        }catch(IllegalArgumentException ignored) {
            return true;
        }
    }

    public static boolean isValidCommandFloat(String value) {
        if(value.isEmpty()) return false;
        int index = 0;
        while(index < value.length()) {
            char peeked = value.charAt(index);
            if((peeked >= '0' && peeked <= '9') || peeked == '-' || peeked == '.') {
                index++;
            }else {
                break;
            }
        }
        if(index != value.length()) return false;
        try {
            Float.parseFloat(value);
            return true;
        }catch(NumberFormatException ignored) {}
        return false;
    }

    public static boolean isValidCommandDouble(String value) {
        if(value.isEmpty()) return false;
        int index = 0;
        while(index < value.length()) {
            char peeked = value.charAt(index);
            if((peeked >= '0' && peeked <= '9') || peeked == '-' || peeked == '.') {
                index++;
            }else {
                break;
            }
        }
        if(index != value.length()) return false;
        try {
            Double.parseDouble(value);
            return true;
        }catch(NumberFormatException ignored) {}
        return false;
    }

}
