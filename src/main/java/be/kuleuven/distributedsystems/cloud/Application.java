package be.kuleuven.distributedsystems.cloud;

import be.kuleuven.distributedsystems.cloud.pubsub.PubSub;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.HypermediaWebClientConfigurer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@SpringBootApplication
public class Application {

    public static PubSub pubSub;
    public static Firestore db;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException, InterruptedException {
        System.setProperty("server.port", System.getenv().getOrDefault("PORT", "8080"));
        ApplicationContext context = SpringApplication.run(Application.class, args);
        setup(projectId());
        // TODO: (level 2) load this data into Firestore
        String data = new String(new ClassPathResource("data.json").getInputStream().readAllBytes());
    }

    static void setup(String projectId) {
        db = FirestoreOptions.getDefaultInstance()
                .toBuilder()
                .setProjectId(projectId)
                .setCredentials(new FirestoreOptions.EmulatorCredentials())
                .setEmulatorHost("localhost:8084")
                .build()
                .getService();
    }

    @Bean
    public boolean isProduction() {
        return Objects.equals(System.getenv("GAE_ENV"), "standard");
    }

    @Bean
    public static String projectId() {
        return "demo-distributed-systems-kul";
    }

    @Bean
    public static PubSub getPubSub() {
        if(pubSub == null) pubSub = new PubSub(Application.projectId(), "confirm-quote", "test");
        return pubSub;
    }

    @Bean
    public Firestore make_db() {
        return FirestoreOptions.getDefaultInstance()
                .toBuilder()
                .setProjectId(projectId())
                .setCredentials(new FirestoreOptions.EmulatorCredentials())
                .setEmulatorHost("localhost:8084")
                .build()
                .getService();
    }

    /*
     * You can use this builder to create a Spring WebClient instance which can be used to make REST-calls.
     */
    @Bean
    WebClient.Builder webClientBuilder(HypermediaWebClientConfigurer configurer) {
        return configurer.registerHypermediaTypes(WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)));
    }

    @Bean
    HttpFirewall httpFirewall() {
        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }
}
