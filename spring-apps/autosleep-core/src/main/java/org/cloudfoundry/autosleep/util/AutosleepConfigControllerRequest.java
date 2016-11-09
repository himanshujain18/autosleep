package org.cloudfoundry.autosleep.util;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public class AutosleepConfigControllerRequest {

    @JsonProperty
    private String organizationId;

    @JsonProperty(value = "idle-duration")
    private String idleDuration;

}
