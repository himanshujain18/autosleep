/**
 * Autosleep
 * Copyright (C) 2016 Orange
 * Authors: Benjamin Einaudi   benjamin.einaudi@orange.com
 *          Arnaud Ruffin      arnaud.ruffin@orange.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.autosleep.worker.remote.model;

import lombok.Builder;
import lombok.Getter;
import org.cloudfoundry.autosleep.dao.model.ApplicationInfo;

@Getter
@Builder
public class ApplicationActivity {

    private ApplicationIdentity application;

    private ApplicationInfo.DiagnosticInfo.ApplicationEvent lastEvent;

    private ApplicationInfo.DiagnosticInfo.ApplicationLog lastLog;

    private String state;

}