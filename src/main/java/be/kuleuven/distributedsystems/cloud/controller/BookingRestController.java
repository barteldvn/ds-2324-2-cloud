package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.Application;
import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.*;
import be.kuleuven.distributedsystems.cloud.firestore.Firestore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
        CollectionModel<Train> unreliableTrains = webClientBuilder
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
        allTrains.addAll(unreliableTrains.getContent());
        System.out.println(allTrains);
        return allTrains;
    }

    @GetMapping("/api/getTrain")
    Train getTrain(@RequestParam String trainCompany, @RequestParam String trainId) throws RemoteException {
        Collection<Train> trains =  getAllTrains();
        System.out.println(trainId);
        System.out.println(trainCompany);
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
    void confirmQuotes(@RequestBody String quotes) throws InterruptedException {
        System.out.println("test");
        Application.getPubSub().sendMessage(quotes,SecurityFilter.getUser().getEmail());
        TimeUnit.SECONDS.sleep(1);
    }

    @PostMapping("/subscription/confirmQuote")
    void subscription(@RequestBody String body) throws ExecutionException, InterruptedException {
        Gson gson = new Gson();
        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();

        String data = jsonObject.getAsJsonObject("message").get("data").getAsString();
        String email = jsonObject.getAsJsonObject("message").getAsJsonObject("attributes").get("userEmail").getAsString();

        String decodedData = new String(java.util.Base64.getDecoder().decode(data));
        System.out.println(decodedData);

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

    @GetMapping("/api/getBookings")
    Collection<Booking> getBookings() {
        return Firestore.getBookings(SecurityFilter.getUser().getEmail());
    }

    @GetMapping("/api/getAllBookings")
    Collection<Booking> getAllBookings() {
        return Firestore.getBookings();
    }

    @GetMapping("/api/getBestCustomers")
    Collection<String> getBestCustomers() {
        Map<String, Integer> ticketsPerEmail = Firestore.getBookings().stream()
                .collect(Collectors.groupingBy(Booking::getCustomer,
                        Collectors.summingInt(
                        entry -> entry.getTickets().size())
                ));
        OptionalInt maxTotalTickets = ticketsPerEmail.values().stream()
                .mapToInt(Integer::intValue)
                .max();

        if(maxTotalTickets.isPresent()){
            return ticketsPerEmail.entrySet().stream()
                    .filter(entry -> entry.getValue() == maxTotalTickets.getAsInt())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
