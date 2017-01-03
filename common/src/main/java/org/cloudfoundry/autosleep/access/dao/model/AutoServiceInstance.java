package org.cloudfoundry.autosleep.access.dao.model;

import com.fasterxml.jackson.annotation.JsonProperty; 
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.Id;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode(of = {"serviceInstanceId"})
@Entity
public class AutoServiceInstance {
    
    @Id
    @JsonProperty
    private String serviceInstanceId;
    
    @JsonProperty
    private String spaceId;
    
    @JsonProperty
    private String organizationId;

}
