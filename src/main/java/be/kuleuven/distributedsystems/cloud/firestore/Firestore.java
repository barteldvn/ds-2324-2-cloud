package be.kuleuven.distributedsystems.cloud.firestore;

import be.kuleuven.distributedsystems.cloud.Application;
import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class Firestore {

    public static void addBooking(Booking booking) throws ExecutionException, InterruptedException {
        DocumentReference docRef = Application.db.collection("bookings").document(booking.getId().toString());
        ApiFuture<WriteResult> result = docRef.set(encodeBooking(booking));
        System.out.println("Update time : " + result.get().getUpdateTime());
    }

    public static ArrayList<Booking> getBookings(String email) {
        ApiFuture<QuerySnapshot> query = Application.db.collection("bookings").get();
        QuerySnapshot querySnapshot = null;
        try {
            querySnapshot = query.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
        ArrayList<Booking> bookings = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            String costumer = document.get("bookingEmail",String.class);
            if(Objects.equals(costumer, email)) {
                String id = document.get("bookingID", String.class);
                com.google.cloud.Timestamp time = document.get("bookingTime", com.google.cloud.Timestamp.class);
                List<Map<String, Object>> tickets = (List<Map<String, Object>>) document.get("bookingTickets");
                bookings.add(decodeBooking(id, time, tickets, costumer));
                System.out.printf("Id: %s, time:" + time + ", tickets: %s, email: %s", id, tickets, costumer);
            }
        }
        System.out.println("Bookings: " + bookings);
        return bookings;
    }

    public static ArrayList<Booking> getBookings() {
        ApiFuture<QuerySnapshot> query = Application.db.collection("bookings").get();
        QuerySnapshot querySnapshot = null;
        try {
            querySnapshot = query.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
        ArrayList<Booking> bookings = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            String id = document.get("bookingID", String.class);
            String costumer = document.get("bookingEmail",String.class);
            com.google.cloud.Timestamp time = document.get("bookingTime", com.google.cloud.Timestamp.class);
            List<Map<String, Object>> tickets = (List<Map<String, Object>>) document.get("bookingTickets");
            bookings.add(decodeBooking(id, time, tickets, costumer));
            System.out.printf("Id: %s, time:" + time + ", tickets: %s, email: %s", id, tickets, costumer);
        }
        System.out.println("Bookings: " + bookings);
        return bookings;
    }
    private static Booking decodeBooking(String id, com.google.cloud.Timestamp time, List<Map<String, Object>> tickets, String costumer) {
        UUID bookingId = UUID.fromString(id);
        LocalDateTime bookingTime = time.toSqlTimestamp().toLocalDateTime();
        List<Ticket> bookingTickets = decodeTickets(tickets);
        return new Booking(bookingId, bookingTime, bookingTickets, costumer);
    }

    private static List<Ticket> decodeTickets(List<Map<String, Object>> tickets) {
        List<Ticket> decodedTickets = new ArrayList<>();
        for(Map<String, Object> ticket : tickets){
            decodedTickets.add(decodeTicket(ticket));
        }
        return decodedTickets;
    }

    private static Ticket decodeTicket(Map<String, Object> ticket) {
        String trainCompany = (String) ticket.get("trainCompany");
        String trainId = (String) ticket.get("trainId");
        String seatId = (String) ticket.get("seatId");
        String ticketId = (String) ticket.get("ticketId");
        String customer = (String) ticket.get("customer");
        String bookingReference = (String) ticket.get("bookingReference");
        return new Ticket(
                trainCompany,
                UUID.fromString(trainId),
                UUID.fromString(seatId),
                UUID.fromString(ticketId),
                customer,
                bookingReference);
    }

    private static Map<String, Object> encodeBooking(Booking booking) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingID", booking.getId().toString());
        data.put("bookingTime", Timestamp.valueOf(booking.getTime()));
        data.put("bookingTickets", encodeTickets(booking.getTickets()));
        data.put("bookingEmail", booking.getCustomer());
        return data;
    }

    private static List<Map<String, Object>> encodeTickets(List<Ticket> tickets) {
        List<Map<String, Object>> encodedTickets = new ArrayList<>();
        for(Ticket ticket : tickets) {
            Map<String, Object> data = new HashMap<>();
            data.put("trainCompany", ticket.getTrainCompany());
            data.put("trainId", ticket.getTrainId().toString());
            data.put("seatId", ticket.getSeatId().toString());
            data.put("ticketId", ticket.getTicketId().toString());
            data.put("customer", ticket.getCustomer());
            data.put("bookingReference", ticket.getBookingReference());
            encodedTickets.add(data);
        }
        return encodedTickets;
    }
}
