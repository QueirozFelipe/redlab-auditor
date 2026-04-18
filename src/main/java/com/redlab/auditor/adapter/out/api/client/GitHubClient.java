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