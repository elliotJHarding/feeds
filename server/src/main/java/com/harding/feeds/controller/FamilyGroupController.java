package com.harding.feeds.controller;

import com.harding.feeds.api.FamilyGroupApi;
import com.harding.feeds.dto.FamilyGroupDto;
import com.harding.feeds.dto.InviteCodeResponse;
import com.harding.feeds.dto.JoinGroupRequest;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.mapping.FamilyGroupMapper;
import com.harding.feeds.service.FamilyGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FamilyGroupController implements FamilyGroupApi {

    private final FamilyGroupService familyGroupService;
    private final FamilyGroupMapper familyGroupMapper;

    public FamilyGroupController(FamilyGroupService familyGroupService, FamilyGroupMapper familyGroupMapper) {
        this.familyGroupService = familyGroupService;
        this.familyGroupMapper = familyGroupMapper;
    }

    @Override
    public ResponseEntity<FamilyGroupDto> getFamilyGroup() {
        return ResponseEntity.ok(toDto(familyGroupService.getGroup(CurrentUser.get())));
    }

    @Override
    public ResponseEntity<FamilyGroupDto> createFamilyGroup() {
        return ResponseEntity.ok(toDto(familyGroupService.create(CurrentUser.get())));
    }

    @Override
    public ResponseEntity<InviteCodeResponse> getInviteCode() {
        String code = familyGroupService.getOrCreateInviteCode(CurrentUser.get());
        return ResponseEntity.ok(new InviteCodeResponse().code(code));
    }

    @Override
    public ResponseEntity<InviteCodeResponse> regenerateInviteCode() {
        String code = familyGroupService.regenerateInviteCode(CurrentUser.get());
        return ResponseEntity.ok(new InviteCodeResponse().code(code));
    }

    @Override
    public ResponseEntity<FamilyGroupDto> joinFamilyGroup(JoinGroupRequest joinGroupRequest) {
        return ResponseEntity.ok(toDto(familyGroupService.join(CurrentUser.get(), joinGroupRequest.getCode())));
    }

    private FamilyGroupDto toDto(FamilyGroup group) {
        return familyGroupMapper.toDto(group, familyGroupService.membersOf(group));
    }
}
