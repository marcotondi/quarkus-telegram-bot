package io.quarkiverse.telegram.bot.runtime.client;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.DeleteMessage;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageReplyMarkup;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageResult;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.GetUpdatesRequest;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendAnimationJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendAudioJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendChatAction;
import io.quarkiverse.telegram.bot.runtime.model.SendDocumentJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendMediaGroupJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.SendPhotoJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendVideoJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendVoiceJsonRequest;
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
    @Path("/editMessageText")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<io.quarkiverse.telegram.bot.runtime.model.EditMessageResult>> editMessageText(
            @PathParam("token") String token, EditMessageText req);

    @POST
    @Path("/editMessageReplyMarkup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<EditMessageResult>> editMessageReplyMarkup(
            @PathParam("token") String token, EditMessageReplyMarkup req);

    @POST
    @Path("/deleteMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> deleteMessage(
            @PathParam("token") String token, DeleteMessage req);

    @POST
    @Path("/sendChatAction")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> sendChatAction(
            @PathParam("token") String token, SendChatAction req);

    @POST
    @Path("/answerCallbackQuery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> answerCallbackQuery(@PathParam("token") String token, AnswerCallbackQuery req);

    @POST
    @Path("/answerInlineQuery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> answerInlineQuery(
            @PathParam("token") String token,
            io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery req);

    @POST
    @Path("/setMyCommands")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> setMyCommands(@PathParam("token") String token, SetMyCommandsRequest req);

    // sendPhoto: JSON vs Multipart
    @POST
    @Path("/sendPhoto")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendPhotoJson(
            @PathParam("token") String token,
            SendPhotoJsonRequest req);

    @POST
    @Path("/sendPhoto")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendPhotoMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);

    // sendDocument: JSON vs Multipart
    @POST
    @Path("/sendDocument")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendDocumentJson(
            @PathParam("token") String token,
            SendDocumentJsonRequest req);

    @POST
    @Path("/sendDocument")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendDocumentMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);

    // sendAudio: JSON vs Multipart
    @POST
    @Path("/sendAudio")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendAudioJson(
            @PathParam("token") String token,
            SendAudioJsonRequest req);

    @POST
    @Path("/sendAudio")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendAudioMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);

    // sendMediaGroup: JSON vs Multipart
    @POST
    @Path("/sendMediaGroup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<List<Message>>> sendMediaGroupJson(
            @PathParam("token") String token,
            SendMediaGroupJsonRequest req);

    @POST
    @Path("/sendMediaGroup")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<List<Message>>> sendMediaGroupMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);

    // sendVideo: JSON vs Multipart
    @POST
    @Path("/sendVideo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendVideoJson(
            @PathParam("token") String token,
            SendVideoJsonRequest req);

    @POST
    @Path("/sendVideo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendVideoMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);

    // sendAnimation: JSON vs Multipart
    @POST
    @Path("/sendAnimation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendAnimationJson(
            @PathParam("token") String token,
            SendAnimationJsonRequest req);

    @POST
    @Path("/sendAnimation")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendAnimationMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);

    // sendVoice: JSON vs Multipart
    @POST
    @Path("/sendVoice")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendVoiceJson(
            @PathParam("token") String token,
            SendVoiceJsonRequest req);

    @POST
    @Path("/sendVoice")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendVoiceMultipart(
            @PathParam("token") String token,
            ClientMultipartForm form);
}
