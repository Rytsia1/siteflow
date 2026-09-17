package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.siteflow.domain.UserWithRole;
import com.siteflow.mapper.UserMapper;

@ExtendWith(MockitoExtension.class)
class DbUserDetailsServiceTest {

    @Mock
    private UserMapper userMapper;

    private DbUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new DbUserDetailsService(userMapper);
    }

    @Test
    @DisplayName("loadUserByUsername returns UserPrincipal with ROLE_ authority for valid user")
    void loadUserByUsername_validUser_returnsUserPrincipal() {
        UserWithRole user = UserWithRole.builder()
                .id(10L)
                .username("supervisor")
                .passwordHash("$2a$10$encryptedHash")
                .roleName("FIELD_STAFF")
                .build();

        when(userMapper.findByUsername("supervisor")).thenReturn(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername("supervisor");

        assertThat(userDetails).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) userDetails;
        assertThat(principal.getUserId()).isEqualTo(10L);
        assertThat(principal.getUsername()).isEqualTo("supervisor");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$encryptedHash");
        assertThat(principal.getRoleName()).isEqualTo("FIELD_STAFF");
        assertThat(principal.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_FIELD_STAFF");
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user does not exist")
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() {
        when(userMapper.findByUsername("nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found: nonexistent");
    }
}
