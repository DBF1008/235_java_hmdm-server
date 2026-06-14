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

import java.io.File;

/**
 * <p>The single source of truth for translating between a stored file and the download URL that is exposed to
 * devices.</p>
 *
 * <p>Historically the {@code baseUrl + "/files/" + [customerDir + "/"] + relativePath} layout (and its inverse) was
 * re-implemented in several places ({@link FileUtil#createFileUrl}, {@code UploadedFile#getUrl},
 * {@code ApplicationDAO#translateAppVersionUrl}, {@link FileUtil#translateURLToLocalFilePath}) with subtle differences
 * &mdash; some normalized the path separator, some did not &mdash; which made it easy for the management side and the
 * device side to disagree on where a file lives. Centralizing the layout here keeps the URL a device downloads and the
 * path the server reads strictly in sync.</p>
 */
public final class FileUrlUtil {

    /**
     * <p>Constructs new <code>FileUrlUtil</code> instance. This implementation does nothing.</p>
     */
    private FileUrlUtil() {
    }

    /**
     * <p>Builds the common prefix shared by every download URL: the {@code /files/} mount point optionally followed by
     * the per-customer sub-directory.</p>
     *
     * @param baseUrl the externally visible base URL of the server.
     * @param customerDir the customer's files sub-directory, or {@code null}/empty for the default (single-tenant) area.
     * @return the URL prefix, always terminated so that a relative path can be appended directly.
     */
    public static String fileUrlPrefix(String baseUrl, String customerDir) {
        String prefix = baseUrl + "/files/";
        if (customerDir != null && !customerDir.isEmpty()) {
            prefix += customerDir + "/";
        }
        return prefix;
    }

    /**
     * <p>Builds the download URL exposed to a device for a file stored at the specified path relative to the customer's
     * files area.</p>
     *
     * <p>The local file-system separator is normalized to {@code '/'} so the produced URL is identical regardless of the
     * platform the file path was assembled on.</p>
     *
     * @param baseUrl the externally visible base URL of the server.
     * @param customerDir the customer's files sub-directory, or {@code null}/empty for the default area.
     * @param relativePath the path of the file relative to the customer's files area (may contain sub-directories).
     * @return the absolute download URL for the file.
     */
    public static String buildFileUrl(String baseUrl, String customerDir, String relativePath) {
        final String normalized = relativePath == null ? "" : relativePath.replace(File.separator, "/");
        return fileUrlPrefix(baseUrl, customerDir) + normalized;
    }

    /**
     * <p>The inverse of {@link #buildFileUrl(String, String, String)}: extracts the path of a file relative to the
     * customer's files area from a download URL, or returns {@code null} if the URL does not point into that area.</p>
     *
     * <p>The returned path uses the local file-system separator so it can be resolved against the files directory.</p>
     *
     * @param baseUrl the externally visible base URL of the server.
     * @param customerDir the customer's files sub-directory, or {@code null}/empty for the default area.
     * @param url the download URL to translate.
     * @return the relative local path, or {@code null} if the URL does not reference this customer's files area.
     */
    public static String urlToRelativePath(String baseUrl, String customerDir, String url) {
        if (url == null) {
            return null;
        }
        final String prefix = fileUrlPrefix(baseUrl, customerDir);
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length()).replace("/", File.separator);
        }
        return null;
    }
}
