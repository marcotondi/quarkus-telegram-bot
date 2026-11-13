package io.quarkiverse.telegram.bot.runtime.client;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.telegram.bot.runtime.model.GetUpdatesRequest;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.SetMyCommandsRequest;
import io.quarkiverse.telegram.bot.runtime.model.SetWebhookRequest;
import io.quarkiverse.telegram.bot.runtime.model.TelegramResponse;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.smallrye.mutiny.Uni;

@Path("/bot{token}")
@RegisterRestClient(configKey = "telegram-api")
public interface TelegramBotApi {

    @POST
    @Path("/getMe")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<User>> getMe(@PathParam("token") String token);

    @POST
    @Path("/getUpdates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<List<io.quarkiverse.telegram.bot.runtime.model.Update>>> getUpdates(
            @PathParam("token") String token,
            GetUpdatesRequest req);

    @POST
    @Path("/setWebhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> setWebhook(@PathParam("token") String token, SetWebhookRequest req);

    @POST
    @Path("/deleteWebhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> deleteWebhook(@PathParam("token") String token);

    @POST
    @Path("/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendMessage(@PathParam("token") String token, SendMessage req);

    @POST
    @Path("/setMyCommands")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> setMyCommands(@PathParam("token") String token, SetMyCommandsRequest req);
}
