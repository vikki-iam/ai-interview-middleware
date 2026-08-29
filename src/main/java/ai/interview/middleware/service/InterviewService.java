package ai.interview.middleware.service;

import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.entity.Interview;
import ai.interview.middleware.domain.entity.InterviewQuestion;
import ai.interview.middleware.domain.entity.InterviewResult;
import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.InterviewStatus;
import ai.interview.middleware.domain.enums.Role;
import ai.interview.middleware.dto.interview.GenerateQuestionsRequest;
import ai.interview.middleware.dto.interview.InterviewCreateRequest;
import ai.interview.middleware.dto.interview.InterviewQuestionResponse;
import ai.interview.middleware.dto.interview.InterviewResponse;
import ai.interview.middleware.dto.interview.InterviewResultRequest;
import ai.interview.middleware.dto.interview.InterviewResultResponse;
import ai.interview.middleware.dto.interview.InterviewUpdateRequest;
import ai.interview.middleware.exception.BadRequestException;
import ai.interview.middleware.exception.ResourceNotFoundException;
import ai.interview.middleware.mapper.InterviewMapper;
import ai.interview.middleware.repository.CandidateRepository;
import ai.interview.middleware.repository.InterviewQuestionRepository;
import ai.interview.middleware.repository.InterviewRepository;
import ai.interview.middleware.repository.InterviewResultRepository;
import ai.interview.middleware.repository.UserRepository;
import ai.interview.middleware.repository.spec.InterviewSpecifications;
import ai.interview.middleware.security.AuthenticatedUser;
import ai.interview.middleware.service.ai.AiQuestionClient;
import ai.interview.middleware.service.ai.AiQuestionRequest;
import ai.interview.middleware.service.ai.AiQuestionSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Interview scheduling, assignment, AI question generation and results.
 *
 * <p>Role scoping lives here rather than in the controller: a {@code CANDIDATE} may only ever see
 * interviews belonging to their own candidate record, and enforcing that in the query means no
 * endpoint can accidentally leak another candidate's data by forgetting a check.
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewResultRepository resultRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final AiQuestionClient aiQuestionClient;
    private final InterviewQuestionWriter questionWriter;
    private final InterviewMapper interviewMapper;

    public InterviewService(
            InterviewRepository interviewRepository,
            InterviewQuestionRepository questionRepository,
            InterviewResultRepository resultRepository,
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            AiQuestionClient aiQuestionClient,
            InterviewQuestionWriter questionWriter,
            InterviewMapper interviewMapper) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.resultRepository = resultRepository;
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.aiQuestionClient = aiQuestionClient;
        this.questionWriter = questionWriter;
        this.interviewMapper = interviewMapper;
    }

    // ---------------------------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<InterviewResponse> search(
            String searchTerm,
            InterviewStatus status,
            UUID candidateId,
            UUID interviewerId,
            Instant scheduledFrom,
            Instant scheduledUntil,
            AuthenticatedUser currentUser,
            Pageable pageable) {

        Specification<Interview> specification = InterviewSpecifications.unfiltered();

        if (currentUser.hasRole(Role.CANDIDATE)) {
            Optional<UUID> ownCandidateId = resolveOwnCandidateId(currentUser);
            if (ownCandidateId.isEmpty()) {
                // Authenticated as a candidate with no candidate record: nothing to show, and
                // returning an empty page is more honest than a 403 on a list endpoint.
                return Page.<InterviewResponse>empty(pageable);
            }
            specification = specification.and(InterviewSpecifications.hasCandidate(ownCandidateId.get()));
        } else if (candidateId != null) {
            specification = specification.and(InterviewSpecifications.hasCandidate(candidateId));
        }

        if (StringUtils.hasText(searchTerm)) {
            specification = specification.and(InterviewSpecifications.matchesText(searchTerm.trim()));
        }
        if (status != null) {
            specification = specification.and(InterviewSpecifications.hasStatus(status));
        }
        if (interviewerId != null) {
            specification = specification.and(InterviewSpecifications.hasInterviewer(interviewerId));
        }
        if (scheduledFrom != null) {
            specification = specification.and(InterviewSpecifications.scheduledFrom(scheduledFrom));
        }
        if (scheduledUntil != null) {
            specification = specification.and(InterviewSpecifications.scheduledUntil(scheduledUntil));
        }

        Page<Interview> page = interviewRepository.findAll(specification, pageable);
        Set<UUID> withResults = interviewIdsWithResults(page.getContent());
        return page.map(
                interview ->
                        interviewMapper.toSummaryResponse(interview, withResults.contains(interview.getId())));
    }

    @Transactional(readOnly = true)
    public InterviewResponse findById(UUID id, AuthenticatedUser currentUser) {
        Interview interview = requireInterview(id);
        assertVisibleTo(interview, currentUser);
        boolean includeAnswers = !currentUser.hasRole(Role.CANDIDATE);
        return interviewMapper.toDetailResponse(
                interview, includeAnswers, resultRepository.existsByInterviewId(id));
    }

    @Transactional(readOnly = true)
    public List<InterviewQuestionResponse> listQuestions(UUID id, AuthenticatedUser currentUser) {
        Interview interview = requireInterview(id);
        assertVisibleTo(interview, currentUser);
        boolean includeAnswers = !currentUser.hasRole(Role.CANDIDATE);
        return questionRepository.findByInterviewIdOrderBySequenceNoAsc(id).stream()
                .map(question -> interviewMapper.toQuestionResponse(question, includeAnswers))
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewResultResponse findResult(UUID id, AuthenticatedUser currentUser) {
        Interview interview = requireInterview(id);
        assertVisibleTo(interview, currentUser);
        return resultRepository
                .findByInterviewId(id)
                .map(interviewMapper::toResultResponse)
                .orElseThrow(
                        () -> new ResourceNotFoundException("No result has been submitted for interview " + id));
    }

    // ---------------------------------------------------------------------------------------------
    // Write
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public InterviewResponse create(InterviewCreateRequest request, AuthenticatedUser currentUser) {
        Candidate candidate =
                candidateRepository
                        .findById(request.candidateId())
                        .orElseThrow(() -> new ResourceNotFoundException("Candidate", request.candidateId()));

        Interview interview = new Interview();
        interview.setCandidate(candidate);
        interview.setTitle(request.title().trim());
        interview.setRoleTitle(request.roleTitle().trim());
        interview.setExperienceLevel(request.experienceLevel());
        interview.setRoundNumber(request.roundNumber() == null ? 1 : request.roundNumber());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDurationMinutes(request.durationMinutes() == null ? 60 : request.durationMinutes());
        interview.setFocusSkills(joinSkills(request.focusSkills()));
        interview.setStatus(InterviewStatus.SCHEDULED);

        if (request.interviewerId() != null) {
            interview.setInterviewer(requireInterviewer(request.interviewerId()));
        }
        userRepository.findById(currentUser.getId()).ifPresent(interview::setCreatedBy);

        Interview saved = interviewRepository.save(interview);
        log.info(
                "Created interview {} for candidate {} (round {})",
                saved.getId(),
                candidate.getId(),
                saved.getRoundNumber());
        return interviewMapper.toDetailResponse(saved, true, false);
    }

    @Transactional
    public InterviewResponse update(UUID id, InterviewUpdateRequest request) {
        Interview interview = requireInterview(id);
        if (interview.getStatus().isTerminal() && request.status() == null) {
            throw new BadRequestException(
                    "Interview %s is %s and can no longer be edited".formatted(id, interview.getStatus()));
        }

        interview.setTitle(request.title().trim());
        interview.setRoleTitle(request.roleTitle().trim());
        interview.setExperienceLevel(request.experienceLevel());
        if (request.roundNumber() != null) {
            interview.setRoundNumber(request.roundNumber());
        }
        interview.setScheduledAt(request.scheduledAt());
        if (request.durationMinutes() != null) {
            interview.setDurationMinutes(request.durationMinutes());
        }
        interview.setFocusSkills(joinSkills(request.focusSkills()));

        if (request.status() != null && request.status() != interview.getStatus()) {
            applyStatusTransition(interview, request.status());
        }

        Interview saved = interviewRepository.save(interview);
        log.info("Updated interview {}", id);
        return interviewMapper.toDetailResponse(saved, true, resultRepository.existsByInterviewId(id));
    }

    @Transactional
    public InterviewResponse assignInterviewer(UUID id, UUID interviewerId) {
        Interview interview = requireInterview(id);
        if (interview.getStatus().isTerminal()) {
            throw new BadRequestException(
                    "Cannot reassign interview %s because it is %s".formatted(id, interview.getStatus()));
        }
        interview.setInterviewer(requireInterviewer(interviewerId));
        Interview saved = interviewRepository.save(interview);
        log.info("Assigned interviewer {} to interview {}", interviewerId, id);
        return interviewMapper.toDetailResponse(saved, true, resultRepository.existsByInterviewId(id));
    }

    @Transactional
    public InterviewResponse updateStatus(UUID id, InterviewStatus target) {
        Interview interview = requireInterview(id);
        applyStatusTransition(interview, target);
        Interview saved = interviewRepository.save(interview);
        log.info("Interview {} moved to {}", id, target);
        return interviewMapper.toDetailResponse(saved, true, resultRepository.existsByInterviewId(id));
    }

    @Transactional
    public void delete(UUID id) {
        Interview interview = requireInterview(id);
        interviewRepository.delete(interview);
        log.info("Deleted interview {}", id);
    }

    /**
     * Records the scorecard and closes the interview.
     *
     * <p>Re-submitting overwrites the existing result rather than failing, because a typo in a score is
     * a normal thing to fix and {@code uq_interview_results_interview} guarantees there is only ever
     * one row to correct.
     */
    @Transactional
    public InterviewResultResponse submitResult(
            UUID id, InterviewResultRequest request, AuthenticatedUser currentUser) {

        Interview interview = requireInterview(id);
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw new BadRequestException("Cannot record a result for a cancelled interview");
        }

        InterviewResult result =
                resultRepository.findByInterviewId(id).orElseGet(
                        () -> {
                            InterviewResult created = new InterviewResult();
                            created.setInterview(interview);
                            return created;
                        });

        result.setTechnicalScore(request.technicalScore());
        result.setCommunicationScore(request.communicationScore());
        result.setProblemSolvingScore(request.problemSolvingScore());
        // Derived, never taken from the request.
        result.recalculateOverallScore();
        result.setRecommendation(request.recommendation());
        result.setStrengths(request.strengths());
        result.setImprovements(request.improvements());
        result.setFeedback(request.feedback());
        result.setSubmittedAt(Instant.now());
        userRepository.findById(currentUser.getId()).ifPresent(result::setSubmittedBy);

        InterviewResult saved = resultRepository.save(result);

        // Submitting a result is what completes an interview; requiring a separate status call would
        // leave the dashboard permanently wrong if a client forgot it.
        if (interview.getStatus() != InterviewStatus.COMPLETED) {
            interview.setStatus(InterviewStatus.COMPLETED);
            interviewRepository.save(interview);
        }

        log.info(
                "Recorded result for interview {} (overall {}, {})",
                id,
                saved.getOverallScore(),
                saved.getRecommendation());
        return interviewMapper.toResultResponse(saved);
    }

    // ---------------------------------------------------------------------------------------------
    // AI question generation
    // ---------------------------------------------------------------------------------------------

    /**
     * Generates and stores interview questions.
     *
     * <p>Deliberately not {@code @Transactional}: the AI call can take tens of seconds, and holding a
     * pooled database connection across it would let a slow AI service exhaust the pool. The read and
     * the write each run in their own short transaction, and {@link InterviewQuestionWriter} owns the
     * write.
     */
    public List<InterviewQuestionResponse> generateQuestions(
            UUID id, GenerateQuestionsRequest request, AuthenticatedUser currentUser) {

        Interview interview = requireInterview(id);
        assertVisibleTo(interview, currentUser);
        if (interview.getStatus().isTerminal()) {
            throw new BadRequestException(
                    "Cannot generate questions for interview %s because it is %s"
                            .formatted(id, interview.getStatus()));
        }

        List<String> skills =
                request.focusSkills() == null || request.focusSkills().isEmpty()
                        ? interview.focusSkillList()
                        : request.focusSkills();
        if (skills.isEmpty()) {
            throw new BadRequestException(
                    "The interview has no focus skills; supply focusSkills in the request body");
        }

        AiQuestionSet generated =
                aiQuestionClient.generateQuestions(
                        new AiQuestionRequest(
                                id,
                                interview.getRoleTitle(),
                                interview.getExperienceLevel().name(),
                                skills,
                                request.questionCountOrDefault()));

        List<InterviewQuestion> stored =
                questionWriter.persist(id, generated, request.replaceExistingOrDefault());

        return stored.stream().map(question -> interviewMapper.toQuestionResponse(question, true)).toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    private Interview requireInterview(UUID id) {
        return interviewRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview", id));
    }

    private User requireInterviewer(UUID interviewerId) {
        User user =
                userRepository
                        .findById(interviewerId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", interviewerId));
        if (user.getRole() == Role.CANDIDATE) {
            throw new BadRequestException(
                    "User %s has role CANDIDATE and cannot be assigned as an interviewer".formatted(interviewerId));
        }
        if (!user.isEnabled()) {
            throw new BadRequestException("User %s is disabled".formatted(interviewerId));
        }
        return user;
    }

    private void applyStatusTransition(Interview interview, InterviewStatus target) {
        if (!interview.getStatus().canTransitionTo(target)) {
            throw new BadRequestException(
                    "Illegal status transition %s -> %s".formatted(interview.getStatus(), target));
        }
        interview.setStatus(target);
    }

    /** A candidate may only read interviews attached to their own candidate record. */
    private void assertVisibleTo(Interview interview, AuthenticatedUser currentUser) {
        if (!currentUser.hasRole(Role.CANDIDATE)) {
            return;
        }
        UUID ownCandidateId = resolveOwnCandidateId(currentUser).orElse(null);
        if (ownCandidateId == null || !ownCandidateId.equals(interview.getCandidate().getId())) {
            // AccessDeniedException so the response is a 403 from the standard handler.
            throw new org.springframework.security.access.AccessDeniedException(
                    "This interview does not belong to the authenticated candidate");
        }
    }

    /**
     * Links a {@code CANDIDATE} account to its candidate record by email.
     *
     * <p>Email is the join key because the two tables model different things (an account and a person
     * in the pipeline) and a candidate can exist long before they are given a login.
     */
    private Optional<UUID> resolveOwnCandidateId(AuthenticatedUser currentUser) {
        return candidateRepository.findByEmailIgnoreCase(currentUser.getEmail()).map(Candidate::getId);
    }

    /** One aggregate query for the whole page rather than an EXISTS per row. */
    private Set<UUID> interviewIdsWithResults(List<Interview> interviews) {
        if (interviews.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = interviews.stream().map(Interview::getId).toList();
        return Set.copyOf(resultRepository.findInterviewIdsWithResults(ids));
    }

    private String joinSkills(List<String> skills) {
        String joined =
                skills.stream().map(String::trim).filter(skill -> !skill.isEmpty()).distinct()
                        .reduce((left, right) -> left + "," + right)
                        .orElseThrow(() -> new BadRequestException("At least one non-blank focus skill is required"));
        if (joined.length() > 500) {
            throw new BadRequestException("focusSkills exceeds the 500 character limit when combined");
        }
        return joined;
    }
}
