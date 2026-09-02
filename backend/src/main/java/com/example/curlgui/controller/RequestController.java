package com.example.curlgui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.ErrorResponseDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.RequestExecutionException;
import com.example.curlgui.service.RequestService;

/**
 * REST endpoint for performing an outgoing HTTP request.
 *
 * <p>{@code @RestController} + {@code @RequestMapping("/api")}: every method here
 * returns data serialised to JSON, under the {@code /api} path prefix.
 *
 * <p>The controller is thin on purpose - it does no HTTP work itself. It:
 * <ol>
 *   <li>receives the JSON body as a {@link SendRequestDto} ({@code @RequestBody}
 *       tells Spring to build the object from the request body via Jackson),</li>
 *   <li>delegates to {@link RequestService} (injected through the constructor),</li>
 *   <li>translates the two service exceptions into JSON error responses with a
 *       sensible status code.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping("/requests/send")
    public ResponseEntity<?> send(@RequestBody(required = false) SendRequestDto request) {
        try {
            SendResponseDto response = requestService.execute(request);
            // NOTE: a 404/500 from the *target* server arrives here as a normal
            // SendResponseDto and is returned with HTTP 200 from our API. The
            // GUI decides how to display it.
            return ResponseEntity.ok(response);

        } catch (InvalidRequestException ex) {
            // Bad input from the frontend -> 400.
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponseDto("Invalid request", ex.getMessage()));

        } catch (RequestExecutionException ex) {
            // We're fine, the upstream server isn't -> 502 Bad Gateway.
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponseDto(ex.getMessage(), ex.getDetail()));
        }
    }
}
