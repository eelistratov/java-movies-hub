package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonSyntaxException;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.Year;
import java.util.*;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        try {
            if ("GET".equals(method) && "/movies".equals(path)) {
                if (query != null && query.startsWith("year=")) {
                    handleGetByYear(exchange, query);
                } else {
                    handleGetAll(exchange);
                }
            } else if ("GET".equals(method) && path.startsWith("/movies/")) {
                handleGetById(exchange, path);
            } else if ("POST".equals(method) && "/movies".equals(path)) {
                handlePost(exchange);
            } else if ("DELETE".equals(method) && path.startsWith("/movies/")) {
                handleDelete(exchange, path);
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<Movie> movies = store.getAll();
        String response = gson.toJson(movies);
        sendResponse(exchange, 200, response);
    }

    private void handleGetByYear(HttpExchange exchange, String query) throws IOException {
        try {
            String yearStr = query.substring(5);
            int year = Integer.parseInt(yearStr);
            List<Movie> movies = store.getByYear(year);
            String response = gson.toJson(movies);
            sendResponse(exchange, 200, response);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Некорректный параметр запроса — 'year'\"}");
        }
    }

    private void handleGetById(HttpExchange exchange, String path) throws IOException {
        try {
            String idStr = path.substring(8);
            int id = Integer.parseInt(idStr);
            Optional<Movie> movie = store.getById(id);
            if (movie.isPresent()) {
                sendResponse(exchange, 200, gson.toJson(movie.get()));
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Фильм не найден\"}");
            }
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Некорректный ID\"}");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        if (!isContentTypeJson(exchange)) {
            sendResponse(exchange, 415, "{\"error\":\"Unsupported Media Type\"}");
            return;
        }

        String body = readBody(exchange);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = gson.fromJson(body, Map.class);
            String title = (String) json.get("title");
            Integer yearInteger = (Integer) json.get("year");
            int year = yearInteger != null ? yearInteger : 0;


            List<String> errors = validateMovie(title, year);
            if (!errors.isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации", errors);
                sendResponse(exchange, 422, gson.toJson(errorResponse));
                return;
            }

            Movie movie = store.add(title, year);
            sendResponse(exchange, 201, gson.toJson(movie));
        } catch (JsonSyntaxException e) {
            ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации",
                    List.of("Некорректный JSON"));
            sendResponse(exchange, 422, gson.toJson(errorResponse));
        }
    }

    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        try {
            String idStr = path.substring(8);
            int id = Integer.parseInt(idStr);
            if (store.delete(id)) {
                sendEmptyResponse(exchange, 204);
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Фильм не найден\"}");
            }
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Некорректный ID\"}");
        }
    }

    private List<String> validateMovie(String title, int year) {
        List<String> errors = new ArrayList<>();
        int currentYear = Year.now().getValue();

        if (title == null || title.trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (title.length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        if (year < 1888 || year > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }

        return errors;
    }
}