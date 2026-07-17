package com.saga.transfer;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.math.BigDecimal;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

/**
 * Typed REST client used by the transfer service to call both
 * withdrawal-service and deposit-service.  The base URL is supplied
 * programmatically so a single interface covers both downstream services.
 */
@RegisterRestClient
@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AccountClient {

    @PUT
    @Path("/{accountNumber}/withdraw")
    Response withdraw(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
            @PathParam("accountNumber") String accountNumber,
            @QueryParam("amount") BigDecimal amount);

    @PUT
    @Path("/{accountNumber}/deposit")
    Response deposit(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
            @PathParam("accountNumber") String accountNumber,
            @QueryParam("amount") BigDecimal amount);
}
