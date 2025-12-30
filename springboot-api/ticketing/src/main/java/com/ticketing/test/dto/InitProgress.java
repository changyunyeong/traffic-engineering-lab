package com.ticketing.test.dto;

import com.ticketing.test.dto.data.DataInitResponse;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class InitProgress {

    private final String taskId;
    private final String dataType;
    private final int targetCount;
    // Atomic: 멀티스레드 환경에서 동시성 보장
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalTimeMs = new AtomicLong(0);
    private final LocalDateTime startedAt;
    private String status = "RUNNING";  // RUNNING, COMPLETED, FAILED
    private LocalDateTime completedAt;

    public InitProgress(String taskId, String dataType, int targetCount) {
        this.taskId = taskId;
        this.dataType = dataType;
        this.targetCount = targetCount;
        this.startedAt = LocalDateTime.now();
    }

    public void incrementCompleted(int count) {
        completedCount.addAndGet(count);
    }

    public void incrementError(int count) {
        errorCount.addAndGet(count);
    }

    public void addTime(long milliseconds) {
        totalTimeMs.addAndGet(milliseconds);
    }

    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
    }

    public double getProgressPercent() {
        return (completedCount.get() * 100.0) / targetCount;
    }

    public long getElapsedSeconds() {
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, endTime).getSeconds();
    }

    public DataInitResponse toResponse() {
        return DataInitResponse.builder()
                .taskId(taskId)
                .status(status)
                .dataType(dataType)
                .targetCount(targetCount)
                .completedCount(completedCount.get())
                .errorCount(errorCount.get())
                .progressPercent(getProgressPercent())
                .elapsedSeconds(getElapsedSeconds())
                .startedAt(startedAt)
                .completedAt(completedAt)
                .build();
    }
}