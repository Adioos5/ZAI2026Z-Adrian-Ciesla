package pl.zai.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Placeholder thrown by endpoints that are declared but not implemented yet (HTTP 501). */
public final class NotImplemented {

    private NotImplemented() {
    }

    public static ResponseStatusException yet() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }
}
