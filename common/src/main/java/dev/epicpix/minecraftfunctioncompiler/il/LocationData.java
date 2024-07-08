package dev.epicpix.minecraftfunctioncompiler.il;

@SuppressWarnings("unused")
public record LocationData<T>(
        Integer local,
        String string,
        Integer integer
) {
    public static <T> LocationData<T> ofLocal(int local) {
        return new LocationData<>(local, null, null);
    }

    public static LocationData<String> ofString(String string) {
        return new LocationData<>(null, string, null);
    }

    public static LocationData<Integer> ofInteger(int integer) {
        return new LocationData<>(null, null, integer);
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
        return (local != null ? "local " : "")
                + (local != null ? local : "")
                + (string != null ? string.replace("\\", "\\\\").replace("\n", "\\n") : "")
                + (integer != null ? integer : "");
    }
}