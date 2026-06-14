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
import java.util.function.Predicate;

/**
 * <p>A test suite for {@link ConfigurationFileReconciler}.</p>
 *
 * <p>This is the regression guard for the <em>delete</em> scenario of the configuration-file publishing chain: when a
 * file is detached from a configuration the reconciler must schedule its link row for removal so the device update
 * manifest stops referencing it, while files that are kept must not be churned or duplicated.</p>
 */
public class ConfigurationFileReconcilerTests {

    /** Minimal stand-in for a file/configuration attachment: linked when {@code id != null}, desired when selected. */
    private static final class Link {
        final String name;
        final Integer id;
        final boolean selected;

        Link(String name, Integer id, boolean selected) {
            this.name = name;
            this.id = id;
            this.selected = selected;
        }
    }

    private static final Predicate<Link> LINKED = link -> link.id != null;
    private static final Predicate<Link> SELECTED = link -> link.selected;

    @Test
    public void testExistingAndUnselectedIsRemoved() {
        Link link = new Link("a", 1, false);
        ConfigurationFileReconciler.Plan<Link> plan =
                ConfigurationFileReconciler.reconcile(Collections.singletonList(link), LINKED, SELECTED);
        Assert.assertEquals(Collections.singletonList(link), plan.getToRemove());
        Assert.assertTrue(plan.getToAdd().isEmpty());
    }

    @Test
    public void testNewAndSelectedIsAdded() {
        Link link = new Link("a", null, true);
        ConfigurationFileReconciler.Plan<Link> plan =
                ConfigurationFileReconciler.reconcile(Collections.singletonList(link), LINKED, SELECTED);
        Assert.assertEquals(Collections.singletonList(link), plan.getToAdd());
        Assert.assertTrue(plan.getToRemove().isEmpty());
    }

    @Test
    public void testExistingAndSelectedIsKept() {
        // Already linked and still wanted => neither added nor removed (no churn).
        Link link = new Link("a", 1, true);
        ConfigurationFileReconciler.Plan<Link> plan =
                ConfigurationFileReconciler.reconcile(Collections.singletonList(link), LINKED, SELECTED);
        Assert.assertTrue(plan.getToAdd().isEmpty());
        Assert.assertTrue(plan.getToRemove().isEmpty());
    }

    @Test
    public void testNewAndUnselectedIsIgnored() {
        Link link = new Link("a", null, false);
        ConfigurationFileReconciler.Plan<Link> plan =
                ConfigurationFileReconciler.reconcile(Collections.singletonList(link), LINKED, SELECTED);
        Assert.assertTrue(plan.getToAdd().isEmpty());
        Assert.assertTrue(plan.getToRemove().isEmpty());
    }

    @Test
    public void testDeleteScenario() {
        // A configuration is saved with: keepA (kept), removeB (detached), addC (newly attached), noiseD (untouched).
        Link keepA = new Link("keepA", 1, true);
        Link removeB = new Link("removeB", 2, false);
        Link addC = new Link("addC", null, true);
        Link noiseD = new Link("noiseD", null, false);

        ConfigurationFileReconciler.Plan<Link> plan = ConfigurationFileReconciler.reconcile(
                Arrays.asList(keepA, removeB, addC, noiseD), LINKED, SELECTED);

        // The detached file is the one (and only one) scheduled for removal => no dangling reference remains.
        Assert.assertEquals(Collections.singletonList(removeB), plan.getToRemove());
        // The newly attached file is the one (and only one) scheduled for insertion.
        Assert.assertEquals(Collections.singletonList(addC), plan.getToAdd());
        // Kept and untouched files are not churned.
        Assert.assertFalse(plan.getToAdd().contains(keepA));
        Assert.assertFalse(plan.getToRemove().contains(keepA));
        Assert.assertFalse(plan.getToAdd().contains(noiseD));
        Assert.assertFalse(plan.getToRemove().contains(noiseD));
    }

    @Test
    public void testNullAndEmptyInput() {
        ConfigurationFileReconciler.Plan<Link> nullPlan = ConfigurationFileReconciler.reconcile(null, LINKED, SELECTED);
        Assert.assertTrue(nullPlan.getToAdd().isEmpty());
        Assert.assertTrue(nullPlan.getToRemove().isEmpty());

        ConfigurationFileReconciler.Plan<Link> emptyPlan =
                ConfigurationFileReconciler.reconcile(Collections.<Link>emptyList(), LINKED, SELECTED);
        Assert.assertTrue(emptyPlan.getToAdd().isEmpty());
        Assert.assertTrue(emptyPlan.getToRemove().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPlanListsAreImmutable() {
        ConfigurationFileReconciler.Plan<Link> plan =
                ConfigurationFileReconciler.reconcile(Collections.<Link>emptyList(), LINKED, SELECTED);
        plan.getToAdd().add(new Link("x", null, true));
    }
}
