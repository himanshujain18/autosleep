/*
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

package org.cloudfoundry.autosleep.worker.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.util.TimeManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
public class Clock {

    @Autowired
    private TimeManager timeManager;

    private final Map<String/*taskId*/, ScheduledFuture<?>> tasks = new HashMap<>();

    /**
     * Access to the task ids.
     * @return a read-only set containing the ids of the current tasks
     */
    public Set<String> listTaskIds() {
        return Collections.unmodifiableSet(tasks.keySet());
    }

    /**
     * Remove a task by its id.
     * @param id task id, will be used to cancel it
     */
    public void removeTask(String id) {
        log.debug("removeTask - task {}", id);
        tasks.remove(id);
    }

    /**
     * Schedule a Runnable to be run after a certain delay.
     * @param id       task id, will be used to remove it
     * @param duration the time to wait before execution
     * @param action   Runnable to call
     */
    public void scheduleTask(String id, Duration duration, Runnable action) {
        log.debug("scheduleTask - task {}", id);
        
      //Required to cancel the already set time for this thread as per earlier Duraiton
        ScheduledFuture<?> oldHandle = tasks.get(id);        
        if(oldHandle != null) {
            oldHandle.cancel(true);
        }
        
        ScheduledFuture<?> handle = timeManager.schedule(action, duration);
        tasks.put(id, handle);
        
        //For TESTING
        Iterator<Map.Entry<String/*taskId*/, ScheduledFuture<?>>> entries = tasks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String/*taskId*/, ScheduledFuture<?>> entry = entries.next();
            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
        }
        
    }

}
