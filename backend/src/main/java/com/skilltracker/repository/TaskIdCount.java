package com.skilltracker.repository;

/** Projection for per-task aggregate counts used by the task list view. */
public interface TaskIdCount {

    Integer getTaskId();

    long getTotal();
}
