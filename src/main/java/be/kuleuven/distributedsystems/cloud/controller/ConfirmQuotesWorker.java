package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import be.kuleuven.distributedsystems.cloud.firestore.Firestore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
public class ConfirmQuotesWorker {
    private final String API_KEY = "JViZPgNadspVcHsMbDFrdGg0XXxyiE";
    @Resource(name = "webClientBuilder")
    private WebClient.Builder webClientBuilder;


    @PostMapping("/subscription/confirmQuote")
    void subscription(@RequestBody String body) throws ExecutionException, InterruptedException {
        Gson gson = new Gson();
        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
        String data = jsonObject.getAsJsonObject("message").get("data").getAsString();
        String email = jsonObject.getAsJsonObject("message").getAsJsonObject("attributes").get("userEmail").getAsString();
        String decodedData = new String(java.util.Base64.getDecoder().decode(data));
        List<Quote> quotes = gson.fromJson(decodedData, new TypeToken<List<Quote>>(){}.getType());

        Set<Ticket> tickets = new HashSet<>();
        UUID bookingUUID = UUID.randomUUID();
        try {
            for (Quote quote : quotes) {
                tickets.add(webClientBuilder
                        .baseUrl("https://" + quote.getTrainCompany() + "/")
                        .build()
                        .put()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("trains", quote.getTrainId().toString(), "seats", quote.getSeatId().toString(), "ticket")
                                .queryParam("customer", email)
                                .queryParam("bookingReference", bookingUUID.toString())
                                .queryParam("key", API_KEY)
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Ticket>() {
                        })
                        .retry(10)
                        .block());
            }
        } catch (Exception e) {
            for (Ticket ticket : tickets) {
                webClientBuilder
                        .baseUrl("https://" + ticket.getTrainCompany() + "/")
                        .build()
                        .delete()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("trains", ticket.getTrainId().toString(), "seats", ticket.getSeatId().toString(), "ticket", ticket.getTicketId().toString())
                                .queryParam("key", API_KEY)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .retry(10)
                        .block();
                return;
            }
        }
        Firestore.addBooking(new Booking(bookingUUID, LocalDateTime.now(), tickets.stream().toList(), email));
    }
}
