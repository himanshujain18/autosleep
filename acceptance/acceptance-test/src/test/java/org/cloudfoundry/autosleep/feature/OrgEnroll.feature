@start
Feature: Organization enrollment with autosleep
		 Retrieve details of an organization enrolled with autosleep
		 Un-enroll an organization from autosleep
		 Enroll and Update an organization with autosleep
       
@ScenarioOrganizationFound
Scenario: Successfully retrieve the details of an enrolled organization
    Given a cloud foundry instance with autosleep application deployed on it
    When fetching the organizations enrollment details
    Then should return the organizations details as "{"organizationId":" "<orgid>" ","idle-duration":"PT2M"}"
    And the response status code is 200
	
@ScenarioOrganizationNotFound
Scenario: Fail to retrieve the details of an organization from autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    When fetching the organizations enrollment details
    Then the response status code is 404
    
@ScenarioOrganizationFoundForUnenroll
Scenario: Successfully unenroll an enrolled organization from autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    When deleting the organizations enrollment details
    Then the response status code is 200
    
@ScenarioOrganizationNotFoundForUnenroll
Scenario: Fail to unenroll an organization from autosleep
    Given a cloud foundry instance with autosleep application deployed on it
    When deleting the organizations enrollment details
    Then the response status code is 404
    
@ScenarioUpdateOrganizationWithEmptyBody
Scenario: Updating an existing organization enrolled with autosleep - empty body
    Given a cloud foundry instance with autosleep application deployed on it
    And request body as
    	"""
    	{}
    	"""
    When enrolling organization with autosleep
    Then should return the organizations details as "[{"parameter":"organizationId","value":" "<orgid>" ","error":null}]"
    And the response status code is 200
	    
@ScenarioEnrollOrganizationWithEmptyBody
Scenario: Enrolling a new organization with autosleep - empty body
    Given a cloud foundry instance with autosleep application deployed on it
    And request body as
    	"""
    	{}
    	"""
    When enrolling organization with autosleep
    Then should return the organizations details as "[{"parameter":"organizationId","value":" "<orgid>" ","error":null}]"
    And the response status code is 201

@ScenarioUpdateOrganizationWithBody_IdleDuration
Scenario: Successfully update an existing organization with parameter idle duration
    Given a cloud foundry instance with autosleep application deployed on it
    And request body as
    	"""
    	{"idle-duration":"PT2M"}
    	"""
    When enrolling organization with autosleep
    Then should return the organizations details as "[{"parameter":"organizationId","value":" "<orgid>" ","error":null},{"parameter":"idle-duration","value":"PT2M","error":null}]"
    And the response status code is 200

@ScenarioUpdateOrganizationWithBody_IdleDuration_Failure    
Scenario: Fail to update an existing organization with parameter idle duration
    Given a cloud foundry instance with autosleep application deployed on it
    And request body as
    	"""
    	{"idle-duration":"1 hour"}
    	"""
    When enrolling organization with autosleep
    Then should return the organizations details as
    	"""
    	[{"parameter":"idle-duration","value":null,"error":"idle-duration param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn"}]
    	"""
    And the response status code is 400
    
@ScenarioEnrollOrganizationWithBody_IdleDuration
Scenario: Successfully enroll a new organization with parameter idle duration
    Given a cloud foundry instance with autosleep application deployed on it
    And request body as
    	"""
    	{"idle-duration":"PT2M"}
    	"""
    When enrolling organization with autosleep
    Then should return the organizations details as "[{"parameter":"organizationId","value":" "<orgid>" ","error":null},{"parameter":"idle-duration","value":"PT2M","error":null}]"
    And the response status code is 201
    
@ScenarioInvalidOrgForPUT
Scenario: Enrolling a fake organization
    Given a cloud foundry instance with autosleep application deployed on it
    And an organization with id "fakeId"
    And request body as
    	"""
    	{"idle-duration":"PT2M"}
    	"""
    When enrolling organization with autosleep
    Then should return the organizations details as
    	"""
    	[{"parameter":"fakeId","value":null,"error":"CF-OrganizationNotFound(30003): The organization could not be found: fakeId"}]
    	"""
    And the response status code is 400