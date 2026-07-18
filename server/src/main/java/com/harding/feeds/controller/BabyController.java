package com.harding.feeds.controller;

import com.harding.feeds.api.BabiesApi;
import com.harding.feeds.dto.BabyDto;
import com.harding.feeds.mapping.BabyMapper;
import com.harding.feeds.service.BabyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BabyController implements BabiesApi {

    private final BabyService babyService;
    private final BabyMapper babyMapper;

    public BabyController(BabyService babyService, BabyMapper babyMapper) {
        this.babyService = babyService;
        this.babyMapper = babyMapper;
    }

    @Override
    public ResponseEntity<List<BabyDto>> getBabies() {
        List<BabyDto> babies = babyService.getBabies(CurrentUser.get()).stream()
                .map(babyMapper::toDto)
                .toList();
        return ResponseEntity.ok(babies);
    }

    @Override
    public ResponseEntity<BabyDto> createBaby(BabyDto babyDto) {
        return ResponseEntity.ok(babyMapper.toDto(
                babyService.create(CurrentUser.get(), babyDto.getName(), babyDto.getDateOfBirth())));
    }

    @Override
    public ResponseEntity<BabyDto> updateBaby(Long id, BabyDto babyDto) {
        return ResponseEntity.ok(babyMapper.toDto(
                babyService.update(CurrentUser.get(), id, babyDto.getName(), babyDto.getDateOfBirth())));
    }
}
