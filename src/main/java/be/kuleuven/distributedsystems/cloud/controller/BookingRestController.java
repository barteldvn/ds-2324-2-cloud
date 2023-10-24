package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Seat;
import be.kuleuven.distributedsystems.cloud.entities.Train;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RestController
public class BookingRestController {


    @GetMapping("/api/getTrains")
    Collection<Train> getAllTrains(){
        return null;
    }

    @GetMapping("/api/getTrain/{trainCompany}/{trainId}")
    Train getTrain(@PathVariable String trainCompany, @PathVariable String trainId) {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }

    @GetMapping("/api/getTrainTimes/{trainCompany}/{trainId}}")
    Collection<LocalDateTime> getTrainTimes(@PathVariable String trainCompany, @PathVariable String trainId) {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }


    @GetMapping("/api/getAvailableSeats/{trainCompany}/{trainId}/{time}")
    Collection<Seat> getAvailableSeats(@PathVariable String trainCompany, @PathVariable String trainId, @PathVariable String time) {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }

    @GetMapping("/api/getSeat/{trainCompany}/{trainId}/{seatId}")
    Seat getSeat(@PathVariable String trainCompany, @PathVariable String trainId, @PathVariable String seatId) {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }

    @PostMapping("/api/confirmQuotes")
    Train confirmQuotes() {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }

    @GetMapping("/api/getBookings")
    Collection<Booking> getBookings() {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }

    @GetMapping("/api/getAllBookings")
    Collection<Booking> getAllBookings() {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }

    @GetMapping("/api/getAllCustomers")
    Collection<User> getAllCustomers() {
        Optional<Meal> meal = mealsRepository.findMeal(id);

        return meal.orElseThrow(() -> new MealNotFoundException(id));
    }





}
