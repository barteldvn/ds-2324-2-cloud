package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Train;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
public class BookingRestController {


    @GetMapping("/api/getTrains")
    Collection<Train> getAllTrains(){
        return null;
    }

}
