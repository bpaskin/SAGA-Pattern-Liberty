package com.saga.deposit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path("/accounts")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DepositResource {

    private static final Logger LOG = Logger.getLogger(DepositResource.class.getName());

    @Inject
    private AccountRepository repository;

    @PUT
    @Path("/{accountNumber}/deposit")
    @LRA(value = LRA.Type.MANDATORY, end = false)
    public Response deposit(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            @PathParam("accountNumber") String accountNumber,
            @QueryParam("amount") BigDecimal amount) {

        LOG.info(() -> "Deposit requested: account=" + accountNumber + " amount=" + amount + " lra=" + lraId);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOG.warning(() -> "Deposit rejected (invalid amount): account=" + accountNumber + " amount=" + amount);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Amount must be positive\"}")
                    .build();
        }
        try {
            repository.deposit(accountNumber, amount);
            LOG.info(() -> "Deposit succeeded: account=" + accountNumber + " amount=" + amount);
            return Response.ok()
                    .entity("{\"status\":\"deposited\",\"account\":\"" + accountNumber
                            + "\",\"amount\":" + amount + "}")
                    .build();
        } catch (IllegalArgumentException e) {
            LOG.warning(() -> "Deposit failed (account not found): " + e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (SQLException e) {
            LOG.severe(() -> "Deposit failed (SQL error): " + e.getMessage());
            throw new InternalServerErrorException("Deposit failed: " + e.getMessage(), e);
        }
    }

    @PUT
    @Path("/{accountNumber}/deposit/compensate")
    @Compensate
    public Response compensate(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            @PathParam("accountNumber") String accountNumber,
            @QueryParam("amount") BigDecimal amount) {

        LOG.info(() -> "Deposit compensate: account=" + accountNumber + " amount=" + amount + " lra=" + lraId);
        try {
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                repository.reverseDeposit(accountNumber, amount);
            }
            LOG.info(() -> "Deposit compensation succeeded: account=" + accountNumber);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        } catch (SQLException e) {
            LOG.severe(() -> "Deposit compensation failed (SQL error): account=" + accountNumber + " - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ParticipantStatus.FailedToCompensate.name())
                    .build();
        }
    }

    @PUT
    @Path("/{accountNumber}/deposit/complete")
    @Complete
    public Response complete(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            @PathParam("accountNumber") String accountNumber) {
        LOG.info(() -> "Deposit complete: account=" + accountNumber + " lra=" + lraId);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }
}
