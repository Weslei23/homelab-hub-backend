package com.homelab.homepage.domain.user.repository;

import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>
{
    Optional<UserDetails> findUserByEmail( String userEmail );

    Optional<User> findByEmail( String email );

    boolean existsByEmail( String email );

    long countByRole( Role role );
}
