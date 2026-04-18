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