package ai.interview.middleware.domain.entity;

import ai.interview.middleware.domain.enums.Recommendation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** The single scorecard for a completed interview. */
@Entity
@Table(name = "interview_results")
public class InterviewResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false, unique = true)
    private Interview interview;

    @Column(name = "technical_score", nullable = false, precision = 4, scale = 1)
    private BigDecimal technicalScore;

    @Column(name = "communication_score", nullable = false, precision = 4, scale = 1)
    private BigDecimal communicationScore;

    @Column(name = "problem_solving_score", nullable = false, precision = 4, scale = 1)
    private BigDecimal problemSolvingScore;

    @Column(name = "overall_score", nullable = false, precision = 4, scale = 1)
    private BigDecimal overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false, length = 20)
    private Recommendation recommendation;

    @Column(name = "strengths", columnDefinition = "text")
    private String strengths;

    @Column(name = "improvements", columnDefinition = "text")
    private String improvements;

    @Column(name = "feedback", nullable = false, columnDefinition = "text")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /**
     * Derives the overall score as the mean of the three dimensions, so a client cannot submit a
     * headline score that contradicts its own breakdown.
     */
    public void recalculateOverallScore() {
        BigDecimal sum = technicalScore.add(communicationScore).add(problemSolvingScore);
        this.overallScore = sum.divide(BigDecimal.valueOf(3), 1, RoundingMode.HALF_UP);
    }

    public Interview getInterview() {
        return interview;
    }

    public void setInterview(Interview interview) {
        this.interview = interview;
    }

    public BigDecimal getTechnicalScore() {
        return technicalScore;
    }

    public void setTechnicalScore(BigDecimal technicalScore) {
        this.technicalScore = technicalScore;
    }

    public BigDecimal getCommunicationScore() {
        return communicationScore;
    }

    public void setCommunicationScore(BigDecimal communicationScore) {
        this.communicationScore = communicationScore;
    }

    public BigDecimal getProblemSolvingScore() {
        return problemSolvingScore;
    }

    public void setProblemSolvingScore(BigDecimal problemSolvingScore) {
        this.problemSolvingScore = problemSolvingScore;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(BigDecimal overallScore) {
        this.overallScore = overallScore;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getImprovements() {
        return improvements;
    }

    public void setImprovements(String improvements) {
        this.improvements = improvements;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(User submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
