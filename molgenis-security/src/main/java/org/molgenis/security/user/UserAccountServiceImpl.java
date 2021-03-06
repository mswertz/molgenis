package org.molgenis.security.user;

import java.util.Objects;
import org.molgenis.data.security.auth.User;
import org.molgenis.data.security.user.UserService;
import org.molgenis.security.core.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountServiceImpl implements UserAccountService {
  @Autowired private UserService userService;

  @Autowired private PasswordEncoder passwordEncoder;

  @Override
  @Transactional(readOnly = true)
  public User getCurrentUser() {
    return userService.getUser(SecurityUtils.getCurrentUsername());
  }

  @Override
  @PreAuthorize("hasAnyRole('ROLE_SU', 'ROLE_USER')")
  @Transactional
  public void updateCurrentUser(User updatedCurrentUser) {
    String currentUsername = SecurityUtils.getCurrentUsername();
    if (!Objects.equals(currentUsername, updatedCurrentUser.getUsername())) {
      throw new RuntimeException("Updated user differs from the current user");
    }

    User currentUser = userService.getUser(currentUsername);
    if (currentUser == null) {
      throw new RuntimeException("User does not exist [" + currentUsername + "]");
    }
    userService.update(updatedCurrentUser);
  }

  @Override
  @PreAuthorize("hasAnyRole('ROLE_SU', 'ROLE_USER')")
  @Transactional
  public boolean validateCurrentUserPassword(String password) {
    if (password == null || password.isEmpty()) return false;

    String currentUsername = SecurityUtils.getCurrentUsername();
    User currentUser = userService.getUser(currentUsername);
    if (currentUser == null) {
      throw new RuntimeException(
          "User does not exist [" + SecurityUtils.getCurrentUsername() + "]");
    }
    return passwordEncoder.matches(password, currentUser.getPassword());
  }
}
