package com.skilltracker.service;

import com.skilltracker.domain.Label;
import com.skilltracker.domain.Skill;
import com.skilltracker.dto.LabelResponse;
import com.skilltracker.dto.SkillResponse;
import com.skilltracker.dto.TaskAnalyzeRequest;
import com.skilltracker.dto.TaskFieldSuggestionResponse;
import com.skilltracker.dto.TaskSkillResponse;
import com.skilltracker.exception.ServiceUnavailableException;
import com.skilltracker.integration.RawTaskSuggestion;
import com.skilltracker.integration.SuggestionCandidate;
import com.skilltracker.integration.TaskSuggestionSource;
import com.skilltracker.repository.LabelRepository;
import com.skilltracker.repository.OffsetLimit;
import com.skilltracker.repository.SkillRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a model suggestion into a response that only ever references rows that exist.
 *
 * <p>The model output is untrusted: unknown ids are dropped and every numeric field is clamped to
 * the range the API accepts.
 */
@Service
public class TaskSuggestionService {

    private static final int DEFAULT_DIFFICULTY = 3;
    private static final int SKILL_CANDIDATE_PAGE_SIZE = 1000;

    private final LabelRepository labelRepository;
    private final SkillRepository skillRepository;
    private final Optional<TaskSuggestionSource> source;
    private final Clock clock;

    public TaskSuggestionService(
            LabelRepository labelRepository,
            SkillRepository skillRepository,
            Optional<TaskSuggestionSource> source,
            Clock clock) {
        this.labelRepository = labelRepository;
        this.skillRepository = skillRepository;
        this.source = source;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TaskFieldSuggestionResponse analyze(TaskAnalyzeRequest request) {
        TaskSuggestionSource suggestionSource = source.orElseThrow(
                () -> new ServiceUnavailableException("ML suggestions are unavailable: set OPENROUTER_API_KEY"));

        List<Label> labels = labelRepository.findAllByOrderByNameAsc();
        List<Skill> skills = loadAllSkills();

        RawTaskSuggestion raw = suggestionSource.suggestFields(
                request.title(),
                request.description(),
                labels.stream()
                        .map(label -> new SuggestionCandidate(label.getId(), label.getName()))
                        .toList(),
                skills.stream()
                        .map(skill -> new SuggestionCandidate(skill.getId(), skill.getName()))
                        .toList());

        Map<Integer, Label> labelsById = new LinkedHashMap<>();
        labels.forEach(label -> labelsById.put(label.getId(), label));
        Map<Integer, Skill> skillsById = new LinkedHashMap<>();
        skills.forEach(skill -> skillsById.put(skill.getId(), skill));

        return new TaskFieldSuggestionResponse(
                raw.difficulty() == null ? DEFAULT_DIFFICULTY : clamp(raw.difficulty(), 1, 5),
                deadlineFor(raw.estimatedDays()),
                suggestedLabels(raw, labelsById),
                suggestedSkills(raw, skillsById));
    }

    /**
     * The model has no idea what today is, so it estimates an effort in days and the deadline is
     * derived here.
     */
    private OffsetDateTime deadlineFor(Integer estimatedDays) {
        if (estimatedDays == null) {
            return null;
        }
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).plusDays(clamp(estimatedDays, 1, 365));
    }

    private List<LabelResponse> suggestedLabels(RawTaskSuggestion raw, Map<Integer, Label> labelsById) {
        return raw.labelIds().stream()
                .filter(labelsById::containsKey)
                .map(labelId -> LabelResponse.from(labelsById.get(labelId)))
                .toList();
    }

    private List<TaskSkillResponse> suggestedSkills(RawTaskSuggestion raw, Map<Integer, Skill> skillsById) {
        Set<Integer> seen = new HashSet<>();
        List<TaskSkillResponse> suggested = new ArrayList<>();
        for (RawTaskSuggestion.SkillReward reward : raw.skills()) {
            Skill skill = skillsById.get(reward.skillId());
            if (skill == null || !seen.add(reward.skillId())) {
                continue;
            }
            suggested.add(new TaskSkillResponse(SkillResponse.from(skill), clamp(reward.expReward(), 1, 1000)));
        }
        return suggested;
    }

    private List<Skill> loadAllSkills() {
        List<Skill> skills = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<Skill> page = skillRepository.findAllOrderedById(
                    OffsetLimit.of(SKILL_CANDIDATE_PAGE_SIZE, offset, Sort.unsorted()));
            skills.addAll(page);
            if (page.size() < SKILL_CANDIDATE_PAGE_SIZE) {
                return skills;
            }
            offset += page.size();
        }
    }

    private int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
