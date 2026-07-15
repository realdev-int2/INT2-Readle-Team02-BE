package com.realdev.readle.domain.content.repository;

import com.realdev.readle.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {}
