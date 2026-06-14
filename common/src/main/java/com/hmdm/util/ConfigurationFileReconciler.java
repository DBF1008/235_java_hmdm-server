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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * <p>The single reconciliation rule for the link rows that attach files to a configuration.</p>
 *
 * <p>Reconciling the set of files attached to a configuration was previously done in several inconsistent ways &mdash;
 * delete-all-then-reinsert in {@code ConfigurationDAO}, an ad-hoc add/remove diff in {@code UploadedFileDAO}, and
 * single-row inserts in {@code ConfigurationFileDAO}. The divergence made it easy to leave a stale link behind, so a
 * deleted file would keep being referenced by the device update manifest. This class states the rule once:</p>
 *
 * <ul>
 *     <li>a file that is <em>selected</em> but not yet <em>linked</em> must be added;</li>
 *     <li>a file that is <em>linked</em> but no longer <em>selected</em> must be removed;</li>
 *     <li>a file that is both linked and selected is kept as-is;</li>
 *     <li>a file that is neither is ignored.</li>
 * </ul>
 */
public final class ConfigurationFileReconciler {

    /**
     * <p>The outcome of a reconciliation: the items to add and the items to remove. Items that should be kept or
     * ignored appear in neither list.</p>
     *
     * @param <T> the type describing a desired/existing file attachment.
     */
    public static final class Plan<T> {
        private final List<T> toAdd;
        private final List<T> toRemove;

        private Plan(List<T> toAdd, List<T> toRemove) {
            this.toAdd = Collections.unmodifiableList(toAdd);
            this.toRemove = Collections.unmodifiableList(toRemove);
        }

        public List<T> getToAdd() {
            return toAdd;
        }

        public List<T> getToRemove() {
            return toRemove;
        }
    }

    /**
     * <p>Constructs new <code>ConfigurationFileReconciler</code> instance. This implementation does nothing.</p>
     */
    private ConfigurationFileReconciler() {
    }

    /**
     * <p>Computes the add/remove plan for a set of file attachments.</p>
     *
     * @param items the file attachments to reconcile (typically the full set submitted by the UI); may be {@code null}.
     * @param linked tells whether an attachment already exists as a link row in the database.
     * @param selected tells whether an attachment is desired (should be present on the device).
     * @param <T> the type describing a file attachment.
     * @return a plan listing the attachments to add and to remove; never {@code null}.
     */
    public static <T> Plan<T> reconcile(Collection<T> items, Predicate<T> linked, Predicate<T> selected) {
        final List<T> toAdd = new ArrayList<>();
        final List<T> toRemove = new ArrayList<>();
        if (items != null) {
            for (T item : items) {
                final boolean isLinked = linked.test(item);
                final boolean isSelected = selected.test(item);
                if (isSelected && !isLinked) {
                    toAdd.add(item);
                } else if (!isSelected && isLinked) {
                    toRemove.add(item);
                }
            }
        }
        return new Plan<>(toAdd, toRemove);
    }
}
