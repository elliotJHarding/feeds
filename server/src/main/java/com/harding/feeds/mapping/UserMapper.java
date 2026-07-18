package com.harding.feeds.mapping;

import com.harding.feeds.dto.AppUserDto;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.PublicDetails;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public AppUserDto toDto(AppUser user) {
        PublicDetails details = user.getPublicDetails();
        return new AppUserDto()
                .id(user.getId())
                .name(details != null ? details.getName() : null)
                .email(user.getEmail())
                .pictureUrl(details != null ? details.getPictureUrl() : null);
    }
}
