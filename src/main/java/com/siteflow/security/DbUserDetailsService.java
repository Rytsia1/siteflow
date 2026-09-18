package com.siteflow.security;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.siteflow.domain.UserWithRole;
import com.siteflow.mapper.UserMapper;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public DbUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserWithRole user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new DisabledException("User account is deactivated.");
        }
        return new UserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRoleName());
    }
}
