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

package com.hmdm.persistence;

import com.hmdm.persistence.domain.CustomerData;
import com.hmdm.security.SecurityContext;
import com.hmdm.security.SecurityException;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>A base class for DAO implementations managing the domain objects linked to some other types of domain objects.
 * Provides the convenience methods for operating in security context.</p>
 *
 * @param <T> - a type of the main domain object maintained by the DAO implementation.
 * @param <L> - a type of the domain objects linked to main maintained by the DAO implementation.
 * @author isv
 */
public abstract class AbstractLinkedDAO<T extends CustomerData, L extends CustomerData> extends AbstractDAO<T> {

    /**
     * <p>Constructs new <code>AbstractLinkedDAO</code> instance. This implementation does nothing.</p>
     */
    protected AbstractLinkedDAO() {
    }

    /**
     * <p>Gets the list of records linked to specified record. Uses specified list retrieval logic for getting the
     * linked records data.</p>
     *
     * @param recordId           an ID of a record to get the linked records for.
     * @param findByIdLogic      a logic to be used for finding the required record by ID.
     * @param listRetrievalLogic a logic to be used for records data retrieval.
     * @param exceptionProvider  a provider for exception to be thrown in case the access to specified record is denied.
     * @return a list of records.
     */
    protected List<L> getLinkedList(int recordId,
                                    Function<Integer, T> findByIdLogic,
                                    Function<Integer, List<L>> listRetrievalLogic,
                                    Function<Integer, SecurityException> exceptionProvider) {

        T record = findByIdLogic.apply(recordId);

        if (record == null) {
            throw exceptionProvider.apply(recordId);
        }

        SecurityContext context = SecurityContext.get();
        if (!context.canAccess(record)) {
            throw exceptionProvider.apply(recordId);
        }

        // Common records are shared, so their linked data is read in the scope of the current user's
        // customer; otherwise the data is read in the scope of the record's own customer.
        int scopeCustomerId = record.isCommon()
                ? context.getCurrentCustomerId().orElseThrow(() -> exceptionProvider.apply(recordId))
                : record.getCustomerId();
        return listRetrievalLogic.apply(scopeCustomerId);
    }

    /**
     * <p>Updates the data records linked to specified record. If requested record is found then uses specified linked
     * data update logic for updating the linked records data within current security context.</p>
     *
     * @param recordId              an ID of a record to update the linked records for.
     * @param findByIdLogic         a logic to be used for finding the required record by ID.
     * @param linkedDataUpdateLogic a logic to be used for updating the linked data records.
     * @param exceptionProvider     a provider for exception to be thrown in case the access to specified record is denied.
     */
    protected void updateLinkedData(int recordId,
                                    Function<Integer, T> findByIdLogic,
                                    Consumer<T> linkedDataUpdateLogic,
                                    Function<Integer, SecurityException> exceptionProvider) {

        T record = findByIdLogic.apply(recordId);

        if (record == null) {
            throw exceptionProvider.apply(recordId);
        }

        if (SecurityContext.get().canAccess(record)) {
            linkedDataUpdateLogic.accept(record);
        } else {
            throw exceptionProvider.apply(recordId);
        }
    }
}
