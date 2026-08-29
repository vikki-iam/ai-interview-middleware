package ai.interview.middleware.mapper;

import ai.interview.middleware.domain.entity.Resume;
import ai.interview.middleware.dto.resume.ResumeResponse;
import org.springframework.stereotype.Component;

@Component
public class ResumeMapper {

    private static final String DOWNLOAD_URL_TEMPLATE = "/api/v1/resumes/%s/download";

    public ResumeResponse toResponse(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getCandidate().getId(),
                resume.getOriginalFilename(),
                resume.getContentType(),
                resume.getSizeBytes(),
                resume.getChecksumSha256(),
                resume.getStorageType(),
                DOWNLOAD_URL_TEMPLATE.formatted(resume.getId()),
                resume.getUploadedAt());
    }
}
