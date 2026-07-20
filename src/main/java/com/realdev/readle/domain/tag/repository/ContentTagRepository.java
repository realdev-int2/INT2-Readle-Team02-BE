package com.realdev.readle.domain.tag.repository;

import com.realdev.readle.domain.tag.entity.ContentTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {

  @Query("SELECT ct FROM ContentTag ct JOIN FETCH ct.tag WHERE ct.content.id = :contentId")
  List<ContentTag> findByContentIdWithTag(@Param("contentId") Long contentId);
}
