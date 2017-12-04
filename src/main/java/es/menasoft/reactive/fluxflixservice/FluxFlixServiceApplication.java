package es.menasoft.reactive.fluxflixservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.HttpSecurity;
import org.springframework.security.core.userdetails.MapUserDetailsRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class FluxFlixServiceApplication {

    @Bean
    RouterFunction<ServerResponse> routerFunction(MovieHandler handler) {
        return route(RequestPredicates.GET("/movies"), handler::all)
                .andRoute(RequestPredicates.GET("/movies/{id}"), handler::byId)
                .andRoute(RequestPredicates.GET("/movies/{id}/events"), handler::events);
    }

    public static void main(String[] args) {
        SpringApplication.run(FluxFlixServiceApplication.class, args);
    }
}

@EnableWebFluxSecurity
@Configuration
class SecurityConfiguration {

    @Bean
    UserDetailsRepository authentication() {
        return new MapUserDetailsRepository(User.withUsername("jam").password("developer").roles("USER").build(),
                User.withUsername("jjmena").password("developer").roles("USER", "ADMIN").build());
    }

    @Bean
    SecurityWebFilterChain authorization(HttpSecurity security) {
        return security
                .httpBasic()
                .and()
                .authorizeExchange().anyExchange().hasRole("ADMIN")
                .and()
                .build();
    }
}

@Component
class MovieHandler {
    private final MovieService movieService;

    public MovieHandler(MovieService movieService) {
        this.movieService = movieService;
    }

    public Mono<ServerResponse> all(ServerRequest serverRequest) {
        return ServerResponse.ok().body(movieService.getAllMovies(), Movie.class);
    }

    public Mono<ServerResponse> byId(ServerRequest serverRequest) {
        return ServerResponse.ok()
                .body(movieService.getMovieById(serverRequest.pathVariable("id")), Movie.class);
    }

    public Mono<ServerResponse> events(ServerRequest serverRequest) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(movieService.getEvents(serverRequest.pathVariable("id")), MovieEvent.class);
    }
}

@Component
class SampleDataInitializer implements ApplicationRunner {
    private final MovieRepository mr;

    SampleDataInitializer(MovieRepository mr) {
        this.mr = mr;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        Flux<Movie> movieFlux = Flux.just("Silence of the lambdas", "Y Tu Mono También", "Back to the future", "AEon Flux")
                .map(m -> new Movie(null, m))
                .flatMap(mr::save);


        mr.deleteAll()
                .thenMany(movieFlux)
                .thenMany(mr.findAll())
                .subscribe(System.out::println);
    }
}

@Service
class MovieService {
    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public Flux<Movie> getAllMovies() {
        return this.movieRepository.findAll();
    }

    public Mono<Movie> getMovieById(String id) {
        return this.movieRepository.findById(id);
    }

    public Flux<MovieEvent> getEvents(String movieId) {
        return Flux.<MovieEvent>generate(sink -> sink.next(new MovieEvent(movieId, new Date())))
                .delayElements(Duration.ofSeconds(1));
    }

}

/*
@RestController
@RequestMapping("/movies")
class MoviesController {
    private final MovieService movieService;

    public MoviesController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public Flux<Movie> all() {
        return this.movieService.getAllMovies();
    }

    @GetMapping("/{id}")
    public Mono<Movie> byId(@PathVariable String id) {
        return this.movieService.getMovieById(id);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MovieEvent> events(@PathVariable String id) {
        return this.movieService.getEvents(id);
    }
}
*/

@Data
@NoArgsConstructor
@AllArgsConstructor
class MovieEvent {
    private String movieId;
    private Date dateViewed;
}

interface MovieRepository extends ReactiveMongoRepository<Movie, String> {
    Flux<Movie> findByTitle(String title);
}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Movie {
    @Id
    private String id;

    private String title;
}


