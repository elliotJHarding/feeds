package com.harding.feeds.controller;

import com.harding.feeds.api.NapsApi;
import com.harding.feeds.dto.NapDto;
import com.harding.feeds.entity.Nap;
import com.harding.feeds.mapping.NapMapper;
import com.harding.feeds.service.NapService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class NapController implements NapsApi {

    private final NapService napService;
    private final NapMapper napMapper;

    public NapController(NapService napService, NapMapper napMapper) {
        this.napService = napService;
        this.napMapper = napMapper;
    }

    @Override
    public ResponseEntity<List<NapDto>> getNaps(Long babyId, OffsetDateTime from, OffsetDateTime to,
                                                OffsetDateTime updatedSince) {
        List<Nap> naps = napService.getNaps(CurrentUser.get(), babyId, from, to, updatedSince);
        return ResponseEntity.ok(naps.stream().map(napMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<NapDto> createNap(NapDto napDto) {
        NapService.Creation creation = napService.create(
                CurrentUser.get(),
                napDto.getId(),
                napDto.getBabyId(),
                napDto.getStartTime(),
                napDto.getEndTime());

        // 201 for a new nap, 200 for an idempotent replay of an existing id.
        HttpStatus status = creation.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(napMapper.toDto(creation.nap()));
    }

    @Override
    public ResponseEntity<NapDto> updateNap(UUID id, NapDto napDto) {
        Nap nap = napService.update(
                CurrentUser.get(),
                id,
                napDto.getBabyId(),
                napDto.getStartTime(),
                napDto.getEndTime());

        return ResponseEntity.ok(napMapper.toDto(nap));
    }

    @Override
    public ResponseEntity<Void> deleteNap(UUID id) {
        napService.delete(CurrentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
