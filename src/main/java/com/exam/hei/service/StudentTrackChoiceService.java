package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.TrackResolution;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.StudentTrackChoiceRepository;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentTrackChoice;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records which track a student follows, and answers which one applies to a given semester.
 *
 * <p>A track is never a permanent property of a student: they follow the common core until the last
 * common core semester, then choose. Everything downstream that needs to know a track asks {@link
 * #resolve} rather than reading a column.
 */
@Service
@AllArgsConstructor
public class StudentTrackChoiceService {

  private final StudentTrackChoiceRepository trackChoiceRepository;
  private final StudentRepository studentRepository;
  private final SemesterRepository semesterRepository;
  private final TrackService trackService;
  private final StudentAuthorizer studentAuthorizer;

  /** Read path: a student may only read their own choices. */
  public List<StudentTrackChoice> findAllByStudentId(UUID studentId) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return trackChoiceRepository.findAllByStudentIdOrderByFromSemesterSemOrderAsc(studentId);
  }

  /**
   * Which track applies to the student for that semester.
   *
   * <p>The resolution doc/api.yml specifies: nothing applies to a common core semester, otherwise
   * the choice with the highest effective semester at or below it, and a missing choice is an error
   * rather than an absence.
   */
  public TrackResolution resolve(UUID studentId, Semester semester) {
    if (semester.isCommonCore()) {
      return TrackResolution.commonCore();
    }
    return trackChoiceRepository
        .findRulingChoice(studentId, semester.getSemOrder())
        .map(choice -> TrackResolution.resolved(choice.getTrack()))
        .orElseGet(TrackResolution::notSelected);
  }

  /**
   * The track the student ends the curriculum in, empty while they are still in the common core.
   */
  public Optional<Track> exitTrackOf(UUID studentId) {
    return trackChoiceRepository
        .findFirstByStudentIdOrderByFromSemesterSemOrderDesc(studentId)
        .map(StudentTrackChoice::getTrack);
  }

  @Transactional
  public StudentTrackChoice choose(
      UUID studentId, UUID trackId, SemesterRef fromSemesterRef, String reason) {
    var student = requireStudent(studentId);
    var track = trackService.findById(trackId);
    var fromSemester = requireSemester(fromSemesterRef);

    checkSemesterExpectsATrack(fromSemester);
    checkFirstChoiceStartsWhereTracksBegin(studentId, fromSemester);
    checkTrackIsOpenedByThePromotion(student, track);
    checkSemesterIsNotAlreadyCovered(studentId, fromSemester);

    return trackChoiceRepository.save(
        StudentTrackChoice.builder()
            .student(student)
            .track(track)
            .fromSemester(fromSemester)
            .reason(reason)
            .build());
  }

  private Student requireStudent(UUID studentId) {
    return studentRepository
        .findById(studentId)
        .orElseThrow(() -> new NotFoundException("Student " + studentId + " not found"));
  }

  private Semester requireSemester(SemesterRef ref) {
    return semesterRepository
        .findByRef(ref)
        .orElseThrow(() -> new NotFoundException("Semester " + ref + " not found"));
  }

  private void checkSemesterExpectsATrack(Semester semester) {
    if (semester.isCommonCore()) {
      throw new BadRequestException(
          "Semester " + semester.getRef() + " is common core: no track is chosen for it");
    }
  }

  /**
   * A choice starting later than the first track semester would leave that semester uncovered, and
   * therefore not evaluable. Refusing it at write time beats reporting it at diploma time.
   */
  private void checkFirstChoiceStartsWhereTracksBegin(UUID studentId, Semester fromSemester) {
    var hasChosenBefore =
        !trackChoiceRepository
            .findAllByStudentIdOrderByFromSemesterSemOrderAsc(studentId)
            .isEmpty();
    if (hasChosenBefore) {
      return;
    }
    var firstTrackSemester =
        semesterRepository
            .findFirstByCommonCoreFalseOrderBySemOrderAsc()
            .orElseThrow(() -> new NotFoundException("No semester expects a track"));
    if (!firstTrackSemester.getId().equals(fromSemester.getId())) {
      throw new BadRequestException(
          "A first track choice takes effect on "
              + firstTrackSemester.getRef()
              + ", not on "
              + fromSemester.getRef());
    }
  }

  private void checkTrackIsOpenedByThePromotion(Student student, Track track) {
    var opened =
        student.getPromotion().getTracks().stream()
            .anyMatch(openedTrack -> openedTrack.getId().equals(track.getId()));
    if (!opened) {
      throw new BadRequestException(
          "Track "
              + track.getCode()
              + " is not opened by promotion "
              + student.getPromotion().getRef());
    }
  }

  private void checkSemesterIsNotAlreadyCovered(UUID studentId, Semester fromSemester) {
    trackChoiceRepository
        .findByStudentIdAndFromSemesterId(studentId, fromSemester.getId())
        .ifPresent(
            existing -> {
              throw new ConflictException(
                  "A track choice already takes effect on " + fromSemester.getRef());
            });
  }
}
