package com.skilltracker.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Flushes pending writes and detaches the persistence context.
 *
 * <p>Rows inserted earlier in a transaction are still managed as the bare objects that were built in
 * memory, so a read-back would reuse them and see their associations unset. Detaching first makes
 * the follow-up query load complete rows from the database.
 */
@Component
public class ReadBack {

    private final EntityManager entityManager;

    public ReadBack(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }
}
