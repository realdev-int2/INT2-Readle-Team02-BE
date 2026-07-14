package com.realdev.readle.domain.member.repository;

import com.realdev.readle.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
