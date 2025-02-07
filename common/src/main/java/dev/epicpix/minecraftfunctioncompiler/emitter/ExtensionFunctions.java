package dev.epicpix.minecraftfunctioncompiler.emitter;

// Used in bytecode instructions, to not insert too much code at once
@SuppressWarnings("unused")
public class ExtensionFunctions {

    public static boolean isValidInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        }catch(NumberFormatException ignored) {}
        return false;
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
