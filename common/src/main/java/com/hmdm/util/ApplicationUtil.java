/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * <p>An utility class for manipulating with application data.</p>
 *
 * @author isv
 */
public final class ApplicationUtil {

    /**
     * <p>Constructs new <code>ApplicationUtil</code> instance. This implementation does nothing.</p>
     */
    private ApplicationUtil() {
    }

    /**
     * <p>Compares the specified application versions.</p>
     *
     * @param version1 a first application version to compare.
     * @param version2 a second application version to compare.
     * @return a comparison result. 0 - if versions are equal, -1 - if first version is considered to be less than the
     *         second one; 1 - otherwise.
     */
    public static int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null) {
            return 0;
        }
        if (version1 != null && version2 == null) {
            return 1;
        }
        if (version1 == null) {
            return -1;
        }

        final String[] split1 = normalizeVersion(version1).split("\\.");
        final String[] split2 = normalizeVersion(version2).split("\\.");

        final StringBuilder b1 = new StringBuilder();
        final StringBuilder b2 = new StringBuilder();
        final int N = Math.max(split1.length, split2.length);
        for (int i = 0; i < N; i++) {
            String s1 = i < split1.length ? split1[i] : "";
            if (s1.isEmpty()) {
                s1 = "0";
            }

            b1.append(StringUtils.leftPad(s1, 10, "0"));

            String s2 = i < split2.length ? split2[i] : "";
            if (s2.isEmpty()) {
                s2 = "0";
            }
            b2.append(StringUtils.leftPad(s2, 10, "0"));
        }

        int result = b1.toString().compareTo(b2.toString());
        if (result < 0) {
            return -1;
        } else if (result > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * <p>Normalizes the specified string for comparison. Strips off all non-digit and non-dot characters from it.</p>
     *
     * @param version a version text to normalize.
     * @return a normalized version text.
     */
    public static String normalizeVersion(String version) {
        return (version == null ? "" : version).replaceAll("[^\\d.]", "");
    }

    /**
     * <p>Resolves the application version a configuration actually ships to a device.</p>
     *
     * <p>This is the canonical Java definition of the {@code COALESCE(usedVersionId, latestVersion)} rule that the
     * persistence layer applies when linking an application to a configuration: a configuration delivers the version it
     * was explicitly pinned to ({@code usedVersionId}), falling back to the application's most recent version
     * ({@code latestVersion}) when it is not pinned. Keeping this rule in one place stops the management view and the
     * device-facing sync from disagreeing about which version is current.</p>
     *
     * @param usedVersionId the version the configuration is pinned to, or {@code null} if it is not pinned.
     * @param latestVersionId the most recent version of the application, or {@code null} if unknown.
     * @return the id of the version that should be delivered to the device, or {@code null} if neither is known.
     */
    public static Integer effectiveVersionId(Integer usedVersionId, Integer latestVersionId) {
        return usedVersionId != null ? usedVersionId : latestVersionId;
    }

    /**
     * <p>Tells whether the version a configuration ships is behind the application's most recent version.</p>
     *
     * <p>This mirrors the {@code latestVersion <> applicationVersions.id} expression used by the persistence layer,
     * including its NULL handling: if either the latest version or the shipped version is unknown the result is
     * {@code false} (nothing is reported as outdated when there is nothing to compare against).</p>
     *
     * @param latestVersionId the most recent version of the application.
     * @param shippedVersionId the version currently shipped by the configuration.
     * @return {@code true} if a newer version exists than the one being shipped.
     */
    public static boolean isOutdated(Integer latestVersionId, Integer shippedVersionId) {
        return latestVersionId != null && shippedVersionId != null && !latestVersionId.equals(shippedVersionId);
    }

    /**
     * <p>Selects the most recent version text from a collection of version texts, using {@link #compareVersions}.</p>
     *
     * <p>This is the Java counterpart of the {@code recalculateLatestVersion} persistence routine which picks the
     * application version with the highest version-comparison index.</p>
     *
     * @param versions the available version texts (may contain {@code null} entries, which are ignored).
     * @return the greatest version text, or {@code null} if there are no non-null versions.
     */
    public static String selectLatestVersion(Collection<String> versions) {
        if (versions == null) {
            return null;
        }
        String latest = null;
        for (String version : versions) {
            if (version == null) {
                continue;
            }
            if (latest == null || compareVersions(version, latest) > 0) {
                latest = version;
            }
        }
        return latest;
    }

    /**
     * <p>Selects the version immediately preceding the specified one &mdash; the greatest available version that is
     * strictly older than {@code current}. This is the target a rollback falls back to when the current version is
     * withdrawn, and is the Java counterpart of the {@code getPrecedingVersion} persistence routine.</p>
     *
     * @param current the version to roll back from.
     * @param versions the available version texts (may contain {@code null} entries, which are ignored).
     * @return the greatest version strictly less than {@code current}, or {@code null} if there is no older version.
     */
    public static String selectPrecedingVersion(String current, Collection<String> versions) {
        if (versions == null) {
            return null;
        }
        String preceding = null;
        for (String version : versions) {
            if (version == null) {
                continue;
            }
            if (compareVersions(version, current) < 0) {
                if (preceding == null || compareVersions(version, preceding) > 0) {
                    preceding = version;
                }
            }
        }
        return preceding;
    }
}
