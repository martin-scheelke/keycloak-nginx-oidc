package za.co.tsa.oidcdemo.web;

import org.springframework.web.bind.annotation.RestController;

import za.co.tsa.oidcdemo.api.PublicApi;
import za.co.tsa.oidcdemo.api.model.EchoRequest;
import za.co.tsa.oidcdemo.api.model.EchoResponse;
import za.co.tsa.oidcdemo.api.model.MessageResponse;
import za.co.tsa.oidcdemo.service.EchoService;

/** Implements the {@code Public} tag of {@code openapi/openapi.yaml}. */
@RestController
public class PublicController implements PublicApi {

    private final EchoService echoService;

    public PublicController(EchoService echoService) {
        this.echoService = echoService;
    }

    @Override
    public MessageResponse getPublicHello() {
        return new MessageResponse("Hello from a public endpoint");
    }

    @Override
    public EchoResponse postPublicEcho(EchoRequest echoRequest) {
        return echoService.echo(echoRequest.getMessage());
    }
}
