package za.co.tsa.oidcdemo.web;

import org.springframework.web.bind.annotation.RestController;

import za.co.tsa.oidcdemo.api.PublicApi;
import za.co.tsa.oidcdemo.api.model.MessageResponse;

/** Implements the {@code Public} tag of {@code openapi/openapi.yaml}. */
@RestController
public class PublicController implements PublicApi {

    @Override
    public MessageResponse getPublicHello() {
        return new MessageResponse("Hello from a public endpoint");
    }
}
