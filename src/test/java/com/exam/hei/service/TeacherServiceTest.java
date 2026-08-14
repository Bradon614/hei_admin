package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Teacher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeacherServiceTest {

  private final TeacherRepository teacherRepository = mock(TeacherRepository.class);
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final TeacherService subject = new TeacherService(teacherRepository, appUserRepository);

  private static Teacher teacher(UUID id) {
    return Teacher.builder()
        .id(id)
        .ref("TCH0012")
        .firstName("Aina")
        .lastName("Randria")
        .email("aina@hei.test")
        .build();
  }

  private void repositoryEchoesWhatItIsGiven() {
    when(teacherRepository.saveAll(any()))
        .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
    when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void creating_a_teacher_creates_the_account_it_signs_in_with() {
    repositoryEchoesWhatItIsGiven();

    var saved = subject.saveAll(List.of(teacher(null))).get(0);

    assertNotNull(saved.getUser());
    assertEquals(Role.TEACHER, saved.getUser().getRole());
    assertEquals("aina@hei.test", saved.getUser().getEmail());
    assertNotNull(saved.getUser().getApiKey());
  }

  @Test
  void updating_a_teacher_keeps_its_existing_account() {
    var id = UUID.randomUUID();
    var account = AppUser.builder().id(UUID.randomUUID()).email("aina@hei.test").build();
    var existing = teacher(id);
    existing.setUser(account);
    when(teacherRepository.findById(id)).thenReturn(Optional.of(existing));
    repositoryEchoesWhatItIsGiven();

    var saved = subject.saveAll(List.of(teacher(id))).get(0);

    assertEquals(account.getId(), saved.getUser().getId());
    verify(appUserRepository, never()).save(any());
  }

  @Test
  void renaming_the_email_of_a_teacher_keeps_its_account_in_step() {
    var id = UUID.randomUUID();
    var account = AppUser.builder().id(UUID.randomUUID()).email("old@hei.test").build();
    var existing = teacher(id);
    existing.setUser(account);
    when(teacherRepository.findById(id)).thenReturn(Optional.of(existing));
    repositoryEchoesWhatItIsGiven();

    var renamed = teacher(id);
    renamed.setEmail("new@hei.test");
    subject.saveAll(List.of(renamed));

    assertEquals("new@hei.test", account.getEmail());
  }

  @Test
  void a_teacher_naming_an_unknown_id_is_not_found() {
    var id = UUID.randomUUID();
    when(teacherRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(teacher(id))));
  }

  @Test
  void an_unknown_teacher_is_not_found() {
    var id = UUID.randomUUID();
    when(teacherRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void an_account_without_a_teacher_profile_is_not_found() {
    var userId = UUID.randomUUID();
    when(teacherRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findByUserId(userId));
  }

  @Test
  void an_invalid_page_is_a_bad_request() {
    assertThrows(BadRequestException.class, () -> subject.findAll(0, 50));
    assertThrows(BadRequestException.class, () -> subject.findAll(1, 501));
  }
}
