package com.ticketing.test.controller;

import com.ticketing.global.dto.ApiResponse;
import com.ticketing.test.dto.test.AnomalyTestRequest;
import com.ticketing.test.dto.test.AnomalyTestResponse;
import com.ticketing.test.service.AnomalyTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Anomaly Test", description = "이상 거래 테스트 API (개발용)")
@Slf4j
@RestController
@RequestMapping("/api/v1/test-data/anomaly")
@RequiredArgsConstructor
public class  AnomalyTestController {

    private final AnomalyTestService anomalyTestService;

    @Operation(summary = "정상 패턴 예약 생성", 
               description = "정상적인 시간대와 패턴의 예약 데이터를 생성합니다")
    @PostMapping("/normal")
    public ApiResponse<AnomalyTestResponse> generateNormalPattern(
            @RequestBody(required = false) AnomalyTestRequest request) {
        
        if (request == null) {
            request = AnomalyTestRequest.builder().count(100).build();
        }
        
        AnomalyTestResponse response = anomalyTestService.generateNormalReservations(request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "심야 시간대 이상 패턴",
               description = "새벽 1~5시에 집중된 예약 패턴을 생성합니다")
    @PostMapping("/night-time")
    public ApiResponse<AnomalyTestResponse> generateNightTimeAnomaly(
            @RequestBody(required = false) AnomalyTestRequest request) {
        
        if (request == null) {
            request = AnomalyTestRequest.builder().count(50).build();
        }
        
        AnomalyTestResponse response = anomalyTestService.generateNightTimeAnomaly(request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "단시간 대량 예약 패턴 (봇)", 
               description = "1분 이내에 다수의 예약을 생성하는 패턴입니다")
    @PostMapping("/bulk")
    public ApiResponse<AnomalyTestResponse> generateBulkAnomaly(
            @RequestBody(required = false) AnomalyTestRequest request) {
        
        if (request == null) {
            request = AnomalyTestRequest.builder().count(30).build();
        }
        
        AnomalyTestResponse response = anomalyTestService.generateBulkReservationAnomaly(request);
        return ApiResponse.success(response);
    }

//    @Operation(summary = "고가 티켓 반복 구매 패턴",
//               description = "VIP석 등 고가 티켓을 반복적으로 구매하는 패턴입니다")
//    @PostMapping("/high-price")
//    public ApiResponse<AnomalyTestResponse> generateHighPriceAnomaly(
//            @RequestBody(required = false) AnomalyTestRequest request) {
//
//        if (request == null) {
//            request = AnomalyTestRequest.builder().count(20).build();
//        }
//
//        AnomalyTestResponse response = anomalyTestService.generateHighPriceAnomaly(request);
//        return ApiResponse.success(response);
//    }

    @Operation(summary = "동일 IP 다중 계정 패턴", 
               description = "같은 IP에서 여러 계정으로 예약하는 패턴입니다")
    @PostMapping("/same-ip")
    public ApiResponse<AnomalyTestResponse> generateSameIpAnomaly(
            @RequestBody(required = false) AnomalyTestRequest request) {
        
        if (request == null) {
            request = AnomalyTestRequest.builder().count(40).build();
        }
        
        AnomalyTestResponse response = anomalyTestService.generateSameIpAnomaly(request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "혼합 패턴 생성", 
               description = "정상(80%) + 다양한 이상 패턴(20%)을 혼합하여 생성합니다")
    @PostMapping("/mixed")
    public ApiResponse<AnomalyTestResponse> generateMixedPatterns(
            @RequestBody(required = false) AnomalyTestRequest request) {
        
        if (request == null) {
            request = AnomalyTestRequest.builder().count(500).build();
        }
        
        AnomalyTestResponse response = anomalyTestService.generateMixedPatterns(request);
        return ApiResponse.success(response);
    }
}