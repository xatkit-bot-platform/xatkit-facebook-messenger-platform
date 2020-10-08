package com.xatkit.plugins.messenger.platform;

import com.xatkit.core.XatkitBot;
import com.xatkit.core.platform.RuntimePlatform;
import com.xatkit.core.server.*;
import com.xatkit.execution.StateContext;
import com.xatkit.plugins.messenger.platform.action.*;
import com.xatkit.plugins.messenger.platform.entity.Message;
import com.xatkit.plugins.messenger.platform.entity.Messaging;
import com.xatkit.plugins.messenger.platform.entity.Recipient;
import com.xatkit.plugins.messenger.platform.entity.SenderAction;
import com.xatkit.plugins.rest.platform.RestPlatform;
import com.xatkit.plugins.rest.platform.utils.ApiResponse;
import lombok.NonNull;
import fr.inria.atlanmod.commons.log.Log;
import lombok.val;
import org.apache.commons.configuration2.Configuration;
import org.apache.http.entity.StringEntity;

import static java.util.Objects.requireNonNull;

// TODO: Add javadocs

/**
 * A {@link RuntimePlatform} class that connects and interacts with the Messenger API.
 */
public class MessengerPlatform extends RestPlatform {
    private String verifyToken;
    private String accessToken;
    private String appSecret;
    private int messagesSent;
    private int messagesSentSuccessfully;

    @Override
    public void start(@NonNull XatkitBot xatkitBot, @NonNull Configuration configuration) {
        verifyToken = requireNonNull(configuration.getString(MessengerUtils.VERIFY_TOKEN_KEY));
        accessToken = requireNonNull(configuration.getString(MessengerUtils.ACCESS_TOKEN_KEY));
        appSecret = requireNonNull(configuration.getString(MessengerUtils.APP_SECRET_KEY));
        messagesSent = 0;
        messagesSentSuccessfully = 0;
        super.start(xatkitBot, configuration);

        xatkitBot.getXatkitServer().registerRestEndpoint(HttpMethod.GET, "/messenger/webhook",
                RestHandlerFactory.createEmptyContentRestHandler((headers, params, content) -> {
                    val mode = requireNonNull(HttpUtils.getParameterValue("hub.mode", params), "Missing mode");
                    val token = requireNonNull(HttpUtils.getParameterValue("hub.verify_token", params), "Missing token");
                    val challenge = requireNonNull(HttpUtils.getParameterValue("hub.challenge", params), "Missing challenge");
                    if (!mode.equals("subscribe")) {
                        throw new RestHandlerException(403, "Mode is not 'subscribe'");
                    }
                    if (!token.equals(verifyToken)) {
                        throw new RestHandlerException(403, "Token does not match verify token.");
                    }
                    return new StringEntity(challenge, "UTF-8");
                }));
    }

    public void markSeen(@NonNull StateContext context) {
        sendAction(context, SenderAction.markSeen);
    }

    public void sendAction(@NonNull StateContext context, @NonNull SenderAction senderAction) {
        val senderId = context.getContextId();
        Log.debug("Replying to {0} with a sender_action {1}", senderId, senderAction.name());
        val messaging = new Messaging(new Recipient(senderId), senderAction);
        excecuteReply(new Reply(this, context, messaging));
    }

    public void reply(@NonNull StateContext context, @NonNull String text) {
        reply(context, new Message(text));
    }

    public void reply(@NonNull StateContext context, @NonNull Message message) {
        val senderId = context.getContextId();
        Log.debug("REPLYING TO: {0}", senderId);
        val messaging = new Messaging(new Recipient(senderId), message);
        excecuteReply(new MessageReply(
                this,
                context,
                messaging));
    }

    private void excecuteReply(Reply reply) {
        messagesSent++;
        val result = reply.call().getResult();

        if (result instanceof ApiResponse) {
            val apiResponse = (ApiResponse<?>) result;
            Log.debug("REPLY RESPONSE STATUS: {0} {1}\n BODY: {2}", apiResponse.getStatus(), apiResponse.getStatusText(), apiResponse.getBody().toString());
            if (apiResponse.getStatus() == 200) messagesSentSuccessfully++;
        } else {
            Log.debug("Unexpected reply result: {0}", result);
        }
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public int getMessagesSent() { return messagesSent; }
}
