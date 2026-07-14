package com.realdev.readle.domain.content.repository;

import com.realdev.readle.domain.content.entity.ContentValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentValidationRepository extends JpaRepository<ContentValidation, Long> {}
