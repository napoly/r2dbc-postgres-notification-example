package com.example.demo;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.connectionfactory.init.CompositeDatabasePopulator;
import org.springframework.data.r2dbc.connectionfactory.init.ConnectionFactoryInitializer;
import org.springframework.data.r2dbc.connectionfactory.init.ResourceDatabasePopulator;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;

@SpringBootApplication
@EnableR2dbcRepositories(considerNestedRepositories = true)
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public PostgresqlConnectionFactory connectionFactory() {
		return new PostgresqlConnectionFactory(
				PostgresqlConnectionConfiguration.builder()
						.database("postgres")
						.host("localhost")
						.port(5432)
						.username("postgres")
						.password("example")
						.build());
	}

	@Bean
	public ConnectionFactoryInitializer initializer(PostgresqlConnectionFactory connectionFactory) {
		CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
		ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
		resourceDatabasePopulator.setSeparator(";;");
		populator.addPopulators(resourceDatabasePopulator);

		ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
		initializer.setConnectionFactory(connectionFactory);
		initializer.setDatabasePopulator(populator);
		return initializer;
	}

	@RestController
	class LoginController {

		final LoginEventRepository repository;
		final PostgresqlConnection connection;

		LoginController(LoginEventRepository repository, PostgresqlConnectionFactory connectionFactory) {
			this.repository = repository;
			this.connection = Mono.from(connectionFactory.create()).block();
		}

		@PostConstruct
		private void postConstruct() {
			connection.createStatement("LISTEN login_event_notification").execute()
					.flatMap(PostgresqlResult::getRowsUpdated).subscribe();
		}

		@PreDestroy
		private void preDestroy() {
			connection.close().subscribe();
		}

		@PostMapping("/login/{username}")
		Mono<Void> login(@PathVariable String username) {
			return repository.save(new LoginEvent(username, LocalDateTime.now())).then();
		}

		@GetMapping(value = "/login-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
		Flux<CharSequence> getStream() {
			return connection.getNotifications().map(Notification::getParameter);
		}

	}

	interface LoginEventRepository extends ReactiveCrudRepository<LoginEvent, Integer> {

	}

	@Table
	class LoginEvent {

		@Id
		Integer id;

		String username;

		LocalDateTime loginTime;

		public LoginEvent(String username, LocalDateTime loginTime) {
			this.username = username;
			this.loginTime = loginTime;
		}

	}

}
