package com.skilltracker.integration;

import java.util.List;

/** Write side of the ML integration, kept behind an interface so suggestions can be stubbed. */
public interface TaskSuggestionSource {

    RawTaskSuggestion suggestFields(
            String title, String description, List<SuggestionCandidate> labels, List<SuggestionCandidate> skills);
}
