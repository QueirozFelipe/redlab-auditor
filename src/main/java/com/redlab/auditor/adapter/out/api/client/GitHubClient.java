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

import java.util.List;

@RegisterProvider(ApiResponseExceptionHandler.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubClient {

    @GET
    @Path("/orgs/{org}/repos")
    List<JsonNode> getOrgRepos(
            @PathParam("org") String org,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page,
            @HeaderParam("Authorization") String token,
            @HeaderParam("X-GitHub-Api-Version") String apiVersion);

    @GET
    @Path("/users/{user}/repos")
    List<JsonNode> getUserRepos(
            @PathParam("user") String user,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page,
            @HeaderParam("Authorization") String token,
            @HeaderParam("X-GitHub-Api-Version") String apiVersion);

    @GET
    @Path("/repos/{owner}/{repo}/compare/{base}...{head}")
    JsonNode compareBranches(
            @PathParam("owner") String owner,
            @PathParam("repo") String repo,
            @PathParam("base") String base,
            @PathParam("head") String head,
            @HeaderParam("Authorization") String token,
            @HeaderParam("X-GitHub-Api-Version") String apiVersion);
}