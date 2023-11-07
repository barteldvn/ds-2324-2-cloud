package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.*;
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
    void confirmQuotes(@RequestBody List<Seat> seats) {
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
                    .bodyToMono(new ParameterizedTypeReference<Ticket>() {})
                    .block());
        }
        Booking booking = new Booking(bookingUUID, LocalDateTime.now(), tickets.stream().toList(), SecurityFilter.getUser().getEmail());
        ArrayList<Booking> bookings = bookingMap.getOrDefault(SecurityFilter.getUser().getEmail(), new ArrayList<>());
        bookings.add(booking);
        bookingMap.putIfAbsent(SecurityFilter.getUser().getEmail(), bookings);
    }

    @GetMapping("/api/getBookings")
    Collection<Booking> getBookings() {
        return bookingMap.getOrDefault(SecurityFilter.getUser().getEmail(), new ArrayList<>());
    }

    @GetMapping("/api/getAllBookings")
    Collection<Booking> getAllBookings() {
        return bookingMap.values().stream().flatMap(List::stream).toList();
    }

    @GetMapping("/api/getAllCustomers")
    Collection<String> getAllCustomers() {
        Optional<ArrayList<Booking>> maxBookings =  bookingMap.values().stream().max(Comparator.comparing(List::size));
        return maxBookings.<Collection<String>>map(bookings -> bookings.stream().map(Booking::getCustomer).toList()).orElseGet(ArrayList::new);
    }
}
