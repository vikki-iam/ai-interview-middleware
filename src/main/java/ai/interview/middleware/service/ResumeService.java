package ai.interview.middleware.service;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.entity.Resume;
import ai.interview.middleware.dto.resume.ResumeResponse;
import ai.interview.middleware.exception.BadRequestException;
import ai.interview.middleware.exception.ResourceNotFoundException;
import ai.interview.middleware.mapper.ResumeMapper;
import ai.interview.middleware.repository.CandidateRepository;
import ai.interview.middleware.repository.ResumeRepository;
import ai.interview.middleware.repository.UserRepository;
import ai.interview.middleware.security.AuthenticatedUser;
import ai.interview.middleware.service.storage.FileStorageService;
import ai.interview.middleware.service.storage.StorageKeyFactory;
import ai.interview.middleware.service.storage.StoredFile;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resume upload, download and deletion.
 *
 * <p>Metadata lives in PostgreSQL; the bytes go to whichever {@link FileStorageService} is active.
 * Nothing here knows whether that is local disk or S3, which is what makes the production switch a
 * Helm value.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ResumeMapper resumeMapper;
    private final long maxFileSizeBytes;
    private final List<String> allowedContentTypes;

    public ResumeService(
            ResumeRepository resumeRepository,
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            ResumeMapper resumeMapper,
            AppProperties properties) {
        this.resumeRepository = resumeRepository;
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.resumeMapper = resumeMapper;
        this.maxFileSizeBytes = properties.storage().maxFileSizeBytes();
        this.allowedContentTypes = properties.storage().allowedContentTypes();
    }

    /** The bytes of a stored resume plus what a browser needs to save it. */
    public record ResumeDownload(byte[] content, String filename, String contentType) {}

    @Transactional
    public ResumeResponse upload(UUID candidateId, MultipartFile file, AuthenticatedUser currentUser) {
        Candidate candidate =
                candidateRepository
                        .findById(candidateId)
                        .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));

        byte[] content = readAndValidate(file);

        StoredFile stored =
                fileStorageService.store(
                        candidateId, file.getOriginalFilename(), resolveContentType(file), content);

        Resume resume = new Resume();
        resume.setCandidate(candidate);
        resume.setOriginalFilename(StorageKeyFactory.sanitise(file.getOriginalFilename()));
        resume.setStorageType(stored.storageType());
        resume.setStorageKey(stored.storageKey());
        resume.setContentType(resolveContentType(file));
        resume.setSizeBytes(stored.sizeBytes());
        resume.setChecksumSha256(stored.checksumSha256());
        resume.setUploadedAt(Instant.now());
        userRepository.findById(currentUser.getId()).ifPresent(resume::setUploadedBy);

        Resume saved = resumeRepository.save(resume);
        log.info(
                "Stored resume {} for candidate {} ({} bytes, {})",
                saved.getId(),
                candidateId,
                stored.sizeBytes(),
                stored.storageType());
        return resumeMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> listForCandidate(UUID candidateId) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate", candidateId);
        }
        return resumeRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .map(resumeMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse findById(UUID resumeId) {
        return resumeMapper.toResponse(requireResume(resumeId));
    }

    @Transactional(readOnly = true)
    public ResumeDownload download(UUID resumeId) {
        Resume resume = requireResume(resumeId);
        byte[] content = fileStorageService.retrieve(resume.getStorageKey());
        return new ResumeDownload(content, resume.getOriginalFilename(), resume.getContentType());
    }

    /**
     * Removes the stored object first, then the row.
     *
     * <p>If the object delete fails the transaction rolls back and the row survives, so the platform
     * never ends up with an unreferenced file quietly consuming storage.
     */
    @Transactional
    public void delete(UUID resumeId) {
        Resume resume = requireResume(resumeId);
        fileStorageService.delete(resume.getStorageKey());
        resumeRepository.delete(resume);
        log.info("Deleted resume {}", resumeId);
    }

    private Resume requireResume(UUID resumeId) {
        return resumeRepository
                .findWithCandidateById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", resumeId));
    }

    /**
     * Validates size and declared type, then reads the bytes.
     *
     * <p>The size check is duplicated from {@code spring.servlet.multipart.max-file-size} on purpose:
     * the container limit produces a generic 413, while this produces a message that names the actual
     * and permitted sizes.
     */
    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Resume file is required and must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException(
                    "Resume is %d bytes; the limit is %d bytes".formatted(file.getSize(), maxFileSizeBytes));
        }
        String contentType = resolveContentType(file);
        if (!allowedContentTypes.contains(contentType)) {
            throw new BadRequestException(
                    "Unsupported content type '%s'. Allowed: %s"
                            .formatted(contentType, String.join(", ", allowedContentTypes)));
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file: " + e.getMessage());
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return StringUtils.hasText(contentType) ? contentType.toLowerCase() : "application/octet-stream";
    }
}
