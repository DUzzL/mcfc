package dev.epicpix.minecraftfunctioncompiler.il;

@SuppressWarnings("unused")
public record LocationData<T>(
        Integer local,
        String string,
        Integer integer,
        Double doubleValue
) {
    public static <T> LocationData<T> ofLocal(int local) {
        return new LocationData<>(local, null, null, null);
    }

    public static LocationData<String> ofString(String string) {
        return new LocationData<>(null, string, null, null);
    }

    public static LocationData<Integer> ofInteger(int integer) {
        return new LocationData<>(null, null, integer, null);
    }

    public static LocationData<Double> ofDouble(double doubleValue) {
        return new LocationData<>(null, null, null, doubleValue);
    }

    @SuppressWarnings("unchecked")
    public LocationData<Object> lower() {
        return (LocationData<Object>) this;
    }

    @SuppressWarnings("unchecked")
    public <R> LocationData<R> forceCast() {
        return (LocationData<R>) this;
    }

    public String toString() {
        if(local != null) return "local<" + local + ">";
        if(string != null) return "\"" + string.translateEscapes() + "\"";
        if(integer != null) return integer.toString();
        if(doubleValue != null) return doubleValue.toString();
        return "unknown";
    }
}