package com.retit.greensoftware.scheduler;

import com.retit.greensoftware.service.TeamSetupService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Handles application startup events: initialises teams from configuration
 * and (optionally) triggers an initial carbon data sync.
 */
@ApplicationScoped
public class ApplicationStartup {

    private static final Logger LOG = Logger.getLogger(ApplicationStartup.class);

    @Inject
    TeamSetupService teamSetupService;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Green Software Champions starting up...");
        teamSetupService.initialiseTeams();
        LOG.info("Application startup complete.");
    }
}
