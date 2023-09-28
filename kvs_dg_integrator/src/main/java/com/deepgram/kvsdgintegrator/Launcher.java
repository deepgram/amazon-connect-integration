package com.deepgram.kvsdgintegrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Launcher {
	private static final Logger logger = LogManager.getLogger(Launcher.class);

	public static void main(String[] args) throws IOException {
		logger.info("Launching integrator task");
		String deepgramApiKey = System.getenv("DEEPGRAM_API_KEY");
		if (deepgramApiKey == null) {
			logger.error("This task expects an environment variable DEEPGRAM_API_KEY");
			return;
		}

		HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
		server.createContext("/health-check", httpExchange -> {
			try (httpExchange) {
				httpExchange.sendResponseHeaders(200, 0);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		server.createContext("/start-session", new StartSessionHandler(deepgramApiKey));
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
	}

	static class StartSessionHandler implements HttpHandler {
		private final String deepgramApiKey;

		private static final Logger logger = LogManager.getLogger(StartSessionHandler.class);

		public StartSessionHandler(String deepgramApiKey) {
			this.deepgramApiKey = Validate.notNull(deepgramApiKey);
		}

		@Override
		public void handle(HttpExchange httpExchange) {
			logger.info("Received HTTP request");

			IntegratorArguments integratorArguments;
			try (httpExchange) {
				InputStream requestBody = httpExchange.getRequestBody();

				try {
					ObjectMapper objectMapper = new ObjectMapper();
					integratorArguments = objectMapper.readValue(requestBody, IntegratorArguments.class);
				} catch (JsonProcessingException e) {
					logger.error("Error parsing JSON for start-session request", e);
					sendBadRequest("Error parsing JSON body for start-session request", httpExchange);
					return;
				}

				sendSuccess(httpExchange);
			} catch (IOException e) {
				logger.error("IOException while handling start-session request", e);
				return;
			}

			Validate.notNull(integratorArguments);
			logger.info("Integrator Arguments: %s".formatted(integratorArguments));

			try {
				KvsToDgStreamer.startKvsToDgStreaming(integratorArguments, this.deepgramApiKey);
			} catch (Exception e) {
				logger.error("Exception during integrator session", e);
			}
		}

		private void sendSuccess(HttpExchange httpExchange) throws IOException {
			sendSuccess("", httpExchange);
		}

		private void sendSuccess(String body, HttpExchange httpExchange) throws IOException {
			sendResponse(200, body, httpExchange);
		}

		private void sendBadRequest(String body, HttpExchange httpExchange) throws IOException {
			sendResponse(400, body, httpExchange);
		}

		private void sendResponse(int statusCode, String body, HttpExchange httpExchange) throws IOException {
			httpExchange.sendResponseHeaders(statusCode, body.getBytes().length);
			OutputStream os = httpExchange.getResponseBody();
			os.write(body.getBytes());
			os.close();
		}
	}
}
