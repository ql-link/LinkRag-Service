package com.qingluo.link.components.oss.enums;

/**
 * Storage visibility for uploaded objects.
 *
 * <p>PUBLIC / PRIVATE — general-purpose public/private buckets (avatar, chatImage, etc.).
 * BLOG — dedicated blog bucket (tolink-blog); always returns a full public URL since the bucket
 * allows anonymous read.</p>
 */
public enum OssSavePlaceEnum {
    PUBLIC,
    PRIVATE,
    BLOG
}
