package com.example.curlgui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.ErrorResponseDto;
import com.example.curlgui.dto.ImportCurlRequestDto;
import com.example.curlgui.dto.ParsedRequestDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;
import com.example.curlgui.service.CurlParseException;
import com.example.curlgui.service.CurlParserService;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.RequestExecutionException;
import com.example.curlgui.service.RequestService;

/**
 * REST endpoints for building and performing an outgoing HTTP request.
 *
 * <p>{@code @RestController} + {@code @RequestMapping("/api")}: every method here
 * returns data serialised to JSON, under the {@code /api} path prefix.
 *
 * <p>The controller stays thin - it receives DTOs, delegates to a service, and
 * translates the service's exceptions into JSON error responses with a sensible
 * status code. Both services are supplied through the constructor (dependency
 * injection).
 */
@RestController
@RequestMapping("/api")
public class RequestController {

    private final RequestService requestService;
    private final CurlParserService curlParserService;

    public RequestController(RequestService requestService, CurlParserService curlParserService) {
        this.requestService = requestService;
        this.curlParserService = curlParserService;
    }

    /** Perform the request the user built and return the response. */
    @PostMapping("/requests/send")
    public ResponseEntity<?> send(@RequestBody(required = false) SendRequestDto request) {
        try {
            SendResponseDto response = requestService.execute(request);
            // NOTE: a 404/500 from the *target* server arrives here as a normal
            // SendResponseDto and is returned with HTTP 200 from our API. The
            // GUI decides how to display it.
            return ResponseEntity.ok(response);

        } catch (InvalidRequestException ex) {
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponseDto("Invalid request", ex.getMessage()));

        } catch (RequestExecutionException ex) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponseDto(ex.getMessage(), ex.getDetail()));
        }
    }

    /**
     * Parse a pasted cURL command into a request the editor can load. The command
     * is only parsed as text - never executed.
     *
     * <p>Success -> HTTP 200 + {@link ParsedRequestDto}.
     * Parse failure -> HTTP 400 + {@link ErrorResponseDto} whose {@code error}
     * field is a message safe to show the user directly (no stack traces).
     */
    @PostMapping("/requests/import-curl")
    public ResponseEntity<?> importCurl(@RequestBody(required = false) ImportCurlRequestDto request) {
        try {
            String curl = request == null ? null : request.curl();
            ParsedRequestDto parsed = curlParserService.parse(curl);
            return ResponseEntity.ok(parsed);

        } catch (CurlParseException ex) {
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponseDto(ex.getMessage(), null));
        }
    }
}
