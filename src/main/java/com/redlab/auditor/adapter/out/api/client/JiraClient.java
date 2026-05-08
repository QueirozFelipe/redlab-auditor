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
public interface JiraClient {

    @GET
    @Path("/rest/api/3/version/{id}")
    JsonNode getVersion(
            @PathParam("id") String id,
            @HeaderParam("Authorization") String authHeader
    );

    @GET
    @Path("/rest/api/3/project/{projectIdOrKey}")
    JsonNode getProject(
            @PathParam("projectIdOrKey") String projectIdOrKey,
            @HeaderParam("Authorization") String authHeader
    );

    @GET
    @Path("/rest/api/3/issuetype")
    JsonNode getIssueTypes(@HeaderParam("Authorization") String authHeader);

    @GET
    @Path("/rest/api/3/search/jql")
    JsonNode searchIssues(
            @QueryParam("jql") String jql,
            @QueryParam("startAt") int startAt,
            @QueryParam("maxResults") int maxResults,
            @QueryParam("fields") String fields,
            @HeaderParam("Authorization") String authHeader
    );
}