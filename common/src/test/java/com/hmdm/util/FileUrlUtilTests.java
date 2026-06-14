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

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * <p>A test suite for {@link FileUrlUtil}.</p>
 *
 * <p>These tests pin down the download semantics of the update/configuration-file publishing chain: the exact URL a
 * device is told to download and the inverse mapping the server uses to locate the file on disk. They are the
 * regression guard for the <em>replace</em> scenario &mdash; replacing a stored file must change the URL devices see, so
 * that nobody keeps downloading the stale file.</p>
 */
public class FileUrlUtilTests {

    private static final String BASE = "https://mdm.example.com";

    @Test
    public void testBuildUrlWithCustomerDir() {
        Assert.assertEquals("https://mdm.example.com/files/cust1/app.apk",
                FileUrlUtil.buildFileUrl(BASE, "cust1", "app.apk"));
    }

    @Test
    public void testBuildUrlWithoutCustomerDir() {
        // Both null and empty customer dir must collapse to the default (single-tenant) area.
        Assert.assertEquals("https://mdm.example.com/files/app.apk",
                FileUrlUtil.buildFileUrl(BASE, null, "app.apk"));
        Assert.assertEquals("https://mdm.example.com/files/app.apk",
                FileUrlUtil.buildFileUrl(BASE, "", "app.apk"));
    }

    @Test
    public void testBuildUrlWithSubdirectory() {
        Assert.assertEquals("https://mdm.example.com/files/cust1/sub/dir/config.xml",
                FileUrlUtil.buildFileUrl(BASE, "cust1", "sub/dir/config.xml"));
    }

    @Test
    public void testBuildUrlNormalizesLocalSeparator() {
        // A path assembled with the local file separator must still produce a forward-slashed URL.
        final String localPath = "sub" + File.separator + "dir" + File.separator + "config.xml";
        Assert.assertEquals("https://mdm.example.com/files/cust1/sub/dir/config.xml",
                FileUrlUtil.buildFileUrl(BASE, "cust1", localPath));
    }

    @Test
    public void testBuildUrlNullPath() {
        Assert.assertEquals("https://mdm.example.com/files/cust1/",
                FileUrlUtil.buildFileUrl(BASE, "cust1", null));
    }

    @Test
    public void testReplaceChangesUrl() {
        // Replace scenario: the same logical slot now points to a different stored file => different download URL.
        final String oldUrl = FileUrlUtil.buildFileUrl(BASE, "cust1", "myapp-1.0.apk");
        final String newUrl = FileUrlUtil.buildFileUrl(BASE, "cust1", "myapp-2.0.apk");
        Assert.assertNotEquals("Replacing the stored file must change the device-visible URL", oldUrl, newUrl);

        // ...but the same stored file always resolves to the same URL (no spurious churn).
        Assert.assertEquals(oldUrl, FileUrlUtil.buildFileUrl(BASE, "cust1", "myapp-1.0.apk"));
    }

    @Test
    public void testUrlToRelativePathInverse() {
        final String url = FileUrlUtil.buildFileUrl(BASE, "cust1", "sub/dir/config.xml");
        final String expected = "sub/dir/config.xml".replace("/", File.separator);
        Assert.assertEquals(expected, FileUrlUtil.urlToRelativePath(BASE, "cust1", url));
    }

    @Test
    public void testUrlToRelativePathInverseNoCustomerDir() {
        final String url = FileUrlUtil.buildFileUrl(BASE, null, "app.apk");
        Assert.assertEquals("app.apk", FileUrlUtil.urlToRelativePath(BASE, null, url));
    }

    @Test
    public void testRoundTrip() {
        // build -> parse -> build must be stable for any stored path.
        final String relative = "a" + File.separator + "b" + File.separator + "file.bin";
        final String url = FileUrlUtil.buildFileUrl(BASE, "tenant", relative);
        final String parsed = FileUrlUtil.urlToRelativePath(BASE, "tenant", url);
        Assert.assertEquals(relative, parsed);
        Assert.assertEquals(url, FileUrlUtil.buildFileUrl(BASE, "tenant", parsed));
    }

    @Test
    public void testUrlToRelativePathForeignUrlReturnsNull() {
        // A URL that does not point into this customer's files area must not be mistaken for a local file.
        Assert.assertNull(FileUrlUtil.urlToRelativePath(BASE, "cust1", "https://other.host/files/cust1/app.apk"));
        Assert.assertNull(FileUrlUtil.urlToRelativePath(BASE, "cust1", "https://mdm.example.com/files/cust2/app.apk"));
        Assert.assertNull(FileUrlUtil.urlToRelativePath(BASE, "cust1", "ftp://anywhere/app.apk"));
    }

    @Test
    public void testUrlToRelativePathNullUrl() {
        Assert.assertNull(FileUrlUtil.urlToRelativePath(BASE, "cust1", null));
    }

    @Test
    public void testPrefix() {
        Assert.assertEquals("https://mdm.example.com/files/cust1/", FileUrlUtil.fileUrlPrefix(BASE, "cust1"));
        Assert.assertEquals("https://mdm.example.com/files/", FileUrlUtil.fileUrlPrefix(BASE, null));
        Assert.assertEquals("https://mdm.example.com/files/", FileUrlUtil.fileUrlPrefix(BASE, ""));
    }
}
