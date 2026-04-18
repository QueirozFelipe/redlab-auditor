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