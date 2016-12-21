package org.cloudfoundry.autosleep.access.dao.model;

import java.util.regex.Pattern;

import javax.persistence.Id;

import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.util.serializer.PatternDeserializer;
import org.cloudfoundry.autosleep.util.serializer.PatternSerializer;

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
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString
@EqualsAndHashCode(of = {"spaceId"})
public class EnrolledSpaceConfig {

    @Id
    @JsonProperty
    private String spaceId;

    @JsonProperty
    private String organizationId;

    @JsonProperty
    private String idleDuration;

    @JsonProperty
    private Config.ServiceInstanceParameters.Enrollment autoEnrollment;

    @JsonSerialize(using = PatternSerializer.class)
    @JsonDeserialize(using = PatternDeserializer.class)
    private Pattern excludeFromAutoEnrollment;


}
