package com.exam.hei.service;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class PromotionService {

  private final PromotionRepository promotionRepository;
  private final TrackService trackService;

  /**
   * @param page 1-based, as declared in doc/api.yml
   */
  public List<Promotion> findAll(int page, int pageSize) {
    return promotionRepository
        .findAll(Pagination.toPageRequest(page, pageSize, Sort.by("ref")))
        .getContent();
  }

  public Promotion findById(UUID id) {
    return promotionRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Promotion " + id + " not found"));
  }

  @Transactional
  public List<Promotion> saveAll(List<Promotion> promotions) {
    promotions.forEach(this::checkIsKnownWhenIdentified);
    promotions.forEach(this::resolveTracks);
    return promotionRepository.saveAll(promotions);
  }

  /** An unknown id is a caller mistake rather than a request to create a promotion at that id. */
  private void checkIsKnownWhenIdentified(Promotion promotion) {
    if (promotion.getId() != null) {
      findById(promotion.getId());
    }
  }

  /**
   * Replaces the tracks named by the payload with the persisted ones.
   *
   * <p>Tracks are reference data with their own endpoint: naming an unknown one is a 404, never an
   * implicit creation.
   */
  private void resolveTracks(Promotion promotion) {
    if (promotion.getTracks() == null) {
      promotion.setTracks(List.of());
      return;
    }
    promotion.setTracks(
        promotion.getTracks().stream()
            .map(Track::getId)
            .filter(Objects::nonNull)
            .distinct()
            .map(trackService::findById)
            .toList());
  }
}
