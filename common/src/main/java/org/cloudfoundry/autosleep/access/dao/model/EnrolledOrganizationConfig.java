package org.cloudfoundry.autosleep.access.dao.model;

import java.time.Duration;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.cloudfoundry.autosleep.util.serializer.IntervalDeserializer;
import org.cloudfoundry.autosleep.util.serializer.IntervalSerializer;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
    
    @JsonSerialize(using = IntervalSerializer.class)
    @JsonDeserialize(using = IntervalDeserializer.class)
    @Column(columnDefinition = "BLOB")
    @JsonProperty(value = "idle-duration")
    private Duration idleDuration;    
    
}