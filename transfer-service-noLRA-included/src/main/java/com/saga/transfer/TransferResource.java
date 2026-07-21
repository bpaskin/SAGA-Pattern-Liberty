package com.saga.transfer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.net.URI;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path("/transfer")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransferResource {

    private static final Logger LOG = Logger.getLogger(TransferResource.class.getName());

    @Inject
    @ConfigProperty(name = "withdrawal.service.url", defaultValue = "http://localhost:9082/withdrawal")
    private String withdrawalServiceUrl;

    @Inject
    @ConfigProperty(name = "deposit.service.url", defaultValue = "http://localhost:9081/deposit")
    private String depositServiceUrl;

    @POST
    @LRA(value = LRA.Type.REQUIRES_NEW, end = true)
    public Response transfer(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            TransferRequest request) {

        if (request == null
                || request.getFromAccount() == null
                || request.getToAccount() == null
                || request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            LOG.warning(() -> "Transfer rejected (invalid request): " + request);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid transfer request\"}")
                    .build();
        }

        LOG.info(() -> "Transfer started: from=" + request.getFromAccount()
                + " to=" + request.getToAccount()
                + " amount=" + request.getAmount()
                + " lra=" + lraId);

        String lraHeader = lraId != null ? lraId.toString() : null;

        // Step 1 – withdraw from source account
        AccountClient withdrawalClient = RestClientBuilder.newBuilder()
                .baseUri(URI.create(withdrawalServiceUrl))
                .build(AccountClient.class);

        Response withdrawalResponse = withdrawalClient.withdraw(
                lraHeader, request.getFromAccount(), request.getAmount());

        if (withdrawalResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            String body = withdrawalResponse.readEntity(String.class);
            LOG.warning(() -> "Transfer failed at withdrawal step: status="
                    + withdrawalResponse.getStatus() + " body=" + body);
            throw new WebApplicationException("Withdrawal failed: " + body, Response.Status.CONFLICT);
        }
        LOG.fine(() -> "Withdrawal step succeeded: account=" + request.getFromAccount());

        // Step 2 – deposit into destination account
        AccountClient depositClient = RestClientBuilder.newBuilder()
                .baseUri(URI.create(depositServiceUrl))
                .build(AccountClient.class);

        Response depositResponse = depositClient.deposit(
                lraHeader, request.getToAccount(), request.getAmount());

        if (depositResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            String body = depositResponse.readEntity(String.class);
            LOG.warning(() -> "Transfer failed at deposit step: status="
                    + depositResponse.getStatus() + " body=" + body);
            throw new WebApplicationException("Deposit failed: " + body, Response.Status.INTERNAL_SERVER_ERROR);
        }
        LOG.fine(() -> "Deposit step succeeded: account=" + request.getToAccount());

        LOG.info(() -> "Transfer completed: from=" + request.getFromAccount()
                + " to=" + request.getToAccount()
                + " amount=" + request.getAmount());

        return Response.ok()
                .entity("{\"status\":\"transferred\","
                        + "\"from\":\"" + request.getFromAccount() + "\","
                        + "\"to\":\"" + request.getToAccount() + "\","
                        + "\"amount\":" + request.getAmount() + "}")
                .build();
    }

    @PUT
    @Path("/compensate")
    @Compensate
    public Response compensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        LOG.warning(() -> "Transfer compensated: lra=" + lraId);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }

    @PUT
    @Path("/complete")
    @Complete
    public Response complete(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        LOG.info(() -> "Transfer complete: lra=" + lraId);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }
}
