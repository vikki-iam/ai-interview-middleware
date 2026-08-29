package ai.interview.middleware.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.entity.Resume;
import ai.interview.middleware.domain.enums.CandidateStatus;
import ai.interview.middleware.domain.enums.Role;
import ai.interview.middleware.domain.enums.StorageType;
import ai.interview.middleware.dto.candidate.CandidateRequest;
import ai.interview.middleware.dto.candidate.CandidateResponse;
import ai.interview.middleware.exception.DuplicateResourceException;
import ai.interview.middleware.exception.ResourceNotFoundException;
import ai.interview.middleware.mapper.CandidateMapper;
import ai.interview.middleware.repository.CandidateRepository;
import ai.interview.middleware.repository.InterviewRepository;
import ai.interview.middleware.repository.ResumeRepository;
import ai.interview.middleware.repository.UserRepository;
import ai.interview.middleware.security.AuthenticatedUser;
import ai.interview.middleware.service.storage.FileStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Candidate service behaviour.
 *
 * <p>Mockito rather than a Spring context: these tests are about the service's own decisions
 * (duplicate detection, email normalisation, deleting stored files before rows), none of which need a
 * database to verify.
 */
@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock private CandidateRepository candidateRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private InterviewRepository interviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;

    // The mapper is pure translation with no collaborators, so the real one gives the test more
    // coverage than a stub would.
    @Spy private CandidateMapper candidateMapper = new CandidateMapper();

    @InjectMocks private CandidateService candidateService;

    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = AuthenticatedUser.fromEntity(adminUser());
    }

    private ai.interview.middleware.domain.entity.User adminUser() {
        ai.interview.middleware.domain.entity.User user =
                new ai.interview.middleware.domain.entity.User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@aiinterview.local");
        user.setFullName("Platform Administrator");
        user.setRole(Role.ADMIN);
        user.setPasswordHash("$2b$10$irrelevant");
        user.setEnabled(true);
        return user;
    }

    private CandidateRequest request(String email) {
        return new CandidateRequest(
                "  Neha  ",
                "  Gupta ",
                email,
                "+91-98200-11201",
                "Infobell Systems",
                "Senior DevOps Engineer",
                new BigDecimal("7.5"),
                " Kubernetes ",
                "Bengaluru, IN",
                null,
                "Referred by Priya.");
    }

    @Test
    @DisplayName("create trims input and normalises the email to lower case")
    void createNormalisesInput() {
        when(candidateRepository.existsByEmailIgnoreCase("neha.gupta@example.com")).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(call -> call.getArgument(0));
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(adminUser()));

        CandidateResponse response =
                candidateService.create(request("  NEHA.Gupta@Example.com "), currentUser);

        ArgumentCaptor<Candidate> saved = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository).save(saved.capture());

        assertThat(saved.getValue().getFirstName()).isEqualTo("Neha");
        assertThat(saved.getValue().getLastName()).isEqualTo("Gupta");
        assertThat(saved.getValue().getEmail()).isEqualTo("neha.gupta@example.com");
        assertThat(saved.getValue().getPrimarySkill()).isEqualTo("Kubernetes");
        assertThat(response.email()).isEqualTo("neha.gupta@example.com");
    }

    @Test
    @DisplayName("create defaults a candidate with no status to NEW")
    void createDefaultsStatusToNew() {
        when(candidateRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(call -> call.getArgument(0));
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.empty());

        CandidateResponse response = candidateService.create(request("new@example.com"), currentUser);

        assertThat(response.status()).isEqualTo(CandidateStatus.NEW);
    }

    /** The pre-check exists to produce a clear 409 rather than a constraint violation at flush. */
    @Test
    @DisplayName("create rejects a duplicate email without touching the repository")
    void createRejectsDuplicateEmail() {
        when(candidateRepository.existsByEmailIgnoreCase("neha.gupta@example.com")).thenReturn(true);

        assertThatThrownBy(() -> candidateService.create(request("neha.gupta@example.com"), currentUser))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("neha.gupta@example.com");

        verify(candidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("update keeps the existing status when the request omits it")
    void updatePreservesStatusWhenOmitted() {
        UUID id = UUID.randomUUID();
        Candidate existing = new Candidate();
        existing.setId(id);
        existing.setEmail("neha.gupta@example.com");
        existing.setStatus(CandidateStatus.INTERVIEWING);

        when(candidateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(call -> call.getArgument(0));

        CandidateResponse response = candidateService.update(id, request("neha.gupta@example.com"));

        assertThat(response.status()).isEqualTo(CandidateStatus.INTERVIEWING);
    }

    @Test
    @DisplayName("update allows keeping the same email on the same record")
    void updateAllowsUnchangedEmail() {
        UUID id = UUID.randomUUID();
        Candidate existing = new Candidate();
        existing.setId(id);
        existing.setEmail("neha.gupta@example.com");
        existing.setStatus(CandidateStatus.NEW);

        when(candidateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(call -> call.getArgument(0));

        candidateService.update(id, request("NEHA.GUPTA@example.com"));

        // The uniqueness probe must not run for the record's own address, or every save would 409.
        verify(candidateRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("update rejects an email already used by another candidate")
    void updateRejectsEmailTakenByAnother() {
        UUID id = UUID.randomUUID();
        Candidate existing = new Candidate();
        existing.setId(id);
        existing.setEmail("original@example.com");
        existing.setStatus(CandidateStatus.NEW);

        when(candidateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(candidateRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> candidateService.update(id, request("taken@example.com")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(candidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("a missing candidate is a 404, not a null response")
    void missingCandidateRaisesNotFound() {
        UUID id = UUID.randomUUID();
        when(candidateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    /**
     * Stored objects must be removed before the rows cascade away, otherwise a failure would leave
     * files in the bucket that nothing references and nothing will ever clean up.
     */
    @Test
    @DisplayName("delete removes every stored resume file before deleting the candidate")
    void deleteRemovesStoredFilesFirst() {
        UUID id = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(id);

        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));
        when(resumeRepository.findByCandidateIdOrderByUploadedAtDesc(id))
                .thenReturn(List.of(resume("candidates/a/one.pdf"), resume("candidates/a/two.pdf")));

        candidateService.delete(id);

        verify(fileStorageService).delete("candidates/a/one.pdf");
        verify(fileStorageService).delete("candidates/a/two.pdf");
        verify(candidateRepository).delete(candidate);
    }

    @Test
    @DisplayName("delete of a candidate with no resumes touches no storage")
    void deleteWithoutResumesSkipsStorage() {
        UUID id = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(id);

        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));
        when(resumeRepository.findByCandidateIdOrderByUploadedAtDesc(id)).thenReturn(List.of());

        candidateService.delete(id);

        verify(fileStorageService, never()).delete(anyString());
        verify(candidateRepository, times(1)).delete(candidate);
    }

    @Test
    @DisplayName("findById reports resume and interview counts")
    void findByIdIncludesCounts() {
        UUID id = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setFirstName("Neha");
        candidate.setLastName("Gupta");
        candidate.setStatus(CandidateStatus.NEW);

        when(candidateRepository.findById(id)).thenReturn(Optional.of(candidate));
        when(resumeRepository.countByCandidateId(id)).thenReturn(2L);
        when(interviewRepository.countByCandidateId(id)).thenReturn(3L);

        CandidateResponse response = candidateService.findById(id);

        assertThat(response.resumeCount()).isEqualTo(2L);
        assertThat(response.interviewCount()).isEqualTo(3L);
        assertThat(response.fullName()).isEqualTo("Neha Gupta");
    }

    private Resume resume(String storageKey) {
        Resume resume = new Resume();
        resume.setStorageKey(storageKey);
        resume.setStorageType(StorageType.LOCAL);
        return resume;
    }
}
