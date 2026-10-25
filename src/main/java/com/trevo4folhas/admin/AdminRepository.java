package com.trevo4folhas.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

  Optional<Admin> findByUsername(String username);
}
