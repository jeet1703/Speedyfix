package com.speedyfixautosales.store.catalog;

/**
 * Sample photos from Unsplash (Unsplash License). Replace with the client's own
 * photos of each actual part before launch.
 */
final class Images {

    private Images() {
    }

    static String unsplash(String photoId, int width, int height) {
        return "https://images.unsplash.com/photo-" + photoId
                + "?auto=format&fit=crop&w=" + width + "&h=" + height + "&q=70";
    }

    /** True for the Unsplash sample photos, which don't count as a real photo of the part. */
    static boolean isSample(String url) {
        return url != null && url.startsWith("https://images.unsplash.com/");
    }
}
