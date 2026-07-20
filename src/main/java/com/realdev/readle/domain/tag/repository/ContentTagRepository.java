package com.realdev.readle.domain.tag.repository;

import com.realdev.readle.domain.tag.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {}
