package br.com.rbwone.web;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Resource allowlist and current Android grants; no persisted permission cache. */
final class WebMediaPermissions {
    static List<String> missing(String[] resources, Predicate<String> granted) {
        if (resources == null || resources.length == 0) throw new IllegalArgumentException("Empty resources");
        List<String> missing = new ArrayList<>();
        for (String resource : resources) {
            String permission;
            if ("android.webkit.resource.AUDIO_CAPTURE".equals(resource)) permission = "android.permission.RECORD_AUDIO";
            else if ("android.webkit.resource.VIDEO_CAPTURE".equals(resource)) permission = "android.permission.CAMERA";
            else throw new IllegalArgumentException("Unsupported resource");
            if (!granted.test(permission) && !missing.contains(permission)) missing.add(permission);
        }
        return missing;
    }
}
