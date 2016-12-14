package org.cloudfoundry.autosleep.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AutosleepConfigControllerResponse {
 
    @JsonProperty
    String parameter;
    
    @JsonProperty
    String value;
    
    @JsonProperty
    String error;

}
