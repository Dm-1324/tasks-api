package com.tasks;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TasksController {

    private static final Logger log = LoggerFactory.getLogger(TasksController.class);

    /** A task, saved as one JSON blob: <id>.json */
    public record TaskItem(String id, String title, boolean done, String createdAt) {}

    private final BlobContainerClient tasks;

    @Value("${app.version}")
    private String version;

    /**
     * The API key. On App Service this app setting is a Key Vault reference:
     *   API_KEY = @Microsoft.KeyVault(SecretUri=https://<vault>.vault.azure.net/secrets/tasks-api-key/)
     * App Service fetches the secret with the app's managed identity, so the code needs no Key Vault SDK.
     */
    @Value("${app.api-key}")
    private String apiKey;

    public TasksController(BlobContainerClient tasks) {
        this.tasks = tasks;
    }

    /** 1. GET /api/info - is the app up, which version, is the API key configured? */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "app", "Tasks API on Azure",
                "version", version,
                "apiKeyConfigured", !apiKey.isBlank() && !apiKey.startsWith("@Microsoft.KeyVault"),
                "time", Instant.now().toString());
    }

    /** 2. POST /api/tasks - create a task (needs X-API-KEY). */
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskItem create(@RequestHeader(value = "X-API-KEY", required = false) String key,
                           @RequestBody Map<String, String> body) {
        checkKey(key);
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("'title' is required");
        }
        TaskItem task = new TaskItem(UUID.randomUUID().toString().substring(0, 8), title, false,
                Instant.now().toString());
        save(task);
        log.info("Created task {} '{}'", task.id(), task.title());
        return task;
    }

    /** 3. GET /api/tasks - list all tasks (public, read-only). */
    @GetMapping("/tasks")
    public List<TaskItem> list() {
        List<TaskItem> result = new ArrayList<>();
        for (BlobItem item : tasks.listBlobs()) {
            result.add(tasks.getBlobClient(item.getName()).downloadContent().toObject(TaskItem.class));
        }
        log.info("Listed {} tasks", result.size());
        return result;
    }

    /** 4. PUT /api/tasks/{id}/done - mark a task as done (needs X-API-KEY). */
    @PutMapping("/tasks/{id}/done")
    public TaskItem markDone(@RequestHeader(value = "X-API-KEY", required = false) String key,
                             @PathVariable String id) {
        checkKey(key);
        BlobClient blob = tasks.getBlobClient(id + ".json");
        if (!blob.exists()) {
            throw new NotFoundException("task " + id + " not found");
        }
        TaskItem old = blob.downloadContent().toObject(TaskItem.class);
        TaskItem done = new TaskItem(old.id(), old.title(), true, old.createdAt());
        save(done);
        log.info("Marked task {} as done", id);
        return done;
    }

    private void save(TaskItem task) {
        tasks.getBlobClient(task.id() + ".json").upload(BinaryData.fromObject(task), true);
    }

    private void checkKey(String key) {
        if (key == null || !key.equals(apiKey)) {
            throw new UnauthorizedException("missing or wrong X-API-KEY header");
        }
    }

    // ---- errors -> clean JSON with the right status code ----

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(UnauthorizedException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NotFoundException e) {
        return Map.of("error", e.getMessage());
    }

    static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String m) { super(m); }
    }

    static class NotFoundException extends RuntimeException {
        NotFoundException(String m) { super(m); }
    }
}
