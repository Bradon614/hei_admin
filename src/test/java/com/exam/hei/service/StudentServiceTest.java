package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

class StudentServiceTest {
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final PromotionService promotionService = mock(PromotionService.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final StudentService subject =
      new StudentService(
          studentRepository,
          appUserRepository,
          promotionService,
          studentAuthorizer,
          passwordEncoder);

  private static final UUID PROMOTION_ID = UUID.randomUUID();

  private static Promotion promotion() {
    return Promotion.builder().id(PROMOTION_ID).ref("K").build();
  }

  private static Student student(UUID id) {
    return Student.builder()
        .id(id)
        .ref("STD22045")
        .firstName("Jean")
        .lastName("Rakoto")
        .email("jean@hei.test")
        .entranceDate(LocalDate.of(2025, 9, 1))
        .promotion(Promotion.builder().id(PROMOTION_ID).build())
        .password("s3cret!!")
        .build();
  }

  private void repositoryEchoesWhatItIsGiven() {
    when(studentRepository.saveAll(any()))
        .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
    when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(passwordEncoder.encode(any()))
        .thenAnswer(invocation -> "hashed:" + invocation.getArgument(0));
  }

  @Test
  void creating_a_student_creates_the_account_it_signs_in_with() {
    when(promotionService.findById(PROMOTION_ID)).thenReturn(promotion());
    repositoryEchoesWhatItIsGiven();

    var saved = subject.saveAll(List.of(student(null))).get(0);

    assertNotNull(saved.getUser());
    assertEquals(Role.STUDENT, saved.getUser().getRole());
    assertEquals("jean@hei.test", saved.getUser().getEmail());
    assertEquals("hashed:s3cret!!", saved.getUser().getPasswordHash());
  }

  @Test
  void a_student_cannot_be_created_without_a_password() {
    when(promotionService.findById(PROMOTION_ID)).thenReturn(promotion());
    var passwordless = student(null);
    passwordless.setPassword(null);

    assertThrows(BadRequestException.class, () -> subject.saveAll(List.of(passwordless)));
  }

  @Test
  void updating_a_student_keeps_its_existing_account() {
    var id = UUID.randomUUID();
    var account =
        AppUser.builder().id(UUID.randomUUID()).email("jean@hei.test").passwordHash("old").build();
    var existing = student(id);
    existing.setUser(account);
    when(studentRepository.findById(id)).thenReturn(Optional.of(existing));
    when(promotionService.findById(PROMOTION_ID)).thenReturn(promotion());
    repositoryEchoesWhatItIsGiven();

    var updated = student(id);
    updated.setPassword(null);
    var saved = subject.saveAll(List.of(updated)).get(0);

    assertEquals(account.getId(), saved.getUser().getId());
    assertEquals("old", saved.getUser().getPasswordHash());
    verify(appUserRepository, never()).save(any());
  }

  @Test
  void an_updated_password_replaces_the_account_hash() {
    var id = UUID.randomUUID();
    var account =
        AppUser.builder().id(UUID.randomUUID()).email("jean@hei.test").passwordHash("old").build();
    var existing = student(id);
    existing.setUser(account);
    when(studentRepository.findById(id)).thenReturn(Optional.of(existing));
    when(promotionService.findById(PROMOTION_ID)).thenReturn(promotion());
    repositoryEchoesWhatItIsGiven();

    var withNewPassword = student(id);
    withNewPassword.setPassword("newPass1!");
    subject.saveAll(List.of(withNewPassword));

    assertEquals("hashed:newPass1!", account.getPasswordHash());
    verify(appUserRepository).save(account);
  }

  @Test
  void renaming_the_email_of_a_student_keeps_its_account_in_step() {
    var id = UUID.randomUUID();
    var account =
        AppUser.builder().id(UUID.randomUUID()).email("old@hei.test").passwordHash("old").build();
    var existing = student(id);
    existing.setUser(account);
    when(studentRepository.findById(id)).thenReturn(Optional.of(existing));
    when(promotionService.findById(PROMOTION_ID)).thenReturn(promotion());
    repositoryEchoesWhatItIsGiven();

    var renamed = student(id);
    renamed.setEmail("new@hei.test");
    renamed.setPassword(null);
    subject.saveAll(List.of(renamed));

    assertEquals("new@hei.test", account.getEmail());
    verify(appUserRepository).save(account);
  }

  @Test
  void a_student_naming_an_unknown_promotion_is_not_found() {
    when(promotionService.findById(PROMOTION_ID))
        .thenThrow(new NotFoundException("Promotion not found"));

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(student(null))));
  }

  @Test
  void a_student_without_a_promotion_is_not_found() {
    var orphan = student(null);
    orphan.setPromotion(null);

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(orphan)));
  }

  @Test
  void a_student_naming_an_unknown_id_is_not_found() {
    var id = UUID.randomUUID();
    when(studentRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(student(id))));
  }

  @Test
  void an_unknown_student_is_not_found() {
    var id = UUID.randomUUID();
    when(studentRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void an_account_without_a_student_profile_is_not_found() {
    var userId = UUID.randomUUID();
    when(studentRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findByUserId(userId));
  }

  @Test
  void listing_without_any_filter_returns_every_student() {
    when(studentRepository.findAll(any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, null, null, null, null);

    verify(studentRepository).findAll(any(Pageable.class));
    verify(studentRepository, never()).findAllByPromotionId(any(), any());
  }

  @Test
  void listing_with_a_promotion_filter_narrows_the_query() {
    when(studentRepository.findAllByPromotionId(eq(PROMOTION_ID), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, PROMOTION_ID, null, null, null);

    verify(studentRepository).findAllByPromotionId(eq(PROMOTION_ID), any(Pageable.class));
    verify(studentRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void listing_by_group_looks_at_a_date() {
    var groupId = UUID.randomUUID();
    var date = LocalDate.of(2025, 10, 1);
    when(studentRepository.findAllInGroupAt(eq(groupId), eq(date), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, null, groupId, date, null);

    verify(studentRepository).findAllInGroupAt(eq(groupId), eq(date), any(Pageable.class));
  }

  @Test
  void listing_by_group_without_a_date_looks_at_today() {
    var groupId = UUID.randomUUID();
    when(studentRepository.findAllInGroupAt(eq(groupId), eq(LocalDate.now()), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, null, groupId, null, null);

    verify(studentRepository)
        .findAllInGroupAt(eq(groupId), eq(LocalDate.now()), any(Pageable.class));
  }

  @Test
  void listing_by_track_narrows_the_query() {
    when(studentRepository.findAllFollowingTrack(eq("EL"), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, null, null, null, "EL");

    verify(studentRepository).findAllFollowingTrack(eq("EL"), any(Pageable.class));
  }

  @Test
  void the_group_filter_takes_precedence_over_the_others() {
    var groupId = UUID.randomUUID();
    when(studentRepository.findAllInGroupAt(any(), any(), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, PROMOTION_ID, groupId, null, "EL");

    verify(studentRepository).findAllInGroupAt(eq(groupId), any(), any(Pageable.class));
    verify(studentRepository, never()).findAllFollowingTrack(any(), any());
    verify(studentRepository, never()).findAllByPromotionId(any(), any());
  }

  @Test
  void an_invalid_page_is_a_bad_request() {
    assertThrows(BadRequestException.class, () -> subject.findAll(0, 50, null, null, null, null));
    assertThrows(BadRequestException.class, () -> subject.findAll(1, 0, null, null, null, null));
    assertThrows(BadRequestException.class, () -> subject.findAll(1, 501, null, null, null, null));
  }
}
