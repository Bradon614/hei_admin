package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.StudentStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StudentMapper {
  private final PromotionMapper promotionMapper;
  private final TrackMapper trackMapper;
  private final GroupMapper groupMapper;

  public Student toRest(
      com.exam.hei.repository.model.Student domain,
      com.exam.hei.repository.model.Track currentTrack,
      com.exam.hei.repository.model.Group currentGroup) {
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
        .currentTrack(currentTrack == null ? null : trackMapper.toRest(currentTrack))
        .currentGroup(currentGroup == null ? null : groupMapper.toRest(currentGroup))
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
        .password(rest.getPassword())
        .build();
  }

  private Promotion promotionOf(Student rest) {
    UUID promotionId = rest.getPromotion() == null ? null : rest.getPromotion().getId();
    return promotionId == null ? null : Promotion.builder().id(promotionId).build();
  }
}
