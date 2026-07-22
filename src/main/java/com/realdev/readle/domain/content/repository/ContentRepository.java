package com.realdev.readle.domain.content.repository;

import com.realdev.readle.domain.content.entity.Content;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Content c WHERE c.id = :id")
  Optional<Content> findByIdWithPessimisticLock(@Param("id") Long id);
}
