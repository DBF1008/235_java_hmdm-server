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

import java.util.Arrays;
import java.util.Collections;

/**
 * <p>A test suite for {@link ApplicationUtil} class.</p>
 *
 * @author isv
 */
public class ApplicationUtilTests {

    /**
     * <p>Constructs new <code>ApplicationUtilTests</code> instance. This implementation does nothing.</p>
     */
    public ApplicationUtilTests() {
    }

    @Test
    public void testEquality() {
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("1.0", "1.0"));
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("1.0", "1.00"));
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("1.01", "1.1"));
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("1.01", "1.00001"));
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("1", "1"));
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("0", "0"));
        Assert.assertEquals("Should be equal", 0, ApplicationUtil.compareVersions("0.abc", "0.xyz"));
    }

    @Test
    public void testLess() {
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.1", "2.0"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.3", "1.11"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.03", "1.11"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.11", "1.033"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.1", "1.1.12"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.1.11", "1.1.12"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.1.12", "1.1.110"));
        Assert.assertEquals("Should be less", -1, ApplicationUtil.compareVersions("1.a.12", "1.1.110"));
    }

    @Test
    public void testGreater() {
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("2.0", "1.1"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.11", "1.3"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.11", "1.03"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.033", "1.11"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.1.12", "1.1"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.1.12", "1.1.11"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.1.110", "1.1.12"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.1.110", "1.a.12"));
        Assert.assertEquals("Should be greater", 1, ApplicationUtil.compareVersions("1.03", "1.2.12"));
    }

    @Test
    public void testNormalization() {
        Assert.assertEquals("Incorrect version normalization", "12.334.78", ApplicationUtil.normalizeVersion("a12.334tyz.78x"));
        Assert.assertEquals("Incorrect version normalization", "1.03", ApplicationUtil.normalizeVersion("1.03"));
        Assert.assertEquals("Incorrect version normalization", "1.03.011", ApplicationUtil.normalizeVersion("1.03.011"));
        Assert.assertEquals("Incorrect version normalization", "1.03", ApplicationUtil.normalizeVersion("1.03-a"));
        Assert.assertEquals("Incorrect version normalization", "1.03", ApplicationUtil.normalizeVersion("1.03a"));
        Assert.assertEquals("Incorrect version normalization", "1.03", ApplicationUtil.normalizeVersion("v1.03"));
        Assert.assertEquals("Incorrect version normalization", "103", ApplicationUtil.normalizeVersion("v103"));
        Assert.assertEquals("Incorrect version normalization", "", ApplicationUtil.normalizeVersion("aaaa"));
        Assert.assertEquals("Incorrect version normalization", "1", ApplicationUtil.normalizeVersion("aaaa1"));
        Assert.assertEquals("Incorrect version normalization", "1.", ApplicationUtil.normalizeVersion("1."));
    }

    // --- Version metadata / device-visible version semantics (replace & rollback regression guards) ---

    @Test
    public void testEffectiveVersionId() {
        // A configuration pinned to a specific version ships that version...
        Assert.assertEquals(Integer.valueOf(5), ApplicationUtil.effectiveVersionId(5, 9));
        // ...otherwise it falls back to the application's latest version.
        Assert.assertEquals(Integer.valueOf(9), ApplicationUtil.effectiveVersionId(null, 9));
        Assert.assertNull(ApplicationUtil.effectiveVersionId(null, null));
    }

    @Test
    public void testIsOutdated() {
        // The shipped version is behind the latest one.
        Assert.assertTrue(ApplicationUtil.isOutdated(9, 5));
        // The shipped version is the latest one.
        Assert.assertFalse(ApplicationUtil.isOutdated(9, 9));
        // Nothing to compare against => never outdated (mirrors SQL NULL handling).
        Assert.assertFalse(ApplicationUtil.isOutdated(null, 5));
        Assert.assertFalse(ApplicationUtil.isOutdated(9, null));
        Assert.assertFalse(ApplicationUtil.isOutdated(null, null));
    }

    @Test
    public void testSelectLatestVersion() {
        Assert.assertEquals("2.0", ApplicationUtil.selectLatestVersion(Arrays.asList("1.0", "2.0", "1.1")));
        // Numeric (not lexicographic) ordering.
        Assert.assertEquals("1.11", ApplicationUtil.selectLatestVersion(Arrays.asList("1.1", "1.11", "1.3")));
        Assert.assertEquals("1.0", ApplicationUtil.selectLatestVersion(Collections.singletonList("1.0")));
        // Null entries are ignored.
        Assert.assertEquals("2.0", ApplicationUtil.selectLatestVersion(Arrays.asList("1.0", null, "2.0")));
        Assert.assertNull(ApplicationUtil.selectLatestVersion(Collections.<String>emptyList()));
        Assert.assertNull(ApplicationUtil.selectLatestVersion(null));
    }

    @Test
    public void testSelectPrecedingVersion() {
        // Rollback target: the greatest version strictly older than the current one.
        Assert.assertEquals("2.0", ApplicationUtil.selectPrecedingVersion("3.0", Arrays.asList("1.0", "2.0", "3.0")));
        Assert.assertEquals("1.0", ApplicationUtil.selectPrecedingVersion("2.0", Arrays.asList("1.0", "2.0", "3.0")));
        // No older version exists.
        Assert.assertNull(ApplicationUtil.selectPrecedingVersion("1.0", Arrays.asList("1.0", "2.0", "3.0")));
        // The reference version need not itself be present in the list.
        Assert.assertEquals("2.0", ApplicationUtil.selectPrecedingVersion("2.5", Arrays.asList("1.0", "2.0", "3.0")));
        // Unsorted input and null entries are handled.
        Assert.assertEquals("1.11", ApplicationUtil.selectPrecedingVersion("2.0", Arrays.asList("2.0", null, "1.3", "1.11", "1.1")));
    }

    @Test
    public void testRollbackScenario() {
        // An application ships version 2.0 (latest) and has older versions 1.0 and 1.1 available.
        // When 2.0 is withdrawn, recomputing the latest version and resolving the preceding version must agree
        // on the rollback target (1.1) so devices roll back to it rather than keep referencing the removed 2.0.
        Assert.assertEquals("1.1", ApplicationUtil.selectLatestVersion(Arrays.asList("1.0", "1.1")));
        Assert.assertEquals("1.1", ApplicationUtil.selectPrecedingVersion("2.0", Arrays.asList("1.0", "1.1", "2.0")));
    }
}
