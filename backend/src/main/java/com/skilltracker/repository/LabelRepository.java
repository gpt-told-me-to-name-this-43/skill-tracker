package com.skilltracker.repository;

import com.skilltracker.domain.Label;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabelRepository extends JpaRepository<Label, Integer> {

    List<Label> findAllByOrderByNameAsc();

    @Query("select label from Label label where lower(label.name) = lower(:name)")
    Optional<Label> findByNameIgnoreCase(@Param("name") String name);

    List<Label> findByIdIn(Collection<Integer> ids);
}
