@start
Feature: Delete all the service instances of autosleep in and organization which
         is deregistered from autosleep
         
@deleteInstancesStandardMode
Scenario: Delete service instances in an unenrolled organization with service instance in standard mode
    Given a cloud foundry instance with an unenrolled organization which have service instance in standard mode
    When organization deregister runs
    Then service instances should be deleted

@deleteInstancesTransitiveMode
Scenario: Delete service instances in an unenrolled organization with service instance in transitive mode
    Given a cloud foundry instance with an unenrolled organization which have service instance in transitive mode
    When organization deregister runs
    Then service instances should be deleted
    
@NoDeletionInForcedMode
Scenario: Fail to delete service instances in an unenrolled organization with service instance in forced mode
    Given a cloud foundry instance with an unenrolled organization which have service instance in forced mode
    When organization deregister runs
    Then service instances are not deleted