package org.raincityvoices.ttrack.tools;

import java.util.Optional;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import com.clerk.backend_api.Clerk;
import com.clerk.backend_api.models.components.ActorToken;
import com.clerk.backend_api.models.errors.ClerkErrors;
import com.clerk.backend_api.models.operations.Actor;
import com.clerk.backend_api.models.operations.CreateActorTokenRequestBody;
import com.clerk.backend_api.models.operations.CreateActorTokenResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Option;

@SpringBootApplication
@Component
@ComponentScan(basePackages = "org.raincityvoices.ttrack")
@Slf4j
public class OpsCli implements CommandLineRunner {

    private ApplicationContext appCtx;

    public OpsCli(ApplicationContext appCtx) { this.appCtx = appCtx; }

    private class CommandFactory implements IFactory {
        @Override
        public <K> K create(Class<K> cls) throws Exception {
            log.info("Creating command {}", cls);
            return appCtx.getBean(cls);
        }
    }

    @Component
    @Command(name="get-token", aliases = {"gt"}, description="Get testing JWT Clerk token")
    @RequiredArgsConstructor
    private static class GetToken implements Callable<Integer> {
        private final Clerk clerk;
        private final String clerkApiKey;

        @Option(names = "-u")
        private String userId;

        @Override
        public Integer call() throws Exception {
            log.info("Starting get-token");
            CreateActorTokenResponse resp;
            try {
                resp = clerk.actorTokens().create(Optional.of(CreateActorTokenRequestBody.builder()
                                .actor(Actor.builder().sub(userId).build())
                                .userId(userId)
                                .expiresInSeconds(3600)
                                .sessionMaxDurationInSeconds(3600)
                                .build()
                        ), Optional.empty());
            } catch(ClerkErrors e) {
                log.error("Error(s) calling CreateActorToken: {}", e.getMessage());
                e.errors().forEach(err -> log.error("- {}: {}", err.code(), err.longMessage()));
                return -1;
            }
            if (resp.actorToken().isEmpty()) {
                log.error("No token returned. Response [{}]: {}", resp.statusCode(), resp.rawResponse().body());
                return -1;
            }
            ActorToken token = resp.actorToken().get();
            log.info(token.url().orElse("NO URL"));
            // log.info("Got token: {}", token.token().orElse("<n/a>"));
            return 0;
        }
    }
    
    @Command(subcommands = {
        GetToken.class
    })
    public class Main {

    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Command line: {}", StringUtils.join(args, " "));
        CommandLine cl = new CommandLine(new Main(), new CommandFactory());
        System.exit(cl.execute(args));
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(OpsCli.class);
        app.setAdditionalProfiles("cli");
        app.run(args);
    }
}
