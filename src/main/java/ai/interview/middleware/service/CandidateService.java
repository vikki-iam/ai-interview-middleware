package ai.interview.middleware.service;

import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.entity.Resume;
import ai.interview.middleware.domain.enums.CandidateStatus;
import ai.interview.middleware.dto.candidate.CandidateRequest;
import ai.interview.middleware.dto.candidate.CandidateResponse;
import ai.interview.middleware.exception.DuplicateResourceException;
import ai.interview.middleware.exception.ResourceNotFoundException;
import ai.interview.middleware.mapper.CandidateMapper;
import ai.interview.middleware.repository.CandidateRepository;
import ai.interview.middleware.repository.InterviewRepository;
import ai.interview.middleware.repository.ResumeRepository;
import ai.interview.middleware.repository.UserRepository;
import ai.interview.middleware.repository.spec.CandidateSpecifications;
import ai.interview.middleware.security.AuthenticatedUser;
import ai.interview.middleware.service.storage.FileStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Candidate CRUD and search. */
@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepository;
    private final ResumeRepository resumeRepository;
    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final CandidateMapper candidateMapper;

    public CandidateService(
            CandidateRepository candidateRepository,
            ResumeRepository resumeRepository,
            InterviewRepository interviewRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            CandidateMapper candidateMapper) {
        this.candidateRepository = candidateRepository;
        this.resumeRepository = resumeRepository;
        this.interviewRepository = interviewRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.candidateMapper = candidateMapper;
    }

    /**
     * Paged search. Filters are composed so the generated SQL contains only the predicates the caller
     * asked for.
     */
    @Transactional(readOnly = true)
    public Page<CandidateResponse> search(
            String searchTerm,
            CandidateStatus status,
            String primarySkill,
            BigDecimal minExperience,
            Pageable pageable) {

        Specification<Candidate> specification = CandidateSpecifications.unfiltered();
        if (StringUtils.hasText(searchTerm)) {
            specification = specification.and(CandidateSpecifications.matchesText(searchTerm.trim()));
        }
        if (status != null) {
            specification = specification.and(CandidateSpecifications.hasStatus(status));
        }
        if (StringUtils.hasText(primarySkill)) {
            specification = specification.and(CandidateSpecifications.hasPrimarySkill(primarySkill.trim()));
        }
        if (minExperience != null) {
            specification = specification.and(CandidateSpecifications.hasMinimumExperience(minExperience));
        }

        return candidateRepository.findAll(specification, pageable).map(candidateMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CandidateResponse findById(UUID id) {
        Candidate candidate = requireCandidate(id);
        return candidateMapper.toResponse(
                candidate,
                resumeRepository.countByCandidateId(id),
                interviewRepository.countByCandidateId(id));
    }

    /** Returns the entity for use by other services; never leaves the service layer. */
    @Transactional(readOnly = true)
    public Candidate requireCandidate(UUID id) {
        return candidateRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", id));
    }

    @Transactional
    public CandidateResponse create(CandidateRequest request, AuthenticatedUser currentUser) {
        String email = request.email().trim().toLowerCase();
        // Checked up front so the caller gets a 409 with a clear message rather than a constraint
        // violation surfacing from the flush. The unique index is still the real guard against a race.
        if (candidateRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Candidate", "email", email);
        }

        Candidate candidate = candidateMapper.toEntity(request);
        userRepository.findById(currentUser.getId()).ifPresent(candidate::setCreatedBy);

        Candidate saved = candidateRepository.save(candidate);
        log.info("Created candidate {} ({})", saved.getId(), saved.getEmail());
        return candidateMapper.toResponse(saved, 0L, 0L);
    }

    @Transactional
    public CandidateResponse update(UUID id, CandidateRequest request) {
        Candidate candidate = requireCandidate(id);
        String email = request.email().trim().toLowerCase();

        if (!email.equalsIgnoreCase(candidate.getEmail())
                && candidateRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Candidate", "email", email);
        }

        candidateMapper.applyRequest(candidate, request);
        // Omitting status on an update leaves it alone rather than resetting it to NEW.
        if (request.status() != null) {
            candidate.setStatus(request.status());
        }

        Candidate saved = candidateRepository.save(candidate);
        log.info("Updated candidate {}", saved.getId());
        return candidateMapper.toResponse(
                saved,
                resumeRepository.countByCandidateId(id),
                interviewRepository.countByCandidateId(id));
    }

    /**
     * Deletes a candidate along with their resumes and interviews.
     *
     * <p>Stored resume bytes are removed first: the database rows go away by cascade, so if the object
     * deletions were left until after the commit a failure would orphan files with no row pointing at
     * them. Doing it in this order can instead leave a row whose bytes are gone, which the download
     * endpoint already reports as a 404.
     */
    @Transactional
    public void delete(UUID id) {
        Candidate candidate = requireCandidate(id);
        List<Resume> resumes = resumeRepository.findByCandidateIdOrderByUploadedAtDesc(id);
        for (Resume resume : resumes) {
            fileStorageService.delete(resume.getStorageKey());
        }
        candidateRepository.delete(candidate);
        log.info("Deleted candidate {} along with {} resume(s)", id, resumes.size());
    }
}
