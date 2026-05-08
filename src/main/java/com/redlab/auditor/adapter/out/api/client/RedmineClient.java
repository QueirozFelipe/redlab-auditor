/*
 * Copyright 2026 Felipe Queiroz
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

package com.redlab.auditor.adapter.out.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.redlab.auditor.infrastructure.client.ApiResponseExceptionHandler;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

@RegisterProvider(ApiResponseExceptionHandler.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface RedmineClient {

    @GET
    @Path("/versions/{id}.json")
    JsonNode getVersion(
            @PathParam("id") String id,
            @HeaderParam("X-Redmine-API-Key") String token
    );

    @GET
    @Path("/issues.json")
    JsonNode getIssues(
            @QueryParam("fixed_version_id") String versionId,
            @QueryParam("status_id") String status,
            @QueryParam("limit") int limit,
            @QueryParam("offset") int offset,
            @QueryParam("tracker_id") String trackerIds,
            @HeaderParam("X-Redmine-API-Key") String token
    );

    @GET
    @Path("/trackers.json")
    JsonNode getTrackers(@HeaderParam("X-Redmine-API-Key") String token);
}