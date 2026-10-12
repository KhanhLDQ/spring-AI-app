package com.tommap.springaiapp.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
