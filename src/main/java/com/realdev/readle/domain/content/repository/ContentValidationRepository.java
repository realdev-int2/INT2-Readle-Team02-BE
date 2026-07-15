package com.realdev.readle.domain.content.repository;

import com.realdev.readle.domain.content.entity.ContentValidation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentValidationRepository extends JpaRepository<ContentValidation, Long> {

  @Query("select cv from ContentValidation cv join fetch cv.content where cv.id = :id")
  Optional<ContentValidation> findByIdWithContent(@Param("id") Long id);
}
