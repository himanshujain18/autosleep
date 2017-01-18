@start
Feature: Organization enrollment and unenrollment with autosleep
         Enroll a new organization with autosleep and create service instances in all the spaces of that organization
         Unenroll an organization from autosleep and check if service instances are deleted
         
@registerNewOrganization
Scenario: Enroll new organization with autosleep and create service instances in it
    Given a cloud foundry instance with autosleep application deployed on it
    When an organization is enrolled with autosleep
    Then service instances are created in all spaces of the organization
    And applications in the spaces are bounded with the service instance
    
@ScenarioOrganizationFound
Scenario: Successfully retrieve the details of an enrolled organization
    Given a cloud foundry instance with autosleep application deployed on it
    When fetching the organizations enrollment details
    Then should return the organizations details as "{"organizationId":" "<orgid>" ","idle-duration":"PT6M"}"
    And the response status code is 200
    
@updateAlreadyEnrolledOrganization
Scenario: Update service instances in an organization enrolled with autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    And an organization is enrolled with autosleep with service instances in it
    When an organization enrollment is updated with autosleep
    Then service instances are updated in all spaces of the organization
    
@ScenarioOrganizationFoundForUnenroll
Scenario: Successfully unenroll an enrolled organization from autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    When unenrolling an organization from autosleep
    Then the response status code is 200
    And service instances are deleted
    
@ScenarioOrganizationNotFound
Scenario: Fail to retrieve the details of an organization from autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    When fetching the organizations enrollment details
    Then the response status code is 404
    
@ScenarioOrganizationNotFoundForUnenroll
Scenario: Fail to unenroll an organization from autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    When unenrolling an organization from autosleep
    Then the response status code is 404
    
