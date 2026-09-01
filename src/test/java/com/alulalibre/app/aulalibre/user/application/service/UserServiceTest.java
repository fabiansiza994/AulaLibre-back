package com.alulalibre.app.aulalibre.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.user.application.dto.request.CreateUserRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserPasswordRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserStatusRequest;
import com.alulalibre.app.aulalibre.user.application.dto.response.UserResponse;
import com.alulalibre.app.aulalibre.user.application.mapper.UserMapper;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import com.alulalibre.app.aulalibre.user.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserService userService;

    private User admin;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, passwordEncoder, currentUserProvider);

        admin = new User();
        admin.setId(1L);
        admin.setRole(UserRole.ADMIN);

        lenient().when(userMapper.toResponse(any(User.class)))
                .thenReturn(new UserResponse(null, null, null, null, null, false, null, null));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_rejectsADuplicateEmail() {
        when(userRepository.findByEmailIgnoreCase("juan@aulalibre.edu")).thenReturn(Optional.of(new User()));
        CreateUserRequest request = new CreateUserRequest("Juan", "Pérez", "juan@aulalibre.edu",
                UserRole.PROFESSOR, "Temporal123*");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void create_hashesThePasswordAndAlwaysStartsActive() {
        when(userRepository.findByEmailIgnoreCase("carlos@aulalibre.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Temporal123*")).thenReturn("$2a$10$hashed");
        CreateUserRequest request = new CreateUserRequest("Carlos", "Ramírez", "carlos@aulalibre.edu",
                UserRole.PROFESSOR, "Temporal123*");

        userService.create(request);

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void update_rejectsWhenTheAdminTriesToChangeTheirOwnRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailIgnoreCase("admin@aulalibre.edu")).thenReturn(Optional.empty());
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        UpdateUserRequest request = new UpdateUserRequest("Adm", "In", "admin@aulalibre.edu", UserRole.PROFESSOR);

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
    }

    @Test
    void update_allowsTheAdminToKeepTheirOwnRoleAsAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailIgnoreCase("admin@aulalibre.edu")).thenReturn(Optional.empty());
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        UpdateUserRequest request = new UpdateUserRequest("Adm", "In", "admin@aulalibre.edu", UserRole.ADMIN);

        userService.update(1L, request);

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void update_rejectsADuplicateEmailFromAnotherUser() {
        User other = new User();
        other.setId(2L);
        other.setRole(UserRole.PROFESSOR);
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        User owner = new User();
        owner.setId(3L);
        when(userRepository.findByEmailIgnoreCase("taken@aulalibre.edu")).thenReturn(Optional.of(owner));
        UpdateUserRequest request = new UpdateUserRequest("X", "Y", "taken@aulalibre.edu", UserRole.PROFESSOR);

        assertThatThrownBy(() -> userService.update(2L, request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void update_allowsKeepingYourOwnCurrentEmail() {
        User other = new User();
        other.setId(2L);
        other.setEmail("me@aulalibre.edu");
        other.setRole(UserRole.PROFESSOR);
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userRepository.findByEmailIgnoreCase("me@aulalibre.edu")).thenReturn(Optional.of(other));
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        UpdateUserRequest request = new UpdateUserRequest("X", "Y", "me@aulalibre.edu", UserRole.PROFESSOR);

        userService.update(2L, request);

        assertThat(other.getFirstName()).isEqualTo("X");
    }

    @Test
    void updateStatus_rejectsWhenTheAdminTriesToDisableThemselves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);

        assertThatThrownBy(() -> userService.updateStatus(1L, new UpdateUserStatusRequest(false)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_DISABLE_SELF);
    }

    @Test
    void updateStatus_disablesAnotherUserSuccessfully() {
        User professor = new User();
        professor.setId(5L);
        professor.setActive(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(professor));
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);

        userService.updateStatus(5L, new UpdateUserStatusRequest(false));

        assertThat(professor.isActive()).isFalse();
    }

    @Test
    void updatePassword_hashesTheNewPasswordAndNeverStoresItRaw() {
        User professor = new User();
        professor.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(professor));
        when(passwordEncoder.encode("NuevaTemporal123*")).thenReturn("$2a$10$otherhash");

        userService.updatePassword(5L, new UpdateUserPasswordRequest("NuevaTemporal123*"));

        assertThat(professor.getPasswordHash()).isEqualTo("$2a$10$otherhash");
    }
}
