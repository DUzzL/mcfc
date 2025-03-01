package dev.epicpix.minecraftfunctioncompiler.il;

@SuppressWarnings("unused")
public record LocationData<T>(
        Integer local,
        Object object
) {
    public static <T> LocationData<T> ofLocal(int local) {
        return new LocationData<>(local, null);
    }

    public static LocationData<String> ofString(String string) {
        return new LocationData<>(null, string);
    }

    public static LocationData<Integer> ofBoolean(boolean bool) {
        return ofInteger(bool ? 1 : 0);
    }

    public static LocationData<Integer> ofInteger(int integer) {
        return new LocationData<>(null, integer);
    }

    public static LocationData<Float> ofFloat(float floatValue) {
        return new LocationData<>(null, floatValue);
    }

    public static LocationData<Double> ofDouble(double doubleValue) {
        return new LocationData<>(null, doubleValue);
    }

    public static LocationData<Double> ofDoubleOrNull(Double doubleValue) {
        return doubleValue != null ? new LocationData<>(null, doubleValue) : null;
    }

    @SuppressWarnings("unchecked")
    public <R> LocationData<R> forceCast() {
        return (LocationData<R>) this;
    }

    public String toString() {
        if(local != null) {
            return "local<" + local + ">";
        }else if(object instanceof String str) {
            return "\"" + str
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\\\"") + "\"";
        }else if(object instanceof Integer integer) {
            return integer.toString();
        }else if(object instanceof Float floatValue) {
            return floatValue.toString();
        }else if(object instanceof Double doubleValue) {
            return doubleValue.toString();
        }else {
            return "INVALID<" + object + ">";
        }
    }
}