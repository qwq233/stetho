package android.os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SystemProperties {
    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @return an empty string if the {@code key} isn't found
     */
    @NonNull
    public static String get(@NonNull String key) {
        throw new RuntimeException("");
    }

    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @param def the default value in case the property is not set or empty
     * @return if the {@code key} isn't found, return {@code def} if it isn't null, or an empty
     * string otherwise
     */
    @NonNull
    public static String get(@NonNull String key, @Nullable String def) {
        throw new RuntimeException("");
    }
}
