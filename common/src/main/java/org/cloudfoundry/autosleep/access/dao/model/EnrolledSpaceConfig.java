package org.cloudfoundry.autosleep.access.dao.model;

import java.time.Duration;
import java.util.regex.Pattern;

import org.cloudfoundry.autosleep.config.Config;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString
public class EnrolledSpaceConfig {
    
    private String spaceId;
    
    private String organizationId;
    
    private Duration idleDuration;
    
    private Config.ServiceInstanceParameters.Enrollment autoEnrollment;
    
    private Pattern excludeFromAutoEnrollment;
}