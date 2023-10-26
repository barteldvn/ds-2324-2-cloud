package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Seat;
import be.kuleuven.distributedsystems.cloud.entities.Train;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class BookingRestController {

    private final String API_KEY = "JViZPgNadspVcHsMbDFrdGg0XXxyiE";
    @Resource(name = "webClientBuilder")
    private WebClient.Builder webClientBuilder;


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

    @GetMapping("/api/getTrain/{trainCompany}/{trainId}")
    Train getTrain(@PathVariable String trainCompany, @PathVariable String trainId) throws RemoteException {
        Collection<Train> trains =  getAllTrains();
        for (Train train : trains){
            if (Objects.equals(train.getTrainCompany(), trainCompany) && Objects.equals(train.getTrainId().toString(), trainId)) return train;
        }
        throw new RemoteException("Train not found");
    }

    @GetMapping("/api/getTrainTimes/{trainCompany}/{trainId}}")
    Collection<LocalDateTime> getTrainTimes(@PathVariable String trainCompany, @PathVariable String trainId) throws RemoteException {
        Train train =  getTrain(trainCompany, trainId);
        return webClientBuilder
                .baseUrl("https://reliabletrains.com")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("trains/{id}/times")
                        .queryParam("key", API_KEY)
                        .build(train.getTrainId()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<LocalDateTime>>() {})
                .block()
                .getContent();
    }
//
//
//    @GetMapping("/api/getAvailableSeats/{trainCompany}/{trainId}/{time}")
//    Collection<Seat> getAvailableSeats(@PathVariable String trainCompany, @PathVariable String trainId, @PathVariable String time) {
//        Optional<Meal> meal = mealsRepository.findMeal(id);
//
//        return meal.orElseThrow(() -> new MealNotFoundException(id));
//    }
//
//    @GetMapping("/api/getSeat/{trainCompany}/{trainId}/{seatId}")
//    Seat getSeat(@PathVariable String trainCompany, @PathVariable String trainId, @PathVariable String seatId) {
//        Optional<Meal> meal = mealsRepository.findMeal(id);
//
//        return meal.orElseThrow(() -> new MealNotFoundException(id));
//    }
//
//    @PostMapping("/api/confirmQuotes")
//    Train confirmQuotes() {
//        Optional<Meal> meal = mealsRepository.findMeal(id);
//
//        return meal.orElseThrow(() -> new MealNotFoundException(id));
//    }
//
//    @GetMapping("/api/getBookings")
//    Collection<Booking> getBookings() {
//        Optional<Meal> meal = mealsRepository.findMeal(id);
//
//        return meal.orElseThrow(() -> new MealNotFoundException(id));
//    }
//
//    @GetMapping("/api/getAllBookings")
//    Collection<Booking> getAllBookings() {
//        Optional<Meal> meal = mealsRepository.findMeal(id);
//
//        return meal.orElseThrow(() -> new MealNotFoundException(id));
//    }
//
//    @GetMapping("/api/getAllCustomers")
//    Collection<User> getAllCustomers() {
//        Optional<Meal> meal = mealsRepository.findMeal(id);
//
//        return meal.orElseThrow(() -> new MealNotFoundException(id));
//    }





}
