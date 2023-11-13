package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.Application;
import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.*;
import be.kuleuven.distributedsystems.cloud.pubsub.PubSub;
import be.kuleuven.distributedsystems.cloud.pubsub.Publisher;
import com.google.api.client.json.Json;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
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
        return webClientBuilder
                .baseUrl("https://reliabletrains.com")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains")
                        .queryParam("key", API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Train>>() {})
                .block()
                .getContent();
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
                .block();
    }

    @PostMapping("/api/confirmQuotes")
    void confirmQuotes(@RequestBody String seats) throws ExecutionException, InterruptedException, IOException {
        System.out.println("test");
        Gson gson = new Gson();
        System.out.println(seats);
        Application.getPubSub().sendMessage(seats);
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

        // Get the "data" field from the "message" object
        String data = jsonObject.getAsJsonObject("message").get("data").getAsString();
        String decodedData = new String(java.util.Base64.getDecoder().decode(data));
        System.out.println(decodedData);

        Type seatListType = new TypeToken<List<Seat>>() {}.getType();
        List<Seat> seats = gson.fromJson(decodedData, seatListType);
        Collection<Ticket> tickets = new HashSet<>();
        UUID bookingUUID = UUID.randomUUID();
        for (Seat seat : seats) {
            tickets.add(webClientBuilder
                    .baseUrl("https://" + seat.getTrainCompany() + "/")
                    .build()
                    .put()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("trains", seat.getTrainId().toString(), "seats", seat.getSeatId().toString(), "ticket")
                            .queryParam("customer", SecurityFilter.getUser().getEmail())
                            .queryParam("bookingReference", bookingUUID.toString())
                            .queryParam("key", API_KEY)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Ticket>() {
                    })
                    .block());
        }
        Booking booking = new Booking(bookingUUID, LocalDateTime.now(), tickets.stream().toList(), SecurityFilter.getUser().getEmail());
        ArrayList<Booking> bookings = bookingMap.getOrDefault(SecurityFilter.getUser().getEmail(), new ArrayList<>());
        bookings.add(booking);
        bookingMap.putIfAbsent(SecurityFilter.getUser().getEmail(), bookings);
        System.out.println("SUCCESS???!!!");
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
