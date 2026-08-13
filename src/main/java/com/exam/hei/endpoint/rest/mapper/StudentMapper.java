package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.StudentStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pure conversion between the REST and the persistence representations of a student.
 *
 * <p>No repository here: resolving the promotion named by a payload, and the 404 that comes with an
 * unknown one, is a business decision left to the service. The mapper only carries the id across.
 */
@Component
@AllArgsConstructor
public class StudentMapper {

  private final PromotionMapper promotionMapper;

  public Student toRest(com.exam.hei.repository.model.Student domain) {
    return Student.builder()
        .id(domain.getId())
        .ref(domain.getRef())
        .firstName(domain.getFirstName())
        .lastName(domain.getLastName())
        .email(domain.getEmail())
        .birthDate(domain.getBirthDate())
        .entranceDate(domain.getEntranceDate())
        .status(domain.getStatus())
        .promotion(promotionMapper.toRest(domain.getPromotion()))
        .build();
  }

  public com.exam.hei.repository.model.Student toDomain(Student rest) {
    return com.exam.hei.repository.model.Student.builder()
        .id(rest.getId())
        .ref(rest.getRef())
        .firstName(rest.getFirstName())
        .lastName(rest.getLastName())
        .email(rest.getEmail())
        .birthDate(rest.getBirthDate())
        .entranceDate(rest.getEntranceDate())
        .status(rest.getStatus() == null ? StudentStatus.ACTIVE : rest.getStatus())
        .promotion(promotionOf(rest))
        .build();
  }

  /** Carries the named promotion as an id only: the service replaces it with the persisted one. */
  private Promotion promotionOf(Student rest) {
    UUID promotionId = rest.getPromotion() == null ? null : rest.getPromotion().getId();
    return promotionId == null ? null : Promotion.builder().id(promotionId).build();
  }
}
