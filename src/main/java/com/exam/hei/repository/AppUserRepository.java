package com.exam.hei.repository;

import com.exam.hei.repository.model.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByEmail(String email);
}
