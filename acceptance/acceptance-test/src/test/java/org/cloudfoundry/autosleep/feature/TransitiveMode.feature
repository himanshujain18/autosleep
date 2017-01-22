@start
Feature: Transitive mode functionality of autosleep service instances
		 Autosleep service instances in transitive mode have transient opt outs.
		 Autosleep service instances in transitive mode can be deleted.
		 
@transientOptOut
Scenario: Service instance in transitive mode are temporarily opted out after unbinding
    Given a cloud foundry instance with autosleep application deployed on it
    And an autosleep service instance in transitive mode is present in a space of an organization
    And an application is bounded with the service instance
    When the application is unbinded from the service instance
    Then the application gets bounded with the service instance in next scan
    
@transientmodeInstanceDeletion
Scenario: Service instance in transitive mode can be deleted
    Given a cloud foundry instance with autosleep application deployed on it
    And an autosleep service instance in transitive mode is present in a space of an organization
    When we delete the service instance
    Then the service instance gets deleted from the space within the organization