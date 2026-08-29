package ai.interview.middleware.mapper;

import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.enums.CandidateStatus;
import ai.interview.middleware.dto.candidate.CandidateRequest;
import ai.interview.middleware.dto.candidate.CandidateResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CandidateMapper {

    /** Summary view used by list endpoints; the aggregate counts are left unset. */
    public CandidateResponse toResponse(Candidate candidate) {
        return toResponse(candidate, null, null);
    }

    public CandidateResponse toResponse(Candidate candidate, Long resumeCount, Long interviewCount) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.fullName(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getCurrentCompany(),
                candidate.getCurrentPosition(),
                candidate.getYearsOfExperience(),
                candidate.getPrimarySkill(),
                candidate.getLocation(),
                candidate.getStatus(),
                candidate.getNotes(),
                resumeCount,
                interviewCount,
                candidate.getCreatedAt(),
                candidate.getUpdatedAt());
    }

    public Candidate toEntity(CandidateRequest request) {
        Candidate candidate = new Candidate();
        applyRequest(candidate, request);
        candidate.setStatus(request.status() == null ? CandidateStatus.NEW : request.status());
        return candidate;
    }

    /**
     * Copies the mutable fields of a request onto an existing entity. Status is handled by the
     * caller so an update cannot silently reset it to NEW when omitted.
     */
    public void applyRequest(Candidate candidate, CandidateRequest request) {
        candidate.setFirstName(request.firstName().trim());
        candidate.setLastName(request.lastName().trim());
        candidate.setEmail(request.email().trim().toLowerCase());
        candidate.setPhone(emptyToNull(request.phone()));
        candidate.setCurrentCompany(emptyToNull(request.currentCompany()));
        candidate.setCurrentPosition(emptyToNull(request.currentPosition()));
        candidate.setYearsOfExperience(
                request.yearsOfExperience() == null ? BigDecimal.ZERO : request.yearsOfExperience());
        candidate.setPrimarySkill(request.primarySkill().trim());
        candidate.setLocation(emptyToNull(request.location()));
        candidate.setNotes(emptyToNull(request.notes()));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
