package com.ticketing.test.controller;

import com.ticketing.global.dto.ApiResponse;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.data.DataInitResponse;
import com.ticketing.test.service.TestDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Test Data", description = "테스트 데이터 생성 API (개발용)")
@Slf4j
@RestController
@RequestMapping("/api/v1/test-data")
@RequiredArgsConstructor
public class TestDataController {

    private final TestDataService testDataService;

    @Operation(summary = "사용자 데이터 생성",
            description = "대량의 테스트 사용자를 생성합니다. 비동기로 실행되며 taskId로 진행상황을 확인할 수 있습니다.")
    @PostMapping("/users")
    public ApiResponse<DataInitResponse> initializeUsers(
            @RequestBody(required = false) DataInitRequest request) {

        if (request == null) {
            request = DataInitRequest.builder().build();
        }

        log.info("사용자 생성 요청: count={}, batchSize={}, threads={}",
                request.getCount(), request.getBatchSize(), request.getThreadCount());

        DataInitResponse response = testDataService.initializeUsers(request);
        return ApiResponse.success("사용자 생성 작업이 시작되었습니다", response);
    }

    @Operation(summary = "이벤트 데이터 생성")
    @PostMapping("/events")
    public ApiResponse<DataInitResponse> initializeEvents(
            @RequestBody(required = false) DataInitRequest request) {

        if (request == null) {
            request = DataInitRequest.builder().build();
        }

        DataInitResponse response = testDataService.initializeEvents(request);
        return ApiResponse.success("이벤트 생성 작업이 시작되었습니다", response);
    }

    @Operation(summary = "티켓 데이터 생성",
            description = "모든 이벤트에 대해 티켓을 생성합니다 (이벤트당 5종류)")
    @PostMapping("/tickets")
    public ApiResponse<DataInitResponse> initializeTickets(
            @RequestBody(required = false) DataInitRequest request) {

        if (request == null) {
            request = DataInitRequest.builder().build();
        }

        DataInitResponse response = testDataService.initializeTickets(request);
        return ApiResponse.success("티켓 생성 작업이 시작되었습니다", response);
    }

    @Operation(summary = "예약 데이터 생성")
    @PostMapping("/reservations")
    public ApiResponse<DataInitResponse> initializeReservations(
            @RequestBody(required = false) DataInitRequest request) {

        if (request == null) {
            request = DataInitRequest.builder().build();
        }

        DataInitResponse response = testDataService.initializeReservations(request);
        return ApiResponse.success("예약 생성 작업이 시작되었습니다", response);
    }

    @Operation(summary = "전체 데이터 생성",
            description = "사용자 → 이벤트 → 티켓 → 예약 순서로 모든 데이터를 생성합니다")
    @PostMapping("/all")
    public ApiResponse<DataInitResponse> initializeAll(
            @RequestBody(required = false) DataInitRequest request) {

        if (request == null) {
            request = DataInitRequest.builder()
                    .count(10000)
                    .batchSize(500)
                    .threadCount(10)
                    .build();
        }

        DataInitResponse response = testDataService.initializeAll(request);
        return ApiResponse.success("전체 데이터 생성 작업이 시작되었습니다", response);
    }

    @Operation(summary = "작업 진행상황 조회",
            description = "taskId로 데이터 생성 작업의 진행상황을 조회합니다")
    @GetMapping("/progress/{taskId}")
    public ApiResponse<DataInitResponse> getProgress(@PathVariable String taskId) {
        DataInitResponse response = testDataService.getProgress(taskId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "작업 완료 후 정리",
            description = "완료된 작업의 진행상황 데이터를 삭제합니다")
    @DeleteMapping("/progress/{taskId}")
    public ApiResponse<Void> cleanupProgress(@PathVariable String taskId) {
        testDataService.cleanupProgress(taskId);
        return ApiResponse.success("작업 데이터가 정리되었습니다", null);
    }
}
