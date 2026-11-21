package com.homelab.homepage.common.config;

import com.homelab.homepage.domain.user.model.Role;
import com.homelab.homepage.domain.user.model.User;
import com.homelab.homepage.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Garante o primeiro administrador a partir das variaveis ADMIN_EMAIL, ADMIN_PASSWORD e ADMIN_USERNAME.
 * - E-mail ainda nao cadastrado: cria o usuario como ADMIN (exige ADMIN_PASSWORD).
 * - E-mail ja cadastrado: promove a ADMIN (se ainda nao for) e, se ADMIN_PASSWORD estiver definido
 *   e for diferente da senha atual, troca a senha. Assim a credencial do Jenkins e a fonte da senha do admin.
 * - ADMIN_PASSWORD vazio: a senha existente e mantida.
 * Sem ADMIN_EMAIL nada e alterado.
 */
@Component
public class AdminSeedLoader implements ApplicationRunner
{

    private static final Logger log = LoggerFactory.getLogger( AdminSeedLoader.class );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminUsername;
    private final String adminPassword;

    public AdminSeedLoader( UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value( "${app.admin.email:}" ) String adminEmail,
            @Value( "${app.admin.username:admin}" ) String adminUsername,
            @Value( "${app.admin.password:}" ) String adminPassword )
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        if ( adminEmail == null || adminEmail.isBlank() )
        {
            if ( userRepository.countByRole( Role.ADMIN ) == 0 )
            {
                log.warn( "Nenhum administrador cadastrado. Defina ADMIN_EMAIL (e ADMIN_PASSWORD) para criar ou promover um." );
            }
            return;
        }

        String email = adminEmail.trim();
        Optional<User> existingUser = userRepository.findByEmail( email );

        if ( existingUser.isPresent() )
        {
            User user = existingUser.get();
            boolean changed = false;

            if ( user.getRole() != Role.ADMIN )
            {
                user.setRole( Role.ADMIN );
                changed = true;
                log.info( "Usuario {} promovido a ADMIN", email );
            }
            if ( hasPassword() && !passwordEncoder.matches( adminPassword, user.getPassword() ) )
            {
                user.setPassword( passwordEncoder.encode( adminPassword ) );
                changed = true;
                log.info( "Senha do administrador {} atualizada a partir de ADMIN_PASSWORD", email );
            }
            if ( changed )
            {
                userRepository.save( user );
            }
            return;
        }

        if ( !hasPassword() )
        {
            log.warn( "ADMIN_EMAIL definido, mas {} nao existe e ADMIN_PASSWORD esta vazio: administrador nao criado.", email );
            return;
        }

        User admin = new User();
        admin.setEmail( email );
        admin.setUsername( adminUsername );
        admin.setPassword( passwordEncoder.encode( adminPassword ) );
        admin.setRole( Role.ADMIN );
        userRepository.save( admin );
        log.info( "Administrador inicial {} criado", email );
    }

    private boolean hasPassword()
    {
        return adminPassword != null && !adminPassword.isBlank();
    }
}
