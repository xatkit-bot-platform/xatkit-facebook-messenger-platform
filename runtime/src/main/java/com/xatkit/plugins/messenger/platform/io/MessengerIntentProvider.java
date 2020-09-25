package com.xatkit.plugins.messenger.platform.io;

import com.google.gson.JsonElement;
import com.xatkit.core.XatkitException;
import com.xatkit.core.platform.io.IntentRecognitionHelper;
import com.xatkit.core.platform.io.WebhookEventProvider;
import com.xatkit.core.recognition.IntentRecognitionProviderException;
import com.xatkit.core.server.RestHandlerException;
import com.xatkit.execution.StateContext;
import com.xatkit.plugins.messenger.platform.MessengerPlatform;
import com.xatkit.plugins.messenger.platform.MessengerRestHandler;
import lombok.NonNull;
import lombok.val;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.entity.StringEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.xatkit.plugins.messenger.platform.MessengerUtils.calculateRFC2104HMAC;
import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

/**
 * A Messenger-based {@link WebhookEventProvider}.
 *
 * @see MessengerPlatform
 * @see MessengerRestHandler
 */
public class MessengerIntentProvider extends WebhookEventProvider<MessengerPlatform, MessengerRestHandler> {
    /**
     * Constructs a {@link MessengerIntentProvider} and binds it to the provided {@code platform}.
     *
     * @param platform the {@link MessengerPlatform} managing this provider
     */
    public MessengerIntentProvider(final @NonNull MessengerPlatform platform) {
        super(platform);
    }

    /**
     * Returns the URI of the REST endpoint to register the provider to.
     */
    @Override
    public String getEndpointURI() {
        return "/messenger/webhook";
    }

    /**
     * Creates a {@link MessengerRestHandler} used to process incoming Rest requests.
     */
    @Override
    protected MessengerRestHandler createRestHandler() {
        return new MessengerRestHandler() {
            @Override
            public HttpEntity handleParsedContent(
                    final @Nonnull List<Header> headers,
                    final @Nonnull List<NameValuePair> params,
                    final @Nullable JsonElement content
            ) throws RestHandlerException {
                try {
                    checkArgument(nonNull(content), "Missing content.");

                    if (!verifyValidation(headers, content))
                        throw new RestHandlerException(403, "Incoming JSON has no validation code");


                    requireNonNull(content.getAsJsonObject().get("entry"), "Missing entry.")
                            .getAsJsonArray()
                            .iterator()
                            .forEachRemaining(entry -> handleWebhookEntry(entry));

                    return new StringEntity("RECEIVED", StandardCharsets.UTF_8);
                } catch (NullPointerException | IllegalStateException | IllegalArgumentException | InvalidKeyException | NoSuchAlgorithmException e) {
                    throw new RestHandlerException(HttpStatus.SC_FORBIDDEN, e.getMessage());
                }
            }
        };
    }

    private boolean verifyValidation(final List<Header> headers, JsonElement content) throws RestHandlerException, InvalidKeyException, NoSuchAlgorithmException {
        for (Header h : headers) {
            if (h.getElements()[0].getName().equals("sha1")) {
                if (!calculateRFC2104HMAC(content.toString(), runtimePlatform.getAppSecret()).equals(h.getElements()[0].getValue())) {
                    throw new RestHandlerException(403, "Incoming JSON has incorrect validation code");
                }
                return true;
            }
        }
        return false;
    }

    private void handleWebhookEntry(final JsonElement jsonElement) {
        if (!jsonElement.isJsonObject()) {
            return;
        }
        val jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has("messaging")) {
            return;
        }
        val messaging = jsonObject.get("messaging").getAsJsonArray();
        messaging.iterator().forEachRemaining(messagingEntity -> {
            val messageJsonObject = messagingEntity.getAsJsonObject();
            val sender = requireNonNull(messageJsonObject.get("sender"), "Message has no sender");
            val id = requireNonNull(sender.getAsJsonObject().get("id"), "Sender has no id").getAsString();

            val message = requireNonNull(messageJsonObject.get("message"), "Message has no content");
            val text = requireNonNull(message.getAsJsonObject().get("text"), "Message has no text").getAsString();

            try {
                StateContext context = this.xatkitBot.getOrCreateContext(id);
                val recognizedIntent = IntentRecognitionHelper.getRecognizedIntent(text,
                        context, this.getRuntimePlatform().getXatkitBot());
                recognizedIntent.getPlatformData().put("rawMessage", text);
                this.sendEventInstance(recognizedIntent, context);
            } catch (IntentRecognitionProviderException e) {
                throw new XatkitException("An internal error occurred when computing the intent, see attached exception", e);
            }
        });
    }
}
