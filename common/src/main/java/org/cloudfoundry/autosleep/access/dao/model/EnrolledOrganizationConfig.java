package org.cloudfoundry.autosleep.access.dao.model;

import java.time.Duration;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode(of = {"organizationId"})
@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrolledOrganizationConfig {
    
    @Id
    @JsonProperty
    private String organizationId;
    
    @JsonProperty(value = "idle-duration")
    @Lob
    private Duration idleDuration;   
    
}