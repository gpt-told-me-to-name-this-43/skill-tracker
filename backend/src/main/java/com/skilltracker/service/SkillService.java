package com.skilltracker.service;

import com.skilltracker.domain.Skill;
import com.skilltracker.domain.UserSkill;
import com.skilltracker.dto.SkillCreateRequest;
import com.skilltracker.dto.SkillResponse;
import com.skilltracker.dto.SkillUpdateRequest;
import com.skilltracker.dto.UserProgressResponse;
import com.skilltracker.dto.UserSkillResponse;
import com.skilltracker.exception.ConflictException;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.repository.OffsetLimit;
import com.skilltracker.repository.SkillRepository;
import com.skilltracker.repository.UserRepository;
import com.skilltracker.repository.UserSkillRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;

    public SkillService(
            SkillRepository skillRepository, UserSkillRepository userSkillRepository, UserRepository userRepository) {
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> list(int limit, int offset) {
        return skillRepository.findAllOrderedById(OffsetLimit.of(limit, offset, Sort.unsorted())).stream()
                .map(SkillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillResponse get(Integer skillId) {
        return SkillResponse.from(requireSkill(skillId));
    }

    @Transactional
    public SkillResponse create(SkillCreateRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new ConflictException("Skill name cannot be empty");
        }
        if (skillRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("Skill '" + name + "' already exists");
        }

        return SkillResponse.from(skillRepository.save(new Skill(name, request.description())));
    }

    @Transactional
    public SkillResponse update(Integer skillId, SkillUpdateRequest request) {
        Skill skill = requireSkill(skillId);

        if (request.name().value() != null) {
            String name = request.name().value().trim();
            if (name.isEmpty()) {
                throw new ConflictException("Skill name cannot be empty");
            }
            skillRepository
                    .findByNameIgnoreCase(name)
                    .filter(other -> !other.getId().equals(skill.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("Skill '" + name + "' already exists");
                    });
            skill.setName(name);
        }

        if (request.description().isPresent()) {
            skill.setDescription(request.description().value());
        }

        return SkillResponse.from(skillRepository.save(skill));
    }

    @Transactional
    public void delete(Integer skillId) {
        skillRepository.delete(requireSkill(skillId));
    }

    @Transactional
    public UserSkillResponse assignSkillToUser(Integer userId, Integer skillId) {
        requireUser(userId);
        Skill skill = requireSkill(skillId);

        if (userSkillRepository.findByUserIdAndSkillId(userId, skillId).isPresent()) {
            throw new ConflictException("Skill '" + skill.getName() + "' already assigned to user " + userId);
        }

        UserSkill userSkill = userSkillRepository.save(new UserSkill(userId, skillId, 0));
        return toResponse(skill, userSkill.getExperience());
    }

    @Transactional(readOnly = true)
    public List<UserSkillResponse> getUserSkills(Integer userId) {
        requireUser(userId);
        return userSkillRepository.findByUserIdWithSkill(userId).stream()
                .map(userSkill -> toResponse(userSkill.getSkill(), userSkill.getExperience()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProgressResponse getUserProgress(Integer userId) {
        requireUser(userId);
        List<UserSkill> userSkills = userSkillRepository.findByUserIdWithSkill(userId);

        int totalExperience = 0;
        int totalLevel = 0;
        List<UserSkillResponse> skills = new ArrayList<>();
        for (UserSkill userSkill : userSkills) {
            SkillProgress progress = SkillProgress.forExperience(userSkill.getExperience());
            totalExperience += userSkill.getExperience();
            totalLevel += progress.level();
            skills.add(toResponse(userSkill.getSkill(), userSkill.getExperience()));
        }

        return new UserProgressResponse(
                userId,
                totalExperience,
                userSkills.size(),
                SkillProgress.averageLevel(totalLevel, userSkills.size()),
                skills);
    }

    private UserSkillResponse toResponse(Skill skill, int experience) {
        SkillProgress progress = SkillProgress.forExperience(experience);
        return new UserSkillResponse(
                SkillResponse.from(skill),
                experience,
                progress.level(),
                progress.currentLevelXp(),
                progress.nextLevelXp(),
                progress.progressToNextLevel());
    }

    private Skill requireSkill(Integer skillId) {
        return skillRepository
                .findById(skillId)
                .orElseThrow(() -> new NotFoundException("Skill " + skillId + " not found"));
    }

    private void requireUser(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User " + userId + " not found");
        }
    }
}
