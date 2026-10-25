package com.walletpe.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Discovers and loads environment variables from a local .env file into System properties
     * before Spring Boot bootstraps, identical to user-service.
     */
    private static void loadDotEnv() {
        Set<Path> candidates = new LinkedHashSet<>();
        Path directory = Paths.get("").toAbsolutePath().normalize();
        while (directory != null) {
            candidates.add(directory.resolve(".env"));
            candidates.add(directory.resolve("config/env/.env"));
            directory = directory.getParent();
        }

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                try (BufferedReader reader = Files.newBufferedReader(candidate)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String val = line.substring(eqIdx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                (val.startsWith("'") && val.endsWith("'"))) {
                                if (val.length() >= 2) {
                                    val = val.substring(1, val.length() - 1);
                                }
                            }
                            // Do not override environment variables already set by shell
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                    System.out.println("Loaded local environment configuration from " + candidate.toAbsolutePath());
                } catch (Exception ex) {
                    System.err.println("Unable to read local environment configuration from "
                            + candidate.toAbsolutePath() + ": " + ex.getMessage());
                }
                break;
            }
        }
    }
}
