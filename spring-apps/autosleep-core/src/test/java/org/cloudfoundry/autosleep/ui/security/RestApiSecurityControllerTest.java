package org.cloudfoundry.autosleep.ui.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("")
public class RestApiSecurityControllerTest {

    @RequestMapping(value = {"/dashboard/**","/css/**","/fonts/**","/javascript/**",
            "/api/services/*/applications","/**"}, method = RequestMethod.GET)
    public ResponseEntity<Void> get_request_for_fixed_paths() {
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}