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
public interface GitLabClient {

    @GET
    @Path("/groups/{id}")
    JsonNode getGroup(
            @PathParam("id") String groupId,
            @HeaderParam("PRIVATE-TOKEN") String token
    );

    @GET
    @Path("/groups/{id}/projects")
    List<JsonNode> getGroupProjects(
            @PathParam("id") String groupId,
            @QueryParam("include_subgroups") boolean includeSubgroups,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page,
            @HeaderParam("PRIVATE-TOKEN") String token
    );

    @GET
    @Path("/projects/{id}/repository/compare")
    JsonNode compareBranches(
            @PathParam("id") long projectId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("PRIVATE-TOKEN") String token
    );
}