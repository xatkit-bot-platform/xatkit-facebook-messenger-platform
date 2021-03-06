package com.xatkit.plugins.messenger.platform.entity.payloads;

import com.google.gson.annotations.SerializedName;
import com.xatkit.plugins.messenger.platform.entity.GenericElement;

import lombok.Getter;

import java.util.List;


/**
 * Used for creating Generic Templates.
 * Only Android and iOS support by Facebook for European users since 16th of December 2020.
 */
@Deprecated
public class GenericTemplatePayload extends TemplatePayload {
    @SerializedName(value = "image_aspect_ratio")
    @Getter
    private final ImageAspectRatio imageAspectRatio;
    @SerializedName(value = "elements")
    @Getter
    private final List<GenericElement> genericElements; //Maximum size 10

    public GenericTemplatePayload(final List<GenericElement> genericElements) {
        this(genericElements, ImageAspectRatio.horizontal);
    }

    public GenericTemplatePayload(final List<GenericElement> genericElements, ImageAspectRatio imageAspectRatio) {
        super("generic");
        this.genericElements = genericElements;
        this.imageAspectRatio = imageAspectRatio;
    }

    public enum ImageAspectRatio {
        horizontal,
        square
    }


}
