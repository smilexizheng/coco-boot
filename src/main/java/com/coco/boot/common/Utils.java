package com.coco.boot.common;

import java.util.UUID;

public class Utils {

    public static String generateUUID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString().replace("-", "");
    }
}
