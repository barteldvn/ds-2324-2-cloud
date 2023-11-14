package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.Application;
import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.*;
import be.kuleuven.distributedsystems.cloud.pubsub.PubSub;
import com.google.api.client.json.Json;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.pubsub.v1.Subscription;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.io.IOException;
import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
public class BookingRestController {

    private final String API_KEY = "JViZPgNadspVcHsMbDFrdGg0XXxyiE";
    @Resource(name = "webClientBuilder")
    private WebClient.Builder webClientBuilder;
    private HashMap<String, ArrayList<Booking>> bookingMap = new HashMap<>();

    @GetMapping("/api/getTrains")
    Collection<Train> getAllTrains(){
        CollectionModel<Train> reliableTrains = webClientBuilder
                .baseUrl("https://reliabletrains.com")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains")
                        .queryParam("key", API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Train>>() {})
                .block();
        CollectionModel<Train> unreliabletrains = webClientBuilder
                .baseUrl("https://unreliabletrains.com")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains")
                        .queryParam("key", API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Train>>() {})
                .retry(10)
                .block();
        Collection<Train> allTrains = new ArrayList<Train>(reliableTrains.getContent().stream().toList()){};
        allTrains.addAll(unreliabletrains.getContent());
        System.out.println(allTrains);
        return allTrains;
    }

    @GetMapping("/api/getTrain")
    Train getTrain(@RequestParam String trainCompany, @RequestParam String trainId) throws RemoteException {
        Collection<Train> trains =  getAllTrains();
        for (Train train : trains){
            if (Objects.equals(train.getTrainCompany(), trainCompany) && Objects.equals(train.getTrainId().toString(), trainId)) return train;
        }
        throw new RemoteException("Train not found");
    }

    @GetMapping("/api/getTrainTimes")
    Collection<LocalDateTime> getTrainTimes(@RequestParam String trainCompany, @RequestParam String trainId) throws RemoteException {
        Train train =  getTrain(trainCompany, trainId);
        return webClientBuilder
                .baseUrl("https://"+trainCompany+"/")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains", "{id}", "times")
                        .queryParam("key", API_KEY)
                        .build(train.getTrainId()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<LocalDateTime>>() {})
                .retry(10)
                .block()
                .getContent();
    }


    @GetMapping("/api/getAvailableSeats")
    Map<String, List <Seat>> getAvailableSeats(@RequestParam String trainCompany, @RequestParam String trainId, @RequestParam String time) throws RemoteException {
        Train train =  getTrain(trainCompany, trainId);
        LocalDateTime timeObject = LocalDateTime.parse(time);
        Collection<Seat> seats =  webClientBuilder
                .baseUrl("https://"+trainCompany+"/")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains", "{id}", "seats")
                        .queryParam("time", timeObject)
                        .queryParam("available", true)
                        .queryParam("key", API_KEY)
                        .build(train.getTrainId()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Seat>>() {})
                .retry(10)
                .block()
                .getContent();
        return seats.stream().collect(Collectors.groupingBy(Seat::getType));
    }

    @GetMapping("/api/getSeat")
    Seat getSeat(@RequestParam String trainCompany, @RequestParam String trainId, @RequestParam String seatId) throws RemoteException {
        return webClientBuilder
                .baseUrl("https://"+trainCompany+ "/")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains", trainId, "seats", seatId)
                        .queryParam("key", API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Seat>() {})
                .retry(10)
                .block();
    }

    @PostMapping("/api/confirmQuotes")
    void confirmQuotes(@RequestBody String quotes) throws ExecutionException, InterruptedException, IOException {
        System.out.println("test");
        Gson gson = new Gson();
        System.out.println(quotes);
        Application.getPubSub().sendMessage(quotes,SecurityFilter.getUser().getEmail());
    }

    @GetMapping("/api/getBookings")
    Collection<Booking> getBookings() {
        return bookingMap.getOrDefault(SecurityFilter.getUser().getEmail(), new ArrayList<>());
    }

    @GetMapping("/api/getAllBookings")
    Collection<Booking> getAllBookings() {
        return bookingMap.values().stream().flatMap(List::stream).toList();
    }


    @PostMapping("/subscription/confirmQuote")
    void subscription(@RequestBody String body) {
        Gson gson = new Gson();
        System.out.println(body);
        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();

        String data = jsonObject.getAsJsonObject("message").get("data").getAsString();
        String email = jsonObject.getAsJsonObject("message").getAsJsonObject("attributes").get("userEmail").getAsString();
        String decodedData = new String(java.util.Base64.getDecoder().decode(data));
        System.out.println(decodedData);

        Type quoteList = new TypeToken<List<Quote>>() {}.getType();
        List<Quote> quotes = gson.fromJson(decodedData, quoteList);
        Collection<Ticket> tickets = new HashSet<>();
        UUID bookingUUID = UUID.randomUUID();
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
                    .bodyToMono(new ParameterizedTypeReference<Ticket>() {})
                    .retry(10)
                    .block());
        }
        if(quotes.size() != tickets.size()){
            for(Ticket ticket : tickets){
                webClientBuilder
                        .baseUrl("https://" + ticket.getTrainCompany() + "/")
                        .build()
                        .delete()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("trains", ticket.getTrainId().toString(), "seats", ticket.getSeatId().toString(), "ticket")
                                .queryParam("key", API_KEY)
                                .build());
            }
        }
        Booking booking = new Booking(bookingUUID, LocalDateTime.now(), tickets.stream().toList(), email);
        ArrayList<Booking> bookings = bookingMap.getOrDefault(email, new ArrayList<>());
        bookings.add(booking);
        bookingMap.putIfAbsent(email, bookings);
    }
    @GetMapping("/api/getBestCustomers")
    Collection<String> getBestCustomers() {
        return bookingMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                entry -> entry.getValue().size(),
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ))
                .entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .orElse(new ArrayList<>());
    }
}
